package com.platform.payroll.application;

import com.platform.payroll.api.dto.CalculatePayrollRequest;
import com.platform.payroll.api.dto.CalculatePayrollResponse;
import com.platform.payroll.domain.PayrollConfigEntity;
import com.platform.payroll.domain.Payslip;
import com.platform.payroll.extension.config.PayrollConfigLoader;
import com.platform.payroll.extension.engine.DeductionRuleEngine;
import com.platform.payroll.extension.engine.SalaryCalcEngine;
import com.platform.payroll.infrastructure.grpc.HrmsEmployeeClient;
import com.platform.payroll.infrastructure.persistence.PayslipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Orchestrates: HRMS employee fetch → config-driven salary/deduction → save payslip.
 * Idempotent per (employeeId, periodLabel); finalize locks; reopen unlocks draft edits.
 */
@Service
public class PayrollCalculationService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_FINALIZED = "FINALIZED";

    private final HrmsEmployeeClient hrmsEmployeeClient;
    private final PayrollConfigLoader configLoader;
    private final SalaryCalcEngine salaryCalcEngine;
    private final DeductionRuleEngine deductionRuleEngine;
    private final PayslipRepository payslipRepository;

    public PayrollCalculationService(
            HrmsEmployeeClient hrmsEmployeeClient,
            PayrollConfigLoader configLoader,
            SalaryCalcEngine salaryCalcEngine,
            DeductionRuleEngine deductionRuleEngine,
            PayslipRepository payslipRepository) {
        this.hrmsEmployeeClient = hrmsEmployeeClient;
        this.configLoader = configLoader;
        this.salaryCalcEngine = salaryCalcEngine;
        this.deductionRuleEngine = deductionRuleEngine;
        this.payslipRepository = payslipRepository;
    }

    @Transactional
    public CalculatePayrollResponse calculate(CalculatePayrollRequest request) {
        HrmsEmployeeClient.EmployeeSnapshot employee = hrmsEmployeeClient.getEmployee(request.employeeId());
        if (!employee.active()) {
            throw new IllegalStateException("Employee is inactive: " + employee.id());
        }

        Payslip existing = payslipRepository
                .findByEmployeeIdAndPeriodLabel(employee.id(), request.periodLabel())
                .orElse(null);
        if (existing != null && STATUS_FINALIZED.equalsIgnoreCase(statusOf(existing))) {
            throw new IllegalStateException(
                    "Payslip already FINALIZED for employee=" + employee.id()
                            + " period=" + request.periodLabel() + " — reopen before recalculating");
        }

        PayrollConfigEntity config = configLoader.loadOrDefault();
        SalaryCalcEngine.SalaryResult salary = salaryCalcEngine.calculate(
                config,
                employee.hourlyRate(),
                request.regularHours(),
                request.overtimeHours(),
                request.weekendHours()
        );
        DeductionRuleEngine.DeductionResult deductions = deductionRuleEngine.apply(
                config, salary.gross(), request.absenceDays(), request.leaveBalances());
        var net = salary.gross().subtract(deductions.total());

        Map<String, Object> details = new HashMap<>(salary.details());
        details.put("deductions", deductions.applied());
        details.put("employeeCode", employee.employeeCode());
        details.put("employeeName", employee.fullName());
        details.put("absenceCoverOrder", deductionRuleEngine.absenceCoverOrder(config));
        details.put("status", STATUS_DRAFT);

        Payslip payslip = existing != null ? existing : new Payslip();
        payslip.setEmployeeId(employee.id());
        payslip.setPeriodLabel(request.periodLabel());
        payslip.setRegularHours(request.regularHours());
        payslip.setOvertimeHours(request.overtimeHours());
        payslip.setWeekendHours(request.weekendHours());
        payslip.setGrossAmount(salary.gross());
        payslip.setDeductionAmount(deductions.total());
        payslip.setNetAmount(net);
        payslip.setDetailsJson(details);
        if (existing == null) {
            payslip.setCreatedAt(Instant.now());
        }
        Payslip saved = payslipRepository.save(payslip);

        return toResponse(saved, employee.employeeCode(), salary.overtimeMultiplier(), salary.weekendMultiplier());
    }

    @Transactional
    public CalculatePayrollResponse finalize(Long payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new IllegalArgumentException("Payslip not found: " + payslipId));
        if (STATUS_FINALIZED.equalsIgnoreCase(statusOf(payslip))) {
            return toResponse(payslip, null, null, null);
        }
        Map<String, Object> details = new HashMap<>(payslip.getDetailsJson() == null ? Map.of() : payslip.getDetailsJson());
        details.put("status", STATUS_FINALIZED);
        details.put("finalizedAt", Instant.now().toString());
        payslip.setDetailsJson(details);
        return toResponse(payslipRepository.save(payslip), null, null, null);
    }

    @Transactional
    public CalculatePayrollResponse reopen(Long payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new IllegalArgumentException("Payslip not found: " + payslipId));
        Map<String, Object> details = new HashMap<>(payslip.getDetailsJson() == null ? Map.of() : payslip.getDetailsJson());
        details.put("status", STATUS_DRAFT);
        details.put("reopenedAt", Instant.now().toString());
        payslip.setDetailsJson(details);
        return toResponse(payslipRepository.save(payslip), null, null, null);
    }

    private static String statusOf(Payslip p) {
        if (p.getDetailsJson() == null) {
            return STATUS_DRAFT;
        }
        Object s = p.getDetailsJson().get("status");
        return s == null ? STATUS_DRAFT : String.valueOf(s);
    }

    private CalculatePayrollResponse toResponse(Payslip saved,
                                                String employeeCode,
                                                java.math.BigDecimal ot,
                                                java.math.BigDecimal we) {
        Map<String, Object> details = saved.getDetailsJson() == null ? Map.of() : saved.getDetailsJson();
        String code = employeeCode != null ? employeeCode : String.valueOf(details.getOrDefault("employeeCode", ""));
        @SuppressWarnings("unchecked")
        var applied = details.get("deductions") instanceof java.util.List<?> list
                ? (java.util.List<Map<String, Object>>) list
                : java.util.List.<Map<String, Object>>of();
        return new CalculatePayrollResponse(
                saved.getId(),
                saved.getEmployeeId(),
                code,
                saved.getPeriodLabel(),
                ot != null ? ot : java.math.BigDecimal.ZERO,
                we != null ? we : java.math.BigDecimal.ZERO,
                saved.getGrossAmount(),
                saved.getDeductionAmount(),
                saved.getNetAmount(),
                applied,
                details
        );
    }
}
