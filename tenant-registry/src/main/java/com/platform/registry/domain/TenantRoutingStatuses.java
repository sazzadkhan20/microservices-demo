package com.platform.registry.domain;

/**
 * Control-plane routing statuses. Domain services only open pools for {@link #ACTIVE}.
 */
public final class TenantRoutingStatuses {

    /** Tenant DB is on the shared product schema and may receive traffic. */
    public static final String ACTIVE = "ACTIVE";

    /**
     * Liquibase fleet migrate failed for this (service, tenant).
     * Redis L2 is updated so domain services refuse the tenant until migrate succeeds.
     */
    public static final String SCHEMA_OUTDATED = "SCHEMA_OUTDATED";

    private TenantRoutingStatuses() {
    }

    public static boolean isActive(String status) {
        return ACTIVE.equalsIgnoreCase(status);
    }
}
