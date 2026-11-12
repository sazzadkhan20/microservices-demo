package com.platform.timework.api.rest;

import com.platform.common.security.InternalCalls;
import com.platform.common.security.PublicEndpoint;
import com.platform.timework.application.WorkScheduleService;
import com.platform.timework.client.people.EmployeeView;
import com.platform.timework.client.people.PeopleEmployeeGateway;
import com.platform.timework.domain.AttendanceRecord;
import com.platform.timework.domain.Holiday;
import com.platform.timework.domain.WorkSchedule;
import com.platform.timework.infrastructure.persistence.AttendanceRecordRepository;
import com.platform.timework.infrastructure.persistence.HolidayRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * S2S bridge for People / Leave / Payroll (requires X-Internal-Key — SEC-06 / TW-01).
 */
@RestController
@RequestMapping("/api/internal")
@PublicEndpoint(reason = "S2S bridge; gated by X-Internal-Key on each handler")
public class InternalTimeWorkBridgeController {

    private final WorkScheduleService workScheduleService;
    private final PeopleEmployeeGateway employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final HolidayRepository holidayRepository;
    private final String internalKey;

    public InternalTimeWorkBridgeController(
            WorkScheduleService workScheduleService,
            PeopleEmployeeGateway employeeRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            HolidayRepository holidayRepository,
            @Value("${platform.security.internal-key}") String internalKey) {
        this.workScheduleService = workScheduleService;
        this.employeeRepository = employeeRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.holidayRepository = holidayRepository;
        this.internalKey = internalKey;
    }

    @GetMapping("/schedules/resolve/{employeeId}")
    public Map<String, Object> resolveSchedule(@PathVariable Long employeeId, HttpServletRequest request) {
        requireInternal(request);
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
            @RequestParam LocalDate to,
            HttpServletRequest request) {
        requireInternal(request);
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
    public Map<String, Object> syncFromPattern(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        requireInternal(request);
        Long employeeId = asLong(body.get("employeeId"));
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

    @PostMapping("/work-schedules/ensure-location-default")
    public Map<String, Object> ensureLocationDefault(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        requireInternal(request);
        Long locationId = asLong(body == null ? null : body.get("locationId"));
        if (locationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "locationId is required");
        }
        WorkSchedule saved = workScheduleService.ensureDefaultLocationSchedule(locationId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("locationId", locationId);
        out.put("scheduleId", saved.getId());
        out.put("mode", saved.getMode());
        out.put("startTime", saved.getStartTime());
        out.put("endTime", saved.getEndTime());
        return out;
    }

    private void requireInternal(HttpServletRequest request) {
        if (!InternalCalls.isTrusted(request.getHeader(InternalCalls.HEADER), internalKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Internal-Key required");
        }
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
