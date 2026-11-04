package com.platform.payroll.extension.engine;

import com.platform.payroll.domain.PayrollConfigEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollPeriodPolicyTest {

    @Test
    void qii_month_end_finalize_only_after_month() {
        PayrollPeriodPolicy p = PayrollPeriodPolicy.from(config(
                Map.of("type", "CONFIG", "key", "periodEndMode", "value", "MONTH_END")));
        assertEquals(LocalDate.of(2026, 10, 31), p.periodCloseDate("2026-10"));
        assertFalse(p.canFinalize("2026-10", LocalDate.of(2026, 10, 31)));
        assertTrue(p.canFinalize("2026-10", LocalDate.of(2026, 11, 1)));
    }

    @Test
    void other_business_day_25_finalize_on_or_after() {
        PayrollPeriodPolicy p = PayrollPeriodPolicy.from(config(
                Map.of("type", "CONFIG", "key", "periodEndMode", "value", "DAY_OF_MONTH"),
                Map.of("type", "CONFIG", "key", "periodEndDay", "value", 25)));
        assertEquals(LocalDate.of(2026, 10, 25), p.periodCloseDate("2026-10"));
        assertFalse(p.canFinalize("2026-10", LocalDate.of(2026, 10, 24)));
        assertTrue(p.canFinalize("2026-10", LocalDate.of(2026, 10, 25)));
        assertTrue(p.canFinalize("2026-10", LocalDate.of(2026, 10, 30)));
    }

    @Test
    void day_20_policy() {
        PayrollPeriodPolicy p = PayrollPeriodPolicy.from(config(
                Map.of("type", "CONFIG", "key", "periodEndMode", "value", "DAY_OF_MONTH"),
                Map.of("type", "CONFIG", "key", "periodEndDay", "value", 20)));
        assertEquals(LocalDate.of(2026, 11, 20), p.periodCloseDate("2026-11"));
        assertTrue(p.canFinalize("2026-11", LocalDate.of(2026, 11, 20)));
    }

    @SafeVarargs
    private static PayrollConfigEntity config(Map<String, Object>... rules) {
        PayrollConfigEntity e = new PayrollConfigEntity();
        e.setOvertimeMultiplier(new BigDecimal("1.5"));
        e.setWeekendMultiplier(new BigDecimal("2"));
        e.setDeductionRules(new ArrayList<>(List.of(rules)));
        return e;
    }
}
