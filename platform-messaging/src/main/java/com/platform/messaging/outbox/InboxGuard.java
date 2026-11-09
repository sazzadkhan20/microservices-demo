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
     * Claim-first idempotency (MSG-02): insert inbox row before handler so concurrent
     * duplicates cannot both execute side effects. Returns empty if already claimed.
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
        MessageInbox claim = new MessageInbox();
        claim.setEventId(eventId);
        claim.setEventType(eventType == null ? "" : eventType);
        claim.setConsumerName(consumerName == null ? "" : consumerName);
        claim.setPayloadJson(payload == null ? Map.of() : payload);
        claim.setProcessedAt(Instant.now());
        claim.setResultJson(Map.of("status", "CLAIMED"));
        try {
            repository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException dup) {
            return Optional.empty();
        }
        Map<String, Object> result = handler.get();
        claim.setResultJson(result == null ? Map.of() : result);
        claim.setProcessedAt(Instant.now());
        repository.save(claim);
        return Optional.ofNullable(result);
    }
}
