package com.platform.notification.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.messaging.PlatformQueues;
import com.platform.messaging.outbox.InboxGuard;
import com.platform.messaging.tenant.TenantBoundRunner;
import com.platform.notification.api.dto.CreateNotificationRequest;
import com.platform.notification.application.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "platform.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationCreateListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationCreateListener.class);
    private static final String CONSUMER = "notification.create";

    private final ObjectMapper objectMapper;
    private final InboxGuard inboxGuard;
    private final NotificationService notificationService;

    public NotificationCreateListener(
            ObjectMapper objectMapper,
            InboxGuard inboxGuard,
            NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.inboxGuard = inboxGuard;
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = PlatformQueues.NOTIFICATION_CREATE_Q)
    public void onMessage(Message message) {
        byte[] body = message.getBody();
        Map<String, Object> envelope;
        try {
            envelope = objectMapper.readValue(body, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid notification.create payload", e);
        }
        String tenantId = String.valueOf(envelope.getOrDefault("tenantId", ""));
        String eventId = String.valueOf(envelope.getOrDefault("eventId", ""));
        TenantBoundRunner.run(tenantId, () -> inboxGuard.processOnce(
                eventId,
                String.valueOf(envelope.getOrDefault("eventType", "")),
                CONSUMER,
                envelope,
                () -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = envelope.get("payload") instanceof Map<?, ?> m
                            ? (Map<String, Object>) m : envelope;
                    Long employeeId = longOf(payload.get("employeeId"));
                    if (employeeId == null) {
                        throw new IllegalArgumentException("employeeId required");
                    }
                    CreateNotificationRequest req = new CreateNotificationRequest(
                            employeeId,
                            str(payload.get("title")),
                            str(payload.get("body")),
                            str(payload.get("category")),
                            blankToNull(str(payload.get("dedupeKey"))),
                            blankToNull(str(payload.get("linkUrl"))));
                    var created = notificationService.create(tenantId, req);
                    log.info("Notification created id={} emp={}", created.id(), employeeId);
                    return Map.of("notificationId", created.id());
                }));
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
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
