package com.platform.files.application;

import com.platform.files.infrastructure.multitenancy.TenantDataSourceManager;
import com.platform.messaging.tenant.TenantBoundRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Retention + stuck-scan recovery (guide §8 — scan-failure handling and orphan cleanup).
 */
@Component
@ConditionalOnProperty(prefix = "file.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileRetentionJobs {

    private static final Logger log = LoggerFactory.getLogger(FileRetentionJobs.class);

    private final FileService fileService;
    private final TenantDataSourceManager dataSourceManager;
    private final long stuckScanMinutes;

    public FileRetentionJobs(
            FileService fileService,
            TenantDataSourceManager dataSourceManager,
            @Value("${file.retention.stuck-scan-minutes:15}") long stuckScanMinutes) {
        this.fileService = fileService;
        this.dataSourceManager = dataSourceManager;
        this.stuckScanMinutes = Math.max(5L, stuckScanMinutes);
    }

    @Scheduled(fixedDelayString = "${file.retention.purge-interval-ms:3600000}")
    public void purgeRejected() {
        for (String tenantId : dataSourceManager.outboxTenantIds()) {
            try {
                TenantBoundRunner.run(tenantId, () -> {
                    int n = fileService.purgeRejectedOlderThanRetention();
                    if (n > 0) {
                        log.info("Purged {} rejected files for tenant={}", n, tenantId);
                    }
                });
            } catch (Exception e) {
                log.warn("Retention purge skipped for tenant={}: {}", tenantId, e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${file.retention.requeue-interval-ms:120000}")
    public void requeueStuck() {
        Instant cutoff = Instant.now().minus(stuckScanMinutes, ChronoUnit.MINUTES);
        for (String tenantId : dataSourceManager.outboxTenantIds()) {
            try {
                TenantBoundRunner.run(tenantId, () -> {
                    int n = fileService.requeueStuckScans(cutoff);
                    if (n > 0) {
                        log.info("Re-queued {} stuck scans for tenant={}", n, tenantId);
                    }
                });
            } catch (Exception e) {
                log.warn("Stuck-scan requeue skipped for tenant={}: {}", tenantId, e.getMessage());
            }
        }
    }
}
