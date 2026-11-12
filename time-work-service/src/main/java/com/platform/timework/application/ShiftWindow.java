package com.platform.timework.application;

import com.platform.timework.domain.WorkSchedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * One FIXED (or fallback) duty window anchored on {@link #workDate()} — the shift-start
 * calendar date in the employee location zone. End / cut-off may fall on the next day.
 * Bundle: {@code [checkInOpensAt → startAt → endAt → cutoffAt]} (inclusive through end of cut-off minute).
 */
public record ShiftWindow(
        LocalDate workDate,
        Instant startAt,
        Instant endAt,
        Instant lateAfterAt,
        Instant cutoffAt,
        boolean fixedHours
) {

    /**
     * Instant when check-in first becomes available ({@code startAt} minus early minutes).
     */
    public Instant checkInOpensAt(int earlyMinutes) {
        int early = Math.max(0, earlyMinutes);
        return startAt.minusSeconds(early * 60L);
    }

    /** Check-in allowed from (start − early) through cut-off (inclusive). */
    public boolean allowsCheckIn(Instant now, int earlyMinutes) {
        return now != null
                && !now.isBefore(checkInOpensAt(earlyMinutes))
                && !now.isAfter(cutoffAt);
    }

    /** Open punch may still be checked out through cut-off. */
    public boolean allowsCheckOut(Instant now) {
        return now != null && !now.isAfter(cutoffAt);
    }

    public boolean isBeforeStart(Instant now) {
        return now != null && now.isBefore(startAt);
    }

    /** True when {@code now} is before the early check-in open instant. */
    public boolean isBeforeCheckInOpen(Instant now, int earlyMinutes) {
        return now != null && now.isBefore(checkInOpensAt(earlyMinutes));
    }

    public boolean isPastCutoff(Instant now) {
        return now != null && now.isAfter(cutoffAt);
    }

    /**
     * Build window for a shift-start date. FIXED uses schedule start/end/cutoff/grace;
     * HOURLY / missing hours → local calendar day until default cut-off (23:59).
     */
    public static ShiftWindow forSchedule(
            LocalDate workDate,
            WorkSchedule schedule,
            ZoneId zone,
            int defaultGraceMinutes,
            String defaultCheckoutCutoff
    ) {
        if (workDate == null || zone == null) {
            return null;
        }
        boolean hourly = schedule != null && "HOURLY".equalsIgnoreCase(schedule.getMode());
        String startRaw = schedule == null ? null : schedule.getStartTime();
        String endRaw = schedule == null ? null : schedule.getEndTime();
        if (hourly || startRaw == null || startRaw.isBlank() || endRaw == null || endRaw.isBlank()) {
            LocalTime cutoff;
            try {
                String raw = schedule != null && schedule.getCheckoutCutoff() != null
                        && !schedule.getCheckoutCutoff().isBlank()
                        ? schedule.getCheckoutCutoff().trim()
                        : (defaultCheckoutCutoff == null || defaultCheckoutCutoff.isBlank()
                        ? "23:59" : defaultCheckoutCutoff.trim());
                cutoff = LocalTime.parse(raw);
            } catch (Exception e) {
                cutoff = LocalTime.of(23, 59);
            }
            Instant startAt = workDate.atStartOfDay(zone).toInstant();
            Instant endAt = workDate.atTime(LocalTime.of(23, 59)).atZone(zone).toInstant();
            Instant cutoffAt = workDate.atTime(cutoff).atZone(zone).toInstant().plusSeconds(59);
            return new ShiftWindow(workDate, startAt, endAt, startAt, cutoffAt, false);
        }
        LocalTime start;
        LocalTime end;
        try {
            start = LocalTime.parse(startRaw.trim());
            end = LocalTime.parse(endRaw.trim());
        } catch (Exception e) {
            return null;
        }
        if (start.equals(end)) {
            return null;
        }
        int grace = schedule.getGraceMinutes() == null ? defaultGraceMinutes : schedule.getGraceMinutes();
        if (grace < 0) {
            grace = 0;
        }
        LocalTime cutoffClock;
        try {
            String raw = schedule.getCheckoutCutoff() == null || schedule.getCheckoutCutoff().isBlank()
                    ? (defaultCheckoutCutoff == null || defaultCheckoutCutoff.isBlank()
                    ? "23:59" : defaultCheckoutCutoff)
                    : schedule.getCheckoutCutoff().trim();
            cutoffClock = LocalTime.parse(raw.trim());
        } catch (Exception e) {
            cutoffClock = LocalTime.of(23, 59);
        }
        Instant startAt = workDate.atTime(start).atZone(zone).toInstant();
        Instant endAt = WorkScheduleService.shiftEndInstant(workDate, start, end, zone);
        Instant lateAfterAt = startAt.plusSeconds(grace * 60L);
        Instant cutoffAt = WorkScheduleService.checkoutCutoffInstant(workDate, start, end, cutoffClock, zone)
                .plusSeconds(59);
        return new ShiftWindow(workDate, startAt, endAt, lateAfterAt, cutoffAt, true);
    }
}
