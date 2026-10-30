package com.platform.payroll.application;

import com.platform.common.security.AuthContext;
import com.platform.payroll.api.dto.CalculatePayrollRequest;
import com.platform.payroll.api.dto.CalculatePayrollResponse;
import com.platform.payroll.domain.PayrollConfigEntity;
import com.platform.payroll.domain.Payslip;
import com.platform.payroll.domain.SalaryHistory;
import com.platform.payroll.extension.config.PayrollConfigLoader;
import com.platform.payroll.extension.engine.MonthlyPayrollEngine;
import com.platform.payroll.extension.engine.SalaryCalcEngine;
import com.platform.payroll.infrastructure.HrmsPayrollFactsClient;
import com.platform.payroll.infrastructure.grpc.HrmsEmployeeClient;
import com.platform.payroll.infrastructure.persistence.PayslipRepository;
import com.platform.payroll.infrastructure.persistence.SalaryHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orchestrates: HRMS payroll facts → config-driven monthly/hourly engine → payslip persistence.
 * FR-PAY-01..16, FR-SAL-01..03.
 * Config-driven: no tenant if-else; all knobs come from payroll_config.deductionRules type=CONFIG.
 */
@Service
public class PayrollCalculationService {

    private static final Logger log = LoggerFactory.getLogger(PayrollCalculationService.class);

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_FINALIZED = "FINALIZED";

    private final HrmsEmployeeClient hrmsEmployeeClient;
    private final HrmsPayrollFactsClient hrmsPayrollFactsClient;
    private final PayrollConfigLoader configLoader;
    private final MonthlyPayrollEngine monthlyPayrollEngine;
    private final SalaryCalcEngine salaryCalcEngine;
    private final PayslipRepository payslipRepository;
    private final SalaryHistoryRepository salaryHistoryRepository;
    public PayrollCalculationService(
            HrmsEmployeeClient hrmsEmployeeClient,
            HrmsPayrollFactsClient hrmsPayrollFactsClient,
            PayrollConfigLoader configLoader,
            MonthlyPayrollEngine monthlyPayrollEngine,
            SalaryCalcEngine salaryCalcEngine,
            PayslipRepository payslipRepository,
            SalaryHistoryRepository salaryHistoryRepository) {
        this.hrmsEmployeeClient = hrmsEmployeeClient;
        this.hrmsPayrollFactsClient = hrmsPayrollFactsClient;
        this.configLoader = configLoader;
        this.monthlyPayrollEngine = monthlyPayrollEngine;
        this.salaryCalcEngine = salaryCalcEngine;
        this.payslipRepository = payslipRepository;
        this.salaryHistoryRepository = salaryHistoryRepository;
    }

    // ---------------------- calculate ----------------------

    @Transactional
    public CalculatePayrollResponse calculate(CalculatePayrollRequest request) {
        HrmsEmployeeClient.EmployeeSnapshot employee = hrmsEmployeeClient.getEmployee(request.employeeId());
        // FR-PAY-01: inactive leavers still get paid for days employed in the period.
        // Skip only when HRMS facts say daysEmployed == 0 after fetch.

        Payslip existing = payslipRepository
                .findByEmployeeIdAndPeriodLabel(employee.id(), request.periodLabel())
                .orElse(null);
        if (existing != null && STATUS_FINALIZED.equalsIgnoreCase(statusOf(existing))) {
            throw new IllegalStateException(
                    "Payslip already FINALIZED for employee=" + employee.id()
                            + " period=" + request.periodLabel() + " — reopen before recalculating");
        }

        PayrollConfigEntity config = configLoader.loadOrDefault();

        // Fetch payroll facts from HRMS (working days, attendance, leave) — fail-closed.
        Map<String, Object> facts = new HashMap<>(hrmsPayrollFactsClient.fetchFacts(employee.id(), request.periodLabel()));

        // Explicit request overrides (tests / admin corrections) win over HRMS facts when provided.
        if (request.absenceDays() != null) {
            facts.put("absentDays", request.absenceDays());
        }
        if (request.leaveBalances() != null && !request.leaveBalances().isEmpty()) {
            facts.put("leaveBalances", request.leaveBalances());
        }
        if (request.regularHours() != null
                && Boolean.FALSE.equals(request.useHrmsAttendance())) {
            facts.put("hoursWorked", request.regularHours());
        }
        if (request.workingDaysInMonth() != null && request.workingDaysInMonth() > 0) {
            facts.put("workingDaysInMonth", request.workingDaysInMonth());
        }
        if (request.daysEmployed() != null && request.daysEmployed() > 0) {
            facts.put("daysEmployed", request.daysEmployed());
        }
        if (intOf(facts, "daysEmployed", 0) <= 0) {
            throw new IllegalStateException("Employee not employed in period: " + employee.id());
        }

        // Resolve effective salary: salary_history on/before period end wins; else HRMS employee fields.
        LocalDate periodEnd = YearMonth.parse(request.periodLabel().substring(0, 7)).atEndOfMonth();
        SalaryHistory effectiveSalary = resolveEffectiveSalary(employee.id(), periodEnd);

        String payType;
        String currency;
        BigDecimal baseSalary;
        BigDecimal hourlyRate;
        if (effectiveSalary != null) {
            payType = effectiveSalary.getPayType() == null ? "MONTHLY" : effectiveSalary.getPayType();
            currency = effectiveSalary.getCurrency() == null ? "BDT" : effectiveSalary.getCurrency();
            if ("HOURLY".equalsIgnoreCase(payType)) {
                hourlyRate = effectiveSalary.getAmount();
                baseSalary = BigDecimal.ZERO;
            } else {
                baseSalary = effectiveSalary.getAmount();
                hourlyRate = employee.hourlyRate();
            }
        } else {
            baseSalary = employee.baseSalary();
            hourlyRate = employee.hourlyRate();
            payType = String.valueOf(facts.getOrDefault("payType", "MONTHLY"));
            currency = String.valueOf(facts.getOrDefault("currency", "BDT"));
        }

        // FR-PAY-08: optional one-off adjustment for this period
        BigDecimal adjustment = request.adjustmentAmount();

        MonthlyPayrollEngine.PayrollResult result;
        if ("HOURLY".equalsIgnoreCase(payType)
                || (hourlyRate != null && hourlyRate.compareTo(BigDecimal.ZERO) > 0
                && (baseSalary == null || baseSalary.compareTo(BigDecimal.ZERO) == 0))) {
            result = monthlyPayrollEngine.calculateHourly(config, hourlyRate, facts, adjustment);
        } else {
            // Allow test/admin to force working-days divisor via facts when HRMS returns 0
            if (intOf(facts, "workingDaysInMonth", 0) <= 0) {
                facts.putIfAbsent("workingDaysInMonth", 22);
            }
            if (intOf(facts, "daysEmployed", 0) <= 0) {
                facts.putIfAbsent("daysEmployed", intOf(facts, "workingDaysInMonth", 22));
            }
            result = monthlyPayrollEngine.calculateMonthly(config, baseSalary, facts, adjustment);
        }

        // Build rich details (FR-PAY-15)
        Map<String, Object> details = new LinkedHashMap<>(result.details());
        details.put("employeeCode", employee.employeeCode());
        details.put("employeeName", employee.fullName());
        details.put("currency", currency);
        details.put("payType", payType);
        details.put("status", STATUS_DRAFT);
        details.put("effectiveSalarySource",
                effectiveSalary != null ? "salary_history:" + effectiveSalary.getId() : "employee_base");
        details.put("factsSource", "hrms_payroll_facts");
        if (adjustment != null && adjustment.compareTo(BigDecimal.ZERO) != 0) {
            details.put("adjustmentAmount", adjustment);
        }
        if (request.adjustmentNote() != null && !request.adjustmentNote().isBlank()) {
            details.put("adjustmentNote", request.adjustmentNote());
        }

        Payslip payslip = existing != null ? existing : new Payslip();
        payslip.setEmployeeId(employee.id());
        payslip.setPeriodLabel(request.periodLabel());
        payslip.setRegularHours(bd(facts, "hoursWorked", BigDecimal.ZERO));
        payslip.setOvertimeHours(
                request.overtimeHours() == null ? BigDecimal.ZERO : request.overtimeHours());
        payslip.setWeekendHours(
                request.weekendHours() == null ? BigDecimal.ZERO : request.weekendHours());
        payslip.setGrossAmount(result.basePay());
        payslip.setDeductionAmount(result.deduction());
        payslip.setNetAmount(result.net());
        payslip.setDetailsJson(details);
        if (existing == null) {
            payslip.setCreatedAt(Instant.now());
        }
        Payslip saved = payslipRepository.save(payslip);
        return toResponse(saved, employee.employeeCode(), null, null);
    }

    // ---------------------- calculateMonth ----------------------

    /**
     * Calculate payslips for ALL active employees in the given month.
     * Creates/updates DRAFT payslips. Warns about previous unfinalized.
     */
    @Transactional
    public Map<String, Object> calculateMonth(String periodLabel) {
        YearMonth ym = YearMonth.parse(periodLabel.substring(0, 7));
        PayrollConfigEntity config = configLoader.loadOrDefault();

        // FR-PAY-01 — every active employee employed any part of the month (HRMS list). Fail-closed.
        List<Long> employeeIds = new ArrayList<>(hrmsPayrollFactsClient.listActiveEmployeeIds());

        int created = 0, updated = 0, errors = 0, skipped = 0;
        List<String> errorList = new ArrayList<>();

        for (Long empId : employeeIds) {
            try {
                boolean existed = payslipRepository.findByEmployeeIdAndPeriodLabel(empId, periodLabel).isPresent();
                CalculatePayrollRequest req = new CalculatePayrollRequest(
                        empId, periodLabel, null, null, null, null, null, true);
                calculate(req);
                if (existed) {
                    updated++;
                } else {
                    created++;
                }
            } catch (IllegalStateException e) {
                // Inactive / missing salary / HRMS facts failure — count as error when facts fail
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (msg.contains("inactive") || msg.contains("Inactive") || msg.contains("not employed")) {
                    skipped++;
                    log.debug("calculateMonth skip emp={} period={}: {}", empId, periodLabel, msg);
                } else {
                    errors++;
                    errorList.add("emp=" + empId + ": " + msg);
                    log.warn("calculateMonth error emp={} period={}: {}", empId, periodLabel, msg);
                }
            } catch (Exception e) {
                errors++;
                errorList.add("emp=" + empId + ": " + e.getMessage());
                log.warn("calculateMonth error emp={} period={}: {}", empId, periodLabel, e.getMessage());
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", periodLabel);
        out.put("processed", created + updated);
        out.put("created", created);
        out.put("updated", updated);
        out.put("skipped", skipped);
        out.put("errors", errors);
        out.put("errorDetails", errorList);
        out.put("previousMonthWarning", warnings(periodLabel));
        return out;
    }

    // ---------------------- finalize ----------------------

    @Transactional
    public CalculatePayrollResponse finalize(Long payslipId, boolean confirm) {
        if (!confirm) {
            throw new IllegalArgumentException("Pass confirm=true to finalize a payslip");
        }
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new IllegalArgumentException("Payslip not found: " + payslipId));
        if (STATUS_FINALIZED.equalsIgnoreCase(statusOf(payslip))) {
            return toResponse(payslip, null, null, null);
        }

        // FR-PAY-11: month must be over (today > monthEnd)
        String period = payslip.getPeriodLabel();
        if (period != null && period.length() >= 7) {
            YearMonth ym = YearMonth.parse(period.substring(0, 7));
            LocalDate monthEnd = ym.atEndOfMonth();
            if (!LocalDate.now().isAfter(monthEnd)) {
                throw new IllegalStateException(
                        "Cannot finalize: month " + period + " has not ended yet (today="
                                + LocalDate.now() + ", monthEnd=" + monthEnd + ")");
            }
        }

        Map<String, Object> details = new HashMap<>(payslip.getDetailsJson() == null ? Map.of() : payslip.getDetailsJson());

        // Apply leave cover in HRMS (best-effort)
        Map<String, BigDecimal> consumedByType = extractConsumedByType(details);
        if (!consumedByType.isEmpty()) {
            hrmsPayrollFactsClient.applyLeaveCover(payslip.getEmployeeId(), period, consumedByType, false);
            details.put("leaveCoverApplied", true);
        }

        details.put("status", STATUS_FINALIZED);
        details.put("finalizedAt", Instant.now().toString());
        details.put("finalizedBy", AuthContext.getEmployeeId());
        payslip.setDetailsJson(details);

        // TODO: notify notification-service (best-effort HTTP call)
        return toResponse(payslipRepository.save(payslip), null, null, null);
    }

    // ---------------------- reopen ----------------------

    @Transactional
    public CalculatePayrollResponse reopen(Long payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new IllegalArgumentException("Payslip not found: " + payslipId));
        Map<String, Object> details = new HashMap<>(payslip.getDetailsJson() == null ? Map.of() : payslip.getDetailsJson());

        // Reverse leave cover if it was applied
        boolean coverApplied = Boolean.TRUE.equals(details.get("leaveCoverApplied"));
        if (coverApplied) {
            Map<String, BigDecimal> consumedByType = extractConsumedByType(details);
            if (!consumedByType.isEmpty()) {
                hrmsPayrollFactsClient.applyLeaveCover(payslip.getEmployeeId(),
                        payslip.getPeriodLabel(), consumedByType, true);
            }
            details.remove("leaveCoverApplied");
        }

        details.put("status", STATUS_DRAFT);
        details.remove("finalizedAt");
        details.remove("finalizedBy");
        details.put("reopenedAt", Instant.now().toString());
        details.put("reopenedBy", AuthContext.getEmployeeId());
        payslip.setDetailsJson(details);
        return toResponse(payslipRepository.save(payslip), null, null, null);
    }

    // ---------------------- warnings ----------------------

    /**
     * FR-PAY-12: warn when previous month has any DRAFT or missing finals.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> warnings(String periodLabel) {
        YearMonth current = YearMonth.parse(periodLabel.substring(0, 7));
        YearMonth prev = current.minusMonths(1);
        String prevPeriod = prev.toString();

        List<Payslip> prevSlips = payslipRepository.findAll().stream()
                .filter(p -> prevPeriod.equals(p.getPeriodLabel()))
                .toList();

        List<Map<String, Object>> unfinalized = new ArrayList<>();
        for (Payslip p : prevSlips) {
            if (!STATUS_FINALIZED.equalsIgnoreCase(statusOf(p))) {
                unfinalized.add(Map.of(
                        "payslipId", p.getId(),
                        "employeeId", p.getEmployeeId(),
                        "status", statusOf(p)));
            }
        }

        return Map.of(
                "currentPeriod", periodLabel,
                "previousPeriod", prevPeriod,
                "unfinalizedCount", unfinalized.size(),
                "unfinalized", unfinalized
        );
    }

    // ---------------------- export CSV ----------------------

    /**
     * FR-PAY-15: CSV export of finalized (or all) payslips for a period.
     */
    @Transactional(readOnly = true)
    public String exportCsv(String periodLabel, boolean finalizedOnly) {
        List<Payslip> slips = payslipRepository.findAll().stream()
                .filter(p -> periodLabel.substring(0, 7).equals(
                        p.getPeriodLabel() == null ? null : p.getPeriodLabel().substring(0, 7)))
                .filter(p -> !finalizedOnly || STATUS_FINALIZED.equalsIgnoreCase(statusOf(p)))
                .toList();

        StringWriter sw = new StringWriter();
        sw.write("employeeId,employeeCode,periodLabel,payType,currency,grossAmount,deductionAmount,netAmount,status\n");
        for (Payslip p : slips) {
            Map<String, Object> d = p.getDetailsJson() == null ? Map.of() : p.getDetailsJson();
            sw.write(String.join(",",
                    str(p.getEmployeeId()),
                    csvEscape(str(d.get("employeeCode"))),
                    csvEscape(p.getPeriodLabel()),
                    csvEscape(str(d.get("payType"))),
                    csvEscape(str(d.get("currency"))),
                    str(p.getGrossAmount()),
                    str(p.getDeductionAmount()),
                    str(p.getNetAmount()),
                    csvEscape(statusOf(p))
            ) + "\n");
        }
        return sw.toString();
    }

    // ---------------------- me/current ----------------------

    /**
     * FR-SAL-03: MTD estimate and finalized payslips for the calling employee.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> meEstimate() {
        Long empId = AuthContext.getEmployeeId();
        if (empId == null) throw new IllegalStateException("Missing employee identity");

        String currentPeriod = YearMonth.now().toString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employeeId", empId);
        result.put("currentPeriod", currentPeriod);

        // MTD estimate from draft payslip if exists
        payslipRepository.findByEmployeeIdAndPeriodLabel(empId, currentPeriod)
                .ifPresent(p -> result.put("mtdEstimate", Map.of(
                        "grossAmount", p.getGrossAmount(),
                        "deductionAmount", p.getDeductionAmount(),
                        "netAmount", p.getNetAmount(),
                        "status", statusOf(p))));

        // Historical finalized payslips
        List<Map<String, Object>> history = payslipRepository
                .findByEmployeeIdOrderByCreatedAtDesc(empId).stream()
                .filter(p -> STATUS_FINALIZED.equalsIgnoreCase(statusOf(p)))
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("periodLabel", p.getPeriodLabel());
                    m.put("netAmount", p.getNetAmount());
                    m.put("grossAmount", p.getGrossAmount());
                    m.put("deductionAmount", p.getDeductionAmount());
                    return m;
                }).toList();
        result.put("payslips", history);
        return result;
    }

    // ---------------------- private helpers ----------------------

    private SalaryHistory resolveEffectiveSalary(long employeeId, LocalDate onOrBefore) {
        return salaryHistoryRepository.findByEmployeeIdOrderByEffectiveDateDesc(employeeId)
                .stream()
                .filter(h -> h.getEffectiveDate() != null && !h.getEffectiveDate().isAfter(onOrBefore))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, BigDecimal> extractConsumedByType(Map<String, Object> details) {
        Object raw = details.get("consumedByType");
        if (!(raw instanceof Map<?, ?> m)) return Map.of();
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        m.forEach((k, v) -> {
            if (k != null && v != null) {
                try {
                    BigDecimal bd = v instanceof BigDecimal b ? b
                            : v instanceof Number n ? BigDecimal.valueOf(n.doubleValue())
                            : new BigDecimal(String.valueOf(v));
                    out.put(String.valueOf(k).toUpperCase(Locale.ROOT), bd);
                } catch (Exception ignored) { /* skip */ }
            }
        });
        return out;
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

    private static BigDecimal bd(Map<String, Object> m, String key, BigDecimal def) {
        Object v = m == null ? null : m.get(key);
        if (v == null) return def;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    private static int intOf(Map<String, Object> m, String key, int def) {
        Object v = m == null ? null : m.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v == null) return def;
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

}
