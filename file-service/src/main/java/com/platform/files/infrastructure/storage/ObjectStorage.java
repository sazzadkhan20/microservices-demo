package com.platform.files.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Tenant-agnostic object storage port. Implementations: local disk (dev) or MinIO (default).
 */
public interface ObjectStorage {

    String store(String bucket, String objectKey, InputStream inputStream, String contentType, long sizeBytes)
            throws IOException;

    InputStream load(String bucket, String objectKey) throws IOException;

    void delete(String bucket, String objectKey) throws IOException;

    /** Ensure bucket exists (idempotent). Called on first upload per tenant. */
    void ensureBucket(String bucket) throws IOException;
}
