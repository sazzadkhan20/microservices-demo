package com.platform.hrms.api.rest;

import com.platform.hrms.application.PayrollFactsService;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * FR-SAL-01..03, FR-PAY-01..05 — payroll facts REST API consumed by payroll-service.
 * Authenticated via service-key (passed by payroll-service); employee scope guarded by leave:view.
 */
@RestController
@RequestMapping("/api/payroll-facts")
@Tag(name = "Payroll Facts", description = "HRMS payroll facts for payroll-service (service-key auth)")
public class PayrollFactsController {

    private final PayrollFactsService payrollFactsService;

    public PayrollFactsController(PayrollFactsService payrollFactsService) {
        this.payrollFactsService = payrollFactsService;
    }

    @GetMapping("/employee/{id}")
    @RequiresPermission(feature = "leave", action = "view", allowInternal = true)
    @Operation(summary = "Compute payroll facts for employee in period YYYY-MM — leave:view or internal key")
    public Map<String, Object> facts(
            @PathVariable Long id,
            @RequestParam String period) {
        return payrollFactsService.computeFacts(id, period);
    }

    @GetMapping("/active-employees")
    @RequiresPermission(feature = "employees", action = "view", allowInternal = true)
    @Operation(summary = "Active employee ids for payroll month run — employees:view or internal key")
    public Map<String, Object> activeEmployees() {
        return payrollFactsService.listActiveEmployeeIds();
    }

    @PostMapping("/apply-leave-cover")
    @RequiresPermission(feature = "leave", action = "manage", allowInternal = true)
    @Operation(summary = "Apply (or reverse) leave balance deductions from payroll absence cover — leave:manage or internal key")
    public Map<String, Object> applyLeaveCover(@RequestBody Map<String, Object> body) {
        Long employeeId = Long.valueOf(String.valueOf(body.get("employeeId")));
        String periodLabel = String.valueOf(body.get("periodLabel"));
        boolean reverse = Boolean.parseBoolean(String.valueOf(body.getOrDefault("reverse", "false")));

        @SuppressWarnings("unchecked")
        Map<String, Object> raw = body.get("consumedByType") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        java.util.Map<String, BigDecimal> consumedByType = new java.util.LinkedHashMap<>();
        raw.forEach((k, v) -> {
            if (k != null && v != null) {
                try {
                    consumedByType.put(String.valueOf(k).toUpperCase(java.util.Locale.ROOT),
                            new BigDecimal(String.valueOf(v)));
                } catch (Exception ignored) { /* skip malformed */ }
            }
        });

        payrollFactsService.applyLeaveCover(employeeId, periodLabel, consumedByType, reverse);
        return Map.of(
                "employeeId", employeeId,
                "periodLabel", periodLabel,
                "consumedByType", consumedByType,
                "reverse", reverse,
                "status", "OK"
        );
    }
}
