package com.platform.hrms.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.hrms.api.dto.LeaveApplicationResponse;
import com.platform.hrms.api.dto.LeaveDecisionRequest;
import com.platform.hrms.application.LeaveApplicationService;
import com.platform.hrms.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/approvals")
@Tag(name = "Approvals", description = "Pending leave approvals for managers/HR")
public class ApprovalsController {

    private final LeaveApplicationService leaveApplicationService;
    private final LeaveApplicationRepository applicationRepository;

    public ApprovalsController(
            LeaveApplicationService leaveApplicationService,
            LeaveApplicationRepository applicationRepository) {
        this.leaveApplicationService = leaveApplicationService;
        this.applicationRepository = applicationRepository;
    }

    @GetMapping("/pending")
    @RequiresPermission(feature = "leave", action = "approve")
    @Operation(summary = "List pending leave applications awaiting approval")
    public List<LeaveApplicationResponse> pending() {
        return applicationRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> {
                    String s = a.getStatus() == null ? "" : a.getStatus().toUpperCase(Locale.ROOT);
                    return s.contains("PENDING") || "SUBMITTED".equals(s) || "IN_REVIEW".equals(s);
                })
                .map(a -> leaveApplicationService.get(a.getId()))
                .toList();
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
