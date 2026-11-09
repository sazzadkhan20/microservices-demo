package com.platform.files.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Object storage port. Implementations: local disk (dev) or MinIO (default).
 * Buckets stay private — no public ACLs; authorized downloads use short-lived app tokens.
 */
public interface ObjectStorage {

    String store(String bucket, String objectKey, InputStream inputStream, String contentType, long sizeBytes)
            throws IOException;

    InputStream load(String bucket, String objectKey) throws IOException;

    void delete(String bucket, String objectKey) throws IOException;

    /** Ensure bucket exists (idempotent). Called once at startup. */
    void ensureBucket(String bucket) throws IOException;

    /** True when the object exists (used for link validation / orphan checks). */
    boolean exists(String bucket, String objectKey) throws IOException;

    /**
     * Copy object within a bucket (quarantine → released). Default: stream copy.
     */
    default void copy(String bucket, String sourceKey, String destKey, String contentType, long sizeBytes)
            throws IOException {
        try (InputStream in = load(bucket, sourceKey)) {
            store(bucket, destKey, in, contentType, sizeBytes);
        }
    }
}
