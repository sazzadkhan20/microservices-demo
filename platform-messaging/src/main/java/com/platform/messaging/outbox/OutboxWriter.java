package com.platform.messaging.outbox;

import com.platform.common.messaging.PlatformEvent;
import com.platform.common.tenant.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists a {@link PlatformEvent} into the local outbox in the same DB transaction
 * as the business write (guide §7). The {@link OutboxRelay} publishes after commit.
 */
@Component
public class OutboxWriter {

    private final MessageOutboxRepository repository;

    public OutboxWriter(MessageOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public MessageOutbox enqueue(PlatformEvent event, String routingKey) {
        if (event == null) {
            throw new IllegalArgumentException("event required");
        }
        if (routingKey == null || routingKey.isBlank()) {
            throw new IllegalArgumentException("routingKey required");
        }
        String tenant = event.tenantId();
        if (tenant == null || tenant.isBlank()) {
            tenant = TenantContext.getTenantId();
        }
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalStateException("tenantId required for outbox write");
        }

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.eventId());
        envelope.put("eventType", event.eventType());
        envelope.put("schemaVersion", event.schemaVersion());
        envelope.put("aggregateId", event.aggregateId());
        envelope.put("aggregateVersion", event.aggregateVersion());
        envelope.put("occurredAt", event.occurredAt() == null ? Instant.now().toString() : event.occurredAt().toString());
        envelope.put("correlationId", event.correlationId());
        envelope.put("tenantId", tenant);
        envelope.put("payload", event.payload() == null ? Map.of() : event.payload());

        MessageOutbox row = new MessageOutbox();
        row.setEventId(event.eventId());
        row.setEventType(event.eventType());
        row.setRoutingKey(routingKey);
        row.setAggregateId(event.aggregateId());
        row.setAggregateVersion(event.aggregateVersion());
        row.setCorrelationId(event.correlationId());
        row.setTenantId(tenant);
        row.setPayloadJson(envelope);
        row.setStatus(MessageOutbox.PENDING);
        row.setAttempts(0);
        row.setCreatedAt(Instant.now());
        return repository.save(row);
    }
}
