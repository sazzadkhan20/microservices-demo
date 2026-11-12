package com.platform.people.api.rest;

import com.platform.common.security.PublicEndpoint;
import com.platform.people.domain.Employee;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * S2S (internal key, /api/internal/**): employment facts for Payroll (docs/PAYROLL_SPEC.md §2.1).
 * People owns employees and employment periods; it does not aggregate attendance or leave.
 * FR-PAY-01 — everyone employed for any part of the month, including leavers who are now inactive.
 */
@RestController
@RequestMapping("/api/internal/payroll")
@PublicEndpoint(reason = "S2S payroll employment facts; gated by InternalApiInterceptor (X-Internal-Key)")
@Hidden
public class InternalPayrollEmployeesController {

    private final EmployeeRepository employees;

    public InternalPayrollEmployeesController(EmployeeRepository employees) {
        this.employees = employees;
    }

    @GetMapping("/employees")
    @Transactional(readOnly = true)
    public Map<String, Object> employees(@RequestParam String period,
                                         @RequestParam(required = false) String employeeIds) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "period must be YYYY-MM");
        }
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        Set<Long> only = employeeIds == null || employeeIds.isBlank() ? null
                : Arrays.stream(employeeIds.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).collect(Collectors.toSet());
        List<Employee> source = only == null ? employees.findAll() : employees.findAllById(only);
        List<Map<String, Object>> rows = source.stream()
                .filter(e -> e.getStartDate() != null && !e.getStartDate().isAfter(monthEnd))
                .filter(e -> e.getExitDate() == null || !e.getExitDate().isBefore(monthStart))
                .sorted(Comparator.comparing(Employee::getId))
                .map(InternalPayrollEmployeesController::row)
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", ym.toString());
        out.put("employees", rows);
        return out;
    }

    private static Map<String, Object> row(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("employeeCode", e.getEmployeeCode());
        m.put("fullName", e.getFullName());
        m.put("payCurrency", e.getPayCurrency());
        m.put("locationId", e.getLocationId());
        m.put("startDate", e.getStartDate() == null ? null : e.getStartDate().toString());
        m.put("exitDate", e.getExitDate() == null ? null : e.getExitDate().toString());
        m.put("status", e.getStatus());
        return m;
    }
}
