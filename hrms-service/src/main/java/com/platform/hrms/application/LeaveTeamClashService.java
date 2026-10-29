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

    public void enforce(Employee employee, LocalDate start, LocalDate end) {
        if (employee.getTeamId() == null) {
            return;
        }
        Team team = teamRepository.findById(employee.getTeamId()).orElse(null);
        if (team == null || team.getMaxOnLeave() <= 0) {
            return;
        }
        List<Long> teammateIds = employeeRepository.findByTeamIdAndActiveTrue(team.getId()).stream()
                .map(Employee::getId)
                .toList();
        if (teammateIds.isEmpty()) {
            return;
        }
        List<LeaveApplication> overlapping = applicationRepository.findOverlappingForEmployees(
                teammateIds, start, end, employee.getId());
        long distinctOnLeave = overlapping.stream().map(LeaveApplication::getEmployeeId).distinct().count();
        if (distinctOnLeave < team.getMaxOnLeave()) {
            return;
        }
        String action = team.getClashAction() == null ? "ALERT" : team.getClashAction().trim().toUpperCase(Locale.ROOT);
        if ("BLOCK".equals(action)) {
            throw new IllegalStateException(
                    "Team clash: max_on_leave=" + team.getMaxOnLeave()
                            + " already reached for team " + team.getCode());
        }
    }
}
