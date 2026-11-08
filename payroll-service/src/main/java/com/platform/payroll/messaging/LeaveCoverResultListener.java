package com.platform.payroll.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.messaging.PlatformQueues;
import com.platform.messaging.correlation.CorrelationContext;
import com.platform.messaging.outbox.InboxGuard;
import com.platform.messaging.tenant.TenantBoundRunner;
import com.platform.payroll.application.PayrollFinalizeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "platform.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LeaveCoverResultListener {

    private static final Logger log = LoggerFactory.getLogger(LeaveCoverResultListener.class);
    private static final String CONSUMER = "payroll.leave-cover-result";

    private final ObjectMapper objectMapper;
    private final InboxGuard inboxGuard;
    private final PayrollFinalizeService finalizeService;

    public LeaveCoverResultListener(
            ObjectMapper objectMapper,
            InboxGuard inboxGuard,
            PayrollFinalizeService finalizeService) {
        this.objectMapper = objectMapper;
        this.inboxGuard = inboxGuard;
        this.finalizeService = finalizeService;
    }

    @RabbitListener(queues = PlatformQueues.LEAVE_COVER_RESULT_Q)
    public void onMessage(Message message) {
        byte[] body = message.getBody();
        Map<String, Object> envelope;
        try {
            envelope = objectMapper.readValue(body, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid leave-cover-result payload", e);
        }
        String tenantId = String.valueOf(envelope.getOrDefault("tenantId", ""));
        String eventId = String.valueOf(envelope.getOrDefault("eventId", ""));
        String correlationId = String.valueOf(envelope.getOrDefault("correlationId", eventId));
        TenantBoundRunner.run(tenantId, () -> {
            CorrelationContext.set(correlationId);
            try {
                inboxGuard.processOnce(eventId,
                        String.valueOf(envelope.getOrDefault("eventType", "")),
                        CONSUMER,
                        envelope,
                        () -> {
                            finalizeService.onLeaveCoverResult(envelope);
                            return Map.of("ok", true);
                        });
            } finally {
                CorrelationContext.clear();
            }
        });
        log.debug("Processed leave cover result eventId={}", eventId);
    }
}
