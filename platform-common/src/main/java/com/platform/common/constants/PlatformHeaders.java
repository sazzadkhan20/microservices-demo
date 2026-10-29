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

    private PlatformHeaders() {
    }
}
