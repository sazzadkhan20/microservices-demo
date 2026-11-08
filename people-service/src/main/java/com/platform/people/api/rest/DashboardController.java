package com.platform.people.api.rest;

import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * People dashboard slice. Attendance / leave widgets come from their services via BFF/FE.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final EmployeeRepository employeeRepository;

    public DashboardController(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @GetMapping("/summary")
    @RequiresPermission(feature = "employees", action = "view")
    @Operation(summary = "People headcount summary (no cross-service DB)")
    public Map<String, Object> summary() {
        long total = employeeRepository.count();
        long active = employeeRepository.findAll().stream().filter(e -> e.isActive()).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeesTotal", total);
        out.put("employeesActive", active);
        return out;
    }
}
