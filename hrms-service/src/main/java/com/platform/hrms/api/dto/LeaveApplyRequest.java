package com.platform.hrms.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record LeaveApplyRequest(
        /** Ignored unless caller has leave:manage — applicant is AuthContext employee. */
        Long employeeId,
        String leaveTypeCode,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String reason,
        Long reliefEmployeeId,
        Boolean halfDay
) {
}
