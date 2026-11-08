package com.platform.people.api.rest;

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
 * People report facade. Leave/attendance exports are owned by leave / time-work / report-service.
 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports")
public class ReportFacadeController {

    private final EmployeeRepository employeeRepository;

    public ReportFacadeController(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @GetMapping("/employees")
    @RequiresPermission(feature = "employees", action = "view")
    public Map<String, Object> employees() {
        List<Map<String, Object>> rows = employeeRepository.findAll().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("employeeCode", e.getEmployeeCode());
                    m.put("fullName", e.getFullName());
                    m.put("email", e.getEmail());
                    m.put("active", e.isActive());
                    m.put("status", e.getStatus());
                    return m;
                })
                .toList();
        return Map.of("content", rows, "totalElements", rows.size());
    }
}
