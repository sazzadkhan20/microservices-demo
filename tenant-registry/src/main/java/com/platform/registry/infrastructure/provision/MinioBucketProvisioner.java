package com.platform.registry.infrastructure.provision;

import com.platform.common.tenant.TenantIds;
import com.platform.registry.config.PlatformProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Creates one MinIO bucket per tenant at provision time (reusable; no business if-else).
 */
@Component
public class MinioBucketProvisioner {

    private static final Logger log = LoggerFactory.getLogger(MinioBucketProvisioner.class);

    private final PlatformProperties properties;

    public MinioBucketProvisioner(PlatformProperties properties) {
        this.properties = properties;
    }

    public void ensureTenantBucket(String tenantId) {
        PlatformProperties.Minio cfg = properties.getProvision().getMinio();
        if (cfg == null || !cfg.isEnabled()) {
            log.info("MinIO provision disabled — skip bucket for {}", tenantId);
            return;
        }
        String bucket = TenantIds.filesBucket(tenantId);
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(cfg.getEndpoint())
                    .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                    .build();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket {} for tenant {}", bucket, tenantId);
            } else {
                log.info("MinIO bucket {} already exists", bucket);
            }
        } catch (Exception e) {
            // Non-fatal for local boot without MinIO — file uploads will fail until MinIO is up
            log.warn("MinIO bucket provision failed for {}: {}", tenantId, e.getMessage());
        }
    }
}
