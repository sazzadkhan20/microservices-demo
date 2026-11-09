package com.platform.common.tenant;

/**
 * SEC-05 — JDBC passwords must not live in Redis. Prefer the routing payload password
 * (Registry L3 only), otherwise the service-local secret from env / secrets manager.
 */
public final class TenantDbCredentials {

    private TenantDbCredentials() {
    }

    public static String resolvePassword(String routingPassword, String localPassword) {
        if (routingPassword != null && !routingPassword.isBlank()) {
            return routingPassword;
        }
        if (localPassword != null && !localPassword.isBlank()) {
            return localPassword;
        }
        throw new IllegalStateException(
                "No JDBC password available: Redis routing is passwordless (SEC-05) and "
                        + "platform.datasource.password / *_PG_PASSWORD is unset");
    }
}
