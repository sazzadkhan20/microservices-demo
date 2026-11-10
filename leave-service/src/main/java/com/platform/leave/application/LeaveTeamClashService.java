package com.platform.leave.application;

import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.domain.LeaveApplication;
import com.platform.leave.client.people.PeopleEmployeeGateway;
import com.platform.leave.infrastructure.persistence.LeaveApplicationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Team leave clash using People projection fields on {@link EmployeeView} (no local team table).
 * Limit is evaluated <b>per working day</b> in the request range (weekly offs / holidays excluded).
 */
@Service
public class LeaveTeamClashService {

    public record ClashResult(boolean blocked, boolean alert, String message) {}

    private final PeopleEmployeeGateway employeeRepository;
    private final LeaveApplicationRepository applicationRepository;

    public LeaveTeamClashService(
            PeopleEmployeeGateway employeeRepository,
            LeaveApplicationRepository applicationRepository) {
        this.employeeRepository = employeeRepository;
        this.applicationRepository = applicationRepository;
    }

    /**
     * @param workingDates applicant working days in the request (already excludes offs/holidays)
     */
    public ClashResult check(EmployeeView employee, LocalDate start, LocalDate end, List<LocalDate> workingDates) {
        if (employee.getTeamId() == null || start == null || end == null) {
            return new ClashResult(false, false, null);
        }
        int maxOnLeave = employee.getTeamMaxOnLeave() == null ? 0 : employee.getTeamMaxOnLeave();
        if (maxOnLeave <= 0) {
            return new ClashResult(false, false, null);
        }
        List<LocalDate> days = workingDates == null || workingDates.isEmpty()
                ? List.of()
                : workingDates;
        if (days.isEmpty()) {
            return new ClashResult(false, false, null);
        }
        List<Long> teammateIds = employeeRepository.findByTeamIdAndActiveTrue(employee.getTeamId()).stream()
                .map(EmployeeView::getId)
                .toList();
        if (teammateIds.isEmpty()) {
            return new ClashResult(false, false, null);
        }
        List<LeaveApplication> overlapping = applicationRepository.findOverlappingForEmployees(
                teammateIds, start, end, employee.getId());

        LocalDate peakDay = null;
        long peak = 0;
        for (LocalDate day : days) {
            Set<Long> onLeave = new HashSet<>();
            for (LeaveApplication a : overlapping) {
                if (a.getStartDate() != null && a.getEndDate() != null
                        && !day.isBefore(a.getStartDate()) && !day.isAfter(a.getEndDate())) {
                    onLeave.add(a.getEmployeeId());
                }
            }
            // Applicant would also be on leave this working day.
            long count = onLeave.size() + 1L;
            if (count > peak) {
                peak = count;
                peakDay = day;
            }
        }
        if (peak <= maxOnLeave) {
            return new ClashResult(false, false, null);
        }
        String action = employee.getTeamClashAction() == null
                ? "ALERT"
                : employee.getTeamClashAction().trim().toUpperCase(Locale.ROOT);
        String code = employee.getTeamCode() == null ? String.valueOf(employee.getTeamId()) : employee.getTeamCode();
        String msg = "Team clash: max_on_leave=" + maxOnLeave
                + " would be exceeded on " + peakDay
                + " for team " + code
                + " (would be " + peak + " people including you)";
        if ("BLOCK".equals(action)) {
            return new ClashResult(true, false, msg);
        }
        return new ClashResult(false, true, msg);
    }

    /** @deprecated prefer {@link #check(EmployeeView, LocalDate, LocalDate, List)} with working dates */
    @Deprecated
    public ClashResult check(EmployeeView employee, LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return new ClashResult(false, false, null);
        }
        List<LocalDate> all = new java.util.ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            all.add(d);
        }
        return check(employee, start, end, all);
    }

    public void enforce(EmployeeView employee, LocalDate start, LocalDate end) {
        ClashResult result = check(employee, start, end);
        if (result.blocked()) {
            throw new IllegalStateException(result.message());
        }
    }
}
