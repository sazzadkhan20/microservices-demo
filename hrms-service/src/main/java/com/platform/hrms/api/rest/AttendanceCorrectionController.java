package com.platform.hrms.api.rest;

import com.platform.hrms.application.AttendanceCorrectionService;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance/corrections")
@Tag(name = "Attendance Corrections", description = "Employee correction requests with HR / manager approval")
public class AttendanceCorrectionController {

    private final AttendanceCorrectionService service;

    public AttendanceCorrectionController(AttendanceCorrectionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "attendance", action = "checkin")
    @Operation(summary = "Request an attendance correction (self)",
            description = "Body: workDate (yyyy-MM-dd), requestedCheckIn / requestedCheckOut (ISO-8601 instant), reason")
    public Map<String, Object> request(@RequestBody Map<String, Object> body) {
        LocalDate workDate;
        try {
            workDate = body.get("workDate") == null ? null : LocalDate.parse(String.valueOf(body.get("workDate")));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("workDate must be yyyy-MM-dd");
        }
        return service.request(
                workDate,
                parseInstant(body.get("requestedCheckIn"), "requestedCheckIn"),
                parseInstant(body.get("requestedCheckOut"), "requestedCheckOut"),
                body.get("reason") == null ? null : String.valueOf(body.get("reason")));
    }

    @GetMapping("/my")
    @RequiresPermission(feature = "attendance", action = "checkin")
    @Operation(summary = "My correction requests")
    public List<Map<String, Object>> mine() {
        return service.mine();
    }

    @GetMapping
    @RequiresPermission(feature = "attendance", action = "correct")
    @Operation(summary = "List correction requests (default status=PENDING; ALL for everything)")
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "PENDING") String status) {
        return service.list(status);
    }

    @PostMapping("/{id}/approve")
    @RequiresPermission(feature = "attendance", action = "correct")
    @Operation(summary = "Approve correction and apply it to attendance_record")
    public Map<String, Object> approve(@PathVariable Long id,
                                       @RequestBody(required = false) Map<String, Object> body) {
        return service.approve(id, note(body));
    }

    @PostMapping("/{id}/reject")
    @RequiresPermission(feature = "attendance", action = "correct")
    @Operation(summary = "Reject correction request")
    public Map<String, Object> reject(@PathVariable Long id,
                                      @RequestBody(required = false) Map<String, Object> body) {
        return service.reject(id, note(body));
    }

    private static String note(Map<String, Object> body) {
        if (body == null) return null;
        Object n = body.get("note") != null ? body.get("note") : body.get("reviewNote");
        return n == null ? null : String.valueOf(n);
    }

    private static Instant parseInstant(Object v, String field) {
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(v).trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " must be ISO-8601 instant, e.g. 2026-01-05T09:00:00Z");
        }
    }
}
