package com.platform.payroll.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.payroll.application.PayrollException;
import com.platform.payroll.application.PayrollRunService;
import com.platform.payroll.application.PayrollSettingsService;
import com.platform.payroll.application.PayslipSelfService;
import com.platform.payroll.application.SalaryService;
import com.platform.payroll.security.PermissionSet;
import com.platform.payroll.security.RbacInterceptor;
import com.platform.payroll.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Salary records, payroll rules and the employee self-view (FR-SAL-01..03, §1.9). */
@RestController
@RequestMapping("/api/payroll")
@Tag(name = "Salary, rules and my pay")
public class SalaryAndSelfController {

    private final SalaryService salaries;
    private final PayrollSettingsService settings;
    private final PayslipSelfService self;
    private final PayrollRunService runs;

    public SalaryAndSelfController(SalaryService salaries, PayrollSettingsService settings,
                                   PayslipSelfService self, PayrollRunService runs) {
        this.salaries = salaries;
        this.settings = settings;
        this.self = self;
        this.runs = runs;
    }

    // ---- salary (FR-SAL-01/02)

    @GetMapping("/salary/employees/{employeeId}")
    @RequiresPermission(feature = "salary", action = "view")
    @Operation(summary = "Current pay + salary history — own with salary:view; others need salary:edit or payroll:run")
    public Map<String, Object> salary(@PathVariable Long employeeId, HttpServletRequest request) {
        Long me = AuthContext.requireEmployeeId();
        if (!me.equals(employeeId) && !canManageSalary(request)) {
            throw new PayrollException(403, "FORBIDDEN", "Salary is visible only to its owner and to salary managers");
        }
        return salaries.forEmployee(employeeId);
    }

    @PostMapping("/salary/employees/{employeeId}")
    @RequiresPermission(feature = "salary", action = "edit")
    @Operation(summary = "Record a salary change {payType, amount, currency, effectiveDate, reason} (audited)")
    public Map<String, Object> recordSalary(@PathVariable Long employeeId, @RequestBody Map<String, Object> body) {
        return salaries.record(employeeId, body, AuthContext.requireEmployeeId());
    }

    // ---- rules (§1.9)

    @GetMapping("/settings")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Payroll rules (SRS defaults until changed) and the fixed rules")
    public Map<String, Object> getSettings() {
        Map<String, Object> out = new LinkedHashMap<>(PayrollSettingsService.view(settings.load()));
        out.put("fixedRules", PayrollSettingsService.FIXED_RULES);
        out.put("defaults", Map.of("perDayRateBasis", "WORKING_DAYS", "absenceRule", "BALANCE_FIRST",
                "absenceCoverOrder", List.of("CASUAL", "ANNUAL"), "notifyPayslipReady", true));
        return out;
    }

    @PutMapping("/settings")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Update payroll rules (audited). Applies to drafts on their next calculation")
    public Map<String, Object> updateSettings(@RequestBody Map<String, Object> body) {
        settings.update(body, AuthContext.requireEmployeeId());
        return getSettings();
    }

    // ---- my pay (FR-SAL-03)

    @GetMapping("/me/payslips")
    @RequiresPermission(feature = "payroll", action = "view")
    @Operation(summary = "My finalised payslips")
    public List<Map<String, Object>> myPayslips() {
        return self.list(AuthContext.requireEmployeeId());
    }

    @GetMapping("/me/payslips/{period}")
    @RequiresPermission(feature = "payroll", action = "view")
    @Operation(summary = "My finalised payslip for a month")
    public Map<String, Object> myPayslip(@PathVariable String period) {
        return self.get(AuthContext.requireEmployeeId(), period);
    }

    @GetMapping("/me/estimate")
    @RequiresPermission(feature = "payroll", action = "view")
    @Operation(summary = "My month-to-date estimate (live, not stored)")
    public Map<String, Object> myEstimate() {
        return runs.estimate(AuthContext.requireEmployeeId());
    }

    @GetMapping("/me/salary")
    @RequiresPermission(feature = "salary", action = "view")
    @Operation(summary = "My current pay")
    public Map<String, Object> mySalary() {
        return salaries.current(AuthContext.requireEmployeeId());
    }

    private static boolean canManageSalary(HttpServletRequest request) {
        return request.getAttribute(RbacInterceptor.PERMISSIONS_ATTR) instanceof PermissionSet p
                && (p.has("salary", "edit") || p.has("payroll", "run"));
    }
}
