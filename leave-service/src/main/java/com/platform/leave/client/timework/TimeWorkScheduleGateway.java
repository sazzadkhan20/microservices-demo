package com.platform.leave.client.timework;

import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.client.timework.ScheduleView;
import com.platform.leave.client.TimeWorkClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Leave-side schedule/holiday reads via Time&amp;Work HTTP (guide §3 — no local schedule/holiday tables).
 */
@Service
public class TimeWorkScheduleGateway {

    private final TimeWorkClient timeWorkClient;

    public TimeWorkScheduleGateway(TimeWorkClient timeWorkClient) {
        this.timeWorkClient = timeWorkClient;
    }

    public Optional<ScheduleView> resolveForEmployee(EmployeeView emp) {
        if (emp == null || emp.getId() == null) {
            return Optional.empty();
        }
        var remote = timeWorkClient.resolveSchedule(emp.getId());
        String mode = remote.get("mode") == null ? "" : String.valueOf(remote.get("mode")).trim();
        if (mode.isEmpty() && !(remote.get("workingDays") instanceof List<?> days && !days.isEmpty())) {
            return Optional.empty();
        }
        ScheduleView s = new ScheduleView();
        s.setMode(mode.isEmpty() ? "FIXED" : mode);
        return Optional.of(s);
    }

    public List<String> workingDayCodesForEmployee(EmployeeView emp) {
        if (emp == null || emp.getId() == null) {
            return List.of();
        }
        return timeWorkClient.workingDayCodes(emp.getId());
    }

    public boolean isHoliday(Long locationId, LocalDate day) {
        return timeWorkClient.isHoliday(locationId, day);
    }

    public Set<LocalDate> holidaysInRange(Long locationId, LocalDate from, LocalDate to) {
        return timeWorkClient.holidaysInRange(locationId, from, to);
    }
}
