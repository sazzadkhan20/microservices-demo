package com.platform.common.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Standard cross-service event envelope (Developer Guide §7).
 * Services publish after local outbox commit; consumers dedupe via inbox.
 */
public record PlatformEvent(
        String eventId,
        String eventType,
        int schemaVersion,
        String aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        String correlationId,
        String tenantId,
        Map<String, Object> payload
) {
    public static PlatformEvent of(
            String eventType,
            String aggregateId,
            long aggregateVersion,
            String correlationId,
            String tenantId,
            Map<String, Object> payload) {
        return new PlatformEvent(
                UUID.randomUUID().toString(),
                eventType,
                1,
                aggregateId,
                aggregateVersion,
                Instant.now(),
                correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId,
                tenantId,
                payload == null ? Map.of() : payload);
    }
}
