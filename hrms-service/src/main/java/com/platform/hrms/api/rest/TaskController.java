package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.common.security.AuthContext;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.domain.WorkTask;
import com.platform.hrms.infrastructure.persistence.WorkTaskRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
// Map body accepted so FE can send description/priority without schema migration

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Work tasks / billable items")
public class TaskController {

    private final WorkTaskRepository workTaskRepository;

    public TaskController(WorkTaskRepository workTaskRepository) {
        this.workTaskRepository = workTaskRepository;
    }

    @GetMapping
    @Operation(summary = "List tasks (paginated, searchable by title or client name)")
    public PageResponse<WorkTask> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        return DynamicListSupport.page(
                workTaskRepository.findAll(),
                query,
                task -> DynamicListSupport.containsIgnoreCase(task.getTitle(), query.q())
                        || DynamicListSupport.containsIgnoreCase(task.getClientName(), query.q()),
                task -> Map.of(
                        "status", task.getStatus() == null ? "" : task.getStatus(),
                        "clientName", task.getClientName() == null ? "" : task.getClientName(),
                        "externalRef", task.getExternalRef() == null ? "" : task.getExternalRef()
                )
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "org", action = "manage")
    @Operation(summary = "Create a new task (title, description→clientName, priority→externalRef)")
    public WorkTask create(@RequestBody Map<String, Object> body) {
        WorkTask task = new WorkTask();
        Object title = body.get("title");
        if (title == null || String.valueOf(title).isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        task.setTitle(String.valueOf(title).trim());
        Object desc = body.get("description") != null ? body.get("description") : body.get("clientName");
        if (desc != null && !String.valueOf(desc).isBlank()) {
            task.setClientName(String.valueOf(desc).trim());
        }
        Object priority = body.get("priority");
        if (priority != null && !String.valueOf(priority).isBlank()) {
            task.setExternalRef("PRIORITY:" + String.valueOf(priority).trim().toUpperCase());
        } else if (body.get("externalRef") != null) {
            task.setExternalRef(String.valueOf(body.get("externalRef")));
        }
        Object status = body.get("status");
        task.setStatus(status == null || String.valueOf(status).isBlank() ? "OPEN" : String.valueOf(status));
        Object due = body.get("dueDate");
        if (due != null && !String.valueOf(due).isBlank()) {
            task.setDueDate(java.time.LocalDate.parse(String.valueOf(due)));
        }
        task.setCreatedBy(AuthContext.requireEmployeeId());
        task.setCreatedAt(Instant.now());
        return workTaskRepository.save(task);
    }
}
