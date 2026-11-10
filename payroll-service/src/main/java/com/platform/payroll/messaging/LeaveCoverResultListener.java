package com.platform.payroll.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.messaging.PlatformQueues;
import com.platform.messaging.correlation.CorrelationContext;
import com.platform.messaging.outbox.InboxGuard;
import com.platform.payroll.application.PayrollFinalizeService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Consumes {@code leave.cover.result} at-least-once. The inbox dedupes the delivery; the finalise
 * service additionally ignores stale / already-applied results by operation id and revision.
 * Acknowledged only after the local transaction commits (exception → retry → DLQ).
 */
@Component
@ConditionalOnProperty(prefix = "platform.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LeaveCoverResultListener {

    private static final String CONSUMER = "payroll.leave-cover-result";

    private final ObjectMapper objectMapper;
    private final InboxGuard inboxGuard;
    private final PayrollFinalizeService finalizeService;

    public LeaveCoverResultListener(ObjectMapper objectMapper, InboxGuard inboxGuard,
                                    PayrollFinalizeService finalizeService) {
        this.objectMapper = objectMapper;
        this.inboxGuard = inboxGuard;
        this.finalizeService = finalizeService;
    }

    @RabbitListener(queues = PlatformQueues.LEAVE_COVER_RESULT_Q)
    @Transactional
    public void onMessage(Message message) throws Exception {
        Map<String, Object> envelope = objectMapper.readValue(message.getBody(), new TypeReference<>() {});
        String eventId = String.valueOf(envelope.getOrDefault("eventId", ""));
        CorrelationContext.set(String.valueOf(envelope.getOrDefault("correlationId", eventId)));
        try {
            inboxGuard.processOnce(eventId, String.valueOf(envelope.getOrDefault("eventType", "")), CONSUMER, envelope,
                    () -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = envelope.get("payload") instanceof Map<?, ?> m
                                ? (Map<String, Object>) m : envelope;
                        finalizeService.onLeaveResult(payload);
                        return Map.of("ok", true);
                    });
        } finally {
            CorrelationContext.clear();
        }
    }
}
