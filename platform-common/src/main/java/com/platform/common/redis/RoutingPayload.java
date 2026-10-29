package com.platform.common.redis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * JSON payload stored under {@code tenant-routing:{service}:{tenantId}}.
 * Services deserialize this to build a Hikari DataSource at runtime.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoutingPayload(
        String jdbcUrl,
        String username,
        String password,
        String status,
        long version
) {
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
