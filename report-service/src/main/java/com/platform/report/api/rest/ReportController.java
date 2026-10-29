package com.platform.report.api.rest;

import com.platform.common.tenant.TenantContext;
import com.platform.report.api.dto.AttendanceReportRequest;
import com.platform.report.application.AttendanceReportService;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final AttendanceReportService attendanceService;

    public ReportController(AttendanceReportService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /**
     * Generates a monthly attendance report and returns it as a CSV download.
     *
     * <p>Request body: {@code { year, month, employeeId? }}
     * Response: {@code text/csv} attachment named {@code attendance-YYYY-MM.csv}
     */
    @PostMapping("/attendance/monthly")
    @RequiresPermission(feature = "reports", action = "view")
    public ResponseEntity<byte[]> attendanceMonthly(
            @Valid @RequestBody AttendanceReportRequest req) {

        String tenantId = TenantContext.getTenantId();
        String csv = attendanceService.generateMonthlyCsv(tenantId, req);
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String filename = String.format("attendance-%04d-%02d.csv", req.year(), req.month());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(bytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }

    /** Archived exports are not stored yet — empty list is intentional (on-demand only). */
    @GetMapping("/exports")
    @RequiresPermission(feature = "reports", action = "view")
    public ResponseEntity<List<Map<String, Object>>> listExports() {
        return ResponseEntity.ok(List.of());
    }
}
