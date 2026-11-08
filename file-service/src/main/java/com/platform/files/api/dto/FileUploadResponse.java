package com.platform.files.api.dto;

/**
 * Returned after a successful file upload (still QUARANTINED until async scan releases).
 */
public record FileUploadResponse(
        Long id,
        String originalName,
        String contentType,
        long sizeBytes,
        String scanStatus,
        String downloadUrl
) {}
