package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.domain.WorkSchedule;
import com.platform.hrms.infrastructure.persistence.WorkScheduleRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/work-schedules")
@Tag(name = "Work Schedules")
public class WorkScheduleController {

    private final WorkScheduleRepository repository;

    public WorkScheduleController(WorkScheduleRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @RequiresPermission(feature = "org", action = "view")
    @Operation(summary = "List work schedules (location/team/employee scope)")
    public PageResponse<WorkSchedule> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        return DynamicListSupport.page(
                repository.findAll(),
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "org", action = "manage")
    public WorkSchedule create(@RequestBody WorkSchedule body) {
        return repository.save(body);
    }

    @PutMapping("/{id}")
    @RequiresPermission(feature = "org", action = "manage")
    public WorkSchedule update(@PathVariable Long id, @RequestBody WorkSchedule body) {
        WorkSchedule s = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + id));
        if (body.getScopeType() != null) s.setScopeType(body.getScopeType());
        if (body.getScopeId() != null) s.setScopeId(body.getScopeId());
        if (body.getMode() != null) s.setMode(body.getMode());
        if (body.getWorkingDays() != null) s.setWorkingDays(body.getWorkingDays());
        if (body.getStartTime() != null) s.setStartTime(body.getStartTime());
        if (body.getEndTime() != null) s.setEndTime(body.getEndTime());
        if (body.getGraceMinutes() != null) s.setGraceMinutes(body.getGraceMinutes());
        if (body.getHalfDayHours() != null) s.setHalfDayHours(body.getHalfDayHours());
        if (body.getCheckoutCutoff() != null) s.setCheckoutCutoff(body.getCheckoutCutoff());
        return repository.save(s);
    }
}
