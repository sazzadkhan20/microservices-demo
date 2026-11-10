package com.platform.leave.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LeaveBalanceAdjustRequest(
        @NotNull Long employeeId,
        @NotBlank String leaveTypeCode,
        @NotNull BigDecimal deltaDays,
        @NotBlank String reason,
        Long actorEmployeeId,
        Integer year
) {
}
