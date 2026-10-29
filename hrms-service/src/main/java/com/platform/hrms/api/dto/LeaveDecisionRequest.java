package com.platform.hrms.api.dto;

/**
 * Leave decision payload.
 *
 * <p><b>{@code approverEmployeeId} is deprecated and IGNORED</b>: the approver is always the
 * authenticated employee (JWT → AuthContext). It is kept only so older clients that still send it
 * keep deserialising; a spoofed value cannot change who is recorded as approver.
 */
public record LeaveDecisionRequest(
        @Deprecated Long approverEmployeeId,
        String comment,
        Long reliefEmployeeId
) {
}
