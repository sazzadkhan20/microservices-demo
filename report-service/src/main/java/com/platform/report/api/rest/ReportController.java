package com.platform.report.api.rest;

import com.platform.common.messaging.EventTypes;
import com.platform.common.messaging.PlatformEvent;
import com.platform.common.messaging.PlatformQueues;
import com.platform.common.security.AuthContext;
import com.platform.common.tenant.TenantContext;
import com.platform.messaging.correlation.CorrelationContext;
import com.platform.messaging.outbox.OutboxWriter;
import com.platform.report.api.dto.AttendanceReportRequest;
import com.platform.report.application.AttendanceReportService;
import com.platform.report.application.AuditService;
import com.platform.report.domain.ReportExport;
import com.platform.report.infrastructure.HrmsGatewayClient;
import com.platform.report.infrastructure.persistence.ReportExportRepository;
import com.platform.report.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final AttendanceReportService attendanceService;
    private final ReportExportRepository exportRepository;
    private final HrmsGatewayClient hrms;
    private final ObjectProvider<OutboxWriter> outboxWriter;
    private final AuditService auditService;

    public ReportController(
            AttendanceReportService attendanceService,
            ReportExportRepository exportRepository,
            HrmsGatewayClient hrms,
            ObjectProvider<OutboxWriter> outboxWriter,
            AuditService auditService) {
        this.attendanceService = attendanceService;
        this.exportRepository = exportRepository;
        this.hrms = hrms;
        this.outboxWriter = outboxWriter;
        this.auditService = auditService;
    }

    /**
     * Generates a monthly attendance report and returns it as a CSV download.
     * Pass {@code async=true} to enqueue a background job (202 + export id).
     */
    @PostMapping("/attendance/monthly")
    @RequiresPermission(feature = "reports", action = "view")
    public ResponseEntity<?> attendanceMonthly(
            @Valid @RequestBody AttendanceReportRequest req,
            @RequestParam(defaultValue = "false") boolean async) {

        String tenantId = TenantContext.getTenantId();
        String filename = String.format("attendance-%04d-%02d.csv", req.year(), req.month());
        String period = String.format("%04d-%02d", req.year(), req.month());

        if (async && outboxWriter.getIfAvailable() != null) {
            ReportExport job = new ReportExport();
            job.setReportType("ATTENDANCE_MONTHLY");
            job.setPeriodLabel(period);
            job.setEmployeeId(req.employeeId());
            job.setFileName(filename);
            job.setContentType("text/csv");
            job.setSizeBytes(0);
            job.setRequestedBy(AuthContext.getEmployeeId());
            job.setStatus("PENDING");
            job.setCreatedAt(Instant.now());
            job = exportRepository.save(job);
            auditService.record(AuthContext.getEmployeeId(), "ENQUEUE", "ReportExport",
                    String.valueOf(job.getId()), null, Map.of("reportType", job.getReportType(), "period", period));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("exportId", job.getId());
            payload.put("year", req.year());
            payload.put("month", req.month());
            payload.put("employeeId", req.employeeId());
            PlatformEvent event = PlatformEvent.of(
                    EventTypes.REPORT_EXPORT_COMMAND,
                    String.valueOf(job.getId()),
                    1L,
                    CorrelationContext.getOrCreate(),
                    tenantId,
                    payload);
            outboxWriter.getObject().enqueue(event, PlatformQueues.REPORT_EXPORT);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("exportId", job.getId());
            body.put("status", "PENDING");
            body.put("message", "Report export queued — poll GET /api/reports/exports");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
        }

        String csv = attendanceService.generateMonthlyCsv(tenantId, req);
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ReportExport archived = new ReportExport();
        archived.setReportType("ATTENDANCE_MONTHLY");
        archived.setPeriodLabel(period);
        archived.setEmployeeId(req.employeeId());
        archived.setFileName(filename);
        archived.setContentType("text/csv");
        archived.setSizeBytes(bytes.length);
        archived.setRequestedBy(AuthContext.getEmployeeId());
        archived.setStatus("READY");
        archived.setCompletedAt(Instant.now());
        archived.setCreatedAt(Instant.now());
        exportRepository.save(archived);
        auditService.record(AuthContext.getEmployeeId(), "CREATE", "ReportExport",
                String.valueOf(archived.getId()), null,
                Map.of("reportType", archived.getReportType(), "period", period, "sizeBytes", bytes.length));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(bytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }

    /** Lists recent report exports from the tenant reports database. */
    @GetMapping("/exports")
    @RequiresPermission(feature = "reports", action = "view")
    public ResponseEntity<List<Map<String, Object>>> listExports() {
        Long actor = AuthContext.requireEmployeeId();
        boolean seeAll = hrms.hasPermission(actor, "reports", "export");
        List<Map<String, Object>> rows = exportRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .filter(e -> seeAll || actor.equals(e.getRequestedBy()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("reportType", e.getReportType());
                    m.put("periodLabel", e.getPeriodLabel());
                    m.put("employeeId", e.getEmployeeId());
                    m.put("fileName", e.getFileName());
                    m.put("sizeBytes", e.getSizeBytes());
                    m.put("status", e.getStatus());
                    m.put("requestedBy", e.getRequestedBy());
                    m.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
                    m.put("completedAt", e.getCompletedAt() == null ? null : e.getCompletedAt().toString());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(rows);
    }
}
