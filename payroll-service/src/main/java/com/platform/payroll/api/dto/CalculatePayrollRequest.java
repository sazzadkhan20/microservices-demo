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
         * When true (default), facts are taken from HRMS payroll-facts endpoint.
         * Explicit non-null hours still win when this is false.
         */
        Boolean useHrmsAttendance,
        /** FR-PAY-08: one-off adjustment amount for this period (positive = bonus, negative = deduction). */
        BigDecimal adjustmentAmount,
        /** FR-PAY-08: human-readable note describing the adjustment. */
        String adjustmentNote,
        /** Optional override for working days in month (admin/test). */
        Integer workingDaysInMonth,
        /** Optional override for days employed in month (admin/test). */
        Integer daysEmployed
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

    /** Convenience constructor for internal use (calculateMonth). */
    public CalculatePayrollRequest(Long employeeId, String periodLabel,
                                   BigDecimal regularHours, BigDecimal overtimeHours, BigDecimal weekendHours,
                                   BigDecimal absenceDays, Map<String, BigDecimal> leaveBalances,
                                   Boolean useHrmsAttendance) {
        this(employeeId, periodLabel, regularHours, overtimeHours, weekendHours,
                absenceDays, leaveBalances, useHrmsAttendance, null, null, null, null);
    }
}
