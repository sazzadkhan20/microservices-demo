package com.platform.payroll.application;

import com.platform.common.security.AuthContext;
import com.platform.payroll.api.dto.CalculatePayrollRequest;
import com.platform.payroll.api.dto.CalculatePayrollResponse;
import com.platform.payroll.domain.PayrollConfigEntity;
import com.platform.payroll.domain.Payslip;
import com.platform.payroll.domain.SalaryHistory;
import com.platform.payroll.extension.config.PayrollConfigLoader;
import com.platform.payroll.extension.engine.DeductionRuleEngine;
import com.platform.payroll.extension.engine.MonthlyPayrollEngine;
import com.platform.payroll.extension.engine.PayrollPeriodPolicy;
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
    private final DeductionRuleEngine deductionRuleEngine;
    private final SalaryCalcEngine salaryCalcEngine;
    private final PayslipRepository payslipRepository;
    private final SalaryHistoryRepository salaryHistoryRepository;
    private final PayrollNotificationHook notificationHook;

    public PayrollCalculationService(
            HrmsEmployeeClient hrmsEmployeeClient,
            HrmsPayrollFactsClient hrmsPayrollFactsClient,
            PayrollConfigLoader configLoader,
            MonthlyPayrollEngine monthlyPayrollEngine,
            DeductionRuleEngine deductionRuleEngine,
            SalaryCalcEngine salaryCalcEngine,
            PayslipRepository payslipRepository,
            SalaryHistoryRepository salaryHistoryRepository,
            PayrollNotificationHook notificationHook) {
        this.hrmsEmployeeClient = hrmsEmployeeClient;
        this.hrmsPayrollFactsClient = hrmsPayrollFactsClient;
        this.configLoader = configLoader;
        this.monthlyPayrollEngine = monthlyPayrollEngine;
        this.deductionRuleEngine = deductionRuleEngine;
        this.salaryCalcEngine = salaryCalcEngine;
        this.payslipRepository = payslipRepository;
        this.salaryHistoryRepository = salaryHistoryRepository;
        this.notificationHook = notificationHook;
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
        if (existing != null) {
            String st = statusOf(existing);
            if (STATUS_FINALIZED.equalsIgnoreCase(st)) {
                throw new IllegalStateException(
                        "Payslip already FINALIZED for employee=" + employee.id()
                                + " period=" + request.periodLabel() + " — reopen before recalculating");
            }
            // PAY-04 — do not clobber in-flight finalize/reopen
            if ("FINALIZING".equalsIgnoreCase(st)
                    || "REOPENING".equalsIgnoreCase(st)
                    || "ACTION_REQUIRED".equalsIgnoreCase(st)) {
                throw new IllegalStateException(
                        "Payslip status " + st + " blocks recalculation for employee="
                                + employee.id() + " period=" + request.periodLabel());
            }
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

        // Build rich details (FR-PAY-15). PAY-03 — preserve prior finals across recalculation.
        Map<String, Object> details = new LinkedHashMap<>(result.details());
        if (existing != null && existing.getDetailsJson() != null) {
            Object prior = existing.getDetailsJson().get("priorFinals");
            if (prior != null) {
                details.put("priorFinals", prior);
            }
            Object retained = existing.getDetailsJson().get("retainedFinal");
            if (retained != null) {
                details.put("retainedFinal", retained);
            }
        }
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
            details.put("adjustment", adjustment);
        }
        if (request.adjustmentNote() != null && !request.adjustmentNote().isBlank()) {
            details.put("adjustmentNote", request.adjustmentNote());
        }

        // Tenant FIXED / PERCENT_GROSS pack rules (union dues, etc.) — config-driven, all businesses.
        DeductionRuleEngine.DeductionResult moneyRules =
                deductionRuleEngine.applyConfiguredMoneyRules(config, result.basePay());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deductionLines = details.get("deductions") instanceof List<?> raw
                ? new ArrayList<>((List<Map<String, Object>>) raw)
                : new ArrayList<>();
        deductionLines.addAll(moneyRules.applied());
        BigDecimal totalDeduction = result.deduction().add(moneyRules.total()).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal net = result.basePay().subtract(totalDeduction)
                .add(adjustment == null ? BigDecimal.ZERO : adjustment)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        details.put("deductions", deductionLines);
        details.put("configuredRuleDeduction", moneyRules.total());
        details.put("deduction", totalDeduction);
        details.put("net", net);

        PayrollPeriodPolicy periodPolicy = PayrollPeriodPolicy.from(config);
        details.put("periodPolicy", periodPolicy.toMap());
        details.put("periodCloseDate", periodPolicy.periodCloseDate(request.periodLabel()).toString());
        details.put("canFinalize", periodPolicy.canFinalize(request.periodLabel(), LocalDate.now()));

        Payslip payslip = existing != null ? existing : new Payslip();
        payslip.setEmployeeId(employee.id());
        payslip.setPeriodLabel(request.periodLabel());
        payslip.setRegularHours(bd(facts, "hoursWorked", BigDecimal.ZERO));
        payslip.setOvertimeHours(
                request.overtimeHours() == null ? BigDecimal.ZERO : request.overtimeHours());
        payslip.setWeekendHours(
                request.weekendHours() == null ? BigDecimal.ZERO : request.weekendHours());
        payslip.setGrossAmount(result.basePay());
        payslip.setDeductionAmount(totalDeduction);
        payslip.setNetAmount(net);
        details.put("status", STATUS_DRAFT);
        payslip.setDetailsJson(details);
        payslip.setStatus(STATUS_DRAFT);
        payslip.setFactsSnapshot(facts);
        if (existing == null) {
            payslip.setCreatedAt(Instant.now());
            if (payslip.getRevision() <= 0) {
                payslip.setRevision(1L);
            }
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

    // ---------------------- finalize (legacy sync; unused from API — PAY-09) ----------------------

    @Transactional
    public CalculatePayrollResponse finalizeLegacySync(Long payslipId, boolean confirm) {
        if (!confirm) {
            throw new IllegalArgumentException("Pass confirm=true to finalize a payslip");
        }
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new IllegalArgumentException("Payslip not found: " + payslipId));
        if (STATUS_FINALIZED.equalsIgnoreCase(statusOf(payslip))) {
            return toResponse(payslip, null, null, null);
        }

        // FR-PAY-11: finalize eligibility is tenant config (MONTH_END vs DAY_OF_MONTH 20/25/…)
        String period = payslip.getPeriodLabel();
        PayrollConfigEntity config = configLoader.loadOrDefault();
        PayrollPeriodPolicy periodPolicy = PayrollPeriodPolicy.from(config);
        LocalDate today = LocalDate.now();
        if (period != null && period.length() >= 7 && !periodPolicy.canFinalize(period, today)) {
            throw new IllegalStateException(periodPolicy.finalizeBlockedReason(period, today));
        }

        Map<String, Object> details = new HashMap<>(payslip.getDetailsJson() == null ? Map.of() : payslip.getDetailsJson());

        Map<String, BigDecimal> consumedByType = extractConsumedByType(details);
        if (!consumedByType.isEmpty()) {
            hrmsPayrollFactsClient.applyLeaveCover(payslip.getEmployeeId(), period, consumedByType, false);
            details.put("leaveCoverApplied", true);
        }

        details.put("status", STATUS_FINALIZED);
        details.put("finalizedAt", Instant.now().toString());
        details.put("finalizedBy", AuthContext.getEmployeeId());
        payslip.setDetailsJson(details);
        payslip.setStatus(STATUS_FINALIZED);

        Payslip saved = payslipRepository.save(payslip);

        String currency = details.get("currency") == null ? null : String.valueOf(details.get("currency"));
        notificationHook.notifyPayslipFinalized(
                config,
                saved.getEmployeeId(),
                saved.getId(),
                saved.getPeriodLabel(),
                saved.getNetAmount(),
                currency);
        details.put("notificationSentToEmployeeId", saved.getEmployeeId());
        saved.setDetailsJson(details);
        saved = payslipRepository.save(saved);

        return toResponse(saved, null, null, null);
    }

    // ---------------------- reopen (legacy sync fallback) ----------------------

    @Transactional
    public CalculatePayrollResponse reopenLegacySync(Long payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new IllegalArgumentException("Payslip not found: " + payslipId));
        Map<String, Object> details = new HashMap<>(payslip.getDetailsJson() == null ? Map.of() : payslip.getDetailsJson());

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
        payslip.setStatus(STATUS_DRAFT);
        return toResponse(payslipRepository.save(payslip), null, null, null);
    }

    public CalculatePayrollResponse toPublicResponse(Payslip p) {
        return toResponse(p, null, null, null);
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

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("currentPeriod", periodLabel);
        out.put("previousPeriod", prevPeriod);
        out.put("unfinalizedCount", unfinalized.size());
        out.put("unfinalized", unfinalized);
        if (!unfinalized.isEmpty()) {
            out.put("message", unfinalized.size() + " payslip(s) from " + prevPeriod
                    + " are still not finalized");
        }
        return out;
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
     * FR-SAL-03: MTD estimate + own payslips (DRAFT and FINALIZED) with full details.
     * Employees may open any of their own slips regardless of status.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> meEstimate() {
        Long empId = AuthContext.getEmployeeId();
        if (empId == null) throw new IllegalStateException("Missing employee identity");

        String currentPeriod = YearMonth.now().toString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employeeId", empId);
        result.put("currentPeriod", currentPeriod);

        // MTD estimate from current-period payslip (draft or finalized)
        payslipRepository.findByEmployeeIdAndPeriodLabel(empId, currentPeriod)
                .ifPresent(p -> {
                    Map<String, Object> mtd = new LinkedHashMap<>();
                    mtd.put("id", p.getId());
                    mtd.put("grossAmount", p.getGrossAmount());
                    mtd.put("deductionAmount", p.getDeductionAmount());
                    mtd.put("netAmount", p.getNetAmount());
                    mtd.put("status", statusOf(p));
                    mtd.put("details", p.getDetailsJson() == null ? Map.of() : p.getDetailsJson());
                    result.put("mtdEstimate", mtd);
                });

        // Own payslips — draft and finalized (never other employees)
        List<Map<String, Object>> history = payslipRepository
                .findByEmployeeIdOrderByCreatedAtDesc(empId).stream()
                .map(this::ownPayslipRow)
                .toList();
        result.put("payslips", history);
        return result;
    }

    /** Single own payslip by id (DRAFT or FINALIZED). */
    @Transactional(readOnly = true)
    public Map<String, Object> getOwnPayslip(Long payslipId) {
        Long empId = AuthContext.getEmployeeId();
        if (empId == null) throw new IllegalStateException("Missing employee identity");
        Payslip p = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new IllegalArgumentException("Payslip not found: " + payslipId));
        if (!empId.equals(p.getEmployeeId())) {
            throw new SecurityException("Forbidden: payslip " + payslipId + " is not yours");
        }
        return ownPayslipRow(p);
    }

    private Map<String, Object> ownPayslipRow(Payslip p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("employeeId", p.getEmployeeId());
        m.put("periodLabel", p.getPeriodLabel());
        m.put("grossAmount", p.getGrossAmount());
        m.put("deductionAmount", p.getDeductionAmount());
        m.put("netAmount", p.getNetAmount());
        m.put("regularHours", p.getRegularHours());
        m.put("overtimeHours", p.getOvertimeHours());
        m.put("weekendHours", p.getWeekendHours());
        m.put("status", statusOf(p));
        m.put("details", p.getDetailsJson() == null ? Map.of() : p.getDetailsJson());
        m.put("createdAt", p.getCreatedAt() == null ? null : p.getCreatedAt().toString());
        return m;
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
        if (p.getStatus() != null && !p.getStatus().isBlank()) {
            return p.getStatus();
        }
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
