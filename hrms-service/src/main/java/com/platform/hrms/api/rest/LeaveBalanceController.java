package com.platform.hrms.api.rest;

import com.platform.hrms.api.dto.LeaveBalanceAdjustRequest;
import com.platform.hrms.api.dto.LeaveBalanceResponse;
import com.platform.hrms.application.LeaveBalanceService;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leave-balances")
@Tag(name = "Leave Balances", description = "HR add/deduct leave balances (config-driven types)")
public class LeaveBalanceController {

    private final LeaveBalanceService leaveBalanceService;

    public LeaveBalanceController(LeaveBalanceService leaveBalanceService) {
        this.leaveBalanceService = leaveBalanceService;
    }

    @GetMapping("/employee/{employeeId}")
    @RequiresPermission(feature = "leave", action = "view")
    @Operation(summary = "List leave balances for an employee (optional year)")
    public List<LeaveBalanceResponse> list(@PathVariable Long employeeId,
                                           @RequestParam(required = false) Integer year) {
        return leaveBalanceService.listForEmployee(employeeId, year);
    }

    @PostMapping("/adjust")
    @RequiresPermission(feature = "leave", action = "manage")
    @Operation(summary = "HR add or deduct leave balance (positive or negative deltaDays)")
    public LeaveBalanceResponse adjust(@Valid @RequestBody LeaveBalanceAdjustRequest request) {
        return leaveBalanceService.adjust(request);
    }
}
