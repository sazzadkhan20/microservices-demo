package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.hrms.application.WorkLogService;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/work-logs")
@Tag(name = "Work Logs", description = "Config-driven work logs (FR-WL-01..08)")
public class WorkLogController {

    private final WorkLogService workLogService;

    public WorkLogController(WorkLogService workLogService) {
        this.workLogService = workLogService;
    }

    @GetMapping("/rules")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Work-log validation / checkout rule settings")
    public Map<String, Object> rules() {
        return workLogService.rules();
    }

    @GetMapping
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "List own work logs")
    public PageResponse<Map<String, Object>> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        return DynamicListSupport.page(
                workLogService.mine(),
                query,
                wl -> DynamicListSupport.containsIgnoreCase(String.valueOf(wl.get("note")), query.q())
                        || DynamicListSupport.containsIgnoreCase(String.valueOf(wl.get("taskTitle")), query.q()),
                wl -> Map.of(
                        "taskId", wl.get("taskId") == null ? "" : String.valueOf(wl.get("taskId")),
                        "workDate", wl.get("workDate") == null ? "" : String.valueOf(wl.get("workDate")),
                        "flagged", String.valueOf(Boolean.TRUE.equals(wl.get("flagged")))
                )
        );
    }

    @GetMapping("/team")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Team work logs with punched hours; filter missingOnly (FR-WL-07)")
    public List<Map<String, Object>> team(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "false") boolean missingOnly) {
        LocalDate start;
        LocalDate end;
        if (date != null && !date.isBlank()) {
            start = end = LocalDate.parse(date);
        } else {
            start = from == null || from.isBlank() ? LocalDate.now() : LocalDate.parse(from);
            end = to == null || to.isBlank() ? start : LocalDate.parse(to);
        }
        return workLogService.team(start, end, missingOnly);
    }

    @GetMapping("/employee/{id}")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Last N days of logs for an employee (FR-WL-08, default 14)")
    public List<Map<String, Object>> forEmployee(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "14") int days) {
        return workLogService.forEmployee(id, days);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "attendance", action = "checkin")
    @Operation(summary = "Add work log (15-min steps, assigned open tasks, edit window)")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        return workLogService.create(body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(feature = "attendance", action = "checkin")
    @Operation(summary = "Delete a work log within the edit window")
    public void delete(@PathVariable Long id) {
        workLogService.delete(id);
    }

    @PostMapping("/notify-missing")
    @RequiresPermission(feature = "attendance_settings", action = "edit")
    @Operation(summary = "Scan last N worked days and notify missing logs (FR-WL-06)")
    public Map<String, Object> notifyMissing() {
        return workLogService.notifyMissing();
    }
}
