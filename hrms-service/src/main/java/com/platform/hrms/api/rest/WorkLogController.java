package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.common.security.AuthContext;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.domain.WorkLog;
import com.platform.hrms.infrastructure.persistence.WorkLogRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/work-logs")
@Tag(name = "Work Logs", description = "Time / work logs per task per employee")
public class WorkLogController {

    private final WorkLogRepository workLogRepository;

    public WorkLogController(WorkLogRepository workLogRepository) {
        this.workLogRepository = workLogRepository;
    }

    @GetMapping
    @Operation(summary = "List own work logs (paginated, filterable by taskId or date)")
    public PageResponse<WorkLog> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        boolean all = "true".equalsIgnoreCase(params.getOrDefault("filter.all", params.getOrDefault("all", "")));
        if (all) {
            return DynamicListSupport.page(
                    workLogRepository.findAll(),
                    query,
                    wl -> DynamicListSupport.containsIgnoreCase(wl.getNote(), query.q()),
                    wl -> Map.of(
                            "taskId", wl.getTaskId() == null ? "" : String.valueOf(wl.getTaskId()),
                            "workDate", wl.getWorkDate() == null ? "" : wl.getWorkDate().toString(),
                            "employeeId", wl.getEmployeeId() == null ? "" : String.valueOf(wl.getEmployeeId())
                    )
            );
        }
        Long empId = AuthContext.requireEmployeeId();
        return DynamicListSupport.page(
                workLogRepository.findByEmployeeIdOrderByWorkDateDesc(empId),
                query,
                wl -> DynamicListSupport.containsIgnoreCase(wl.getNote(), query.q()),
                wl -> Map.of(
                        "taskId", wl.getTaskId() == null ? "" : String.valueOf(wl.getTaskId()),
                        "workDate", wl.getWorkDate() == null ? "" : wl.getWorkDate().toString()
                )
        );
    }

    @GetMapping("/team")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Team work logs (all visible logs for managers/HR)")
    public PageResponse<WorkLog> team(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        return DynamicListSupport.page(
                workLogRepository.findAll(),
                query,
                wl -> DynamicListSupport.containsIgnoreCase(wl.getNote(), query.q()),
                wl -> Map.of(
                        "taskId", wl.getTaskId() == null ? "" : String.valueOf(wl.getTaskId()),
                        "workDate", wl.getWorkDate() == null ? "" : wl.getWorkDate().toString(),
                        "employeeId", wl.getEmployeeId() == null ? "" : String.valueOf(wl.getEmployeeId())
                )
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "attendance", action = "checkin")
    @Operation(summary = "Log time against a task")
    public WorkLog create(@RequestBody WorkLog request) {
        WorkLog log = new WorkLog();
        log.setEmployeeId(AuthContext.requireEmployeeId());
        log.setWorkDate(request.getWorkDate());
        log.setTaskId(request.getTaskId());
        log.setMinutes(request.getMinutes());
        log.setNote(request.getNote());
        log.setCreatedAt(Instant.now());
        return workLogRepository.save(log);
    }
}
