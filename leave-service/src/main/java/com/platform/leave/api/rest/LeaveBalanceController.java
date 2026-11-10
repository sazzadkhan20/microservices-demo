package com.platform.leave.api.rest;

import com.platform.leave.api.dto.LeaveBalanceAdjustRequest;
import com.platform.leave.api.dto.LeaveBalanceResponse;
import com.platform.leave.application.LeaveBalanceService;
import com.platform.leave.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
    @Operation(summary = "List leave balances for an employee with breakdown (optional year)")
    public List<LeaveBalanceResponse> list(@PathVariable Long employeeId,
                                           @RequestParam(required = false) Integer year) {
        return leaveBalanceService.listForEmployee(employeeId, year);
    }

    @GetMapping("/employee/{employeeId}/ledger")
    @RequiresPermission(feature = "leave", action = "view")
    @Operation(summary = "List balance ledger / adjustments for an employee (audited trail)")
    public List<Map<String, Object>> ledger(@PathVariable Long employeeId,
                                            @RequestParam(required = false) Integer year,
                                            @RequestParam(required = false) String sourceType) {
        return leaveBalanceService.listLedger(employeeId, year, sourceType);
    }

    @PostMapping("/adjust")
    @RequiresPermission(feature = "leave", action = "manage")
    @Operation(summary = "HR add or deduct leave balance (mandatory reason; audited)")
    public LeaveBalanceResponse adjust(@Valid @RequestBody LeaveBalanceAdjustRequest request) {
        return leaveBalanceService.adjust(request);
    }

    @PostMapping("/opening")
    @RequiresPermission(feature = "leave", action = "manage")
    @Operation(summary = "Record opening / join grant (once per employee+type+year; audited)")
    public LeaveBalanceResponse opening(@Valid @RequestBody OpeningRequest request) {
        return leaveBalanceService.recordOpening(
                request.employeeId(),
                request.leaveTypeCode(),
                request.deltaDays(),
                request.year(),
                request.reason());
    }

    @GetMapping("/year-end/defaults")
    @RequiresPermission(feature = "leave", action = "manage")
    @Operation(summary = "Auto from/to year and carry cap for a leave type")
    public Map<String, Object> yearEndDefaults(@RequestParam String leaveTypeCode) {
        return leaveBalanceService.yearEndDefaults(leaveTypeCode);
    }

    @PostMapping("/year-end/preview")
    @RequiresPermission(feature = "leave", action = "manage")
    @Operation(summary = "Preview year-end carry-over")
    public List<Map<String, Object>> yearEndPreview(@RequestBody Map<String, Object> body) {
        String code = String.valueOf(body.get("leaveTypeCode"));
        int fromYear = Integer.parseInt(String.valueOf(body.get("fromYear")));
        int toYear = Integer.parseInt(String.valueOf(body.get("toYear")));
        BigDecimal cap = body.get("carryMaxDays") == null
                ? null
                : new BigDecimal(String.valueOf(body.get("carryMaxDays")));
        return leaveBalanceService.yearEndPreview(code, fromYear, toYear, cap);
    }

    @PostMapping("/year-end/confirm")
    @RequiresPermission(feature = "leave", action = "manage")
    @Operation(summary = "Confirm year-end carry-over into the next period")
    public Map<String, Object> yearEndConfirm(@RequestBody Map<String, Object> body) {
        String code = String.valueOf(body.get("leaveTypeCode"));
        int fromYear = Integer.parseInt(String.valueOf(body.get("fromYear")));
        int toYear = Integer.parseInt(String.valueOf(body.get("toYear")));
        BigDecimal cap = body.get("carryMaxDays") == null
                ? null
                : new BigDecimal(String.valueOf(body.get("carryMaxDays")));
        return leaveBalanceService.yearEndConfirm(code, fromYear, toYear, cap);
    }

    public record OpeningRequest(
            @NotNull Long employeeId,
            @NotBlank String leaveTypeCode,
            @NotNull BigDecimal deltaDays,
            @NotNull Integer year,
            @NotBlank String reason
    ) {}
}
