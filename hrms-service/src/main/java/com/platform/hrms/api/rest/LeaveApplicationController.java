package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.hrms.api.dto.LeaveApplicationResponse;
import com.platform.hrms.api.dto.LeaveApplyRequest;
import com.platform.hrms.api.dto.LeaveDecisionRequest;
import com.platform.hrms.api.dto.ReliefAssignRequest;
import com.platform.hrms.application.LeaveApplicationService;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.security.RequiresPermission;
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

import java.util.Map;
import java.util.LinkedHashMap;

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
                    + "TEAM = self + direct reports, ALL = everyone.")
    public PageResponse<LeaveApplicationResponse> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        return DynamicListSupport.page(
                leaveApplicationService.listForCaller(),
                query,
                a -> DynamicListSupport.containsIgnoreCase(a.leaveTypeCode(), query.q())
                        || DynamicListSupport.containsIgnoreCase(a.reason(), query.q())
                        || DynamicListSupport.containsIgnoreCase(a.status(), query.q()),
                a -> Map.of(
                        "status", a.status() == null ? "" : a.status(),
                        "leaveTypeCode", a.leaveTypeCode() == null ? "" : a.leaveTypeCode(),
                        "employeeId", a.employeeId() == null ? "" : String.valueOf(a.employeeId())
                )
        );
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
