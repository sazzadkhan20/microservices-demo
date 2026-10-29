package com.platform.payroll.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Server-side RBAC gate — enforced by {@link RbacInterceptor}. Same contract as HRMS
 * {@code RequiresPermission}; permissions are resolved from HRMS (gRPC) for the authenticated employee.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    String feature();
    String action();
}
