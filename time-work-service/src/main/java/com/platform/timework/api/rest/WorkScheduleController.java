package com.platform.timework.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.common.security.AuthContext;
import com.platform.timework.application.WorkScheduleService;
import com.platform.timework.application.support.DynamicListSupport;
import com.platform.timework.client.people.EmployeeView;
import com.platform.timework.domain.WorkSchedule;
import com.platform.timework.client.people.PeopleEmployeeGateway;
import com.platform.timework.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/work-schedules")
@Tag(name = "Work Schedules", description = "FR-SCH-01…06 location/team/employee schedules + expected hours")
public class WorkScheduleController {

    private final WorkScheduleService workScheduleService;
    private final PeopleEmployeeGateway employeeRepository;

    public WorkScheduleController(WorkScheduleService workScheduleService, PeopleEmployeeGateway employeeRepository) {
        this.workScheduleService = workScheduleService;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping
    @RequiresPermission(feature = "attendance_settings", action = "view")
    @Operation(summary = "List work schedules (LOCATION / TEAM / EMPLOYEE), paginated")
    public PageResponse<WorkSchedule> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        // Stable product order: location bases first, then team/person overrides.
        List<WorkSchedule> all = workScheduleService.listAll().stream()
                .sorted(java.util.Comparator
                        .comparingInt((WorkSchedule s) -> scopeRank(s.getScopeType()))
                        .thenComparing(s -> s.getScopeId() == null ? 0L : s.getScopeId())
                        .thenComparing(s -> s.getId() == null ? 0L : s.getId()))
                .toList();
        return DynamicListSupport.page(
                all,
                query,
                s -> DynamicListSupport.containsIgnoreCase(s.getScopeType(), query.q())
                        || DynamicListSupport.containsIgnoreCase(s.getMode(), query.q())
                        || DynamicListSupport.containsIgnoreCase(
                        s.getScopeId() == null ? "" : String.valueOf(s.getScopeId()), query.q())
                        || DynamicListSupport.containsIgnoreCase(s.getStartTime(), query.q())
                        || DynamicListSupport.containsIgnoreCase(s.getEndTime(), query.q()),
                s -> Map.of(
                        "id", s.getId() == null ? "" : String.valueOf(s.getId()),
                        "scopeType", s.getScopeType() == null ? "" : s.getScopeType(),
                        "scopeId", s.getScopeId() == null ? "" : String.valueOf(s.getScopeId()),
                        "mode", s.getMode() == null ? "" : s.getMode(),
                        "startTime", s.getStartTime() == null ? "" : s.getStartTime(),
                        "endTime", s.getEndTime() == null ? "" : s.getEndTime()
                )
        );
    }

    private static int scopeRank(String scopeType) {
        if (scopeType == null) {
            return 99;
        }
        return switch (scopeType.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "LOCATION" -> 0;
            case "TEAM" -> 1;
            case "EMPLOYEE" -> 2;
            default -> 50;
        };
    }

    @GetMapping("/rules")
    @RequiresPermission(feature = "attendance_settings", action = "view")
    @Operation(summary = "Schedule config from tenant scheduleRules")
    public Map<String, Object> rules() {
        WorkScheduleService.Rules r = workScheduleService.loadRules();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("modes", r.modes());
        out.put("scopes", r.scopes());
        out.put("resolveOrder", r.resolveOrder());
        out.put("defaultGraceMinutes", r.defaultGraceMinutes());
        out.put("defaultHalfDayHours", r.defaultHalfDayHours());
        out.put("defaultCheckoutCutoff", r.defaultCheckoutCutoff());
        out.put("requireUniqueScope", r.requireUniqueScope());
        out.put("patternTemplates", r.patternTemplates());
        return out;
    }

    @GetMapping("/mine")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Resolved schedule for the current employee (home / self-service)")
    public Map<String, Object> mine() {
        return workScheduleService.resolveView(AuthContext.requireEmployeeId());
    }

    @GetMapping("/resolve/{employeeId}")
    @RequiresPermission(feature = "attendance_settings", action = "view")
    @Operation(summary = "Resolved schedule for an employee (EMPLOYEE > TEAM > LOCATION)")
    public Map<String, Object> resolve(@PathVariable Long employeeId) {
        return workScheduleService.resolveView(employeeId);
    }

    @GetMapping("/for-scope")
    @RequiresPermission(feature = "attendance_settings", action = "view")
    @Operation(summary = "Current schedule for a LOCATION/TEAM/EMPLOYEE scope (or product defaults)")
    public Map<String, Object> forScope(
            @RequestParam String scopeType,
            @RequestParam Long scopeId) {
        return workScheduleService.scopePreview(scopeType, scopeId);
    }

    @PostMapping("/ensure-location-defaults")
    @RequiresPermission(feature = "attendance_settings", action = "edit")
    @Operation(summary = "Create missing LOCATION base schedules (Mon–Fri 09:00–17:00); never overwrite existing")
    public Map<String, Object> ensureLocationDefaults(@RequestBody Map<String, Object> body) {
        List<Long> ids = new ArrayList<>();
        Object raw = body == null ? null : body.get("locationIds");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o == null) continue;
                if (o instanceof Number n) {
                    ids.add(n.longValue());
                } else {
                    String s = String.valueOf(o).trim();
                    if (!s.isEmpty()) {
                        ids.add(Long.valueOf(s));
                    }
                }
            }
        } else if (body != null && body.get("locationId") != null) {
            Object one = body.get("locationId");
            ids.add(one instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(one).trim()));
        }
        List<WorkSchedule> saved = workScheduleService.ensureDefaultLocationSchedules(ids);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ensured", saved.size());
        out.put("schedules", saved);
        return out;
    }

    @GetMapping("/expected-hours")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Expected hours for a day or period (FR-SCH-05 / FR-SCH-06)")
    public Map<String, Object> expectedHours(
            @RequestParam Long employeeId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        EmployeeView emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        // OWN scope: non-managers only see self unless they have attendance_settings
        Long actor = AuthContext.getEmployeeId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeId", employeeId);
        if (from != null && to != null && !from.isBlank() && !to.isBlank()) {
            LocalDate f = LocalDate.parse(from.trim());
            LocalDate t = LocalDate.parse(to.trim());
            BigDecimal hours = workScheduleService.expectedHoursForPeriod(emp, f, t);
            out.put("from", f.toString());
            out.put("to", t.toString());
            out.put("expectedHours", hours);
        } else {
            LocalDate day = date == null || date.isBlank()
                    ? LocalDate.now()
                    : LocalDate.parse(date.trim());
            out.put("date", day.toString());
            out.put("expectedHours", workScheduleService.expectedHoursForDay(emp, day));
        }
        out.put("actorEmployeeId", actor);
        return out;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "attendance_settings", action = "edit")
    @Operation(summary = "Create schedule (unique per scope; audited)")
    public WorkSchedule create(@RequestBody WorkSchedule body) {
        return workScheduleService.create(body);
    }

    @PutMapping("/{id}")
    @RequiresPermission(feature = "attendance_settings", action = "edit")
    @Operation(summary = "Update schedule (audited)")
    public WorkSchedule update(@PathVariable Long id, @RequestBody WorkSchedule body) {
        return workScheduleService.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(feature = "attendance_settings", action = "edit")
    @Operation(summary = "Delete schedule (audited)")
    public void delete(@PathVariable Long id) {
        workScheduleService.delete(id);
    }
}
