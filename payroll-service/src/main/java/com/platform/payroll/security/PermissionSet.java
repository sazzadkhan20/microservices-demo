package com.platform.payroll.security;

import java.util.Locale;
import java.util.Set;

/**
 * Effective permission keys for the current request's employee (as resolved by HRMS).
 * Matching rules mirror HRMS {@code RbacService.hasPermission}: {@code *:*}, {@code feature:*}, exact key.
 */
public record PermissionSet(Set<String> keys) {

    public boolean has(String feature, String action) {
        if (keys.contains("*:*")) {
            return true;
        }
        return keys.contains((feature + ":" + action).toLowerCase(Locale.ROOT))
                || keys.contains((feature + ":*").toLowerCase(Locale.ROOT));
    }
}
