package com.platform.hrms.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Server-side RBAC gate — enforced by {@link RbacInterceptor}.
 *
 * <p>When {@link #allowInternal()} is true, trusted service-to-service callers presenting
 * {@code X-Internal-Key} may skip the employee-identity RBAC check (system batch jobs such
 * as payroll facts). User requests still require a real employee with the feature:action.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    String feature();
    String action();

    /** Allow trusted internal (service-to-service) callers without an employee identity. */
    boolean allowInternal() default false;
}
