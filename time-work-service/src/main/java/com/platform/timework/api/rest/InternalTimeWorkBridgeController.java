package com.platform.timework.api.rest;

import com.platform.timework.application.WorkScheduleService;
import com.platform.timework.client.people.EmployeeView;
import com.platform.timework.domain.Holiday;
import com.platform.timework.domain.WorkSchedule;
import com.platform.timework.infrastructure.persistence.AttendanceRecordRepository;
import com.platform.timework.client.people.PeopleEmployeeGateway;
import com.platform.timework.infrastructure.persistence.HolidayRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S2S bridge for People / Leave / Payroll (service-key). Schedules &amp; holidays are Time&amp;Work SoT.
 */
@RestController
@RequestMapping("/api/internal")
public class InternalTimeWorkBridgeController {

    private final WorkScheduleService workScheduleService;
    private final PeopleEmployeeGateway employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final HolidayRepository holidayRepository;

    public InternalTimeWorkBridgeController(
            WorkScheduleService workScheduleService,
            PeopleEmployeeGateway employeeRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            HolidayRepository holidayRepository) {
        this.workScheduleService = workScheduleService;
        this.employeeRepository = employeeRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.holidayRepository = holidayRepository;
    }

    @GetMapping("/schedules/resolve/{employeeId}")
    public Map<String, Object> resolveSchedule(@PathVariable Long employeeId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeId", employeeId);
        var resolved = workScheduleService.resolveForEmployeeId(employeeId);
        if (resolved.isEmpty()) {
            out.put("mode", "");
            out.put("workingDays", List.of());
            return out;
        }
        WorkSchedule s = resolved.get();
        out.put("mode", s.getMode() == null ? "" : s.getMode());
        out.put("workingDays", workScheduleService.parseWorkingDays(s));
        out.put("scopeType", s.getScopeType());
        out.put("scopeId", s.getScopeId());
        out.put("startTime", s.getStartTime());
        out.put("endTime", s.getEndTime());
        out.put("weeklyHours", s.getWeeklyHours());
        return out;
    }

    @GetMapping("/holidays/in-range")
    public List<String> holidaysInRange(
            @RequestParam Long locationId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        List<String> dates = new ArrayList<>();
        for (Holiday h : holidayRepository.findAll()) {
            if (!locationId.equals(h.getLocationId()) || h.isOptionalFlag()) {
                continue;
            }
            if (h.getStartDate() == null || h.getEndDate() == null) {
                continue;
            }
            LocalDate d = h.getStartDate().isBefore(from) ? from : h.getStartDate();
            LocalDate end = h.getEndDate().isAfter(to) ? to : h.getEndDate();
            for (; !d.isAfter(end); d = d.plusDays(1)) {
                if (!d.isBefore(from) && !d.isAfter(to)) {
                    dates.add(d.toString());
                }
            }
        }
        return dates;
    }

    @PostMapping("/work-schedules/sync-from-pattern")
    public Map<String, Object> syncFromPattern(@RequestBody Map<String, Object> body) {
        Long employeeId = asLong(body.get("employeeId"));
        // EmployeeView SoT is People — load via API, never persist employee rows here.
        EmployeeView emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found in People: " + employeeId));
        if (body.get("workPattern") != null) {
            emp.setWorkPattern(String.valueOf(body.get("workPattern")));
        }
        if (body.get("locationId") != null) {
            emp.setLocationId(asLong(body.get("locationId")));
        }
        workScheduleService.syncFromWorkPattern(emp);
        return Map.of("ok", true, "employeeId", employeeId);
    }

    @GetMapping("/attendance-facts/{employeeId}")
    public Map<String, Object> attendanceFacts(
            @PathVariable Long employeeId,
            @RequestParam String period) {
        YearMonth ym = YearMonth.parse(period.substring(0, 7));
        var from = ym.atDay(1);
        var to = ym.atEndOfMonth();
        BigDecimal hours = attendanceRecordRepository.findAll().stream()
                .filter(r -> employeeId.equals(r.getEmployeeId()))
                .filter(r -> r.getWorkDate() != null && !r.getWorkDate().isBefore(from) && !r.getWorkDate().isAfter(to))
                .map(r -> {
                    if (r.getCheckInAt() == null || r.getCheckOutAt() == null) {
                        return BigDecimal.ZERO;
                    }
                    long mins = java.time.Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes();
                    if (mins <= 0) {
                        return BigDecimal.ZERO;
                    }
                    return BigDecimal.valueOf(mins).divide(BigDecimal.valueOf(60), 4, java.math.RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeId", employeeId);
        out.put("period", period);
        out.put("regularHours", hours);
        out.put("absenceDays", 0);
        out.put("lateCount", 0);
        return out;
    }

    private static Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(v));
    }
}
