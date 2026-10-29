package com.platform.payroll.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

public record CalculatePayrollRequest(
        @NotNull Long employeeId,
        @NotBlank String periodLabel,
        @JsonAlias({"hoursWorked", "hours"}) BigDecimal regularHours,
        BigDecimal overtimeHours,
        BigDecimal weekendHours,
        /** Optional unpaid/absence days to cover via absenceCoverOrder. */
        BigDecimal absenceDays,
        /** Optional leave balances by type code for cover-order consumption. */
        Map<String, BigDecimal> leaveBalances
) {
    public CalculatePayrollRequest {
        if (regularHours == null) {
            regularHours = BigDecimal.ZERO;
        }
        if (overtimeHours == null) {
            overtimeHours = BigDecimal.ZERO;
        }
        if (weekendHours == null) {
            weekendHours = BigDecimal.ZERO;
        }
        if (absenceDays == null) {
            absenceDays = BigDecimal.ZERO;
        }
        if (leaveBalances == null) {
            leaveBalances = Map.of();
        }
    }
}
