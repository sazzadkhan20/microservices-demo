package com.platform.payroll.extension.engine;

import com.platform.payroll.domain.PayrollConfigEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR-PAY-01..05 unit tests.
 *
 * Arpa September 2026 worked example:
 *   baseSalary=15 000, workingDays=22, daysEmployed=22,
 *   absentDays=2.5, CASUAL balance=1 (no ANNUAL), coverOrder=[CASUAL,ANNUAL]
 *   → perDayRate = 15000/22 = 681.8182 (4dp HALF_UP)
 *   → CASUAL covers 1 day, uncovered = 1.5
 *   → deduction = 1.5 × 681.8182 = 1022.7273  ≈ 1022.73 (2dp)
 *   → basePay = 681.8182 × 22 = 15000.0004 (inherent rounding)
 *   → net = 15000.0004 − 1022.7273 = 13977.2731 ≈ 13977.27 (2dp)
 */
class MonthlyPayrollEngineTest {

    private MonthlyPayrollEngine engine;
    private PayrollConfigEntity defaultConfig;

    @BeforeEach
    void setUp() {
        engine = new MonthlyPayrollEngine();
        defaultConfig = buildConfig(List.of("CASUAL", "ANNUAL"), "BALANCE_FIRST", "SCHEDULE");
    }

    // ── Arpa September 2026 ──────────────────────────────────────────────────

    @Test
    @DisplayName("Arpa Sep-2026: net ≈ 13977.27, deduction ≈ 1022.73")
    void arpa_september_2026_full_example() {
        // Only 1 CASUAL balance; ANNUAL not present → 1.5 days remain uncovered
        Map<String, Object> facts = buildFacts(22, 22,
                new BigDecimal("2.5"), BigDecimal.ZERO,
                Map.of("CASUAL", BigDecimal.ONE));

        MonthlyPayrollEngine.PayrollResult result =
                engine.calculateMonthly(defaultConfig, new BigDecimal("15000"), facts, null);

        BigDecimal net2dp = result.net().setScale(2, RoundingMode.HALF_UP);
        BigDecimal ded2dp = result.deduction().setScale(2, RoundingMode.HALF_UP);

        assertEquals(new BigDecimal("13977.27"), net2dp,
                "net should be 13977.27 — got " + net2dp);
        assertEquals(new BigDecimal("1022.73"), ded2dp,
                "deduction should be 1022.73 — got " + ded2dp);
    }

    @Test
    @DisplayName("perDayRate = 15000/22 = 681.8182 (4dp HALF_UP)")
    void per_day_rate_precision() {
        Map<String, Object> facts = buildFacts(22, 22, BigDecimal.ZERO, BigDecimal.ZERO, Map.of());
        MonthlyPayrollEngine.PayrollResult result =
                engine.calculateMonthly(defaultConfig, new BigDecimal("15000"), facts, null);

        assertEquals(new BigDecimal("681.8182"), result.perDayRate(),
                "perDayRate precision failed — got " + result.perDayRate());
    }

    @Test
    @DisplayName("No absences: baseSalary preserved (within rounding artifact of day-rate formula)")
    void no_absences_full_month() {
        Map<String, Object> facts = buildFacts(22, 22, BigDecimal.ZERO, BigDecimal.ZERO, Map.of());
        MonthlyPayrollEngine.PayrollResult result =
                engine.calculateMonthly(defaultConfig, new BigDecimal("15000"), facts, null);

        // 681.8182 × 22 = 15000.0004 — expected rounding artifact
        assertEquals(0, result.uncoveredDays().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.deduction().compareTo(BigDecimal.ZERO));
        // Net rounded to 2dp should be 15000.00
        assertEquals(new BigDecimal("15000.00"), result.net().setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("Pro-rated: 11 daysEmployed of 22 workingDays → basePay rounds to 7500 (2dp)")
    void pro_rated_half_month() {
        Map<String, Object> facts = buildFacts(22, 11, BigDecimal.ZERO, BigDecimal.ZERO, Map.of());
        MonthlyPayrollEngine.PayrollResult result =
                engine.calculateMonthly(defaultConfig, new BigDecimal("15000"), facts, null);

        // 681.8182 × 11 = 7500.0002; rounded 2dp = 7500.00
        assertEquals(new BigDecimal("7500.00"), result.basePay().setScale(2, RoundingMode.HALF_UP),
                "basePay for half-month pro-rate (2dp) — got " + result.basePay());
        assertTrue(result.notes().stream().anyMatch(n -> n.contains("Pro-rated")));
    }

    @Test
    @DisplayName("DIRECT mode: uncoveredDays = absentDays regardless of balance")
    void direct_mode_no_cover() {
        PayrollConfigEntity direct = buildConfig(List.of("CASUAL", "ANNUAL"), "DIRECT", "SCHEDULE");
        Map<String, Object> facts = buildFacts(22, 22,
                new BigDecimal("2.5"), BigDecimal.ZERO,
                Map.of("CASUAL", new BigDecimal("5"), "ANNUAL", new BigDecimal("10")));

        MonthlyPayrollEngine.PayrollResult result =
                engine.calculateMonthly(direct, new BigDecimal("15000"), facts, null);

        assertEquals(0, result.uncoveredDays().compareTo(new BigDecimal("2.5")),
                "DIRECT: all absent days should be uncovered — got " + result.uncoveredDays());
        assertTrue(result.consumedByType().isEmpty(), "DIRECT: no leave should be consumed");
    }

    @Test
    @DisplayName("Half-day absence (0.5): covered by CASUAL=1, uncovered=0, deduction=0")
    void half_day_covered_by_casual() {
        Map<String, Object> facts = buildFacts(22, 22,
                new BigDecimal("0.5"), BigDecimal.ZERO,
                Map.of("CASUAL", new BigDecimal("1")));

        MonthlyPayrollEngine.PayrollResult result =
                engine.calculateMonthly(defaultConfig, new BigDecimal("15000"), facts, null);

        assertEquals(0, result.uncoveredDays().compareTo(BigDecimal.ZERO),
                "Half-day should be fully covered by CASUAL balance — got " + result.uncoveredDays());
        assertEquals(0, result.consumedByType().get("CASUAL").compareTo(new BigDecimal("0.5")));
        assertEquals(0, result.deduction().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Unpaid leave always deducted (FR-PAY-05): 1 day = perDayRate")
    void unpaid_leave_always_deducted() {
        Map<String, Object> facts = buildFacts(22, 22,
                BigDecimal.ZERO, new BigDecimal("1"), Map.of());

        MonthlyPayrollEngine.PayrollResult result =
                engine.calculateMonthly(defaultConfig, new BigDecimal("15000"), facts, null);

        // DR-02: money amounts at 2 decimal places (per-day rate still 4dp internally)
        BigDecimal expectedDeduction = new BigDecimal("681.82");
        assertEquals(0, result.deduction().compareTo(expectedDeduction),
                "1 unpaid leave day should deduct 681.82 — got " + result.deduction());
    }

    @Test
    @DisplayName("FIXED divisor mode: uses dayDivisor=30 instead of workingDaysInMonth")
    void fixed_divisor_mode() {
        PayrollConfigEntity fixedCfg = buildConfig(List.of("CASUAL"), "BALANCE_FIRST", "FIXED");
        Map<String, Object> facts = buildFacts(22, 22, BigDecimal.ZERO, BigDecimal.ZERO, Map.of());
        // With FIXED divisor=30, perDayRate = 15000/30 = 500
        MonthlyPayrollEngine.PayrollResult result =
                engine.calculateMonthly(fixedCfg, new BigDecimal("15000"), facts, null);

        assertEquals(30, result.workingDays(), "FIXED mode should use dayDivisor=30");
        assertEquals(new BigDecimal("500.0000"), result.perDayRate());
    }

    @Test
    @DisplayName("Adjustment (FR-PAY-08): positive 500 added to net (within 2dp)")
    void positive_adjustment_added_to_net() {
        Map<String, Object> facts = buildFacts(22, 22, BigDecimal.ZERO, BigDecimal.ZERO, Map.of());
        MonthlyPayrollEngine.PayrollResult result =
                engine.calculateMonthly(defaultConfig, new BigDecimal("15000"), facts, new BigDecimal("500"));

        // net = basePay(15000.0004) + 500 = 15500.0004 → 2dp = 15500.00
        assertEquals(new BigDecimal("15500.00"), result.net().setScale(2, RoundingMode.HALF_UP),
                "Net should include +500 adjustment — got " + result.net());
    }

    @Test
    @DisplayName("Absent days beyond CASUAL balance: consumedByType contains only CASUAL")
    void absent_partially_covered_only_casual() {
        Map<String, Object> facts = buildFacts(22, 22,
                new BigDecimal("2.5"), BigDecimal.ZERO,
                Map.of("CASUAL", BigDecimal.ONE));

        MonthlyPayrollEngine.PayrollResult result =
                engine.calculateMonthly(defaultConfig, new BigDecimal("15000"), facts, null);

        assertEquals(0, result.consumedByType().get("CASUAL").compareTo(BigDecimal.ONE));
        assertEquals(0, result.uncoveredDays().compareTo(new BigDecimal("1.5")));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static PayrollConfigEntity buildConfig(
            List<String> coverOrder, String absenceRule, String dayDivisorMode) {
        PayrollConfigEntity cfg = new PayrollConfigEntity();
        cfg.setOvertimeMultiplier(new BigDecimal("1.5"));
        cfg.setWeekendMultiplier(new BigDecimal("1.0"));
        cfg.setDeductionRules(List.of(
                Map.of("type", "CONFIG", "key", "dayDivisorMode", "value", dayDivisorMode),
                Map.of("type", "CONFIG", "key", "dayDivisor", "value", 30),
                Map.of("type", "CONFIG", "key", "absenceRule", "value", absenceRule),
                Map.of("type", "CONFIG", "key", "absenceCoverOrder", "value", coverOrder),
                Map.of("type", "CONFIG", "key", "unpaidAlwaysDeduct", "value", true),
                Map.of("type", "CONFIG", "key", "dailyHours", "value", 8)
        ));
        return cfg;
    }

    private static Map<String, Object> buildFacts(
            int workingDaysInMonth, int daysEmployed,
            BigDecimal absentDays, BigDecimal unpaidLeaveDays,
            Map<String, BigDecimal> leaveBalances) {
        return Map.of(
                "workingDaysInMonth", workingDaysInMonth,
                "daysEmployed", daysEmployed,
                "absentDays", absentDays,
                "unpaidLeaveDays", unpaidLeaveDays,
                "paidLeaveDays", BigDecimal.ZERO,
                "hoursWorked", BigDecimal.ZERO,
                "missingCheckoutCount", 0L,
                "leaveBalances", leaveBalances
        );
    }
}
