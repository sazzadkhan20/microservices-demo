package com.platform.report.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.common.security.AuthContext;
import com.platform.report.api.dto.AttendanceReportRequest;
import com.platform.report.infrastructure.HrmsGatewayClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monthly attendance CSV sourced from HRMS attendance history (not synthetic placeholders).
 */
@Service
public class AttendanceReportService {

    private static final String CSV_HEADER = "employeeId,date,status,checkIn,checkOut\n";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    /** Default when attendance row has no timeZone (Qii / BD tenants). */
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Dhaka");

    private final HrmsGatewayClient hrms;

    public AttendanceReportService(HrmsGatewayClient hrms) {
        this.hrms = hrms;
    }

    public boolean canListAllExports(Long actorId) {
        return hrms.hasPermission(actorId, "reports", "export")
                || hrms.hasPermission(actorId, "payroll", "run")
                || hrms.hasPermission(actorId, "employees", "edit");
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

        List<JsonNode> rows = hrms.attendanceForEmployee(actor, target, 1, 500);
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
                ZoneId zone = zoneOf(row);
                // Prefer HRMS local wall-clock fields (already in tenant TZ); else Instant → zone.
                checkIn = localTimeOf(row, zone, "checkInLocal", "checkIn", "checkInAt");
                checkOut = localTimeOf(row, zone, "checkOutLocal", "checkOut", "checkOutAt");
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

    /**
     * Format punch time in the employee's timezone.
     * Bug fixed: Instant.toString() is UTC ({@code 04:05Z}); slicing HH:mm showed UTC and looked
     * like ±6h vs Asia/Dhaka local time on the attendance UI.
     */
    private static String localTimeOf(JsonNode row, ZoneId zone, String localField, String... instantFields) {
        String local = text(row, localField, "");
        if (local != null && !local.isBlank()) {
            return normalizeClock(local);
        }
        for (String field : instantFields) {
            String v = text(row, field, "");
            if (v == null || v.isBlank()) {
                continue;
            }
            Instant instant = parseInstant(v);
            if (instant != null) {
                return LocalTime.ofInstant(instant, zone).format(TIME_FMT);
            }
            // Already a bare clock (HH:mm / HH:mm:ss) with no offset — keep as-is.
            if (!v.contains("T") && v.length() >= 4 && v.charAt(0) >= '0' && v.charAt(0) <= '9') {
                return normalizeClock(v);
            }
        }
        return "";
    }

    private static Instant parseInstant(String v) {
        try {
            if (v.contains("T") || v.endsWith("Z") || v.contains("+")) {
                return Instant.parse(v);
            }
            // epoch seconds (with optional fraction) as used by some Jackson Instant codecs
            if (v.matches("^\\d+(\\.\\d+)?$")) {
                double n = Double.parseDouble(v);
                long seconds = (long) n;
                return Instant.ofEpochSecond(seconds, (long) ((n - seconds) * 1_000_000_000L));
            }
        } catch (DateTimeParseException | NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    private static String normalizeClock(String v) {
        String s = v.trim();
        // HH:mm:ss.sss → HH:mm
        if (s.length() >= 5 && s.charAt(2) == ':') {
            return s.substring(0, 5);
        }
        return s;
    }

    private static ZoneId zoneOf(JsonNode row) {
        String tz = text(row, "timeZone", text(row, "timezone", ""));
        if (tz != null && !tz.isBlank()) {
            try {
                return ZoneId.of(tz.trim());
            } catch (Exception ignored) {
                // fall through
            }
        }
        return DEFAULT_ZONE;
    }

    private static String text(JsonNode row, String field, String fallback) {
        if (row == null || !row.has(field) || row.get(field).isNull()) return fallback;
        JsonNode n = row.get(field);
        if (n.isNumber()) {
            return n.asText();
        }
        return n.asText(fallback);
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
