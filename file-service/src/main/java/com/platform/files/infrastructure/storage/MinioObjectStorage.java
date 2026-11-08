package com.platform.files.infrastructure.storage;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * MinIO-backed storage — one bucket per tenant (created at first use / provision hook).
 */
@Component
@ConditionalOnProperty(name = "file.storage.type", havingValue = "minio", matchIfMissing = true)
public class MinioObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorage.class);

    private final MinioClient client;

    public MinioObjectStorage(
            @Value("${file.minio.endpoint:http://127.0.0.1:9000}") String endpoint,
            @Value("${file.minio.access-key}") String accessKey,
            @Value("${file.minio.secret-key}") String secretKey) {
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        log.info("MinIO object storage endpoint={}", endpoint);
    }

    @Override
    public void ensureBucket(String bucket) throws IOException {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket {}", bucket);
            }
        } catch (Exception e) {
            String hint = e.getMessage() != null && e.getMessage().toLowerCase().contains("connection refused")
                    ? " — start MinIO: docker compose --profile minio up -d minio (http://127.0.0.1:9000)"
                    : "";
            throw new IOException("Failed to ensure MinIO bucket " + bucket + ": " + e.getMessage() + hint, e);
        }
    }

    @Override
    public String store(String bucket, String objectKey, InputStream inputStream, String contentType, long sizeBytes)
            throws IOException {
        ensureBucket(bucket);
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, sizeBytes, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
            return objectKey;
        } catch (Exception e) {
            throw new IOException("MinIO put failed: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream load(String bucket, String objectKey) throws IOException {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new IOException("MinIO get failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String bucket, String objectKey) throws IOException {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new IOException("MinIO delete failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String bucket, String objectKey) throws IOException {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return true;
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("nosuchkey") || msg.contains("not found") || msg.contains("no such key")) {
                return false;
            }
            throw new IOException("MinIO stat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void copy(String bucket, String sourceKey, String destKey, String contentType, long sizeBytes)
            throws IOException {
        try {
            client.copyObject(CopyObjectArgs.builder()
                    .bucket(bucket)
                    .object(destKey)
                    .source(CopySource.builder().bucket(bucket).object(sourceKey).build())
                    .build());
        } catch (Exception e) {
            // Fallback to stream copy when server-side copy is unavailable.
            ObjectStorage.super.copy(bucket, sourceKey, destKey, contentType, sizeBytes);
        }
    }
}
