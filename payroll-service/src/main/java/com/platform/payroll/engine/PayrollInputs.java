package com.platform.payroll.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Immutable inputs for one employee's payroll row (docs/PAYROLL_SPEC.md §1.3–1.4). Assembled by
 * {@code PayrollFactsAssembler} from People, Time &amp; Work, Leave and Payroll's own salary records.
 */
public final class PayrollInputs {

    private PayrollInputs() {
    }

    /** Admin payroll rules (§1.9). */
    public record Rules(String perDayRateBasis, String absenceRule, List<String> coverOrder) {
        public static final String WORKING_DAYS = "WORKING_DAYS";
        public static final String CALENDAR_30 = "CALENDAR_30";
        public static final String BALANCE_FIRST = "BALANCE_FIRST";
        public static final String DIRECT = "DIRECT";

        public static Rules srsDefaults() {
            return new Rules(WORKING_DAYS, BALANCE_FIRST, List.of("CASUAL", "ANNUAL"));
        }

        public boolean calendar30() {
            return CALENDAR_30.equalsIgnoreCase(perDayRateBasis);
        }

        public boolean direct() {
            return DIRECT.equalsIgnoreCase(absenceRule);
        }
    }

    /** One salary record; effective from {@code effectiveDate} until the next record. */
    public record Salary(LocalDate effectiveDate, String payType, BigDecimal amount, String currency) {
        public boolean hourly() {
            return "HOURLY".equalsIgnoreCase(payType);
        }
    }

    /**
     * One calendar day from Time &amp; Work. {@code absence} is already weighted per Appendix A and D1
     * (Absent = 1, Half day = 0.5, half-day leave with no check-in = 0.5) and is 0 for today/future days.
     */
    public record Day(
            LocalDate date,
            boolean working,
            boolean holiday,
            String status,
            BigDecimal absence,
            int workedMinutes,
            boolean missingCheckout) {
    }

    /** Approved leave from Leave, clipped by the engine to working days in the month. */
    public record Leave(LocalDate start, LocalDate end, String halfDayPart, boolean paid, String category) {
        public boolean halfDay() {
            return halfDayPart != null && !halfDayPart.isBlank() && !"FULL".equalsIgnoreCase(halfDayPart);
        }
    }

    /** Usable balance of one cover category for the month (Decision D4), in cover order. */
    public record CoverBalance(String family, String category, BigDecimal available) {
    }

    public record Adjustment(BigDecimal amount, String note) {
    }

    public record Employee(
            long employeeId,
            String employeeCode,
            String fullName,
            String defaultCurrency,
            LocalDate startDate,
            LocalDate exitDate,
            String scheduleMode) {
    }

    /** Everything the engine needs for one row. */
    public record Line(
            YearMonth period,
            LocalDate asOf,
            Rules rules,
            Employee employee,
            List<Salary> salaries,
            List<Day> days,
            List<Leave> leaves,
            List<CoverBalance> coverBalances,
            Adjustment adjustment) {
    }
}
