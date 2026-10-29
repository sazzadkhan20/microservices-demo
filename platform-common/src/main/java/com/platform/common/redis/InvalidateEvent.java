package com.platform.common.redis;

/**
 * Pub/Sub message published when routing or tenant config changes.
 * Subscribers invalidate Caffeine L1 and reload from Redis L2.
 */
public record InvalidateEvent(
        String service,
        String tenantId,
        long version
) {
}
