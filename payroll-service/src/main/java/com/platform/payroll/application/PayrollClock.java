package com.platform.payroll.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Company calendar for payroll period boundaries (guide §5 — preserve location time zones).
 * {@code payroll.close-time-zone} must be the company's westernmost location (Qii: Asia/Dhaka, behind
 * Australia/Darwin) so "the month has ended" is true in every location (BR-18).
 */
@Component
public class PayrollClock {

    private final ZoneId zone;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PayrollClock(@Value("${payroll.close-time-zone:Asia/Dhaka}") String zone) {
        this(ZoneId.of(zone), Clock.systemUTC());
    }

    PayrollClock(ZoneId zone, Clock clock) {
        this.zone = zone;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public YearMonth currentPeriod() {
        return YearMonth.from(today());
    }

    public boolean monthEnded(YearMonth period) {
        return today().isAfter(period.atEndOfMonth());
    }

    public ZoneId zone() {
        return zone;
    }

    /** Parses and validates {@code YYYY-MM}. */
    public static YearMonth parse(String period) {
        if (period == null || !period.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new PayrollException(400, "INVALID_PERIOD", "Period must be YYYY-MM");
        }
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw new PayrollException(400, "INVALID_PERIOD", "Period must be YYYY-MM");
        }
    }
}
