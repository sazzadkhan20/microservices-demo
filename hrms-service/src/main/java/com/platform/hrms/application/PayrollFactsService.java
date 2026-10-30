package com.platform.hrms.application;

import com.platform.hrms.api.dto.LeaveBalanceAdjustRequest;
import com.platform.hrms.domain.AttendanceRecord;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.LeaveApplication;
import com.platform.hrms.domain.LeaveBalance;
import com.platform.hrms.domain.LeaveType;
import com.platform.hrms.domain.WorkSchedule;
import com.platform.hrms.infrastructure.persistence.AttendanceRecordRepository;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.hrms.infrastructure.persistence.LeaveBalanceRepository;
import com.platform.hrms.infrastructure.persistence.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FR-PAY-01..05 — gathers payroll facts for a given employee + period.
 * All logic is config/data-driven; no tenant if-else.
 */
@Service
public class PayrollFactsService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final WorkScheduleService workScheduleService;
    private final LeaveBalanceService leaveBalanceService;

    public PayrollFactsService(
            EmployeeRepository employeeRepository,
            AttendanceRecordRepository attendanceRepository,
            LeaveApplicationRepository leaveApplicationRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            LeaveTypeRepository leaveTypeRepository,
            WorkScheduleService workScheduleService,
            LeaveBalanceService leaveBalanceService) {
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.workScheduleService = workScheduleService;
        this.leaveBalanceService = leaveBalanceService;
    }

    /**
     * Computes all payroll facts for an employee in a given period (YYYY-MM).
     * Never throws for missing attendance — returns zeros for those fields.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> computeFacts(Long employeeId, String periodLabel) {
        Employee emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        YearMonth ym = YearMonth.parse(periodLabel.substring(0, 7));
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        LocalDate today = LocalDate.now();

        // Salary/payType from employee (salary_history used at calculate time by payroll-service)
        String payType = resolvePayType(emp);
        String currency = emp.getPayCurrency() == null ? "BDT" : emp.getPayCurrency();
        LocalDate startDate = emp.getStartDate();
        LocalDate exitDate = emp.getContractEnd(); // null for permanent employees

        // Resolve the work schedule for this employee
        WorkSchedule schedule = workScheduleService.resolveForEmployee(emp).orElse(null);

        // --- workingDaysInMonth: schedule working days in month excluding holidays ---
        int workingDaysInMonth = countScheduleWorkingDays(schedule, emp, monthStart, monthEnd);

        // --- daysEmployed: working days in [startDate..exitDate_or_monthEnd] ∩ [monthStart..monthEnd] ---
        LocalDate empStart = startDate != null ? startDate.isAfter(monthStart) ? startDate : monthStart : monthStart;
        LocalDate empEnd = exitDate != null ? (exitDate.isBefore(monthEnd) ? exitDate : monthEnd) : monthEnd;
        int daysEmployed;
        if (empEnd.isBefore(empStart)) {
            daysEmployed = 0;
        } else {
            daysEmployed = countScheduleWorkingDays(schedule, emp, empStart, empEnd);
        }

        // --- Attendance records for the period ---
        List<AttendanceRecord> attRecords = attendanceRepository
                .findByEmployeeIdAndWorkDateBetween(employeeId, monthStart, monthEnd);
        Map<LocalDate, AttendanceRecord> attByDate = attRecords.stream()
                .collect(Collectors.toMap(AttendanceRecord::getWorkDate, r -> r, (a, b) -> a));

        // --- Leave applications overlapping the period ---
        List<LeaveApplication> leaveApps = leaveApplicationRepository
                .findByEmployeeIdOrderByCreatedAtDesc(employeeId)
                .stream()
                .filter(a -> LeaveApplication.STATUS_APPROVED.equalsIgnoreCase(a.getStatus()))
                .filter(a -> a.getStartDate() != null && a.getEndDate() != null)
                .filter(a -> !a.getStartDate().isAfter(monthEnd) && !a.getEndDate().isBefore(monthStart))
                .toList();

        // Build a set of days that are covered by approved leave
        java.util.Set<LocalDate> leaveDays = new java.util.HashSet<>();
        for (LeaveApplication la : leaveApps) {
            LocalDate s = la.getStartDate().isBefore(monthStart) ? monthStart : la.getStartDate();
            LocalDate e = la.getEndDate().isAfter(monthEnd) ? monthEnd : la.getEndDate();
            for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
                leaveDays.add(d);
            }
        }

        // LeaveType lookup map (code → type)
        Map<String, LeaveType> leaveTypeMap = leaveTypeRepository.findByActiveTrueOrderBySortOrderAsc()
                .stream().collect(Collectors.toMap(LeaveType::getCode, lt -> lt, (a, b) -> a));

        // --- absentDays: per working day through min(today, monthEnd) ---
        LocalDate checkUpTo = today.isBefore(monthEnd) ? today : monthEnd;
        BigDecimal absentDays = BigDecimal.ZERO;
        long missingCheckoutCount = 0;
        long hoursWorkedMinutes = 0;

        for (LocalDate d = monthStart; !d.isAfter(checkUpTo); d = d.plusDays(1)) {
            // Skip non-working days (weekends per schedule, holidays)
            if (schedule != null && !workScheduleService.isWorkingDay(schedule, d)) {
                continue;
            }
            if (workScheduleService.isHoliday(emp.getLocationId(), d)) {
                continue;
            }

            AttendanceRecord rec = attByDate.get(d);
            if (rec != null) {
                String status = rec.getStatusCode() == null ? "" : rec.getStatusCode().toUpperCase(Locale.ROOT);
                // A / ABSENT = 1, HD / HALF_DAY = 0.5
                // OL treated separately below; M (missing checkout) treated as absent-like
                if ("A".equals(status) || "ABSENT".equals(status)) {
                    absentDays = absentDays.add(BigDecimal.ONE);
                } else if ("HD".equals(status) || "HALF".equals(status) || "HALF_DAY".equals(status)) {
                    absentDays = absentDays.add(new BigDecimal("0.5"));
                }
                // Count hours for closed shifts only
                if (rec.getCheckInAt() != null && rec.getCheckOutAt() != null) {
                    hoursWorkedMinutes += Duration.between(rec.getCheckInAt(), rec.getCheckOutAt()).toMinutes();
                }
                // Missing checkout count
                if (rec.getCheckInAt() != null && rec.getCheckOutAt() == null) {
                    missingCheckoutCount++;
                }
            } else {
                // No attendance record: on approved leave → OL (not absent); else synthetic absent
                if (!leaveDays.contains(d)) {
                    absentDays = absentDays.add(BigDecimal.ONE);
                }
                // else: OL — not counted as absent (FR-PAY-03)
            }
        }

        // --- hoursWorked from remaining records not covered above (if checkUpTo < monthEnd for closed shifts) ---
        // Already accumulated above. Convert to hours.
        BigDecimal hoursWorked = BigDecimal.valueOf(hoursWorkedMinutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        // --- unpaidLeaveDays and paidLeaveDays ---
        BigDecimal unpaidLeaveDays = BigDecimal.ZERO;
        BigDecimal paidLeaveDays = BigDecimal.ZERO;
        for (LeaveApplication la : leaveApps) {
            String code = la.getLeaveTypeCode();
            LeaveType lt = code != null ? leaveTypeMap.get(code) : null;
            BigDecimal days = la.getWorkingDaysCounted() == null ? BigDecimal.ZERO : la.getWorkingDaysCounted();
            // Pro-rate if leave application spans beyond period boundaries
            // We use workingDaysCounted as-is since it's already computed by LeaveApplicationService
            if (lt != null && !lt.isPaid()) {
                unpaidLeaveDays = unpaidLeaveDays.add(days);
            } else {
                paidLeaveDays = paidLeaveDays.add(days);
            }
        }

        // --- leaveBalances: map leaveTypeCode → balance for current year ---
        int balanceYear = today.getYear();
        Map<String, BigDecimal> leaveBalances = new LinkedHashMap<>();
        for (LeaveBalance lb : leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, balanceYear)) {
            leaveBalances.put(lb.getLeaveTypeCode(), lb.getBalanceDays());
        }

        // Build result map
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employeeId", employeeId);
        result.put("periodLabel", periodLabel.substring(0, 7));
        result.put("startDate", startDate != null ? startDate.toString() : null);
        result.put("exitDate", exitDate != null ? exitDate.toString() : null);
        result.put("payType", payType);
        result.put("currency", currency);
        result.put("baseSalary", emp.getBaseSalary());
        result.put("hourlyRate", emp.getHourlyRate());
        result.put("workingDaysInMonth", workingDaysInMonth);
        result.put("daysEmployed", daysEmployed);
        result.put("absentDays", absentDays);
        result.put("unpaidLeaveDays", unpaidLeaveDays);
        result.put("paidLeaveDays", paidLeaveDays);
        result.put("hoursWorked", hoursWorked);
        result.put("missingCheckoutCount", missingCheckoutCount);
        result.put("leaveBalances", leaveBalances);
        result.put("monthStart", monthStart.toString());
        result.put("monthEnd", monthEnd.toString());
        return result;
    }

    /**
     * Apply leave cover: deduct from leaveBalances for each type consumed.
     * Pass reverse=true on reopen to reverse (add back).
     *
     * @param employeeId     employee
     * @param periodLabel    YYYY-MM
     * @param consumedByType map leaveTypeCode → daysConsumed
     * @param reverse        true = add back (reopen); false = deduct (finalize)
     */
    @Transactional
    public void applyLeaveCover(Long employeeId, String periodLabel,
                                Map<String, BigDecimal> consumedByType, boolean reverse) {
        if (consumedByType == null || consumedByType.isEmpty()) {
            return;
        }
        int year = YearMonth.parse(periodLabel.substring(0, 7)).getYear();
        for (Map.Entry<String, BigDecimal> entry : consumedByType.entrySet()) {
            String leaveTypeCode = entry.getKey();
            BigDecimal days = entry.getValue();
            if (days == null || days.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal delta = reverse ? days : days.negate();
            String reason = "Payroll absence cover " + periodLabel.substring(0, 7);
            try {
                leaveBalanceService.adjustBySystem(employeeId, leaveTypeCode, delta, reason, year);
            } catch (Exception ex) {
                // Best-effort: log but don't fail the finalize
                org.slf4j.LoggerFactory.getLogger(PayrollFactsService.class)
                        .warn("applyLeaveCover failed for emp={} type={} delta={}: {}",
                                employeeId, leaveTypeCode, delta, ex.getMessage());
            }
        }
    }

    /**
     * Count schedule working days (non-holiday) between from and to inclusive.
     * If schedule is null, returns calendar days (fallback).
     */
    private int countScheduleWorkingDays(WorkSchedule schedule, Employee emp,
                                         LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return 0;
        }
        int count = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (schedule != null && !workScheduleService.isWorkingDay(schedule, d)) {
                continue;
            }
            if (workScheduleService.isHoliday(emp.getLocationId(), d)) {
                continue;
            }
            count++;
        }
        return count;
    }

    private static String resolvePayType(Employee emp) {
        // Hourly rate set and positive → HOURLY; else MONTHLY
        if (emp.getHourlyRate() != null && emp.getHourlyRate().compareTo(BigDecimal.ZERO) > 0) {
            return "HOURLY";
        }
        return "MONTHLY";
    }
}
