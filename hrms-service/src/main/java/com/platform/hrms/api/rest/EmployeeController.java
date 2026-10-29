package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.hrms.api.dto.EmployeeRequest;
import com.platform.hrms.api.dto.EmployeeResponse;
import com.platform.hrms.application.EmployeeService;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/employees")
@Tag(name = "Employees", description = "Reusable employee APIs (tenant-routed)")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "employees", action = "create")
    @Operation(summary = "Create employee (auto QII-#### if code omitted; team sets department)")
    public EmployeeResponse create(@Valid @RequestBody EmployeeRequest request) {
        return employeeService.create(request);
    }

    @PutMapping("/{id}")
    @RequiresPermission(feature = "employees", action = "edit")
    @Operation(summary = "Update employee employment / status / org fields")
    public EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
        return employeeService.update(id, request);
    }

    @GetMapping("/next-code")
    @RequiresPermission(feature = "employees", action = "create")
    @Operation(summary = "Preview next employee code")
    public Map<String, String> nextCode() {
        return Map.of("employeeCode", employeeService.nextEmployeeCode());
    }

    @GetMapping("/{id}")
    @RequiresPermission(feature = "employees", action = "view")
    @Operation(summary = "Get employee by id")
    public EmployeeResponse get(@PathVariable Long id) {
        return employeeService.get(id);
    }

    @GetMapping("/{id}/reporting-line")
    @RequiresPermission(feature = "employees", action = "view")
    @Operation(summary = "Reporting line history (manager changes)")
    public List<Map<String, Object>> reportingLine(@PathVariable Long id) {
        return employeeService.reportingLine(id);
    }

    @PostMapping("/{id}/roles")
    @RequiresPermission(feature = "employees", action = "edit")
    @Operation(summary = "Replace employee role assignments")
    public Map<String, Object> assignRoles(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> codes = body.get("roleCodes") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        List<String> assigned = employeeService.assignRoles(id, codes);
        return Map.of("employeeId", id, "roleCodes", assigned);
    }

    @PatchMapping("/{id}/status")
    @RequiresPermission(feature = "employees", action = "edit")
    @Operation(summary = "Change employee employment status")
    public EmployeeResponse patchStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object status = body.get("status");
        return employeeService.updateStatus(id, status == null ? null : String.valueOf(status));
    }

    @GetMapping
    @RequiresPermission(feature = "employees", action = "view")
    @Operation(summary = "List employees (dynamic page/size/q/filter.*)")
    public PageResponse<EmployeeResponse> list(@RequestParam Map<String, String> allParams) {
        return employeeService.list(PageQuery.fromParams(allParams));
    }
}
