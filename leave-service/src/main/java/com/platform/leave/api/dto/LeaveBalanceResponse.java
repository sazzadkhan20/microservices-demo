package com.platform.leave.api.dto;

import com.platform.leave.application.LeaveDays;
import com.platform.leave.domain.LeaveBalance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Balance breakdown: {@code availableDays = balanceDays - pendingDays}.
 * All day fields use nearest 0.5 quantization — identical to apply-time checks.
 */
public record LeaveBalanceResponse(
        Long id,
        Long employeeId,
        String leaveTypeCode,
        String leaveTypeName,
        int year,
        BigDecimal quotaDays,
        BigDecimal carriedDays,
        BigDecimal adjustmentDays,
        BigDecimal takenDays,
        BigDecimal pendingDays,
        BigDecimal balanceDays,
        BigDecimal availableDays,
        Instant updatedAt
) {
    public static LeaveBalanceResponse from(LeaveBalance b) {
        return bare(b, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public static LeaveBalanceResponse bare(
            LeaveBalance b,
            String leaveTypeName,
            BigDecimal quotaDays,
            BigDecimal carriedDays,
            BigDecimal adjustmentDays,
            BigDecimal takenDays,
            BigDecimal pendingDays) {
        BigDecimal balance = LeaveDays.of(b.getBalanceDays());
        BigDecimal pending = LeaveDays.of(pendingDays);
        return new LeaveBalanceResponse(
                b.getId(),
                b.getEmployeeId(),
                b.getLeaveTypeCode(),
                leaveTypeName,
                b.getYear(),
                LeaveDays.of(quotaDays),
                LeaveDays.of(carriedDays),
                LeaveDays.of(adjustmentDays),
                LeaveDays.of(takenDays),
                pending,
                balance,
                LeaveDays.of(balance.subtract(pending)),
                b.getUpdatedAt()
        );
    }

    /** Prefer {@link LeaveDays#of(BigDecimal)}; kept for LeaveTypeResponse. */
    public static BigDecimal days(BigDecimal v) {
        return LeaveDays.of(v);
    }
}
