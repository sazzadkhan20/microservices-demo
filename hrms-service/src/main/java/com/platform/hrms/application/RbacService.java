package com.platform.hrms.application;

import com.platform.common.security.AuthContext;
import com.platform.hrms.domain.rbac.AppPermission;
import com.platform.hrms.domain.rbac.AppRole;
import com.platform.hrms.domain.rbac.AppRolePermission;
import com.platform.hrms.domain.rbac.AppUserRole;
import com.platform.hrms.infrastructure.persistence.rbac.AppPermissionRepository;
import com.platform.hrms.infrastructure.persistence.rbac.AppRolePermissionRepository;
import com.platform.hrms.infrastructure.persistence.rbac.AppRoleRepository;
import com.platform.hrms.infrastructure.persistence.rbac.AppUserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Config-driven RBAC: roles × permissions × data scope + role policy flags from tenant DB.
 * Business packs configure flags; this service never branches on a hard-coded role name.
 * <p>
 * Prototype-granular matrix keys (directory, leave_requests, …) are aliased to coarse API
 * keys (employees, leave, …) so {@code @RequiresPermission} keeps working.
 */
@Service
public class RbacService {

    /**
     * Coarse API key → any of these matrix keys (or the coarse key itself) satisfies it.
     * Kept here as compatibility glue; matrix catalog remains the editable surface.
     */
    private static final Map<String, List<String>> API_ALIASES = Map.ofEntries(
            Map.entry("employees:view", List.of(
                    "employees:view", "directory:view", "profile_personal:view", "profile_employment:view", "documents:view")),
            Map.entry("employees:create", List.of(
                    "employees:create", "profile_employment:create")),
            Map.entry("employees:edit", List.of(
                    "employees:edit", "profile_personal:edit", "profile_employment:edit", "documents:create", "documents:delete")),
            Map.entry("attendance:view", List.of("attendance:view")),
            Map.entry("attendance:checkin", List.of("attendance:checkin", "attendance:edit", "attendance:view")),
            Map.entry("attendance:correct", List.of("attendance:correct", "attendance:edit")),
            Map.entry("leave:view", List.of(
                    "leave:view", "leave_requests:view", "leave_balances:view", "leave_setup:view")),
            Map.entry("leave:request", List.of("leave:request", "leave_requests:create")),
            Map.entry("leave:approve", List.of("leave:approve", "leave_requests:approve")),
            Map.entry("leave:manage", List.of(
                    "leave:manage", "leave_setup:view", "leave_setup:create", "leave_setup:edit", "leave_setup:delete",
                    "leave_balances:edit")),
            Map.entry("roles:view", List.of("roles:view")),
            Map.entry("roles:assign", List.of("roles:assign", "roles:edit")),
            // Matrix mutate only — roles:edit maps to assign, not manage.
            Map.entry("roles:manage", List.of("roles:manage", "roles:create", "roles:delete")),
            // salary:view satisfies payroll:view checks; salary:edit does NOT grant payroll:run.
            Map.entry("payroll:view", List.of("payroll:view", "salary:view", "salary:edit")),
            Map.entry("payroll:run", List.of(
                    "payroll:run", "payroll:create", "payroll:approve", "payroll:export")),
            Map.entry("salary:view", List.of("salary:view", "salary:edit")),
            Map.entry("salary:edit", List.of("salary:edit")),
            Map.entry("reports:view", List.of("reports:view")),
            Map.entry("reports:export", List.of("reports:export")),
            Map.entry("notifications:view", List.of("notifications:view", "emails:view")),
            // Settings / leave_setup / holidays / attendance_settings stay on their own feature keys.
            Map.entry("settings:manage", List.of("settings:manage")),
            // Org structure (departments / teams / locations) — prototype hierarchy.edit only.
            // Do NOT alias holidays or schedules into org:manage (that let HR/Manager mutate org).
            Map.entry("org:view", List.of("org:view", "hierarchy:view")),
            Map.entry("org:manage", List.of("org:manage", "hierarchy:edit"))
    );

    private final AppUserRoleRepository userRoleRepository;
    private final AppRoleRepository roleRepository;
    private final AppRolePermissionRepository rolePermissionRepository;
    private final AppPermissionRepository permissionRepository;

    public RbacService(
            AppUserRoleRepository userRoleRepository,
            AppRoleRepository roleRepository,
            AppRolePermissionRepository rolePermissionRepository,
            AppPermissionRepository permissionRepository) {
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public List<AppRole> rolesFor(Long employeeId) {
        if (employeeId == null) {
            return List.of();
        }
        List<Long> roleIds = userRoleRepository.findByEmployeeId(employeeId).stream()
                .map(AppUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleRepository.findAllById(roleIds).stream()
                .filter(AppRole::isActive)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> roleCodesFor(Long employeeId) {
        return rolesFor(employeeId).stream().map(AppRole::getCode).toList();
    }

    @Transactional(readOnly = true)
    public boolean hasWildcard(Long employeeId) {
        return rolesFor(employeeId).stream().anyMatch(AppRole::isGrantsWildcard);
    }

    @Transactional(readOnly = true)
    public boolean isProtectedFromOffboard(Long employeeId) {
        return rolesFor(employeeId).stream().anyMatch(AppRole::isProtectFromOffboard);
    }

    @Transactional(readOnly = true)
    public Set<String> permissionKeysFor(Long employeeId) {
        List<AppRole> roles = rolesFor(employeeId);
        if (roles.isEmpty()) {
            return Set.of();
        }
        List<Long> roleIds = roles.stream().map(AppRole::getId).toList();
        List<AppRolePermission> rps = rolePermissionRepository.findByRoleIdIn(roleIds);
        Set<Long> permIds = rps.stream().map(AppRolePermission::getPermissionId).collect(Collectors.toSet());
        Set<String> keys = new HashSet<>();
        for (AppPermission p : permissionRepository.findAllById(permIds)) {
            keys.add(p.key().toLowerCase(Locale.ROOT));
        }
        if (roles.stream().anyMatch(AppRole::isGrantsWildcard)) {
            keys.add("*:*");
        }
        Set<String> withAliases = new HashSet<>(keys);
        for (String k : keys) {
            if (k.endsWith(":view")) {
                withAliases.add(k.substring(0, k.length() - 4) + "read");
            } else if (k.endsWith(":read")) {
                withAliases.add(k.substring(0, k.length() - 4) + "view");
            }
        }
        // Expand coarse API keys when any aliased matrix key is present.
        for (Map.Entry<String, List<String>> e : API_ALIASES.entrySet()) {
            for (String alt : e.getValue()) {
                if (withAliases.contains(alt.toLowerCase(Locale.ROOT))) {
                    withAliases.add(e.getKey().toLowerCase(Locale.ROOT));
                    break;
                }
            }
        }
        return withAliases;
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(Long employeeId, String feature, String action) {
        Set<String> keys = permissionKeysFor(employeeId);
        if (keys.contains("*:*")) {
            return true;
        }
        String want = (feature + ":" + action).toLowerCase(Locale.ROOT);
        if (keys.contains(want) || keys.contains((feature + ":*").toLowerCase(Locale.ROOT))) {
            return true;
        }
        List<String> alts = API_ALIASES.get(want);
        if (alts == null) {
            return false;
        }
        for (String alt : alts) {
            if (keys.contains(alt.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * After a matrix save, upsert legacy coarse permission rows so DB-backed grants match aliases.
     * Idempotent; skips when the coarse permission catalog entry does not exist.
     */
    @Transactional
    public void syncLegacyApiGrants(Long roleId) {
        List<AppRolePermission> links = rolePermissionRepository.findByRoleIdIn(List.of(roleId));
        Map<Long, AppPermission> byId = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(AppPermission::getId, p -> p));
        Set<String> held = new HashSet<>();
        Map<String, String> widest = new HashMap<>();
        for (AppRolePermission link : links) {
            AppPermission p = byId.get(link.getPermissionId());
            if (p == null) continue;
            String key = p.key().toLowerCase(Locale.ROOT);
            held.add(key);
            widest.merge(key, link.getDataScope(), RbacService::widerScope);
        }
        for (Map.Entry<String, List<String>> e : API_ALIASES.entrySet()) {
            String coarse = e.getKey();
            boolean hit = false;
            String scope = "OWN";
            for (String alt : e.getValue()) {
                String a = alt.toLowerCase(Locale.ROOT);
                if (held.contains(a)) {
                    hit = true;
                    scope = widerScope(scope, widest.getOrDefault(a, "ALL"));
                }
            }
            String[] fa = coarse.split(":", 2);
            if (fa.length != 2) continue;
            var permOpt = permissionRepository.findByFeatureAndAction(fa[0], fa[1]);
            if (permOpt.isEmpty()) continue;
            AppPermission coarsePerm = permOpt.get();
            AppRolePermission existing = links.stream()
                    .filter(l -> l.getPermissionId().equals(coarsePerm.getId()))
                    .findFirst()
                    .orElse(null);
            if (hit) {
                if (existing == null) {
                    AppRolePermission link = new AppRolePermission();
                    link.setRoleId(roleId);
                    link.setPermissionId(coarsePerm.getId());
                    link.setDataScope(scope);
                    rolePermissionRepository.save(link);
                } else {
                    existing.setDataScope(widerScope(existing.getDataScope(), scope));
                    rolePermissionRepository.save(existing);
                }
            }
        }
    }

    private static String widerScope(String a, String b) {
        return rank(b) > rank(a) ? b : a;
    }

    private static int rank(String scope) {
        if (scope == null) return 0;
        return switch (scope.toUpperCase(Locale.ROOT)) {
            case "ALL", "YES" -> 3;
            case "TEAM" -> 2;
            case "OWN" -> 1;
            default -> 0;
        };
    }

    /**
     * Widest data scope across roles. Config flag {@code grants_wildcard} ⇒ ALL.
     */
    @Transactional(readOnly = true)
    public String widestScope(Long employeeId, String feature, String action) {
        List<AppRole> roles = rolesFor(employeeId);
        if (roles.isEmpty()) {
            return "NONE";
        }
        if (roles.stream().anyMatch(AppRole::isGrantsWildcard)) {
            return "ALL";
        }
        List<Long> roleIds = roles.stream().map(AppRole::getId).toList();
        List<AppRolePermission> rps = rolePermissionRepository.findByRoleIdIn(roleIds);
        Set<Long> permIds = rps.stream().map(AppRolePermission::getPermissionId).collect(Collectors.toSet());
        java.util.Map<Long, AppPermission> perms = new java.util.HashMap<>();
        for (AppPermission p : permissionRepository.findAllById(permIds)) {
            perms.put(p.getId(), p);
        }
        String widest = "NONE";
        for (AppRolePermission rp : rps) {
            AppPermission p = perms.get(rp.getPermissionId());
            if (p == null || !permissionMatches(p, feature, action)) {
                continue;
            }
            String s = rp.getDataScope() == null ? "OWN" : rp.getDataScope().toUpperCase(Locale.ROOT);
            if ("ALL".equals(s)) {
                return "ALL";
            }
            if ("TEAM".equals(s)) {
                widest = "TEAM";
            } else if ("OWN".equals(s) && "NONE".equals(widest)) {
                widest = "OWN";
            }
        }
        return widest;
    }

    private static boolean permissionMatches(AppPermission p, String feature, String action) {
        String pf = p.getFeature() == null ? "" : p.getFeature().toLowerCase(Locale.ROOT);
        String pa = p.getAction() == null ? "" : p.getAction().toLowerCase(Locale.ROOT);
        String f = feature.toLowerCase(Locale.ROOT);
        String a = action.toLowerCase(Locale.ROOT);
        if ("*".equals(pf) && "*".equals(pa)) {
            return true;
        }
        if (!pf.equals(f)) {
            return false;
        }
        if ("*".equals(pa) || pa.equals(a)) {
            return true;
        }
        return ("view".equals(a) && "read".equals(pa)) || ("read".equals(a) && "view".equals(pa));
    }

    /**
     * Add one role to an employee (merge). Enforces assignable_by via {@link #assignRoles}.
     */
    @Transactional
    public List<String> addRole(Long employeeId, String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new IllegalArgumentException("roleCode is required");
        }
        List<String> next = new ArrayList<>(roleCodesFor(employeeId));
        String code = roleCode.trim();
        if (next.stream().noneMatch(c -> c.equalsIgnoreCase(code))) {
            next.add(code);
        }
        return assignRoles(employeeId, next);
    }

    /**
     * Remove one role from an employee. Enforces min_holders via {@link #assignRoles}.
     */
    @Transactional
    public List<String> removeRole(Long employeeId, String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new IllegalArgumentException("roleCode is required");
        }
        String code = roleCode.trim();
        List<String> next = roleCodesFor(employeeId).stream()
                .filter(c -> !c.equalsIgnoreCase(code))
                .toList();
        return assignRoles(employeeId, next);
    }

    /**
     * Apply tenant default roles on create without assignable_by gates (policy, not a user grant).
     * Still validates role codes exist and are active.
     */
    @Transactional
    public List<String> assignSystemDefaults(Long employeeId, List<String> roleCodes) {
        if (employeeId == null || roleCodes == null || roleCodes.isEmpty()) {
            return roleCodesFor(employeeId);
        }
        List<String> nextCodes = roleCodes.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        userRoleRepository.deleteByEmployeeId(employeeId);
        userRoleRepository.flush();
        for (String code : nextCodes) {
            AppRole role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown default create role: " + code));
            if (!role.isActive()) {
                throw new IllegalStateException("Default create role inactive: " + code);
            }
            AppUserRole link = new AppUserRole();
            link.setEmployeeId(employeeId);
            link.setRoleId(role.getId());
            userRoleRepository.save(link);
        }
        return roleCodesFor(employeeId);
    }

    /** Replace all roles for an employee — enforces assignable_by + min_holders from config. */
    @Transactional
    public List<String> assignRoles(Long employeeId, List<String> roleCodes) {
        List<AppRole> current = rolesFor(employeeId);
        List<String> nextCodes = roleCodes == null ? List.of() : roleCodes.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .toList();

        List<AppRole> nextRoles = new ArrayList<>();
        for (String code : nextCodes) {
            AppRole role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + code));
            if (!role.isActive()) {
                throw new IllegalStateException("Role inactive: " + code);
            }
            nextRoles.add(role);
        }

        Long actor = AuthContext.getEmployeeId();
        Set<String> actorCodes = actor == null ? Set.of() : new HashSet<>(roleCodesFor(actor));
        boolean actorWildcard = actor != null && hasWildcard(actor);

        for (AppRole role : nextRoles) {
            if (current.stream().anyMatch(r -> r.getId().equals(role.getId()))) {
                continue; // already held — no new assignment gate
            }
            assertCanAssign(actor, actorCodes, actorWildcard, role);
        }

        for (AppRole removed : current) {
            boolean stillHeld = nextRoles.stream().anyMatch(r -> r.getId().equals(removed.getId()));
            if (!stillHeld) {
                assertCanUnassign(removed);
            }
        }

        userRoleRepository.deleteByEmployeeId(employeeId);
        userRoleRepository.flush();
        for (AppRole role : nextRoles) {
            AppUserRole link = new AppUserRole();
            link.setEmployeeId(employeeId);
            link.setRoleId(role.getId());
            userRoleRepository.save(link);
        }
        return roleCodesFor(employeeId);
    }

    private void assertCanAssign(Long actor, Set<String> actorCodes, boolean actorWildcard, AppRole role) {
        String gate = role.getAssignableBy();
        if (gate == null || gate.isBlank()) {
            return;
        }
        Set<String> allowed = Arrays.stream(gate.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (allowed.isEmpty()) {
            return;
        }
        if (actorWildcard) {
            return;
        }
        boolean ok = actorCodes.stream().map(c -> c.toUpperCase(Locale.ROOT)).anyMatch(allowed::contains);
        if (!ok) {
            throw new IllegalStateException(
                    "Not allowed: role " + role.getCode() + " can only be assigned by " + gate);
        }
        if (actor == null) {
            throw new IllegalStateException("Not allowed: missing actor for restricted role assignment");
        }
    }

    private void assertCanUnassign(AppRole role) {
        if (role.getMinHolders() <= 0) {
            return;
        }
        long holders = userRoleRepository.findByRoleId(role.getId()).stream()
                .map(AppUserRole::getEmployeeId)
                .distinct()
                .count();
        if (holders <= role.getMinHolders()) {
            throw new IllegalStateException(
                    "Not allowed: role " + role.getCode() + " requires at least "
                            + role.getMinHolders() + " holder(s)");
        }
    }
}
