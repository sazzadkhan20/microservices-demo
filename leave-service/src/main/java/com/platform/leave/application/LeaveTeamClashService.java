package com.platform.leave.application;

import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.domain.LeaveApplication;
import com.platform.leave.client.people.PeopleEmployeeGateway;
import com.platform.leave.infrastructure.persistence.LeaveApplicationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Team leave clash using People projection fields on {@link EmployeeView} (no local team table).
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

    public ClashResult check(EmployeeView employee, LocalDate start, LocalDate end) {
        if (employee.getTeamId() == null) {
            return new ClashResult(false, false, null);
        }
        int maxOnLeave = employee.getTeamMaxOnLeave() == null ? 0 : employee.getTeamMaxOnLeave();
        if (maxOnLeave <= 0) {
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
        long distinctOnLeave = overlapping.stream().map(LeaveApplication::getEmployeeId).distinct().count();
        if (distinctOnLeave < maxOnLeave) {
            return new ClashResult(false, false, null);
        }
        String action = employee.getTeamClashAction() == null
                ? "ALERT"
                : employee.getTeamClashAction().trim().toUpperCase(Locale.ROOT);
        String code = employee.getTeamCode() == null ? String.valueOf(employee.getTeamId()) : employee.getTeamCode();
        String msg = "Team clash: max_on_leave=" + maxOnLeave + " already reached for team " + code;
        if ("BLOCK".equals(action)) {
            return new ClashResult(true, false, msg);
        }
        return new ClashResult(false, true, msg);
    }

    public void enforce(EmployeeView employee, LocalDate start, LocalDate end) {
        ClashResult result = check(employee, start, end);
        if (result.blocked()) {
            throw new IllegalStateException(result.message());
        }
    }
}
