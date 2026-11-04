package com.platform.payroll.extension.engine;

import com.platform.payroll.domain.PayrollConfigEntity;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Config-driven pay-period close + finalize eligibility.
 *
 * <p>Keys in {@code payroll_config.deduction_rules} (type=CONFIG):
 * <ul>
 *   <li>{@code periodEndMode} — {@code MONTH_END} (default) or {@code DAY_OF_MONTH}</li>
 *   <li>{@code periodEndDay} — 1..28 when mode is DAY_OF_MONTH (e.g. 20 or 25)</li>
 * </ul>
 *
 * <p>Qii: MONTH_END → finalize only after the calendar month ends.
 * Other packs: DAY_OF_MONTH + 20/25 → finalize on/after that day in the period month.
 */
public final class PayrollPeriodPolicy {

    public static final String MODE_MONTH_END = "MONTH_END";
    public static final String MODE_DAY_OF_MONTH = "DAY_OF_MONTH";

    private final String periodEndMode;
    private final int periodEndDay;

    private PayrollPeriodPolicy(String periodEndMode, int periodEndDay) {
        this.periodEndMode = periodEndMode;
        this.periodEndDay = periodEndDay;
    }

    public static PayrollPeriodPolicy from(PayrollConfigEntity config) {
        String mode = configStr(config, "periodEndMode", MODE_MONTH_END).toUpperCase(Locale.ROOT);
        if (!MODE_DAY_OF_MONTH.equals(mode)) {
            // MONTH_END: periodEndDay is informational only (unused for close date).
            return new PayrollPeriodPolicy(MODE_MONTH_END, configInt(config, "periodEndDay", 31));
        }
        int day = configInt(config, "periodEndDay", 25);
        if (day < 1) day = 1;
        if (day > 28) day = 28; // safe for Feb across all years
        return new PayrollPeriodPolicy(MODE_DAY_OF_MONTH, day);
    }

    public String periodEndMode() {
        return periodEndMode;
    }

    public int periodEndDay() {
        return periodEndDay;
    }

    /** Last inclusive day of the pay period for the given YYYY-MM label. */
    public LocalDate periodCloseDate(String periodLabel) {
        YearMonth ym = YearMonth.parse(periodLabel.substring(0, 7));
        if (MODE_DAY_OF_MONTH.equals(periodEndMode)) {
            int dom = Math.min(periodEndDay, ym.lengthOfMonth());
            return ym.atDay(dom);
        }
        return ym.atEndOfMonth();
    }

    /**
     * Whether a draft payslip for {@code periodLabel} may be finalized on {@code today}.
     * MONTH_END: today must be after month end. DAY_OF_MONTH: today on/after period close day.
     */
    public boolean canFinalize(String periodLabel, LocalDate today) {
        if (periodLabel == null || periodLabel.length() < 7 || today == null) {
            return false;
        }
        LocalDate close = periodCloseDate(periodLabel);
        if (MODE_DAY_OF_MONTH.equals(periodEndMode)) {
            return !today.isBefore(close);
        }
        return today.isAfter(close);
    }

    public String finalizeBlockedReason(String periodLabel, LocalDate today) {
        LocalDate close = periodCloseDate(periodLabel);
        if (MODE_DAY_OF_MONTH.equals(periodEndMode)) {
            return "Cannot finalize: period " + periodLabel.substring(0, 7)
                    + " closes on day " + periodEndDay + " (closeDate=" + close
                    + ", today=" + today + ")";
        }
        return "Cannot finalize: month " + periodLabel.substring(0, 7)
                + " has not ended yet (today=" + today + ", monthEnd=" + close + ")";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("periodEndMode", periodEndMode);
        m.put("periodEndDay", periodEndDay);
        m.put("description", MODE_DAY_OF_MONTH.equals(periodEndMode)
                ? "Finalize on/after day " + periodEndDay + " of the period month"
                : "Finalize only after the calendar month ends");
        return m;
    }

    private static String configStr(PayrollConfigEntity config, String key, String def) {
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

    private static int configInt(PayrollConfigEntity config, String key, int def) {
        String v = configStr(config, key, null);
        if (v == null) return def;
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            return def;
        }
    }

    /** Ensure defaults exist in a rules list (idempotent merge for loaders/seeders). */
    public static void ensureDefaults(List<Map<String, Object>> rules, String mode, int day) {
        if (rules == null) return;
        putIfAbsent(rules, "periodEndMode", mode);
        putIfAbsent(rules, "periodEndDay", day);
    }

    private static void putIfAbsent(List<Map<String, Object>> rules, String key, Object value) {
        for (Map<String, Object> rule : rules) {
            if ("CONFIG".equalsIgnoreCase(String.valueOf(rule.getOrDefault("type", "")))
                    && key.equals(String.valueOf(rule.get("key")))) {
                return;
            }
        }
        rules.add(Map.of("type", "CONFIG", "key", key, "value", value));
    }
}
