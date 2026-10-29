package com.platform.report.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.common.security.AuthContext;
import com.platform.report.api.dto.AttendanceReportRequest;
import com.platform.report.infrastructure.HrmsGatewayClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Monthly attendance CSV sourced from HRMS attendance history (not synthetic placeholders).
 */
@Service
public class AttendanceReportService {

    private static final String CSV_HEADER = "employeeId,date,status,checkIn,checkOut\n";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final HrmsGatewayClient hrms;

    public AttendanceReportService(HrmsGatewayClient hrms) {
        this.hrms = hrms;
    }

    public String generateMonthlyCsv(String tenantId, AttendanceReportRequest req) {
        Long actor = AuthContext.getEmployeeId();
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing employee identity");
        }
        Long target = req.employeeId() == null ? actor : req.employeeId();
        // Without reports:export / org-wide rights, only own report (permission interceptor already
        // required reports:view; export of others needs reports:export or being self).
        if (!target.equals(actor) && !hrms.hasPermission(actor, "reports", "export")
                && !hrms.hasPermission(actor, "payroll", "run")
                && !hrms.hasPermission(actor, "employees", "edit")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Forbidden: reports:export required for other employees");
        }

        YearMonth ym = YearMonth.of(req.year(), req.month());
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        List<JsonNode> rows = hrms.attendanceForEmployee(target, 1, 100);
        Map<LocalDate, JsonNode> byDate = new HashMap<>();
        for (JsonNode row : rows) {
            LocalDate d = parseDate(row);
            if (d != null && !d.isBefore(from) && !d.isAfter(to)) {
                byDate.put(d, row);
            }
        }

        StringBuilder sb = new StringBuilder(CSV_HEADER);
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            JsonNode row = byDate.get(d);
            String status;
            String checkIn = "";
            String checkOut = "";
            if (row != null) {
                status = text(row, "status", text(row, "statusCode", "RECORDED"));
                checkIn = timeOf(row, "checkIn", "checkInAt");
                checkOut = timeOf(row, "checkOut", "checkOutAt");
            } else {
                status = "NO_RECORD";
            }
            sb.append(target).append(',')
                    .append(d.format(DATE_FMT)).append(',')
                    .append(csv(status)).append(',')
                    .append(csv(checkIn)).append(',')
                    .append(csv(checkOut)).append('\n');
        }
        return sb.toString();
    }

    private static LocalDate parseDate(JsonNode row) {
        if (row == null) return null;
        JsonNode n = row.get("workDate");
        if (n == null) n = row.get("date");
        if (n == null) return null;
        if (n.isArray() && n.size() >= 3) {
            return LocalDate.of(n.get(0).asInt(), n.get(1).asInt(), n.get(2).asInt());
        }
        String s = n.asText(null);
        if (s == null || s.isBlank()) return null;
        if (s.length() >= 10) s = s.substring(0, 10);
        return LocalDate.parse(s);
    }

    private static String timeOf(JsonNode row, String a, String b) {
        String v = text(row, a, text(row, b, ""));
        if (v == null || v.isBlank()) return "";
        // Instant → HH:mm locally-ish from ISO string
        if (v.contains("T")) {
            int t = v.indexOf('T');
            String tod = v.substring(t + 1);
            if (tod.length() >= 5) return tod.substring(0, 5);
        }
        return v.length() >= 5 ? v.substring(0, 5) : v;
    }

    private static String text(JsonNode row, String field, String fallback) {
        if (row == null || !row.has(field) || row.get(field).isNull()) return fallback;
        return row.get(field).asText(fallback);
    }

    private static String csv(String v) {
        if (v == null) return "";
        String s = v.replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s + "\"";
        }
        return s;
    }
}
