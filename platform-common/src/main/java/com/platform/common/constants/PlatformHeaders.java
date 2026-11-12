package com.platform.common.constants;

/**
 * HTTP / gRPC header names used across the platform.
 *
 * <p><b>Trust rule:</b> clients must NEVER set {@link #EMPLOYEE_ID}.
 * Only the API Gateway injects these after JWT verification.
 */
public final class PlatformHeaders {

    public static final String EMPLOYEE_ID = "X-Employee-ID";
    public static final String GRPC_EMPLOYEE_ID = "x-employee-id";
    public static final String AUTHORIZATION = "Authorization";

    /**
     * Shared secret proving a request came through the Gateway (or another trusted internal caller).
     * The Gateway strips any client-supplied copy and sets its own; downstream services reject
     * requests without a matching value.
     */
    public static final String SERVICE_KEY = "X-Service-Key";
    /** Operator admin secret (administrative session revoke). */
    public static final String ADMIN_KEY = "X-Platform-Admin-Key";
    /** Shared secret for the demo account-picker endpoints (token mint / account list). */
    public static final String DEMO_AUTH_KEY = "X-Demo-Auth-Key";
    /**
     * Shared secret for BFF → gateway OIDC session mint ({@code POST /api/auth/oidc/token}).
     * Held only by the Next.js BFF; never sent by the browser.
     */
    public static final String OIDC_BFF_KEY = "X-Oidc-Bff-Key";
    public static final String GRPC_SERVICE_KEY = "x-service-key";

    /** Propagated across HTTP / gRPC / broker messages (guide §7 / §9). */
    public static final String CORRELATION_ID = "X-Correlation-ID";
    public static final String GRPC_CORRELATION_ID = "x-correlation-id";

    private PlatformHeaders() {
    }
}
