package com.platform.payroll.engine;

import com.platform.payroll.engine.PayrollInputs.Adjustment;
import com.platform.payroll.engine.PayrollInputs.CoverBalance;
import com.platform.payroll.engine.PayrollInputs.Day;
import com.platform.payroll.engine.PayrollInputs.Employee;
import com.platform.payroll.engine.PayrollInputs.Leave;
import com.platform.payroll.engine.PayrollInputs.Line;
import com.platform.payroll.engine.PayrollInputs.Rules;
import com.platform.payroll.engine.PayrollInputs.Salary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NFR-13 / AT-14 / guide PAY03 — hand-calculated expectations from docs/PAYROLL_SPEC.md. */
class PayrollEngineTest {

    private static final Set<DayOfWeek> SUN_THU = EnumSet.of(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
    private static final YearMonth SEP = YearMonth.of(2026, 9);
    private static final YearMonth OCT = YearMonth.of(2026, 10);
    private static final LocalDate AFTER = LocalDate.of(2026, 12, 1);

    /** Builds a month: working days present (480 min) unless overridden. */
    private static final class Month {
        final YearMonth ym;
        final Map<LocalDate, Day> days = new HashMap<>();

        Month(YearMonth ym, Set<DayOfWeek> working, LocalDate... holidays) {
            this.ym = ym;
            Set<LocalDate> hol = Set.of(holidays);
            for (LocalDate d = ym.atDay(1); !d.isAfter(ym.atEndOfMonth()); d = d.plusDays(1)) {
                boolean w = working.contains(d.getDayOfWeek()) && !hol.contains(d);
                days.put(d, new Day(d, w, hol.contains(d), w ? "P" : (hol.contains(d) ? "HO" : "WO"),
                        BigDecimal.ZERO, w ? 480 : 0, false));
            }
        }

        Month absent(int day) {
            LocalDate d = ym.atDay(day);
            days.put(d, new Day(d, true, false, "A", BigDecimal.ONE, 0, false));
            return this;
        }

        Month halfDay(int day) {
            LocalDate d = ym.atDay(day);
            days.put(d, new Day(d, true, false, "HD", new BigDecimal("0.5"), 180, false));
            return this;
        }

        Month onLeave(int day) {
            LocalDate d = ym.atDay(day);
            days.put(d, new Day(d, true, false, "OL", BigDecimal.ZERO, 0, false));
            return this;
        }

        Month missingCheckout(int day) {
            LocalDate d = ym.atDay(day);
            days.put(d, new Day(d, true, false, "M", BigDecimal.ZERO, 0, true));
            return this;
        }

        List<Day> list() {
            return new ArrayList<>(days.values());
        }
    }

    private static Employee emp(LocalDate start, LocalDate exit) {
        return new Employee(1, "QII-T", "Test", "BDT", start, exit, "FIXED");
    }

    private static Salary monthly(String date, String amount) {
        return new Salary(LocalDate.parse(date), "MONTHLY", new BigDecimal(amount), "BDT");
    }

    private static List<CoverBalance> balances(String casual, String annual) {
        return List.of(new CoverBalance("CASUAL", "CASUAL", new BigDecimal(casual)),
                new CoverBalance("ANNUAL", "ANNUAL", new BigDecimal(annual)));
    }

    private static Line line(YearMonth ym, Rules rules, Employee e, List<Salary> s, Month m,
                             List<Leave> leaves, List<CoverBalance> cover, Adjustment adj) {
        return new Line(ym, AFTER, rules, e, s, m.list(), leaves, cover, adj);
    }

    @Test
    void srsWorkedExampleArpaSeptember2026() {
        Month m = new Month(SEP, SUN_THU).absent(8).absent(9).halfDay(10);
        var r = PayrollEngine.calculate(line(SEP, Rules.srsDefaults(), emp(LocalDate.of(2025, 1, 1), null),
                List.of(monthly("2025-01-01", "15000")), m, List.of(), balances("1", "0"), null));

        assertEquals(new BigDecimal("22"), r.workingDays());
        assertEquals(new BigDecimal("681.82"), r.perDayRate());
        assertEquals(new BigDecimal("15000.00"), r.basePay());
        assertEquals(new BigDecimal("2.5"), r.absentDays());
        assertEquals(new BigDecimal("1"), r.coveredDays());
        assertEquals("CASUAL", r.covers().get(0).category());
        assertEquals(new BigDecimal("1.5"), r.deductedDays());
        assertEquals(new BigDecimal("1022.73"), r.deduction());
        assertEquals(new BigDecimal("13977.27"), r.netPay());
        assertFalse(r.exception());
    }

    @Test
    void fullMonthNoAbsencePaysExactGross() {
        var r = PayrollEngine.calculate(line(OCT, Rules.srsDefaults(), emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "70000")), new Month(OCT, SUN_THU), List.of(), balances("0", "0"), null));
        assertEquals(new BigDecimal("21"), r.workingDays());
        assertEquals(new BigDecimal("70000.00"), r.basePay());
        assertEquals(new BigDecimal("0.00"), r.deduction());
        assertEquals(new BigDecimal("70000.00"), r.netPay());
        assertTrue(r.notes().isEmpty());
    }

    @Test
    void lateArrivalsNeverReducePay() {
        Month m = new Month(OCT, SUN_THU);
        LocalDate d = OCT.atDay(5);
        m.days.put(d, new Day(d, true, false, "L", BigDecimal.ZERO, 400, false));
        var r = PayrollEngine.calculate(line(OCT, Rules.srsDefaults(), emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "70000")), m, List.of(), balances("0", "0"), null));
        assertEquals(new BigDecimal("70000.00"), r.netPay());
    }

    @Test
    void joinerIsProRatedOnWorkingDaysEmployed() {
        var r = PayrollEngine.calculate(line(SEP, Rules.srsDefaults(), emp(LocalDate.of(2026, 9, 14), null),
                List.of(monthly("2026-09-14", "44000")), new Month(SEP, SUN_THU), List.of(), balances("0", "0"), null));
        assertEquals(new BigDecimal("13.00"), r.daysEmployed());
        assertEquals(new BigDecimal("26000.00"), r.basePay());
        assertTrue(r.notes().get(0).startsWith("Joined 14 Sep"));
    }

    @Test
    void leaverIsProRatedAndIgnoresDaysAfterExit() {
        Month m = new Month(OCT, SUN_THU).absent(20); // after exit — must not count
        var r = PayrollEngine.calculate(line(OCT, Rules.srsDefaults(), emp(LocalDate.of(2022, 1, 1), LocalDate.of(2026, 10, 15)),
                List.of(monthly("2022-01-01", "40000")), m, List.of(), balances("0", "0"), null));
        assertEquals(new BigDecimal("11.00"), r.daysEmployed());
        assertEquals(new BigDecimal("20952.38"), r.basePay());
        assertEquals(0, r.absentDays().signum());
        assertTrue(r.notes().get(0).startsWith("Left 15 Oct"));
    }

    @Test
    void midMonthSalaryChangeIsSegmentedPerDay() {
        var r = PayrollEngine.calculate(line(SEP, Rules.srsDefaults(), emp(LocalDate.of(2023, 1, 8), null),
                List.of(monthly("2023-01-08", "58000"), monthly("2026-09-16", "62000")),
                new Month(SEP, SUN_THU).absent(17), List.of(), balances("0", "0"), null));
        assertEquals(new BigDecimal("60000.00"), r.basePay());
        // the absent day (17 Sep) is valued at the new salary: 62000 / 22
        assertEquals(new BigDecimal("2818.18"), r.deduction());
        assertEquals(new BigDecimal("2818.18"), r.perDayRate());
        assertTrue(r.notes().stream().anyMatch(n -> n.startsWith("Salary changed mid-month")));
    }

    @Test
    void unpaidLeaveIsAlwaysDeductedOnWorkingDaysOnly() {
        // 10–11 Sep (Thu, Fri): Friday is a weekly day off, so 1 unpaid working day; 15 Sep half day.
        Month m = new Month(SEP, SUN_THU).onLeave(10).onLeave(15);
        List<Leave> leaves = List.of(
                new Leave(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11), "FULL", false, "UNPAID"),
                new Leave(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 15), "FIRST_HALF", false, "UNPAID"),
                new Leave(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20), "FULL", true, "CASUAL"));
        var r = PayrollEngine.calculate(line(SEP, Rules.srsDefaults(), emp(LocalDate.of(2022, 1, 15), null),
                List.of(monthly("2022-01-15", "65000")), m, leaves, balances("10", "10"), null));
        assertEquals(new BigDecimal("1.5"), r.unpaidLeaveDays());
        assertEquals(0, r.coveredDays().signum(), "unpaid leave never uses balance");
        assertEquals(new BigDecimal("4431.82"), r.deduction()); // 1.5 × 2954.5454…
    }

    @Test
    void coverOrderUsesCasualThenAnnualThenDeductsExcess() {
        Month m = new Month(OCT, SUN_THU).absent(4).absent(5).absent(6).absent(7);
        var r = PayrollEngine.calculate(line(OCT, Rules.srsDefaults(), emp(LocalDate.of(2021, 11, 1), null),
                List.of(monthly("2021-11-01", "70000")), m, List.of(), balances("1", "2"), null));
        assertEquals(new BigDecimal("4"), r.absentDays());
        assertEquals(2, r.covers().size());
        assertEquals("CASUAL", r.covers().get(0).category());
        assertEquals(new BigDecimal("1"), r.covers().get(0).days());
        assertEquals(new BigDecimal("2"), r.covers().get(1).days());
        assertEquals(new BigDecimal("1"), r.deductedDays());
        assertEquals(new BigDecimal("3333.33"), r.deduction()); // 70000 / 21
    }

    @Test
    void directRuleDeductsEveryAbsentDay() {
        Month m = new Month(SEP, SUN_THU).absent(8).absent(9).halfDay(10);
        Rules direct = new Rules(Rules.WORKING_DAYS, Rules.DIRECT, List.of("CASUAL", "ANNUAL"));
        var r = PayrollEngine.calculate(line(SEP, direct, emp(LocalDate.of(2025, 1, 1), null),
                List.of(monthly("2025-01-01", "15000")), m, List.of(), balances("5", "5"), null));
        assertEquals(0, r.coveredDays().signum());
        assertEquals(new BigDecimal("1704.55"), r.deduction()); // 2.5 × 681.8181…
    }

    @Test
    void calendar30BasisPaysGrossForFullMonthAndThirtiethPerAbsentDay() {
        Rules cal = new Rules(Rules.CALENDAR_30, Rules.DIRECT, List.of());
        var full = PayrollEngine.calculate(line(OCT, cal, emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "30000")), new Month(OCT, SUN_THU).absent(5), List.of(), List.of(), null));
        assertEquals(new BigDecimal("30000.00"), full.basePay());
        assertEquals(new BigDecimal("1000.00"), full.perDayRate());
        assertEquals(new BigDecimal("1000.00"), full.deduction());

        var joiner = PayrollEngine.calculate(line(OCT, cal, emp(LocalDate.of(2026, 10, 22), null),
                List.of(monthly("2026-10-22", "30000")), new Month(OCT, SUN_THU), List.of(), List.of(), null));
        assertEquals(new BigDecimal("10000.00"), joiner.basePay()); // 10 calendar days × 1000
    }

    @Test
    void hourlyPaysClosedShiftsOnlyAndFlagsMissingCheckout() {
        Month m = new Month(OCT, EnumSet.noneOf(DayOfWeek.class));
        m.days.put(OCT.atDay(5), new Day(OCT.atDay(5), false, false, "W", BigDecimal.ZERO, 450, false));
        m.days.put(OCT.atDay(6), new Day(OCT.atDay(6), false, false, "W", BigDecimal.ZERO, 300, false));
        m.days.put(OCT.atDay(7), new Day(OCT.atDay(7), false, false, "M", BigDecimal.ZERO, 0, true));
        var r = PayrollEngine.calculate(line(OCT, Rules.srsDefaults(),
                new Employee(2, "QII-H", "Hourly", "AUD", LocalDate.of(2024, 6, 15), null, "HOURLY"),
                List.of(new Salary(LocalDate.of(2024, 6, 15), "HOURLY", new BigDecimal("45"), "AUD")),
                m, List.of(), List.of(), new Adjustment(new BigDecimal("50"), "Travel")));
        assertEquals("HOURLY", r.payType());
        assertEquals(new BigDecimal("12.50"), r.hoursWorked());
        assertEquals(new BigDecimal("562.50"), r.basePay());
        assertEquals(new BigDecimal("0.00"), r.deduction());
        assertEquals(new BigDecimal("612.50"), r.netPay());
        assertNull(r.perDayRate());
        assertTrue(r.notes().stream().anyMatch(n -> n.contains("missing check-out — not paid")));
        assertFalse(r.exception());
    }

    @Test
    void hourlyRateChangeValuesEachShiftAtItsDayRate() {
        Month m = new Month(OCT, EnumSet.noneOf(DayOfWeek.class));
        m.days.put(OCT.atDay(5), new Day(OCT.atDay(5), false, false, "W", BigDecimal.ZERO, 60, false));
        m.days.put(OCT.atDay(20), new Day(OCT.atDay(20), false, false, "W", BigDecimal.ZERO, 60, false));
        var r = PayrollEngine.calculate(line(OCT, Rules.srsDefaults(),
                new Employee(2, "QII-H", "Hourly", "BDT", LocalDate.of(2024, 1, 1), null, "HOURLY"),
                List.of(new Salary(LocalDate.of(2024, 1, 1), "HOURLY", new BigDecimal("200"), "BDT"),
                        new Salary(LocalDate.of(2026, 10, 15), "HOURLY", new BigDecimal("250"), "BDT")),
                m, List.of(), List.of(), null));
        assertEquals(new BigDecimal("450.00"), r.basePay());
        assertEquals(new BigDecimal("250.00"), r.hourlyRate());
    }

    @Test
    void negativeNetAndMissingSalaryAreExceptions() {
        var negative = PayrollEngine.calculate(line(OCT, Rules.srsDefaults(), emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "1000")), new Month(OCT, SUN_THU), List.of(), List.of(),
                new Adjustment(new BigDecimal("-5000"), "Advance recovery")));
        assertTrue(negative.exception());
        assertEquals(new BigDecimal("-4000.00"), negative.netPay());

        var noSalary = PayrollEngine.calculate(line(OCT, Rules.srsDefaults(), emp(LocalDate.of(2020, 1, 1), null),
                List.of(), new Month(OCT, SUN_THU), List.of(), List.of(), null));
        assertTrue(noSalary.exception());
        assertEquals(new BigDecimal("0.00"), noSalary.netPay());
    }

    @Test
    void joinerOnWeekendWithNoWorkingDaysIsNotAnException() {
        // 30–31 Oct 2026 are Friday/Saturday (BD weekly off).
        var r = PayrollEngine.calculate(line(OCT, Rules.srsDefaults(), emp(LocalDate.of(2026, 10, 30), null),
                List.of(), new Month(OCT, SUN_THU), List.of(), List.of(), null));
        assertFalse(r.exception());
        assertEquals(new BigDecimal("0.00"), r.netPay());
    }

    @Test
    void monthNotOverIsNoted() {
        Line in = new Line(OCT, LocalDate.of(2026, 10, 12), Rules.srsDefaults(), emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "21000")), new Month(OCT, SUN_THU).list(), List.of(), List.of(), null);
        var r = PayrollEngine.calculate(in);
        assertTrue(r.notes().stream().anyMatch(n -> n.startsWith("Month not over")));
        assertEquals(new BigDecimal("21000.00"), r.basePay()); // base is the whole month (FR-PAY-02)
    }
}
