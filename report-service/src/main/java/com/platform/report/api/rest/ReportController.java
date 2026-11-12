package com.platform.report.api.rest;

import com.platform.common.messaging.EventTypes;
import com.platform.common.messaging.PlatformEvent;
import com.platform.common.messaging.PlatformQueues;
import com.platform.common.security.AuthContext;
import com.platform.messaging.correlation.CorrelationContext;
import com.platform.messaging.outbox.OutboxWriter;
import com.platform.report.api.dto.AttendanceReportRequest;
import com.platform.report.application.AttendanceReportService;
import com.platform.report.application.AuditService;
import com.platform.report.application.CsvSupport;
import com.platform.report.application.ReportCatalogService;
import com.platform.report.domain.ReportExport;
import com.platform.report.infrastructure.HrmsGatewayClient;
import com.platform.report.infrastructure.persistence.ReportExportRepository;
import com.platform.report.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final AttendanceReportService attendanceService;
    private final ReportCatalogService catalogService;
    private final ReportExportRepository exportRepository;
    private final HrmsGatewayClient hrms;
    private final ObjectProvider<OutboxWriter> outboxWriter;
    private final AuditService auditService;

    public ReportController(
            AttendanceReportService attendanceService,
            ReportCatalogService catalogService,
            ReportExportRepository exportRepository,
            HrmsGatewayClient hrms,
            ObjectProvider<OutboxWriter> outboxWriter,
            AuditService auditService) {
        this.attendanceService = attendanceService;
        this.catalogService = catalogService;
        this.exportRepository = exportRepository;
        this.hrms = hrms;
        this.outboxWriter = outboxWriter;
        this.auditService = auditService;
    }

    /** Preview + CSV for monthly attendance (FR-RP-01). FE gates download with reports:export. */
    @PostMapping("/attendance/monthly")
    @RequiresPermission(feature = "reports", action = "view")
    public ResponseEntity<?> attendanceMonthly(
            @Valid @RequestBody AttendanceReportRequest req,
            @RequestParam(defaultValue = "false") boolean async) {

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
            payload.put("locationId", req.locationId());
            payload.put("departmentId", req.departmentId());
            payload.put("teamId", req.teamId());
            payload.put("actorEmployeeId", AuthContext.getEmployeeId());
            PlatformEvent event = PlatformEvent.of(
                    EventTypes.REPORT_EXPORT_COMMAND,
                    String.valueOf(job.getId()),
                    1L,
                    CorrelationContext.getOrCreate(),
                    payload);
            outboxWriter.getObject().enqueue(event, PlatformQueues.REPORT_EXPORT);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("exportId", job.getId());
            body.put("status", "PENDING");
            body.put("message", "Report export queued — poll GET /api/reports/exports");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
        }

        String csv = attendanceService.generateMonthlyCsv(req);
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        archive("ATTENDANCE_MONTHLY", period, req.employeeId(), filename, bytes.length);
        return CsvSupport.download(csv, filename);
    }

    @GetMapping("/late-absence.csv")
    @RequiresPermission(feature = "reports", action = "export")
    public ResponseEntity<byte[]> lateAbsence(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long employeeId) {
        LocalDate fromDate = parseOr(from, LocalDate.now().withDayOfMonth(1));
        LocalDate toDate = parseOr(to, LocalDate.now());
        requireRange(fromDate, toDate);
        String csv = catalogService.lateAbsenceSummaryCsv(fromDate, toDate, locationId, departmentId, teamId, employeeId);
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        archive("LATE_ABSENCE", fromDate + "_" + toDate, employeeId, "late-absence.csv", bytes.length);
        return CsvSupport.download(csv, "late-absence.csv");
    }

    @GetMapping("/leave-balances.csv")
    @RequiresPermission(feature = "reports", action = "export")
    public ResponseEntity<byte[]> leaveBalances(
            @RequestParam(required = false) String year,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long employeeId) {
        int y = year != null && !year.isBlank() ? Integer.parseInt(year.trim()) : LocalDate.now().getYear();
        String csv = catalogService.leaveBalancesCsv(y, locationId, departmentId, teamId, employeeId);
        String filename = "leave-balances-" + y + ".csv";
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        archive("LEAVE_BALANCES", String.valueOf(y), employeeId, filename, bytes.length);
        return CsvSupport.download(csv, filename);
    }

    @GetMapping("/leave-requests.csv")
    @RequiresPermission(feature = "reports", action = "export")
    public ResponseEntity<byte[]> leaveRequests(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long employeeId) {
        LocalDate fromDate = parseOr(from, LocalDate.now().withDayOfMonth(1));
        LocalDate toDate = parseOr(to, LocalDate.now());
        requireRange(fromDate, toDate);
        String csv = catalogService.leaveRequestsCsv(fromDate, toDate, locationId, departmentId, teamId, employeeId);
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        archive("LEAVE_REQUESTS", fromDate + "_" + toDate, employeeId, "leave-requests.csv", bytes.length);
        return CsvSupport.download(csv, "leave-requests.csv");
    }

    @GetMapping("/headcount.csv")
    @RequiresPermission(feature = "reports", action = "export")
    public ResponseEntity<byte[]> headcount(
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long teamId) {
        String csv = catalogService.headcountCsv(locationId, departmentId, teamId);
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        archive("HEADCOUNT", "last-6-months", null, "headcount.csv", bytes.length);
        return CsvSupport.download(csv, "headcount.csv");
    }

    @GetMapping("/employees.csv")
    @RequiresPermission(feature = "reports", action = "export")
    public ResponseEntity<byte[]> employees(
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long employeeId) {
        String csv = catalogService.employeeMasterCsv(locationId, departmentId, teamId, employeeId);
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        archive("EMPLOYEE_MASTER", null, employeeId, "employees.csv", bytes.length);
        return CsvSupport.download(csv, "employees.csv");
    }

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

    private void archive(String type, String period, Long employeeId, String filename, int size) {
        ReportExport archived = new ReportExport();
        archived.setReportType(type);
        archived.setPeriodLabel(period);
        archived.setEmployeeId(employeeId);
        archived.setFileName(filename);
        archived.setContentType("text/csv");
        archived.setSizeBytes(size);
        archived.setRequestedBy(AuthContext.getEmployeeId());
        archived.setStatus("READY");
        archived.setCompletedAt(Instant.now());
        archived.setCreatedAt(Instant.now());
        exportRepository.save(archived);
        auditService.record(AuthContext.getEmployeeId(), "CREATE", "ReportExport",
                String.valueOf(archived.getId()), null,
                Map.of("reportType", type, "period", period == null ? "" : period, "sizeBytes", size));
    }

    private static LocalDate parseOr(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return LocalDate.parse(raw.trim());
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be on/after from");
        }
        if (from.plusDays(366).isBefore(to)) {
            throw new IllegalArgumentException("Range must be 366 days or less");
        }
    }
}
