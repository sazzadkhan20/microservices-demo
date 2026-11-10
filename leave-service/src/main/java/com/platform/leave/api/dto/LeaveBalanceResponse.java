package com.platform.leave.api.dto;

import com.platform.leave.domain.LeaveBalance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Balance breakdown:
 * {@code availableDays = quotaDays + carriedDays + adjustmentDays - takenDays - pendingDays}
 * (same as {@code balanceDays - pendingDays} when ledger and balance stay in sync).
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
        BigDecimal balance = b.getBalanceDays() == null ? BigDecimal.ZERO : b.getBalanceDays();
        BigDecimal pending = pendingDays == null ? BigDecimal.ZERO : pendingDays;
        return new LeaveBalanceResponse(
                b.getId(),
                b.getEmployeeId(),
                b.getLeaveTypeCode(),
                leaveTypeName,
                b.getYear(),
                nz(quotaDays),
                nz(carriedDays),
                nz(adjustmentDays),
                nz(takenDays),
                nz(pending),
                balance,
                balance.subtract(pending),
                b.getUpdatedAt()
        );
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
