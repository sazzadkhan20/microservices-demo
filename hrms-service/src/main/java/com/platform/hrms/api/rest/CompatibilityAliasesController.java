package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.domain.Department;
import com.platform.hrms.domain.WorkTask;
import com.platform.hrms.infrastructure.persistence.DepartmentRepository;
import com.platform.hrms.infrastructure.persistence.WorkTaskRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Stable aliases for clients that call legacy/short paths.
 * Canonical resources remain under {@code /api/org/departments} and {@code /api/tasks}.
 * Config-driven tenants share the same aliases — no business-specific branching.
 */
@RestController
@RequestMapping("/api")
@Hidden
public class CompatibilityAliasesController {

    private final DepartmentRepository departmentRepository;
    private final WorkTaskRepository workTaskRepository;

    public CompatibilityAliasesController(
            DepartmentRepository departmentRepository,
            WorkTaskRepository workTaskRepository) {
        this.departmentRepository = departmentRepository;
        this.workTaskRepository = workTaskRepository;
    }

    @GetMapping("/departments")
    @RequiresPermission(feature = "hierarchy", action = "view")
    public List<Department> departmentsAlias() {
        return departmentRepository.findAll();
    }

    @GetMapping("/work-tasks")
    @RequiresPermission(feature = "tasks", action = "view")
    public PageResponse<WorkTask> workTasksAlias(@RequestParam Map<String, String> params) {
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
}
