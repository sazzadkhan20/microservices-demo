package com.platform.report.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.common.tenant.TenantContext;
import com.platform.report.api.dto.AttendanceReportRequest;
import com.platform.report.application.AttendanceReportService;
import com.platform.report.domain.ReportExport;
import com.platform.report.infrastructure.persistence.ReportExportRepository;
import com.platform.report.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public ReportController(AttendanceReportService attendanceService, ReportExportRepository exportRepository) {
        this.attendanceService = attendanceService;
        this.exportRepository = exportRepository;
    }

    /**
     * Generates a monthly attendance report and returns it as a CSV download.
     * Metadata is archived in the tenant's {@code reports_*} database.
     */
    @PostMapping("/attendance/monthly")
    @RequiresPermission(feature = "reports", action = "view")
    public ResponseEntity<byte[]> attendanceMonthly(
            @Valid @RequestBody AttendanceReportRequest req) {

        String tenantId = TenantContext.getTenantId();
        String csv = attendanceService.generateMonthlyCsv(tenantId, req);
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String filename = String.format("attendance-%04d-%02d.csv", req.year(), req.month());
        String period = String.format("%04d-%02d", req.year(), req.month());

        ReportExport archived = new ReportExport();
        archived.setReportType("ATTENDANCE_MONTHLY");
        archived.setPeriodLabel(period);
        archived.setEmployeeId(req.employeeId());
        archived.setFileName(filename);
        archived.setContentType("text/csv");
        archived.setSizeBytes(bytes.length);
        archived.setRequestedBy(AuthContext.getEmployeeId());
        archived.setCreatedAt(Instant.now());
        exportRepository.save(archived);

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
        List<Map<String, Object>> rows = exportRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("reportType", e.getReportType());
                    m.put("periodLabel", e.getPeriodLabel());
                    m.put("employeeId", e.getEmployeeId());
                    m.put("fileName", e.getFileName());
                    m.put("sizeBytes", e.getSizeBytes());
                    m.put("requestedBy", e.getRequestedBy());
                    m.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(rows);
    }
}
