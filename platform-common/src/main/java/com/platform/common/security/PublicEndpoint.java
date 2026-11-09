package com.platform.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SEC-14 — marks a handler as intentionally public (or authenticated-without-feature permission).
 * Handlers without {@code @RequiresPermission} or {@code @PublicEndpoint} are denied by default.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {
    String reason() default "";
}
