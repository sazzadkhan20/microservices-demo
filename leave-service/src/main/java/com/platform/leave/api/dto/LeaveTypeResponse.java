package com.platform.leave.api.dto;

import com.platform.leave.domain.LeaveCategory;
import com.platform.leave.domain.LeaveType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LeaveTypeResponse(
        Long id,
        String code,
        String name,
        boolean paid,
        boolean requiresBalance,
        boolean allowWhenZeroBalance,
        boolean allowWhenCoverageFail,
        boolean deductBalance,
        int sortOrder,
        Long categoryId,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal availableDays,
        BigDecimal balanceDays,
        BigDecimal pendingDays,
        String attachmentMode,
        int attachmentAfterDays,
        int noticeDays,
        int maxPerRequest,
        boolean halfDayAllowed,
        BigDecimal negativeCap
) {
    public static LeaveTypeResponse from(LeaveType t) {
        return enrich(t, null, null, null, null);
    }

    public static LeaveTypeResponse from(
            LeaveType t,
            Long categoryId,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal availableDays,
            BigDecimal balanceDays,
            BigDecimal pendingDays) {
        return new LeaveTypeResponse(
                t.getId(),
                t.getCode(),
                t.getName(),
                t.isPaid(),
                t.isRequiresBalance(),
                t.isAllowWhenZeroBalance(),
                t.isAllowWhenCoverageFail(),
                t.isDeductBalance(),
                t.getSortOrder(),
                categoryId,
                periodStart,
                periodEnd,
                availableDays,
                balanceDays,
                pendingDays,
                null,
                0,
                0,
                0,
                true,
                null
        );
    }

    public static LeaveTypeResponse enrich(
            LeaveType t,
            LeaveCategory c,
            BigDecimal availableDays,
            BigDecimal balanceDays,
            BigDecimal pendingDays) {
        return new LeaveTypeResponse(
                t.getId(),
                t.getCode(),
                t.getName(),
                t.isPaid(),
                t.isRequiresBalance(),
                t.isAllowWhenZeroBalance(),
                t.isAllowWhenCoverageFail(),
                t.isDeductBalance(),
                t.getSortOrder(),
                c == null ? null : c.getId(),
                c == null ? null : c.getPeriodStart(),
                c == null ? null : c.getPeriodEnd(),
                availableDays,
                balanceDays,
                pendingDays,
                c == null ? null : c.getAttachmentMode(),
                c == null ? 0 : c.getAttachmentAfterDays(),
                c == null ? 0 : c.getNoticeDays(),
                c == null ? 0 : c.getMaxPerRequest(),
                c == null || c.isHalfDayAllowed(),
                c == null ? null : c.getNegativeCap()
        );
    }
}
