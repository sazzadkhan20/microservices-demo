package com.platform.common.constants;

/**
 * HTTP / gRPC header names used across the platform.
 *
 * <p><b>Trust rule:</b> clients must NEVER set {@link #TENANT_ID} or {@link #EMPLOYEE_ID}.
 * Only the API Gateway injects these after JWT verification.
 */
public final class PlatformHeaders {

    public static final String TENANT_ID = "X-Tenant-ID";
    public static final String EMPLOYEE_ID = "X-Employee-ID";
    public static final String GRPC_TENANT_ID = "x-tenant-id";
    public static final String GRPC_EMPLOYEE_ID = "x-employee-id";
    public static final String AUTHORIZATION = "Authorization";

    /**
     * Shared secret proving a request came through the Gateway (or another trusted internal caller).
     * The Gateway strips any client-supplied copy and sets its own; downstream services reject
     * requests without a matching value.
     */
    public static final String SERVICE_KEY = "X-Service-Key";
    /** Shared secret for the demo account-picker endpoints (token mint / account list). */
    public static final String DEMO_AUTH_KEY = "X-Demo-Auth-Key";
    public static final String GRPC_SERVICE_KEY = "x-service-key";

    private PlatformHeaders() {
    }
}
