package com.platform.report.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.common.security.AuthContext;
import com.platform.report.infrastructure.HrmsGatewayClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FR-RP-02..06 CSV builders. Attendance matrix lives in {@link AttendanceReportService}.
 */
@Service
public class ReportCatalogService {

    private final HrmsGatewayClient hrms;
    private final ReportScope scope;
    private final AuditService auditService;

    public ReportCatalogService(HrmsGatewayClient hrms, ReportScope scope, AuditService auditService) {
        this.hrms = hrms;
        this.scope = scope;
        this.auditService = auditService;
    }

    /** FR-RP-02 — per-person late/absence summary for a date range. */
    public String lateAbsenceSummaryCsv(
            LocalDate from, LocalDate to,
            Long locationId, Long departmentId, Long teamId, Long employeeId) {
        Long actor = AuthContext.requireEmployeeId();
        List<JsonNode> people = scope.employees(actor, employeeId, locationId, departmentId, teamId);
        Set<Long> allow = new LinkedHashSet<>();
        Map<Long, JsonNode> byId = new HashMap<>();
        for (JsonNode e : people) {
            Long id = ReportScope.asLong(e.get("id"));
            if (id != null) {
                allow.add(id);
                byId.put(id, e);
            }
        }

        JsonNode wh = hrms.workHours(actor, from, to, locationId, teamId, null);
        Map<Long, JsonNode> rowsById = new HashMap<>();
        if (wh != null && wh.has("rows") && wh.get("rows").isArray()) {
            wh.get("rows").forEach(r -> {
                Long id = ReportScope.asLong(r.get("employeeId"));
                if (id != null && allow.contains(id)) {
                    rowsById.put(id, r);
                }
            });
        }

        StringBuilder csv = new StringBuilder();
        csv.append("employeeId,employeeCode,fullName,workingDays,late,absent,missingCheckout,halfDay,leaveDays,latePercent\n");
        for (Long id : allow) {
            JsonNode emp = byId.get(id);
            JsonNode r = rowsById.get(id);
            int working = intOf(r, "workingDays");
            if (working == 0) {
                // Older work-hours payloads without workingDays — approximate from counters.
                working = intOf(r, "daysPresent") + intOf(r, "absences") + intOf(r, "leaveDays");
            }
            int late = intOf(r, "lateArrivals");
            int absent = intOf(r, "absences");
            int missing = intOf(r, "missingCheckouts");
            int half = intOf(r, "halfDays");
            int leave = intOf(r, "leaveDays");
            double latePct = working > 0 ? Math.round((late * 1000.0) / working) / 10.0 : 0.0;
            csv.append(id).append(',')
                    .append(CsvSupport.cell(ReportScope.text(emp, "employeeCode"))).append(',')
                    .append(CsvSupport.cell(ReportScope.text(emp, "fullName"))).append(',')
                    .append(working).append(',')
                    .append(late).append(',')
                    .append(absent).append(',')
                    .append(missing).append(',')
                    .append(half).append(',')
                    .append(leave).append(',')
                    .append(latePct)
                    .append('\n');
        }
        auditService.record(actor, "EXPORT", "LateAbsenceSummary", null, null,
                Map.of("from", from.toString(), "to", to.toString(), "rows", allow.size()));
        return csv.toString();
    }

    /** FR-RP-03 — leave balances with quota fields when present. */
    public String leaveBalancesCsv(
            int year,
            Long locationId, Long departmentId, Long teamId, Long employeeId) {
        Long actor = AuthContext.requireEmployeeId();
        List<JsonNode> people = scope.employees(actor, employeeId, locationId, departmentId, teamId);
        Map<Long, JsonNode> byId = new HashMap<>();
        Set<Long> allow = new LinkedHashSet<>();
        for (JsonNode e : people) {
            Long id = ReportScope.asLong(e.get("id"));
            if (id != null) {
                allow.add(id);
                byId.put(id, e);
            }
        }
        List<JsonNode> balances = hrms.leaveBalances(actor, year);

        StringBuilder csv = new StringBuilder();
        csv.append("employeeId,employeeCode,fullName,leaveTypeCode,quota,carried,adjusted,taken,pending,remaining\n");
        int rows = 0;
        for (JsonNode b : balances) {
            Long id = ReportScope.asLong(b.get("employeeId"));
            if (id == null || !allow.contains(id)) {
                continue;
            }
            int y = b.path("year").asInt(year);
            if (y != year) {
                continue;
            }
            JsonNode emp = byId.get(id);
            String code = ReportScope.text(b, "leaveTypeCode");
            if (code.isBlank()) {
                code = ReportScope.text(b, "code");
            }
            csv.append(id).append(',')
                    .append(CsvSupport.cell(ReportScope.text(emp, "employeeCode"))).append(',')
                    .append(CsvSupport.cell(ReportScope.text(emp, "fullName"))).append(',')
                    .append(CsvSupport.cell(code)).append(',')
                    .append(CsvSupport.cell(num(b, "quotaDays", "openingDays", "entitlementDays"))).append(',')
                    .append(CsvSupport.cell(num(b, "carriedDays", "carryForwardDays"))).append(',')
                    .append(CsvSupport.cell(num(b, "adjustedDays", "adjustmentDays"))).append(',')
                    .append(CsvSupport.cell(num(b, "takenDays", "usedDays"))).append(',')
                    .append(CsvSupport.cell(num(b, "pendingDays"))).append(',')
                    .append(CsvSupport.cell(num(b, "balanceDays", "remainingDays", "availableDays")))
                    .append('\n');
            rows++;
        }
        auditService.record(actor, "EXPORT", "LeaveBalances", null, null,
                Map.of("year", year, "people", people.size(), "rows", rows));
        return csv.toString();
    }

    /** FR-RP-04 — leave requests log (approvals when present on payload). */
    public String leaveRequestsCsv(
            LocalDate from, LocalDate to,
            Long locationId, Long departmentId, Long teamId, Long employeeId) {
        Long actor = AuthContext.requireEmployeeId();
        List<JsonNode> people = scope.employees(actor, employeeId, locationId, departmentId, teamId);
        Map<Long, JsonNode> byId = new HashMap<>();
        Set<Long> allow = new LinkedHashSet<>();
        for (JsonNode e : people) {
            Long id = ReportScope.asLong(e.get("id"));
            if (id != null) {
                allow.add(id);
                byId.put(id, e);
            }
        }
        List<JsonNode> apps = hrms.leaveApplications(actor, from, to);
        StringBuilder csv = new StringBuilder();
        csv.append("id,employeeId,employeeCode,fullName,leaveTypeCode,startDate,endDate,days,status,reason,approvers,decisions,decisionTimes\n");
        int rows = 0;
        for (JsonNode a : apps) {
            Long id = ReportScope.asLong(a.get("employeeId"));
            if (id == null || !allow.contains(id)) {
                continue;
            }
            LocalDate start = parseDate(a.get("startDate"));
            if (start == null) {
                continue;
            }
            JsonNode emp = byId.get(id);
            String approvers = "";
            String decisions = "";
            String times = "";
            if (a.has("approvals") && a.get("approvals").isArray()) {
                List<String> ap = new ArrayList<>();
                List<String> dec = new ArrayList<>();
                List<String> tm = new ArrayList<>();
                a.get("approvals").forEach(step -> {
                    ap.add(textOr(step, "actorName", textOr(step, "approverRole", "")));
                    dec.add(textOr(step, "status", ""));
                    tm.add(textOr(step, "decidedAt", ""));
                });
                approvers = String.join(" | ", ap);
                decisions = String.join(" | ", dec);
                times = String.join(" | ", tm);
            }
            csv.append(ReportScope.asLong(a.get("id"))).append(',')
                    .append(id).append(',')
                    .append(CsvSupport.cell(ReportScope.text(emp, "employeeCode"))).append(',')
                    .append(CsvSupport.cell(ReportScope.text(emp, "fullName"))).append(',')
                    .append(CsvSupport.cell(ReportScope.text(a, "leaveTypeCode"))).append(',')
                    .append(CsvSupport.cell(start)).append(',')
                    .append(CsvSupport.cell(parseDate(a.get("endDate")))).append(',')
                    .append(CsvSupport.cell(textOr(a, "workingDaysCounted", ""))).append(',')
                    .append(CsvSupport.cell(ReportScope.text(a, "status"))).append(',')
                    .append(CsvSupport.cell(ReportScope.text(a, "reason"))).append(',')
                    .append(CsvSupport.cell(approvers)).append(',')
                    .append(CsvSupport.cell(decisions)).append(',')
                    .append(CsvSupport.cell(times))
                    .append('\n');
            rows++;
        }
        auditService.record(actor, "EXPORT", "LeaveRequests", null, null,
                Map.of("from", from.toString(), "to", to.toString(), "rows", rows));
        return csv.toString();
    }

    /**
     * FR-RP-05 — month-end headcount, joiners, leavers for last 6 months.
     * <p>Departure date is {@code exitDate} (offboarding). {@code contractEnd} is only a
     * fallback when the person is already inactive and exitDate is missing — never treat a
     * future contract end on an active employee as a leaver.
     */
    public String headcountCsv(Long locationId, Long departmentId, Long teamId) {
        Long actor = AuthContext.requireEmployeeId();
        List<JsonNode> all = scope.employees(actor, null, locationId, departmentId, teamId);
        LocalDate today = LocalDate.now();
        StringBuilder csv = new StringBuilder();
        csv.append("yearMonth,joiners,leavers,activeAtEnd\n");
        for (int i = 5; i >= 0; i--) {
            LocalDate monthStart = today.minusMonths(i).withDayOfMonth(1);
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
            long joiners = all.stream().filter(e -> {
                LocalDate s = parseDate(e.get("startDate"));
                return s != null && !s.isBefore(monthStart) && !s.isAfter(monthEnd);
            }).count();
            long leavers = all.stream().filter(e -> {
                LocalDate left = departureDate(e);
                return left != null && !left.isBefore(monthStart) && !left.isAfter(monthEnd);
            }).count();
            long activeAtEnd = all.stream().filter(e -> wasEmployedAt(e, monthEnd)).count();

            csv.append(monthStart.getYear()).append('-')
                    .append(String.format("%02d", monthStart.getMonthValue())).append(',')
                    .append(joiners).append(',')
                    .append(leavers).append(',')
                    .append(activeAtEnd).append('\n');
        }
        auditService.record(actor, "EXPORT", "Headcount", null, null,
                Map.of("months", 6, "people", all.size()));
        return csv.toString();
    }

    /**
     * True when the person had started by {@code asOf} and had not yet left
     * (exit/leave date strictly after {@code asOf}, or no departure date).
     */
    static boolean wasEmployedAt(JsonNode e, LocalDate asOf) {
        if (e == null || asOf == null) {
            return false;
        }
        LocalDate start = parseDate(e.get("startDate"));
        if (start != null && start.isAfter(asOf)) {
            return false;
        }
        LocalDate left = departureDate(e);
        // Left on/before asOf → not in month-end headcount.
        return left == null || left.isAfter(asOf);
    }

    /**
     * Actual departure date for headcount: prefer offboarding {@code exitDate}; fall back to
     * {@code contractEnd} only for people already marked inactive without an exit date.
     */
    static LocalDate departureDate(JsonNode e) {
        if (e == null) {
            return null;
        }
        LocalDate exit = parseDate(e.get("exitDate"));
        if (exit != null) {
            return exit;
        }
        if (e.has("customFields") && e.get("customFields").isObject()) {
            exit = parseDate(e.get("customFields").get("exitDate"));
            if (exit != null) {
                return exit;
            }
        }
        if (!ReportScope.isActive(e)) {
            return parseDate(e.get("contractEnd"));
        }
        return null;
    }

    /** FR-RP-06 — employee master list. */
    public String employeeMasterCsv(Long locationId, Long departmentId, Long teamId, Long employeeId) {
        Long actor = AuthContext.requireEmployeeId();
        List<JsonNode> people = scope.employees(actor, employeeId, locationId, departmentId, teamId);
        StringBuilder csv = new StringBuilder();
        csv.append("id,employeeCode,fullName,email,status,active,employmentType,workPattern,payType,")
                .append("departmentId,designationId,teamId,locationId,managerId,")
                .append("startDate,probationEnd,contractEnd,exitDate,exitReason,jobTitle,phone\n");
        for (JsonNode e : people) {
            JsonNode custom = e.has("customFields") ? e.get("customFields") : null;
            String payType = custom != null && custom.has("payType") ? custom.get("payType").asText("") : "";
            String exitReason = ReportScope.text(e, "exitReason");
            if (exitReason.isBlank() && custom != null && custom.has("exitReason")) {
                exitReason = custom.get("exitReason").asText("");
            }
            csv.append(CsvSupport.cell(ReportScope.asLong(e.get("id")))).append(',')
                    .append(CsvSupport.cell(ReportScope.text(e, "employeeCode"))).append(',')
                    .append(CsvSupport.cell(ReportScope.text(e, "fullName"))).append(',')
                    .append(CsvSupport.cell(ReportScope.text(e, "email"))).append(',')
                    .append(CsvSupport.cell(ReportScope.text(e, "status"))).append(',')
                    .append(ReportScope.isActive(e)).append(',')
                    .append(CsvSupport.cell(ReportScope.text(e, "employmentType"))).append(',')
                    .append(CsvSupport.cell(ReportScope.text(e, "workPattern"))).append(',')
                    .append(CsvSupport.cell(payType)).append(',')
                    .append(CsvSupport.cell(ReportScope.asLong(e.get("departmentId")))).append(',')
                    .append(CsvSupport.cell(ReportScope.asLong(e.get("designationId")))).append(',')
                    .append(CsvSupport.cell(ReportScope.asLong(e.get("teamId")))).append(',')
                    .append(CsvSupport.cell(ReportScope.asLong(e.get("locationId")))).append(',')
                    .append(CsvSupport.cell(ReportScope.asLong(e.get("managerId")))).append(',')
                    .append(CsvSupport.cell(parseDate(e.get("startDate")))).append(',')
                    .append(CsvSupport.cell(parseDate(e.get("probationEnd")))).append(',')
                    .append(CsvSupport.cell(parseDate(e.get("contractEnd")))).append(',')
                    .append(CsvSupport.cell(departureDate(e))).append(',')
                    .append(CsvSupport.cell(exitReason)).append(',')
                    .append(CsvSupport.cell(ReportScope.text(e, "jobTitle"))).append(',')
                    .append(CsvSupport.cell(ReportScope.text(e, "phone")))
                    .append('\n');
        }
        auditService.record(actor, "EXPORT", "EmployeeMaster", null, null, Map.of("rows", people.size()));
        return csv.toString();
    }

    private static int intOf(JsonNode r, String field) {
        if (r == null || !r.has(field) || r.get(field).isNull()) {
            return 0;
        }
        return r.get(field).asInt(0);
    }

    private static String num(JsonNode b, String... fields) {
        if (b == null) {
            return "0";
        }
        for (String f : fields) {
            if (b.has(f) && !b.get(f).isNull()) {
                return b.get(f).asText("0");
            }
        }
        return "0";
    }

    private static String textOr(JsonNode n, String field, String fallback) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return fallback;
        }
        String s = n.get(field).asText("");
        return s == null || s.isBlank() ? fallback : s;
    }

    static LocalDate parseDate(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isArray() && n.size() >= 3) {
            return LocalDate.of(n.get(0).asInt(), n.get(1).asInt(), n.get(2).asInt());
        }
        String s = n.asText("");
        if (s == null || s.isBlank()) {
            return null;
        }
        if (s.length() >= 10) {
            s = s.substring(0, 10);
        }
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
