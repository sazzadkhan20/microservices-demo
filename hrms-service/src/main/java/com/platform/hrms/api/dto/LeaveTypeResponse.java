package com.platform.hrms.api.dto;

import com.platform.hrms.domain.LeaveType;

public record LeaveTypeResponse(
        Long id,
        String code,
        String name,
        boolean paid,
        boolean requiresBalance,
        boolean allowWhenZeroBalance,
        boolean allowWhenCoverageFail,
        boolean deductBalance,
        int sortOrder
) {
    public static LeaveTypeResponse from(LeaveType t) {
        return new LeaveTypeResponse(
                t.getId(),
                t.getCode(),
                t.getName(),
                t.isPaid(),
                t.isRequiresBalance(),
                t.isAllowWhenZeroBalance(),
                t.isAllowWhenCoverageFail(),
                t.isDeductBalance(),
                t.getSortOrder()
        );
    }
}
