package com.platform.files.api.dto;

import java.time.Instant;

/** Short-lived authorized download URL (guide §8). */
public record DownloadUrlResponse(
        Long fileId,
        String url,
        String token,
        Instant expiresAt
) {}
