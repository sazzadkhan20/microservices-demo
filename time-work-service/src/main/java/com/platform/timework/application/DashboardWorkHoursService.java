package com.platform.timework.application;

import com.platform.common.security.AuthContext;
import com.platform.timework.client.LeaveClient;
import com.platform.timework.client.people.EmployeeView;
import com.platform.timework.client.people.PeopleEmployeeGateway;
import com.platform.timework.domain.AttendanceRecord;
import com.platform.timework.domain.Holiday;
import com.platform.timework.domain.WorkLog;
import com.platform.timework.domain.WorkSchedule;
import com.platform.timework.infrastructure.persistence.AttendanceRecordRepository;
import com.platform.timework.infrastructure.persistence.HolidayRepository;
import com.platform.timework.infrastructure.persistence.WorkLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Fast admin work-hours dashboard: one scope pass, batch attendance/logs/leave —
 * never N×days of {@code teamForDate}. Supports search + pagination.
 */
@Service
public class DashboardWorkHoursService {

    private final PeopleEmployeeGateway employeeRepository;
    private final DataScopeService dataScopeService;
    private final AttendanceRecordRepository attendanceRepository;
    private final WorkLogRepository workLogRepository;
    private final WorkScheduleService workScheduleService;
    private final LeaveClient leaveClient;
    private final HolidayRepository holidayRepository;

    public DashboardWorkHoursService(
            PeopleEmployeeGateway employeeRepository,
            DataScopeService dataScopeService,
            AttendanceRecordRepository attendanceRepository,
            WorkLogRepository workLogRepository,
            WorkScheduleService workScheduleService,
            LeaveClient leaveClient,
            HolidayRepository holidayRepository) {
        this.employeeRepository = employeeRepository;
        this.dataScopeService = dataScopeService;
        this.attendanceRepository = attendanceRepository;
        this.workLogRepository = workLogRepository;
        this.workScheduleService = workScheduleService;
        this.leaveClient = leaveClient;
        this.holidayRepository = holidayRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> workHours(
            LocalDate from,
            LocalDate to,
            Long locationId,
            Long teamId,
            String sort,
            String q,
            int page,
            int size) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be on/after from");
        }
        if (from.plusDays(92).isBefore(to)) {
            throw new IllegalArgumentException("Range must be 93 days or less");
        }
        Long actor = AuthContext.requireEmployeeId();
        int pageSafe = Math.max(1, page);
        int sizeSafe = Math.min(100, Math.max(5, size));
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        DataScopeService.Visibility vis = dataScopeService.visibility(actor, "attendance", "view");
        List<EmployeeView> emps = new ArrayList<>();
        for (EmployeeView e : employeeRepository.findAll()) {
            if (e == null || e.getId() == null || !e.isActive() || !vis.allows(e.getId())) {
                continue;
            }
            if (locationId != null && !locationId.equals(e.getLocationId())) {
                continue;
            }
            if (teamId != null && !teamId.equals(e.getTeamId())) {
                continue;
            }
            if (!needle.isEmpty()) {
                String name = e.getFullName() == null ? "" : e.getFullName().toLowerCase(Locale.ROOT);
                String code = e.getEmployeeCode() == null ? "" : e.getEmployeeCode().toLowerCase(Locale.ROOT);
                if (!name.contains(needle) && !code.contains(needle) && !String.valueOf(e.getId()).contains(needle)) {
                    continue;
                }
            }
            emps.add(e);
        }

        List<Long> ids = emps.stream().map(EmployeeView::getId).toList();
        Map<Long, Map<LocalDate, AttendanceRecord>> attByEmp = indexAttendance(
                ids.isEmpty() ? List.of() : attendanceRepository.findByEmployeeIdInAndWorkDateBetween(ids, from, to));
        Map<Long, Map<LocalDate, Integer>> logByEmp = indexLogs(
                ids.isEmpty() ? List.of() : workLogRepository.findByEmployeeIdInAndWorkDateBetween(ids, from, to));
        Map<Long, List<Map<String, Object>>> leaveByEmp = ids.isEmpty()
                ? Map.of()
                : safeLeaveBatch(ids, from, to);
        Set<String> holidayKeys = loadHolidayKeys(from, to);
        Map<Long, Optional<WorkSchedule>> scheduleCache = new HashMap<>();

        List<Map<String, Object>> rows = new ArrayList<>(emps.size());
        Totals totals = new Totals();
        for (EmployeeView emp : emps) {
            RowAcc acc = buildRow(emp, from, to, attByEmp, logByEmp, leaveByEmp, holidayKeys, scheduleCache);
            Map<String, Object> row = acc.toRow();
            rows.add(row);
            totals.add(acc);
        }
        sortRows(rows, sort);

        int totalElements = rows.size();
        int fromIdx = Math.min((pageSafe - 1) * sizeSafe, totalElements);
        int toIdx = Math.min(fromIdx + sizeSafe, totalElements);
        List<Map<String, Object>> pageRows = rows.subList(fromIdx, toIdx);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", from.toString());
        out.put("to", to.toString());
        out.put("scope", vis.scope());
        out.put("q", q == null ? "" : q);
        out.put("page", pageSafe);
        out.put("size", sizeSafe);
        out.put("totalElements", totalElements);
        out.put("totalPages", totalElements == 0 ? 0 : (totalElements + sizeSafe - 1) / sizeSafe);
        out.put("rowCount", pageRows.size());
        out.put("rows", pageRows);
        out.put("totals", totals.toMap());
        return out;
    }

    /**
     * Period day-book for one employee — same classification / hour math as {@link #workHours}.
     * Returns calendar-filled days so summary counts match the Work hours table row.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> employeeAttendance(Long employeeId, LocalDate from, LocalDate to) {
        if (employeeId == null) {
            throw new IllegalArgumentException("employeeId is required");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be on/after from");
        }
        if (from.plusDays(92).isBefore(to)) {
            throw new IllegalArgumentException("Range must be 93 days or less");
        }

        Long actor = AuthContext.requireEmployeeId();
        DataScopeService.Visibility vis = dataScopeService.visibility(actor, "attendance", "view");
        if (!vis.allows(employeeId)) {
            throw new IllegalStateException("Forbidden: employee outside attendance:view scope");
        }

        EmployeeView emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        List<Long> ids = List.of(employeeId);
        Map<Long, Map<LocalDate, AttendanceRecord>> attByEmp = indexAttendance(
                attendanceRepository.findByEmployeeIdInAndWorkDateBetween(ids, from, to));
        Map<Long, Map<LocalDate, Integer>> logByEmp = indexLogs(
                workLogRepository.findByEmployeeIdInAndWorkDateBetween(ids, from, to));
        Map<Long, List<Map<String, Object>>> leaveByEmp = safeLeaveBatch(ids, from, to);
        Set<String> holidayKeys = loadHolidayKeys(from, to);
        Map<Long, Optional<WorkSchedule>> scheduleCache = new HashMap<>();

        Map<LocalDate, AttendanceRecord> daysAtt = attByEmp.getOrDefault(employeeId, Map.of());
        Map<LocalDate, Integer> daysLog = logByEmp.getOrDefault(employeeId, Map.of());
        List<Map<String, Object>> leaveRows = leaveByEmp.getOrDefault(employeeId, List.of());

        RowAcc acc = new RowAcc();
        acc.employeeId = emp.getId();
        acc.employeeName = emp.getFullName() == null ? ("#" + emp.getId()) : emp.getFullName();
        acc.employeeCode = emp.getEmployeeCode() == null ? "" : emp.getEmployeeCode();
        acc.workPattern = emp.getWorkPattern() == null ? "FIXED" : emp.getWorkPattern();
        acc.locationId = emp.getLocationId();
        acc.teamId = emp.getTeamId();

        List<Map<String, Object>> days = new ArrayList<>();
        Map<String, Integer> byStatus = new LinkedHashMap<>();

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            LeaveClient.LeaveImpact leave = leaveOnDay(leaveRows, d);
            AttendanceRecord rec = daysAtt.get(d);
            int loggedMin = daysLog.getOrDefault(d, 0);
            DayFact fact = dayFact(emp, d, rec, leave, loggedMin, holidayKeys, scheduleCache);
            applyDay(acc, fact);

            byStatus.merge(fact.statusCode, 1, Integer::sum);

            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", d.toString());
            day.put("statusCode", fact.statusCode);
            day.put("statusLabel", statusLabel(fact.statusCode, rec));
            day.put("checkInAt", rec == null || rec.getCheckInAt() == null ? null : rec.getCheckInAt().toString());
            day.put("checkOutAt", rec == null || rec.getCheckOutAt() == null ? null : rec.getCheckOutAt().toString());
            day.put("expectedHours", round2(fact.expectedHours));
            day.put("workedHours", round2(fact.workedHours));
            day.put("loggedHours", round2(fact.loggedHours));
            day.put("missingWorkLog", fact.missingWorkLog);
            day.put("leaveImpact", leave.name());
            day.put("recordId", rec == null || rec.getId() == null ? null : String.valueOf(rec.getId()));
            // Punch GPS label — never surface placeholder/default client strings.
            day.put("punchLocation", cleanPunchLocation(rec == null ? null : rec.getLocationLabel()));
            days.add(day);
        }

        Optional<WorkSchedule> empSchedule = scheduleCache.getOrDefault(employeeId, Optional.empty());
        if (empSchedule.isEmpty()) {
            empSchedule = workScheduleService.resolveForEmployee(emp);
        }
        boolean hourly = workScheduleService.isHourlyEngagement(emp, empSchedule.orElse(null));

        Map<String, Object> employee = new LinkedHashMap<>();
        employee.put("id", emp.getId());
        employee.put("fullName", acc.employeeName);
        employee.put("employeeCode", acc.employeeCode);
        employee.put("workPattern", acc.workPattern);
        employee.put("hourly", hourly);
        employee.put("startDate", emp.getStartDate() == null ? null : emp.getStartDate().toString());
        employee.put("contractEnd", emp.getContractEnd() == null ? null : emp.getContractEnd().toString());
        employee.put("locationId", emp.getLocationId());
        employee.put("teamId", emp.getTeamId());
        employee.put("locationTimeZone", emp.getLocationTimeZone());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", from.toString());
        out.put("to", to.toString());
        out.put("scope", vis.scope());
        out.put("employee", employee);
        out.put("summary", acc.toRow());
        out.put("byStatus", byStatus);
        out.put("days", days);
        out.put("dayCount", days.size());
        return out;
    }

    private static String cleanPunchLocation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String label = raw.trim();
        if ("Location not shared".equalsIgnoreCase(label)
                || "Share my location".equalsIgnoreCase(label)
                || "Unknown".equalsIgnoreCase(label)
                || "—".equals(label)
                || "-".equals(label)) {
            return null;
        }
        return label;
    }

    /** Today's attendance KPIs — single day, batch leave, no per-employee HTTP leave calls. */
    @Transactional(readOnly = true)
    public Map<String, Object> attendanceToday(LocalDate date) {
        LocalDate d = date == null ? LocalDate.now() : date;
        Long actor = AuthContext.requireEmployeeId();
        DataScopeService.Visibility vis = dataScopeService.visibility(actor, "attendance", "view");

        List<EmployeeView> emps = new ArrayList<>();
        for (EmployeeView e : employeeRepository.findAll()) {
            if (e != null && e.getId() != null && e.isActive() && vis.allows(e.getId())) {
                emps.add(e);
            }
        }
        List<Long> ids = emps.stream().map(EmployeeView::getId).toList();
        Map<Long, AttendanceRecord> att = new HashMap<>();
        for (AttendanceRecord r : attendanceRepository.findByWorkDate(d)) {
            if (r.getEmployeeId() != null) {
                att.putIfAbsent(r.getEmployeeId(), r);
            }
        }
        Map<Long, List<Map<String, Object>>> leaveByEmp = ids.isEmpty()
                ? Map.of()
                : safeLeaveBatch(ids, d, d);
        Set<String> holidayKeys = loadHolidayKeys(d, d);
        Map<Long, Optional<WorkSchedule>> scheduleCache = new HashMap<>();

        int expected = 0;
        int checkedIn = 0;
        int onLeave = 0;
        int late = 0;
        int absent = 0;
        int half = 0;
        int missingCheckout = 0;
        int weekOff = 0;
        int holiday = 0;
        int present = 0;

        for (EmployeeView emp : emps) {
            LeaveClient.LeaveImpact leave = leaveOnDay(leaveByEmp.get(emp.getId()), d);
            AttendanceRecord rec = att.get(emp.getId());
            String code = classifyDay(emp, d, rec, leave, holidayKeys, scheduleCache);
            switch (code) {
                case "P", "W" -> {
                    checkedIn++;
                    present++;
                    expected++;
                }
                case "L" -> {
                    checkedIn++;
                    late++;
                    expected++;
                }
                case "HD" -> {
                    checkedIn++;
                    half++;
                    expected++;
                }
                case "OL" -> {
                    onLeave++;
                    expected++;
                }
                case "A", "NC" -> {
                    absent++;
                    expected++;
                }
                case "M" -> {
                    checkedIn++;
                    missingCheckout++;
                    expected++;
                }
                case "WO" -> weekOff++;
                case "HO" -> holiday++;
                default -> { /* NS */ }
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", d.toString());
        out.put("headcountInScope", emps.size());
        out.put("expectedWorking", expected);
        out.put("checkedIn", checkedIn);
        out.put("onLeave", onLeave);
        out.put("late", late);
        out.put("absent", absent);
        out.put("halfDay", half);
        out.put("missingCheckout", missingCheckout);
        out.put("weekOff", weekOff);
        out.put("holiday", holiday);
        out.put("byStatus", Map.of(
                "present", present,
                "late", late,
                "halfDay", half,
                "onLeave", onLeave,
                "absent", absent,
                "missingCheckout", missingCheckout,
                "weekOff", weekOff,
                "holiday", holiday
        ));
        return out;
    }

    private RowAcc buildRow(
            EmployeeView emp,
            LocalDate from,
            LocalDate to,
            Map<Long, Map<LocalDate, AttendanceRecord>> attByEmp,
            Map<Long, Map<LocalDate, Integer>> logByEmp,
            Map<Long, List<Map<String, Object>>> leaveByEmp,
            Set<String> holidayKeys,
            Map<Long, Optional<WorkSchedule>> scheduleCache) {
        RowAcc acc = new RowAcc();
        acc.employeeId = emp.getId();
        acc.employeeName = emp.getFullName() == null ? ("#" + emp.getId()) : emp.getFullName();
        acc.employeeCode = emp.getEmployeeCode() == null ? "" : emp.getEmployeeCode();
        acc.workPattern = emp.getWorkPattern() == null ? "FIXED" : emp.getWorkPattern();
        acc.locationId = emp.getLocationId();
        acc.teamId = emp.getTeamId();

        Map<LocalDate, AttendanceRecord> daysAtt = attByEmp.getOrDefault(emp.getId(), Map.of());
        Map<LocalDate, Integer> daysLog = logByEmp.getOrDefault(emp.getId(), Map.of());
        List<Map<String, Object>> leaveRows = leaveByEmp.getOrDefault(emp.getId(), List.of());

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            LeaveClient.LeaveImpact leave = leaveOnDay(leaveRows, d);
            AttendanceRecord rec = daysAtt.get(d);
            int loggedMin = daysLog.getOrDefault(d, 0);
            applyDay(acc, dayFact(emp, d, rec, leave, loggedMin, holidayKeys, scheduleCache));
        }
        return acc;
    }

    private DayFact dayFact(
            EmployeeView emp,
            LocalDate day,
            AttendanceRecord rec,
            LeaveClient.LeaveImpact leave,
            int loggedMin,
            Set<String> holidayKeys,
            Map<Long, Optional<WorkSchedule>> scheduleCache) {
        double expected = expectedHoursLocal(emp, day, leave, holidayKeys, scheduleCache);
        double worked = workedHours(rec);
        double logged = Math.round(loggedMin / 60.0 * 100.0) / 100.0;
        String code = classifyDay(emp, day, rec, leave, holidayKeys, scheduleCache);
        boolean present = "P".equals(code) || "L".equals(code) || "HD".equals(code)
                || "W".equals(code) || "M".equals(code);
        // No-log only when they actually punched in (open or closed) on a present-class day.
        boolean missingWorkLog = present && loggedMin <= 0
                && rec != null && rec.getCheckInAt() != null;
        return new DayFact(code, expected, worked, logged, present, missingWorkLog);
    }

    private static void applyDay(RowAcc acc, DayFact fact) {
        acc.expectedHours += fact.expectedHours;
        acc.workedHours += fact.workedHours;
        acc.loggedHours += fact.loggedHours;
        String code = fact.statusCode == null ? "" : fact.statusCode;
        // Working day = expected on-site / leave day (exclude week-off, holiday, pre-join, blank).
        if (!code.isBlank() && !"WO".equals(code) && !"HO".equals(code)
                && !"NS".equals(code) && !"NE".equals(code)) {
            acc.workingDays++;
        }
        if (fact.present) {
            acc.daysPresent++;
        }
        if ("L".equals(code)) {
            acc.lateArrivals++;
        }
        if ("HD".equals(code)) {
            acc.halfDays++;
        }
        if ("OL".equals(code)) {
            acc.leaveDays++;
        }
        if ("A".equals(code) || "NC".equals(code)) {
            acc.absences++;
        }
        if ("M".equals(code)) {
            acc.missingCheckouts++;
        }
        if (fact.missingWorkLog) {
            acc.missingWorkLogDays++;
        }
    }

    private static String statusLabel(String code, AttendanceRecord rec) {
        if (rec != null && rec.getStatusLabel() != null && !rec.getStatusLabel().isBlank()) {
            return rec.getStatusLabel().trim();
        }
        return switch (code == null ? "" : code.toUpperCase(Locale.ROOT)) {
            case "P" -> "Present";
            case "L" -> "Late";
            case "HD" -> "Half day";
            case "W" -> "Worked";
            case "M" -> "Missing checkout";
            case "OL" -> "On leave";
            case "A" -> "Absent";
            case "NC" -> "Not checked in";
            case "WO" -> "Week off";
            case "HO" -> "Holiday";
            case "NS" -> "No punch";
            case "NE" -> ""; // blank — before join / after employment end
            default -> code == null || code.isBlank() ? "—" : code;
        };
    }

    private record DayFact(
            String statusCode,
            double expectedHours,
            double workedHours,
            double loggedHours,
            boolean present,
            boolean missingWorkLog) {}

    /**
     * Outside the employment window: before {@code startDate}, or after {@code contractEnd}
     * when set. Those days must not count as absent / expected (production HR norm).
     */
    private static boolean outsideEmployment(EmployeeView emp, LocalDate day) {
        if (emp == null || day == null) {
            return false;
        }
        LocalDate start = emp.getStartDate();
        if (start != null && day.isBefore(start)) {
            return true;
        }
        LocalDate end = emp.getContractEnd();
        return end != null && day.isAfter(end);
    }

    private String classifyDay(
            EmployeeView emp,
            LocalDate day,
            AttendanceRecord rec,
            LeaveClient.LeaveImpact leave,
            Set<String> holidayKeys,
            Map<Long, Optional<WorkSchedule>> scheduleCache) {
        // Pre-join / post-contract: blank day — never Absent / Holiday / Leave / Week off.
        if (outsideEmployment(emp, day)) {
            if (rec != null && rec.getCheckInAt() != null) {
                // Rare backdated punch — keep the punch status, expected stays 0.
                String code = rec.getStatusCode() == null ? "P" : rec.getStatusCode().trim().toUpperCase(Locale.ROOT);
                return code.isEmpty() ? "P" : code;
            }
            return "NE";
        }
        if (rec != null && rec.getCheckInAt() != null) {
            String code = rec.getStatusCode() == null ? "P" : rec.getStatusCode().trim().toUpperCase(Locale.ROOT);
            return code.isEmpty() ? "P" : code;
        }
        Optional<WorkSchedule> schedule = scheduleCache.computeIfAbsent(
                emp.getId(), id -> workScheduleService.resolveForEmployee(emp));
        WorkSchedule s = schedule.orElse(null);

        // Hourly paid / hourly pattern: never label week-off, holiday, or leave.
        if (workScheduleService.isHourlyEngagement(emp, s)) {
            // No punch — not office-absent; expected still accrues via hourlyCalendarDayHours.
            return "NS";
        }

        if (s != null && !workScheduleService.isWorkingDay(s, day)) {
            return "WO";
        }
        if (emp.getLocationId() != null && holidayKeys.contains(emp.getLocationId() + "|" + day)) {
            return "HO";
        }
        if (leave == LeaveClient.LeaveImpact.FULL || leave == LeaveClient.LeaveImpact.HALF) {
            return "OL";
        }
        if (schedule.isEmpty()) {
            return "NS";
        }
        // Expected working day with no punch → absent
        double expected = expectedHoursLocal(emp, day, leave, holidayKeys, scheduleCache);
        return expected > 0 ? "A" : "NS";
    }

    private double expectedHoursLocal(
            EmployeeView emp,
            LocalDate day,
            LeaveClient.LeaveImpact leave,
            Set<String> holidayKeys,
            Map<Long, Optional<WorkSchedule>> scheduleCache) {
        if (outsideEmployment(emp, day)) {
            return 0;
        }
        Optional<WorkSchedule> opt = scheduleCache.computeIfAbsent(
                emp.getId(), id -> workScheduleService.resolveForEmployee(emp));
        if (opt.isEmpty()) {
            return 0;
        }
        WorkSchedule schedule = opt.get();

        // Hourly: every calendar day in the employment window carries the same daily slice.
        if (workScheduleService.isHourlyEngagement(emp, schedule)) {
            return workScheduleService.hourlyCalendarDayHours(schedule).doubleValue();
        }

        if (leave == LeaveClient.LeaveImpact.FULL) {
            return 0;
        }
        if (emp.getLocationId() != null && holidayKeys.contains(emp.getLocationId() + "|" + day)) {
            return 0;
        }
        if (!workScheduleService.isWorkingDay(schedule, day)) {
            return 0;
        }
        BigDecimal full = workScheduleService.dailyContractHours(schedule);
        if (leave == LeaveClient.LeaveImpact.HALF) {
            full = full.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        }
        return full.doubleValue();
    }

    /**
     * Worked hours from a valid completed attendance punch only:
     * check-in + check-out present, positive duration, not missing-checkout (M).
     * Open punches and M days contribute 0 so Expected vs Worked stays payroll-honest.
     */
    private static double workedHours(AttendanceRecord r) {
        if (r == null || r.getCheckInAt() == null || r.getCheckOutAt() == null) {
            return 0;
        }
        if ("M".equalsIgnoreCase(r.getStatusCode())) {
            return 0;
        }
        long mins = Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes();
        if (mins <= 0) {
            return 0;
        }
        return Math.round(mins / 60.0 * 100.0) / 100.0;
    }

    private Map<Long, List<Map<String, Object>>> safeLeaveBatch(List<Long> ids, LocalDate from, LocalDate to) {
        try {
            return leaveClient.approvedLeaveBatch(ids, from, to);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static LeaveClient.LeaveImpact leaveOnDay(List<Map<String, Object>> rows, LocalDate day) {
        if (rows == null || rows.isEmpty() || day == null) {
            return LeaveClient.LeaveImpact.NONE;
        }
        for (Map<String, Object> a : rows) {
            LocalDate start = parseDate(a.get("startDate"));
            LocalDate end = parseDate(a.get("endDate"));
            if (start == null || end == null || day.isBefore(start) || day.isAfter(end)) {
                continue;
            }
            BigDecimal counted = asDecimal(a.get("workingDaysCounted"), BigDecimal.ONE);
            String part = a.get("halfDayPart") == null ? "FULL" : String.valueOf(a.get("halfDayPart"));
            boolean halfPart = !"FULL".equalsIgnoreCase(part);
            if (start.equals(end) && (halfPart
                    || (counted.compareTo(BigDecimal.ONE) < 0 && counted.compareTo(BigDecimal.ZERO) > 0))) {
                return LeaveClient.LeaveImpact.HALF;
            }
            return LeaveClient.LeaveImpact.FULL;
        }
        return LeaveClient.LeaveImpact.NONE;
    }

    private Set<String> loadHolidayKeys(LocalDate from, LocalDate to) {
        Set<String> keys = new HashSet<>();
        for (Holiday h : holidayRepository.findAll()) {
            if (h == null || h.isOptionalFlag() || h.getLocationId() == null
                    || h.getStartDate() == null || h.getEndDate() == null) {
                continue;
            }
            LocalDate start = h.getStartDate().isBefore(from) ? from : h.getStartDate();
            LocalDate end = h.getEndDate().isAfter(to) ? to : h.getEndDate();
            if (end.isBefore(start)) {
                continue;
            }
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                keys.add(h.getLocationId() + "|" + d);
            }
        }
        return keys;
    }

    private static Map<Long, Map<LocalDate, AttendanceRecord>> indexAttendance(List<AttendanceRecord> rows) {
        Map<Long, Map<LocalDate, AttendanceRecord>> out = new HashMap<>();
        for (AttendanceRecord r : rows) {
            if (r.getEmployeeId() == null || r.getWorkDate() == null) {
                continue;
            }
            out.computeIfAbsent(r.getEmployeeId(), k -> new HashMap<>()).putIfAbsent(r.getWorkDate(), r);
        }
        return out;
    }

    private static Map<Long, Map<LocalDate, Integer>> indexLogs(List<WorkLog> rows) {
        Map<Long, Map<LocalDate, Integer>> out = new HashMap<>();
        for (WorkLog w : rows) {
            if (w.getEmployeeId() == null || w.getWorkDate() == null) {
                continue;
            }
            out.computeIfAbsent(w.getEmployeeId(), k -> new HashMap<>())
                    .merge(w.getWorkDate(), Math.max(0, w.getMinutes()), Integer::sum);
        }
        return out;
    }

    private static void sortRows(List<Map<String, Object>> rows, String sort) {
        String key = sort == null || sort.isBlank() ? "name" : sort.trim().toLowerCase(Locale.ROOT);
        Comparator<Map<String, Object>> cmp = switch (key) {
            case "worked", "hours", "workedhours" ->
                    Comparator.comparingDouble(r -> asDouble(r.get("workedHours")));
            case "gap" -> Comparator.comparingDouble(r -> asDouble(r.get("gapHours")));
            case "absences", "absent" -> Comparator.comparingInt(r -> asInt(r.get("absences")));
            case "leave", "leavedays" -> Comparator.comparingInt(r -> asInt(r.get("leaveDays")));
            case "missing", "missinglogs", "missingworklog" ->
                    Comparator.comparingInt(r -> asInt(r.get("missingWorkLogDays")));
            default -> Comparator.comparing(
                    r -> String.valueOf(r.getOrDefault("employeeName", "")),
                    String.CASE_INSENSITIVE_ORDER);
        };
        if (!"name".equals(key)) {
            cmp = cmp.reversed();
        }
        rows.sort(cmp.thenComparing(
                r -> String.valueOf(r.getOrDefault("employeeName", "")),
                String.CASE_INSENSITIVE_ORDER));
    }

    private static final class RowAcc {
        long employeeId;
        String employeeName;
        String employeeCode;
        String workPattern;
        Long locationId;
        Long teamId;
        double expectedHours;
        double workedHours;
        double loggedHours;
        int workingDays;
        int daysPresent;
        int leaveDays;
        int absences;
        int lateArrivals;
        int halfDays;
        int missingWorkLogDays;
        int missingCheckouts;

        Map<String, Object> toRow() {
            double gap = round2(workedHours - expectedHours);
            double pct = expectedHours > 0
                    ? Math.min(999, Math.round((workedHours / expectedHours) * 1000.0) / 10.0)
                    : (workedHours > 0 ? 100.0 : 0.0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeId", employeeId);
            m.put("employeeName", employeeName);
            m.put("employeeCode", employeeCode);
            m.put("workPattern", workPattern);
            m.put("locationId", locationId);
            m.put("teamId", teamId);
            m.put("expectedHours", round2(expectedHours));
            m.put("workedHours", round2(workedHours));
            m.put("loggedHours", round2(loggedHours));
            m.put("gapHours", gap);
            m.put("workedPercent", pct);
            m.put("workingDays", workingDays);
            m.put("daysPresent", daysPresent);
            m.put("leaveDays", leaveDays);
            m.put("absences", absences);
            m.put("lateArrivals", lateArrivals);
            m.put("halfDays", halfDays);
            m.put("missingWorkLogDays", missingWorkLogDays);
            m.put("missingCheckouts", missingCheckouts);
            return m;
        }
    }

    private static final class Totals {
        double expectedHours;
        double workedHours;
        double loggedHours;
        int leaveDays;
        int absences;
        int lateArrivals;
        int halfDays;
        int missingWorkLogDays;
        int missingCheckouts;
        int workingDays;
        int daysPresent;

        void add(RowAcc r) {
            expectedHours += r.expectedHours;
            workedHours += r.workedHours;
            loggedHours += r.loggedHours;
            leaveDays += r.leaveDays;
            absences += r.absences;
            lateArrivals += r.lateArrivals;
            halfDays += r.halfDays;
            missingWorkLogDays += r.missingWorkLogDays;
            missingCheckouts += r.missingCheckouts;
            workingDays += r.workingDays;
            daysPresent += r.daysPresent;
        }

        Map<String, Object> toMap() {
            double loggedShare = workedHours > 0
                    ? Math.round((loggedHours / workedHours) * 1000.0) / 10.0
                    : 0.0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("expectedHours", round2(expectedHours));
            m.put("workedHours", round2(workedHours));
            m.put("loggedHours", round2(loggedHours));
            m.put("gapHours", round2(workedHours - expectedHours));
            m.put("loggedSharePercent", loggedShare);
            m.put("workingDays", workingDays);
            m.put("leaveDays", leaveDays);
            m.put("absences", absences);
            m.put("lateArrivals", lateArrivals);
            m.put("halfDays", halfDays);
            m.put("missingWorkLogDays", missingWorkLogDays);
            m.put("missingCheckouts", missingCheckouts);
            m.put("daysPresent", daysPresent);
            return m;
        }
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static LocalDate parseDate(Object v) {
        if (v == null) return null;
        try {
            String s = String.valueOf(v);
            return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal asDecimal(Object v, BigDecimal fallback) {
        if (v == null) return fallback;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int asInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double asDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
