package com.platform.hrms.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.hrms.application.AuditService;
import com.platform.hrms.domain.AttendanceRecord;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.LeaveApplication;
import com.platform.hrms.domain.LeaveBalance;
import com.platform.hrms.domain.LeaveType;
import com.platform.hrms.infrastructure.persistence.AttendanceRecordRepository;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.hrms.infrastructure.persistence.LeaveBalanceRepository;
import com.platform.hrms.infrastructure.persistence.LeaveTypeRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FR-RP — CSV report exports.
 * GET /api/reports/leave-balances.csv?year=
 * GET /api/reports/leave-requests.csv?from=&to=
 * GET /api/reports/headcount.csv
 * GET /api/reports/late-absence.csv?from=&to=
 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "CSV data exports for HR analytics")
public class ReportFacadeController {

    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AuditService auditService;

    public ReportFacadeController(
            EmployeeRepository employeeRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            LeaveApplicationRepository leaveApplicationRepository,
            AttendanceRecordRepository attendanceRepository,
            LeaveTypeRepository leaveTypeRepository,
            AuditService auditService) {
        this.employeeRepository = employeeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.auditService = auditService;
    }

    /** FR-RP: Leave balance report per employee per leave type for a given year. */
    @GetMapping("/leave-balances.csv")
    @RequiresPermission(feature = "reports", action = "view")
    @Operation(summary = "FR-RP Leave balances CSV (year param, defaults to current year)")
    public ResponseEntity<String> leaveBalancesCsv(
            @RequestParam(required = false) String year) {
        int y = year != null && !year.isBlank() ? Integer.parseInt(year) : LocalDate.now().getYear();

        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(Employee::isActive).toList();
        List<LeaveType> types = leaveTypeRepository.findByActiveTrueOrderBySortOrderAsc();
        List<LeaveBalance> balances = leaveBalanceRepository.findAll().stream()
                .filter(b -> b.getYear() == y).toList();

        Map<Long, Map<String, LeaveBalance>> byEmpType = balances.stream()
                .collect(Collectors.groupingBy(LeaveBalance::getEmployeeId,
                        Collectors.toMap(LeaveBalance::getLeaveTypeCode, lb -> lb)));

        StringBuilder csv = new StringBuilder();
        // header
        csv.append("employeeId,employeeCode,fullName");
        for (LeaveType lt : types) {
            csv.append(",").append(csvSafe(lt.getCode()));
        }
        csv.append("\n");
        // rows
        for (Employee emp : employees) {
            csv.append(emp.getId()).append(",")
                    .append(csvSafe(emp.getEmployeeCode())).append(",")
                    .append(csvSafe(emp.getFullName()));
            Map<String, LeaveBalance> empBalances = byEmpType.getOrDefault(emp.getId(), Map.of());
            for (LeaveType lt : types) {
                LeaveBalance lb = empBalances.get(lt.getCode());
                csv.append(",").append(lb == null ? "0" : lb.getBalanceDays().toPlainString());
            }
            csv.append("\n");
        }

        auditService.record(AuthContext.getEmployeeId(), "EXPORT", "LeaveBalances", null,
                null, Map.of("year", y, "rows", employees.size()));
        return csvResponse(csv.toString(), "leave-balances-" + y + ".csv");
    }

    /** FR-RP: Leave requests CSV for a date range. */
    @GetMapping("/leave-requests.csv")
    @RequiresPermission(feature = "reports", action = "view")
    @Operation(summary = "FR-RP Leave requests CSV for a date range")
    public ResponseEntity<String> leaveRequestsCsv(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate fromDate = from != null && !from.isBlank() ? LocalDate.parse(from)
                : LocalDate.now().withDayOfMonth(1);
        LocalDate toDate = to != null && !to.isBlank() ? LocalDate.parse(to) : LocalDate.now();

        Map<Long, Employee> empMap = employeeRepository.findAll().stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        List<LeaveApplication> applications = leaveApplicationRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(a -> a.getStartDate() != null
                        && !a.getStartDate().isBefore(fromDate)
                        && !a.getStartDate().isAfter(toDate))
                .toList();

        StringBuilder csv = new StringBuilder();
        csv.append("id,employeeId,employeeCode,fullName,leaveTypeCode,startDate,endDate,days,status,reason\n");
        for (LeaveApplication a : applications) {
            Employee emp = empMap.get(a.getEmployeeId());
            csv.append(a.getId()).append(",")
                    .append(a.getEmployeeId()).append(",")
                    .append(csvSafe(emp == null ? "" : emp.getEmployeeCode())).append(",")
                    .append(csvSafe(emp == null ? "" : emp.getFullName())).append(",")
                    .append(csvSafe(a.getLeaveTypeCode())).append(",")
                    .append(a.getStartDate()).append(",")
                    .append(a.getEndDate()).append(",")
                    .append(a.getWorkingDaysCounted() == null ? "" : a.getWorkingDaysCounted().toPlainString()).append(",")
                    .append(csvSafe(a.getStatus())).append(",")
                    .append(csvSafe(a.getReason()))
                    .append("\n");
        }

        auditService.record(AuthContext.getEmployeeId(), "EXPORT", "LeaveRequests", null,
                null, Map.of("from", fromDate.toString(), "to", toDate.toString(), "rows", applications.size()));
        return csvResponse(csv.toString(), "leave-requests.csv");
    }

    /** FR-RP: Headcount joiners/leavers summary for the last 6 months. */
    @GetMapping("/headcount.csv")
    @RequiresPermission(feature = "reports", action = "view")
    @Operation(summary = "FR-RP Headcount joiners/leavers last 6 months summary")
    public ResponseEntity<String> headcountCsv() {
        LocalDate today = LocalDate.now();
        LocalDate sixMonthsAgo = today.minusMonths(6).withDayOfMonth(1);

        List<Employee> all = employeeRepository.findAll();

        StringBuilder csv = new StringBuilder();
        csv.append("yearMonth,joiners,leavers,activeAtEnd\n");

        for (int i = 5; i >= 0; i--) {
            LocalDate monthStart = today.minusMonths(i).withDayOfMonth(1);
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

            long joiners = all.stream()
                    .filter(e -> e.getStartDate() != null
                            && !e.getStartDate().isBefore(monthStart)
                            && !e.getStartDate().isAfter(monthEnd))
                    .count();

            long leavers = all.stream()
                    .filter(e -> !e.isActive() && e.getContractEnd() != null
                            && !e.getContractEnd().isBefore(monthStart)
                            && !e.getContractEnd().isAfter(monthEnd))
                    .count();

            long activeAtEnd = all.stream()
                    .filter(e -> e.isActive()
                            && (e.getStartDate() == null || !e.getStartDate().isAfter(monthEnd)))
                    .count();

            csv.append(monthStart.getYear()).append("-")
                    .append(String.format("%02d", monthStart.getMonthValue())).append(",")
                    .append(joiners).append(",")
                    .append(leavers).append(",")
                    .append(activeAtEnd).append("\n");
        }

        auditService.record(AuthContext.getEmployeeId(), "EXPORT", "Headcount", null,
                null, Map.of("from", sixMonthsAgo.toString(), "to", today.toString()));
        return csvResponse(csv.toString(), "headcount.csv");
    }

    /** FR-RP: Late and absence report for a date range. */
    @GetMapping("/late-absence.csv")
    @RequiresPermission(feature = "reports", action = "view")
    @Operation(summary = "FR-RP Late and absence records CSV for a date range")
    public ResponseEntity<String> lateAbsenceCsv(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate fromDate = from != null && !from.isBlank() ? LocalDate.parse(from)
                : LocalDate.now().withDayOfMonth(1);
        LocalDate toDate = to != null && !to.isBlank() ? LocalDate.parse(to) : LocalDate.now();

        Map<Long, Employee> empMap = employeeRepository.findAll().stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        // Collect all attendance records in range that are late or absent
        List<AttendanceRecord> records = new java.util.ArrayList<>();
        for (LocalDate d = fromDate; !d.isAfter(toDate); d = d.plusDays(1)) {
            attendanceRepository.findByWorkDate(d).stream()
                    .filter(r -> "L".equalsIgnoreCase(r.getStatusCode())
                            || "A".equalsIgnoreCase(r.getStatusCode())
                            || "NC".equalsIgnoreCase(r.getStatusCode()))
                    .forEach(records::add);
        }

        StringBuilder csv = new StringBuilder();
        csv.append("date,employeeId,employeeCode,fullName,statusCode,status,checkIn,checkOut\n");
        for (AttendanceRecord r : records) {
            Employee emp = empMap.get(r.getEmployeeId());
            csv.append(r.getWorkDate()).append(",")
                    .append(r.getEmployeeId()).append(",")
                    .append(csvSafe(emp == null ? "" : emp.getEmployeeCode())).append(",")
                    .append(csvSafe(emp == null ? "" : emp.getFullName())).append(",")
                    .append(csvSafe(r.getStatusCode())).append(",")
                    .append(csvSafe(r.getStatusLabel())).append(",")
                    .append(r.getCheckInAt() == null ? "" : r.getCheckInAt().toString()).append(",")
                    .append(r.getCheckOutAt() == null ? "" : r.getCheckOutAt().toString())
                    .append("\n");
        }

        auditService.record(AuthContext.getEmployeeId(), "EXPORT", "LateAbsence", null,
                null, Map.of("from", fromDate.toString(), "to", toDate.toString(), "rows", records.size()));
        return csvResponse(csv.toString(), "late-absence.csv");
    }

    // ---- helpers ----------------------------------------------------------------

    private static ResponseEntity<String> csvResponse(String csv, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    /** Escape a CSV field: wrap in quotes if it contains comma, quote, or newline. */
    private static String csvSafe(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
