package com.platform.hrms.application;

import com.platform.hrms.domain.Designation;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.LeaveApplication;
import com.platform.hrms.domain.LeaveApproval;
import com.platform.hrms.domain.LeavePolicy;
import com.platform.hrms.extension.config.TenantConfigLoader;
import com.platform.hrms.infrastructure.persistence.DesignationRepository;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApprovalDelegateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Config-driven leave approval authority. Engines use resolver types only —
 * business role names live in {@code tenant_settings.approvalAuthority} and
 * {@code leave_policy.approval_chain}, never in Java if-branches.
 *
 * <p>Resolvers: {@code REPORTING_MANAGER}, {@code REPORTING_MANAGER_AND_DESIGNATION},
 * {@code DESIGNATION_OR_RBAC}, {@code RBAC_ROLE}.
 */
@Service
public class ApprovalAuthorityService {

    public static final String RESOLVER_REPORTING_MANAGER = "REPORTING_MANAGER";
    public static final String RESOLVER_REPORTING_MANAGER_AND_DESIGNATION = "REPORTING_MANAGER_AND_DESIGNATION";
    public static final String RESOLVER_DESIGNATION_OR_RBAC = "DESIGNATION_OR_RBAC";
    public static final String RESOLVER_RBAC_ROLE = "RBAC_ROLE";
    public static final String RESOLVER_MANAGER_OF_MANAGER = "MANAGER_OF_MANAGER";
    public static final String RESOLVER_NAMED_EMPLOYEE = "NAMED_EMPLOYEE";

    private final EmployeeRepository employeeRepository;
    private final DesignationRepository designationRepository;
    private final TenantConfigLoader configLoader;
    private final RbacService rbacService;
    private final LeaveApprovalDelegateRepository delegateRepository;

    public ApprovalAuthorityService(
            EmployeeRepository employeeRepository,
            DesignationRepository designationRepository,
            TenantConfigLoader configLoader,
            RbacService rbacService,
            LeaveApprovalDelegateRepository delegateRepository) {
        this.employeeRepository = employeeRepository;
        this.designationRepository = designationRepository;
        this.configLoader = configLoader;
        this.rbacService = rbacService;
        this.delegateRepository = delegateRepository;
    }

    /**
     * Ensures {@code actorEmployeeId} may decide the given pending step; returns the active actor.
     * Override is config-only ({@code overrideRoleCodes} / optional {@code overridePermissions}) —
     * holding {@code leave:manage} alone does <em>not</em> skip a REPORTING_MANAGER step.
     */
    @Transactional(readOnly = true)
    public Employee assertCanApprove(Long actorEmployeeId, LeaveApproval step, LeaveApplication app) {
        Employee actor = requireActive(actorEmployeeId);
        if (!mayDecide(actor, step, app)) {
            StepRule rule = resolveStepRule(step);
            throw new IllegalStateException(
                    "Not allowed to approve step " + step.getStepOrder()
                            + " (role=" + rule.role() + ", resolver=" + rule.resolver()
                            + "). Waiting on the configured approver for this step.");
        }
        return actor;
    }

    /** Non-throwing check used by pending queues so HR does not see manager-step items. */
    @Transactional(readOnly = true)
    public boolean canApprove(Long actorEmployeeId, LeaveApproval step, LeaveApplication app) {
        if (actorEmployeeId == null || step == null || app == null) {
            return false;
        }
        Employee actor = employeeRepository.findById(actorEmployeeId).orElse(null);
        if (actor == null || !actor.isActive()) {
            return false;
        }
        return mayDecide(actor, step, app);
    }

    private boolean mayDecide(Employee actor, LeaveApproval step, LeaveApplication app) {
        StepRule rule = resolveStepRule(step);
        if (Boolean.TRUE.equals(rule.allowOverride()) && isOverride(actor.getId())) {
            return true;
        }
        if (matchesResolver(actor, app, rule)) {
            return true;
        }
        // Check delegate covering: if an expected approver has delegated to actor today, allow.
        Long expectedId = step.getExpectedApproverId();
        if (expectedId != null) {
            return isDelegateFor(actor.getId(), expectedId);
        }
        return false;
    }

    /** True when actor is an active delegate covering today for expectedApproverId. */
    private boolean isDelegateFor(Long actorId, Long expectedApproverId) {
        return delegateRepository.findActiveDelegate(expectedApproverId, LocalDate.now())
                .map(d -> d.getDelegateEmployeeId().equals(actorId))
                .orElse(false);
    }

    private boolean matchesResolver(Employee actor, LeaveApplication app, StepRule rule) {
        String resolver = rule.resolver() == null ? RESOLVER_DESIGNATION_OR_RBAC : rule.resolver().toUpperCase(Locale.ROOT);
        return switch (resolver) {
            case RESOLVER_REPORTING_MANAGER -> isReportingManager(actor, app);
            case RESOLVER_REPORTING_MANAGER_AND_DESIGNATION ->
                    isReportingManager(actor, app) && designationMatches(actor, rule.role());
            case RESOLVER_RBAC_ROLE -> holdsAnyRole(actor.getId(), rule.roleCodes(), rule.role());
            case RESOLVER_DESIGNATION_OR_RBAC ->
                    designationMatches(actor, rule.role()) || holdsAnyRole(actor.getId(), rule.roleCodes(), rule.role());
            case RESOLVER_MANAGER_OF_MANAGER -> isManagerOfManager(actor, app);
            case RESOLVER_NAMED_EMPLOYEE -> isNamedEmployee(actor, app, rule);
            default -> throw new IllegalStateException("Unknown approval resolver: " + resolver);
        };
    }

    /** MoM: actor is the manager of the applicant's direct manager. */
    private boolean isManagerOfManager(Employee actor, LeaveApplication app) {
        Employee applicant = employeeRepository.findById(app.getEmployeeId()).orElse(null);
        if (applicant == null || applicant.getManagerId() == null) {
            return false;
        }
        Employee manager = employeeRepository.findById(applicant.getManagerId()).orElse(null);
        return manager != null && manager.getManagerId() != null && manager.getManagerId().equals(actor.getId());
    }

    /** NAMED_EMPLOYEE: actor.id == step.namedEmployeeId OR step.expectedApproverId. */
    private boolean isNamedEmployee(Employee actor, LeaveApplication app, StepRule rule) {
        // step.namedEmployeeId is embedded in the rule's role field as we pass it through; look via step directly.
        // We rely on the step's expectedApproverId being set during creation.
        // The rule itself doesn't carry namedEmployeeId — check via app's approvals is complex here,
        // so we use the actor's own id against rule.namedEmployeeId if provided.
        if (rule.namedEmployeeId() != null && rule.namedEmployeeId().equals(actor.getId())) {
            return true;
        }
        if (rule.expectedApproverId() != null && rule.expectedApproverId().equals(actor.getId())) {
            return true;
        }
        return false;
    }

    private boolean isReportingManager(Employee actor, LeaveApplication app) {
        Employee applicant = employeeRepository.findById(app.getEmployeeId()).orElse(null);
        return applicant != null
                && applicant.getManagerId() != null
                && applicant.getManagerId().equals(actor.getId());
    }

    private boolean designationMatches(Employee actor, String requiredRole) {
        if (requiredRole == null || requiredRole.isBlank()) {
            return false;
        }
        return designationApproverRole(actor)
                .map(r -> r.equalsIgnoreCase(requiredRole))
                .orElse(false);
    }

    private boolean holdsAnyRole(Long employeeId, List<String> roleCodes, String fallbackRole) {
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
            want.add(fallbackRole.toUpperCase(Locale.ROOT));
        }
        for (String w : want) {
            if (held.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOverride(Long employeeId) {
        if (rbacService.hasWildcard(employeeId)) {
            return true;
        }
        Map<String, Object> auth = approvalAuthoritySettings();
        // Optional permission list from pack — empty by default so leave:manage (HR setup) ≠ step skip.
        Object permsRaw = auth.get("overridePermissions");
        if (permsRaw instanceof Collection<?> perms) {
            for (Object p : perms) {
                if (p == null) {
                    continue;
                }
                String token = String.valueOf(p).trim().toLowerCase(Locale.ROOT);
                int colon = token.indexOf(':');
                if (colon > 0 && colon < token.length() - 1) {
                    if (rbacService.hasPermission(employeeId, token.substring(0, colon), token.substring(colon + 1))) {
                        return true;
                    }
                }
            }
        }
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

    @SuppressWarnings("unchecked")
    StepRule resolveStepRule(LeaveApproval step) {
        String storedRole = step.getApproverRole() == null ? "" : step.getApproverRole().trim();
        // If the step itself has a resolver stored (set during creation), prefer it.
        String stepResolver = step.getResolver();
        // Prefer policy chain row that matches this step's role (category chains may differ from policy).
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
        Boolean allowOverride = firstBool(
                chainItem.get("allowOverride"),
                fromRole.get("allowOverride"),
                fromStar.get("allowOverride"),
                Boolean.FALSE);
        List<String> roleCodes = firstRoleCodes(chainItem.get("roleCodes"), fromRole.get("roleCodes"), fromStar.get("roleCodes"));
        // Stored step role wins — never let a mismatched policy row rename the step.
        String role = firstString(storedRole, chainItem.get("role"), "");
        return new StepRule(role, resolver.toUpperCase(Locale.ROOT), allowOverride, roleCodes,
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
        // Ensure wildcard fallback exists
        if (!out.containsKey("*")) {
            out.put("*", Map.of("resolver", RESOLVER_DESIGNATION_OR_RBAC, "allowOverride", false));
        }
        return out;
    }

    /** Platform-neutral defaults so tenants without pack settings still behave sanely. */
    static Map<String, Object> defaultApprovalAuthority() {
        Map<String, Object> roleDefaults = new HashMap<>();
        roleDefaults.put("MANAGER", Map.of("resolver", RESOLVER_REPORTING_MANAGER, "allowOverride", true));
        roleDefaults.put("MANAGER_OF_MANAGER", Map.of("resolver", RESOLVER_MANAGER_OF_MANAGER, "allowOverride", true));
        roleDefaults.put("STORE_MANAGER", Map.of(
                "resolver", RESOLVER_REPORTING_MANAGER_AND_DESIGNATION, "allowOverride", true));
        roleDefaults.put("ADMIN", Map.of(
                "resolver", RESOLVER_RBAC_ROLE,
                "roleCodes", List.of("ADMIN", "SUPERADMIN"),
                "allowOverride", true));
        roleDefaults.put("SUPER_ADMIN", Map.of(
                "resolver", RESOLVER_RBAC_ROLE,
                "roleCodes", List.of("SUPERADMIN"),
                "allowOverride", true));
        roleDefaults.put("*", Map.of("resolver", RESOLVER_DESIGNATION_OR_RBAC, "allowOverride", false));
        Map<String, Object> auth = new HashMap<>();
        auth.put("overrideRoleCodes", List.of("SUPERADMIN", "ADMIN"));
        auth.put("roleDefaults", roleDefaults);
        return auth;
    }

    private java.util.Optional<String> designationApproverRole(Employee employee) {
        if (employee.getDesignationId() == null) {
            return java.util.Optional.empty();
        }
        return designationRepository.findById(employee.getDesignationId())
                .map(Designation::getApproverRole)
                .filter(r -> r != null && !r.isBlank());
    }

    private Employee requireActive(Long id) {
        Employee e = employeeRepository.findById(id)
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

    private static Boolean firstBool(Object... values) {
        for (Object v : values) {
            if (v instanceof Boolean b) {
                return b;
            }
            if (v != null) {
                String s = String.valueOf(v).trim();
                if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
                    return Boolean.parseBoolean(s);
                }
            }
        }
        return Boolean.FALSE;
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

    record StepRule(String role, String resolver, Boolean allowOverride, List<String> roleCodes,
                    Long namedEmployeeId, Long expectedApproverId) {}
}
