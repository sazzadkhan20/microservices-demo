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
        assertEquals(new BigDecimal("9677.42"), joiner.basePay()); // 10 of 31 calendar days = 9.68 paid days
        assertEquals(new BigDecimal("9.68"), joiner.daysEmployed());
    }

    // ---------------------------------------------------------------- Decision D2 (÷30) edge cases

    private static final YearMonth FEB = YearMonth.of(2027, 2);
    private static final Rules CAL30 = new Rules(Rules.CALENDAR_30, Rules.BALANCE_FIRST, List.of("CASUAL", "ANNUAL"));

    private static Line cal30Line(YearMonth ym, LocalDate start, LocalDate exit, List<Salary> s, Month m, List<CoverBalance> cover) {
        return new Line(ym, LocalDate.of(2027, 6, 1), CAL30, emp(start, exit), s, m.list(), List.of(), cover, null);
    }

    @Test
    void calendar30FullFebruaryPaysExactGross() {
        var r = PayrollEngine.calculate(cal30Line(FEB, LocalDate.of(2020, 1, 1), null,
                List.of(monthly("2020-01-01", "30000")), new Month(FEB, SUN_THU), balances("0", "0")));
        assertEquals(new BigDecimal("30000.00"), r.basePay());
        assertEquals(new BigDecimal("30.00"), r.daysEmployed());
        assertEquals(new BigDecimal("30000.00"), r.netPay());
    }

    @Test
    void calendar30JoinerIsPaidInExactProportionInShortAndLongMonths() {
        // Joined 2 Feb: 27 of 28 days → 28.93 paid days (not 27).
        var feb = PayrollEngine.calculate(cal30Line(FEB, LocalDate.of(2027, 2, 2), null,
                List.of(monthly("2027-02-02", "30000")), new Month(FEB, SUN_THU), balances("0", "0")));
        assertEquals(new BigDecimal("28928.57"), feb.basePay());
        // Joined 2 Oct: 30 of 31 days → 29.03 paid days (not a full month).
        var oct = PayrollEngine.calculate(line(OCT, CAL30, emp(LocalDate.of(2026, 10, 2), null),
                List.of(monthly("2026-10-02", "30000")), new Month(OCT, SUN_THU), List.of(), balances("0", "0"), null));
        assertEquals(new BigDecimal("29032.26"), oct.basePay());
        assertTrue(oct.basePay().compareTo(new BigDecimal("30000.00")) < 0);
    }

    @Test
    void calendar30LeaverIsSymmetricWithAJoiner() {
        // Left 14 Oct: 14 of 31 days.
        var r = PayrollEngine.calculate(line(OCT, CAL30, emp(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 10, 14)),
                List.of(monthly("2020-01-01", "31000")), new Month(OCT, SUN_THU), List.of(), balances("0", "0"), null));
        assertEquals(new BigDecimal("14000.00"), r.basePay());
        assertTrue(r.notes().stream().anyMatch(n -> n.startsWith("Left 14 Oct")));
    }

    @Test
    void calendar30AbsenceDeductsOneThirtiethAfterCover() {
        Month m = new Month(OCT, SUN_THU).absent(5).absent(6).absent(7);
        var r = PayrollEngine.calculate(line(OCT, CAL30, emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "30000")), m, List.of(), balances("1", "1"), null));
        assertEquals(new BigDecimal("2"), r.coveredDays());
        assertEquals(new BigDecimal("1000.00"), r.deduction()); // 1 uncovered × 30000 / 30
        assertEquals(new BigDecimal("29000.00"), r.netPay());
    }

    @Test
    void calendar30UnpaidOnEveryWorkingDayEarnsNothing() {
        Month m = new Month(OCT, SUN_THU);
        for (int d = 1; d <= 31; d++) {
            if (m.days.get(OCT.atDay(d)).working()) {
                m.absent(d);
            }
        }
        var r = PayrollEngine.calculate(line(OCT, CAL30, emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "30000")), m, List.of(), balances("0", "0"), null));
        assertEquals(new BigDecimal("30000.00"), r.deduction()); // rest days are not paid on their own
        assertEquals(new BigDecimal("0.00"), r.netPay());
        assertTrue(r.notes().stream().anyMatch(n -> n.startsWith("Unpaid on every working day")));
    }

    @Test
    void calendar30AbsencesCoveredByLeaveKeepTheMonthPaid() {
        Month m = new Month(OCT, SUN_THU);
        int working = 0;
        for (int d = 1; d <= 31; d++) {
            if (m.days.get(OCT.atDay(d)).working()) {
                m.absent(d);
                working++;
            }
        }
        var r = PayrollEngine.calculate(line(OCT, CAL30, emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "30000")), m, List.of(), balances(String.valueOf(working), "0"), null));
        assertEquals(new BigDecimal("0.00"), r.deduction());
        assertEquals(new BigDecimal("30000.00"), r.netPay());
    }

    @Test
    void calendar30OneWorkedDayStillPaysTheRestOfTheMonth() {
        Month m = new Month(OCT, SUN_THU);
        int absent = 0;
        for (int d = 2; d <= 31; d++) {
            if (m.days.get(OCT.atDay(d)).working()) {
                m.absent(d);
                absent++;
            }
        }
        var r = PayrollEngine.calculate(line(OCT, CAL30, emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "30000")), m, List.of(), balances("0", "0"), null));
        assertEquals(new BigDecimal(30000 - absent * 1000).setScale(2), r.netPay());
        assertFalse(r.notes().stream().anyMatch(n -> n.startsWith("Unpaid on every working day")));
    }

    @Test
    void calendar30SalaryChangeIsSegmentedByDay() {
        // 1–14 Oct at 30,000; 15–31 Oct at 31,000.
        var r = PayrollEngine.calculate(line(OCT, CAL30, emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "30000"), monthly("2026-10-15", "31000")),
                new Month(OCT, SUN_THU).absent(20), List.of(), balances("0", "0"), null));
        assertEquals(new BigDecimal("30548.39"), r.basePay()); // (14×30000 + 17×31000) / 31
        assertEquals(new BigDecimal("1033.33"), r.deduction()); // 20 Oct is valued at 31000 / 30
    }

    // ---------------------------------------------------------------- Decision D3 (segmentation) edge cases

    @Test
    void absenceIsValuedAtTheSalaryOfItsOwnDay() {
        // Working-days basis, Oct 2026 (21 working days): raise 30,000 → 42,000 from 15 Oct.
        Month m = new Month(OCT, SUN_THU).absent(5).absent(20);
        var r = PayrollEngine.calculate(line(OCT, Rules.srsDefaults(), emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "30000"), monthly("2026-10-15", "42000")), m, List.of(), balances("0", "0"), null));
        // 5 Oct at 30000/21, 20 Oct at 42000/21.
        assertEquals(new BigDecimal("3428.57"), r.deduction());
        assertTrue(r.notes().stream().anyMatch(n -> n.startsWith("Salary changed mid-month")));
    }

    @Test
    void currencyChangeMidMonthIsAnException() {
        var r = PayrollEngine.calculate(line(OCT, Rules.srsDefaults(), emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "30000"),
                        new Salary(LocalDate.of(2026, 10, 15), "MONTHLY", new BigDecimal("6000"), "AUD")),
                new Month(OCT, SUN_THU), List.of(), balances("0", "0"), null));
        assertTrue(r.exception());
        assertTrue(r.notes().stream().anyMatch(n -> n.startsWith("Currency changed mid-month (BDT → AUD)")), r.notes().toString());
    }

    @Test
    void currencyChangeOnTheFirstIsNormal() {
        var r = PayrollEngine.calculate(line(OCT, Rules.srsDefaults(), emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "30000"),
                        new Salary(LocalDate.of(2026, 10, 1), "MONTHLY", new BigDecimal("6000"), "AUD")),
                new Month(OCT, SUN_THU), List.of(), balances("0", "0"), null));
        assertFalse(r.exception());
        assertEquals("AUD", r.currency());
        assertEquals(new BigDecimal("6000.00"), r.netPay());
    }

    // ---------------------------------------------------------------- Decision D1 (half-day leave)

    @Test
    void halfDayLeaveWithNoCheckInCountsTheOtherHalfAbsent() {
        Month m = new Month(OCT, SUN_THU);
        m.days.put(OCT.atDay(12), new Day(OCT.atDay(12), true, false, "OL", new BigDecimal("0.5"), 0, false));
        var r = PayrollEngine.calculate(line(OCT, Rules.srsDefaults(), emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "21000")), m,
                List.of(new Leave(OCT.atDay(12), OCT.atDay(12), "AM", true, "CASUAL")), balances("0", "0"), null));
        assertEquals(new BigDecimal("0.5"), r.absentDays());
        assertEquals(new BigDecimal("500.00"), r.deduction()); // half of 21000 / 21
    }

    @Test
    void unpaidHalfDayLeaveWithNoCheckInDeductsTheWholeDayOnce() {
        Month m = new Month(OCT, SUN_THU);
        m.days.put(OCT.atDay(12), new Day(OCT.atDay(12), true, false, "OL", new BigDecimal("0.5"), 0, false));
        var r = PayrollEngine.calculate(line(OCT, Rules.srsDefaults(), emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "21000")), m,
                List.of(new Leave(OCT.atDay(12), OCT.atDay(12), "PM", false, "UNPAID")), balances("0", "0"), null));
        assertEquals(new BigDecimal("0.5"), r.unpaidLeaveDays());
        assertEquals(new BigDecimal("1000.00"), r.deduction()); // 0.5 unpaid + 0.5 absent = one day
    }

    @Test
    void deductionNeverExceedsMonthlyPay() {
        Month m = new Month(OCT, SUN_THU);
        for (int d = 1; d <= 31; d++) {
            if (m.days.get(OCT.atDay(d)).working()) {
                m.absent(d);
            }
        }
        var r = PayrollEngine.calculate(line(OCT, Rules.srsDefaults(), emp(LocalDate.of(2020, 1, 1), null),
                List.of(monthly("2020-01-01", "21000")), m,
                List.of(new Leave(OCT.atDay(1), OCT.atDay(31), "FULL", false, "UNPAID")), balances("0", "0"), null));
        assertEquals(new BigDecimal("21000.00"), r.deduction());
        assertEquals(new BigDecimal("0.00"), r.netPay());
        assertFalse(r.exception());
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
