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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FR-RP-01 — monthly attendance register: one row per person, one column per day + status totals.
 */
@Service
public class AttendanceReportService {

    private static final String[] TOTAL_KEYS = {"P", "L", "HD", "A", "M", "OL", "WO", "HO", "NS", "NE", "W", "NC"};

    private final HrmsGatewayClient hrms;
    private final ReportScope scope;
    private final AuditService auditService;

    public AttendanceReportService(HrmsGatewayClient hrms, ReportScope scope, AuditService auditService) {
        this.hrms = hrms;
        this.scope = scope;
        this.auditService = auditService;
    }

    public boolean canListAllExports(Long actorId) {
        return hrms.hasPermission(actorId, "reports", "export")
                || hrms.hasPermission(actorId, "payroll", "run")
                || hrms.hasPermission(actorId, "employees", "edit");
    }

    public String generateMonthlyCsv(AttendanceReportRequest req) {
        Long actor = AuthContext.getEmployeeId();
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing employee identity");
        }

        YearMonth ym = YearMonth.of(req.year(), req.month());
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        int daysInMonth = ym.lengthOfMonth();

        List<JsonNode> people = scope.employees(
                actor, req.employeeId(), req.locationId(), req.departmentId(), req.teamId());
        if (people.isEmpty()) {
            // OWN scope with no match still returns header-only file
            return header(daysInMonth).toString();
        }

        StringBuilder csv = header(daysInMonth);
        for (JsonNode emp : people) {
            Long id = ReportScope.asLong(emp.get("id"));
            if (id == null) {
                continue;
            }
            JsonNode book = hrms.employeeAttendanceDayBook(actor, id, from, to);
            Map<Integer, String> byDay = new LinkedHashMap<>();
            Map<String, Integer> totals = new LinkedHashMap<>();
            for (String k : TOTAL_KEYS) {
                totals.put(k, 0);
            }
            if (book != null && book.has("days") && book.get("days").isArray()) {
                book.get("days").forEach(d -> {
                    LocalDate date = parseDate(d.get("date"));
                    if (date == null || date.getYear() != ym.getYear() || date.getMonthValue() != ym.getMonthValue()) {
                        return;
                    }
                    String code = text(d, "statusCode");
                    if (code.isBlank()) {
                        code = "NS";
                    }
                    code = code.trim().toUpperCase(Locale.ROOT);
                    byDay.put(date.getDayOfMonth(), code);
                    totals.merge(code, 1, Integer::sum);
                });
            }
            csv.append(id).append(',')
                    .append(CsvSupport.cell(ReportScope.text(emp, "employeeCode"))).append(',')
                    .append(CsvSupport.cell(ReportScope.text(emp, "fullName")));
            for (int day = 1; day <= daysInMonth; day++) {
                String code = byDay.getOrDefault(day, "");
                csv.append(',').append(CsvSupport.cell(code));
            }
            for (String k : TOTAL_KEYS) {
                csv.append(',').append(totals.getOrDefault(k, 0));
            }
            csv.append('\n');
        }

        auditService.record(actor, "EXPORT", "AttendanceMonthly", null, null,
                Map.of("period", ym.toString(), "rows", people.size()));
        return csv.toString();
    }

    private static StringBuilder header(int daysInMonth) {
        StringBuilder csv = new StringBuilder();
        csv.append("employeeId,employeeCode,fullName");
        for (int d = 1; d <= daysInMonth; d++) {
            csv.append(',').append('D').append(d);
        }
        for (String k : TOTAL_KEYS) {
            csv.append(',').append(k);
        }
        csv.append('\n');
        return csv;
    }

    private static LocalDate parseDate(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        String s = n.asText("");
        if (s.length() >= 10) {
            s = s.substring(0, 10);
        }
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return "";
        }
        return n.get(field).asText("");
    }
}
