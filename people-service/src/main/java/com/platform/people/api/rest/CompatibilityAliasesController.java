package com.platform.people.api.rest;

import com.platform.people.domain.Department;
import com.platform.people.infrastructure.persistence.DepartmentRepository;
import com.platform.people.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Stable aliases for clients that call legacy/short paths.
 * Work-task aliases live on time-work-service.
 */
@RestController
@RequestMapping("/api")
@Hidden
public class CompatibilityAliasesController {

    private final DepartmentRepository departmentRepository;

    public CompatibilityAliasesController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @GetMapping("/departments")
    @RequiresPermission(feature = "hierarchy", action = "view")
    public List<Department> departmentsAlias() {
        return departmentRepository.findAll();
    }
}
