package com.platform.leave.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.messaging.EventTypes;
import com.platform.common.messaging.PlatformEvent;
import com.platform.common.messaging.PlatformQueues;
import com.platform.leave.application.LeaveCoverService;
import com.platform.leave.domain.LeaveOperation;
import com.platform.leave.infrastructure.persistence.LeaveOperationRepository;
import com.platform.messaging.correlation.CorrelationContext;
import com.platform.messaging.outbox.InboxGuard;
import com.platform.messaging.outbox.OutboxRelay;
import com.platform.messaging.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Consumes payroll leave-cover commands; applies balance changes idempotently; replies via outbox.
 */
@Component
@ConditionalOnProperty(prefix = "platform.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LeaveCoverCommandListener {

    private static final Logger log = LoggerFactory.getLogger(LeaveCoverCommandListener.class);
    private static final String CONSUMER = "hrms.leave-cover";

    private final ObjectMapper objectMapper;
    private final InboxGuard inboxGuard;
    private final OutboxWriter outboxWriter;
    private final ObjectProvider<OutboxRelay> outboxRelay;
    private final LeaveCoverService leaveCoverService;
    private final LeaveOperationRepository leaveOperationRepository;
    private final LeaveCoverCommandHandler commandHandler;

    public LeaveCoverCommandListener(
            ObjectMapper objectMapper,
            InboxGuard inboxGuard,
            OutboxWriter outboxWriter,
            ObjectProvider<OutboxRelay> outboxRelay,
            LeaveCoverService leaveCoverService,
            LeaveOperationRepository leaveOperationRepository,
            @Lazy LeaveCoverCommandHandler commandHandler) {
        this.objectMapper = objectMapper;
        this.inboxGuard = inboxGuard;
        this.outboxWriter = outboxWriter;
        this.outboxRelay = outboxRelay;
        this.leaveCoverService = leaveCoverService;
        this.leaveOperationRepository = leaveOperationRepository;
        this.commandHandler = commandHandler;
    }

    @RabbitListener(queues = PlatformQueues.PAYROLL_LEAVE_COVER_Q)
    public void onMessage(Message message) {
        byte[] body = message.getBody();
        Map<String, Object> envelope;
        try {
            envelope = objectMapper.readValue(body, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid leave-cover payload", e);
        }
        String eventId = str(envelope.get("eventId"));
        String correlationId = str(envelope.get("correlationId"));
        CorrelationContext.set(correlationId.isBlank() ? eventId : correlationId);
        try {
            commandHandler.handle(envelope);
        } finally {
            CorrelationContext.clear();
        }
    }

    /** Invoked only via {@link LeaveCoverCommandHandler} so the transaction proxy applies. */
    void handleInTransaction(Map<String, Object> envelope) {
        String eventId = str(envelope.get("eventId"));
        String eventType = str(envelope.get("eventType"));
        // Guide §6: duplicate commands must return the recorded result (re-emit via outbox).
        // If inbox already claimed the eventId but payroll never got the reply (lost result /
        // crash after leave_operation), redelivery must still replay from leave_operation.
        if (inboxGuard.find(eventId).isPresent()) {
            reemitRecordedResult(envelope);
            return;
        }
        inboxGuard.processOnce(eventId, eventType, CONSUMER, envelope, () -> processCommand(envelope))
                .ifPresent(result -> log.info("Leave cover processed eventId={} success={}",
                        eventId, result.get("success")));
    }

    /** Idempotent replay when the command event was already consumed. */
    private void reemitRecordedResult(Map<String, Object> envelope) {
        Map<String, Object> payload = payloadOf(envelope);
        String operationKey = str(payload.get("operationKey"));
        LeaveOperation existing = leaveOperationRepository.findByOperationKey(operationKey).orElse(null);
        if (existing == null) {
            log.warn("Duplicate leave-cover eventId with no leave_operation; skipping replay key={}",
                    operationKey);
            return;
        }
        Map<String, Object> resultPayload = new LinkedHashMap<>();
        if (existing.getResultJson() != null) {
            resultPayload.putAll(existing.getResultJson());
        }
        resultPayload.putIfAbsent("operationId", str(payload.get("operationId")));
        resultPayload.putIfAbsent("operationKey", operationKey);
        resultPayload.putIfAbsent("period", existing.getPeriodLabel());
        resultPayload.put("success", LeaveOperation.STATUS_SUCCEEDED.equals(existing.getStatus()));
        resultPayload.put("status", existing.getStatus());
        resultPayload.put("idempotentReplay", true);

        String correlationId = str(envelope.get("correlationId"));
        long aggregateVersion = longOf(envelope.get("aggregateVersion")) == null
                ? 1L : longOf(envelope.get("aggregateVersion"));
        enqueueResult(correlationId, existing.getPeriodLabel(), aggregateVersion, resultPayload);
        log.info("Re-emitted leave-cover result for duplicate event opKey={}", operationKey);
    }

    /**
     * Run-level payroll command (docs/PAYROLL_SPEC.md §2.3): FINALIZE applies every cover of the run or
     * none (explicit rejection with reasons); REOPEN reverses the ledger rows of {@code reverseOfKey}.
     * The leave_operation row, the inbox key and the result outbox commit together.
     */
    private Map<String, Object> processCommand(Map<String, Object> envelope) {
        Map<String, Object> payload = payloadOf(envelope);
        String operationKey = str(payload.get("operationKey"));
        String operationId = str(payload.get("operationId"));
        String action = str(payload.get("action"));
        String period = str(payload.get("period"));
        String correlationId = str(envelope.get("correlationId"));
        String eventId = str(envelope.get("eventId"));
        long aggregateVersion = longOf(envelope.get("aggregateVersion")) == null
                ? 1L : longOf(envelope.get("aggregateVersion"));

        Map<String, Object> resultPayload = new LinkedHashMap<>();
        resultPayload.put("operationId", operationId);
        resultPayload.put("operationKey", operationKey);
        resultPayload.put("action", action);
        resultPayload.put("period", period);
        resultPayload.put("revision", payload.get("revision"));

        LeaveOperation existing = leaveOperationRepository.findByOperationKey(operationKey).orElse(null);
        if (existing != null) {
            resultPayload.putAll(existing.getResultJson() == null ? Map.of() : existing.getResultJson());
            resultPayload.put("idempotentReplay", true);
            enqueueResult(correlationId, period, aggregateVersion, resultPayload);
            return resultPayload;
        }

        LeaveOperation op = new LeaveOperation();
        op.setOperationKey(operationKey);
        op.setEventId(eventId);
        op.setEmployeeId(0L); // run-level operation (all employees of the payroll month)
        op.setPeriodLabel(period);
        op.setReverseFlag("REOPEN".equals(action));
        op.setCreatedAt(Instant.now());
        try {
            if ("FINALIZE".equals(action)) {
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> lines = payload.get("lines") instanceof java.util.List<?> l
                        ? (java.util.List<Map<String, Object>>) l : java.util.List.of();
                java.util.List<String> problems = leaveCoverService.applyFinalize(operationKey, period, lines);
                if (problems.isEmpty()) {
                    op.setStatus(LeaveOperation.STATUS_SUCCEEDED);
                    resultPayload.put("success", true);
                    resultPayload.put("employees", lines.size());
                } else {
                    op.setStatus(LeaveOperation.STATUS_FAILED);
                    resultPayload.put("success", false);
                    resultPayload.put("errorMessage", String.join("; ", problems));
                    resultPayload.put("problems", problems);
                }
            } else if ("REOPEN".equals(action)) {
                int reversed = leaveCoverService.applyReversal(str(payload.get("reverseOfKey")), period);
                op.setStatus(LeaveOperation.STATUS_SUCCEEDED);
                resultPayload.put("success", true);
                resultPayload.put("reversedRows", reversed);
            } else {
                op.setStatus(LeaveOperation.STATUS_FAILED);
                resultPayload.put("success", false);
                resultPayload.put("errorMessage", "Unknown payroll leave-cover action: " + action);
            }
        } catch (Exception e) {
            op.setStatus(LeaveOperation.STATUS_FAILED);
            resultPayload.put("success", false);
            resultPayload.put("errorMessage", e.getMessage());
            log.warn("Leave cover failed opKey={}: {}", operationKey, e.getMessage());
        }
        resultPayload.put("status", op.getStatus());
        op.setResultJson(new LinkedHashMap<>(resultPayload));
        op.setCompletedAt(Instant.now());
        leaveOperationRepository.save(op);
        enqueueResult(correlationId, period, aggregateVersion, resultPayload);
        return resultPayload;
    }

    private void enqueueResult(
            String correlationId,
            String aggregateId,
            long version,
            Map<String, Object> resultPayload) {
        PlatformEvent event = PlatformEvent.of(
                EventTypes.LEAVE_COVER_RESULT,
                aggregateId == null || aggregateId.isBlank() ? "unknown" : aggregateId,
                version,
                correlationId,
                resultPayload);
        outboxWriter.enqueue(event, PlatformQueues.LEAVE_COVER_RESULT);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    OutboxRelay relay = outboxRelay.getIfAvailable();
                    if (relay != null) {
                        try {
                            relay.drainBatch();
                        } catch (Exception e) {
                            log.debug("Outbox drain after leave-cover: {}", e.getMessage());
                        }
                    }
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payloadOf(Map<String, Object> envelope) {
        Object p = envelope.get("payload");
        if (p instanceof Map<?, ?> m) {
            return new HashMap<>((Map<String, Object>) m);
        }
        return new HashMap<>(envelope);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static Long longOf(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }
}
