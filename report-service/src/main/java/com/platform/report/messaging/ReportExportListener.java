package com.platform.report.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.messaging.PlatformQueues;
import com.platform.messaging.outbox.InboxGuard;
import com.platform.report.api.dto.AttendanceReportRequest;
import com.platform.report.application.AttendanceReportService;
import com.platform.report.domain.ReportExport;
import com.platform.report.infrastructure.persistence.ReportExportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Background report export worker. Sync HTTP path still works; async jobs land here.
 */
@Component
@ConditionalOnProperty(prefix = "platform.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReportExportListener {

    private static final Logger log = LoggerFactory.getLogger(ReportExportListener.class);
    private static final String CONSUMER = "reports.export";

    private final ObjectMapper objectMapper;
    private final InboxGuard inboxGuard;
    private final AttendanceReportService attendanceService;
    private final ReportExportRepository exportRepository;

    public ReportExportListener(
            ObjectMapper objectMapper,
            InboxGuard inboxGuard,
            AttendanceReportService attendanceService,
            ReportExportRepository exportRepository) {
        this.objectMapper = objectMapper;
        this.inboxGuard = inboxGuard;
        this.attendanceService = attendanceService;
        this.exportRepository = exportRepository;
    }

    @RabbitListener(queues = PlatformQueues.REPORT_EXPORT_Q)
    public void onMessage(Message message) throws Exception {
        byte[] body = message.getBody();
        Map<String, Object> envelope = objectMapper.readValue(body, new TypeReference<>() {});
        String eventId = String.valueOf(envelope.getOrDefault("eventId", ""));
        inboxGuard.processOnce(
                eventId,
                String.valueOf(envelope.getOrDefault("eventType", "")),
                CONSUMER,
                envelope,
                () -> runExport(envelope));
    }

    @Transactional
    protected Map<String, Object> runExport(Map<String, Object> envelope) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = envelope.get("payload") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : envelope;
        Long exportId = payload.get("exportId") instanceof Number n
                ? n.longValue()
                : Long.parseLong(String.valueOf(payload.get("exportId")));
        ReportExport job = exportRepository.findById(exportId)
                .orElseThrow(() -> new IllegalArgumentException("Report export not found: " + exportId));
        if ("READY".equals(job.getStatus()) || "FAILED".equals(job.getStatus())) {
            return Map.of("exportId", exportId, "status", job.getStatus());
        }
        try {
            int year = Integer.parseInt(String.valueOf(payload.get("year")));
            int month = Integer.parseInt(String.valueOf(payload.get("month")));
            Long employeeId = asLong(payload.get("employeeId"));
            Long locationId = asLong(payload.get("locationId"));
            Long departmentId = asLong(payload.get("departmentId"));
            Long teamId = asLong(payload.get("teamId"));
            AttendanceReportRequest req = new AttendanceReportRequest(
                    year, month, employeeId, locationId, departmentId, teamId);
            String csv = attendanceService.generateMonthlyCsv(req);
            byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
            job.setSizeBytes(bytes.length);
            job.setStatus("READY");
            job.setCompletedAt(Instant.now());
            job.setErrorMessage(null);
            // Store CSV inline key marker — download still generated on-demand from archive list / sync API.
            job.setStorageKey("inline:" + job.getFileName());
            exportRepository.save(job);
            log.info("Async report export READY id={}", exportId);
            return Map.of("exportId", exportId, "status", "READY", "sizeBytes", bytes.length);
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
            exportRepository.save(job);
            throw e;
        }
    }

    private static Long asLong(Object v) {
        if (v == null || "null".equals(String.valueOf(v))) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        return Long.parseLong(s);
    }
}
