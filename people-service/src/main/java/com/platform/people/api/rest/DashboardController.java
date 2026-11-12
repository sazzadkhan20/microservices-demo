package com.platform.people.api.rest;

import com.platform.people.application.AdminDashboardService;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * People dashboard slices. Attendance / leave widgets come from their services (gateway-routed).
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final EmployeeRepository employeeRepository;
    private final AdminDashboardService adminDashboardService;

    public DashboardController(
            EmployeeRepository employeeRepository,
            AdminDashboardService adminDashboardService) {
        this.employeeRepository = employeeRepository;
        this.adminDashboardService = adminDashboardService;
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

    @GetMapping("/admin")
    @RequiresPermission(feature = "employees", action = "view")
    @Operation(summary = "Admin dashboard — headcount by location/type and contracts ending in 30 days (report-scoped)")
    public Map<String, Object> admin(@RequestParam(required = false) String date) {
        LocalDate d = date == null || date.isBlank() ? null : LocalDate.parse(date.trim());
        return adminDashboardService.admin(d);
    }
}
