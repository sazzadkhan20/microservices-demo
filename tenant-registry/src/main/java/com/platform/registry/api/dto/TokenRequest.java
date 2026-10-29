package com.platform.registry.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Issues JWT for Gateway. Pass employeeId for product/demo account-picker sessions.
 */
public record TokenRequest(
        @NotBlank String tenantId,
        String subject,
        Long employeeId
) {
}
