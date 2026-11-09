package com.platform.timework.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.messaging.EventTypes;
import com.platform.common.messaging.PlatformQueues;
import com.platform.common.people.PeopleDirectoryClient;
import com.platform.messaging.correlation.CorrelationContext;
import com.platform.messaging.outbox.InboxGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Time&amp;Work does not store employees — only invalidates People API cache on upsert events (guide §3).
 */
@Component
@ConditionalOnProperty(prefix = "platform.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PeopleEmployeeCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(PeopleEmployeeCacheInvalidator.class);
    private static final String CONSUMER = "timework.people-employee-cache";

    private final ObjectMapper objectMapper;
    private final InboxGuard inboxGuard;
    private final PeopleDirectoryClient people;

    public PeopleEmployeeCacheInvalidator(
            ObjectMapper objectMapper,
            InboxGuard inboxGuard,
            PeopleDirectoryClient people) {
        this.objectMapper = objectMapper;
        this.inboxGuard = inboxGuard;
        this.people = people;
    }

    @RabbitListener(queues = PlatformQueues.PEOPLE_EMPLOYEE_UPSERTED_TIMEWORK_Q)
    public void onEmployeeUpserted(Message message) {
        byte[] body = message.getBody();
        Map<String, Object> envelope;
        try {
            envelope = objectMapper.readValue(body, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid people.employee.upserted payload", e);
        }
        String eventType = str(envelope.get("eventType"));
        if (!EventTypes.PEOPLE_EMPLOYEE_UPSERTED.equals(eventType)) {
            return;
        }
        String eventId = str(envelope.get("eventId"));
        String correlationId = str(envelope.get("correlationId"));
        if (eventId.isBlank()) {
            return;
        }
        CorrelationContext.set(correlationId.isBlank() ? eventId : correlationId);
        try {
            inboxGuard.processOnce(eventId, eventType, CONSUMER, envelope, () -> {
                Long id = employeeIdOf(envelope);
                people.invalidateEmployee(id);
                log.debug("Time&Work People cache invalidated employeeId={}", id);
                return Map.of("ok", true, "employeeId", id == null ? "" : id);
            });
        } finally {
            CorrelationContext.clear();
        }
    }

    private Long employeeIdOf(Map<String, Object> envelope) {
        Object payload = envelope.get("payload");
        if (!(payload instanceof Map<?, ?> map)) {
            return null;
        }
        Object id = map.get("id");
        if (id instanceof Number n) {
            return n.longValue();
        }
        if (id != null && !id.toString().isBlank()) {
            try {
                return Long.parseLong(id.toString().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
