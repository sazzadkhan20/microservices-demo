package com.platform.payroll.application;

import com.platform.payroll.api.dto.CalculatePayrollRequest;
import com.platform.payroll.api.dto.CalculatePayrollResponse;
import com.platform.payroll.domain.PayrollConfigEntity;
import com.platform.payroll.domain.Payslip;
import com.platform.payroll.extension.config.PayrollConfigLoader;
import com.platform.payroll.extension.engine.DeductionRuleEngine;
import com.platform.payroll.extension.engine.SalaryCalcEngine;
import com.platform.payroll.infrastructure.HrmsAttendanceClient;
import com.platform.payroll.infrastructure.grpc.HrmsEmployeeClient;
import com.platform.payroll.infrastructure.persistence.PayslipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates: HRMS employee + attendance facts → config-driven salary/deduction → save payslip.
 * Idempotent per (employeeId, periodLabel); finalize locks; reopen unlocks draft edits.
 */
@Service
public class PayrollCalculationService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_FINALIZED = "FINALIZED";

    private final HrmsEmployeeClient hrmsEmployeeClient;
    private final HrmsAttendanceClient hrmsAttendanceClient;
    private final PayrollConfigLoader configLoader;
    private final SalaryCalcEngine salaryCalcEngine;
    private final DeductionRuleEngine deductionRuleEngine;
    private final PayslipRepository payslipRepository;

    public PayrollCalculationService(
            HrmsEmployeeClient hrmsEmployeeClient,
            HrmsAttendanceClient hrmsAttendanceClient,
            PayrollConfigLoader configLoader,
            SalaryCalcEngine salaryCalcEngine,
            DeductionRuleEngine deductionRuleEngine,
            PayslipRepository payslipRepository) {
        this.hrmsEmployeeClient = hrmsEmployeeClient;
        this.hrmsAttendanceClient = hrmsAttendanceClient;
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

        BigDecimal regular = request.regularHours() == null ? BigDecimal.ZERO : request.regularHours();
        BigDecimal absence = request.absenceDays() == null ? BigDecimal.ZERO : request.absenceDays();
        boolean usedHrms = false;
        if (Boolean.TRUE.equals(request.useHrmsAttendance())) {
            HrmsAttendanceClient.AttendanceFacts facts =
                    hrmsAttendanceClient.factsForPeriod(employee.id(), request.periodLabel());
            if (facts.rows() > 0) {
                // Prefer HRMS hours when attendance exists; allow explicit override only when useHrms=false
                regular = facts.regularHours();
                if (request.absenceDays() == null
                        || request.absenceDays().compareTo(BigDecimal.ZERO) == 0) {
                    absence = facts.absenceDays();
                }
                usedHrms = true;
            }
        }

        PayrollConfigEntity config = configLoader.loadOrDefault();
        SalaryCalcEngine.SalaryResult salary = salaryCalcEngine.calculate(
                config,
                employee.hourlyRate(),
                regular,
                request.overtimeHours(),
                request.weekendHours()
        );
        DeductionRuleEngine.DeductionResult deductions = deductionRuleEngine.apply(
                config, salary.gross(), absence, request.leaveBalances());
        var net = salary.gross().subtract(deductions.total());

        Map<String, Object> details = new HashMap<>(salary.details());
        details.put("deductions", deductions.applied());
        details.put("employeeCode", employee.employeeCode());
        details.put("employeeName", employee.fullName());
        details.put("absenceCoverOrder", deductionRuleEngine.absenceCoverOrder(config));
        details.put("status", STATUS_DRAFT);
        details.put("hoursSource", usedHrms ? "HRMS_ATTENDANCE" : "CLIENT");
        details.put("absenceDays", absence);

        Payslip payslip = existing != null ? existing : new Payslip();
        payslip.setEmployeeId(employee.id());
        payslip.setPeriodLabel(request.periodLabel());
        payslip.setRegularHours(regular);
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
        details.remove("finalizedAt");
        details.put("reopenedAt", Instant.now().toString());
        payslip.setDetailsJson(details);
        return toResponse(payslipRepository.save(payslip), null, null, null);
    }

    private static String statusOf(Payslip p) {
        if (p.getDetailsJson() == null) return STATUS_DRAFT;
        Object s = p.getDetailsJson().get("status");
        return s == null ? STATUS_DRAFT : String.valueOf(s);
    }

    private CalculatePayrollResponse toResponse(
            Payslip p, String employeeCode, BigDecimal otMult, BigDecimal weMult) {
        Map<String, Object> details = p.getDetailsJson() == null ? Map.of() : p.getDetailsJson();
        if (employeeCode == null) {
            Object c = details.get("employeeCode");
            employeeCode = c == null ? null : String.valueOf(c);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deductions = details.get("deductions") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
        return new CalculatePayrollResponse(
                p.getId(),
                p.getEmployeeId(),
                employeeCode,
                p.getPeriodLabel(),
                otMult,
                weMult,
                p.getGrossAmount(),
                p.getDeductionAmount(),
                p.getNetAmount(),
                deductions,
                details
        );
    }
}
