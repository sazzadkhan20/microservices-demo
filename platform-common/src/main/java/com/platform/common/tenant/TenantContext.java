package com.platform.common.tenant;

/**
 * Thread-local holder for the current request's tenant id.
 *
 * <p><b>CRITICAL:</b> Always clear in a {@code finally} block (servlet filter / gRPC interceptor).
 * Leaking tenant id across threads causes cross-tenant data access — a severe production bug.
 *
 * <p>SOLID: single responsibility — only stores/clears identity; routing & business logic live elsewhere.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static String getTenantId() {
        return CURRENT.get();
    }

    /** Must be called in finally after every request / gRPC call. */
    public static void clear() {
        CURRENT.remove();
    }
}
