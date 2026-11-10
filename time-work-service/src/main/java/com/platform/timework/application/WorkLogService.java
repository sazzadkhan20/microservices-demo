package com.platform.timework.application;

import com.platform.common.security.AuthContext;
import com.platform.timework.domain.AttendanceRecord;
import com.platform.timework.client.people.EmployeeView;
import com.platform.timework.domain.TenantSettings;
import com.platform.timework.domain.WorkLog;
import com.platform.timework.domain.WorkTask;
import com.platform.timework.infrastructure.persistence.AttendanceRecordRepository;
import com.platform.timework.client.people.PeopleEmployeeGateway;
import com.platform.timework.infrastructure.persistence.TenantSettingsRepository;
import com.platform.timework.infrastructure.persistence.WorkLogRepository;
import com.platform.timework.infrastructure.persistence.WorkTaskAssigneeRepository;
import com.platform.timework.infrastructure.persistence.WorkTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Config-driven work logs (FR-WL-01..08). Rules from {@code workLog*} / {@code workLogRules} settings.
 */
@Service
public class WorkLogService {

    private final WorkLogRepository workLogRepository;
    private final WorkTaskRepository taskRepository;
    private final WorkTaskAssigneeRepository assigneeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final PeopleEmployeeGateway employeeRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final DataScopeService dataScopeService;
    private final TimeWorkNotificationHook notificationHook;

    public WorkLogService(
            WorkLogRepository workLogRepository,
            WorkTaskRepository taskRepository,
            WorkTaskAssigneeRepository assigneeRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            PeopleEmployeeGateway employeeRepository,
            TenantSettingsRepository tenantSettingsRepository,
            DataScopeService dataScopeService,
            TimeWorkNotificationHook notificationHook) {
        this.workLogRepository = workLogRepository;
        this.taskRepository = taskRepository;
        this.assigneeRepository = assigneeRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.employeeRepository = employeeRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.dataScopeService = dataScopeService;
        this.notificationHook = notificationHook;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> rules() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("editDaysBack", editDaysBack());
        out.put("stepMinutes", stepMinutes());
        out.put("minMinutes", minMinutes());
        out.put("maxMinutesPerDay", maxMinutesPerDay());
        out.put("overPunchGraceMinutes", overPunchGraceMinutes());
        out.put("missingNotifyDays", missingNotifyDays());
        out.put("workLogRule", workLogRule());
        out.put("workLogMinPercent", workLogMinPercent());
        out.put("workLogPercentOf", workLogPercentOf());
        return out;
    }

    /**
     * Admin update for FR-WL checkout + logging rules (stored in timework_settings).
     * Keys written match what {@link AttendanceService} and this service read on check-out / logging.
     */
    @Transactional
    public Map<String, Object> updateRules(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("Body is required");
        }
        TenantSettings row = tenantSettingsRepository.findAll().stream().findFirst().orElseGet(TenantSettings::new);
        Map<String, Object> merged = new LinkedHashMap<>(row.getSettings() == null ? Map.of() : row.getSettings());

        String rule = normalizeRule(first(body, "workLogRule", "rule"));
        if (rule != null) {
            merged.put("workLogRule", rule);
        }

        Double minPct = asDouble(body.get("workLogMinPercent"));
        if (minPct == null) minPct = asDouble(body.get("minPercent"));
        if (minPct != null) {
            if (minPct < 0 || minPct > 100) {
                throw new IllegalArgumentException("workLogMinPercent must be between 0 and 100");
            }
            merged.put("workLogMinPercent", minPct);
        }

        String percentOf = first(body, "workLogPercentOf", "percentOf");
        if (percentOf != null) {
            String p = percentOf.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("WORKED", "EXPECTED").contains(p)) {
                throw new IllegalArgumentException("workLogPercentOf must be WORKED or EXPECTED");
            }
            merged.put("workLogPercentOf", p);
        }

        Integer editDays = asInt(body.get("editDaysBack"));
        if (editDays == null) editDays = asInt(body.get("workLogEditDaysBack"));
        if (editDays != null) {
            if (editDays < 0 || editDays > 365) {
                throw new IllegalArgumentException("editDaysBack must be between 0 and 365");
            }
            merged.put("workLogEditDaysBack", editDays);
        }

        Integer step = asInt(body.get("stepMinutes"));
        Integer minM = asInt(body.get("minMinutes"));
        Integer maxDay = asInt(body.get("maxMinutesPerDay"));
        Integer grace = asInt(body.get("overPunchGraceMinutes"));
        Integer notifyDays = asInt(body.get("missingNotifyDays"));

        if (step != null && (step <= 0 || step > 240)) {
            throw new IllegalArgumentException("stepMinutes must be between 1 and 240");
        }
        if (minM != null && (minM <= 0 || minM > 480)) {
            throw new IllegalArgumentException("minMinutes must be between 1 and 480");
        }
        if (maxDay != null && (maxDay < 60 || maxDay > 24 * 60)) {
            throw new IllegalArgumentException("maxMinutesPerDay must be between 60 and 1440");
        }
        if (grace != null && (grace < 0 || grace > 480)) {
            throw new IllegalArgumentException("overPunchGraceMinutes must be between 0 and 480");
        }
        if (notifyDays != null && (notifyDays < 0 || notifyDays > 30)) {
            throw new IllegalArgumentException("missingNotifyDays must be between 0 and 30");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> bag = merged.get("workLogRules") instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<>();
        if (editDays != null) bag.put("editDaysBack", editDays);
        if (step != null) bag.put("stepMinutes", step);
        if (minM != null) bag.put("minMinutes", minM);
        if (maxDay != null) bag.put("maxMinutesPerDay", maxDay);
        if (grace != null) bag.put("overPunchGraceMinutes", grace);
        if (notifyDays != null) bag.put("missingNotifyDays", notifyDays);
        if (!bag.isEmpty()) {
            merged.put("workLogRules", bag);
        }

        row.setSettings(merged);
        row.setUpdatedAt(Instant.now());
        tenantSettingsRepository.save(row);
        return rules();
    }

    private static String normalizeRule(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String r = raw.trim().toUpperCase(Locale.ROOT);
        return switch (r) {
            case "BLOCK", "FLAG", "OFF", "NONE", "DISABLED" -> "NONE".equals(r) || "DISABLED".equals(r) ? "OFF" : r;
            default -> throw new IllegalArgumentException("workLogRule must be BLOCK, FLAG, or OFF");
        };
    }

    private static Integer asInt(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Expected an integer, got: " + v);
        }
    }

    private static Double asDouble(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Expected a number, got: " + v);
        }
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        Long actor = AuthContext.requireEmployeeId();
        EmployeeView self = employeeRepository.findById(actor).orElse(null);
        LocalDate workDate = parseDate(body.get("workDate"), "workDate");
        assertEditableDate(workDate, self);

        Long taskId = Long.valueOf(String.valueOf(body.get("taskId")));
        WorkTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        assertAssignedOpen(actor, task);

        int minutes = parseMinutes(body);
        assertStep(minutes);

        int already = dayTotal(actor, workDate);
        if (already + minutes > maxMinutesPerDay()) {
            throw new IllegalStateException(
                    "Day cannot exceed " + (maxMinutesPerDay() / 60) + " logged hours (FR-WL-04)");
        }

        String note = first(body, "note", "description");
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }

        WorkLog log = new WorkLog();
        log.setEmployeeId(actor);
        log.setWorkDate(workDate);
        log.setTaskId(taskId);
        log.setMinutes(minutes);
        log.setNote(trim(note.trim(), 1024));
        log.setCreatedAt(Instant.now());
        WorkLog saved = workLogRepository.save(log);
        // Recompute over-punch flags for the whole day (FR-WL-04).
        reflagDay(actor, workDate);
        WorkLog refreshed = workLogRepository.findById(saved.getId()).orElse(saved);
        return toView(refreshed, task, self);
    }

    @Transactional
    public void delete(Long id) {
        Long actor = AuthContext.requireEmployeeId();
        WorkLog log = workLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Work log not found: " + id));
        boolean owner = actor.equals(log.getEmployeeId());
        boolean canEditOthers = dataScopeService.canAccess(actor, log.getEmployeeId(), "worklogs", "edit");
        if (!owner && !canEditOthers) {
            throw new SecurityException("Forbidden: cannot delete this work log");
        }
        // FR-WL-03 — older than N days are read-only for everyone (including managers).
        EmployeeView subject = employeeRepository.findById(log.getEmployeeId()).orElse(null);
        assertEditableDate(log.getWorkDate(), subject);
        Long empId = log.getEmployeeId();
        LocalDate workDate = log.getWorkDate();
        workLogRepository.delete(log);
        reflagDay(empId, workDate);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine() {
        Long actor = AuthContext.requireEmployeeId();
        EmployeeView self = employeeRepository.findById(actor).orElse(null);
        return workLogRepository.findByEmployeeIdOrderByWorkDateDesc(actor).stream()
                .map(w -> toView(w, taskRepository.findById(w.getTaskId()).orElse(null), self))
                .toList();
    }

    /** FR-WL-08 — last N days of logs for an employee (default 14). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> forEmployee(Long employeeId, int days) {
        Long actor = AuthContext.requireEmployeeId();
        if (!dataScopeService.canAccess(actor, employeeId, "worklogs", "view")
                && !dataScopeService.canAccess(actor, employeeId, "attendance", "view")) {
            throw new SecurityException("Forbidden: work-log view does not cover employee " + employeeId);
        }
        EmployeeView subject = employeeRepository.findById(employeeId).orElse(null);
        int n = days <= 0 ? 14 : days;
        LocalDate to = todayFor(subject);
        LocalDate from = to.minusDays(n - 1L);
        return workLogRepository.findByEmployeeIdAndWorkDateBetween(employeeId, from, to).stream()
                .sorted((a, b) -> b.getWorkDate().compareTo(a.getWorkDate()))
                .map(w -> toView(w, taskRepository.findById(w.getTaskId()).orElse(null), subject))
                .toList();
    }

    /**
     * FR-WL-07 — scoped team logs with punched/worked hours; optional missing-only /
     * employeeId / taskId filters (applied before pagination).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> team(
            LocalDate from, LocalDate to, boolean missingOnly, Long employeeId, Long taskId) {
        Long actor = AuthContext.requireEmployeeId();
        DataScopeService.Visibility vis = teamVisibility(actor);
        LocalDate start = from == null ? LocalDate.now() : from;
        LocalDate end = to == null ? start : to;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("to must be on/after from");
        }
        if (employeeId != null && !vis.allows(employeeId)) {
            throw new SecurityException("Forbidden: employee " + employeeId + " is outside your work-log scope");
        }

        List<EmployeeView> people = employeeRepository.findAll().stream()
                .filter(EmployeeView::isActive)
                .filter(e -> vis.allows(e.getId()))
                .filter(e -> employeeId == null || employeeId.equals(e.getId()))
                .toList();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (EmployeeView emp : people) {
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                List<WorkLog> logs = workLogRepository.findByEmployeeIdAndWorkDate(emp.getId(), d);
                AttendanceRecord att = attendanceRecordRepository.findByEmployeeIdAndWorkDate(emp.getId(), d).orElse(null);
                int logged = logs.stream().mapToInt(WorkLog::getMinutes).sum();
                long punched = punchedMinutes(att, d, emp);
                boolean workedDay = att != null && att.getCheckInAt() != null;
                if (missingOnly) {
                    // Missing-log days have no task — skip when a task filter is active.
                    if (taskId != null) continue;
                    if (!(workedDay && logged == 0)) continue;
                    rows.add(dayRow(emp, d, att, punched, logged));
                    continue;
                }
                if (logs.isEmpty() && !workedDay) continue;
                if (logs.isEmpty()) {
                    if (taskId != null) continue;
                    rows.add(dayRow(emp, d, att, punched, 0));
                } else {
                    for (WorkLog wl : logs) {
                        if (taskId != null && !taskId.equals(wl.getTaskId())) continue;
                        Map<String, Object> row = toView(wl, taskRepository.findById(wl.getTaskId()).orElse(null), emp);
                        row.put("employeeName", emp.getFullName());
                        row.put("employeeCode", emp.getEmployeeCode());
                        row.put("punchedMinutes", punched);
                        row.put("punchedHours", Math.round(punched / 60.0 * 100.0) / 100.0);
                        row.put("dayLoggedMinutes", logged);
                        row.put("checkIn", att == null || att.getCheckInAt() == null ? null : att.getCheckInAt().toString());
                        row.put("checkOut", att == null || att.getCheckOutAt() == null ? null : att.getCheckOutAt().toString());
                        rows.add(row);
                    }
                }
            }
        }
        return rows;
    }

    /**
     * Filter dropdowns for team work logs — people in attendance/work-log scope plus
     * every distinct task those people logged against in the date range (not limited to
     * tasks the manager created).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> teamFilterOptions(LocalDate from, LocalDate to) {
        Long actor = AuthContext.requireEmployeeId();
        DataScopeService.Visibility vis = teamVisibility(actor);
        LocalDate start = from == null ? LocalDate.now() : from;
        LocalDate end = to == null ? start : to;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("to must be on/after from");
        }

        List<Map<String, Object>> employees = new ArrayList<>();
        Map<Long, Map<String, Object>> tasksById = new LinkedHashMap<>();
        for (EmployeeView emp : employeeRepository.findAll()) {
            if (!emp.isActive() || !vis.allows(emp.getId())) continue;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", emp.getId());
            e.put("fullName", emp.getFullName());
            e.put("employeeCode", emp.getEmployeeCode());
            employees.add(e);

            // Tasks assigned to people in scope (includes tasks others created).
            for (var a : assigneeRepository.findByEmployeeId(emp.getId())) {
                putTaskOption(tasksById, a.getTaskId());
            }
            // Plus any task already logged in range (covers closed/unassigned leftovers).
            for (WorkLog wl : workLogRepository.findByEmployeeIdAndWorkDateBetween(emp.getId(), start, end)) {
                putTaskOption(tasksById, wl.getTaskId());
            }
        }
        employees.sort((a, b) -> String.valueOf(a.get("fullName")).compareToIgnoreCase(String.valueOf(b.get("fullName"))));
        List<Map<String, Object>> tasks = new ArrayList<>(tasksById.values());
        tasks.sort((a, b) -> String.valueOf(a.get("title")).compareToIgnoreCase(String.valueOf(b.get("title"))));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employees", employees);
        out.put("tasks", tasks);
        return out;
    }

    /**
     * FR-WL-06 — notify employee + manager when a worked day has no work log.
     * Deduped once per employee+date (and once for the manager copy).
     * Called on check-out (immediate) and by the daily scan job.
     */
    @Transactional(readOnly = true)
    public int notifyIfNoWorkLog(Long employeeId, LocalDate workDate) {
        if (employeeId == null || workDate == null) return 0;
        if (dayTotal(employeeId, workDate) > 0) return 0;
        EmployeeView emp = employeeRepository.findById(employeeId).orElse(null);
        if (emp == null || !emp.isActive()) return 0;
        int sent = 0;
        if (notificationHook.notifyMissingWorkLog(emp.getId(), workDate.toString())) {
            sent++;
        }
        if (notificationHook.notifyManagerMissingWorkLog(
                emp.getManagerId(), emp.getId(), emp.getFullName(), workDate.toString())) {
            sent++;
        }
        return sent;
    }

    /**
     * FR-WL-06 daily scan — last {@code missingNotifyDays} calendar days (default 3),
     * including today when the person already checked out (day is complete).
     * Yesterday and older: any check-in with zero logs counts as a worked day.
     */
    @Transactional
    public Map<String, Object> notifyMissing() {
        int days = missingNotifyDays();
        LocalDate today = LocalDate.now(defaultZone());
        int scanned = 0;
        int sent = 0;
        int candidates = 0;
        for (int i = 0; i < days; i++) {
            LocalDate d = today.minusDays(i);
            scanned++;
            for (AttendanceRecord att : attendanceRecordRepository.findByWorkDate(d)) {
                if (att.getCheckInAt() == null) continue;
                // Today: wait until check-out so we don't nag mid-shift.
                if (i == 0 && att.getCheckOutAt() == null) continue;
                if (dayTotal(att.getEmployeeId(), d) > 0) continue;
                candidates++;
                sent += notifyIfNoWorkLog(att.getEmployeeId(), d);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scannedDays", scanned);
        out.put("missingLogDays", candidates);
        out.put("notificationsAttempted", sent);
        out.put("today", today.toString());
        return out;
    }

    private void putTaskOption(Map<Long, Map<String, Object>> tasksById, Long taskId) {
        if (taskId == null || tasksById.containsKey(taskId)) return;
        WorkTask task = taskRepository.findById(taskId).orElse(null);
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("id", taskId);
        t.put("title", task == null ? ("Task #" + taskId) : task.getTitle());
        if (task != null && task.getStatus() != null) {
            t.put("status", task.getStatus());
        }
        tasksById.put(taskId, t);
    }

    private DataScopeService.Visibility teamVisibility(Long actor) {
        DataScopeService.Visibility wl = dataScopeService.visibility(actor, "worklogs", "view");
        if (!"NONE".equalsIgnoreCase(wl.scope())) {
            return wl;
        }
        return dataScopeService.visibility(actor, "attendance", "view");
    }

    private void assertAssignedOpen(Long employeeId, WorkTask task) {
        Set<String> open = openStatuses();
        String st = task.getStatus() == null ? "" : task.getStatus().toUpperCase(Locale.ROOT);
        if (!open.contains(st)) {
            throw new IllegalStateException("Can only log against open / in-progress tasks (status=" + task.getStatus() + ")");
        }
        if (!assigneeRepository.existsByTaskIdAndEmployeeId(task.getId(), employeeId)) {
            throw new IllegalStateException("Task is not assigned to you");
        }
    }

    private void assertEditableDate(LocalDate workDate, EmployeeView emp) {
        LocalDate today = todayFor(emp);
        if (workDate.isAfter(today)) {
            throw new IllegalArgumentException("Cannot log future dates");
        }
        int back = editDaysBack();
        LocalDate oldest = today.minusDays(back);
        if (workDate.isBefore(oldest)) {
            throw new IllegalStateException(
                    "Entries older than " + back + " days are read-only (workLogEditDaysBack)");
        }
    }

    private void assertStep(int minutes) {
        int min = minMinutes();
        int step = stepMinutes();
        if (minutes < min) {
            throw new IllegalArgumentException("Time spent must be at least " + min + " minutes (0.25 h)");
        }
        if (step > 0 && minutes % step != 0) {
            throw new IllegalArgumentException("Time spent must be in " + step + "-minute steps");
        }
    }

    /** Recompute FR-WL-04 over-punch flags for every entry on the day. */
    private void reflagDay(Long empId, LocalDate date) {
        EmployeeView emp = employeeRepository.findById(empId).orElse(null);
        AttendanceRecord att = attendanceRecordRepository.findByEmployeeIdAndWorkDate(empId, date).orElse(null);
        List<WorkLog> logs = workLogRepository.findByEmployeeIdAndWorkDate(empId, date);
        int dayLogged = logs.stream().mapToInt(WorkLog::getMinutes).sum();
        long punched = punchedMinutes(att, date, emp);
        long grace = overPunchGraceMinutes();
        boolean over = punched > 0 && dayLogged > punched + grace;
        String reason = over
                ? "Logged " + dayLogged + "m exceeds punched " + punched + "m + " + grace + "m grace"
                : null;
        for (WorkLog w : logs) {
            w.setFlagged(over);
            w.setFlagReason(reason);
        }
        workLogRepository.saveAll(logs);
    }

    /**
     * Punched minutes for over-log compare.
     * Closed punch → check-out − check-in; still open today → now − check-in; else 0.
     */
    private long punchedMinutes(AttendanceRecord att, LocalDate workDate, EmployeeView emp) {
        if (att == null || att.getCheckInAt() == null) {
            return 0;
        }
        Instant end = att.getCheckOutAt();
        if (end == null) {
            LocalDate today = todayFor(emp);
            if (workDate != null && workDate.equals(today)) {
                end = Instant.now();
            } else {
                return 0;
            }
        }
        return Math.max(0, Duration.between(att.getCheckInAt(), end).toMinutes());
    }

    private int dayTotal(Long empId, LocalDate date) {
        return workLogRepository.findByEmployeeIdAndWorkDate(empId, date).stream()
                .mapToInt(WorkLog::getMinutes).sum();
    }

    private Map<String, Object> dayRow(EmployeeView emp, LocalDate d, AttendanceRecord att,
                                      long punched, int logged) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", emp.getId());
        m.put("employeeName", emp.getFullName());
        m.put("employeeCode", emp.getEmployeeCode());
        m.put("workDate", d.toString());
        m.put("punchedMinutes", punched);
        m.put("punchedHours", Math.round(punched / 60.0 * 100.0) / 100.0);
        m.put("dayLoggedMinutes", logged);
        m.put("loggedMinutes", logged);
        m.put("minutes", logged);
        m.put("missingLog", att != null && att.getCheckInAt() != null && logged == 0);
        m.put("checkIn", att == null || att.getCheckInAt() == null ? null : att.getCheckInAt().toString());
        m.put("checkOut", att == null || att.getCheckOutAt() == null ? null : att.getCheckOutAt().toString());
        m.put("entries", 0);
        return m;
    }

    private Map<String, Object> toView(WorkLog w, WorkTask task, EmployeeView emp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.getId());
        m.put("employeeId", w.getEmployeeId());
        m.put("workDate", w.getWorkDate() == null ? null : w.getWorkDate().toString());
        m.put("taskId", w.getTaskId());
        m.put("taskTitle", task == null ? null : task.getTitle());
        m.put("minutes", w.getMinutes());
        m.put("hours", Math.round(w.getMinutes() / 60.0 * 100.0) / 100.0);
        m.put("note", w.getNote());
        m.put("description", w.getNote());
        m.put("flagged", w.isFlagged());
        m.put("flagReason", w.getFlagReason());
        m.put("createdAt", w.getCreatedAt() == null ? null : w.getCreatedAt().toString());
        boolean editable = false;
        try {
            assertEditableDate(w.getWorkDate(), emp);
            editable = true;
        } catch (Exception ignored) { /* read-only */ }
        m.put("editable", editable);
        return m;
    }

    private LocalDate todayFor(EmployeeView emp) {
        return LocalDate.now(zoneOf(emp));
    }

    private ZoneId zoneOf(EmployeeView emp) {
        if (emp != null && emp.getLocationTimeZone() != null && !emp.getLocationTimeZone().isBlank()) {
            try {
                return ZoneId.of(emp.getLocationTimeZone().trim());
            } catch (Exception ignored) { /* fall through */ }
        }
        return defaultZone();
    }

    private ZoneId defaultZone() {
        Object tz = settings().get("defaultTimeZone");
        if (tz != null && !String.valueOf(tz).isBlank()) {
            try {
                return ZoneId.of(String.valueOf(tz).trim());
            } catch (Exception ignored) { /* */ }
        }
        return ZoneId.of("Asia/Dhaka");
    }

    private int parseMinutes(Map<String, Object> body) {
        if (body.get("minutes") != null && !String.valueOf(body.get("minutes")).isBlank()) {
            return (int) Math.round(Double.parseDouble(String.valueOf(body.get("minutes"))));
        }
        if (body.get("hours") != null && !String.valueOf(body.get("hours")).isBlank()) {
            return (int) Math.round(Double.parseDouble(String.valueOf(body.get("hours"))) * 60.0);
        }
        throw new IllegalArgumentException("minutes (or hours) is required");
    }

    private static LocalDate parseDate(Object raw, String field) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return LocalDate.parse(String.valueOf(raw));
    }

    private int editDaysBack() {
        return intSetting(List.of("workLogRules.editDaysBack", "workLogEditDaysBack"), 7);
    }

    private int stepMinutes() {
        return intSetting(List.of("workLogRules.stepMinutes"), 15);
    }

    private int minMinutes() {
        return intSetting(List.of("workLogRules.minMinutes"), 15);
    }

    private int maxMinutesPerDay() {
        return intSetting(List.of("workLogRules.maxMinutesPerDay"), 960);
    }

    private int overPunchGraceMinutes() {
        return intSetting(List.of("workLogRules.overPunchGraceMinutes"), 60);
    }

    private int missingNotifyDays() {
        return intSetting(List.of("workLogRules.missingNotifyDays"), 3);
    }

    private String workLogRule() {
        Object v = settings().get("workLogRule");
        return v == null ? "BLOCK" : String.valueOf(v).toUpperCase(Locale.ROOT);
    }

    private double workLogMinPercent() {
        Object v = settings().get("workLogMinPercent");
        if (v instanceof Number n) return n.doubleValue();
        if (v != null) {
            try { return Double.parseDouble(v.toString()); } catch (Exception ignored) { /* */ }
        }
        return 80;
    }

    private String workLogPercentOf() {
        Object v = settings().get("workLogPercentOf");
        return v == null ? "WORKED" : String.valueOf(v).toUpperCase(Locale.ROOT);
    }

    private Set<String> openStatuses() {
        Object raw = nested("taskRules", "openStatuses");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(o -> String.valueOf(o).trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        }
        return Set.of("OPEN", "IN_PROGRESS");
    }

    @SuppressWarnings("unchecked")
    private Object nested(String bag, String key) {
        Object raw = settings().get(bag);
        if (raw instanceof Map<?, ?> m) {
            return m.get(key);
        }
        return null;
    }

    private int intSetting(List<String> keys, int def) {
        Map<String, Object> s = settings();
        for (String key : keys) {
            if (key.contains(".")) {
                String[] p = key.split("\\.", 2);
                Object bag = s.get(p[0]);
                if (bag instanceof Map<?, ?> m && m.get(p[1]) != null) {
                    Object v = m.get(p[1]);
                    if (v instanceof Number n) return n.intValue();
                    try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Exception ignored) { /* */ }
                }
            } else if (s.get(key) != null) {
                Object v = s.get(key);
                if (v instanceof Number n) return n.intValue();
                try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Exception ignored) { /* */ }
            }
        }
        return def;
    }

    private Map<String, Object> settings() {
        return tenantSettingsRepository.findAll().stream()
                .findFirst().map(TenantSettings::getSettings).orElse(Map.of());
    }

    private static String first(Map<String, Object> body, String... keys) {
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return null;
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
