package com.platform.hrms.api.rest;

import com.platform.hrms.domain.AttendanceRecord;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.LeaveApplication;
import com.platform.hrms.infrastructure.persistence.AttendanceRecordRepository;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.hrms.infrastructure.persistence.WorkLogRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FR-DB — Admin dashboard stats API.
 * GET /api/dashboard/admin?date=YYYY-MM-DD
 * GET /api/dashboard/work-hours?from=&to=&locationId=&teamId=
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Admin headcount and attendance summary cards")
public class DashboardController {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final LeaveApplicationRepository leaveRepository;
    private final WorkLogRepository workLogRepository;

    public DashboardController(
            EmployeeRepository employeeRepository,
            AttendanceRecordRepository attendanceRepository,
            LeaveApplicationRepository leaveRepository,
            WorkLogRepository workLogRepository) {
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRepository = leaveRepository;
        this.workLogRepository = workLogRepository;
    }

    /**
     * FR-DB admin stats cards for a given date (defaults to today).
     */
    @GetMapping("/admin")
    @RequiresPermission(feature = "reports", action = "view")
    @Operation(summary = "FR-DB Admin dashboard summary cards for a given date")
    public Map<String, Object> admin(@RequestParam(required = false) String date) {
        LocalDate target = date != null && !date.isBlank() ? LocalDate.parse(date) : LocalDate.now();

        List<Employee> activeEmployees = employeeRepository.findAll().stream()
                .filter(Employee::isActive)
                .toList();

        // headcountByLocation
        Map<String, Long> byLocation = activeEmployees.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getLocationId() == null ? "unassigned" : String.valueOf(e.getLocationId()),
                        Collectors.counting()));

        // headcountByEmploymentType
        Map<String, Long> byType = activeEmployees.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getEmploymentType() == null ? "unassigned" : e.getEmploymentType(),
                        Collectors.counting()));

        // checkedInVsExpected: attendance records for target date
        List<AttendanceRecord> recs = attendanceRepository.findByWorkDate(target);
        Set<Long> checkedIn = recs.stream()
                .filter(r -> r.getCheckInAt() != null)
                .map(AttendanceRecord::getEmployeeId)
                .collect(Collectors.toSet());
        long expectedPresent = activeEmployees.size();
        long checkedInCount = checkedIn.size();

        // onLeaveCount
        long onLeave = activeEmployees.stream()
                .filter(e -> isOnApprovedLeave(e.getId(), target))
                .count();

        // lateCount
        long lateCount = recs.stream()
                .filter(r -> "L".equalsIgnoreCase(r.getStatusCode()))
                .count();

        // pendingApprovals
        long pendingApprovals = leaveRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> LeaveApplication.STATUS_PENDING_APPROVAL.equalsIgnoreCase(a.getStatus()))
                .count();

        // pendingApprovalsIdle2d — pending for more than 2 days
        Instant cutoff2d = Instant.now().minus(2, ChronoUnit.DAYS);
        long pendingIdle = leaveRepository.findPendingApprovalOlderThan(cutoff2d).size();

        // contractsEnding30d
        LocalDate in30 = target.plusDays(30);
        long contractsEnding30d = activeEmployees.stream()
                .filter(e -> e.getContractEnd() != null
                        && !e.getContractEnd().isBefore(target)
                        && !e.getContractEnd().isAfter(in30))
                .count();

        // probationEnding30d
        long probationEnding30d = activeEmployees.stream()
                .filter(e -> e.getProbationEnd() != null
                        && !e.getProbationEnd().isBefore(target)
                        && !e.getProbationEnd().isAfter(in30))
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", target.toString());
        result.put("headcountByLocation", byLocation);
        result.put("headcountByEmploymentType", byType);
        result.put("headcountTotal", activeEmployees.size());
        result.put("checkedInVsExpected", Map.of("checkedIn", checkedInCount, "expected", expectedPresent));
        result.put("onLeaveCount", onLeave);
        result.put("lateCount", lateCount);
        result.put("pendingApprovals", pendingApprovals);
        result.put("pendingApprovalsIdle2d", pendingIdle);
        result.put("contractsEnding30d", contractsEnding30d);
        result.put("probationEnding30d", probationEnding30d);
        return result;
    }

    /**
     * FR-DB per-employee work-hours summary for a date range.
     * Returns rows with expected/worked/logged/gap/present/leave/absent/late/noLog counts.
     */
    @GetMapping("/work-hours")
    @RequiresPermission(feature = "reports", action = "view")
    @Operation(summary = "FR-DB Per-employee work-hours summary for a date range")
    public List<Map<String, Object>> workHours(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String locationId,
            @RequestParam(required = false) String teamId) {

        LocalDate fromDate = from != null && !from.isBlank() ? LocalDate.parse(from)
                : LocalDate.now().withDayOfMonth(1);
        LocalDate toDate = to != null && !to.isBlank() ? LocalDate.parse(to) : LocalDate.now();

        Long locId = locationId != null && !locationId.isBlank() ? Long.valueOf(locationId) : null;
        Long tmId = teamId != null && !teamId.isBlank() ? Long.valueOf(teamId) : null;

        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(Employee::isActive)
                .filter(e -> locId == null || locId.equals(e.getLocationId()))
                .filter(e -> tmId == null || tmId.equals(e.getTeamId()))
                .toList();

        long rangeDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1;

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Employee emp : employees) {
            List<AttendanceRecord> recs =
                    attendanceRepository.findByEmployeeIdAndWorkDateBetween(emp.getId(), fromDate, toDate);

            long present = recs.stream()
                    .filter(r -> "P".equalsIgnoreCase(r.getStatusCode())
                            || "L".equalsIgnoreCase(r.getStatusCode())
                            || "HD".equalsIgnoreCase(r.getStatusCode()))
                    .count();
            long late = recs.stream().filter(r -> "L".equalsIgnoreCase(r.getStatusCode())).count();
            long onLeave = recs.stream().filter(r -> "OL".equalsIgnoreCase(r.getStatusCode())).count();
            long absent = recs.stream().filter(r -> "A".equalsIgnoreCase(r.getStatusCode())
                    || "NC".equalsIgnoreCase(r.getStatusCode())).count();
            long noLog = recs.stream().filter(r -> r.getCheckInAt() != null
                    && workLogRepository.findByEmployeeIdAndWorkDate(emp.getId(), r.getWorkDate()).isEmpty())
                    .count();

            // worked minutes from closed punches
            long workedMinutes = recs.stream()
                    .filter(r -> r.getCheckInAt() != null && r.getCheckOutAt() != null)
                    .mapToLong(r -> Math.max(0,
                            Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes()))
                    .sum();

            // logged minutes from work log
            long loggedMinutes = workLogRepository
                    .findByEmployeeIdAndWorkDateBetween(emp.getId(), fromDate, toDate).stream()
                    .mapToLong(wl -> wl.getMinutes())
                    .sum();

            // expected: 8h per working day approximation (schedule-agnostic)
            long expectedMinutes = present * 480L;
            long gap = Math.max(0, expectedMinutes - workedMinutes);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", emp.getId());
            row.put("employeeCode", emp.getEmployeeCode());
            row.put("fullName", emp.getFullName());
            row.put("locationId", emp.getLocationId());
            row.put("teamId", emp.getTeamId());
            row.put("expected", Math.round(expectedMinutes / 60.0 * 100) / 100.0);
            row.put("worked", Math.round(workedMinutes / 60.0 * 100) / 100.0);
            row.put("logged", Math.round(loggedMinutes / 60.0 * 100) / 100.0);
            row.put("gap", Math.round(gap / 60.0 * 100) / 100.0);
            row.put("present", present);
            row.put("leave", onLeave);
            row.put("absent", absent);
            row.put("late", late);
            row.put("noLog", noLog);
            rows.add(row);
        }
        return rows;
    }

    private boolean isOnApprovedLeave(Long employeeId, LocalDate date) {
        return leaveRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .filter(a -> LeaveApplication.STATUS_APPROVED.equalsIgnoreCase(a.getStatus()))
                .anyMatch(a -> a.getStartDate() != null && a.getEndDate() != null
                        && !date.isBefore(a.getStartDate()) && !date.isAfter(a.getEndDate()));
    }
}
