package com.platform.hrms.extension.engine;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Counts leave days using tenant-configured working weekdays.
 * TechShop example: SAT–THU count; FRI does not (config, not hardcoded).
 */
@Component
public class WorkingDaysCalculator {

    public List<LocalDate> listWorkingDates(LocalDate start, LocalDate end, List<String> workingDayCodes) {
        return listWorkingDates(start, end, workingDayCodes, Set.of());
    }

    public List<LocalDate> listWorkingDates(LocalDate start, LocalDate end, List<String> workingDayCodes,
                                            Set<LocalDate> holidays) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new IllegalArgumentException("endDate must be on/after startDate");
        }
        Set<DayOfWeek> working = toDaySet(workingDayCodes);
        Set<LocalDate> holidaySet = holidays == null ? Set.of() : holidays;
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (working.contains(d.getDayOfWeek()) && !holidaySet.contains(d)) {
                days.add(d);
            }
        }
        return days;
    }

    public int countWorkingDays(LocalDate start, LocalDate end, List<String> workingDayCodes) {
        return listWorkingDates(start, end, workingDayCodes).size();
    }

    private Set<DayOfWeek> toDaySet(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            // Safe platform default if config missing
            return EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        }
        Set<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
        for (String code : codes) {
            set.add(parse(code));
        }
        return set;
    }

    private DayOfWeek parse(String code) {
        String c = code.trim().toUpperCase(Locale.ROOT);
        return switch (c) {
            case "MON", "MONDAY" -> DayOfWeek.MONDAY;
            case "TUE", "TUESDAY" -> DayOfWeek.TUESDAY;
            case "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY;
            case "THU", "THURSDAY" -> DayOfWeek.THURSDAY;
            case "FRI", "FRIDAY" -> DayOfWeek.FRIDAY;
            case "SAT", "SATURDAY" -> DayOfWeek.SATURDAY;
            case "SUN", "SUNDAY" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Unknown working day code in leave_policy.working_days: " + code);
        };
    }
}
