package com.platform.hrms.application;

import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.LeaveApplication;
import com.platform.hrms.domain.Team;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.hrms.infrastructure.persistence.TeamRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Config-driven team leave clash: uses team.max_on_leave + team.clash_action (BLOCK|ALERT).
 */
@Service
public class LeaveTeamClashService {

    public record ClashResult(boolean blocked, boolean alert, String message) {}

    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveApplicationRepository applicationRepository;

    public LeaveTeamClashService(
            TeamRepository teamRepository,
            EmployeeRepository employeeRepository,
            LeaveApplicationRepository applicationRepository) {
        this.teamRepository = teamRepository;
        this.employeeRepository = employeeRepository;
        this.applicationRepository = applicationRepository;
    }

    /**
     * Returns a ClashResult (blocked/alert/none). Callers decide what to do.
     */
    public ClashResult check(Employee employee, LocalDate start, LocalDate end) {
        if (employee.getTeamId() == null) {
            return new ClashResult(false, false, null);
        }
        Team team = teamRepository.findById(employee.getTeamId()).orElse(null);
        if (team == null || team.getMaxOnLeave() <= 0) {
            return new ClashResult(false, false, null);
        }
        List<Long> teammateIds = employeeRepository.findByTeamIdAndActiveTrue(team.getId()).stream()
                .map(Employee::getId)
                .toList();
        if (teammateIds.isEmpty()) {
            return new ClashResult(false, false, null);
        }
        List<LeaveApplication> overlapping = applicationRepository.findOverlappingForEmployees(
                teammateIds, start, end, employee.getId());
        long distinctOnLeave = overlapping.stream().map(LeaveApplication::getEmployeeId).distinct().count();
        if (distinctOnLeave < team.getMaxOnLeave()) {
            return new ClashResult(false, false, null);
        }
        String action = team.getClashAction() == null ? "ALERT" : team.getClashAction().trim().toUpperCase(Locale.ROOT);
        String msg = "Team clash: max_on_leave=" + team.getMaxOnLeave()
                + " already reached for team " + team.getCode();
        if ("BLOCK".equals(action)) {
            return new ClashResult(true, false, msg);
        }
        return new ClashResult(false, true, msg);
    }

    /** Legacy method kept for any callers — throws on BLOCK, returns normally on ALERT. */
    public void enforce(Employee employee, LocalDate start, LocalDate end) {
        ClashResult result = check(employee, start, end);
        if (result.blocked()) {
            throw new IllegalStateException(result.message());
        }
    }
}
