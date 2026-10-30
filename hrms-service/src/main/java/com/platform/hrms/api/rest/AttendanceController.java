package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.hrms.api.dto.AttendanceActionRequest;
import com.platform.hrms.application.AttendanceService;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
@Tag(name = "Attendance", description = "Check-in / check-out and history (schedule + after-midnight rules)")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/today")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Today's attendance + late-night checkout flag")
    public Map<String, Object> today() {
        return attendanceService.today();
    }

    @PostMapping({"/check-in", "/checkin"})
    @RequiresPermission(feature = "attendance", action = "checkin")
    @Operation(summary = "Check in once per day (captures time, TZ, geo, IP, device, note)")
    public Map<String, Object> checkIn(HttpServletRequest request,
                                       @RequestBody(required = false) AttendanceActionRequest body) {
        String loc = body == null ? null : body.locationLabel();
        String note = body == null ? null : body.note();
        Double lat = body == null ? null : body.latitude();
        Double lng = body == null ? null : body.longitude();
        String tz = body == null ? null : body.timeZone();
        String deviceType = body == null ? null : body.deviceType();
        return attendanceService.checkIn(request, loc, note, lat, lng, tz, deviceType);
    }

    @PostMapping({"/check-out", "/checkout"})
    @RequiresPermission(feature = "attendance", action = "checkin")
    @Operation(summary = "Check out (before 06:00 may attribute to yesterday; FR-ATT-03)")
    public Map<String, Object> checkOut(HttpServletRequest request,
                                        @RequestBody(required = false) AttendanceActionRequest body) {
        String note = body == null ? null : body.note();
        String loc = body == null ? null : body.locationLabel();
        Double lat = body == null ? null : body.latitude();
        Double lng = body == null ? null : body.longitude();
        String tz = body == null ? null : body.timeZone();
        String deviceType = body == null ? null : body.deviceType();
        return attendanceService.checkOut(request, note, loc, lat, lng, tz, deviceType);
    }

    @GetMapping("/hours-summary")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Worked / expected / logged hours for today, week, month (FR-HRS-04)")
    public Map<String, Object> hoursSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate f = from == null || from.isBlank() ? null : LocalDate.parse(from);
        LocalDate t = to == null || to.isBlank() ? null : LocalDate.parse(to);
        return attendanceService.hoursSummary(f, t);
    }

    @GetMapping({"", "/my"})
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "My attendance history (paginated)")
    public PageResponse<Map<String, Object>> history(@RequestParam Map<String, String> params) {
        return attendanceService.myHistory(PageQuery.fromParams(params));
    }

    @GetMapping("/team")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Team attendance for a date (RBAC scope ALL/TEAM)")
    public List<Map<String, Object>> team(@RequestParam(required = false) String date) {
        LocalDate d = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        return attendanceService.teamForDate(d);
    }

    @GetMapping("/employee/{id}")
    @RequiresPermission(feature = "attendance", action = "view")
    @Operation(summary = "Attendance history for one employee")
    public PageResponse<Map<String, Object>> employeeHistory(@PathVariable Long id,
                                                             @RequestParam Map<String, String> params) {
        return attendanceService.historyForEmployee(id, PageQuery.fromParams(params));
    }

    @PostMapping("/close-missing")
    @RequiresPermission(feature = "attendance_settings", action = "edit")
    @Operation(summary = "Mark open check-ins past schedule checkoutCutoff as Missing (FR-SCH-02)")
    public Map<String, Object> closeMissing(@RequestParam(required = false) String date) {
        LocalDate d = date == null || date.isBlank() ? null : LocalDate.parse(date);
        return attendanceService.closeMissingCheckouts(d);
    }

    @PostMapping("/correct")
    @RequiresPermission(feature = "attendance", action = "correct")
    @Operation(summary = "HR edit another person's attendance with a reason (FR-ATT-08) — self-edit forbidden")
    public Map<String, Object> correct(@RequestBody Map<String, Object> body) {
        Long employeeId = body.get("employeeId") == null ? null : Long.valueOf(String.valueOf(body.get("employeeId")));
        LocalDate workDate = LocalDate.parse(String.valueOf(body.get("workDate")));
        Instant checkIn = body.get("checkInAt") == null ? null : Instant.parse(String.valueOf(body.get("checkInAt")));
        Instant checkOut = body.get("checkOutAt") == null ? null : Instant.parse(String.valueOf(body.get("checkOutAt")));
        String statusCode = body.get("statusCode") == null ? null : String.valueOf(body.get("statusCode"));
        String statusLabel = body.get("statusLabel") == null ? null : String.valueOf(body.get("statusLabel"));
        Object reasonRaw = body.get("reason") != null ? body.get("reason") : body.get("note");
        String note = reasonRaw == null ? null : String.valueOf(reasonRaw);
        return attendanceService.correct(employeeId, workDate, checkIn, checkOut, statusCode, statusLabel, note);
    }
}
