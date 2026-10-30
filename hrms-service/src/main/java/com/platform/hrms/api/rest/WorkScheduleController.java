package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.common.security.AuthContext;
import com.platform.hrms.application.WorkScheduleService;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.WorkSchedule;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.security.RequiresPermission;
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
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/work-schedules")
@Tag(name = "Work Schedules", description = "FR-SCH-01…06 location/team/employee schedules + expected hours")
public class WorkScheduleController {

    private final WorkScheduleService workScheduleService;
    private final EmployeeRepository employeeRepository;

    public WorkScheduleController(WorkScheduleService workScheduleService, EmployeeRepository employeeRepository) {
        this.workScheduleService = workScheduleService;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping
    @RequiresPermission(feature = "attendance_settings", action = "view")
    @Operation(summary = "List work schedules (LOCATION / TEAM / EMPLOYEE)")
    public PageResponse<WorkSchedule> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        return DynamicListSupport.page(
                workScheduleService.listAll(),
                query,
                s -> DynamicListSupport.containsIgnoreCase(s.getScopeType(), query.q())
                        || DynamicListSupport.containsIgnoreCase(s.getMode(), query.q()),
                s -> Map.of(
                        "scopeType", s.getScopeType() == null ? "" : s.getScopeType(),
                        "scopeId", s.getScopeId() == null ? "" : String.valueOf(s.getScopeId()),
                        "mode", s.getMode() == null ? "" : s.getMode()
                )
        );
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

    @GetMapping("/resolve/{employeeId}")
    @RequiresPermission(feature = "attendance_settings", action = "view")
    @Operation(summary = "Resolved schedule for an employee (EMPLOYEE > TEAM > LOCATION)")
    public Map<String, Object> resolve(@PathVariable Long employeeId) {
        return workScheduleService.resolveView(employeeId);
    }

    @GetMapping("/expected-hours")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Expected hours for a day or period (FR-SCH-05 / FR-SCH-06)")
    public Map<String, Object> expectedHours(
            @RequestParam Long employeeId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Employee emp = employeeRepository.findById(employeeId)
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
