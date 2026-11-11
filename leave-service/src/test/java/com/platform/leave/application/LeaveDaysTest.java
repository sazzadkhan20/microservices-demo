package com.platform.leave.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaveDaysTest {

    @Test
    void quantizesToNearestHalfDay() {
        assertEquals(0, new BigDecimal("3").compareTo(LeaveDays.of(new BigDecimal("2.79"))));
        assertEquals(0, new BigDecimal("2.5").compareTo(LeaveDays.of(new BigDecimal("2.3"))));
        assertEquals(0, new BigDecimal("2.5").compareTo(LeaveDays.of(new BigDecimal("2.25"))));
        assertEquals(0, new BigDecimal("3").compareTo(LeaveDays.of(new BigDecimal("2.75"))));
        assertEquals(0, new BigDecimal("0.5").compareTo(LeaveDays.of(new BigDecimal("0.5"))));
        assertEquals(0, new BigDecimal("1").compareTo(LeaveDays.of(new BigDecimal("1"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(LeaveDays.of(null)));
    }

    @Test
    void wholeDaysForNegativeCapOnly() {
        assertEquals(0, new BigDecimal("2").compareTo(LeaveDays.whole(new BigDecimal("2.4"))));
        assertEquals(0, new BigDecimal("3").compareTo(LeaveDays.whole(new BigDecimal("2.5"))));
    }

    @Test
    void formatOmitsTrailingZero() {
        assertEquals("3", LeaveDays.format(new BigDecimal("2.79")));
        assertEquals("2.5", LeaveDays.format(new BigDecimal("2.3")));
        assertEquals("0.5", LeaveDays.format(new BigDecimal("0.5")));
    }
}
