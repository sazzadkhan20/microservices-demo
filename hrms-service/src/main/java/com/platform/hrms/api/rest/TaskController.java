package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.hrms.application.TaskService;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Config-driven work tasks (FR-TSK-01..04)")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/rules")
    @RequiresPermission(feature = "tasks", action = "view")
    @Operation(summary = "Task create/status rules from tenant settings")
    public Map<String, Object> rules() {
        return taskService.rules();
    }

    @GetMapping("/assigned")
    @RequiresPermission(feature = "attendance", action = "checkin")
    @Operation(summary = "Open tasks assigned to the caller (work-log picker)")
    public List<Map<String, Object>> assigned() {
        return taskService.assignedOpenForCaller();
    }

    @GetMapping
    @RequiresPermission(feature = "tasks", action = "view")
    @Operation(summary = "List tasks with logged totals and overdue flag")
    public PageResponse<Map<String, Object>> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        return DynamicListSupport.page(
                taskService.listEnriched(),
                query,
                task -> DynamicListSupport.containsIgnoreCase(String.valueOf(task.get("title")), query.q())
                        || DynamicListSupport.containsIgnoreCase(String.valueOf(task.get("clientName")), query.q()),
                task -> Map.of(
                        "status", task.get("status") == null ? "" : String.valueOf(task.get("status")),
                        "clientName", task.get("clientName") == null ? "" : String.valueOf(task.get("clientName")),
                        "externalRef", task.get("externalRef") == null ? "" : String.valueOf(task.get("externalRef")),
                        "overdue", String.valueOf(Boolean.TRUE.equals(task.get("overdue")))
                )
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "tasks", action = "create")
    @Operation(summary = "Create task with assignees (scoped by taskRules.createScope)")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        return taskService.create(body);
    }

    @PutMapping("/{id}")
    @RequiresPermission(feature = "tasks", action = "edit")
    @Operation(summary = "Update task / reassign / change status")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return taskService.update(id, body);
    }
}
