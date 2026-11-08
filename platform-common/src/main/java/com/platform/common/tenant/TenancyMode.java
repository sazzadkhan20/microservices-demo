package com.platform.common.tenant;

/**
 * DYNAMIC = registry/redis routing (SaaS). FIXED = single JDBC URL for one-company prod launch.
 */
public enum TenancyMode {
    DYNAMIC,
    FIXED;

    public static TenancyMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DYNAMIC;
        }
        return TenancyMode.valueOf(raw.trim().toUpperCase());
    }
}
