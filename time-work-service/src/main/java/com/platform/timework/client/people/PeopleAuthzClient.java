package com.platform.timework.client.people;

import com.platform.common.people.PeopleDirectoryClient;
import com.platform.common.security.HrmsPermissionClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Time&amp;Work RBAC via People directory API — no local app_* tables (guide §3).
 */
@Service
public class PeopleAuthzClient {

    private final PeopleDirectoryClient people;

    public PeopleAuthzClient(PeopleDirectoryClient people) {
        this.people = people;
    }

    public List<String> roleCodesFor(Long employeeId) {
        Object raw = people.authz(employeeId).get("roleCodes");
        if (!(raw instanceof Collection<?> col)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : col) {
            if (o != null && !String.valueOf(o).isBlank()) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    public boolean hasWildcard(Long employeeId) {
        Object v = people.authz(employeeId).get("grantsWildcard");
        return v instanceof Boolean b && b;
    }

    public Set<String> permissionKeysFor(Long employeeId) {
        Object raw = people.authz(employeeId).get("permissions");
        Set<String> out = new HashSet<>();
        if (raw instanceof Collection<?> col) {
            for (Object o : col) {
                if (o != null) {
                    out.add(String.valueOf(o).toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    public boolean hasPermission(Long employeeId, String feature, String action) {
        return HrmsPermissionClient.grants(permissionKeysFor(employeeId), feature, action);
    }

    public String widestScope(Long employeeId, String feature, String action) {
        if (hasWildcard(employeeId)) {
            return "ALL";
        }
        Object scopesRaw = people.authz(employeeId).get("permissionScopes");
        if (!(scopesRaw instanceof Map<?, ?> scopes)) {
            return hasPermission(employeeId, feature, action) ? "OWN" : "NONE";
        }
        String want = (feature + ":" + action).toLowerCase(Locale.ROOT);
        String widest = "NONE";
        for (Map.Entry<?, ?> e : scopes.entrySet()) {
            String key = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
            if (!keyEqualsOrAlias(key, want, feature, action)) {
                continue;
            }
            String scope = e.getValue() == null ? "OWN" : String.valueOf(e.getValue()).toUpperCase(Locale.ROOT);
            if ("ALL".equals(scope)) {
                return "ALL";
            }
            if ("TEAM".equals(scope)) {
                widest = "TEAM";
            } else if ("OWN".equals(scope) && "NONE".equals(widest)) {
                widest = "OWN";
            }
        }
        if ("NONE".equals(widest) && hasPermission(employeeId, feature, action)) {
            return "OWN";
        }
        return widest;
    }

    private static boolean keyEqualsOrAlias(String key, String want, String feature, String action) {
        if (key.equals(want) || key.equals(feature.toLowerCase(Locale.ROOT) + ":*") || key.equals("*:*")) {
            return true;
        }
        if ("view".equalsIgnoreCase(action) && key.equals(feature.toLowerCase(Locale.ROOT) + ":read")) {
            return true;
        }
        return "read".equalsIgnoreCase(action) && key.equals(feature.toLowerCase(Locale.ROOT) + ":view");
    }
}
