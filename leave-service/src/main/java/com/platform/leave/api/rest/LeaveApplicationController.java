package com.platform.leave.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.leave.api.dto.LeaveApplicationResponse;
import com.platform.leave.api.dto.LeaveApplyRequest;
import com.platform.leave.api.dto.LeaveDecisionRequest;
import com.platform.leave.api.dto.ReliefAssignRequest;
import com.platform.leave.application.LeaveApplicationService;
import com.platform.leave.application.support.DynamicListSupport;
import com.platform.leave.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leave-applications")
@Tag(name = "Leave Applications", description = "Config-driven leave apply, relief, approve, reject")
public class LeaveApplicationController {

    private final LeaveApplicationService leaveApplicationService;

    public LeaveApplicationController(LeaveApplicationService leaveApplicationService) {
        this.leaveApplicationService = leaveApplicationService;
    }

    @GetMapping
    @RequiresPermission(feature = "leave", action = "view")
    @Operation(summary = "List leave applications (paginated / searchable / filterable)",
            description = "Rows are limited by the caller's leave:view data scope: OWN = own, "
                    + "TEAM = self + direct reports, ALL = everyone. "
                    + "filter.from + filter.to (or from/to) narrow to applications overlapping that date range. "
                    + "q matches leave type, reason, status, or employee id.")
    public PageResponse<LeaveApplicationResponse> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        Map<String, String> filters = new LinkedHashMap<>(query.filters());
        LocalDate rangeFrom = parseDate(firstNonBlank(filters.remove("from"), filters.remove("overlapsFrom")));
        LocalDate rangeTo = parseDate(firstNonBlank(filters.remove("to"), filters.remove("overlapsTo")));

        List<LeaveApplicationResponse> rows = leaveApplicationService.listForCaller();
        if (rangeFrom != null && rangeTo != null) {
            final LocalDate from = rangeFrom;
            final LocalDate to = rangeTo;
            rows = rows.stream().filter(a -> overlaps(a.startDate(), a.endDate(), from, to)).toList();
        }

        PageQuery cleaned = new PageQuery(query.page(), query.size(), query.sort(), query.q(), filters);
        return DynamicListSupport.page(
                rows,
                cleaned,
                a -> DynamicListSupport.containsIgnoreCase(a.leaveTypeCode(), cleaned.q())
                        || DynamicListSupport.containsIgnoreCase(a.reason(), cleaned.q())
                        || DynamicListSupport.containsIgnoreCase(a.status(), cleaned.q())
                        || DynamicListSupport.containsIgnoreCase(
                                a.employeeId() == null ? null : String.valueOf(a.employeeId()), cleaned.q()),
                a -> Map.of(
                        "status", a.status() == null ? "" : a.status(),
                        "leaveTypeCode", a.leaveTypeCode() == null ? "" : a.leaveTypeCode(),
                        "employeeId", a.employeeId() == null ? "" : String.valueOf(a.employeeId()),
                        "startDate", a.startDate() == null ? "" : a.startDate().toString(),
                        "endDate", a.endDate() == null ? "" : a.endDate().toString()
                )
        );
    }

    private static boolean overlaps(LocalDate start, LocalDate end, LocalDate from, LocalDate to) {
        if (start == null || from == null || to == null) {
            return false;
        }
        LocalDate finish = end != null ? end : start;
        return !start.isAfter(to) && !finish.isBefore(from);
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    @GetMapping("/{id}")
    @RequiresPermission(feature = "leave", action = "view")
    @Operation(summary = "Get one leave application + approval steps")
    public LeaveApplicationResponse get(@PathVariable Long id) {
        return leaveApplicationService.getForCaller(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "leave", action = "request")
    @Operation(summary = "Apply for leave",
            description = "Applicant bound to AuthContext; leave:manage required to apply for another employee.")
    public LeaveApplicationResponse apply(@Valid @RequestBody LeaveApplyRequest request) {
        return leaveApplicationService.apply(request);
    }

    @PostMapping("/{id}/assign-relief")
    @RequiresPermission(feature = "leave", action = "manage")
    @Operation(summary = "Assign relief when status is PENDING_RELIEF")
    public LeaveApplicationResponse assignRelief(@PathVariable Long id,
                                                 @Valid @RequestBody ReliefAssignRequest request) {
        return leaveApplicationService.assignRelief(id, request);
    }

    @PostMapping("/{id}/approve")
    @RequiresPermission(feature = "leave", action = "approve")
    @Operation(summary = "Approve current step",
            description = "Approver = authenticated employee; approverEmployeeId in the body is ignored.")
    public LeaveApplicationResponse approve(@PathVariable Long id,
                                            @Valid @RequestBody(required = false) LeaveDecisionRequest request) {
        return leaveApplicationService.approve(id, request);
    }

    @PostMapping("/{id}/withdraw")
    @RequiresPermission(feature = "leave", action = "request")
    @Operation(summary = "Withdraw a pending leave request (owner or leave:manage)")
    public LeaveApplicationResponse withdraw(@PathVariable Long id) {
        return leaveApplicationService.withdraw(id);
    }

    @PostMapping("/{id}/reject")
    @RequiresPermission(feature = "leave", action = "approve")
    @Operation(summary = "Reject leave",
            description = "Approver = authenticated employee; approverEmployeeId in the body is ignored.")
    public LeaveApplicationResponse reject(@PathVariable Long id,
                                           @Valid @RequestBody(required = false) LeaveDecisionRequest request) {
        return leaveApplicationService.reject(id, request);
    }

    @PostMapping("/preview")
    @RequiresPermission(feature = "leave", action = "request")
    @Operation(summary = "FR-LV-09 — preview leave application",
            description = "Runs all validations without saving. Returns workingDaysCounted, balanceAfter, "
                    + "approvers preview, warnings.")
    public Map<String, Object> preview(@Valid @RequestBody LeaveApplyRequest request) {
        return leaveApplicationService.preview(request);
    }

    @PostMapping("/{id}/cancel")
    @RequiresPermission(feature = "leave", action = "request")
    @Operation(summary = "FR-LV-18 — request cancellation of an APPROVED leave",
            description = "Starts a cancel approval chain; on final approve balance is restored.")
    public LeaveApplicationResponse cancel(@PathVariable Long id) {
        return leaveApplicationService.requestCancelApproved(id);
    }
}
