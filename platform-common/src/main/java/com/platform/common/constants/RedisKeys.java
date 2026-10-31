package com.platform.common.constants;

/**
 * Redis key + pub/sub channel contracts shared by Registry and domain services.
 *
 * <p>Steady-state request path uses L1 (Caffeine) → L2 (these keys).
 * Registry writes keys and publishes invalidate events; services subscribe.
 */
public final class RedisKeys {

    /** Routing payload: jdbcUrl, username, password, status, version */
    public static final String ROUTING_PREFIX = "tenant-routing:";

    /** Monotonic version per service (startup / reconnect safety net). */
    public static final String VERSION_PREFIX = "tenant-routing:version:";

    /** Optional mirrored tenant business config (future / optional L2). */
    public static final String CONFIG_PREFIX = "tenant-config:";

    /**
     * Pub/Sub channel. Payload JSON:
     * {@code {"service":"hrms","tenantId":"acme","version":3}}
     */
    public static final String INVALIDATE_CHANNEL = "tenant-routing:invalidate";

    /**
     * Redis SET NX lock so only one registry replica runs fleet Liquibase at a time.
     */
    public static final String FLEET_SCHEMA_MIGRATE_LOCK = "platform:fleet-schema-migrate:lock";

    /**
     * Written when a fleet migrate finishes successfully (value = epoch millis).
     * Follower replicas wait on this key instead of re-running Liquibase.
     */
    public static final String FLEET_SCHEMA_MIGRATE_DONE = "platform:fleet-schema-migrate:done";

    private RedisKeys() {
    }

    public static String routingKey(String service, String tenantId) {
        return ROUTING_PREFIX + service + ":" + tenantId;
    }

    public static String versionKey(String service) {
        return VERSION_PREFIX + service;
    }

    public static String configKey(String service, String tenantId) {
        return CONFIG_PREFIX + service + ":" + tenantId;
    }
}
