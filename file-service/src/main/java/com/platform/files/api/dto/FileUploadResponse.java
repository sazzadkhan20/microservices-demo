package com.platform.files.api.dto;

/**
 * Returned after a successful file upload.
 *
 * @param id           metadata record id
 * @param originalName original filename from the upload
 * @param contentType  detected MIME type
 * @param sizeBytes    file size in bytes
 * @param downloadUrl  relative URL to download the file
 */
public record FileUploadResponse(
        Long id,
        String originalName,
        String contentType,
        long sizeBytes,
        String downloadUrl
) {}
