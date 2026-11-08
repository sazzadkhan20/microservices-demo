package com.platform.messaging.outbox;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Idempotent consumer helper: claim event_id in inbox, run handler once, store result.
 */
@Component
public class InboxGuard {

    private final MessageInboxRepository repository;

    public InboxGuard(MessageInboxRepository repository) {
        this.repository = repository;
    }

    public Optional<MessageInbox> find(String eventId) {
        return repository.findByEventId(eventId);
    }

    /**
     * @return empty if already processed; otherwise result of handler after inbox insert
     */
    @Transactional
    public Optional<Map<String, Object>> processOnce(
            String eventId,
            String eventType,
            String consumerName,
            Map<String, Object> payload,
            Supplier<Map<String, Object>> handler) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId required");
        }
        if (repository.existsByEventId(eventId)) {
            return Optional.empty();
        }
        Map<String, Object> result = handler.get();
        MessageInbox row = new MessageInbox();
        row.setEventId(eventId);
        row.setEventType(eventType == null ? "" : eventType);
        row.setConsumerName(consumerName);
        row.setPayloadJson(payload == null ? Map.of() : payload);
        row.setProcessedAt(Instant.now());
        row.setResultJson(result);
        try {
            repository.saveAndFlush(row);
        } catch (DataIntegrityViolationException dup) {
            return Optional.empty();
        }
        return Optional.ofNullable(result);
    }
}
