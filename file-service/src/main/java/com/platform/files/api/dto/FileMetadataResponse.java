package com.platform.files.api.dto;

import java.time.Instant;

/**
 * Full metadata view of a stored file.
 */
public record FileMetadataResponse(
        Long id,
        String tenantId,
        String originalName,
        String contentType,
        long sizeBytes,
        String uploadedBy,
        Instant createdAt,
        String downloadUrl
) {}
