package com.platform.payroll.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CalculatePayrollResponse(
        Long payslipId,
        Long employeeId,
        String employeeCode,
        String periodLabel,
        BigDecimal overtimeMultiplier,
        BigDecimal weekendMultiplier,
        BigDecimal grossAmount,
        BigDecimal deductionAmount,
        BigDecimal netAmount,
        List<Map<String, Object>> deductions,
        Map<String, Object> details
) {
}
