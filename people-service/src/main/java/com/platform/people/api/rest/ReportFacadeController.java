package com.platform.people.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.people.application.DataScopeService;
import com.platform.people.domain.Employee;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.security.RequiresPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * People report facade — scoped employee list for report pickers.
 * CSV master list is owned by report-service ({@code GET /api/reports/employees.csv}).
 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports")
public class ReportFacadeController {

    private final EmployeeRepository employeeRepository;
    private final DataScopeService dataScopeService;

    public ReportFacadeController(EmployeeRepository employeeRepository, DataScopeService dataScopeService) {
        this.employeeRepository = employeeRepository;
        this.dataScopeService = dataScopeService;
    }

    @GetMapping("/employees")
    @RequiresPermission(feature = "employees", action = "view")
    public Map<String, Object> employees() {
        DataScopeService.Visibility vis = dataScopeService.visibility(
                AuthContext.requireEmployeeId(), "employees", "view");
        List<Map<String, Object>> rows = employeeRepository.findAll().stream()
                .filter(e -> vis.allows(e.getId()))
                .map(this::row)
                .toList();
        return Map.of("content", rows, "totalElements", rows.size());
    }

    private Map<String, Object> row(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("employeeCode", e.getEmployeeCode());
        m.put("fullName", e.getFullName());
        m.put("email", e.getEmail());
        m.put("active", e.isActive());
        m.put("status", e.getStatus());
        m.put("departmentId", e.getDepartmentId());
        m.put("teamId", e.getTeamId());
        m.put("locationId", e.getLocationId());
        m.put("employmentType", e.getEmploymentType());
        m.put("workPattern", e.getWorkPattern());
        m.put("startDate", e.getStartDate() == null ? null : e.getStartDate().toString());
        return m;
    }
}
