package com.platform.report.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Monthly attendance register (FR-RP-01).
 *
 * @param year         4-digit year
 * @param month        1–12
 * @param employeeId   optional person filter
 * @param locationId   optional location filter
 * @param departmentId optional department filter
 * @param teamId       optional team filter
 */
public record AttendanceReportRequest(
        @NotNull @Min(2000) @Max(2100) Integer year,
        @NotNull @Min(1) @Max(12) Integer month,
        Long employeeId,
        Long locationId,
        Long departmentId,
        Long teamId
) {}
