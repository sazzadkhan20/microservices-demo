package com.platform.payroll.extension.engine;

import com.platform.payroll.domain.PayrollConfigEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared salary engine — multipliers come from tenant config, not tenant-specific classes.
 */
@Component
public class SalaryCalcEngine {

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

    public SalaryResult calculate(
            PayrollConfigEntity config,
            BigDecimal hourlyRate,
            BigDecimal regularHours,
            BigDecimal overtimeHours,
            BigDecimal weekendHours) {

        BigDecimal otMul = config.getOvertimeMultiplier();
        BigDecimal weMul = config.getWeekendMultiplier();

        BigDecimal regularPay = hourlyRate.multiply(regularHours).setScale(4, RoundingMode.HALF_UP);
        BigDecimal overtimePay = hourlyRate.multiply(otMul).multiply(overtimeHours).setScale(4, RoundingMode.HALF_UP);
        BigDecimal weekendPay = hourlyRate.multiply(weMul).multiply(weekendHours).setScale(4, RoundingMode.HALF_UP);
        BigDecimal gross = regularPay.add(overtimePay).add(weekendPay);

        Map<String, Object> details = new HashMap<>();
        details.put("hourlyRate", hourlyRate);
        details.put("regularHours", regularHours);
        details.put("overtimeHours", overtimeHours);
        details.put("weekendHours", weekendHours);
        details.put("overtimeMultiplier", otMul);
        details.put("weekendMultiplier", weMul);

        return new SalaryResult(otMul, weMul, regularPay, overtimePay, weekendPay, gross, details);
    }
}
