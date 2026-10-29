package com.platform.payroll.api.rest;

import com.platform.payroll.api.dto.CalculatePayrollRequest;
import com.platform.payroll.api.dto.CalculatePayrollResponse;
import com.platform.payroll.application.PayrollCalculationService;
import com.platform.payroll.extension.config.PayrollConfigLoader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payroll")
@Tag(name = "Payroll", description = "Reusable payroll calculation (tenant-routed)")
public class PayrollController {

    private final PayrollCalculationService calculationService;
    private final PayrollConfigLoader configLoader;

    public PayrollController(PayrollCalculationService calculationService, PayrollConfigLoader configLoader) {
        this.calculationService = calculationService;
        this.configLoader = configLoader;
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calculate payslip using HRMS employee + tenant payroll config (idempotent per period)")
    public CalculatePayrollResponse calculate(@Valid @RequestBody CalculatePayrollRequest request) {
        return calculationService.calculate(request);
    }

    @PostMapping("/payslips/{id}/finalize")
    @Operation(summary = "Finalize (lock) a draft payslip")
    public CalculatePayrollResponse finalize(@PathVariable Long id) {
        return calculationService.finalize(id);
    }

    @PostMapping("/payslips/{id}/reopen")
    @Operation(summary = "Reopen a finalized payslip for recalculation")
    public CalculatePayrollResponse reopen(@PathVariable Long id) {
        return calculationService.reopen(id);
    }

    @GetMapping("/config")
    @Operation(summary = "View current tenant payroll config (platform defaults until customized)")
    public Map<String, Object> config() {
        var cfg = configLoader.loadOrDefault();
        return Map.of(
                "overtimeMultiplier", cfg.getOvertimeMultiplier(),
                "weekendMultiplier", cfg.getWeekendMultiplier(),
                "deductionRules", cfg.getDeductionRules(),
                "source", "tenant_db.payroll_config"
        );
    }
}
