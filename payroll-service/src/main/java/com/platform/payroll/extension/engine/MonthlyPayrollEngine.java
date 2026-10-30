package com.platform.payroll.extension.engine;

import com.platform.payroll.domain.PayrollConfigEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FR-PAY-01..16 — config-driven monthly (and hourly) payroll calculation.
 *
 * <p>Algorithm for MONTHLY employees:
 * <ol>
 *   <li>workingDays = facts.workingDaysInMonth (SCHEDULE mode) or config dayDivisor (FIXED mode)</li>
 *   <li>perDayRate = monthlyGross / workingDays  (4dp HALF_UP)</li>
 *   <li>basePay = perDayRate × daysEmployed</li>
 *   <li>BALANCE_FIRST: walk absenceCoverOrder consuming leave balances; DIRECT: uncovered = all absent</li>
 *   <li>unpaidLeaveDays always added to deductible pool (FR-PAY-05)</li>
 *   <li>deduction = (uncovered + unpaidLeaveDays) × perDayRate</li>
 *   <li>net = basePay − deduction + adjustmentAmount</li>
 * </ol>
 *
 * <p>Worked example (Arpa, September 2026):
 * baseSalary=15 000, workingDays=22, daysEmployed=22, absentDays=2.5, CASUAL balance=1, coverOrder=[CASUAL,ANNUAL]
 * → perDayRate=681.8182, covered=1 CASUAL, uncovered=1.5, deduction=1022.7273≈1022.73, net=13977.27
 */
@Component
public class MonthlyPayrollEngine {

    /** Result of a single payroll calculation. */
    public record PayrollResult(
            BigDecimal basePay,
            BigDecimal deduction,
            BigDecimal net,
            BigDecimal perDayRate,
            int workingDays,
            int daysEmployed,
            BigDecimal absentDays,
            BigDecimal uncoveredDays,
            BigDecimal unpaidLeaveDays,
            Map<String, BigDecimal> consumedByType,
            List<String> notes,
            Map<String, Object> details
    ) {}

    // ---------------------- public API ----------------------

    /**
     * Calculate payroll for a MONTHLY employee.
     *
     * @param config          tenant payroll config
     * @param monthlyGross    effective gross salary for the period (from salary history or employee)
     * @param facts           payroll facts from HRMS (see HrmsPayrollFactsClient)
     * @param adjustmentAmount optional one-off adjustment (FR-PAY-08)
     */
    public PayrollResult calculateMonthly(
            PayrollConfigEntity config,
            BigDecimal monthlyGross,
            Map<String, Object> facts,
            BigDecimal adjustmentAmount) {

        int workingDays = resolveWorkingDays(config, facts);
        int daysEmployed = intVal(facts, "daysEmployed", workingDays);
        BigDecimal absentDays = bd(facts, "absentDays", BigDecimal.ZERO);
        BigDecimal unpaidLeaveDays = bd(facts, "unpaidLeaveDays", BigDecimal.ZERO);

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> balances = facts.get("leaveBalances") instanceof Map<?, ?> m
                ? toStringBd((Map<Object, Object>) m) : Map.of();

        if (workingDays <= 0) {
            workingDays = 22; // safety fallback
        }

        BigDecimal gross = monthlyGross == null ? BigDecimal.ZERO : monthlyGross;
        // Intermediate rate at 4dp; money amounts stored/returned at 2dp (DR-02).
        BigDecimal perDayRate = gross.divide(BigDecimal.valueOf(workingDays), 4, RoundingMode.HALF_UP);
        BigDecimal basePay = perDayRate.multiply(BigDecimal.valueOf(daysEmployed))
                .setScale(2, RoundingMode.HALF_UP);

        // Absence resolution
        String absenceRule = configStr(config, "absenceRule", "BALANCE_FIRST");
        Map<String, BigDecimal> consumedByType = new LinkedHashMap<>();
        BigDecimal uncoveredDays;
        List<Map<String, Object>> coverSteps = new ArrayList<>();

        if ("DIRECT".equalsIgnoreCase(absenceRule)) {
            uncoveredDays = absentDays.max(BigDecimal.ZERO);
        } else {
            // BALANCE_FIRST: walk cover order
            List<String> coverOrder = absenceCoverOrder(config);
            BigDecimal remaining = absentDays.max(BigDecimal.ZERO);
            Map<String, BigDecimal> mutableBalances = new HashMap<>(balances);
            for (String typeCode : coverOrder) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal avail = mutableBalances.getOrDefault(typeCode, BigDecimal.ZERO);
                if (avail.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal use = avail.min(remaining);
                consumedByType.put(typeCode, use);
                remaining = remaining.subtract(use);
                coverSteps.add(Map.of(
                        "leaveType", typeCode,
                        "consumed", use,
                        "remainingAbsence", remaining));
            }
            uncoveredDays = remaining;
        }

        // FR-PAY-05: unpaid leave always deducted
        BigDecimal totalDeductDays = uncoveredDays.add(unpaidLeaveDays.max(BigDecimal.ZERO));
        BigDecimal deduction = totalDeductDays.multiply(perDayRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal adj = adjustmentAmount == null ? BigDecimal.ZERO : adjustmentAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = basePay.subtract(deduction).add(adj).setScale(2, RoundingMode.HALF_UP);

        // Notes
        List<String> notes = new ArrayList<>();
        if (daysEmployed < workingDays) {
            notes.add(String.format("Pro-rated: %d of %d working days employed", daysEmployed, workingDays));
        }
        if (uncoveredDays.compareTo(BigDecimal.ZERO) > 0 && !"DIRECT".equalsIgnoreCase(absenceRule)) {
            notes.add(String.format("%.2f absent day(s) beyond leave balance deducted as unpaid", uncoveredDays.doubleValue()));
        }
        long missingCheckouts = longVal(facts, "missingCheckoutCount", 0);
        if (missingCheckouts > 0) {
            notes.add(missingCheckouts + " open shift(s) with missing checkout — hours excluded");
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("payMode", "MONTHLY");
        details.put("monthlyGross", gross);
        details.put("workingDays", workingDays);
        details.put("daysEmployed", daysEmployed);
        details.put("perDayRate", perDayRate);
        details.put("basePay", basePay);
        details.put("absentDays", absentDays);
        details.put("uncoveredAbsentDays", uncoveredDays);
        details.put("unpaidLeaveDays", unpaidLeaveDays);
        details.put("totalDeductDays", totalDeductDays);
        details.put("deduction", deduction);
        details.put("adjustmentAmount", adj);
        details.put("net", net);
        details.put("absenceRule", absenceRule);
        details.put("absenceCoverOrder", absenceCoverOrder(config));
        details.put("consumedByType", consumedByType);
        details.put("coverSteps", coverSteps);
        details.put("notes", notes);

        return new PayrollResult(basePay, deduction, net, perDayRate,
                workingDays, daysEmployed, absentDays, uncoveredDays,
                unpaidLeaveDays, consumedByType, notes, details);
    }

    /**
     * Calculate payroll for an HOURLY employee.
     * basePay = hoursWorked × hourlyRate
     * unpaidLeave deducted as: unpaidLeaveDays × hourlyRate × dailyHours (config default 8)
     */
    public PayrollResult calculateHourly(
            PayrollConfigEntity config,
            BigDecimal hourlyRate,
            Map<String, Object> facts,
            BigDecimal adjustmentAmount) {

        BigDecimal hoursWorked = bd(facts, "hoursWorked", BigDecimal.ZERO);
        BigDecimal unpaidLeaveDays = bd(facts, "unpaidLeaveDays", BigDecimal.ZERO);
        long missingCheckouts = longVal(facts, "missingCheckoutCount", 0);
        BigDecimal rate = hourlyRate == null ? BigDecimal.ZERO : hourlyRate;

        BigDecimal basePay = rate.multiply(hoursWorked).setScale(2, RoundingMode.HALF_UP);

        // Unpaid leave deduction: days × rate × dailyHours
        BigDecimal dailyHours = configBd(config, "dailyHours", new BigDecimal("8"));
        BigDecimal deduction = unpaidLeaveDays.multiply(rate).multiply(dailyHours)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal adj = adjustmentAmount == null ? BigDecimal.ZERO : adjustmentAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = basePay.subtract(deduction).add(adj).setScale(2, RoundingMode.HALF_UP);

        List<String> notes = new ArrayList<>();
        if (missingCheckouts > 0) {
            notes.add(missingCheckouts + " open shift(s) with missing checkout — those hours excluded");
        }
        if (unpaidLeaveDays.compareTo(BigDecimal.ZERO) > 0) {
            notes.add(String.format("%.2f unpaid leave day(s) deducted at rate×%.0fh",
                    unpaidLeaveDays.doubleValue(), dailyHours.doubleValue()));
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("payMode", "HOURLY");
        details.put("hourlyRate", rate);
        details.put("hoursWorked", hoursWorked);
        details.put("basePay", basePay);
        details.put("unpaidLeaveDays", unpaidLeaveDays);
        details.put("dailyHours", dailyHours);
        details.put("deduction", deduction);
        details.put("adjustmentAmount", adj);
        details.put("net", net);
        details.put("missingCheckoutCount", missingCheckouts);
        details.put("notes", notes);

        return new PayrollResult(basePay, deduction, net, rate,
                0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                unpaidLeaveDays, Map.of(), notes, details);
    }

    // ---------------------- config helpers ----------------------

    /**
     * Resolve working days divisor: SCHEDULE mode uses facts.workingDaysInMonth;
     * FIXED mode uses config dayDivisor (default 30).
     */
    public int resolveWorkingDays(PayrollConfigEntity config, Map<String, Object> facts) {
        String mode = configStr(config, "dayDivisorMode", "SCHEDULE");
        if ("FIXED".equalsIgnoreCase(mode)) {
            return configInt(config, "dayDivisor", 30);
        }
        // SCHEDULE: use actual working days from HRMS schedule
        int fromFacts = intVal(facts, "workingDaysInMonth", 0);
        return fromFacts > 0 ? fromFacts : configInt(config, "dayDivisor", 22);
    }

    public List<String> absenceCoverOrder(PayrollConfigEntity config) {
        for (Map<String, Object> rule : config.getDeductionRules()) {
            if (!"CONFIG".equalsIgnoreCase(String.valueOf(rule.getOrDefault("type", "")))) continue;
            if (!"absenceCoverOrder".equals(String.valueOf(rule.get("key")))) continue;
            Object value = rule.get("value");
            if (value instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    if (o != null) out.add(String.valueOf(o).trim().toUpperCase(Locale.ROOT));
                }
                if (!out.isEmpty()) return out;
            }
        }
        return List.of("CASUAL", "ANNUAL");
    }

    private String configStr(PayrollConfigEntity config, String key, String def) {
        if (config == null || config.getDeductionRules() == null) return def;
        for (Map<String, Object> rule : config.getDeductionRules()) {
            if (!"CONFIG".equalsIgnoreCase(String.valueOf(rule.getOrDefault("type", "")))) continue;
            if (key.equals(String.valueOf(rule.get("key")))) {
                Object v = rule.get("value");
                if (v != null) return String.valueOf(v).trim();
            }
        }
        return def;
    }

    private int configInt(PayrollConfigEntity config, String key, int def) {
        String v = configStr(config, key, null);
        if (v == null) return def;
        try { return Integer.parseInt(v); } catch (Exception e) { return def; }
    }

    private BigDecimal configBd(PayrollConfigEntity config, String key, BigDecimal def) {
        String v = configStr(config, key, null);
        if (v == null) return def;
        try { return new BigDecimal(v); } catch (Exception e) { return def; }
    }

    // ---------------------- facts helpers ----------------------

    private static BigDecimal bd(Map<String, Object> m, String key, BigDecimal def) {
        Object v = m == null ? null : m.get(key);
        if (v == null) return def;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    private static int intVal(Map<String, Object> m, String key, int def) {
        Object v = m == null ? null : m.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { /* */ }
        return def;
    }

    private static long longVal(Map<String, Object> m, String key, long def) {
        Object v = m == null ? null : m.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v != null) try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { /* */ }
        return def;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, BigDecimal> toStringBd(Map<Object, Object> raw) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if (raw == null) return out;
        raw.forEach((k, v) -> {
            if (k != null && v != null) {
                try {
                    BigDecimal val = v instanceof BigDecimal b ? b
                            : v instanceof Number n ? BigDecimal.valueOf(n.doubleValue())
                            : new BigDecimal(String.valueOf(v));
                    out.put(String.valueOf(k).toUpperCase(Locale.ROOT), val);
                } catch (Exception ignored) { /* skip */ }
            }
        });
        return out;
    }
}
