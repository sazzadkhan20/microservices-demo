package com.platform.notification.api.dto;

import java.time.Instant;

/**
 * Public view of a {@link com.platform.notification.domain.Notification}.
 */
public record NotificationResponse(
        Long id,
        String tenantId,
        Long employeeId,
        String title,
        String body,
        String category,
        boolean readFlag,
        String dedupeKey,
        String linkUrl,
        Instant createdAt
) {}
