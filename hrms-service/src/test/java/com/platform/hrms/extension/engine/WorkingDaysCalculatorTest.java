package com.platform.hrms.extension.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qii BD week = SUN–THU (Q6). */
class WorkingDaysCalculatorTest {

    private final WorkingDaysCalculator calc = new WorkingDaysCalculator();
    private final List<String> bdWeek = List.of("SUN", "MON", "TUE", "WED", "THU");

    @Test
    void qii_bd_week_counts_sunday_not_friday() {
        // 2026-11-20 Fri, 21 Sat, 22 Sun
        LocalDate fri = LocalDate.of(2026, 11, 20);
        LocalDate sun = LocalDate.of(2026, 11, 22);
        assertEquals(0, calc.countWorkingDays(fri, fri, bdWeek));
        assertEquals(1, calc.countWorkingDays(sun, sun, bdWeek));
        assertTrue(calc.listWorkingDates(fri, sun, bdWeek).contains(sun));
        assertFalse(calc.listWorkingDates(fri, sun, bdWeek).contains(fri));
    }

    @Test
    void techshop_style_sat_thu_excludes_fri() {
        List<String> retail = List.of("SAT", "SUN", "MON", "TUE", "WED", "THU");
        assertEquals(0, calc.countWorkingDays(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20), retail));
        assertEquals(1, calc.countWorkingDays(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 21), retail));
    }
}
