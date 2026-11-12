package com.platform.timework.api.rest;

import com.platform.timework.application.DashboardWorkHoursService;
import com.platform.timework.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Attendance / work-hours admin dashboard slices")
public class DashboardController {

    private final DashboardWorkHoursService dashboardWorkHoursService;

    public DashboardController(DashboardWorkHoursService dashboardWorkHoursService) {
        this.dashboardWorkHoursService = dashboardWorkHoursService;
    }

    @GetMapping("/work-hours")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Work-hours table (batched, report-scoped) with search + pagination")
    public Map<String, Object> workHours(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false, defaultValue = "name") String sort,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return dashboardWorkHoursService.workHours(
                LocalDate.parse(from.trim()),
                LocalDate.parse(to.trim()),
                locationId,
                teamId,
                sort,
                q,
                page,
                size);
    }

    @GetMapping("/attendance-today")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Today's attendance KPIs (batched leave, report scope)")
    public Map<String, Object> attendanceToday(@RequestParam(required = false) String date) {
        LocalDate d = date == null || date.isBlank() ? null : LocalDate.parse(date.trim());
        return dashboardWorkHoursService.attendanceToday(d);
    }

    @GetMapping("/employee/{id}/attendance")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "One employee attendance day-book for a period (matches work-hours classification)")
    public Map<String, Object> employeeAttendance(
            @PathVariable Long id,
            @RequestParam String from,
            @RequestParam String to) {
        return dashboardWorkHoursService.employeeAttendance(
                id,
                LocalDate.parse(from.trim()),
                LocalDate.parse(to.trim()));
    }
}
