package com.platform.hrms.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.hrms.api.dto.LeaveApplicationResponse;
import com.platform.hrms.api.dto.LeaveDecisionRequest;
import com.platform.hrms.application.LeaveApplicationService;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/approvals")
@Tag(name = "Approvals", description = "Pending leave approvals for managers/HR")
public class ApprovalsController {

    private final LeaveApplicationService leaveApplicationService;

    public ApprovalsController(LeaveApplicationService leaveApplicationService) {
        this.leaveApplicationService = leaveApplicationService;
    }

    @GetMapping("/pending")
    @RequiresPermission(feature = "leave", action = "approve")
    @Operation(summary = "Leave waiting on the current user for the active approval step",
            description = "Filtered by leave:approve data scope AND step authority "
                    + "(e.g. HR does not see items still waiting on the reporting manager).")
    public List<LeaveApplicationResponse> pending() {
        return leaveApplicationService.pendingForCaller();
    }

    @PostMapping("/{id}/{action}")
    @RequiresPermission(feature = "leave", action = "approve")
    @Operation(summary = "Approve or reject a leave application as current user")
    public LeaveApplicationResponse decide(@PathVariable Long id, @PathVariable String action) {
        Long approver = AuthContext.requireEmployeeId();
        LeaveDecisionRequest req = new LeaveDecisionRequest(approver, null, null);
        if ("approve".equalsIgnoreCase(action)) {
            return leaveApplicationService.approve(id, req);
        }
        if ("reject".equalsIgnoreCase(action)) {
            return leaveApplicationService.reject(id, req);
        }
        throw new IllegalArgumentException("Unknown action: " + action + " (use approve|reject)");
    }
}
