package com.platform.timework.api.rest;

import com.platform.common.security.InternalCalls;
import com.platform.timework.application.WorkScheduleService;
import com.platform.timework.client.LeaveClient;
import com.platform.timework.client.people.EmployeeView;
import com.platform.timework.client.people.PeopleEmployeeGateway;
import com.platform.timework.domain.AttendanceRecord;
import com.platform.timework.domain.Holiday;
import com.platform.timework.domain.WorkSchedule;
import com.platform.timework.infrastructure.persistence.AttendanceRecordRepository;
import com.platform.timework.infrastructure.persistence.HolidayRepository;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * S2S payroll facts owned by Time &amp; Work (docs/PAYROLL_SPEC.md §2.1): per employee, the schedule mode
 * and one entry per calendar day with the Appendix A status, the payroll absence weight (Absent 1,
 * Half day 0.5, half-day leave without check-in 0.5 — Decision D1), closed-shift minutes and missing
 * check-outs. One batch call per payroll run; approved leave is fetched from Leave once for the batch.
 */
@RestController
@RequestMapping("/api/internal/payroll")
@Hidden
public class InternalPayrollAttendanceController {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HALF = new BigDecimal("0.5");
    /** FR-ATT-03 / BR-08 — a check-out up to 06:00 still counts for the previous day. */
    private static final LocalTime LATE_CHECKOUT_LIMIT = LocalTime.of(6, 0);

    private final PeopleEmployeeGateway employees;
    private final WorkScheduleService schedules;
    private final AttendanceRecordRepository attendance;
    private final HolidayRepository holidays;
    private final LeaveClient leave;
    private final String internalKey;

    public InternalPayrollAttendanceController(PeopleEmployeeGateway employees, WorkScheduleService schedules,
                                               AttendanceRecordRepository attendance, HolidayRepository holidays,
                                               LeaveClient leave,
                                               @Value("${platform.security.internal-key}") String internalKey) {
        this.employees = employees;
        this.schedules = schedules;
        this.attendance = attendance;
        this.holidays = holidays;
        this.leave = leave;
        this.internalKey = internalKey;
    }

    @GetMapping("/attendance")
    @Transactional(readOnly = true)
    public Map<String, Object> attendance(@RequestParam String period, @RequestParam String employeeIds,
                                          HttpServletRequest request) {
        if (!InternalCalls.isTrusted(request.getHeader(InternalCalls.HEADER), internalKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Internal-Key required");
        }
        YearMonth ym;
        try {
            ym = YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "period must be YYYY-MM");
        }
        List<Long> ids = Arrays.stream(employeeIds.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).distinct().toList();
        if (ids.size() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 1000 employees per call");
        }
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        Map<Long, Map<LocalDate, AttendanceRecord>> records = new HashMap<>();
        for (AttendanceRecord r : attendance.findByEmployeeIdInAndWorkDateBetween(ids, from, to)) {
            records.computeIfAbsent(r.getEmployeeId(), k -> new HashMap<>()).put(r.getWorkDate(), r);
        }
        List<Holiday> allHolidays = holidays.findAll();
        Map<Long, List<Map<String, Object>>> approved;
        try {
            approved = leave.approvedLeaveBatch(ids, from, to);
        } catch (Exception e) {
            // Fail closed: without leave we would mark leave days Absent.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Leave service unavailable: " + e.getMessage());
        }

        Map<String, Object> byEmployee = new LinkedHashMap<>();
        for (Long id : ids) {
            Optional<EmployeeView> empOpt = employees.findById(id);
            if (empOpt.isEmpty()) {
                continue;
            }
            EmployeeView emp = empOpt.get();
            WorkSchedule schedule = schedules.resolveForEmployee(emp).orElse(null);
            boolean hourly = schedule != null && "HOURLY".equalsIgnoreCase(schedule.getMode());
            ZoneId zone = zone(emp);
            LocalDate today = LocalDate.now(zone);
            boolean yesterdayStillOpen = LocalTime.now(zone).isBefore(LATE_CHECKOUT_LIMIT);
            Set<LocalDate> holidaySet = holidayDates(allHolidays, emp.getLocationId(), from, to);
            Map<LocalDate, String> leaveByDay = leaveByDay(approved.getOrDefault(id, List.of()), from, to);
            Map<LocalDate, AttendanceRecord> recs = records.getOrDefault(id, Map.of());

            List<Map<String, Object>> days = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                boolean holiday = holidaySet.contains(d);
                boolean working = !hourly && schedule != null && schedules.isWorkingDay(schedule, d) && !holiday;
                String leavePortion = leaveByDay.get(d); // FULL, HALF or null
                AttendanceRecord r = recs.get(d);
                boolean past = d.isBefore(today);
                String status;
                BigDecimal absence = BigDecimal.ZERO;
                int workedMinutes = 0;
                boolean missing = false;

                if (r != null && r.getCheckInAt() != null) {
                    if (r.getCheckOutAt() != null) {
                        workedMinutes = (int) Math.max(0, Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes());
                        status = hourly ? "W" : ("L".equalsIgnoreCase(r.getStatusCode()) ? "L" : "P");
                        // BR-05 — half day only on a working day without leave.
                        if (working && leavePortion == null && schedule.getHalfDayHours() != null
                                && workedMinutes < schedule.getHalfDayHours().multiply(BigDecimal.valueOf(60)).intValue()) {
                            status = "HD";
                            absence = HALF;
                        }
                    } else {
                        // FR-HRS-03 / BR-07 — open shift: missing once its day (plus the 06:00 grace) is over.
                        missing = d.isBefore(today.minusDays(1)) || (d.equals(today.minusDays(1)) && !yesterdayStillOpen);
                        status = missing ? "M" : (hourly ? "W" : "P");
                    }
                } else if ("FULL".equals(leavePortion)) {
                    status = "OL";
                } else if (!working) {
                    status = holiday ? "HO" : (hourly ? "NS" : "WO");
                } else if ("HALF".equals(leavePortion)) {
                    status = "OL";
                    absence = past ? HALF : BigDecimal.ZERO; // Decision D1
                } else if (past) {
                    status = "A";
                    absence = ONE;
                } else {
                    status = d.equals(today) ? "NC" : "-";
                }

                Map<String, Object> day = new LinkedHashMap<>();
                day.put("date", d.toString());
                day.put("working", working);
                day.put("holiday", holiday);
                day.put("status", status);
                day.put("leave", leavePortion);
                day.put("absence", absence);
                day.put("workedMinutes", workedMinutes);
                day.put("missingCheckout", missing);
                days.add(day);
            }
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("scheduleMode", hourly ? "HOURLY" : "FIXED");
            e.put("scheduleId", schedule == null ? null : schedule.getId());
            e.put("timeZone", zone.getId());
            e.put("today", today.toString());
            e.put("days", days);
            byEmployee.put(String.valueOf(id), e);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", ym.toString());
        out.put("employees", byEmployee);
        return out;
    }

    private static Set<LocalDate> holidayDates(List<Holiday> all, Long locationId, LocalDate from, LocalDate to) {
        return all.stream()
                .filter(h -> locationId != null && locationId.equals(h.getLocationId()) && !h.isOptionalFlag())
                .filter(h -> h.getStartDate() != null && h.getEndDate() != null)
                .flatMap(h -> h.getStartDate().datesUntil(h.getEndDate().plusDays(1)))
                .filter(d -> !d.isBefore(from) && !d.isAfter(to))
                .collect(Collectors.toSet());
    }

    /** FULL / HALF per calendar day from approved leave spans (half day = single-date request). */
    private static Map<LocalDate, String> leaveByDay(List<Map<String, Object>> spans, LocalDate from, LocalDate to) {
        Map<LocalDate, String> out = new HashMap<>();
        for (Map<String, Object> a : spans) {
            LocalDate s = LocalDate.parse(String.valueOf(a.get("startDate")));
            LocalDate e = LocalDate.parse(String.valueOf(a.get("endDate")));
            String part = a.get("halfDayPart") == null ? "FULL" : String.valueOf(a.get("halfDayPart")).toUpperCase(Locale.ROOT);
            boolean half = s.equals(e) && !"FULL".equals(part);
            for (LocalDate d = s.isBefore(from) ? from : s; !d.isAfter(e) && !d.isAfter(to); d = d.plusDays(1)) {
                if (half) {
                    out.putIfAbsent(d, "HALF");
                } else {
                    out.put(d, "FULL");
                }
            }
        }
        return out;
    }

    private static ZoneId zone(EmployeeView emp) {
        try {
            if (emp.getLocationTimeZone() != null && !emp.getLocationTimeZone().isBlank()) {
                return ZoneId.of(emp.getLocationTimeZone().trim());
            }
        } catch (Exception ignored) {
            // fall through
        }
        return ZoneId.of("Asia/Dhaka");
    }
}
