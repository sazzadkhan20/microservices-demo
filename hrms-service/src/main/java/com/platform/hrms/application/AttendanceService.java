package com.platform.hrms.application;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.common.security.AuthContext;
import com.platform.hrms.domain.AttendanceRecord;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.TenantSettings;
import com.platform.hrms.domain.WorkLog;
import com.platform.hrms.domain.WorkSchedule;
import com.platform.hrms.infrastructure.persistence.AttendanceRecordRepository;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.TenantSettingsRepository;
import com.platform.hrms.infrastructure.persistence.WorkLogRepository;
import com.platform.hrms.infrastructure.persistence.WorkScheduleRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AttendanceService {

    private final AttendanceRecordRepository repository;
    private final EmployeeRepository employeeRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final WorkLogRepository workLogRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final AuditService auditService;
    private final RbacService rbacService;

    public AttendanceService(
            AttendanceRecordRepository repository,
            EmployeeRepository employeeRepository,
            WorkScheduleRepository workScheduleRepository,
            WorkLogRepository workLogRepository,
            TenantSettingsRepository tenantSettingsRepository,
            AuditService auditService,
            RbacService rbacService) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.workScheduleRepository = workScheduleRepository;
        this.workLogRepository = workLogRepository;
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
    public Map<String, Object> checkOut(HttpServletRequest request, String note) {
        Long empId = AuthContext.requireEmployeeId();
        Employee emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        ZoneId zone = resolveZone(emp);
        LocalDate today = LocalDate.now(zone);
        LocalDate yesterday = today.minusDays(1);
        int minuteOfDay = LocalTime.now(zone).getHour() * 60 + LocalTime.now(zone).getMinute();

        AttendanceRecord todayRec = repository.findByEmployeeIdAndWorkDate(empId, today).orElse(null);
        AttendanceRecord yesterdayRec = repository.findByEmployeeIdAndWorkDate(empId, yesterday).orElse(null);

        boolean lateNightYesterday = yesterdayRec != null
                && yesterdayRec.getCheckInAt() != null
                && yesterdayRec.getCheckOutAt() == null
                && minuteOfDay < 360
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
     * HR correction: adjust check-in/out / status for a given employee+date (config: attendance:correct).
     */
    @Transactional
    public Map<String, Object> correct(Long employeeId, LocalDate workDate,
                                       Instant checkInAt, Instant checkOutAt,
                                       String statusCode, String statusLabel, String note) {
        if (employeeId == null || workDate == null) {
            throw new IllegalArgumentException("employeeId and workDate are required");
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
        if (statusCode != null && !statusCode.isBlank()) {
            rec.setStatusCode(statusCode.trim());
        }
        if (statusLabel != null && !statusLabel.isBlank()) {
            rec.setStatusLabel(statusLabel.trim());
        }
        if (note != null) {
            rec.setNote(trim(note, 512));
        }
        rec.setUpdatedAt(Instant.now());
        AttendanceRecord saved = repository.save(rec);
        Long actor = AuthContext.getEmployeeId();
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
    @Transactional(readOnly = true)
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
                row = toView(rec, emp, zone);
            } else {
                row = new LinkedHashMap<>();
                row.put("date", date.toString());
                row.put("statusCode", "A");
                row.put("status", "Absent");
                row.put("checkIn", null);
                row.put("checkOut", null);
            }
            row.put("employeeId", String.valueOf(emp.getId()));
            row.put("employeeName", emp.getFullName());
            row.put("name", emp.getFullName());
            row.put("employeeCode", emp.getEmployeeCode());
            row.put("departmentId", emp.getDepartmentId());
            // FE filters often use PRESENT/LATE — expose aliases alongside P/L
            String code = String.valueOf(row.getOrDefault("statusCode", ""));
            row.put("statusAlias", switch (code.toUpperCase()) {
                case "P" -> "PRESENT";
                case "L" -> "LATE";
                case "HD" -> "HALF_DAY";
                case "A", "NC" -> "ABSENT";
                default -> code.toUpperCase();
            });
            out.add(row);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> today() {
        Long empId = AuthContext.requireEmployeeId();
        Employee emp = employeeRepository.findById(empId).orElse(null);
        ZoneId zone = resolveZone(emp);
        LocalDate today = LocalDate.now(zone);
        LocalDate yesterday = today.minusDays(1);
        int minuteOfDay = LocalTime.now(zone).getHour() * 60 + LocalTime.now(zone).getMinute();

        AttendanceRecord todayRec = repository.findByEmployeeIdAndWorkDate(empId, today).orElse(null);
        AttendanceRecord yesterdayRec = repository.findByEmployeeIdAndWorkDate(empId, yesterday).orElse(null);

        boolean lateNightCheckoutAvailable = yesterdayRec != null
                && yesterdayRec.getCheckInAt() != null
                && yesterdayRec.getCheckOutAt() == null
                && minuteOfDay < 360
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
        }
        int loggedToday = workLogRepository.findByEmployeeIdAndWorkDate(empId, today).stream()
                .mapToInt(WorkLog::getMinutes).sum();
        out.put("loggedMinutes", loggedToday);
        if (todayRec != null && todayRec.getCheckInAt() != null) {
            Instant end = todayRec.getCheckOutAt() == null ? Instant.now() : todayRec.getCheckOutAt();
            out.put("workedMinutes", Duration.between(todayRec.getCheckInAt(), end).toMinutes());
        } else {
            out.put("workedMinutes", 0);
        }
        return out;
    }

    private void enforceWorkLogRule(Long empId, LocalDate today, AttendanceRecord rec) {
        Map<String, Object> settings = tenantSettingsRepository.findAll().stream()
                .findFirst().map(TenantSettings::getSettings).orElse(Map.of());
        String rule = String.valueOf(settings.getOrDefault("workLogRule", "OFF")).toUpperCase();
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
        Employee emp = employeeRepository.findById(empId).orElse(null);
        WorkSchedule schedule = emp == null ? null : resolveSchedule(emp);
        double expectedMinutes = 8 * 60;
        if (schedule != null && schedule.getStartTime() != null && schedule.getEndTime() != null) {
            try {
                LocalTime start = LocalTime.parse(schedule.getStartTime());
                LocalTime end = LocalTime.parse(schedule.getEndTime());
                expectedMinutes = Duration.between(start, end).toMinutes();
            } catch (Exception ignored) { /* default */ }
        } else if (rec.getCheckInAt() != null) {
            expectedMinutes = Math.max(1, Duration.between(rec.getCheckInAt(), Instant.now()).toMinutes());
        }
        int logged = workLogRepository.findByEmployeeIdAndWorkDate(empId, today).stream()
                .mapToInt(WorkLog::getMinutes).sum();
        double pctDone = expectedMinutes <= 0 ? 100 : (logged * 100.0 / expectedMinutes);
        if (pctDone + 0.01 < minPercent) {
            if ("BLOCK".equals(rule)) {
                throw new IllegalStateException(
                        "Work log incomplete: logged=" + logged + "m, required>=" + minPercent
                                + "% of " + (int) expectedMinutes + "m (workLogRule=BLOCK)");
            }
            rec.setNote("FLAG: work log " + Math.round(pctDone) + "% < " + minPercent + "%");
        }
    }

    private void applyStatusFromSchedule(AttendanceRecord rec, Employee emp, Instant checkInAt, ZoneId zone) {
        WorkSchedule schedule = resolveSchedule(emp);
        if (schedule == null || schedule.getStartTime() == null) {
            rec.setStatusCode("P");
            rec.setStatusLabel("Present");
            return;
        }
        try {
            LocalTime start = LocalTime.parse(schedule.getStartTime());
            int grace = schedule.getGraceMinutes() == null ? 15 : schedule.getGraceMinutes();
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

    private WorkSchedule resolveSchedule(Employee emp) {
        if (emp.getId() != null) {
            Optional<WorkSchedule> empScope = workScheduleRepository
                    .findByScopeTypeAndScopeId("EMPLOYEE", emp.getId());
            if (empScope.isPresent()) return empScope.get();
        }
        if (emp.getTeamId() != null) {
            Optional<WorkSchedule> team = workScheduleRepository
                    .findByScopeTypeAndScopeId("TEAM", emp.getTeamId());
            if (team.isPresent()) return team.get();
        }
        if (emp.getLocationId() != null) {
            return workScheduleRepository.findByScopeTypeAndScopeId("LOCATION", emp.getLocationId()).orElse(null);
        }
        return workScheduleRepository.findAll().stream().findFirst().orElse(null);
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
        if (r.getCheckInAt() != null) {
            Instant end = r.getCheckOutAt() == null ? Instant.now() : r.getCheckOutAt();
            long mins = Duration.between(r.getCheckInAt(), end).toMinutes();
            m.put("workedMinutes", mins);
            m.put("totalHours", Math.round(mins / 60.0 * 100.0) / 100.0);
            m.put("workingHours", String.format("%dh %dm", mins / 60, mins % 60));
            if (z != null) {
                m.put("checkInLocal", LocalTime.ofInstant(r.getCheckInAt(), z).toString());
                if (r.getCheckOutAt() != null) {
                    m.put("checkOutLocal", LocalTime.ofInstant(r.getCheckOutAt(), z).toString());
                }
            }
        }
        return m;
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
