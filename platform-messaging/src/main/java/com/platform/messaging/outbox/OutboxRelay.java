package com.platform.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.messaging.PlatformQueues;
import com.platform.common.tenant.TenantContext;
import com.platform.messaging.config.MessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls PENDING outbox rows and publishes to RabbitMQ.
 * Guide §7: confirm broker publication before marking sent.
 * Guide §7: do not hold a DB transaction open while waiting on the broker.
 * <p>
 * Publishes raw JSON bytes via {@link RabbitTemplate#send} (not convertAndSend) so a
 * configured {@code Jackson2JsonMessageConverter} cannot double-encode the body.
 */
@Component
@ConditionalOnProperty(prefix = "platform.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final MessageOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final MessagingProperties properties;
    private final TransactionTemplate transactionTemplate;

    public OutboxRelay(
            MessageOutboxRepository repository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            MessagingProperties properties,
            TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${platform.messaging.outbox-poll-ms:1000}")
    public void publishPending() {
        String tenantHint = TenantContext.getTenantId();
        // Multi-tenant: services drive via TenantOutboxScheduler with TenantContext bound.
        if (tenantHint == null || tenantHint.isBlank()) {
            return;
        }
        drainBatch();
    }

    /** Called by service-level tenant iterators with TenantContext already set. */
    public int drainBatch() {
        List<Long> claimedIds = transactionTemplate.execute(status -> claimBatch());
        if (claimedIds == null || claimedIds.isEmpty()) {
            return 0;
        }
        int published = 0;
        for (Long id : claimedIds) {
            MessageOutbox snapshot = transactionTemplate.execute(status ->
                    repository.findById(id).orElse(null));
            if (snapshot == null || !MessageOutbox.PUBLISHING.equals(snapshot.getStatus())) {
                continue;
            }
            String eventId = snapshot.getEventId();
            try {
                byte[] body = objectMapper.writeValueAsBytes(snapshot.getPayloadJson());
                Message message = buildMessage(snapshot, body);
                CorrelationData correlation = new CorrelationData(eventId);
                rabbitTemplate.send(PlatformQueues.EXCHANGE, snapshot.getRoutingKey(), message, correlation);
                awaitBrokerConfirm(correlation, eventId);
                transactionTemplate.executeWithoutResult(status -> markPublished(id));
                published++;
            } catch (Exception e) {
                transactionTemplate.executeWithoutResult(status -> markFailedAttempt(id, e.getMessage()));
                log.warn("Outbox publish failed eventId={} : {}", eventId, e.getMessage());
            }
        }
        return published;
    }

    private List<Long> claimBatch() {
        // Crash safety: rows left in PUBLISHING after a killed JVM become PENDING again.
        Instant claimLeaseExpiry = Instant.now().minusSeconds(
                Math.max(30L, properties.getPublishConfirmTimeoutMs() / 1000L * 6L));
        repository.reclaimStalePublishing(claimLeaseExpiry);

        Instant now = Instant.now();
        List<MessageOutbox> batch = repository.findPendingForUpdate(properties.getOutboxBatchSize(), now);
        if (batch == null || batch.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(batch.size());
        for (MessageOutbox row : batch) {
            row.setStatus(MessageOutbox.PUBLISHING);
            // Lease marker until confirm — cleared/replaced on success or failure.
            row.setPublishedAt(now);
            row.setLastError(null);
            repository.save(row);
            ids.add(row.getId());
        }
        return ids;
    }

    private static Message buildMessage(MessageOutbox snapshot, byte[] body) {
        MessageProperties props = new MessageProperties();
        props.setMessageId(snapshot.getEventId());
        props.setCorrelationId(snapshot.getCorrelationId());
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding("UTF-8");
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setHeader("tenantId", snapshot.getTenantId());
        props.setHeader("eventType", snapshot.getEventType());
        props.setHeader("aggregateId", snapshot.getAggregateId());
        return new Message(body, props);
    }

    private void markPublished(Long id) {
        MessageOutbox row = repository.findById(id).orElse(null);
        if (row == null || !MessageOutbox.PUBLISHING.equals(row.getStatus())) {
            return;
        }
        row.setStatus(MessageOutbox.PUBLISHED);
        row.setPublishedAt(Instant.now());
        row.setAttempts(row.getAttempts() + 1);
        row.setLastError(null);
        repository.save(row);
    }

    private void markFailedAttempt(Long id, String error) {
        MessageOutbox row = repository.findById(id).orElse(null);
        if (row == null || !MessageOutbox.PUBLISHING.equals(row.getStatus())) {
            return;
        }
        int attempts = row.getAttempts() + 1;
        row.setAttempts(attempts);
        row.setLastError(truncate(error, 2000));
        if (attempts >= properties.getOutboxMaxAttempts()) {
            row.setStatus(MessageOutbox.FAILED);
            row.setPublishedAt(null);
        } else {
            // MSG-01 — exponential backoff with jitter; published_at = next attempt time while PENDING
            row.setStatus(MessageOutbox.PENDING);
            row.setPublishedAt(Instant.now().plusMillis(backoffMs(attempts)));
        }
        repository.save(row);
    }

    /** Cap ~15 min; base 1s * 2^(attempt-1) with ±20% jitter. */
    public static long backoffMs(int attempt) {
        return backoffMs(attempt, Math.random());
    }

    /** {@code rand} in [0,1]: 0 → −20%, 0.5 → 0%, 1 → +20%. */
    public static long backoffMs(int attempt, double rand) {
        int n = Math.max(1, Math.min(attempt, 12));
        long base = Math.min(900_000L, (1L << (n - 1)) * 1000L);
        double unit = Math.min(1.0, Math.max(0.0, rand));
        long jitter = (long) (base * 0.2 * (unit * 2 - 1));
        return Math.max(500L, base + jitter);
    }

    private void awaitBrokerConfirm(CorrelationData correlation, String eventId) throws Exception {
        long timeout = Math.max(500L, properties.getPublishConfirmTimeoutMs());
        var future = correlation.getFuture();
        if (future == null) {
            throw new IllegalStateException(
                    "Publisher confirms are not enabled (CorrelationData future null) eventId=" + eventId);
        }
        var confirm = future.get(timeout, TimeUnit.MILLISECONDS);
        if (confirm == null || !confirm.isAck()) {
            String reason = confirm == null ? "null confirm" : String.valueOf(confirm.getReason());
            throw new IllegalStateException("Broker nack/timeout for eventId=" + eventId + ": " + reason);
        }
        if (correlation.getReturned() != null) {
            throw new IllegalStateException(
                    "Message returned (unroutable) for eventId=" + eventId
                            + " exchange=" + correlation.getReturned().getExchange()
                            + " rk=" + correlation.getReturned().getRoutingKey());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
