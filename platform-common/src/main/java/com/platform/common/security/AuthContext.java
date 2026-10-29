package com.platform.common.security;

/**
 * Request-scoped identity after Gateway JWT verification.
 * Tenant is always set; employeeId is set for product sessions (account picker / future OAuth).
 */
public final class AuthContext {

    private static final ThreadLocal<Long> EMPLOYEE = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setEmployeeId(Long employeeId) {
        EMPLOYEE.set(employeeId);
    }

    public static Long getEmployeeId() {
        return EMPLOYEE.get();
    }

    public static Long requireEmployeeId() {
        Long id = EMPLOYEE.get();
        if (id == null) {
            throw new IllegalStateException("Authenticated employee required");
        }
        return id;
    }

    public static void clear() {
        EMPLOYEE.remove();
    }
}
