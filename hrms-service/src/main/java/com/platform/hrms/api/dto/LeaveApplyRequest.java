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
        /** Backward-compat: true → FIRST_HALF. */
        Boolean halfDay,
        /** FULL | FIRST_HALF | SECOND_HALF — wins over halfDay when present. */
        String halfDayPart,
        /** Path/key to uploaded attachment stored externally. */
        String attachmentRef,
        /** When true, runs all validations and returns a preview without saving. */
        Boolean previewOnly
) {
}
