package com.platform.hrms.application;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.common.security.AuthContext;
import com.platform.hrms.domain.AttendanceRecord;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.LeaveApplication;
import com.platform.hrms.domain.TenantSettings;
import com.platform.hrms.domain.WorkLog;
import com.platform.hrms.domain.WorkSchedule;
import com.platform.hrms.infrastructure.persistence.AttendanceRecordRepository;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.hrms.infrastructure.persistence.TenantSettingsRepository;
import com.platform.hrms.infrastructure.persistence.WorkLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Config-driven attendance engine (FR-ATT-01..08, FR-HRS-01..05, FR-SCH-02/06).
 * Knobs: {@code tenant_settings.attendanceRules}, {@code workLogRule}, schedule rows.
 */
@Service
public class AttendanceService {

    private final AttendanceRecordRepository repository;
    private final EmployeeRepository employeeRepository;
    private final WorkScheduleService workScheduleService;
    private final WorkLogRepository workLogRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final AuditService auditService;
    private final RbacService rbacService;

    public AttendanceService(
            AttendanceRecordRepository repository,
            EmployeeRepository employeeRepository,
            WorkScheduleService workScheduleService,
            WorkLogRepository workLogRepository,
            LeaveApplicationRepository leaveApplicationRepository,
            TenantSettingsRepository tenantSettingsRepository,
            AuditService auditService,
            RbacService rbacService) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.workScheduleService = workScheduleService;
        this.workLogRepository = workLogRepository;
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.auditService = auditService;
        this.rbacService = rbacService;
    }

    @Transactional
    public Map<String, Object> checkIn(HttpServletRequest request,
                                       String locationLabel,
                                       String note,
                                       Double latitude,
                                       Double longitude,
                                       String clientTimeZone,
                                       String deviceType) {
        Long empId = AuthContext.requireEmployeeId();
        Employee emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        ZoneId zone = resolveZone(emp, clientTimeZone);
        LocalDate today = LocalDate.now(zone);
        AttendanceRecord rec = repository.findByEmployeeIdAndWorkDate(empId, today).orElseGet(() -> {
            AttendanceRecord r = new AttendanceRecord();
            r.setEmployeeId(empId);
            r.setWorkDate(today);
            Instant now = Instant.now();
            r.setCreatedAt(now);
            r.setUpdatedAt(now);
            return r;
        });
        if (rec.getCheckInAt() != null) {
            throw new IllegalStateException("Already checked in today");
        }
        Instant now = Instant.now();
        rec.setCheckInAt(now);
        rec.setEmployeeCodeSnapshot(emp.getEmployeeCode());
        rec.setTimeZone(zone.getId());
        applyStatusFromSchedule(rec, emp, now, zone);
        rec.setIpAddress(clientIp(request));
        String ua = request.getHeader("User-Agent");
        rec.setDevice(trim(ua, 128));
        rec.setDeviceType(resolveDeviceType(deviceType, ua));
        if (latitude != null) {
            rec.setLatitude(BigDecimal.valueOf(latitude));
        }
        if (longitude != null) {
            rec.setLongitude(BigDecimal.valueOf(longitude));
        }
        rec.setLocationLabel(resolveLocationLabel(locationLabel, latitude, longitude));
        if (note != null && !note.isBlank()) {
            rec.setNote(trim(note, 512));
        }
        rec.setUpdatedAt(now);
        if (rec.getCreatedAt() == null) rec.setCreatedAt(now);
        AttendanceRecord saved = repository.save(rec);
        auditService.record(empId, "ATTENDANCE_CHECK_IN", "attendance_record", String.valueOf(saved.getId()), null, saved.getStatusCode());
        return toView(saved, emp, zone);
    }

    /**
     * Check-out with SRS BR-08: before 06:00 local, if yesterday has in and no out (and today not checked in),
     * attribute check-out to yesterday.
     */
    @Transactional
    public Map<String, Object> checkOut(HttpServletRequest request,
                                        String note,
                                        String locationLabel,
                                        Double latitude,
                                        Double longitude,
                                        String clientTimeZone,
                                        String deviceType) {
        Long empId = AuthContext.requireEmployeeId();
        Employee emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        ZoneId zone = resolveZone(emp, clientTimeZone);
        LocalDate today = LocalDate.now(zone);
        LocalDate yesterday = today.minusDays(1);
        int lateNightMinutes = attendanceInt("lateNightCheckoutMinutes", 360);
        int minuteOfDay = LocalTime.now(zone).getHour() * 60 + LocalTime.now(zone).getMinute();

        AttendanceRecord todayRec = repository.findByEmployeeIdAndWorkDate(empId, today).orElse(null);
        AttendanceRecord yesterdayRec = repository.findByEmployeeIdAndWorkDate(empId, yesterday).orElse(null);

        // FR-ATT-03 — up to N minutes after midnight attribute out to yesterday.
        boolean lateNightYesterday = yesterdayRec != null
                && yesterdayRec.getCheckInAt() != null
                && yesterdayRec.getCheckOutAt() == null
                && minuteOfDay < lateNightMinutes
                && (todayRec == null || todayRec.getCheckInAt() == null);

        LocalDate targetDate = lateNightYesterday ? yesterday : today;
        AttendanceRecord rec = lateNightYesterday ? yesterdayRec
                : repository.findByEmployeeIdAndWorkDate(empId, today)
                .orElseThrow(() -> new IllegalStateException("Check in first"));
        if (rec.getCheckInAt() == null) {
            throw new IllegalStateException("Check in first");
        }
        if (rec.getCheckOutAt() != null) {
            throw new IllegalStateException("Already checked out for " + targetDate);
        }

        enforceWorkLogRule(empId, targetDate, rec);

        Instant now = Instant.now();
        rec.setCheckOutAt(now);
        rec.setUpdatedAt(now);
        if (note != null && !note.isBlank()) {
            rec.setNote(trim(note, 512));
        }
        // Capture checkout context when provided (FR-ATT-02) — never blocks on missing location.
        if (latitude != null) {
            rec.setLatitude(BigDecimal.valueOf(latitude));
        }
        if (longitude != null) {
            rec.setLongitude(BigDecimal.valueOf(longitude));
        }
        if (locationLabel != null && !locationLabel.isBlank()) {
            rec.setLocationLabel(resolveLocationLabel(locationLabel, latitude, longitude));
        }
        if (deviceType != null && !deviceType.isBlank()) {
            rec.setDeviceType(resolveDeviceType(deviceType, request.getHeader("User-Agent")));
        }
        // Recompute P/L/W then HD — clears prior Missing check-out (M) once out is recorded.
        applyStatusFromSchedule(rec, emp, rec.getCheckInAt(), zone);
        applyHalfDayIfNeeded(rec, emp);
        AttendanceRecord saved = repository.save(rec);
        auditService.record(empId, "ATTENDANCE_CHECK_OUT", "attendance_record",
                String.valueOf(saved.getId()), null, Map.of(
                        "status", saved.getStatusCode(),
                        "workDate", targetDate.toString(),
                        "lateNightYesterday", lateNightYesterday
                ));
        Map<String, Object> view = toView(saved, emp, zone);
        view.put("attributedDate", targetDate.toString());
        view.put("lateNightCheckout", lateNightYesterday);
        return view;
    }

    /**
     * HR correction (FR-ATT-08): edit another person's record with a reason.
     * Nobody may edit their own attendance via this path.
     */
    @Transactional
    public Map<String, Object> correct(Long employeeId, LocalDate workDate,
                                       Instant checkInAt, Instant checkOutAt,
                                       String statusCode, String statusLabel, String note) {
        if (employeeId == null || workDate == null) {
            throw new IllegalArgumentException("employeeId and workDate are required");
        }
        Long actor = AuthContext.requireEmployeeId();
        if (actor.equals(employeeId)) {
            throw new IllegalStateException("Forbidden: nobody may edit or approve their own attendance");
        }
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("reason is required when editing attendance");
        }
        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        AttendanceRecord rec = repository.findByEmployeeIdAndWorkDate(employeeId, workDate).orElseGet(() -> {
            AttendanceRecord r = new AttendanceRecord();
            r.setEmployeeId(employeeId);
            r.setWorkDate(workDate);
            Instant now = Instant.now();
            r.setCreatedAt(now);
            r.setUpdatedAt(now);
            return r;
        });
        Employee emp = employeeRepository.findById(employeeId).orElse(null);
        ZoneId zone = resolveZone(emp);
        Map<String, Object> before = toView(rec, emp, zone);
        if (checkInAt != null) {
            rec.setCheckInAt(checkInAt);
        }
        if (checkOutAt != null) {
            rec.setCheckOutAt(checkOutAt);
        }
        if (checkInAt != null && checkOutAt == null) {
            // Correction supplied only in — clear a stale out so status can recompute.
        }
        if (statusCode != null && !statusCode.isBlank()) {
            rec.setStatusCode(statusCode.trim());
        } else if (rec.getCheckInAt() != null) {
            applyStatusFromSchedule(rec, emp, rec.getCheckInAt(), zone);
            if (rec.getCheckOutAt() != null) {
                applyHalfDayIfNeeded(rec, emp);
            }
        }
        if (statusLabel != null && !statusLabel.isBlank()) {
            rec.setStatusLabel(statusLabel.trim());
        }
        rec.setNote(trim(note, 512));
        rec.setUpdatedAt(Instant.now());
        AttendanceRecord saved = repository.save(rec);
        auditService.record(actor, "ATTENDANCE_CORRECT", "attendance_record",
                String.valueOf(saved.getId()), before, toView(saved, emp, zone));
        return toView(saved, emp, zone);
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> myHistory(PageQuery query) {
        return historyForEmployee(AuthContext.requireEmployeeId(), query);
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> historyForEmployee(Long empId, PageQuery query) {
        List<AttendanceRecord> all = repository.findByEmployeeIdOrderByWorkDateDesc(empId);
        if (query.q() != null) {
            String q = query.q().toLowerCase();
            all = all.stream().filter(r ->
                    (r.getStatusLabel() != null && r.getStatusLabel().toLowerCase().contains(q))
                            || (r.getStatusCode() != null && r.getStatusCode().toLowerCase().contains(q))
                            || r.getWorkDate().toString().contains(q)
            ).toList();
        }
        String statusFilter = query.filters().get("status");
        if (statusFilter != null) {
            all = all.stream().filter(r ->
                    statusFilter.equalsIgnoreCase(r.getStatusCode())
                            || statusFilter.equalsIgnoreCase(r.getStatusLabel())
            ).toList();
        }
        long total = all.size();
        int from = Math.min(query.offset(), all.size());
        int to = Math.min(from + query.size(), all.size());
        Employee emp = employeeRepository.findById(empId).orElse(null);
        ZoneId zone = resolveZone(emp);
        List<Map<String, Object>> page = all.subList(from, to).stream().map(r -> toView(r, emp, zone)).toList();
        return PageResponse.of(page, query, total);
    }

    /** Team / org attendance for a date (scope: ALL or TEAM via RBAC). */
    @Transactional
    public List<Map<String, Object>> teamForDate(LocalDate date) {
        Long actorId = AuthContext.requireEmployeeId();
        String scope = rbacService.widestScope(actorId, "attendance", "view");
        if ("NONE".equals(scope) && rbacService.hasPermission(actorId, "attendance", "view")) {
            scope = "TEAM";
        }
        Employee me = employeeRepository.findById(actorId).orElse(null);
        List<Employee> emps;
        if ("ALL".equals(scope) || rbacService.permissionKeysFor(actorId).contains("*:*")) {
            emps = employeeRepository.findAll().stream().filter(Employee::isActive).toList();
        } else {
            Long teamId = me == null ? null : me.getTeamId();
            emps = employeeRepository.findAll().stream()
                    .filter(Employee::isActive)
                    .filter(e -> e.getId().equals(actorId)
                            || (e.getManagerId() != null && e.getManagerId().equals(actorId))
                            || (teamId != null && teamId.equals(e.getTeamId())))
                    .toList();
        }
        Map<Long, AttendanceRecord> byEmp = repository.findByWorkDate(date).stream()
                .collect(java.util.stream.Collectors.toMap(AttendanceRecord::getEmployeeId, r -> r, (a, b) -> a));
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Employee emp : emps) {
            ZoneId zone = resolveZone(emp);
            AttendanceRecord rec = byEmp.get(emp.getId());
            Map<String, Object> row;
            if (rec != null) {
                applyMissingCheckoutIfPastCutoff(rec, emp, zone);
                row = toView(rec, emp, zone);
            } else {
                row = new LinkedHashMap<>();
                row.put("date", date.toString());
                applyNotPresentStatus(row, emp, date);
                row.put("checkIn", null);
                row.put("checkOut", null);
                row.put("workedMinutes", 0);
                row.put("totalHours", 0);
                row.put("workingHours", "0h 0m");
            }
            int logged = workLogRepository.findByEmployeeIdAndWorkDate(emp.getId(), date).stream()
                    .mapToInt(WorkLog::getMinutes).sum();
            row.put("loggedMinutes", logged);
            row.put("loggedHours", Math.round(logged / 60.0 * 100.0) / 100.0);
            BigDecimal expected = workScheduleService.expectedHoursForDay(emp, date);
            row.put("expectedHours", expected == null ? 0 : expected);
            row.put("employeeId", String.valueOf(emp.getId()));
            row.put("employeeName", emp.getFullName());
            row.put("name", emp.getFullName());
            row.put("employeeCode", emp.getEmployeeCode());
            row.put("departmentId", emp.getDepartmentId());
            // FE filters often use PRESENT/LATE — expose aliases alongside P/L
            String code = String.valueOf(row.getOrDefault("statusCode", ""));
            row.put("statusAlias", statusAlias(code));
            out.add(row);
        }
        return out;
    }

    /**
     * FR-SCH-02 — mark open check-ins past schedule checkoutCutoff as Missing (M).
     * Idempotent; audited. Callable from jobs or admin tooling.
     */
    @Transactional
    public Map<String, Object> closeMissingCheckouts(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        int marked = 0;
        for (AttendanceRecord rec : repository.findByWorkDate(target)) {
            if (rec.getCheckInAt() == null || rec.getCheckOutAt() != null) {
                continue;
            }
            Employee emp = employeeRepository.findById(rec.getEmployeeId()).orElse(null);
            if (emp == null) {
                continue;
            }
            ZoneId zone = resolveZone(emp);
            if (applyMissingCheckoutIfPastCutoff(rec, emp, zone)) {
                marked++;
            }
        }
        return Map.of("date", target.toString(), "markedMissing", marked);
    }

    @Transactional
    public Map<String, Object> today() {
        Long empId = AuthContext.requireEmployeeId();
        Employee emp = employeeRepository.findById(empId).orElse(null);
        ZoneId zone = resolveZone(emp);
        LocalDate today = LocalDate.now(zone);
        LocalDate yesterday = today.minusDays(1);
        int minuteOfDay = LocalTime.now(zone).getHour() * 60 + LocalTime.now(zone).getMinute();

        AttendanceRecord todayRec = repository.findByEmployeeIdAndWorkDate(empId, today).orElse(null);
        AttendanceRecord yesterdayRec = repository.findByEmployeeIdAndWorkDate(empId, yesterday).orElse(null);
        if (emp != null) {
            if (yesterdayRec != null) {
                applyMissingCheckoutIfPastCutoff(yesterdayRec, emp, zone);
            }
            if (todayRec != null) {
                applyMissingCheckoutIfPastCutoff(todayRec, emp, zone);
            }
        }

        int lateNightMinutes = attendanceInt("lateNightCheckoutMinutes", 360);
        boolean lateNightCheckoutAvailable = yesterdayRec != null
                && yesterdayRec.getCheckInAt() != null
                && yesterdayRec.getCheckOutAt() == null
                && minuteOfDay < lateNightMinutes
                && (todayRec == null || todayRec.getCheckInAt() == null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", today.toString());
        out.put("yesterday", yesterday.toString());
        out.put("serverTime", Instant.now().toString());
        out.put("localTime", LocalTime.now(zone).toString());
        out.put("timeZone", zone.getId());
        out.put("lateNightCheckoutAvailable", lateNightCheckoutAvailable);
        if (lateNightCheckoutAvailable) {
            out.put("checkoutTargetDate", yesterday.toString());
            out.put("yesterdayRecord", toView(yesterdayRec, emp, zone));
        } else {
            out.put("checkoutTargetDate", today.toString());
        }
        if (todayRec != null) {
            out.putAll(toView(todayRec, emp, zone));
        } else {
            out.put("statusCode", "NC");
            out.put("status", "Not checked in");
            out.put("checkIn", null);
            out.put("checkOut", null);
            out.put("workedMinutes", 0);
            out.put("workingHours", "0h 0m");
            out.put("totalHours", 0);
        }
        int loggedToday = workLogRepository.findByEmployeeIdAndWorkDate(empId, today).stream()
                .mapToInt(WorkLog::getMinutes).sum();
        out.put("loggedMinutes", loggedToday);
        out.put("loggedHours", Math.round(loggedToday / 60.0 * 100.0) / 100.0);
        BigDecimal expectedToday = emp == null ? BigDecimal.ZERO
                : workScheduleService.expectedHoursForDay(emp, today);
        out.put("expectedHours", expectedToday == null ? 0 : expectedToday);
        out.put("expectedMinutes", expectedToday == null ? 0
                : expectedToday.multiply(BigDecimal.valueOf(60)).intValue());
        // FR-HRS-04 — week + month rollups on the same payload.
        out.put("week", periodHours(empId, emp, weekStart(today), today));
        out.put("month", periodHours(empId, emp, today.withDayOfMonth(1), today));
        return out;
    }

    /** FR-HRS-04 — worked / expected / logged for an arbitrary inclusive range. */
    @Transactional(readOnly = true)
    public Map<String, Object> hoursSummary(LocalDate from, LocalDate to) {
        Long empId = AuthContext.requireEmployeeId();
        Employee emp = employeeRepository.findById(empId).orElse(null);
        ZoneId zone = resolveZone(emp);
        LocalDate today = LocalDate.now(zone);
        LocalDate start = from == null ? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : from;
        LocalDate end = to == null ? today : to;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("to must be on/after from");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", start.toString());
        out.put("to", end.toString());
        out.put("timeZone", zone.getId());
        out.putAll(periodHours(empId, emp, start, end));
        out.put("today", periodHours(empId, emp, today, today));
        out.put("week", periodHours(empId, emp, weekStart(today), today));
        out.put("month", periodHours(empId, emp, today.withDayOfMonth(1), today));
        return out;
    }

    private Map<String, Object> periodHours(Long empId, Employee emp, LocalDate from, LocalDate to) {
        long worked = 0;
        for (AttendanceRecord r : repository.findByEmployeeIdAndWorkDateBetween(empId, from, to)) {
            worked += workedMinutesFor(r, emp);
        }
        int logged = workLogRepository.findByEmployeeIdAndWorkDateBetween(empId, from, to).stream()
                .mapToInt(WorkLog::getMinutes).sum();
        BigDecimal expected = emp == null ? BigDecimal.ZERO
                : workScheduleService.expectedHoursForPeriod(emp, from, to);
        if (expected == null) {
            expected = BigDecimal.ZERO;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from.toString());
        m.put("to", to.toString());
        m.put("workedMinutes", worked);
        m.put("workedHours", Math.round(worked / 60.0 * 100.0) / 100.0);
        m.put("loggedMinutes", logged);
        m.put("loggedHours", Math.round(logged / 60.0 * 100.0) / 100.0);
        m.put("expectedHours", expected.setScale(2, RoundingMode.HALF_UP));
        m.put("expectedMinutes", expected.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).intValue());
        return m;
    }

    private static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * FR-ATT-04 / FR-WL-05 — percent of <em>worked</em> time by default (config: workLogPercentOf).
     */
    private void enforceWorkLogRule(Long empId, LocalDate today, AttendanceRecord rec) {
        Map<String, Object> settings = loadSettings();
        // SRS default Block when unset; packs may set FLAG / OFF.
        String rule = String.valueOf(settings.getOrDefault("workLogRule", "BLOCK")).toUpperCase(Locale.ROOT);
        if (!"BLOCK".equals(rule) && !"FLAG".equals(rule)) {
            return;
        }
        double minPercent = 0;
        Object pct = settings.get("workLogMinPercent");
        if (pct instanceof Number n) {
            minPercent = n.doubleValue();
        } else if (pct != null) {
            try { minPercent = Double.parseDouble(pct.toString()); } catch (Exception ignored) { /* 0 */ }
        }
        String ofWhat = String.valueOf(settings.getOrDefault("workLogPercentOf", "WORKED")).toUpperCase(Locale.ROOT);
        Employee emp = employeeRepository.findById(empId).orElse(null);
        double baseMinutes;
        if ("EXPECTED".equals(ofWhat) && emp != null) {
            BigDecimal expectedHours = workScheduleService.expectedHoursForDay(emp, today);
            baseMinutes = expectedHours != null && expectedHours.signum() > 0
                    ? expectedHours.doubleValue() * 60
                    : Math.max(1, Duration.between(rec.getCheckInAt(), Instant.now()).toMinutes());
        } else {
            // Default WORKED — % of punch length so far (SRS / prototype).
            baseMinutes = rec.getCheckInAt() == null ? 0
                    : Math.max(1, Duration.between(rec.getCheckInAt(), Instant.now()).toMinutes());
        }
        int logged = workLogRepository.findByEmployeeIdAndWorkDate(empId, today).stream()
                .mapToInt(WorkLog::getMinutes).sum();
        double pctDone = baseMinutes <= 0 ? 100 : (logged * 100.0 / baseMinutes);
        if (pctDone + 0.01 < minPercent) {
            if ("BLOCK".equals(rule)) {
                if (logged <= 0) {
                    throw new IllegalStateException(
                            "Add a work log before checking out (at least "
                                    + (int) Math.round(minPercent) + "% of " + ofWhat.toLowerCase(Locale.ROOT)
                                    + " hours).");
                }
                throw new IllegalStateException(
                        "Work log incomplete — logged " + logged + "m of "
                                + (int) baseMinutes + "m " + ofWhat.toLowerCase(Locale.ROOT)
                                + " (need ≥" + (int) Math.round(minPercent) + "% before check-out).");
            }
            rec.setNote("FLAG: work log " + Math.round(pctDone) + "% < " + minPercent + "%");
        }
    }

    private void applyStatusFromSchedule(AttendanceRecord rec, Employee emp, Instant checkInAt, ZoneId zone) {
        WorkSchedule schedule = resolveSchedule(emp);
        // Hourly: Worked only (FR-ATT-05) — no Late/Present split.
        if (schedule != null && "HOURLY".equalsIgnoreCase(schedule.getMode())) {
            rec.setStatusCode("W");
            rec.setStatusLabel("Worked");
            return;
        }
        if (schedule == null || schedule.getStartTime() == null || schedule.getStartTime().isBlank()) {
            rec.setStatusCode("P");
            rec.setStatusLabel("Present");
            return;
        }
        try {
            LocalTime start = LocalTime.parse(schedule.getStartTime());
            int grace = schedule.getGraceMinutes() == null
                    ? attendanceInt("defaultGraceMinutes", 15)
                    : schedule.getGraceMinutes();
            LocalTime lateAfter = start.plusMinutes(grace);
            LocalTime actual = LocalTime.ofInstant(checkInAt, zone);
            if (actual.isAfter(lateAfter)) {
                rec.setStatusCode("L");
                rec.setStatusLabel("Late");
            } else {
                rec.setStatusCode("P");
                rec.setStatusLabel("Present");
            }
        } catch (Exception e) {
            rec.setStatusCode("P");
            rec.setStatusLabel("Present");
        }
    }

    private ZoneId resolveZone(Employee emp) {
        return resolveZone(emp, null);
    }

    private ZoneId resolveZone(Employee emp, String clientTimeZone) {
        if (clientTimeZone != null && !clientTimeZone.isBlank()) {
            try {
                return ZoneId.of(clientTimeZone.trim());
            } catch (Exception ignored) { /* fall through */ }
        }
        Map<String, Object> settings = tenantSettingsRepository.findAll().stream()
                .findFirst().map(TenantSettings::getSettings).orElse(Map.of());
        Object tz = settings.get("defaultTimeZone");
        if (tz != null && !String.valueOf(tz).isBlank()) {
            try {
                return ZoneId.of(String.valueOf(tz));
            } catch (Exception ignored) { /* fall through */ }
        }
        return ZoneId.of("Asia/Dhaka");
    }

    private static String resolveLocationLabel(String locationLabel, Double latitude, Double longitude) {
        String label = locationLabel == null || locationLabel.isBlank() ? "Location not shared" : locationLabel.trim();
        if ("Share my location".equalsIgnoreCase(label) && latitude != null && longitude != null) {
            return String.format("Shared · %.5f, %.5f", latitude, longitude);
        }
        if (latitude != null && longitude != null && !label.contains(String.valueOf(latitude))) {
            return trim(label + String.format(" · %.5f, %.5f", latitude, longitude), 256);
        }
        return trim(label, 256);
    }

    private static String resolveDeviceType(String deviceType, String ua) {
        if (deviceType != null && !deviceType.isBlank()) {
            return trim(deviceType, 32);
        }
        if (ua == null) return "Desktop";
        String lower = ua.toLowerCase();
        if (lower.contains("mobi") || lower.contains("android") || lower.contains("iphone") || lower.contains("ipad")) {
            return "Mobile";
        }
        return "Desktop";
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return trim(forwarded.split(",")[0].trim(), 64);
        }
        return trim(request.getRemoteAddr(), 64);
    }

    private void applyHalfDayIfNeeded(AttendanceRecord rec, Employee emp) {
        if (rec.getCheckInAt() == null || rec.getCheckOutAt() == null) return;
        WorkSchedule schedule = resolveSchedule(emp);
        if (schedule == null || schedule.getHalfDayHours() == null) return;
        double hours = Duration.between(rec.getCheckInAt(), rec.getCheckOutAt()).toMinutes() / 60.0;
        if (hours + 0.01 < schedule.getHalfDayHours().doubleValue()) {
            rec.setStatusCode("HD");
            rec.setStatusLabel("Half Day");
        }
    }

    /**
     * FR-SCH-02 — when check-in exists, check-out is missing, and local time is past
     * schedule.checkoutCutoff on that work date, persist status M (Missing check-out).
     * @return true when the record was newly marked
     */
    private boolean applyMissingCheckoutIfPastCutoff(AttendanceRecord rec, Employee emp, ZoneId zone) {
        if (rec == null || emp == null || rec.getCheckInAt() == null || rec.getCheckOutAt() != null) {
            return false;
        }
        if ("M".equalsIgnoreCase(rec.getStatusCode())) {
            return false;
        }
        WorkSchedule schedule = resolveSchedule(emp);
        if (schedule == null || "HOURLY".equalsIgnoreCase(schedule.getMode())) {
            return false;
        }
        String cutoffRaw = schedule.getCheckoutCutoff();
        if (cutoffRaw == null || cutoffRaw.isBlank()) {
            return false;
        }
        LocalTime cutoff;
        try {
            cutoff = LocalTime.parse(cutoffRaw.trim());
        } catch (Exception e) {
            return false;
        }
        Instant cutoffAt = rec.getWorkDate().atTime(cutoff).atZone(zone).toInstant();
        // Inclusive end-of-minute for HH:mm like 23:59
        cutoffAt = cutoffAt.plusSeconds(59);
        if (!Instant.now().isAfter(cutoffAt)) {
            return false;
        }
        String before = rec.getStatusCode();
        rec.setStatusCode("M");
        rec.setStatusLabel("Missing check-out");
        rec.setUpdatedAt(Instant.now());
        repository.save(rec);
        auditService.record(AuthContext.getEmployeeId(), "ATTENDANCE_MISSING_CHECKOUT", "attendance_record",
                String.valueOf(rec.getId()), Map.of("status", before), Map.of(
                        "status", "M",
                        "workDate", rec.getWorkDate().toString(),
                        "checkoutCutoff", cutoffRaw
                ));
        return true;
    }

    /**
     * No attendance row (FR-ATT-05): On leave → OL; holiday → HO; week off → WO;
     * hourly → NS (No shift); else Absent.
     */
    private void applyNotPresentStatus(Map<String, Object> row, Employee emp, LocalDate date) {
        if (emp != null && isOnApprovedLeave(emp.getId(), date)) {
            row.put("statusCode", "OL");
            row.put("status", "On leave");
            return;
        }
        WorkSchedule schedule = resolveSchedule(emp);
        if (schedule != null && "HOURLY".equalsIgnoreCase(schedule.getMode())) {
            if (workScheduleService.isHoliday(emp.getLocationId(), date)) {
                row.put("statusCode", "HO");
                row.put("status", "Holiday");
                return;
            }
            row.put("statusCode", "NS");
            row.put("status", "No shift");
            return;
        }
        if (schedule != null) {
            if (workScheduleService.isHoliday(emp.getLocationId(), date)) {
                row.put("statusCode", "HO");
                row.put("status", "Holiday");
                return;
            }
            if (!workScheduleService.isWorkingDay(schedule, date)) {
                row.put("statusCode", "WO");
                row.put("status", "Weekly off");
                return;
            }
        }
        row.put("statusCode", "A");
        row.put("status", "Absent");
    }

    private boolean isOnApprovedLeave(Long employeeId, LocalDate date) {
        for (LeaveApplication a : leaveApplicationRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId)) {
            if (!LeaveApplication.STATUS_APPROVED.equalsIgnoreCase(a.getStatus())) {
                continue;
            }
            if (a.getStartDate() == null || a.getEndDate() == null) {
                continue;
            }
            if (!date.isBefore(a.getStartDate()) && !date.isAfter(a.getEndDate())) {
                return true;
            }
        }
        return false;
    }

    private static String statusAlias(String code) {
        return switch (code == null ? "" : code.toUpperCase(Locale.ROOT)) {
            case "P" -> "PRESENT";
            case "L" -> "LATE";
            case "HD" -> "HALF_DAY";
            case "M" -> "MISSING_CHECKOUT";
            case "WO" -> "WEEK_OFF";
            case "H", "HO" -> "HOLIDAY";
            case "OL" -> "ON_LEAVE";
            case "W" -> "WORKED";
            case "NS" -> "NO_SHIFT";
            case "A", "NC" -> "ABSENT";
            default -> code.toUpperCase(Locale.ROOT);
        };
    }

    private WorkSchedule resolveSchedule(Employee emp) {
        return workScheduleService.resolveForEmployee(emp).orElse(null);
    }

    /**
     * FR-HRS-01/02/03 — closed punch = out−in; open before Missing = live; Missing = 0 until corrected.
     */
    private long workedMinutesFor(AttendanceRecord r, Employee emp) {
        if (r == null || r.getCheckInAt() == null) {
            return 0;
        }
        if (r.getCheckOutAt() != null) {
            return Math.max(0, Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes());
        }
        // FR-HRS-03 — Missing check-out counts 0 until corrected.
        if ("M".equalsIgnoreCase(r.getStatusCode())) {
            return 0;
        }
        boolean zeroOpen = attendanceBool("zeroHoursWhenOpenMissing", true);
        if (zeroOpen) {
            // Still live-count while checked in today before cutoff (FR-HRS-02).
            ZoneId zone = resolveZone(emp);
            LocalDate today = LocalDate.now(zone);
            if (r.getWorkDate() != null && r.getWorkDate().equals(today)) {
                return Math.max(0, Duration.between(r.getCheckInAt(), Instant.now()).toMinutes());
            }
            return 0;
        }
        return Math.max(0, Duration.between(r.getCheckInAt(), Instant.now()).toMinutes());
    }

    private Map<String, Object> toView(AttendanceRecord r, Employee emp, ZoneId zone) {
        ZoneId z = zone;
        if (r.getTimeZone() != null && !r.getTimeZone().isBlank()) {
            try { z = ZoneId.of(r.getTimeZone()); } catch (Exception ignored) { /* keep */ }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(r.getId()));
        m.put("employeeId", r.getEmployeeId() == null ? null : String.valueOf(r.getEmployeeId()));
        m.put("employeeCode", r.getEmployeeCodeSnapshot() != null ? r.getEmployeeCodeSnapshot()
                : (emp == null ? null : emp.getEmployeeCode()));
        m.put("date", r.getWorkDate().toString());
        m.put("workDate", r.getWorkDate().toString());
        m.put("checkIn", r.getCheckInAt() == null ? null : r.getCheckInAt().toString());
        m.put("checkOut", r.getCheckOutAt() == null ? null : r.getCheckOutAt().toString());
        m.put("status", r.getStatusLabel() == null ? r.getStatusCode() : r.getStatusLabel());
        m.put("statusCode", r.getStatusCode());
        m.put("location", r.getLocationLabel());
        m.put("locationLabel", r.getLocationLabel());
        m.put("timeZone", r.getTimeZone() != null ? r.getTimeZone() : (z == null ? null : z.getId()));
        m.put("latitude", r.getLatitude());
        m.put("longitude", r.getLongitude());
        m.put("device", r.getDevice());
        m.put("deviceType", r.getDeviceType());
        m.put("ip", r.getIpAddress());
        m.put("ipAddress", r.getIpAddress());
        m.put("note", r.getNote());
        long mins = workedMinutesFor(r, emp);
        m.put("workedMinutes", mins);
        m.put("totalHours", Math.round(mins / 60.0 * 100.0) / 100.0);
        m.put("workingHours", String.format("%dh %dm", mins / 60, mins % 60));
        m.put("hoursLive", r.getCheckInAt() != null && r.getCheckOutAt() == null
                && !"M".equalsIgnoreCase(r.getStatusCode()));
        if (r.getCheckInAt() != null && z != null) {
            m.put("checkInLocal", LocalTime.ofInstant(r.getCheckInAt(), z).toString());
            if (r.getCheckOutAt() != null) {
                m.put("checkOutLocal", LocalTime.ofInstant(r.getCheckOutAt(), z).toString());
            }
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadSettings() {
        return tenantSettingsRepository.findAll().stream()
                .findFirst().map(TenantSettings::getSettings).orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> attendanceRules() {
        Object raw = loadSettings().get("attendanceRules");
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> {
                if (k != null) {
                    out.put(String.valueOf(k), v);
                }
            });
            return out;
        }
        return Map.of();
    }

    private int attendanceInt(String key, int def) {
        Object v = attendanceRules().get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(String.valueOf(v).trim());
            } catch (Exception ignored) { /* def */ }
        }
        // Fall back to top-level scheduleDefaults when nested key missing.
        Object top = loadSettings().get(key);
        if (top instanceof Number n) {
            return n.intValue();
        }
        return def;
    }

    private boolean attendanceBool(String key, boolean def) {
        Object v = attendanceRules().get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return Boolean.parseBoolean(String.valueOf(v).trim());
        }
        return def;
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
