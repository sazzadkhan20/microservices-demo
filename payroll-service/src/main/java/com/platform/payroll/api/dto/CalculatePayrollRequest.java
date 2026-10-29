package com.platform.payroll.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CalculatePayrollRequest(
        @NotNull Long employeeId,
        @NotBlank String periodLabel,
        @JsonAlias({"hoursWorked", "hours"}) BigDecimal regularHours,
        BigDecimal overtimeHours,
        BigDecimal weekendHours,
        /** Optional unpaid/absence days to cover via absenceCoverOrder. */
        BigDecimal absenceDays,
        /** Optional leave balances by type code for cover-order consumption. */
        Map<String, BigDecimal> leaveBalances,
        /**
         * When true (default), regularHours/absenceDays are taken from HRMS attendance for the
         * period when attendance rows exist. Explicit non-null hours still win when this is false.
         */
        Boolean useHrmsAttendance
) {
    public CalculatePayrollRequest {
        if (overtimeHours == null) {
            overtimeHours = BigDecimal.ZERO;
        }
        if (weekendHours == null) {
            weekendHours = BigDecimal.ZERO;
        }
        if (leaveBalances == null) {
            leaveBalances = Map.of();
        }
        if (useHrmsAttendance == null) {
            useHrmsAttendance = Boolean.TRUE;
        }
    }
}
