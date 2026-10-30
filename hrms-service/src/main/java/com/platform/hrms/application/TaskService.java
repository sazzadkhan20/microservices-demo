package com.platform.hrms.application;

import com.platform.common.security.AuthContext;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.TenantSettings;
import com.platform.hrms.domain.WorkLog;
import com.platform.hrms.domain.WorkTask;
import com.platform.hrms.domain.WorkTaskAssignee;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.TenantSettingsRepository;
import com.platform.hrms.infrastructure.persistence.WorkLogRepository;
import com.platform.hrms.infrastructure.persistence.WorkTaskAssigneeRepository;
import com.platform.hrms.infrastructure.persistence.WorkTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Config-driven tasks (FR-TSK-01..04). Scope + statuses from {@code tenant_settings.taskRules}.
 */
@Service
public class TaskService {

    private final WorkTaskRepository taskRepository;
    private final WorkTaskAssigneeRepository assigneeRepository;
    private final WorkLogRepository workLogRepository;
    private final EmployeeRepository employeeRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final DataScopeService dataScopeService;
    private final RbacService rbacService;

    public TaskService(
            WorkTaskRepository taskRepository,
            WorkTaskAssigneeRepository assigneeRepository,
            WorkLogRepository workLogRepository,
            EmployeeRepository employeeRepository,
            TenantSettingsRepository tenantSettingsRepository,
            DataScopeService dataScopeService,
            RbacService rbacService) {
        this.taskRepository = taskRepository;
        this.assigneeRepository = assigneeRepository;
        this.workLogRepository = workLogRepository;
        this.employeeRepository = employeeRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.dataScopeService = dataScopeService;
        this.rbacService = rbacService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listEnriched() {
        Map<Long, Integer> loggedByTask = new HashMap<>();
        for (WorkLog wl : workLogRepository.findAll()) {
            if (wl.getTaskId() == null) continue;
            loggedByTask.merge(wl.getTaskId(), wl.getMinutes(), Integer::sum);
        }
        LocalDate today = LocalDate.now();
        return taskRepository.findAll().stream()
                .map(t -> toView(t, loggedByTask.getOrDefault(t.getId(), 0), today))
                .toList();
    }

    /** Open tasks assigned to the caller — for work-log picker (FR-WL-02). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> assignedOpenForCaller() {
        Long actor = AuthContext.requireEmployeeId();
        Set<String> open = openStatuses();
        Set<Long> taskIds = assigneeRepository.findByEmployeeId(actor).stream()
                .map(WorkTaskAssignee::getTaskId)
                .collect(Collectors.toSet());
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long tid : taskIds) {
            WorkTask t = taskRepository.findById(tid).orElse(null);
            if (t == null) continue;
            String st = t.getStatus() == null ? "" : t.getStatus().toUpperCase(Locale.ROOT);
            if (!open.contains(st)) continue;
            out.add(toView(t, sumLogged(tid), today));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        Long actor = AuthContext.requireEmployeeId();
        String title = str(body.get("title"));
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        WorkTask task = new WorkTask();
        task.setTitle(title.trim());
        String client = first(body, "clientName", "description", "client");
        if (client != null && !client.isBlank()) {
            task.setClientName(client.trim());
        }
        String status = str(body.get("status"));
        task.setStatus(normalizeStatus(status == null || status.isBlank() ? "OPEN" : status));
        String priority = str(body.get("priority"));
        if (priority != null && !priority.isBlank()) {
            task.setPriority(priority.trim().toUpperCase(Locale.ROOT));
        }
        // FR-TSK-04 — externalRef is the project-module stable id (never priority).
        String ext = str(body.get("externalRef"));
        if (ext != null && !ext.isBlank() && !ext.toUpperCase(Locale.ROOT).startsWith("PRIORITY:")) {
            task.setExternalRef(ext.trim());
        }
        Object due = body.get("dueDate");
        if (due != null && !String.valueOf(due).isBlank()) {
            task.setDueDate(LocalDate.parse(String.valueOf(due)));
        }
        task.setCreatedBy(actor);
        task.setCreatedAt(Instant.now());
        WorkTask saved = taskRepository.save(task);

        List<Long> assignees = parseAssignees(body.get("assigneeIds") != null ? body.get("assigneeIds") : body.get("assignees"));
        if (assignees.isEmpty()) {
            throw new IllegalArgumentException("At least one assignee is required");
        }
        replaceAssignees(saved.getId(), assignees, actor);
        return toView(saved, 0, LocalDate.now());
    }

    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> body) {
        Long actor = AuthContext.requireEmployeeId();
        WorkTask task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));
        if (body.containsKey("title")) {
            String title = str(body.get("title"));
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("title is required");
            }
            task.setTitle(title.trim());
        }
        if (body.containsKey("clientName") || body.containsKey("description")) {
            String client = first(body, "clientName", "description");
            task.setClientName(client == null || client.isBlank() ? null : client.trim());
        }
        if (body.containsKey("status")) {
            task.setStatus(normalizeStatus(str(body.get("status"))));
        }
        if (body.containsKey("priority")) {
            String p = str(body.get("priority"));
            task.setPriority(p == null || p.isBlank() ? null : p.trim().toUpperCase(Locale.ROOT));
        }
        if (body.containsKey("externalRef")) {
            String ext = str(body.get("externalRef"));
            task.setExternalRef(ext == null || ext.isBlank() ? null : ext.trim());
        }
        if (body.containsKey("dueDate")) {
            Object due = body.get("dueDate");
            task.setDueDate(due == null || String.valueOf(due).isBlank()
                    ? null : LocalDate.parse(String.valueOf(due)));
        }
        if (body.containsKey("assigneeIds") || body.containsKey("assignees")) {
            List<Long> assignees = parseAssignees(
                    body.get("assigneeIds") != null ? body.get("assigneeIds") : body.get("assignees"));
            if (assignees.isEmpty()) {
                throw new IllegalArgumentException("At least one assignee is required");
            }
            replaceAssignees(task.getId(), assignees, actor);
        }
        WorkTask saved = taskRepository.save(task);
        return toView(saved, sumLogged(saved.getId()), LocalDate.now());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> rules() {
        Map<String, Object> cfg = taskRules();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("createScope", createScope());
        out.put("statuses", statuses());
        out.put("openStatuses", new ArrayList<>(openStatuses()));
        out.put("priorities", List.of("LOW", "MEDIUM", "HIGH"));
        out.putAll(cfg);
        return out;
    }

    private void replaceAssignees(Long taskId, List<Long> assignees, Long actor) {
        String scopeMode = createScope();
        // FR-TSK-02 — taskRules.createScope=TEAM limits assignees to self + reports + teammates
        // even when RBAC data-scope for tasks:create is ALL (pack default for managers).
        Set<Long> allowedTeam = null;
        if ("TEAM".equalsIgnoreCase(scopeMode) && !rbacService.hasWildcard(actor)) {
            allowedTeam = new java.util.HashSet<>();
            allowedTeam.add(actor);
            for (Employee report : employeeRepository.findByManagerId(actor)) {
                allowedTeam.add(report.getId());
            }
            Employee me = employeeRepository.findById(actor).orElse(null);
            if (me != null && me.getTeamId() != null) {
                for (Employee mate : employeeRepository.findAll()) {
                    if (mate.isActive() && me.getTeamId().equals(mate.getTeamId())) {
                        allowedTeam.add(mate.getId());
                    }
                }
            }
        }
        for (Long empId : assignees) {
            Employee e = employeeRepository.findById(empId)
                    .orElseThrow(() -> new IllegalArgumentException("Assignee not found: " + empId));
            if (!e.isActive()) {
                throw new IllegalStateException("Assignee is inactive: " + empId);
            }
            if (allowedTeam != null && !allowedTeam.contains(empId)) {
                throw new IllegalStateException(
                        "Cannot assign employee " + empId + " — outside your task-create scope (" + scopeMode + ")");
            }
        }
        assigneeRepository.deleteByTaskId(taskId);
        for (Long empId : assignees) {
            assigneeRepository.save(new WorkTaskAssignee(taskId, empId));
        }
    }

    private Map<String, Object> toView(WorkTask t, int loggedMinutes, LocalDate today) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("title", t.getTitle());
        m.put("clientName", t.getClientName());
        m.put("description", t.getClientName());
        m.put("status", t.getStatus());
        m.put("priority", t.getPriority());
        m.put("dueDate", t.getDueDate() == null ? null : t.getDueDate().toString());
        m.put("createdBy", t.getCreatedBy());
        m.put("externalRef", t.getExternalRef());
        m.put("createdAt", t.getCreatedAt() == null ? null : t.getCreatedAt().toString());
        List<Long> assignees = assigneeRepository.findByTaskId(t.getId()).stream()
                .map(WorkTaskAssignee::getEmployeeId).toList();
        m.put("assigneeIds", assignees);
        m.put("assignees", assignees);
        m.put("loggedMinutes", loggedMinutes);
        m.put("loggedHours", Math.round(loggedMinutes / 60.0 * 100.0) / 100.0);
        boolean overdue = t.getDueDate() != null
                && t.getDueDate().isBefore(today)
                && openStatuses().contains(
                        t.getStatus() == null ? "" : t.getStatus().toUpperCase(Locale.ROOT));
        m.put("overdue", overdue);
        // Legacy FE that still reads PRIORITY: from externalRef
        if (t.getPriority() != null && (t.getExternalRef() == null || t.getExternalRef().isBlank())) {
            m.put("externalRefDisplay", "PRIORITY:" + t.getPriority());
        }
        return m;
    }

    private int sumLogged(Long taskId) {
        return workLogRepository.findAll().stream()
                .filter(w -> taskId.equals(w.getTaskId()))
                .mapToInt(WorkLog::getMinutes)
                .sum();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "OPEN";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if ("CLOSED".equals(normalized)) {
            normalized = "DONE";
        }
        final String candidate = normalized;
        List<String> allowed = statuses();
        if (allowed.stream().noneMatch(a -> a.equalsIgnoreCase(candidate))) {
            throw new IllegalArgumentException("status must be one of: " + String.join(", ", allowed));
        }
        return candidate;
    }

    private String createScope() {
        Object v = taskRules().get("createScope");
        if (v != null && !String.valueOf(v).isBlank()) {
            return String.valueOf(v).trim().toUpperCase(Locale.ROOT);
        }
        return "TEAM";
    }

    private List<String> statuses() {
        Object raw = taskRules().get("statuses");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(o -> String.valueOf(o).trim().toUpperCase(Locale.ROOT)).toList();
        }
        return List.of("OPEN", "IN_PROGRESS", "DONE");
    }

    private Set<String> openStatuses() {
        Object raw = taskRules().get("openStatuses");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(o -> String.valueOf(o).trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        }
        return Set.of("OPEN", "IN_PROGRESS");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> taskRules() {
        Map<String, Object> settings = tenantSettingsRepository.findAll().stream()
                .findFirst().map(TenantSettings::getSettings).orElse(Map.of());
        Object raw = settings.get("taskRules");
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> {
                if (k != null) out.put(String.valueOf(k), v);
            });
            return out;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Long> parseAssignees(Object raw) {
        List<Long> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o == null) continue;
                out.add(Long.valueOf(String.valueOf(o)));
            }
        } else if (raw != null && !String.valueOf(raw).isBlank()) {
            for (String part : String.valueOf(raw).split(",")) {
                if (!part.isBlank()) out.add(Long.valueOf(part.trim()));
            }
        }
        return out;
    }

    private static String first(Map<String, Object> body, String... keys) {
        for (String k : keys) {
            String v = str(body.get(k));
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
