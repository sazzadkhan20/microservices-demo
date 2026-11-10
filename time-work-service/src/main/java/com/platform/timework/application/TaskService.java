package com.platform.timework.application;

import com.platform.common.security.AuthContext;
import com.platform.timework.client.people.EmployeeView;
import com.platform.timework.client.people.PeopleAuthzClient;
import com.platform.timework.domain.TenantSettings;
import com.platform.timework.domain.WorkLog;
import com.platform.timework.domain.WorkTask;
import com.platform.timework.domain.WorkTaskAssignee;
import com.platform.timework.client.people.PeopleEmployeeGateway;
import com.platform.timework.infrastructure.persistence.TenantSettingsRepository;
import com.platform.timework.infrastructure.persistence.WorkLogRepository;
import com.platform.timework.infrastructure.persistence.WorkTaskAssigneeRepository;
import com.platform.timework.infrastructure.persistence.WorkTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Config-driven tasks (FR-TSK-01..04).
 * View visibility uses {@code tasks:view} scope; assignee pool uses create/edit data-scope
 * (TEAM = reporting subtree under the actor — not org-team peers).
 */
@Service
public class TaskService {

    private final WorkTaskRepository taskRepository;
    private final WorkTaskAssigneeRepository assigneeRepository;
    private final WorkLogRepository workLogRepository;
    private final PeopleEmployeeGateway employeeRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final DataScopeService dataScopeService;
    private final PeopleAuthzClient rbacService;

    public TaskService(
            WorkTaskRepository taskRepository,
            WorkTaskAssigneeRepository assigneeRepository,
            WorkLogRepository workLogRepository,
            PeopleEmployeeGateway employeeRepository,
            TenantSettingsRepository tenantSettingsRepository,
            DataScopeService dataScopeService,
            PeopleAuthzClient rbacService) {
        this.taskRepository = taskRepository;
        this.assigneeRepository = assigneeRepository;
        this.workLogRepository = workLogRepository;
        this.employeeRepository = employeeRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.dataScopeService = dataScopeService;
        this.rbacService = rbacService;
    }

    /**
     * List tasks visible to the caller:
     * ALL → every task; TEAM (manager) → created by me or assigned to me;
     * OWN → assigned to me only.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listEnriched() {
        Long actor = AuthContext.requireEmployeeId();
        DataScopeService.Visibility vis = dataScopeService.visibility(actor, "tasks", "view");
        Map<Long, Integer> loggedByTask = loggedMinutesByTask();
        LocalDate today = LocalDate.now();

        Map<Long, Set<Long>> assigneesByTask = new HashMap<>();
        for (WorkTaskAssignee a : assigneeRepository.findAll()) {
            assigneesByTask.computeIfAbsent(a.getTaskId(), k -> new HashSet<>()).add(a.getEmployeeId());
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (WorkTask t : taskRepository.findAll()) {
            Set<Long> assignees = assigneesByTask.getOrDefault(t.getId(), Set.of());
            if (!isVisible(vis, actor, t, assignees)) {
                continue;
            }
            out.add(toView(t, assignees, loggedByTask.getOrDefault(t.getId(), 0), today));
        }
        return out;
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
            out.add(toView(t, new HashSet<>(assigneeIds(tid)), sumLogged(tid), today));
        }
        return out;
    }

    /**
     * Assignees the actor may pick when creating/editing a task.
     * TEAM → self + reporting subtree; ALL → all active; OWN → self only.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> assigneeCandidates() {
        Long actor = AuthContext.requireEmployeeId();
        Set<Long> allowed = allowedAssigneeIds(actor);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long id : allowed) {
            EmployeeView e = employeeRepository.findById(id).orElse(null);
            if (e == null || !e.isActive()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("fullName", e.getFullName());
            row.put("employeeCode", e.getEmployeeCode());
            row.put("teamId", e.getTeamId());
            row.put("managerId", e.getManagerId());
            out.add(row);
        }
        out.sort((a, b) -> String.valueOf(a.get("fullName")).compareToIgnoreCase(String.valueOf(b.get("fullName"))));
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
        task.setExternalRef(normalizeExternalRef(str(body.get("externalRef")), true));
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
        return toView(saved, new HashSet<>(assignees), 0, LocalDate.now());
    }

    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> body) {
        Long actor = AuthContext.requireEmployeeId();
        WorkTask task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));
        Set<Long> currentAssignees = new HashSet<>(assigneeIds(id));
        DataScopeService.Visibility viewVis = dataScopeService.visibility(actor, "tasks", "view");
        if (!isVisible(viewVis, actor, task, currentAssignees)
                && !dataScopeService.visibility(actor, "tasks", "edit").isAll()) {
            throw new SecurityException("Forbidden: you cannot edit this task");
        }
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
            task.setExternalRef(normalizeExternalRef(str(body.get("externalRef")), false));
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
            currentAssignees = new HashSet<>(assignees);
        }
        WorkTask saved = taskRepository.save(task);
        return toView(saved, currentAssignees, sumLogged(saved.getId()), LocalDate.now());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> rules() {
        Map<String, Object> cfg = taskRules();
        Long actor = AuthContext.requireEmployeeId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("createScope", createScopeSetting());
        out.put("viewScope", dataScopeService.visibility(actor, "tasks", "view").scope());
        out.put("assignScope", dataScopeService.visibility(actor, "tasks", "create").scope());
        out.put("statuses", statuses());
        out.put("openStatuses", new ArrayList<>(openStatuses()));
        out.put("priorities", List.of("LOW", "MEDIUM", "HIGH"));
        out.putAll(cfg);
        return out;
    }

    private boolean isVisible(DataScopeService.Visibility vis, Long actor, WorkTask t, Set<Long> assignees) {
        if (vis == null || "NONE".equals(vis.scope())) {
            return false;
        }
        if (vis.isAll()) {
            return true;
        }
        boolean assignedToMe = assignees.contains(actor);
        boolean createdByMe = Objects.equals(t.getCreatedBy(), actor);
        return switch (vis.scope()) {
            // Manager: tasks I created + tasks assigned to me.
            case "TEAM" -> createdByMe || assignedToMe;
            // Employee / HR (OWN): only tasks assigned to me.
            case "OWN" -> assignedToMe;
            default -> false;
        };
    }

    private void replaceAssignees(Long taskId, List<Long> assignees, Long actor) {
        Set<Long> allowed = allowedAssigneeIds(actor);
        for (Long empId : assignees) {
            EmployeeView e = employeeRepository.findById(empId)
                    .orElseThrow(() -> new IllegalArgumentException("Assignee not found: " + empId));
            if (!e.isActive()) {
                throw new IllegalStateException("Assignee is inactive: " + empId);
            }
            if (!allowed.contains(empId)) {
                throw new IllegalStateException(
                        "Cannot assign employee " + empId
                                + " — only people in your reporting team (task-create scope) can be assignees");
            }
        }
        assigneeRepository.deleteByTaskId(taskId);
        for (Long empId : assignees) {
            assigneeRepository.save(new WorkTaskAssignee(taskId, empId));
        }
    }

    /**
     * Assignees allowed for create/edit.
     * Prefer {@code tasks:create} data scope; fall back to {@code tasks:edit}.
     * TEAM = reporting subtree (self + underlings). Never expand to org-team peers.
     */
    private Set<Long> allowedAssigneeIds(Long actor) {
        if (rbacService.hasWildcard(actor)) {
            return activeEmployeeIds();
        }
        DataScopeService.Visibility createVis = dataScopeService.visibility(actor, "tasks", "create");
        if ("NONE".equals(createVis.scope())) {
            createVis = dataScopeService.visibility(actor, "tasks", "edit");
        }
        return switch (createVis.scope()) {
            case "ALL" -> activeEmployeeIds();
            case "TEAM" -> createVis.teamIds().stream()
                    .filter(id -> {
                        EmployeeView e = employeeRepository.findById(id).orElse(null);
                        return e != null && e.isActive();
                    })
                    .collect(Collectors.toCollection(HashSet::new));
            case "OWN" -> Set.of(actor);
            default -> Set.of(actor);
        };
    }

    private Set<Long> activeEmployeeIds() {
        Set<Long> out = new HashSet<>();
        for (EmployeeView e : employeeRepository.findAll()) {
            if (e.isActive() && e.getId() != null) {
                out.add(e.getId());
            }
        }
        return out;
    }

    private Map<String, Object> toView(WorkTask t, Set<Long> assignees, int loggedMinutes, LocalDate today) {
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
        List<Long> assigneeList = assignees == null ? List.of() : assignees.stream().sorted().toList();
        m.put("assigneeIds", assigneeList);
        m.put("assignees", assigneeList);
        m.put("loggedMinutes", loggedMinutes);
        m.put("loggedHours", Math.round(loggedMinutes / 60.0 * 100.0) / 100.0);
        boolean overdue = t.getDueDate() != null
                && t.getDueDate().isBefore(today)
                && openStatuses().contains(
                        t.getStatus() == null ? "" : t.getStatus().toUpperCase(Locale.ROOT));
        m.put("overdue", overdue);
        return m;
    }

    private List<Long> assigneeIds(Long taskId) {
        return assigneeRepository.findByTaskId(taskId).stream()
                .map(WorkTaskAssignee::getEmployeeId)
                .toList();
    }

    private Map<Long, Integer> loggedMinutesByTask() {
        Map<Long, Integer> loggedByTask = new HashMap<>();
        for (WorkLog wl : workLogRepository.findAll()) {
            if (wl.getTaskId() == null) continue;
            loggedByTask.merge(wl.getTaskId(), wl.getMinutes(), Integer::sum);
        }
        return loggedByTask;
    }

    private int sumLogged(Long taskId) {
        return workLogRepository.findAll().stream()
                .filter(w -> taskId.equals(w.getTaskId()))
                .mapToInt(WorkLog::getMinutes)
                .sum();
    }

    private String normalizeExternalRef(String ext, boolean creating) {
        if (ext == null || ext.isBlank()) {
            return creating ? null : null;
        }
        String trimmed = ext.trim();
        if (trimmed.toUpperCase(Locale.ROOT).startsWith("PRIORITY:")) {
            throw new IllegalArgumentException(
                    "externalRef is the project-module id — do not store priority here (use priority field)");
        }
        if (trimmed.length() > 64) {
            throw new IllegalArgumentException("externalRef must be ≤ 64 characters");
        }
        return trimmed;
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

    private String createScopeSetting() {
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
