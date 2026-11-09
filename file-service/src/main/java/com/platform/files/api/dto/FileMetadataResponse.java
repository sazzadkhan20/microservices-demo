package com.platform.files.api.dto;

import java.time.Instant;

/**
 * Full metadata view of a stored file (guide §8 — includes scan state).
 */
public record FileMetadataResponse(
        Long id,
        String originalName,
        String contentType,
        long sizeBytes,
        String uploadedBy,
        Long employeeId,
        String scanStatus,
        String classification,
        String scanDetail,
        Instant scannedAt,
        Instant releasedAt,
        Instant createdAt,
        String downloadUrl
) {}
