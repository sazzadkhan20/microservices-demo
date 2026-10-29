package com.platform.payroll.extension.engine;

import com.platform.payroll.domain.PayrollConfigEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Applies config-driven deduction rules.
 * Supported types: FIXED, PERCENT_GROSS, CONFIG (absenceCoverOrder / unpaidAlwaysDeduct / dayDivisor).
 */
@Component
public class DeductionRuleEngine {

    public record DeductionResult(BigDecimal total, List<Map<String, Object>> applied) {
    }

    public record AbsenceCoverResult(
            List<String> coverOrder,
            Map<String, BigDecimal> consumedByType,
            BigDecimal unpaidDays,
            List<Map<String, Object>> steps
    ) {
    }

    public DeductionResult apply(PayrollConfigEntity config, BigDecimal gross) {
        return apply(config, gross, BigDecimal.ZERO, Map.of());
    }

    /**
     * @param unpaidAbsenceDays  calendar unpaid days already known (no leave balance)
     * @param availableBalances  leaveType → remaining days (e.g. CASUAL→5, ANNUAL→10)
     */
    public DeductionResult apply(PayrollConfigEntity config,
                                 BigDecimal gross,
                                 BigDecimal unpaidAbsenceDays,
                                 Map<String, BigDecimal> availableBalances) {
        BigDecimal total = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        List<Map<String, Object>> applied = new ArrayList<>();

        AbsenceCoverResult cover = resolveAbsenceCover(config, unpaidAbsenceDays, availableBalances);
        if (cover.unpaidDays().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dayRate = dayRateFromGross(config, gross);
            BigDecimal unpaidDeduction = cover.unpaidDays().multiply(dayRate).setScale(4, RoundingMode.HALF_UP);
            total = total.add(unpaidDeduction);
            Map<String, Object> row = new HashMap<>();
            row.put("label", "unpaid_absence");
            row.put("type", "ABSENCE_COVER");
            row.put("amount", unpaidDeduction);
            row.put("unpaidDays", cover.unpaidDays());
            row.put("coverOrder", cover.coverOrder());
            row.put("consumedByType", cover.consumedByType());
            row.put("steps", cover.steps());
            applied.add(row);
        }

        for (Map<String, Object> rule : config.getDeductionRules()) {
            String type = String.valueOf(rule.getOrDefault("type", "")).toUpperCase(Locale.ROOT);
            if ("CONFIG".equals(type) || "ABSENCE_COVER".equals(type)) {
                continue;
            }
            String label = String.valueOf(rule.getOrDefault("label", "deduction"));
            BigDecimal amount = BigDecimal.ZERO;

            if ("FIXED".equals(type)) {
                amount = new BigDecimal(String.valueOf(rule.get("amount"))).setScale(4, RoundingMode.HALF_UP);
            } else if ("PERCENT_GROSS".equals(type)) {
                BigDecimal percent = new BigDecimal(String.valueOf(rule.get("percent")));
                amount = gross.multiply(percent).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            } else {
                continue;
            }

            total = total.add(amount);
            Map<String, Object> row = new HashMap<>();
            row.put("label", label);
            row.put("type", type);
            row.put("amount", amount);
            applied.add(row);
        }
        return new DeductionResult(total, applied);
    }

    /**
     * Walks absenceCoverOrder (e.g. CASUAL → ANNUAL) consuming balances first;
     * remainder becomes unpaid days (deducted when unpaidAlwaysDeduct=true).
     */
    public AbsenceCoverResult resolveAbsenceCover(PayrollConfigEntity config,
                                                  BigDecimal absenceDays,
                                                  Map<String, BigDecimal> availableBalances) {
        List<String> order = absenceCoverOrder(config);
        Map<String, BigDecimal> balances = availableBalances == null ? Map.of() : availableBalances;
        BigDecimal remaining = absenceDays == null ? BigDecimal.ZERO : absenceDays.max(BigDecimal.ZERO);
        Map<String, BigDecimal> consumed = new HashMap<>();
        List<Map<String, Object>> steps = new ArrayList<>();

        for (String typeCode : order) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal avail = balances.getOrDefault(typeCode.toUpperCase(Locale.ROOT), BigDecimal.ZERO);
            if (avail.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal use = avail.min(remaining);
            consumed.put(typeCode.toUpperCase(Locale.ROOT), use);
            remaining = remaining.subtract(use);
            steps.add(Map.of(
                    "leaveType", typeCode.toUpperCase(Locale.ROOT),
                    "consumed", use,
                    "remainingAbsence", remaining
            ));
        }

        boolean unpaidAlways = unpaidAlwaysDeduct(config);
        BigDecimal unpaid = unpaidAlways ? remaining : BigDecimal.ZERO;
        return new AbsenceCoverResult(order, consumed, unpaid, steps);
    }

    public List<String> absenceCoverOrder(PayrollConfigEntity config) {
        for (Map<String, Object> rule : config.getDeductionRules()) {
            if (!"CONFIG".equalsIgnoreCase(String.valueOf(rule.getOrDefault("type", "")))) {
                continue;
            }
            if (!"absenceCoverOrder".equals(String.valueOf(rule.get("key")))) {
                continue;
            }
            Object value = rule.get("value");
            List<String> out = new ArrayList<>();
            if (value instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null) {
                        out.add(String.valueOf(o).trim().toUpperCase(Locale.ROOT));
                    }
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        // Platform default Qii-style: Casual then Annual
        return List.of("CASUAL", "ANNUAL");
    }

    private boolean unpaidAlwaysDeduct(PayrollConfigEntity config) {
        for (Map<String, Object> rule : config.getDeductionRules()) {
            if (!"CONFIG".equalsIgnoreCase(String.valueOf(rule.getOrDefault("type", "")))) {
                continue;
            }
            if ("unpaidAlwaysDeduct".equals(String.valueOf(rule.get("key")))) {
                return Boolean.parseBoolean(String.valueOf(rule.get("value")));
            }
        }
        return true;
    }

    private BigDecimal dayRateFromGross(PayrollConfigEntity config, BigDecimal gross) {
        int divisor = 22;
        for (Map<String, Object> rule : config.getDeductionRules()) {
            if (!"CONFIG".equalsIgnoreCase(String.valueOf(rule.getOrDefault("type", "")))) {
                continue;
            }
            if ("dayDivisor".equals(String.valueOf(rule.get("key")))) {
                String v = String.valueOf(rule.get("value"));
                if ("WORKING_DAYS".equalsIgnoreCase(v) || "22".equals(v)) {
                    divisor = 22;
                } else {
                    try {
                        divisor = Integer.parseInt(v);
                    } catch (NumberFormatException ignored) {
                        divisor = 22;
                    }
                }
            }
        }
        if (gross == null || gross.compareTo(BigDecimal.ZERO) <= 0 || divisor <= 0) {
            return BigDecimal.ZERO;
        }
        return gross.divide(BigDecimal.valueOf(divisor), 4, RoundingMode.HALF_UP);
    }
}
