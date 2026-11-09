package com.platform.people.application;

import com.platform.people.client.LeaveClient;
import com.platform.people.client.TimeWorkClient;
import com.platform.people.domain.Employee;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aggregates payroll facts via Leave + Time&amp;Work HTTP (no cross-DB joins).
 * PAY-01: includes daysEmployed / workingDays so Payroll calculate can run after the split.
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

        YearMonth period = parsePeriod(periodLabel);
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();
        int workingDaysInMonth = period.lengthOfMonth(); // calendar days; schedule-aware count is Time-Work's job
        int daysEmployed = daysEmployedInPeriod(emp.getStartDate(), emp.getContractEnd(), periodStart, periodEnd);

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
        out.put("startDate", emp.getStartDate() == null ? null : emp.getStartDate().toString());
        out.put("contractEnd", emp.getContractEnd() == null ? null : emp.getContractEnd().toString());
        out.put("workingDaysInMonth", workingDaysInMonth);
        out.put("daysEmployed", daysEmployed);
        out.put("leave", leave);
        out.put("attendance", attendance);
        // Flatten provider maps for HrmsPayrollFactsClient / PayrollCalculationService
        out.putAll(attendance);
        out.putAll(leave);
        // Ensure employment fields win over any colliding provider keys
        out.put("workingDaysInMonth", workingDaysInMonth);
        out.put("daysEmployed", daysEmployed);
        if (!out.containsKey("hoursWorked") && out.get("regularHours") != null) {
            out.put("hoursWorked", out.get("regularHours"));
        }
        if (!out.containsKey("absentDays") && out.get("absenceDays") != null) {
            out.put("absentDays", out.get("absenceDays"));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listActiveEmployeeIds() {
        // PAY-10 partial: include anyone with a start date (leavers mid-period still need payroll)
        List<Long> ids = employeeRepository.findAll().stream()
                .filter(e -> e.getStartDate() != null)
                .map(Employee::getId)
                .sorted()
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeIds", ids);
        out.put("count", ids.size());
        return out;
    }

    static int daysEmployedInPeriod(LocalDate start, LocalDate end, LocalDate periodStart, LocalDate periodEnd) {
        if (start == null) {
            return 0;
        }
        LocalDate from = start.isAfter(periodStart) ? start : periodStart;
        LocalDate to = end == null || end.isAfter(periodEnd) ? periodEnd : end;
        if (to.isBefore(from)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(from, to) + 1;
    }

    private static YearMonth parsePeriod(String periodLabel) {
        if (periodLabel == null || periodLabel.length() < 7) {
            throw new IllegalArgumentException("periodLabel required as YYYY-MM");
        }
        return YearMonth.parse(periodLabel.substring(0, 7));
    }

    private static String resolvePayType(Employee emp) {
        Object pt = emp.getCustomFields() == null ? null : emp.getCustomFields().get("payType");
        if (pt != null && !String.valueOf(pt).isBlank()) {
            return String.valueOf(pt).trim().toUpperCase(Locale.ROOT);
        }
        return "MONTHLY";
    }
}
