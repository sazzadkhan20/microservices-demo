package com.platform.timework.application;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.common.security.AuthContext;
import com.platform.timework.domain.AttendanceCorrection;
import com.platform.timework.domain.AttendanceRecord;
import com.platform.timework.client.people.EmployeeView;
import com.platform.timework.domain.TenantSettings;
import com.platform.timework.domain.WorkLog;
import com.platform.timework.domain.WorkSchedule;
import com.platform.timework.infrastructure.persistence.AttendanceCorrectionRepository;
import com.platform.timework.infrastructure.persistence.AttendanceRecordRepository;
import com.platform.timework.client.people.PeopleEmployeeGateway;
import com.platform.timework.infrastructure.persistence.TenantSettingsRepository;
import com.platform.timework.infrastructure.persistence.WorkLogRepository;
import com.platform.timework.client.LeaveClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
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
import com.platform.timework.client.people.PeopleAuthzClient;

/**
 * Config-driven attendance engine (FR-ATT-01..08, FR-HRS-01..05, FR-SCH-02/06).
 * Knobs: {@code tenant_settings.attendanceRules}, {@code workLogRule}, schedule rows.
 */
@Service
public class AttendanceService {

    private final AttendanceRecordRepository repository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final PeopleEmployeeGateway employeeRepository;
    private final WorkScheduleService workScheduleService;
    private final WorkLogRepository workLogRepository;
    private final WorkLogService workLogService;
    private final LeaveClient leaveClient;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final AuditService auditService;
    private final PeopleAuthzClient rbacService;
    private final DataScopeService dataScopeService;
    private final Clock clock;

    public AttendanceService(
            AttendanceRecordRepository repository,
            AttendanceCorrectionRepository correctionRepository,
            PeopleEmployeeGateway employeeRepository,
            WorkScheduleService workScheduleService,
            WorkLogRepository workLogRepository,
            WorkLogService workLogService,
            LeaveClient leaveClient,
            TenantSettingsRepository tenantSettingsRepository,
            AuditService auditService,
            PeopleAuthzClient rbacService,
            DataScopeService dataScopeService,
            Clock clock) {
        this.repository = repository;
        this.correctionRepository = correctionRepository;
        this.employeeRepository = employeeRepository;
        this.workScheduleService = workScheduleService;
        this.workLogRepository = workLogRepository;
        this.workLogService = workLogService;
        this.leaveClient = leaveClient;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.auditService = auditService;
        this.rbacService = rbacService;
        this.dataScopeService = dataScopeService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private LocalDate localDate(ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
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
        EmployeeView emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        ZoneId zone = resolveZone(emp, clientTimeZone);
        Instant now = now();
        closePastCutoffOpens(emp, zone, now);

        AttendanceRecord open = findActiveOpenPunch(empId, zone);
        if (open != null) {
            throw new IllegalStateException(
                    "Already checked in for shift " + open.getWorkDate() + " — check out first");
        }

        int earlyMinutes = earlyCheckInMinutes();
        ShiftWindow window = resolveCheckInWindow(emp, zone, now);
        if (window == null) {
            ShiftWindow upcoming = peekNextWindow(emp, zone, now, earlyMinutes);
            if (upcoming != null && upcoming.isBeforeCheckInOpen(now, earlyMinutes)) {
                throw new IllegalStateException(
                        "Check-in opens "
                                + LocalTime.ofInstant(upcoming.checkInOpensAt(earlyMinutes), zone)
                                + " " + zone.getId()
                                + " (" + earlyMinutes + " min before shift start "
                                + LocalTime.ofInstant(upcoming.startAt(), zone) + ")");
            }
            throw new IllegalStateException(
                    "Outside shift window — check-in is allowed from "
                            + earlyMinutes + " min before shift start through check-out cut-off");
        }

        LocalDate workDate = window.workDate();
        AttendanceRecord rec = repository.findByEmployeeIdAndWorkDate(empId, workDate).orElseGet(() -> {
            AttendanceRecord r = new AttendanceRecord();
            r.setEmployeeId(empId);
            r.setWorkDate(workDate);
            r.setCreatedAt(now);
            r.setUpdatedAt(now);
            return r;
        });
        if (rec.getCheckInAt() != null) {
            throw new IllegalStateException("Already checked in for shift " + workDate);
        }
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
        if (rec.getCreatedAt() == null) {
            rec.setCreatedAt(now);
        }
        AttendanceRecord saved = repository.save(rec);
        auditService.record(empId, "ATTENDANCE_CHECK_IN", "attendance_record",
                String.valueOf(saved.getId()), null, Map.of(
                        "status", saved.getStatusCode(),
                        "workDate", workDate.toString()
                ));
        Map<String, Object> view = toView(saved, emp, zone);
        view.put("attributedDate", workDate.toString());
        return view;
    }

    /**
     * Check-out closes the active open shift (work_date = shift-start date), including overnight
     * duties whose cut-off falls on the next calendar day.
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
        EmployeeView emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        ZoneId zone = resolveZone(emp, clientTimeZone);
        Instant now = now();
        // Prefer the open punch before auto-Missing so we can return a clear cut-off error.
        AttendanceRecord rec = findOpenPunch(empId, zone);
        if (rec == null || rec.getCheckInAt() == null) {
            closePastCutoffOpens(emp, zone, now);
            throw new IllegalStateException("Check in first");
        }
        if (rec.getCheckOutAt() != null) {
            throw new IllegalStateException("Already checked out for " + rec.getWorkDate());
        }
        ShiftWindow window = shiftWindowFor(emp, rec.getWorkDate(), zone);
        if (window != null && window.isPastCutoff(now)) {
            applyMissingCheckoutIfPastCutoff(rec, emp, zone);
            throw new IllegalStateException(
                    "Check-out cut-off passed for shift " + rec.getWorkDate()
                            + " — marked Missing; check in for the current shift");
        }

        LocalDate targetDate = rec.getWorkDate();
        LocalDate localToday = localDate(zone);
        boolean priorShiftDay = targetDate.isBefore(localToday);

        enforceWorkLogRule(empId, targetDate, rec);

        rec.setCheckOutAt(now);
        rec.setUpdatedAt(now);
        if (note != null && !note.isBlank()) {
            rec.setNote(trim(note, 512));
        }
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
        if (locationLabel != null && !locationLabel.isBlank()) {
            rec.setLocationLabel(resolveLocationLabel(locationLabel, latitude, longitude));
        } else if (latitude == null && longitude == null
                && (rec.getLocationLabel() == null || rec.getLocationLabel().isBlank())) {
            rec.setLocationLabel("Location not shared");
        }
        applyStatusFromSchedule(rec, emp, rec.getCheckInAt(), zone);
        applyHalfDayIfNeeded(rec, emp);
        AttendanceRecord saved = repository.save(rec);
        auditService.record(empId, "ATTENDANCE_CHECK_OUT", "attendance_record",
                String.valueOf(saved.getId()), null, Map.of(
                        "status", saved.getStatusCode(),
                        "workDate", targetDate.toString(),
                        "priorShiftDay", priorShiftDay
                ));
        try {
            workLogService.notifyIfNoWorkLog(empId, targetDate);
        } catch (Exception ignored) {
            // Never fail checkout because notification delivery failed.
        }
        Map<String, Object> view = toView(saved, emp, zone);
        view.put("attributedDate", targetDate.toString());
        view.put("lateNightCheckout", priorShiftDay);
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
        return correct(employeeId, workDate, checkInAt, checkOutAt, statusCode, statusLabel, note, false);
    }

    public Map<String, Object> correct(Long employeeId, LocalDate workDate,
                                       Instant checkInAt, Instant checkOutAt,
                                       String statusCode, String statusLabel, String note,
                                       boolean clearCheckOut) {
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
            Instant now = now();
            r.setCreatedAt(now);
            r.setUpdatedAt(now);
            return r;
        });
        EmployeeView emp = employeeRepository.findById(employeeId).orElse(null);
        ZoneId zone = resolveZone(emp);
        Map<String, Object> before = toView(rec, emp, zone);
        // FR-ATT-07/08 — keep originals on a correction archive row before overwrite.
        archiveHrEditOriginals(actor, employeeId, workDate, rec, checkInAt, checkOutAt, note);
        if (checkInAt != null) {
            rec.setCheckInAt(checkInAt);
        }
        if (clearCheckOut) {
            rec.setCheckOutAt(null);
        } else if (checkOutAt != null) {
            rec.setCheckOutAt(checkOutAt);
        }
        if (statusCode != null && !statusCode.isBlank()) {
            rec.setStatusCode(statusCode.trim());
        } else if (rec.getCheckInAt() != null) {
            applyStatusFromSchedule(rec, emp, rec.getCheckInAt(), zone);
            if (rec.getCheckOutAt() != null) {
                applyHalfDayIfNeeded(rec, emp);
            } else {
                applyMissingCheckoutIfPastCutoff(rec, emp, zone);
            }
        }
        if (statusLabel != null && !statusLabel.isBlank()) {
            rec.setStatusLabel(statusLabel.trim());
        }
        if (rec.getTimeZone() == null || rec.getTimeZone().isBlank()) {
            rec.setTimeZone(zone.getId());
        }
        rec.setNote(trim(note, 512));
        rec.setUpdatedAt(now());
        AttendanceRecord saved = repository.save(rec);
        auditService.record(actor, "ATTENDANCE_CORRECT", "attendance_record",
                String.valueOf(saved.getId()), before, toView(saved, emp, zone));
        return toView(saved, emp, zone);
    }

    /** Persist original punches when HR/admin edits another person's day (FR-ATT-07/08). */
    private void archiveHrEditOriginals(Long actor, Long employeeId, LocalDate workDate,
                                        AttendanceRecord before, Instant newIn, Instant newOut, String reason) {
        Instant now = now();
        AttendanceCorrection c = new AttendanceCorrection();
        c.setEmployeeId(employeeId);
        c.setWorkDate(workDate);
        c.setOriginalCheckIn(before.getCheckInAt());
        c.setOriginalCheckOut(before.getCheckOutAt());
        c.setOriginalStatusCode(before.getStatusCode());
        c.setRequestedCheckIn(newIn != null ? newIn : before.getCheckInAt());
        c.setRequestedCheckOut(newOut != null ? newOut : before.getCheckOutAt());
        c.setReason(trim("HR edit: " + (reason == null ? "" : reason.trim()), 512));
        c.setStatus(AttendanceCorrection.STATUS_APPROVED);
        c.setReviewerId(actor);
        c.setReviewNote("Direct edit (originals retained)");
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        correctionRepository.save(c);
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> myHistory(PageQuery query) {
        return historyForEmployee(AuthContext.requireEmployeeId(), query);
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> historyForEmployee(Long empId, PageQuery query) {
        Long actorId = AuthContext.requireEmployeeId();
        dataScopeService.requireAccess(actorId, empId, "attendance", "view");
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
        LocalDate fromDate = parseIsoDate(query.filters().get("from"));
        LocalDate toDate = parseIsoDate(query.filters().get("to"));
        if (fromDate != null) {
            all = all.stream().filter(r -> !r.getWorkDate().isBefore(fromDate)).toList();
        }
        if (toDate != null) {
            all = all.stream().filter(r -> !r.getWorkDate().isAfter(toDate)).toList();
        }
        long total = all.size();
        int from = Math.min(query.offset(), all.size());
        int to = Math.min(from + query.size(), all.size());
        EmployeeView emp = employeeRepository.findById(empId).orElse(null);
        ZoneId zone = resolveZone(emp);
        List<Map<String, Object>> page = all.subList(from, to).stream().map(r -> toView(r, emp, zone)).toList();
        return PageResponse.of(page, query, total);
    }

    /** Team / org attendance for a date (scope: ALL / TEAM subtree / OWN via RBAC). */
    @Transactional
    public List<Map<String, Object>> teamForDate(LocalDate date) {
        Long actorId = AuthContext.requireEmployeeId();
        DataScopeService.Visibility vis = dataScopeService.visibility(actorId, "attendance", "view");
        List<EmployeeView> emps = employeeRepository.findAll().stream()
                .filter(EmployeeView::isActive)
                .filter(e -> vis.allows(e.getId()))
                .toList();
        Map<Long, AttendanceRecord> byEmp = repository.findByWorkDate(date).stream()
                .collect(java.util.stream.Collectors.toMap(AttendanceRecord::getEmployeeId, r -> r, (a, b) -> a));
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (EmployeeView emp : emps) {
            ZoneId zone = resolveZone(emp);
            AttendanceRecord rec = byEmp.get(emp.getId());
            Map<String, Object> row;
            if (rec != null) {
                applyMissingCheckoutIfPastCutoff(rec, emp, zone);
                row = toView(rec, emp, zone);
            } else {
                row = new LinkedHashMap<>();
                row.put("date", date.toString());
                row.put("timeZone", zone.getId());
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
     * <p>When {@code date} is null, every open punch is evaluated in the employee's
     * location zone (multi-TZ safe — no JVM {@code LocalDate.now()}).
     */
    @Transactional
    public Map<String, Object> closeMissingCheckouts(LocalDate date) {
        int marked = 0;
        List<AttendanceRecord> candidates = date == null
                ? repository.findByCheckInAtIsNotNullAndCheckOutAtIsNull()
                : repository.findByWorkDate(date);
        for (AttendanceRecord rec : candidates) {
            if (rec.getCheckInAt() == null || rec.getCheckOutAt() != null) {
                continue;
            }
            EmployeeView emp = employeeRepository.findById(rec.getEmployeeId()).orElse(null);
            if (emp == null) {
                continue;
            }
            ZoneId zone = resolveZone(emp);
            if (applyMissingCheckoutIfPastCutoff(rec, emp, zone)) {
                marked++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (date != null) {
            out.put("date", date.toString());
        }
        out.put("scanned", candidates.size());
        out.put("markedMissing", marked);
        return out;
    }

    @Transactional
    public Map<String, Object> today() {
        Long empId = AuthContext.requireEmployeeId();
        EmployeeView emp = employeeRepository.findById(empId).orElse(null);
        ZoneId zone = resolveZone(emp);
        Instant now = now();
        LocalDate today = localDate(zone);
        LocalDate yesterday = today.minusDays(1);

        if (emp != null) {
            closePastCutoffOpens(emp, zone, now);
        }

        AttendanceRecord open = findActiveOpenPunch(empId, zone);
        AttendanceRecord todayRec = repository.findByEmployeeIdAndWorkDate(empId, today).orElse(null);
        boolean priorShiftCheckout = open != null
                && open.getWorkDate() != null
                && open.getWorkDate().isBefore(today);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", today.toString());
        out.put("yesterday", yesterday.toString());
        out.put("serverTime", now.toString());
        out.put("localTime", LocalTime.now(clock.withZone(zone)).toString());
        out.put("timeZone", zone.getId());
        out.put("lateNightCheckoutAvailable", priorShiftCheckout);
        out.put("earlyCheckInMinutes", earlyCheckInMinutes());
        if (open != null) {
            ShiftWindow openWindow = emp == null ? null : shiftWindowFor(emp, open.getWorkDate(), zone);
            boolean canOut = openWindow == null || openWindow.allowsCheckOut(now);
            // Past cut-off / Missing must never keep the check-out button alive.
            if ("M".equalsIgnoreCase(open.getStatusCode()) || (openWindow != null && openWindow.isPastCutoff(now))) {
                canOut = false;
            }
            out.put("checkoutTargetDate", open.getWorkDate().toString());
            out.put("activeWorkDate", open.getWorkDate().toString());
            out.put("canCheckOut", canOut);
            out.put("canCheckIn", false);
            if (priorShiftCheckout && canOut) {
                out.put("lateNightCheckoutAvailable", true);
                out.put("yesterdayRecord", toView(open, emp, zone));
            } else {
                out.put("lateNightCheckoutAvailable", false);
            }
            // Surface the active open shift (may be yesterday's overnight duty) as the punch UI.
            out.putAll(toView(open, emp, zone));
            out.put("date", today.toString());
            // Re-assert flags after toView (status/checkIn fields must not revive dead buttons).
            out.put("canCheckOut", canOut);
            out.put("canCheckIn", false);
        } else {
            ShiftWindow checkInWindow = emp == null ? null : resolveCheckInWindow(emp, zone, now);
            out.put("canCheckOut", false);
            out.put("canCheckIn", checkInWindow != null);
            out.put("lateNightCheckoutAvailable", false);
            if (checkInWindow != null) {
                out.put("activeWorkDate", checkInWindow.workDate().toString());
                out.put("checkoutTargetDate", checkInWindow.workDate().toString());
                AttendanceRecord target = repository
                        .findByEmployeeIdAndWorkDate(empId, checkInWindow.workDate()).orElse(null);
                if (target != null && target.getCheckInAt() != null) {
                    out.putAll(toView(target, emp, zone));
                    out.put("date", today.toString());
                } else {
                    applyTodayNotCheckedInStatus(out, emp, checkInWindow.workDate(), zone);
                    out.put("checkIn", null);
                    out.put("checkOut", null);
                    out.put("workedMinutes", 0);
                    out.put("workingHours", "0h 0m");
                    out.put("totalHours", 0);
                    out.put("workDate", checkInWindow.workDate().toString());
                }
                out.put("canCheckOut", false);
                out.put("canCheckIn", true);
            } else if (todayRec != null) {
                out.put("checkoutTargetDate", today.toString());
                out.put("activeWorkDate", today.toString());
                out.putAll(toView(todayRec, emp, zone));
                // Missing / closed day: no punch buttons even if check-in exists without check-out.
                out.put("canCheckOut", false);
                out.put("canCheckIn", false);
            } else {
                out.put("checkoutTargetDate", today.toString());
                out.put("activeWorkDate", today.toString());
                applyTodayNotCheckedInStatus(out, emp, today, zone);
                out.put("checkIn", null);
                out.put("checkOut", null);
                out.put("workedMinutes", 0);
                out.put("workingHours", "0h 0m");
                out.put("totalHours", 0);
                out.put("canCheckOut", false);
                out.put("canCheckIn", false);
            }
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
        out.put("week", periodHours(empId, emp, weekStart(today), today));
        out.put("month", periodHours(empId, emp, today.withDayOfMonth(1), today));
        return out;
    }

    /** FR-HRS-04 — worked / expected / logged for an arbitrary inclusive range. */
    @Transactional(readOnly = true)
    public Map<String, Object> hoursSummary(LocalDate from, LocalDate to) {
        Long empId = AuthContext.requireEmployeeId();
        EmployeeView emp = employeeRepository.findById(empId).orElse(null);
        ZoneId zone = resolveZone(emp);
        LocalDate today = localDate(zone);
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

    private Map<String, Object> periodHours(Long empId, EmployeeView emp, LocalDate from, LocalDate to) {
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
     * FR-ATT-04 / FR-WL-05 — checkout work-log gate.
     * BLOCK (SRS default): refuse checkout with no work log, and when logged minutes
     * are under {@code workLogMinPercent} of worked/expected time (default 80%).
     * FLAG: allow checkout but stamp a note. OFF: no gate.
     */
    private void enforceWorkLogRule(Long empId, LocalDate workDate, AttendanceRecord rec) {
        Map<String, Object> settings = loadSettings();
        // SRS / Q6 default Block when unset; packs may set FLAG / OFF.
        String rule = String.valueOf(settings.getOrDefault("workLogRule", "BLOCK")).trim().toUpperCase(Locale.ROOT);
        if ("OFF".equals(rule) || "NONE".equals(rule) || "DISABLED".equals(rule)) {
            return;
        }
        if (!"BLOCK".equals(rule) && !"FLAG".equals(rule)) {
            // Unknown value — fail closed to BLOCK for safety.
            rule = "BLOCK";
        }

        int logged = workLogRepository.findByEmployeeIdAndWorkDate(empId, workDate).stream()
                .mapToInt(WorkLog::getMinutes).sum();

        // Hard gate: at least one work-log entry before checkout when BLOCK.
        if ("BLOCK".equals(rule) && logged <= 0) {
            throw new IllegalStateException(
                    "Add at least one work log for " + workDate + " before checking out.");
        }

        // Percent threshold (SRS default 80 when unset — was 0 and silently bypassed BLOCK).
        double minPercent = 80;
        Object pct = settings.get("workLogMinPercent");
        if (pct instanceof Number n) {
            minPercent = n.doubleValue();
        } else if (pct != null && !String.valueOf(pct).isBlank()) {
            try {
                minPercent = Double.parseDouble(String.valueOf(pct).trim());
            } catch (Exception ignored) { /* keep 80 */ }
        }
        if (minPercent <= 0) {
            // Explicit 0 means "any work log is enough" after the hard gate above.
            if ("FLAG".equals(rule) && logged <= 0) {
                rec.setNote("FLAG: no work log for " + workDate);
            }
            return;
        }

        String ofWhat = String.valueOf(settings.getOrDefault("workLogPercentOf", "WORKED")).toUpperCase(Locale.ROOT);
        EmployeeView emp = employeeRepository.findById(empId).orElse(null);
        double baseMinutes;
        if ("EXPECTED".equals(ofWhat) && emp != null) {
            BigDecimal expectedHours = workScheduleService.expectedHoursForDay(emp, workDate);
            baseMinutes = expectedHours != null && expectedHours.signum() > 0
                    ? expectedHours.doubleValue() * 60
                    : Math.max(1, Duration.between(rec.getCheckInAt(), now()).toMinutes());
        } else {
            // Default WORKED — % of punch length so far (SRS / prototype).
            baseMinutes = rec.getCheckInAt() == null ? 0
                    : Math.max(1, Duration.between(rec.getCheckInAt(), now()).toMinutes());
        }
        double pctDone = baseMinutes <= 0 ? 100 : (logged * 100.0 / baseMinutes);
        if (pctDone + 0.01 < minPercent) {
            if ("BLOCK".equals(rule)) {
                throw new IllegalStateException(
                        "Work log incomplete — logged " + logged + "m of "
                                + (int) Math.round(baseMinutes) + "m " + ofWhat.toLowerCase(Locale.ROOT)
                                + " (need ≥" + (int) Math.round(minPercent) + "% before check-out).");
            }
            rec.setNote("FLAG: work log " + Math.round(pctDone) + "% < " + minPercent + "%");
        }
    }

    /**
     * Day classification for a shift {@code workDate} (overnight-safe — uses shift-start date).
     * Priority: weekly off &gt; holiday &gt; on leave &gt; normal working day.
     */
    enum NonWorkingDayKind {
        WEEKLY_OFF,
        HOLIDAY,
        ON_LEAVE,
        NONE
    }

    NonWorkingDayKind classifyNonWorkingDay(EmployeeView emp, LocalDate workDate) {
        if (emp == null || workDate == null) {
            return NonWorkingDayKind.NONE;
        }
        WorkSchedule schedule = resolveSchedule(emp);
        // 1) Weekly off wins over holiday / leave
        if (schedule != null
                && !"HOURLY".equalsIgnoreCase(schedule.getMode())
                && !workScheduleService.isWorkingDay(schedule, workDate)) {
            return NonWorkingDayKind.WEEKLY_OFF;
        }
        // 2) Location holiday
        if (workScheduleService.isHoliday(emp.getLocationId(), workDate)) {
            return NonWorkingDayKind.HOLIDAY;
        }
        // 3) Approved leave (full or half)
        if (isOnApprovedLeave(emp.getId(), workDate)) {
            return NonWorkingDayKind.ON_LEAVE;
        }
        return NonWorkingDayKind.NONE;
    }

    private void applyStatusFromSchedule(AttendanceRecord rec, EmployeeView emp, Instant checkInAt, ZoneId zone) {
        WorkSchedule schedule = resolveSchedule(emp);
        LocalDate workDate = rec.getWorkDate() != null
                ? rec.getWorkDate()
                : LocalDate.ofInstant(checkInAt, zone);
        // Punch on weekly-off / holiday / leave → Worked (never Present/Late/Half-day).
        NonWorkingDayKind kind = classifyNonWorkingDay(emp, workDate);
        if (kind == NonWorkingDayKind.WEEKLY_OFF) {
            rec.setStatusCode("W");
            rec.setStatusLabel("Worked (weekly off)");
            return;
        }
        if (kind == NonWorkingDayKind.HOLIDAY) {
            rec.setStatusCode("W");
            rec.setStatusLabel("Worked (holiday)");
            return;
        }
        if (kind == NonWorkingDayKind.ON_LEAVE) {
            rec.setStatusCode("W");
            rec.setStatusLabel("Worked (on leave)");
            return;
        }
        // Hourly Appendix A: Present (worked) — no Late split; Missing via cutoff elsewhere.
        if (schedule != null && "HOURLY".equalsIgnoreCase(schedule.getMode())) {
            rec.setStatusCode("P");
            rec.setStatusLabel("Present");
            return;
        }
        if (schedule == null || schedule.getStartTime() == null || schedule.getStartTime().isBlank()) {
            rec.setStatusCode("P");
            rec.setStatusLabel("Present");
            return;
        }
        ShiftWindow window = shiftWindowFor(emp, workDate, zone);
        if (window == null) {
            throw new IllegalStateException(
                    "Invalid schedule start/end for attendance status on " + workDate);
        }
        // Instant compare — overnight check-ins after midnight are Late vs start+grace correctly.
        if (checkInAt.isAfter(window.lateAfterAt())) {
            rec.setStatusCode("L");
            rec.setStatusLabel("Late");
        } else {
            rec.setStatusCode("P");
            rec.setStatusLabel("Present");
        }
    }

    private ShiftWindow shiftWindowFor(EmployeeView emp, LocalDate workDate, ZoneId zone) {
        return ShiftWindow.forSchedule(
                workDate,
                resolveSchedule(emp),
                zone,
                attendanceInt("defaultGraceMinutes", 15),
                attendanceStr("defaultCheckoutCutoff", "23:59"));
    }

    /**
     * Prefer the older shift date when multiple windows still contain {@code now}
     * (should be rare). Check-in from (start − earlyCheckInMinutes) through cut-off.
     * Scans yesterday / today / tomorrow so midnight shifts open 60 min before start.
     */
    private ShiftWindow resolveCheckInWindow(EmployeeView emp, ZoneId zone, Instant now) {
        int early = earlyCheckInMinutes();
        LocalDate localDay = localDate(zone);
        for (LocalDate d : List.of(localDay.minusDays(1), localDay, localDay.plusDays(1))) {
            ShiftWindow w = shiftWindowFor(emp, d, zone);
            if (w != null && w.allowsCheckIn(now, early)) {
                AttendanceRecord existing = repository.findByEmployeeIdAndWorkDate(emp.getId(), d).orElse(null);
                if (existing != null && existing.getCheckInAt() != null) {
                    continue; // that shift already punched (incl. Missing)
                }
                return w;
            }
        }
        return null;
    }

    private ShiftWindow peekNextWindow(EmployeeView emp, ZoneId zone, Instant now, int earlyMinutes) {
        LocalDate localDay = localDate(zone);
        for (LocalDate d : List.of(localDay, localDay.plusDays(1))) {
            ShiftWindow w = shiftWindowFor(emp, d, zone);
            if (w != null && w.isBeforeCheckInOpen(now, earlyMinutes)) {
                return w;
            }
        }
        return null;
    }

    /** Minutes before shift start when check-in becomes available (default 60). */
    private int earlyCheckInMinutes() {
        int v = attendanceInt("earlyCheckInMinutes", 60);
        return Math.max(0, Math.min(v, 12 * 60));
    }

    /** Active open punch = checked in, not out, not Missing, still within cut-off. */
    private AttendanceRecord findActiveOpenPunch(Long empId, ZoneId zone) {
        AttendanceRecord rec = findOpenPunch(empId, zone);
        if (rec == null) {
            return null;
        }
        EmployeeView emp = employeeRepository.findById(empId).orElse(null);
        ShiftWindow w = emp == null ? null : shiftWindowFor(emp, rec.getWorkDate(), zone);
        if (w != null && w.isPastCutoff(now())) {
            return null;
        }
        return rec;
    }

    /** Checked in, not out, not already Missing (may be past cut-off). */
    private AttendanceRecord findOpenPunch(Long empId, ZoneId zone) {
        LocalDate localDay = localDate(zone);
        for (LocalDate d : List.of(localDay.minusDays(1), localDay)) {
            AttendanceRecord rec = repository.findByEmployeeIdAndWorkDate(empId, d).orElse(null);
            if (rec == null || rec.getCheckInAt() == null || rec.getCheckOutAt() != null) {
                continue;
            }
            if ("M".equalsIgnoreCase(rec.getStatusCode())) {
                continue;
            }
            return rec;
        }
        return null;
    }

    private void closePastCutoffOpens(EmployeeView emp, ZoneId zone, Instant now) {
        if (emp == null) {
            return;
        }
        LocalDate localDay = localDate(zone);
        for (LocalDate d : List.of(localDay.minusDays(1), localDay)) {
            AttendanceRecord rec = repository.findByEmployeeIdAndWorkDate(emp.getId(), d).orElse(null);
            if (rec != null) {
                applyMissingCheckoutIfPastCutoff(rec, emp, zone);
            }
        }
    }

    private ZoneId resolveZone(EmployeeView emp) {
        return resolveZone(emp, null);
    }

    private ZoneId resolveZone(EmployeeView emp, @SuppressWarnings("unused") String clientTimeZone) {
        // TW-02: client TZ is ignored; lateness / work date use location then tenant zone.
        if (emp != null && emp.getLocationTimeZone() != null && !emp.getLocationTimeZone().isBlank()) {
            try {
                return ZoneId.of(emp.getLocationTimeZone().trim());
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
        if (latitude != null && longitude != null) {
            String label = locationLabel == null || locationLabel.isBlank()
                    || "Location not shared".equalsIgnoreCase(locationLabel.trim())
                    || "Share my location".equalsIgnoreCase(locationLabel.trim())
                    ? "Shared"
                    : locationLabel.trim();
            return trim(String.format("%s · %.5f, %.5f", label, latitude, longitude), 256);
        }
        String label = locationLabel == null || locationLabel.isBlank() ? "Location not shared" : locationLabel.trim();
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
        // Prefer connection remote address (gateway peer). Do not trust client-supplied X-Forwarded-For alone.
        String remote = request.getRemoteAddr();
        if (remote != null && !remote.isBlank()
                && !"https://example.net/id/garnet".equals(remote)
                && !"127.0.0.1".equals(remote)
                && !remote.startsWith("0:0:0:0:0:0:0:1")) {
            return trim(remote, 64);
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return trim(forwarded.split(",")[0].trim(), 64);
        }
        return trim(remote, 64);
    }

    private void applyHalfDayIfNeeded(AttendanceRecord rec, EmployeeView emp) {
        if (rec.getCheckInAt() == null || rec.getCheckOutAt() == null) return;
        // Never overwrite weekly-off / holiday / on-leave worked punches with Half Day.
        if ("W".equalsIgnoreCase(rec.getStatusCode())) {
            return;
        }
        LocalDate workDate = rec.getWorkDate();
        if (workDate != null && classifyNonWorkingDay(emp, workDate) != NonWorkingDayKind.NONE) {
            return;
        }
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
    private boolean applyMissingCheckoutIfPastCutoff(AttendanceRecord rec, EmployeeView emp, ZoneId zone) {
        if (rec == null || emp == null || rec.getCheckInAt() == null || rec.getCheckOutAt() != null) {
            return false;
        }
        if ("M".equalsIgnoreCase(rec.getStatusCode())) {
            return false;
        }
        ShiftWindow window = shiftWindowFor(emp, rec.getWorkDate(), zone);
        if (window == null || !window.isPastCutoff(now())) {
            return false;
        }
        String before = rec.getStatusCode();
        rec.setStatusCode("M");
        rec.setStatusLabel("Missing check-out");
        rec.setUpdatedAt(now());
        repository.save(rec);
        auditService.record(AuthContext.getEmployeeId(), "ATTENDANCE_MISSING_CHECKOUT", "attendance_record",
                String.valueOf(rec.getId()), Map.of("status", before), Map.of(
                        "status", "M",
                        "workDate", rec.getWorkDate().toString(),
                        "checkoutCutoff", window.cutoffAt().toString()
                ));
        return true;
    }

    /**
     * FR-ATT-05 NC — today, after start + grace, no check-in yet.
     * Not-present priority: weekly off &gt; holiday &gt; on leave &gt; NC.
     */
    private void applyTodayNotCheckedInStatus(Map<String, Object> out, EmployeeView emp, LocalDate today, ZoneId zone) {
        NonWorkingDayKind kind = classifyNonWorkingDay(emp, today);
        if (kind == NonWorkingDayKind.WEEKLY_OFF) {
            out.put("statusCode", "WO");
            out.put("status", "Weekly off");
            return;
        }
        if (kind == NonWorkingDayKind.HOLIDAY) {
            out.put("statusCode", "HO");
            out.put("status", "Holiday");
            return;
        }
        if (kind == NonWorkingDayKind.ON_LEAVE) {
            out.put("statusCode", "OL");
            out.put("status", "On leave");
            return;
        }
        WorkSchedule schedule = resolveSchedule(emp);
        if (schedule != null && "HOURLY".equalsIgnoreCase(schedule.getMode())) {
            out.put("statusCode", "NC");
            out.put("status", "Not checked in");
            return;
        }
        ShiftWindow window = shiftWindowFor(emp, today, zone);
        if (window != null && !now().isAfter(window.lateAfterAt())) {
            out.put("statusCode", "NC");
            out.put("status", "Not checked in");
            return;
        }
        out.put("statusCode", "NC");
        out.put("status", "Not checked in");
    }

    /**
     * No attendance row — priority: weekly off &gt; holiday &gt; on leave;
     * hourly → NS (No shift); else Absent.
     */
    private void applyNotPresentStatus(Map<String, Object> row, EmployeeView emp, LocalDate date) {
        NonWorkingDayKind kind = classifyNonWorkingDay(emp, date);
        if (kind == NonWorkingDayKind.WEEKLY_OFF) {
            row.put("statusCode", "WO");
            row.put("status", "Weekly off");
            return;
        }
        if (kind == NonWorkingDayKind.HOLIDAY) {
            row.put("statusCode", "HO");
            row.put("status", "Holiday");
            return;
        }
        if (kind == NonWorkingDayKind.ON_LEAVE) {
            row.put("statusCode", "OL");
            row.put("status", "On leave");
            return;
        }
        WorkSchedule schedule = resolveSchedule(emp);
        if (schedule != null && "HOURLY".equalsIgnoreCase(schedule.getMode())) {
            row.put("statusCode", "NS");
            row.put("status", "No shift");
            return;
        }
        row.put("statusCode", "A");
        row.put("status", "Absent");
    }

    private boolean isOnApprovedLeave(Long employeeId, LocalDate date) {
        LeaveClient.LeaveImpact impact = leaveClient.leaveImpact(employeeId, date);
        return impact == LeaveClient.LeaveImpact.FULL || impact == LeaveClient.LeaveImpact.HALF;
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

    private WorkSchedule resolveSchedule(EmployeeView emp) {
        return workScheduleService.resolveForEmployee(emp).orElse(null);
    }

    /**
     * FR-HRS-01/02/03 — closed punch = out−in; open before Missing = live; Missing = 0 until corrected.
     */
    private long workedMinutesFor(AttendanceRecord r, EmployeeView emp) {
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
            LocalDate today = localDate(zone);
            if (r.getWorkDate() != null && r.getWorkDate().equals(today)) {
                return Math.max(0, Duration.between(r.getCheckInAt(), now()).toMinutes());
            }
            return 0;
        }
        return Math.max(0, Duration.between(r.getCheckInAt(), now()).toMinutes());
    }

    private Map<String, Object> toView(AttendanceRecord r, EmployeeView emp, ZoneId zone) {
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

    private String attendanceStr(String key, String def) {
        Object v = attendanceRules().get(key);
        if (v != null && !String.valueOf(v).isBlank()) {
            return String.valueOf(v).trim();
        }
        Object top = loadSettings().get(key);
        if (top != null && !String.valueOf(top).isBlank()) {
            return String.valueOf(top).trim();
        }
        return def;
    }

    /** Actor's location-local "today" for team views when no date param is sent. */
    @Transactional(readOnly = true)
    public LocalDate actorLocalToday() {
        Long actorId = AuthContext.requireEmployeeId();
        EmployeeView emp = employeeRepository.findById(actorId).orElse(null);
        return localDate(resolveZone(emp));
    }

    private static LocalDate parseIsoDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String s = raw.trim();
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
