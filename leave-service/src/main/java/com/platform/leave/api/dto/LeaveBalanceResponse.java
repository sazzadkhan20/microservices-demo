package com.platform.leave.api.dto;

import com.platform.leave.domain.LeaveBalance;

import java.math.BigDecimal;
import java.time.Instant;

public record LeaveBalanceResponse(
        Long id,
        Long employeeId,
        String leaveTypeCode,
        int year,
        BigDecimal balanceDays,
        Instant updatedAt
) {
    public static LeaveBalanceResponse from(LeaveBalance b) {
        return new LeaveBalanceResponse(
                b.getId(),
                b.getEmployeeId(),
                b.getLeaveTypeCode(),
                b.getYear(),
                b.getBalanceDays(),
                b.getUpdatedAt()
        );
    }
}
