package com.platform.leave.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single source of truth for leave day amounts (balances, quotas, apply checks, API, UI).
 *
 * <p>Balances and day counts are quantized to the nearest half-day step:
 * {@code 0, 0.5, 1, 1.5, 2, …}. Examples: {@code 2.79 → 3}, {@code 2.3 → 2.5},
 * {@code 0.5 → 0.5}.
 *
 * <p>Negative-cap overdraft allowance stays whole days ({@link #whole(BigDecimal)}).
 */
public final class LeaveDays {

    public static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal TWO = new BigDecimal("2");

    private LeaveDays() {}

    /**
     * Quantize to nearest 0.5 (HALF_UP). Null → 0.
     * Result scale is 0 for whole days and 1 for *.5 so JSON/UI stay clean.
     */
    public static BigDecimal of(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal stepped = v.multiply(TWO)
                .setScale(0, RoundingMode.HALF_UP)
                .divide(TWO, 1, RoundingMode.HALF_UP);
        if (stepped.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
            return stepped.setScale(0, RoundingMode.UNNECESSARY);
        }
        return stepped;
    }

    /** Whole days only (negative-cap overdraft). Null → 0. */
    public static BigDecimal whole(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        return v.setScale(0, RoundingMode.HALF_UP);
    }

    /** Plain string for errors/logs: {@code 3} or {@code 3.5}, never {@code 3.00}. */
    public static String format(BigDecimal v) {
        BigDecimal q = of(v);
        return q.stripTrailingZeros().toPlainString();
    }

    /** @deprecated use {@link #of(BigDecimal)} — kept during migration of call sites */
    @Deprecated
    public static BigDecimal wholeDown(BigDecimal v) {
        return of(v);
    }
}
