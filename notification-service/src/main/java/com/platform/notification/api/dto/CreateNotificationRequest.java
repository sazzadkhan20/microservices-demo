package com.platform.notification.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for creating a new notification.
 *
 * @param employeeId  target employee — optional for user-facing create (bound to JWT self);
 *                    required for internal service creates ({@code X-Internal-Key})
 * @param title       short subject line
 * @param body        full notification body (max 2 000 chars)
 * @param category    optional free-form category tag (e.g. "LEAVE", "PAYROLL")
 * @param dedupeKey   optional idempotency key; duplicate (tenantId, dedupeKey) is ignored
 */
public record CreateNotificationRequest(
        Long employeeId,
        @NotBlank String title,
        @NotBlank String body,
        String category,
        String dedupeKey
) {}
