package com.platform.leave.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Category approval-chain normalize + validate (FR-LV-12).
 * Default: direct manager → ADMIN. Max 4 steps.
 */
public final class ApprovalChainSupport {

    public static final int MAX_STEPS = 4;

    public static final Set<String> KNOWN_ROLES = Set.of(
            "MANAGER",
            "MANAGER_OF_MANAGER",
            "STORE_MANAGER",
            "HR",
            "ADMIN",
            "SUPER_ADMIN",
            "SUPERADMIN",
            "NAMED_EMPLOYEE");

    private ApprovalChainSupport() {}

    /** Production default: Manager → Admin (named Fayaz/admin via RBAC ADMIN). */
    public static List<Map<String, Object>> defaultChain() {
        List<Map<String, Object>> chain = new ArrayList<>();
        chain.add(step(1, "MANAGER", ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER, null, null));
        chain.add(step(2, "ADMIN", ApprovalAuthorityService.RESOLVER_RBAC_ROLE,
                List.of("ADMIN", "SUPERADMIN"), null));
        return chain;
    }

    public static String defaultChainJson() {
        return toJson(defaultChain());
    }

    /**
     * Normalize + validate a parsed chain. Throws {@link IllegalArgumentException} on violation.
     */
    public static List<Map<String, Object>> normalizeAndValidate(List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return defaultChain();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int i = 1;
        for (Map<String, Object> item : raw) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            if (out.size() >= MAX_STEPS) {
                throw new IllegalArgumentException(
                        "Approval chain may have at most " + MAX_STEPS + " steps");
            }
            Map<String, Object> row = new HashMap<>(item);
            String role = str(row.get("role")).toUpperCase(Locale.ROOT);
            if (role.isEmpty()) {
                role = "MANAGER";
            }
            // Allow NAMED:<id> shorthand from FE text editor
            Long namedId = longOrNull(row.get("namedEmployeeId"));
            if (role.startsWith("NAMED:") || role.startsWith("EMP:")) {
                String idPart = role.substring(role.indexOf(':') + 1).trim();
                try {
                    namedId = Long.parseLong(idPart);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Named step needs a numeric employee id: " + role);
                }
                role = "NAMED_EMPLOYEE";
            }
            String resolver = str(row.get("resolver")).toUpperCase(Locale.ROOT);
            if (resolver.isEmpty()) {
                resolver = defaultResolverForRole(role);
            }
            validateResolver(resolver);
            if (ApprovalAuthorityService.RESOLVER_NAMED_EMPLOYEE.equals(resolver)
                    || "NAMED_EMPLOYEE".equals(role)) {
                if (namedId == null || namedId <= 0) {
                    throw new IllegalArgumentException(
                            "NAMED_EMPLOYEE step requires namedEmployeeId");
                }
                role = "NAMED_EMPLOYEE";
                resolver = ApprovalAuthorityService.RESOLVER_NAMED_EMPLOYEE;
            } else if (!KNOWN_ROLES.contains(role) && !resolver.equals(ApprovalAuthorityService.RESOLVER_RBAC_ROLE)
                    && !resolver.equals(ApprovalAuthorityService.RESOLVER_DESIGNATION_OR_RBAC)) {
                // Custom role labels are allowed only as RBAC / designation pools
                resolver = ApprovalAuthorityService.RESOLVER_RBAC_ROLE;
            }

            @SuppressWarnings("unchecked")
            List<String> roleCodes = roleCodesOf(row.get("roleCodes"));
            if (roleCodes.isEmpty() && ApprovalAuthorityService.RESOLVER_RBAC_ROLE.equals(resolver)) {
                roleCodes = defaultRoleCodes(role);
            }

            Map<String, Object> normalized = step(i, role, resolver, roleCodes, namedId);
            // Reject exact consecutive duplicates (same role + named id)
            if (!out.isEmpty()) {
                Map<String, Object> prev = out.get(out.size() - 1);
                if (sameStepIdentity(prev, normalized)) {
                    throw new IllegalArgumentException(
                            "Approval chain must not repeat the same step consecutively (" + role + ")");
                }
            }
            out.add(normalized);
            i++;
        }
        if (out.isEmpty()) {
            return defaultChain();
        }
        return out;
    }

    public static List<Map<String, Object>> parseAndValidateJson(String json) {
        if (json == null || json.isBlank()) {
            return defaultChain();
        }
        try {
            Object parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
            if (!(parsed instanceof List<?> list) || list.isEmpty()) {
                return defaultChain();
            }
            List<Map<String, Object>> raw = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> row = new HashMap<>();
                    m.forEach((k, v) -> {
                        if (k != null) row.put(String.valueOf(k), v);
                    });
                    if (!row.isEmpty()) raw.add(row);
                } else if (o instanceof String s && !s.isBlank()) {
                    raw.add(Map.of("role", s.trim()));
                }
            }
            return normalizeAndValidate(raw);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid approvalChain JSON: " + e.getMessage());
        }
    }

    public static String toJson(List<Map<String, Object>> chain) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(chain);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize approval chain", e);
        }
    }

    public static String defaultResolverForRole(String role) {
        return switch (role.toUpperCase(Locale.ROOT)) {
            case "MANAGER" -> ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER;
            case "MANAGER_OF_MANAGER" -> ApprovalAuthorityService.RESOLVER_MANAGER_OF_MANAGER;
            case "STORE_MANAGER" -> ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER_AND_DESIGNATION;
            case "NAMED_EMPLOYEE" -> ApprovalAuthorityService.RESOLVER_NAMED_EMPLOYEE;
            case "ADMIN", "SUPER_ADMIN", "SUPERADMIN", "HR" -> ApprovalAuthorityService.RESOLVER_RBAC_ROLE;
            default -> ApprovalAuthorityService.RESOLVER_RBAC_ROLE;
        };
    }

    private static List<String> defaultRoleCodes(String role) {
        return switch (role.toUpperCase(Locale.ROOT)) {
            case "ADMIN" -> List.of("ADMIN", "SUPERADMIN");
            case "SUPER_ADMIN", "SUPERADMIN" -> List.of("SUPERADMIN");
            case "HR" -> List.of("HR");
            default -> role == null || role.isBlank() ? List.of() : List.of(role.toUpperCase(Locale.ROOT));
        };
    }

    private static void validateResolver(String resolver) {
        Set<String> ok = Set.of(
                ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER,
                ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER_AND_DESIGNATION,
                ApprovalAuthorityService.RESOLVER_MANAGER_OF_MANAGER,
                ApprovalAuthorityService.RESOLVER_NAMED_EMPLOYEE,
                ApprovalAuthorityService.RESOLVER_RBAC_ROLE,
                ApprovalAuthorityService.RESOLVER_DESIGNATION_OR_RBAC);
        if (!ok.contains(resolver)) {
            throw new IllegalArgumentException("Unknown approval resolver: " + resolver);
        }
    }

    private static boolean sameStepIdentity(Map<String, Object> a, Map<String, Object> b) {
        String ra = str(a.get("role")).toUpperCase(Locale.ROOT);
        String rb = str(b.get("role")).toUpperCase(Locale.ROOT);
        Long na = longOrNull(a.get("namedEmployeeId"));
        Long nb = longOrNull(b.get("namedEmployeeId"));
        return ra.equals(rb) && ((na == null && nb == null) || (na != null && na.equals(nb)));
    }

    private static Map<String, Object> step(
            int n, String role, String resolver, List<String> roleCodes, Long namedId) {
        Map<String, Object> m = new HashMap<>();
        m.put("step", n);
        m.put("role", role);
        m.put("resolver", resolver);
        if (roleCodes != null && !roleCodes.isEmpty()) {
            m.put("roleCodes", roleCodes);
        }
        if (namedId != null) {
            m.put("namedEmployeeId", namedId);
        }
        // On-behalf is system-gated by idle days — not per-step allowOverride anymore.
        m.put("allowOverride", false);
        return m;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static Long longOrNull(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return null;
        try {
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) return null;
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> roleCodesOf(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Object o : list) {
            if (o != null && !String.valueOf(o).isBlank()) {
                out.add(String.valueOf(o).trim().toUpperCase(Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }
}
