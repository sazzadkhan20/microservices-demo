package com.platform.timework.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Checkout cutoff — always after shift end (same or next day)")
class CheckoutCutoffInstantTest {

    private static final ZoneId DARWIN = ZoneId.of("Australia/Darwin");
    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

    @Test
    void sameDayCutoff_afterAfternoonEnd() {
        LocalDate work = LocalDate.of(2026, 11, 12);
        Instant cutoff = WorkScheduleService.checkoutCutoffInstant(
                work, LocalTime.of(9, 0), LocalTime.of(17, 0), LocalTime.of(23, 0), DARWIN);
        assertEquals(
                work.atTime(23, 0).atZone(DARWIN).toInstant(),
                cutoff);
        Instant end = WorkScheduleService.shiftEndInstant(
                work, LocalTime.of(9, 0), LocalTime.of(17, 0), DARWIN);
        assertTrue(!cutoff.isBefore(end));
    }

    @Test
    void nextDayCutoff_afterEveningEnd() {
        // TEAM-style: 12:00–20:00, cutoff 02:00 → next calendar morning
        LocalDate work = LocalDate.of(2026, 11, 12);
        Instant cutoff = WorkScheduleService.checkoutCutoffInstant(
                work, LocalTime.of(12, 0), LocalTime.of(20, 0), LocalTime.of(2, 0), DHAKA);
        assertEquals(
                work.plusDays(1).atTime(2, 0).atZone(DHAKA).toInstant(),
                cutoff);
        Instant end = WorkScheduleService.shiftEndInstant(
                work, LocalTime.of(12, 0), LocalTime.of(20, 0), DHAKA);
        assertTrue(cutoff.isAfter(end));
    }

    @Test
    void overnightShift_endAndCutoffOnNextCalendarDay() {
        LocalDate work = LocalDate.of(2026, 11, 12);
        Instant end = WorkScheduleService.shiftEndInstant(
                work, LocalTime.of(22, 0), LocalTime.of(6, 0), DARWIN);
        assertEquals(work.plusDays(1).atTime(6, 0).atZone(DARWIN).toInstant(), end);

        Instant cutoff = WorkScheduleService.checkoutCutoffInstant(
                work, LocalTime.of(22, 0), LocalTime.of(6, 0), LocalTime.of(8, 0), DARWIN);
        assertEquals(work.plusDays(1).atTime(8, 0).atZone(DARWIN).toInstant(), cutoff);
        assertTrue(!cutoff.isBefore(end));
    }

    @Test
    void cutoffEqualToEnd_isAllowed() {
        LocalDate work = LocalDate.of(2026, 11, 12);
        Instant cutoff = WorkScheduleService.checkoutCutoffInstant(
                work, LocalTime.of(9, 0), LocalTime.of(17, 0), LocalTime.of(17, 0), DHAKA);
        assertEquals(work.atTime(17, 0).atZone(DHAKA).toInstant(), cutoff);
    }

    @Test
    void validateCutoffWindow_allowsOvernightCutoff() {
        WorkScheduleService.validateCutoffWindow(
                LocalTime.of(12, 0), LocalTime.of(20, 0), LocalTime.of(2, 0));
        WorkScheduleService.validateCutoffWindow(
                LocalTime.of(22, 0), LocalTime.of(6, 0), LocalTime.of(8, 0));
    }
}
