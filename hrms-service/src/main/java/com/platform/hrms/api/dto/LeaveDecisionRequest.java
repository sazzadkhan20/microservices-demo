package com.platform.hrms.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Leave decision must be made by a real employee (manager / HR user).
 */
public record LeaveDecisionRequest(
        @NotNull Long approverEmployeeId,
        String comment,
        Long reliefEmployeeId
) {
}
