package com.platform.timework.application;

import com.platform.timework.domain.WorkSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ShiftWindow — overnight bundle start→end→cutoff")
class ShiftWindowTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final int EARLY = 60;

    private static WorkSchedule overnight() {
        WorkSchedule s = new WorkSchedule();
        s.setMode("FIXED");
        s.setStartTime("21:00");
        s.setEndTime("05:00");
        s.setGraceMinutes(15);
        s.setCheckoutCutoff("11:00");
        return s;
    }

    private static WorkSchedule shortOvernight() {
        WorkSchedule s = new WorkSchedule();
        s.setMode("FIXED");
        s.setStartTime("00:00");
        s.setEndTime("03:00");
        s.setGraceMinutes(15);
        s.setCheckoutCutoff("03:50");
        return s;
    }

    @Test
    void overnightWindow_spansNextCalendarDay() {
        LocalDate work = LocalDate.of(2026, 11, 11);
        ShiftWindow w = ShiftWindow.forSchedule(work, overnight(), DHAKA, 15, "23:59");
        assertEquals(work.atTime(21, 0).atZone(DHAKA).toInstant(), w.startAt());
        assertEquals(work.plusDays(1).atTime(5, 0).atZone(DHAKA).toInstant(), w.endAt());
        assertEquals(work.plusDays(1).atTime(11, 0).atZone(DHAKA).toInstant().plusSeconds(59), w.cutoffAt());
    }

    @Test
    void checkInAt10pm_belongsToShiftStartDate() {
        LocalDate work = LocalDate.of(2026, 11, 11);
        ShiftWindow w = ShiftWindow.forSchedule(work, overnight(), DHAKA, 15, "23:59");
        Instant tenPm = work.atTime(22, 0).atZone(DHAKA).toInstant();
        assertTrue(w.allowsCheckIn(tenPm, EARLY));
        assertTrue(tenPm.isAfter(w.lateAfterAt())); // 22:00 > 21:15 → Late
    }

    @Test
    void checkInNextMorningBeforeCutoff_stillSameShift() {
        LocalDate work = LocalDate.of(2026, 11, 11);
        ShiftWindow w = ShiftWindow.forSchedule(work, overnight(), DHAKA, 15, "23:59");
        Instant sixAm = work.plusDays(1).atTime(6, 0).atZone(DHAKA).toInstant();
        assertTrue(w.allowsCheckIn(sixAm, EARLY));
        assertTrue(sixAm.isAfter(w.lateAfterAt()));
    }

    @Test
    void earlyCheckIn_opens60MinutesBeforeStart() {
        LocalDate work = LocalDate.of(2026, 11, 11);
        ShiftWindow w = ShiftWindow.forSchedule(work, overnight(), DHAKA, 15, "23:59");
        Instant openAt = work.atTime(20, 0).atZone(DHAKA).toInstant(); // 21:00 − 60m
        Instant tooEarly = work.atTime(19, 59).atZone(DHAKA).toInstant();
        assertTrue(w.allowsCheckIn(openAt, EARLY));
        assertFalse(w.allowsCheckIn(tooEarly, EARLY));
        assertTrue(w.isBeforeCheckInOpen(tooEarly, EARLY));
        assertTrue(w.isBeforeStart(openAt)); // still before official start
    }

    @Test
    void beforeEarlyOpen_notAllowed() {
        LocalDate work = LocalDate.of(2026, 11, 11);
        ShiftWindow w = ShiftWindow.forSchedule(work, overnight(), DHAKA, 15, "23:59");
        Instant sevenPm = work.atTime(19, 0).atZone(DHAKA).toInstant();
        assertTrue(w.isBeforeStart(sevenPm));
        assertFalse(w.allowsCheckIn(sevenPm, EARLY));
    }

    @Test
    void shortOvernight_afterCutoff_noCheckInOrCheckOut() {
        LocalDate work = LocalDate.of(2026, 11, 12);
        ShiftWindow w = ShiftWindow.forSchedule(work, shortOvernight(), DHAKA, 15, "23:59");
        Instant afterCutoff = work.atTime(3, 51).atZone(DHAKA).toInstant();
        assertTrue(w.isPastCutoff(afterCutoff));
        assertFalse(w.allowsCheckIn(afterCutoff, EARLY));
        assertFalse(w.allowsCheckOut(afterCutoff));
    }

    @Test
    void afterCutoff_holidayDayCanOpenNewShift() {
        LocalDate work = LocalDate.of(2026, 11, 11);
        ShiftWindow prior = ShiftWindow.forSchedule(work, overnight(), DHAKA, 15, "23:59");
        Instant noon = work.plusDays(1).atTime(12, 0).atZone(DHAKA).toInstant();
        assertTrue(prior.isPastCutoff(noon));

        WorkSchedule day = new WorkSchedule();
        day.setMode("FIXED");
        day.setStartTime("09:00");
        day.setEndTime("17:00");
        day.setGraceMinutes(15);
        day.setCheckoutCutoff("23:00");
        ShiftWindow holidayDay = ShiftWindow.forSchedule(work.plusDays(1), day, DHAKA, 15, "23:59");
        assertTrue(holidayDay.allowsCheckIn(noon, EARLY));
    }
}
