package com.platform.report.application;

import com.platform.report.api.dto.AttendanceReportRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Generates attendance reports as CSV.
 *
 * <p>v1 strategy:
 * <ol>
 *   <li>If {@code HRMS_BASE_URL} is configured, it is available for live data integration.</li>
 *   <li>Otherwise (or for quick local dev), a placeholder CSV with realistic headers and
 *       synthetic rows is returned so the frontend can validate the download flow.</li>
 * </ol>
 */
@Service
public class AttendanceReportService {

    private static final String CSV_HEADER = "employeeId,date,status,checkIn,checkOut\n";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final String hrmsBaseUrl;

    public AttendanceReportService(
            @Value("${report.hrms-base-url:}") String hrmsBaseUrl) {
        this.hrmsBaseUrl = hrmsBaseUrl;
    }

    /**
     * Generates or retrieves a monthly attendance CSV for the given request.
     *
     * @param tenantId current tenant
     * @param req      report parameters
     * @return raw CSV string (UTF-8, CRLF line endings)
     */
    public String generateMonthlyCsv(String tenantId, AttendanceReportRequest req) {
        // Future: call hrmsBaseUrl if configured to fetch real attendance records.
        // For v1 we always generate a placeholder CSV.
        return buildPlaceholderCsv(req);
    }

    // --- CSV builder ---

    private String buildPlaceholderCsv(AttendanceReportRequest req) {
        YearMonth ym = YearMonth.of(req.year(), req.month());
        StringBuilder sb = new StringBuilder(CSV_HEADER);

        // Produce rows for each day in the month
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate date = ym.atDay(day);
            boolean isWeekend = date.getDayOfWeek().getValue() >= 6;

            String status = isWeekend ? "WEEKEND" : "PRESENT";
            String checkIn = isWeekend ? "" : "09:00";
            String checkOut = isWeekend ? "" : "18:00";

            // If a specific employee was requested, emit one row per day for that employee.
            // Otherwise emit a sample row for a placeholder employee (emp-1).
            long empId = req.employeeId() != null ? req.employeeId() : 1L;

            sb.append(empId).append(',')
              .append(date.format(DATE_FMT)).append(',')
              .append(status).append(',')
              .append(checkIn).append(',')
              .append(checkOut).append('\n');
        }

        return sb.toString();
    }
}
