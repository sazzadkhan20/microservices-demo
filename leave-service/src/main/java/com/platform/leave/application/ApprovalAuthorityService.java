package com.platform.leave.application;

import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.domain.LeaveApplication;
import com.platform.leave.domain.LeaveApproval;
import com.platform.leave.domain.LeavePolicy;
import com.platform.leave.extension.config.TenantConfigLoader;
import com.platform.leave.client.people.PeopleEmployeeGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.platform.leave.client.people.PeopleAuthzClient;

/**
 * Config-driven leave approval authority.
 *
 * <p>Normal decide: actor must match the current step resolver.
 * On-behalf (FR-LV-16): Admin / SuperAdmin / leave:manage may act only after the step
 * has been idle for N working days (default 2). Requester never decides their own request.
 *
 * <p>Resolvers: {@code REPORTING_MANAGER}, {@code REPORTING_MANAGER_AND_DESIGNATION},
 * {@code DESIGNATION_OR_RBAC}, {@code RBAC_ROLE}, {@code MANAGER_OF_MANAGER}, {@code NAMED_EMPLOYEE}.
 */
@Service
public class ApprovalAuthorityService {

    public static final String RESOLVER_REPORTING_MANAGER = "REPORTING_MANAGER";
    public static final String RESOLVER_REPORTING_MANAGER_AND_DESIGNATION = "REPORTING_MANAGER_AND_DESIGNATION";
    public static final String RESOLVER_DESIGNATION_OR_RBAC = "DESIGNATION_OR_RBAC";
    public static final String RESOLVER_RBAC_ROLE = "RBAC_ROLE";
    public static final String RESOLVER_MANAGER_OF_MANAGER = "MANAGER_OF_MANAGER";
    public static final String RESOLVER_NAMED_EMPLOYEE = "NAMED_EMPLOYEE";

    public enum Authority {
        NONE,
        /** Actor is the configured step approver (manager / named / role holder). */
        STEP,
        /** Admin acting after idle threshold — audit must flag on-behalf. */
        ON_BEHALF
    }

    private static final Set<DayOfWeek> WEEKDAYS = EnumSet.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private final PeopleEmployeeGateway employeeRepository;
    private final TenantConfigLoader configLoader;
    private final PeopleAuthzClient rbacService;

    public ApprovalAuthorityService(
            PeopleEmployeeGateway employeeRepository,
            TenantConfigLoader configLoader,
            PeopleAuthzClient rbacService) {
        this.employeeRepository = employeeRepository;
        this.configLoader = configLoader;
        this.rbacService = rbacService;
    }

    /**
     * Ensures {@code actorEmployeeId} may decide the given pending step; returns actor + mode.
     */
    @Transactional(readOnly = true)
    public Decision assertCanApprove(Long actorEmployeeId, LeaveApproval step, LeaveApplication app) {
        EmployeeView actor = requireActive(actorEmployeeId);
        Authority auth = resolveAuthority(actor, step, app);
        if (auth == Authority.NONE) {
            StepRule rule = resolveStepRule(step);
            throw new IllegalStateException(
                    "Not allowed to approve step " + step.getStepOrder()
                            + " (role=" + rule.role() + ", resolver=" + rule.resolver()
                            + "). Waiting on the configured approver for this step"
                            + (isOverrideEligible(actor.getId())
                            ? " (on-behalf only after " + idleWorkingDays() + " idle working days)"
                            : "")
                            + ".");
        }
        return new Decision(actor, auth);
    }

    /** Non-throwing check used by pending queues. */
    @Transactional(readOnly = true)
    public boolean canApprove(Long actorEmployeeId, LeaveApproval step, LeaveApplication app) {
        return authorityOf(actorEmployeeId, step, app) != Authority.NONE;
    }

    @Transactional(readOnly = true)
    public Authority authorityOf(Long actorEmployeeId, LeaveApproval step, LeaveApplication app) {
        if (actorEmployeeId == null || step == null || app == null) {
            return Authority.NONE;
        }
        EmployeeView actor = employeeRepository.findById(actorEmployeeId).orElse(null);
        if (actor == null || !actor.isActive()) {
            return Authority.NONE;
        }
        return resolveAuthority(actor, step, app);
    }

    private Authority resolveAuthority(EmployeeView actor, LeaveApproval step, LeaveApplication app) {
        // Never allow the requester to decide their own leave (conflict of interest).
        if (actor.getId() != null && actor.getId().equals(app.getEmployeeId())) {
            return Authority.NONE;
        }
        if (matchesResolver(actor, app, resolveStepRule(step))) {
            return Authority.STEP;
        }
        // Production: on-behalf only when idle ≥ N working days.
        if (isOverrideEligible(actor.getId()) && isIdleForOnBehalf(app)) {
            return Authority.ON_BEHALF;
        }
        return Authority.NONE;
    }

    /** True when the requester holds a role-pool step (used to skip at chain materialization). */
    @Transactional(readOnly = true)
    public boolean requesterHoldsStepRole(Long requesterId, String role, List<String> roleCodes) {
        if (requesterId == null) {
            return false;
        }
        return holdsAnyRole(requesterId, roleCodes, role)
                || designationMatches(
                        employeeRepository.findById(requesterId).orElse(null),
                        role);
    }

    @Transactional(readOnly = true)
    public boolean employeeHoldsStepRole(Long employeeId, String role, List<String> roleCodes) {
        return requesterHoldsStepRole(employeeId, role, roleCodes);
    }

    private boolean matchesResolver(EmployeeView actor, LeaveApplication app, StepRule rule) {
        String resolver = rule.resolver() == null ? RESOLVER_DESIGNATION_OR_RBAC : rule.resolver().toUpperCase(Locale.ROOT);
        return switch (resolver) {
            case RESOLVER_REPORTING_MANAGER -> isReportingManager(actor, app);
            case RESOLVER_REPORTING_MANAGER_AND_DESIGNATION ->
                    isReportingManager(actor, app) && designationMatches(actor, rule.role());
            case RESOLVER_RBAC_ROLE -> holdsAnyRole(actor.getId(), rule.roleCodes(), rule.role());
            case RESOLVER_DESIGNATION_OR_RBAC ->
                    designationMatches(actor, rule.role()) || holdsAnyRole(actor.getId(), rule.roleCodes(), rule.role());
            case RESOLVER_MANAGER_OF_MANAGER -> isManagerOfManager(actor, app);
            case RESOLVER_NAMED_EMPLOYEE -> isNamedEmployee(actor, rule);
            default -> throw new IllegalStateException("Unknown approval resolver: " + resolver);
        };
    }

    private boolean isManagerOfManager(EmployeeView actor, LeaveApplication app) {
        EmployeeView applicant = employeeRepository.findById(app.getEmployeeId()).orElse(null);
        if (applicant == null || applicant.getManagerId() == null) {
            return false;
        }
        EmployeeView manager = employeeRepository.findById(applicant.getManagerId()).orElse(null);
        return manager != null && manager.getManagerId() != null && manager.getManagerId().equals(actor.getId());
    }

    private boolean isNamedEmployee(EmployeeView actor, StepRule rule) {
        if (rule.namedEmployeeId() != null && rule.namedEmployeeId().equals(actor.getId())) {
            return true;
        }
        return rule.expectedApproverId() != null && rule.expectedApproverId().equals(actor.getId());
    }

    private boolean isReportingManager(EmployeeView actor, LeaveApplication app) {
        EmployeeView applicant = employeeRepository.findById(app.getEmployeeId()).orElse(null);
        return applicant != null
                && applicant.getManagerId() != null
                && applicant.getManagerId().equals(actor.getId());
    }

    private boolean designationMatches(EmployeeView actor, String requiredRole) {
        if (actor == null || requiredRole == null || requiredRole.isBlank()) {
            return false;
        }
        return designationApproverRole(actor)
                .map(r -> r.equalsIgnoreCase(requiredRole))
                .orElse(false);
    }

    boolean holdsAnyRole(Long employeeId, List<String> roleCodes, String fallbackRole) {
        if (employeeId == null) {
            return false;
        }
        Set<String> held = rbacService.roleCodesFor(employeeId).stream()
                .filter(Objects::nonNull)
                .map(c -> c.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> want = new ArrayList<>();
        if (roleCodes != null) {
            for (String c : roleCodes) {
                if (c != null && !c.isBlank()) {
                    want.add(c.toUpperCase(Locale.ROOT));
                }
            }
        }
        if (want.isEmpty() && fallbackRole != null && !fallbackRole.isBlank()) {
            String fb = fallbackRole.toUpperCase(Locale.ROOT);
            if ("SUPER_ADMIN".equals(fb)) {
                want.add("SUPERADMIN");
            } else {
                want.add(fb);
            }
        }
        for (String w : want) {
            if (held.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** Admin / SuperAdmin / leave:manage — eligible for idle on-behalf only. */
    private boolean isOverrideEligible(Long employeeId) {
        if (rbacService.hasWildcard(employeeId)) {
            return true;
        }
        if (rbacService.hasPermission(employeeId, "leave", "manage")) {
            return true;
        }
        Map<String, Object> auth = approvalAuthoritySettings();
        Object raw = auth.get("overrideRoleCodes");
        if (!(raw instanceof Collection<?> col) || col.isEmpty()) {
            return false;
        }
        Set<String> overrides = col.stream()
                .filter(Objects::nonNull)
                .map(o -> String.valueOf(o).trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        if (overrides.isEmpty()) {
            return false;
        }
        return rbacService.roleCodesFor(employeeId).stream()
                .filter(Objects::nonNull)
                .map(c -> c.toUpperCase(Locale.ROOT))
                .anyMatch(overrides::contains);
    }

    @Transactional(readOnly = true)
    public boolean isIdleForOnBehalf(LeaveApplication app) {
        if (app == null) {
            return false;
        }
        Instant since = app.getUpdatedAt() != null ? app.getUpdatedAt() : app.getCreatedAt();
        if (since == null) {
            return false;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate start = LocalDate.ofInstant(since, zone);
        LocalDate today = LocalDate.now(zone);
        return workingDaysBetween(start, today) >= idleWorkingDays();
    }

    int idleWorkingDays() {
        Object reminderCfg = configLoader.loadSettingsMap().get("leaveIdleReminder");
        if (reminderCfg instanceof Map<?, ?> cfg) {
            Object d = cfg.get("idleWorkingDays");
            if (d instanceof Number n && n.intValue() > 0) {
                return n.intValue();
            }
        }
        return 2;
    }

    /** Inclusive of start exclusive of end — Mon–Fri only. */
    static int workingDaysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return 0;
        }
        int count = 0;
        for (LocalDate d = start.plusDays(1); !d.isAfter(end); d = d.plusDays(1)) {
            if (WEEKDAYS.contains(d.getDayOfWeek())) {
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    StepRule resolveStepRule(LeaveApproval step) {
        String storedRole = step.getApproverRole() == null ? "" : step.getApproverRole().trim();
        String stepResolver = step.getResolver();
        Map<String, Object> chainItem = findChainItem(step.getStepOrder(), storedRole);
        Map<String, Object> defaults = roleDefaults();

        Map<String, Object> fromRole = Map.of();
        if (!storedRole.isEmpty()) {
            Object d = defaults.get(storedRole.toUpperCase(Locale.ROOT));
            if (d instanceof Map<?, ?> m) {
                fromRole = castMap(m);
            }
        }
        Object star = defaults.get("*");
        Map<String, Object> fromStar = star instanceof Map<?, ?> m ? castMap(m) : Map.of();

        String resolver = firstString(
                stepResolver,
                chainItem.get("resolver"),
                fromRole.get("resolver"),
                fromStar.get("resolver"),
                RESOLVER_DESIGNATION_OR_RBAC);
        List<String> roleCodes = firstRoleCodes(
                chainItem.get("roleCodes"), fromRole.get("roleCodes"), fromStar.get("roleCodes"));
        String role = firstString(storedRole, chainItem.get("role"), "");
        return new StepRule(role, resolver.toUpperCase(Locale.ROOT), roleCodes,
                step.getNamedEmployeeId(), step.getExpectedApproverId());
    }

    private Map<String, Object> findChainItem(int stepOrder, String storedRole) {
        LeavePolicy policy = configLoader.loadLeavePolicyOrDefault();
        List<Map<String, Object>> chain = policy.getApprovalChain();
        if (chain == null) {
            return Map.of();
        }
        Map<String, Object> byStep = Map.of();
        for (Map<String, Object> item : chain) {
            if (item == null) {
                continue;
            }
            Object s = item.get("step");
            int n = s instanceof Number num ? num.intValue() : -1;
            String role = item.get("role") == null ? "" : String.valueOf(item.get("role")).trim();
            if (!storedRole.isEmpty() && role.equalsIgnoreCase(storedRole)) {
                return item;
            }
            if (n == stepOrder && byStep.isEmpty()) {
                byStep = item;
            }
        }
        return byStep;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> approvalAuthoritySettings() {
        Object raw = configLoader.loadSettingsMap().get("approvalAuthority");
        if (raw instanceof Map<?, ?> m) {
            return castMap(m);
        }
        return defaultApprovalAuthority();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> roleDefaults() {
        Object raw = approvalAuthoritySettings().get("roleDefaults");
        if (!(raw instanceof Map<?, ?> m) || m.isEmpty()) {
            return castMap((Map<?, ?>) defaultApprovalAuthority().get("roleDefaults"));
        }
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            out.put(String.valueOf(e.getKey()).toUpperCase(Locale.ROOT), e.getValue());
        }
        if (!out.containsKey("*")) {
            out.put("*", Map.of("resolver", RESOLVER_DESIGNATION_OR_RBAC));
        }
        return out;
    }

    /** Platform-neutral defaults so tenants without pack settings still behave sanely. */
    static Map<String, Object> defaultApprovalAuthority() {
        Map<String, Object> roleDefaults = new HashMap<>();
        roleDefaults.put("MANAGER", Map.of("resolver", RESOLVER_REPORTING_MANAGER));
        roleDefaults.put("MANAGER_OF_MANAGER", Map.of("resolver", RESOLVER_MANAGER_OF_MANAGER));
        roleDefaults.put("STORE_MANAGER", Map.of("resolver", RESOLVER_REPORTING_MANAGER_AND_DESIGNATION));
        roleDefaults.put("HR", Map.of("resolver", RESOLVER_RBAC_ROLE, "roleCodes", List.of("HR")));
        roleDefaults.put("ADMIN", Map.of(
                "resolver", RESOLVER_RBAC_ROLE,
                "roleCodes", List.of("ADMIN", "SUPERADMIN")));
        roleDefaults.put("SUPER_ADMIN", Map.of(
                "resolver", RESOLVER_RBAC_ROLE,
                "roleCodes", List.of("SUPERADMIN")));
        roleDefaults.put("SUPERADMIN", Map.of(
                "resolver", RESOLVER_RBAC_ROLE,
                "roleCodes", List.of("SUPERADMIN")));
        roleDefaults.put("*", Map.of("resolver", RESOLVER_DESIGNATION_OR_RBAC));
        Map<String, Object> auth = new HashMap<>();
        auth.put("overrideRoleCodes", List.of("SUPERADMIN", "ADMIN"));
        auth.put("overridePermissions", List.of("leave:manage"));
        auth.put("roleDefaults", roleDefaults);
        return auth;
    }

    private java.util.Optional<String> designationApproverRole(EmployeeView employee) {
        String role = employee.getDesignationApproverRole();
        if (role == null || role.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(role.trim());
    }

    private EmployeeView requireActive(Long id) {
        EmployeeView e = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Approver employee not found: " + id));
        if (!e.isActive()) {
            throw new IllegalStateException("Approver is inactive");
        }
        return e;
    }

    private static String firstString(Object... values) {
        for (Object v : values) {
            if (v == null) {
                continue;
            }
            String s = String.valueOf(v).trim();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
                return s;
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static List<String> firstRoleCodes(Object... values) {
        for (Object v : values) {
            if (v instanceof Collection<?> col && !col.isEmpty()) {
                List<String> out = new ArrayList<>();
                for (Object o : col) {
                    if (o != null && !String.valueOf(o).isBlank()) {
                        out.add(String.valueOf(o).trim());
                    }
                }
                if (!out.isEmpty()) {
                    return out;
                }
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    record StepRule(String role, String resolver, List<String> roleCodes,
                    Long namedEmployeeId, Long expectedApproverId) {}

    public record Decision(EmployeeView actor, Authority authority) {
        public boolean onBehalf() {
            return authority == Authority.ON_BEHALF;
        }
    }
}
