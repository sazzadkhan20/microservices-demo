package com.platform.people.application;

import com.platform.people.domain.Employee;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.client.LeaveClient;
import com.platform.people.client.TimeWorkClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aggregates payroll facts via Leave + Time&amp;Work HTTP (no cross-DB joins).
 */
@Service
public class PayrollFactsService {

    private final EmployeeRepository employeeRepository;
    private final LeaveClient leaveClient;
    private final TimeWorkClient timeWorkClient;

    public PayrollFactsService(
            EmployeeRepository employeeRepository,
            LeaveClient leaveClient,
            TimeWorkClient timeWorkClient) {
        this.employeeRepository = employeeRepository;
        this.leaveClient = leaveClient;
        this.timeWorkClient = timeWorkClient;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> computeFacts(Long employeeId, String periodLabel) {
        Employee emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        Map<String, Object> leave = leaveClient.leaveFacts(employeeId, periodLabel);
        Map<String, Object> attendance = timeWorkClient.attendanceFacts(employeeId, periodLabel);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeId", emp.getId());
        out.put("employeeCode", emp.getEmployeeCode());
        out.put("fullName", emp.getFullName());
        out.put("period", periodLabel);
        out.put("payType", resolvePayType(emp));
        out.put("currency", emp.getPayCurrency() == null ? "BDT" : emp.getPayCurrency());
        out.put("baseSalary", emp.getBaseSalary());
        out.put("hourlyRate", emp.getHourlyRate());
        out.put("active", emp.isActive());
        out.put("leave", leave);
        out.put("attendance", attendance);
        out.putAll(attendance);
        out.putAll(leave);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listActiveEmployeeIds() {
        List<Long> ids = employeeRepository.findAll().stream()
                .filter(Employee::isActive)
                .map(Employee::getId)
                .sorted()
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeIds", ids);
        out.put("count", ids.size());
        return out;
    }

    private static String resolvePayType(Employee emp) {
        Object pt = emp.getCustomFields() == null ? null : emp.getCustomFields().get("payType");
        if (pt != null && !String.valueOf(pt).isBlank()) {
            return String.valueOf(pt).trim().toUpperCase(Locale.ROOT);
        }
        return "MONTHLY";
    }
}
