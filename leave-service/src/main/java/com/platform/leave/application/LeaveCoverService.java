package com.platform.leave.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

/**
 * Applies / reverses payroll absence cover against leave balances (guide finalize path).
 */
@Service
public class LeaveCoverService {

    private final LeaveBalanceService leaveBalanceService;

    public LeaveCoverService(LeaveBalanceService leaveBalanceService) {
        this.leaveBalanceService = leaveBalanceService;
    }

    @Transactional
    public void applyLeaveCover(
            Long employeeId,
            String periodLabel,
            Map<String, BigDecimal> consumedByType,
            boolean reverse) {
        if (consumedByType == null || consumedByType.isEmpty()) {
            return;
        }
        int year = YearMonth.parse(periodLabel.substring(0, 7)).getYear();
        for (Map.Entry<String, BigDecimal> entry : consumedByType.entrySet()) {
            String leaveTypeCode = entry.getKey();
            BigDecimal days = entry.getValue();
            if (days == null || days.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal delta = reverse ? days : days.negate();
            String reason = "Payroll absence cover " + periodLabel.substring(0, 7);
            leaveBalanceService.adjustBySystem(employeeId, leaveTypeCode, delta, reason, year);
        }
    }
}
