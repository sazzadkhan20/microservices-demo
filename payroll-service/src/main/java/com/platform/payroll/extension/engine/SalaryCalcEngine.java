package com.platform.payroll.extension.engine;

import com.platform.payroll.domain.PayrollConfigEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared salary engine — multipliers and monthly-hours divisor come from tenant config,
 * not tenant-specific classes. Works for hourly or monthly-salaried employees.
 */
@Component
public class SalaryCalcEngine {

    /** Default working hours in a month when payroll_config does not define monthlyHours. */
    public static final BigDecimal DEFAULT_MONTHLY_HOURS = new BigDecimal("176");

    public record SalaryResult(
            BigDecimal overtimeMultiplier,
            BigDecimal weekendMultiplier,
            BigDecimal regularPay,
            BigDecimal overtimePay,
            BigDecimal weekendPay,
            BigDecimal gross,
            Map<String, Object> details
    ) {
    }

    /**
     * Resolves an effective hourly rate from employee facts + config.
     * Prefer explicit hourlyRate; otherwise derive from baseSalary / monthlyHours (config-driven).
     */
    public BigDecimal resolveHourlyRate(
            PayrollConfigEntity config,
            BigDecimal hourlyRate,
            BigDecimal baseSalary) {
        if (hourlyRate != null && hourlyRate.compareTo(BigDecimal.ZERO) > 0) {
            return hourlyRate;
        }
        if (baseSalary != null && baseSalary.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal monthlyHours = monthlyHours(config);
            if (monthlyHours.compareTo(BigDecimal.ZERO) <= 0) {
                monthlyHours = DEFAULT_MONTHLY_HOURS;
            }
            return baseSalary.divide(monthlyHours, 6, RoundingMode.HALF_UP);
        }
        return hourlyRate == null ? BigDecimal.ZERO : hourlyRate;
    }

    public BigDecimal monthlyHours(PayrollConfigEntity config) {
        if (config == null || config.getDeductionRules() == null) {
            return DEFAULT_MONTHLY_HOURS;
        }
        for (Map<String, Object> rule : config.getDeductionRules()) {
            if (rule == null) continue;
            Object key = rule.get("key");
            if (key != null && "monthlyHours".equalsIgnoreCase(String.valueOf(key))) {
                Object value = rule.get("value");
                if (value != null) {
                    try {
                        return new BigDecimal(String.valueOf(value));
                    } catch (NumberFormatException ignored) {
                        return DEFAULT_MONTHLY_HOURS;
                    }
                }
            }
        }
        return DEFAULT_MONTHLY_HOURS;
    }

    public SalaryResult calculate(
            PayrollConfigEntity config,
            BigDecimal hourlyRate,
            BigDecimal regularHours,
            BigDecimal overtimeHours,
            BigDecimal weekendHours) {
        return calculate(config, hourlyRate, null, regularHours, overtimeHours, weekendHours);
    }

    public SalaryResult calculate(
            PayrollConfigEntity config,
            BigDecimal hourlyRate,
            BigDecimal baseSalary,
            BigDecimal regularHours,
            BigDecimal overtimeHours,
            BigDecimal weekendHours) {

        BigDecimal rate = resolveHourlyRate(config, hourlyRate, baseSalary);
        BigDecimal otMul = config.getOvertimeMultiplier();
        BigDecimal weMul = config.getWeekendMultiplier();
        BigDecimal regH = regularHours == null ? BigDecimal.ZERO : regularHours;
        BigDecimal otH = overtimeHours == null ? BigDecimal.ZERO : overtimeHours;
        BigDecimal weH = weekendHours == null ? BigDecimal.ZERO : weekendHours;

        // Monthly-salaried / base-salary employees with no hours (e.g. HRMS attendance empty):
        // pay full base for the period. Config-driven — any tenant using baseSalary benefits.
        boolean monthlyFallback = baseSalary != null && baseSalary.compareTo(BigDecimal.ZERO) > 0
                && regH.compareTo(BigDecimal.ZERO) == 0
                && otH.compareTo(BigDecimal.ZERO) == 0
                && weH.compareTo(BigDecimal.ZERO) == 0;

        BigDecimal regularPay;
        BigDecimal overtimePay;
        BigDecimal weekendPay;
        BigDecimal gross;
        String payMode;
        if (monthlyFallback) {
            regularPay = baseSalary.setScale(4, RoundingMode.HALF_UP);
            overtimePay = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            weekendPay = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            gross = regularPay;
            payMode = "MONTHLY_BASE";
        } else {
            regularPay = rate.multiply(regH).setScale(4, RoundingMode.HALF_UP);
            overtimePay = rate.multiply(otMul).multiply(otH).setScale(4, RoundingMode.HALF_UP);
            weekendPay = rate.multiply(weMul).multiply(weH).setScale(4, RoundingMode.HALF_UP);
            gross = regularPay.add(overtimePay).add(weekendPay);
            payMode = (hourlyRate != null && hourlyRate.compareTo(BigDecimal.ZERO) > 0)
                    ? "HOURLY" : "DERIVED_HOURLY_FROM_BASE";
        }

        Map<String, Object> details = new HashMap<>();
        details.put("hourlyRate", rate);
        details.put("baseSalary", baseSalary);
        details.put("monthlyHours", monthlyHours(config));
        details.put("payMode", payMode);
        details.put("regularHours", regH);
        details.put("overtimeHours", otH);
        details.put("weekendHours", weH);
        details.put("overtimeMultiplier", otMul);
        details.put("weekendMultiplier", weMul);

        return new SalaryResult(otMul, weMul, regularPay, overtimePay, weekendPay, gross, details);
    }
}
