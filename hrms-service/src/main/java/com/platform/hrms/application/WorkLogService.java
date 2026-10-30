package com.platform.hrms.application;

import com.platform.common.security.AuthContext;
import com.platform.hrms.domain.AttendanceRecord;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.TenantSettings;
import com.platform.hrms.domain.WorkLog;
import com.platform.hrms.domain.WorkTask;
import com.platform.hrms.domain.WorkTaskAssignee;
import com.platform.hrms.infrastructure.persistence.AttendanceRecordRepository;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.TenantSettingsRepository;
import com.platform.hrms.infrastructure.persistence.WorkLogRepository;
import com.platform.hrms.infrastructure.persistence.WorkTaskAssigneeRepository;
import com.platform.hrms.infrastructure.persistence.WorkTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
    private final EmployeeRepository employeeRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final DataScopeService dataScopeService;
    private final LeaveNotificationHook notificationHook;

    public WorkLogService(
            WorkLogRepository workLogRepository,
            WorkTaskRepository taskRepository,
            WorkTaskAssigneeRepository assigneeRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            EmployeeRepository employeeRepository,
            TenantSettingsRepository tenantSettingsRepository,
            DataScopeService dataScopeService,
            LeaveNotificationHook notificationHook) {
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

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        Long actor = AuthContext.requireEmployeeId();
        LocalDate workDate = parseDate(body.get("workDate"), "workDate");
        assertEditableDate(workDate);

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
        applyOverPunchFlag(log, actor, workDate, already + minutes);
        WorkLog saved = workLogRepository.save(log);
        return toView(saved, task);
    }

    @Transactional
    public void delete(Long id) {
        Long actor = AuthContext.requireEmployeeId();
        WorkLog log = workLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Work log not found: " + id));
        if (!actor.equals(log.getEmployeeId())
                && !dataScopeService.canAccess(actor, log.getEmployeeId(), "worklogs", "edit")
                && !dataScopeService.canAccess(actor, log.getEmployeeId(), "attendance", "view")) {
            throw new SecurityException("Forbidden: cannot delete this work log");
        }
        // Owner may only delete within edit window; managers with edit can delete in-scope.
        if (actor.equals(log.getEmployeeId())) {
            assertEditableDate(log.getWorkDate());
        }
        workLogRepository.delete(log);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine() {
        Long actor = AuthContext.requireEmployeeId();
        return workLogRepository.findByEmployeeIdOrderByWorkDateDesc(actor).stream()
                .map(w -> toView(w, taskRepository.findById(w.getTaskId()).orElse(null)))
                .toList();
    }

    /** FR-WL-08 — last N days of logs for an employee (default 14). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> forEmployee(Long employeeId, int days) {
        Long actor = AuthContext.requireEmployeeId();
        dataScopeService.requireAccess(actor, employeeId, "attendance", "view");
        int n = days <= 0 ? 14 : days;
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(n - 1L);
        return workLogRepository.findByEmployeeIdAndWorkDateBetween(employeeId, from, to).stream()
                .sorted((a, b) -> b.getWorkDate().compareTo(a.getWorkDate()))
                .map(w -> toView(w, taskRepository.findById(w.getTaskId()).orElse(null)))
                .toList();
    }

    /**
     * FR-WL-07 — scoped team logs with punched/worked hours; optional missing-only filter.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> team(LocalDate from, LocalDate to, boolean missingOnly) {
        Long actor = AuthContext.requireEmployeeId();
        DataScopeService.Visibility vis = dataScopeService.visibility(actor, "attendance", "view");
        LocalDate start = from == null ? LocalDate.now() : from;
        LocalDate end = to == null ? start : to;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("to must be on/after from");
        }

        List<Employee> people = employeeRepository.findAll().stream()
                .filter(Employee::isActive)
                .filter(e -> vis.allows(e.getId()))
                .toList();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Employee emp : people) {
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                List<WorkLog> logs = workLogRepository.findByEmployeeIdAndWorkDate(emp.getId(), d);
                AttendanceRecord att = attendanceRecordRepository.findByEmployeeIdAndWorkDate(emp.getId(), d).orElse(null);
                int logged = logs.stream().mapToInt(WorkLog::getMinutes).sum();
                long punched = punchedMinutes(att);
                boolean workedDay = att != null && att.getCheckInAt() != null;
                if (missingOnly) {
                    if (!(workedDay && logged == 0)) continue;
                    rows.add(dayRow(emp, d, att, punched, logged, List.of()));
                    continue;
                }
                if (logs.isEmpty() && !workedDay) continue;
                if (logs.isEmpty()) {
                    rows.add(dayRow(emp, d, att, punched, 0, List.of()));
                } else {
                    for (WorkLog wl : logs) {
                        Map<String, Object> row = toView(wl, taskRepository.findById(wl.getTaskId()).orElse(null));
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
     * FR-WL-06 — notify employee + manager once per day for worked days with no log
     * in the last {@code missingNotifyDays} days (excluding today).
     */
    @Transactional
    public Map<String, Object> notifyMissing() {
        int days = missingNotifyDays();
        LocalDate today = LocalDate.now();
        int sent = 0;
        for (int i = 1; i <= days; i++) {
            LocalDate d = today.minusDays(i);
            for (AttendanceRecord att : attendanceRecordRepository.findByWorkDate(d)) {
                if (att.getCheckInAt() == null) continue;
                int logged = dayTotal(att.getEmployeeId(), d);
                if (logged > 0) continue;
                Employee emp = employeeRepository.findById(att.getEmployeeId()).orElse(null);
                if (emp == null || !emp.isActive()) continue;
                notificationHook.notifyMissingWorkLog(emp.getId(), d.toString());
                sent++;
                if (emp.getManagerId() != null) {
                    notificationHook.push(
                            emp.getManagerId(),
                            "Team member missing work log",
                            emp.getFullName() + " has no work log for " + d,
                            "WORKLOG",
                            "worklog_missing_mgr-" + emp.getId() + "-" + d,
                            "/team/work-logs");
                    sent++;
                }
            }
        }
        return Map.of("scannedDays", days, "notificationsAttempted", sent);
    }

    private void assertAssignedOpen(Long employeeId, WorkTask task) {
        Set<String> open = openStatuses();
        String st = task.getStatus() == null ? "" : task.getStatus().toUpperCase(Locale.ROOT);
        if (!open.contains(st)) {
            throw new IllegalStateException("Can only log against open tasks (status=" + task.getStatus() + ")");
        }
        if (!assigneeRepository.existsByTaskIdAndEmployeeId(task.getId(), employeeId)) {
            throw new IllegalStateException("Task is not assigned to you");
        }
    }

    private void assertEditableDate(LocalDate workDate) {
        LocalDate today = LocalDate.now();
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

    private void applyOverPunchFlag(WorkLog log, Long empId, LocalDate date, int dayLogged) {
        AttendanceRecord att = attendanceRecordRepository.findByEmployeeIdAndWorkDate(empId, date).orElse(null);
        long punched = punchedMinutes(att);
        long grace = overPunchGraceMinutes();
        if (punched > 0 && dayLogged > punched + grace) {
            log.setFlagged(true);
            log.setFlagReason("Logged " + dayLogged + "m exceeds punched " + punched + "m + " + grace + "m grace");
        } else {
            log.setFlagged(false);
            log.setFlagReason(null);
        }
    }

    private static long punchedMinutes(AttendanceRecord att) {
        if (att == null || att.getCheckInAt() == null || att.getCheckOutAt() == null) {
            return 0;
        }
        // Missing checkout → 0 punched for over-log compare
        if ("M".equalsIgnoreCase(att.getStatusCode())) {
            return 0;
        }
        return Math.max(0, Duration.between(att.getCheckInAt(), att.getCheckOutAt()).toMinutes());
    }

    private int dayTotal(Long empId, LocalDate date) {
        return workLogRepository.findByEmployeeIdAndWorkDate(empId, date).stream()
                .mapToInt(WorkLog::getMinutes).sum();
    }

    private Map<String, Object> dayRow(Employee emp, LocalDate d, AttendanceRecord att,
                                      long punched, int logged, List<WorkLog> logs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", emp.getId());
        m.put("employeeName", emp.getFullName());
        m.put("employeeCode", emp.getEmployeeCode());
        m.put("workDate", d.toString());
        m.put("punchedMinutes", punched);
        m.put("punchedHours", Math.round(punched / 60.0 * 100.0) / 100.0);
        m.put("dayLoggedMinutes", logged);
        m.put("loggedMinutes", logged);
        m.put("missingLog", att != null && att.getCheckInAt() != null && logged == 0);
        m.put("checkIn", att == null || att.getCheckInAt() == null ? null : att.getCheckInAt().toString());
        m.put("checkOut", att == null || att.getCheckOutAt() == null ? null : att.getCheckOutAt().toString());
        m.put("entries", logs.size());
        return m;
    }

    private Map<String, Object> toView(WorkLog w, WorkTask task) {
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
            assertEditableDate(w.getWorkDate());
            editable = true;
        } catch (Exception ignored) { /* read-only */ }
        m.put("editable", editable);
        return m;
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
        return 0;
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
