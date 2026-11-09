package com.platform.leave.application;

import com.platform.leave.domain.LeaveBalanceLedger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

/**
 * Applies / reverses payroll absence cover against leave balances (guide finalize path).
 */
@Service
public class LeaveCoverService {

    private final LeaveBalanceService leaveBalanceService;

    public LeaveCoverService(LeaveBalanceService leaveBalanceService) {
        this.leaveBalanceService = leaveBalanceService;
    }

    /**
     * LV-02 — REQUIRES_NEW so a business rejection does not mark the inbox/outbox
     * transaction rollback-only; the listener can still commit FAILED + result.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyLeaveCover(
            Long employeeId,
            String periodLabel,
            Map<String, BigDecimal> consumedByType,
            boolean reverse,
            String operationKey,
            String sourceId) {
        if (consumedByType == null || consumedByType.isEmpty()) {
            return;
        }
        int year = YearMonth.parse(periodLabel.substring(0, 7)).getYear();
        String baseKey = (operationKey == null || operationKey.isBlank())
                ? UUID.randomUUID().toString()
                : operationKey;
        for (Map.Entry<String, BigDecimal> entry : consumedByType.entrySet()) {
            String leaveTypeCode = entry.getKey();
            BigDecimal days = entry.getValue();
            if (days == null || days.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal delta = reverse ? days : days.negate();
            String reason = "Payroll absence cover " + periodLabel.substring(0, 7);
            String typeKey = "COVER:" + baseKey + ":" + leaveTypeCode;
            leaveBalanceService.adjustBySystem(
                    employeeId,
                    leaveTypeCode,
                    delta,
                    reason,
                    year,
                    typeKey,
                    LeaveBalanceLedger.SOURCE_COVER,
                    sourceId);
        }
    }
}
