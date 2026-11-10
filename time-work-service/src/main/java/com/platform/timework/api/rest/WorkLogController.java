package com.platform.timework.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.timework.application.MissingWorkLogNotifyJob;
import com.platform.timework.application.WorkLogService;
import com.platform.timework.application.support.DynamicListSupport;
import com.platform.timework.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/work-logs")
@Tag(name = "Work Logs", description = "Config-driven work logs (FR-WL-01..08)")
public class WorkLogController {

    private final WorkLogService workLogService;
    private final ObjectProvider<MissingWorkLogNotifyJob> missingNotifyJob;

    public WorkLogController(WorkLogService workLogService, ObjectProvider<MissingWorkLogNotifyJob> missingNotifyJob) {
        this.workLogService = workLogService;
        this.missingNotifyJob = missingNotifyJob;
    }

    @GetMapping("/rules")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Work-log validation / checkout rule settings")
    public Map<String, Object> rules() {
        return workLogService.rules();
    }

    @PutMapping("/rules")
    @RequiresPermission(feature = "attendance_settings", action = "edit")
    @Operation(summary = "Update work-log checkout + logging rules (admin)")
    public Map<String, Object> updateRules(@RequestBody Map<String, Object> body) {
        return workLogService.updateRules(body);
    }

    @GetMapping
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "List own work logs (paginated)")
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
                        "flagged", String.valueOf(Boolean.TRUE.equals(wl.get("flagged"))),
                        "editable", String.valueOf(Boolean.TRUE.equals(wl.get("editable")))
                )
        );
    }

    /**
     * Flat path (not nested under /team) so gateways / older proxies never treat it as a
     * static resource under /team/*. Same style as /rules and /notify-missing.
     */
    @GetMapping({"/team-options", "/team/options"})
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Employee + task filter options for team work logs (FR-WL-07)")
    public Map<String, Object> teamOptions(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate start = from == null || from.isBlank() ? LocalDate.now() : LocalDate.parse(from);
        LocalDate end = to == null || to.isBlank() ? start : LocalDate.parse(to);
        return workLogService.teamFilterOptions(start, end);
    }

    @GetMapping("/team")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Team work logs with punched hours; employeeId/taskId/missingOnly + pagination (FR-WL-07)")
    public Object team(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "false") boolean missingOnly,
            @RequestParam(required = false, defaultValue = "false") boolean optionsOnly,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String q) {

        LocalDate start;
        LocalDate end;
        if (date != null && !date.isBlank()) {
            start = end = LocalDate.parse(date);
        } else {
            start = from == null || from.isBlank() ? LocalDate.now() : LocalDate.parse(from);
            end = to == null || to.isBlank() ? start : LocalDate.parse(to);
        }

        // Fallback for clients that cannot call /team-options (uses the already-routed /team path).
        if (optionsOnly) {
            return workLogService.teamFilterOptions(start, end);
        }

        // First-class employeeId/taskId — do not rely on filter.* (fragile through proxies).
        PageQuery query = new PageQuery(page, size, sort == null || sort.isBlank() ? "workDate,desc" : sort, q, Map.of());
        return DynamicListSupport.page(
                workLogService.team(start, end, missingOnly, employeeId, taskId),
                query,
                wl -> DynamicListSupport.containsIgnoreCase(String.valueOf(wl.get("employeeName")), query.q())
                        || DynamicListSupport.containsIgnoreCase(String.valueOf(wl.get("taskTitle")), query.q())
                        || DynamicListSupport.containsIgnoreCase(String.valueOf(wl.get("note")), query.q()),
                wl -> {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("employeeId", wl.get("employeeId") == null ? "" : String.valueOf(wl.get("employeeId")));
                    f.put("taskId", wl.get("taskId") == null ? "" : String.valueOf(wl.get("taskId")));
                    f.put("workDate", wl.get("workDate") == null ? "" : String.valueOf(wl.get("workDate")));
                    f.put("missingLog", String.valueOf(Boolean.TRUE.equals(wl.get("missingLog"))));
                    f.put("flagged", String.valueOf(Boolean.TRUE.equals(wl.get("flagged"))));
                    return f;
                }
        );
    }

    @GetMapping("/employee/{id}")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Last N days of logs for an employee (FR-WL-08, default 14)")
    public java.util.List<Map<String, Object>> forEmployee(
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
        MissingWorkLogNotifyJob job = missingNotifyJob.getIfAvailable();
        if (job != null) {
            return job.runNow();
        }
        return workLogService.notifyMissing();
    }
}
