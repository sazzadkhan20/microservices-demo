package com.platform.payroll.engine;

import com.platform.payroll.engine.PayrollInputs.CoverBalance;
import com.platform.payroll.engine.PayrollInputs.Day;
import com.platform.payroll.engine.PayrollInputs.Leave;
import com.platform.payroll.engine.PayrollInputs.Line;
import com.platform.payroll.engine.PayrollInputs.Salary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure payroll calculation (docs/PAYROLL_SPEC.md §1.3–§1.5). No I/O, no clock: everything comes from
 * {@link Line}. Money is computed unrounded (scale 10) and rounded HALF_UP to 2 dp once per amount
 * (DR-02); rates are kept unrounded and only displayed at 2 dp.
 *
 * <p>Every day is valued by the salary effective on that day (Decision D3), so mid-month salary and
 * pay-type changes, joiners and leavers all fall out of the same per-day loop.
 */
public final class PayrollEngine {

    private static final int SCALE = 10;
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    public record Cover(String family, String category, BigDecimal days) {
    }

    public record Result(
            String payType,
            String currency,
            BigDecimal workingDays,
            BigDecimal daysEmployed,
            BigDecimal perDayRate,
            BigDecimal hourlyRate,
            BigDecimal hoursWorked,
            BigDecimal absentDays,
            BigDecimal coveredDays,
            List<Cover> covers,
            BigDecimal unpaidLeaveDays,
            BigDecimal deductedDays,
            BigDecimal basePay,
            BigDecimal deduction,
            BigDecimal adjustment,
            String adjustmentNote,
            BigDecimal netPay,
            boolean exception,
            List<String> notes,
            Map<String, Object> calc) {
    }

    private PayrollEngine() {
    }

    public static Result calculate(Line in) {
        YearMonth period = in.period();
        LocalDate monthStart = period.atDay(1);
        LocalDate monthEnd = period.atEndOfMonth();
        var emp = in.employee();
        LocalDate from = max(monthStart, emp.startDate() == null ? monthStart : emp.startDate());
        LocalDate to = min(monthEnd, emp.exitDate() == null ? monthEnd : emp.exitDate());

        List<Salary> salaries = in.salaries().stream()
                .sorted(Comparator.comparing(Salary::effectiveDate)).toList();
        Map<LocalDate, Day> days = new TreeMap<>();
        for (Day d : in.days()) {
            if (!d.date().isBefore(monthStart) && !d.date().isAfter(monthEnd)) {
                days.put(d.date(), d);
            }
        }

        List<String> notes = new ArrayList<>();
        boolean exception = false;

        // W — working days in the whole month from the person's schedule (BR-15), or 30.
        long scheduleWorkingDays = days.values().stream().filter(Day::working).count();
        boolean cal30 = in.rules().calendar30();
        BigDecimal divisor = cal30 ? THIRTY : BigDecimal.valueOf(scheduleWorkingDays);
        int daysInMonth = period.lengthOfMonth();
        boolean wholeMonth = !from.isAfter(monthStart) && !to.isBefore(monthEnd);

        BigDecimal base = BigDecimal.ZERO;
        BigDecimal daysEmployed = BigDecimal.ZERO;
        BigDecimal hourlyPay = BigDecimal.ZERO;
        long workedMinutes = 0;
        int missingCheckouts = 0;
        int monthlyMissingCheckouts = 0;
        LocalDate firstMissingSalary = null;
        boolean anyMonthly = false;
        boolean anyHourly = false;
        // absent day → (weight, rate of that day)
        List<Object[]> absences = new ArrayList<>();
        Map<LocalDate, BigDecimal> rateByDay = new LinkedHashMap<>();
        List<Map<String, Object>> segments = new ArrayList<>();

        if (!from.isAfter(to)) {
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                Day day = days.get(d);
                Salary s = effective(salaries, d);
                boolean worked = day != null && day.workedMinutes() > 0;
                boolean workingDay = day != null && day.working();
                if (s == null) {
                    if ((workingDay || worked || (day != null && day.missingCheckout())) && firstMissingSalary == null) {
                        firstMissingSalary = d;
                    }
                    continue;
                }
                if (s.hourly()) {
                    anyHourly = true;
                    if (day != null) {
                        if (day.workedMinutes() > 0) {
                            workedMinutes += day.workedMinutes();
                            hourlyPay = hourlyPay.add(s.amount()
                                    .multiply(BigDecimal.valueOf(day.workedMinutes()))
                                    .divide(SIXTY, SCALE, RoundingMode.HALF_UP));
                        }
                        if (day.missingCheckout()) {
                            missingCheckouts++;
                        }
                    }
                    continue;
                }
                anyMonthly = true;
                if (day != null && day.missingCheckout()) {
                    monthlyMissingCheckouts++;
                }
                if (cal30) {
                    BigDecimal rate = s.amount().divide(THIRTY, SCALE, RoundingMode.HALF_UP);
                    // Whole month = exactly the monthly gross whatever the month length (Decision D2).
                    BigDecimal weight = wholeMonth
                            ? THIRTY.divide(BigDecimal.valueOf(daysInMonth), SCALE, RoundingMode.HALF_UP)
                            : BigDecimal.ONE;
                    base = base.add(rate.multiply(weight));
                    daysEmployed = daysEmployed.add(weight);
                    rateByDay.put(d, rate);
                } else if (workingDay && divisor.signum() > 0) {
                    BigDecimal rate = s.amount().divide(divisor, SCALE, RoundingMode.HALF_UP);
                    base = base.add(rate);
                    daysEmployed = daysEmployed.add(BigDecimal.ONE);
                    rateByDay.put(d, rate);
                }
                if (workingDay && day.absence() != null && day.absence().signum() > 0 && rateByDay.containsKey(d)) {
                    absences.add(new Object[]{d, day.absence(), rateByDay.get(d)});
                }
            }
        }
        if (cal30 && !wholeMonth && daysEmployed.compareTo(THIRTY) > 0) {
            // Partial month in a 31-day month cannot exceed 30 paid days.
            BigDecimal scale = THIRTY.divide(daysEmployed, SCALE, RoundingMode.HALF_UP);
            base = base.multiply(scale);
            daysEmployed = THIRTY;
        }

        // Unpaid leave (FR-PAY-05): approved unpaid leave on working, employed, monthly-paid days.
        BigDecimal unpaidDays = BigDecimal.ZERO;
        BigDecimal unpaidAmount = BigDecimal.ZERO;
        for (Leave lv : in.leaves()) {
            if (lv.paid()) {
                continue;
            }
            for (LocalDate d = max(lv.start(), from); !d.isAfter(min(lv.end(), to)); d = d.plusDays(1)) {
                Day day = days.get(d);
                BigDecimal rate = rateByDay.get(d);
                if (day == null || !day.working() || rate == null) {
                    continue;
                }
                BigDecimal portion = lv.halfDay() && lv.start().equals(lv.end()) ? new BigDecimal("0.5") : BigDecimal.ONE;
                unpaidDays = unpaidDays.add(portion);
                unpaidAmount = unpaidAmount.add(rate.multiply(portion));
            }
        }

        // Absences (FR-PAY-03/04): cover in date order from the balances in cover order (BR-16).
        BigDecimal absentDays = BigDecimal.ZERO;
        for (Object[] a : absences) {
            absentDays = absentDays.add((BigDecimal) a[1]);
        }
        List<Cover> covers = new ArrayList<>();
        BigDecimal uncoveredAmount = BigDecimal.ZERO;
        BigDecimal uncoveredDays = BigDecimal.ZERO;
        List<BigDecimal> remaining = new ArrayList<>();
        List<CoverBalance> balances = in.rules().direct() ? List.of() : in.coverBalances();
        for (CoverBalance b : balances) {
            remaining.add(b.available() == null ? BigDecimal.ZERO : b.available().max(BigDecimal.ZERO));
        }
        BigDecimal[] used = new BigDecimal[balances.size()];
        java.util.Arrays.fill(used, BigDecimal.ZERO);
        absences.sort(Comparator.comparing(a -> (LocalDate) a[0]));
        for (Object[] a : absences) {
            BigDecimal need = (BigDecimal) a[1];
            BigDecimal rate = (BigDecimal) a[2];
            for (int i = 0; i < balances.size() && need.signum() > 0; i++) {
                BigDecimal take = remaining.get(i).min(need);
                if (take.signum() > 0) {
                    remaining.set(i, remaining.get(i).subtract(take));
                    used[i] = used[i].add(take);
                    need = need.subtract(take);
                }
            }
            if (need.signum() > 0) {
                uncoveredDays = uncoveredDays.add(need);
                uncoveredAmount = uncoveredAmount.add(rate.multiply(need));
            }
        }
        BigDecimal coveredDays = BigDecimal.ZERO;
        for (int i = 0; i < balances.size(); i++) {
            if (used[i].signum() > 0) {
                covers.add(new Cover(balances.get(i).family(), balances.get(i).category(), used[i]));
                coveredDays = coveredDays.add(used[i]);
            }
        }

        BigDecimal basePay = money(base.add(hourlyPay));
        BigDecimal deduction = money(uncoveredAmount.add(unpaidAmount));
        BigDecimal adjustment = in.adjustment() == null ? BigDecimal.ZERO : money(in.adjustment().amount());
        String adjustmentNote = in.adjustment() == null ? null : in.adjustment().note();
        BigDecimal net = basePay.subtract(deduction).add(adjustment).setScale(2, RoundingMode.HALF_UP);

        // Display values: the salary effective on the last employed day.
        Salary last = effective(salaries, to.isBefore(from) ? monthEnd : to);
        String payType = last == null ? null : last.payType().toUpperCase(Locale.ROOT);
        String currency = last == null ? emp.defaultCurrency() : last.currency();
        BigDecimal perDayRate = null;
        BigDecimal hourlyRate = null;
        if (last != null && !last.hourly() && divisor.signum() > 0) {
            perDayRate = money(last.amount().divide(divisor, SCALE, RoundingMode.HALF_UP));
        }
        if (last != null && last.hourly()) {
            hourlyRate = money(last.amount());
        }

        // Notes (FR-PAY-10) and exceptions (§1.5).
        if (emp.startDate() != null && emp.startDate().isAfter(monthStart) && !emp.startDate().isAfter(monthEnd)) {
            notes.add("Joined " + DAY.format(emp.startDate()) + (anyMonthly ? " — pro-rated: " + fmt(daysEmployed)
                    + " of " + fmt(divisor) + (cal30 ? " days" : " working days") : ""));
        }
        if (emp.exitDate() != null && !emp.exitDate().isBefore(monthStart) && emp.exitDate().isBefore(monthEnd)) {
            notes.add("Left " + DAY.format(emp.exitDate()) + (anyMonthly ? " — pro-rated: " + fmt(daysEmployed)
                    + " of " + fmt(divisor) + (cal30 ? " days" : " working days") : ""));
        }
        long changes = salaries.stream()
                .filter(s -> s.effectiveDate().isAfter(from) && !s.effectiveDate().isAfter(to)).count();
        if (changes > 0) {
            notes.add("Salary changed mid-month — each day paid at the salary effective that day");
        }
        if (anyMonthly && anyHourly) {
            notes.add("Pay type changed mid-month (monthly and hourly days)");
        }
        if (absentDays.signum() > 0) {
            if (in.rules().direct()) {
                notes.add("Absent " + fmt(absentDays) + " day(s) — all deducted (Direct rule)");
            } else if (uncoveredDays.signum() > 0) {
                notes.add("Absent " + fmt(absentDays) + " day(s): " + fmt(coveredDays)
                        + " covered by leave, " + fmt(uncoveredDays) + " beyond balance deducted");
            } else {
                notes.add("Absent " + fmt(absentDays) + " day(s) — all covered by leave balance");
            }
        }
        if (unpaidDays.signum() > 0) {
            notes.add("Unpaid leave " + fmt(unpaidDays) + " day(s) deducted");
        }
        if (missingCheckouts > 0) {
            notes.add(missingCheckouts + " shift(s) with missing check-out — not paid until corrected");
        }
        if (monthlyMissingCheckouts > 0) {
            notes.add(monthlyMissingCheckouts + " missing check-out(s) — correct attendance records");
        }
        if (in.asOf() != null && in.asOf().isBefore(monthEnd)) {
            notes.add("Month not over — absences counted up to " + DAY.format(min(in.asOf(), monthEnd)));
        }
        if (firstMissingSalary != null) {
            exception = true;
            notes.add("No salary record effective on " + DAY.format(firstMissingSalary) + " — add a salary record");
        }
        if (anyMonthly && !cal30 && scheduleWorkingDays == 0) {
            exception = true;
            notes.add("Monthly pay but the schedule has no working days this month");
        }
        if (net.signum() < 0) {
            exception = true;
            notes.add("Net pay is negative — correct the adjustment");
        }

        Map<String, Object> calc = new LinkedHashMap<>();
        calc.put("period", period.toString());
        calc.put("employmentFrom", from.toString());
        calc.put("employmentTo", to.toString());
        calc.put("perDayRateBasis", in.rules().perDayRateBasis());
        calc.put("absenceRule", in.rules().absenceRule());
        calc.put("coverOrder", in.rules().coverOrder());
        calc.put("divisor", divisor);
        calc.put("monthlyBaseExact", base.setScale(6, RoundingMode.HALF_UP));
        calc.put("hourlyPayExact", hourlyPay.setScale(6, RoundingMode.HALF_UP));
        calc.put("uncoveredAbsentDays", uncoveredDays);
        calc.put("uncoveredDeductionExact", uncoveredAmount.setScale(6, RoundingMode.HALF_UP));
        calc.put("unpaidDeductionExact", unpaidAmount.setScale(6, RoundingMode.HALF_UP));
        calc.put("missingCheckouts", missingCheckouts + monthlyMissingCheckouts);
        calc.put("coverBalancesBefore", balances.stream()
                .map(b -> Map.of("family", b.family(), "category", b.category(), "available", b.available()))
                .toList());
        for (Salary s : salaries) {
            if (!s.effectiveDate().isAfter(to)) {
                segments.add(Map.of("effectiveDate", s.effectiveDate().toString(), "payType", s.payType(),
                        "amount", s.amount(), "currency", s.currency()));
            }
        }
        calc.put("salaries", segments);

        BigDecimal deducted = uncoveredDays.add(unpaidDays);
        BigDecimal hours = BigDecimal.valueOf(workedMinutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
        return new Result(payType, currency,
                cal30 ? THIRTY : BigDecimal.valueOf(scheduleWorkingDays),
                daysEmployed.setScale(2, RoundingMode.HALF_UP),
                perDayRate, hourlyRate, hours,
                absentDays, coveredDays, covers, unpaidDays, deducted,
                basePay, deduction, adjustment, adjustmentNote, net, exception, notes, calc);
    }

    static Salary effective(List<Salary> sortedAsc, LocalDate day) {
        Salary hit = null;
        for (Salary s : sortedAsc) {
            if (s.effectiveDate().isAfter(day)) {
                break;
            }
            hit = s;
        }
        return hit;
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String fmt(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
