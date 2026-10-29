package com.platform.report.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for a monthly attendance report.
 *
 * @param year       4-digit year (e.g. 2026)
 * @param month      1–12
 * @param employeeId optional filter; omit to include all employees
 */
public record AttendanceReportRequest(
        @NotNull @Min(2000) @Max(2100) Integer year,
        @NotNull @Min(1) @Max(12) Integer month,
        Long employeeId
) {}
