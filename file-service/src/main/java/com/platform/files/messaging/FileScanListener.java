package com.platform.files.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.messaging.PlatformQueues;
import com.platform.files.application.FileService;
import com.platform.messaging.outbox.InboxGuard;
import com.platform.messaging.outbox.OutboxRelay;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "platform.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileScanListener {

    private static final String CONSUMER = "files.scan";

    private final ObjectMapper objectMapper;
    private final InboxGuard inboxGuard;
    private final FileService fileService;
    private final OutboxRelay outboxRelay;

    public FileScanListener(
            ObjectMapper objectMapper,
            InboxGuard inboxGuard,
            FileService fileService,
            OutboxRelay outboxRelay) {
        this.objectMapper = objectMapper;
        this.inboxGuard = inboxGuard;
        this.fileService = fileService;
        this.outboxRelay = outboxRelay;
    }

    @RabbitListener(queues = PlatformQueues.FILE_SCAN_Q)
    public void onMessage(Message message) throws Exception {
        byte[] body = message.getBody();
        Map<String, Object> envelope = objectMapper.readValue(body, new TypeReference<>() {});
        String eventId = String.valueOf(envelope.getOrDefault("eventId", ""));
        inboxGuard.processOnce(
                eventId,
                String.valueOf(envelope.getOrDefault("eventType", "")),
                CONSUMER,
                envelope,
                () -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = envelope.get("payload") instanceof Map<?, ?> m
                            ? (Map<String, Object>) m : envelope;
                    Long fileId = payload.get("fileId") instanceof Number n
                            ? n.longValue()
                            : Long.parseLong(String.valueOf(payload.get("fileId")));
                    // processScan persists REJECTED without throwing — ack after durable write (guide §7).
                    String status = fileService.processScan(fileId);
                    return Map.of("fileId", fileId, "status", status, "ok", true);
                });
    }
}
