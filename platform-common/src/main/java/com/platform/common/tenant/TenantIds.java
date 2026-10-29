package com.platform.common.tenant;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates and normalizes tenant identifiers used in DB names and Redis keys.
 *
 * <p>Every product service uses database-per-tenant naming (config-driven, reusable):
 * {@code hrms_*} / {@code payroll_*} / {@code files_*} / {@code notif_*} / {@code reports_*}.
 * Only lowercase letters, digits, and underscore are allowed after normalization.
 */
public final class TenantIds {

    private static final Pattern SAFE = Pattern.compile("^[a-z][a-z0-9_]{1,47}$");

    private TenantIds() {
    }

    /**
     * Normalizes raw tenant id: trim, lower-case, replace '-' with '_'.
     *
     * @throws IllegalArgumentException if the result is not a safe identifier
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!SAFE.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Invalid tenantId '" + raw + "'. Use letters, digits, underscore; start with a letter; max 48 chars.");
        }
        return normalized;
    }

    public static String hrmsDbName(String tenantId) {
        return "hrms_" + normalize(tenantId);
    }

    public static String payrollDbName(String tenantId) {
        return "payroll_" + normalize(tenantId);
    }

    public static String filesDbName(String tenantId) {
        return "files_" + normalize(tenantId);
    }

    public static String notificationDbName(String tenantId) {
        return "notif_" + normalize(tenantId);
    }

    public static String reportsDbName(String tenantId) {
        return "reports_" + normalize(tenantId);
    }

    /**
     * MinIO/S3 bucket name — DNS-safe labels only (lowercase letters, digits, hyphens).
     * Tenant ids use underscores for DBs ({@code qii_creative}); buckets use hyphens ({@code t-qii-creative}).
     */
    public static String filesBucket(String tenantId) {
        return "t-" + normalize(tenantId).replace('_', '-');
    }
}
