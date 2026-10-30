package com.platform.hrms.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.hrms.application.DataScopeService;
import com.platform.hrms.application.WorkScheduleService;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FR-DB — Admin dashboard stats API.
 * Scoped by reports:view data scope (OWN / TEAM / ALL).
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Admin headcount and attendance summary cards")
public class DashboardController {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final LeaveApplicationRepository leaveRepository;
    private final WorkLogRepository workLogRepository;
    private final DataScopeService dataScopeService;
    private final WorkScheduleService workScheduleService;

    public DashboardController(
            EmployeeRepository employeeRepository,
            AttendanceRecordRepository attendanceRepository,
            LeaveApplicationRepository leaveRepository,
            WorkLogRepository workLogRepository,
            DataScopeService dataScopeService,
            WorkScheduleService workScheduleService) {
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRepository = leaveRepository;
        this.workLogRepository = workLogRepository;
        this.dataScopeService = dataScopeService;
        this.workScheduleService = workScheduleService;
    }

    @GetMapping("/admin")
    @RequiresPermission(feature = "reports", action = "view")
    @Operation(summary = "FR-DB Admin dashboard summary cards for a given date")
    public Map<String, Object> admin(@RequestParam(required = false) String date) {
        LocalDate target = date != null && !date.isBlank() ? LocalDate.parse(date) : LocalDate.now();
        DataScopeService.Visibility vis = dataScopeService.visibility(
                AuthContext.requireEmployeeId(), "reports", "view");

        List<Employee> activeEmployees = employeeRepository.findAll().stream()
                .filter(Employee::isActive)
                .filter(e -> vis.allows(e.getId()))
                .toList();

        Map<String, Long> byLocation = activeEmployees.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getLocationId() == null ? "unassigned" : String.valueOf(e.getLocationId()),
                        Collectors.counting()));

        Map<String, Long> byType = activeEmployees.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getEmploymentType() == null ? "unassigned" : e.getEmploymentType(),
                        Collectors.counting()));

        Set<Long> scopedIds = activeEmployees.stream().map(Employee::getId).collect(Collectors.toSet());
        List<AttendanceRecord> recs = attendanceRepository.findByWorkDate(target).stream()
                .filter(r -> scopedIds.contains(r.getEmployeeId()))
                .toList();
        Set<Long> checkedIn = recs.stream()
                .filter(r -> r.getCheckInAt() != null)
                .map(AttendanceRecord::getEmployeeId)
                .collect(Collectors.toSet());

        long onLeave = activeEmployees.stream()
                .filter(e -> isOnApprovedLeave(e.getId(), target))
                .count();

        long lateCount = recs.stream()
                .filter(r -> "L".equalsIgnoreCase(r.getStatusCode()))
                .count();

        long pendingApprovals = leaveRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> scopedIds.contains(a.getEmployeeId()))
                .filter(a -> LeaveApplication.STATUS_PENDING_APPROVAL.equalsIgnoreCase(a.getStatus()))
                .count();

        Instant cutoff2d = Instant.now().minus(2, ChronoUnit.DAYS);
        long pendingIdle = leaveRepository.findPendingApprovalOlderThan(cutoff2d).stream()
                .filter(a -> scopedIds.contains(a.getEmployeeId()))
                .count();

        LocalDate in30 = target.plusDays(30);
        long contractsEnding30d = activeEmployees.stream()
                .filter(e -> e.getContractEnd() != null
                        && !e.getContractEnd().isBefore(target)
                        && !e.getContractEnd().isAfter(in30))
                .count();

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
        result.put("checkedInVsExpected", Map.of("checkedIn", checkedIn.size(), "expected", activeEmployees.size()));
        result.put("onLeaveCount", onLeave);
        result.put("lateCount", lateCount);
        result.put("pendingApprovals", pendingApprovals);
        result.put("pendingApprovalsIdle2d", pendingIdle);
        result.put("contractsEnding30d", contractsEnding30d);
        result.put("probationEnding30d", probationEnding30d);
        return result;
    }

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

        DataScopeService.Visibility vis = dataScopeService.visibility(
                AuthContext.requireEmployeeId(), "reports", "view");

        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(Employee::isActive)
                .filter(e -> vis.allows(e.getId()))
                .filter(e -> locId == null || locId.equals(e.getLocationId()))
                .filter(e -> tmId == null || tmId.equals(e.getTeamId()))
                .toList();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Employee emp : employees) {
            List<AttendanceRecord> recs =
                    attendanceRepository.findByEmployeeIdAndWorkDateBetween(emp.getId(), fromDate, toDate);
            Map<LocalDate, AttendanceRecord> byDate = recs.stream()
                    .collect(Collectors.toMap(AttendanceRecord::getWorkDate, r -> r, (a, b) -> a));

            long dayMinutes = workScheduleService.scheduledDayMinutes(emp);

            long expectedWorkingDays = 0;
            long absent = 0;
            long present = 0;
            long late = 0;
            long onLeave = 0;
            Set<LocalDate> leaveDays = leaveDaysInRange(emp.getId(), fromDate, toDate);
            LocalDate today = LocalDate.now();

            for (LocalDate d = fromDate; !d.isAfter(toDate); d = d.plusDays(1)) {
                if (emp.getStartDate() != null && d.isBefore(emp.getStartDate())) continue;
                if (emp.getContractEnd() != null && d.isAfter(emp.getContractEnd())) continue;
                if (!workScheduleService.isScheduledWorkingDay(emp, d)) continue;
                expectedWorkingDays++;
                AttendanceRecord rec = byDate.get(d);
                if (rec != null) {
                    String code = rec.getStatusCode() == null ? "" : rec.getStatusCode();
                    if ("L".equalsIgnoreCase(code)) late++;
                    if ("OL".equalsIgnoreCase(code)) onLeave++;
                    if ("P".equalsIgnoreCase(code) || "L".equalsIgnoreCase(code)
                            || "HD".equalsIgnoreCase(code) || "W".equalsIgnoreCase(code)) {
                        present++;
                    }
                    if ("A".equalsIgnoreCase(code) || "NC".equalsIgnoreCase(code)) {
                        absent++;
                    }
                } else if (leaveDays.contains(d)) {
                    onLeave++;
                } else if (!d.isAfter(today)) {
                    // Synthetic absence: a past working day with no punch and no approved leave.
                    absent++;
                }
            }

            long noLog = recs.stream().filter(r -> r.getCheckInAt() != null
                    && workLogRepository.findByEmployeeIdAndWorkDate(emp.getId(), r.getWorkDate()).isEmpty())
                    .count();

            long workedMinutes = recs.stream()
                    .filter(r -> r.getCheckInAt() != null && r.getCheckOutAt() != null)
                    .mapToLong(r -> Math.max(0,
                            Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes()))
                    .sum();

            long loggedMinutes = workLogRepository
                    .findByEmployeeIdAndWorkDateBetween(emp.getId(), fromDate, toDate).stream()
                    .mapToLong(wl -> wl.getMinutes())
                    .sum();

            long expectedMinutes = expectedWorkingDays * dayMinutes;
            long gap = expectedMinutes - workedMinutes;
            double expectedHours = Math.round(expectedMinutes / 60.0 * 100) / 100.0;
            double workedHours = Math.round(workedMinutes / 60.0 * 100) / 100.0;
            double loggedHours = Math.round(loggedMinutes / 60.0 * 100) / 100.0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", emp.getId());
            row.put("employeeCode", emp.getEmployeeCode());
            row.put("fullName", emp.getFullName());
            row.put("employeeName", emp.getFullName());
            row.put("locationId", emp.getLocationId());
            row.put("teamId", emp.getTeamId());
            row.put("expected", expectedHours);
            row.put("expectedHours", expectedHours);
            row.put("worked", workedHours);
            row.put("logged", loggedHours);
            row.put("totalHours", loggedHours);
            row.put("gap", Math.round(gap / 60.0 * 100) / 100.0);
            row.put("attendanceDays", present);
            row.put("present", present);
            row.put("leave", onLeave);
            row.put("absent", absent);
            row.put("late", late);
            row.put("noLog", noLog);
            rows.add(row);
        }
        return rows;
    }

    private Set<LocalDate> leaveDaysInRange(Long employeeId, LocalDate from, LocalDate to) {
        Set<LocalDate> days = new HashSet<>();
        leaveRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .filter(a -> LeaveApplication.STATUS_APPROVED.equalsIgnoreCase(a.getStatus()))
                .filter(a -> a.getStartDate() != null && a.getEndDate() != null)
                .filter(a -> !a.getStartDate().isAfter(to) && !a.getEndDate().isBefore(from))
                .forEach(a -> {
                    LocalDate s = a.getStartDate().isBefore(from) ? from : a.getStartDate();
                    LocalDate e = a.getEndDate().isAfter(to) ? to : a.getEndDate();
                    for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
                        days.add(d);
                    }
                });
        return days;
    }

    private boolean isOnApprovedLeave(Long employeeId, LocalDate date) {
        return leaveRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .filter(a -> LeaveApplication.STATUS_APPROVED.equalsIgnoreCase(a.getStatus()))
                .anyMatch(a -> a.getStartDate() != null && a.getEndDate() != null
                        && !date.isBefore(a.getStartDate()) && !date.isAfter(a.getEndDate()));
    }
}
