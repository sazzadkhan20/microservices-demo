package com.platform.notification.messaging;

import com.platform.messaging.outbox.OutboxRelay;
import com.platform.messaging.tenant.TenantBoundRunner;
import com.platform.notification.infrastructure.multitenancy.TenantDataSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "platform.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TenantOutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(TenantOutboxScheduler.class);

    private final TenantDataSourceManager dataSourceManager;
    private final OutboxRelay outboxRelay;

    public TenantOutboxScheduler(TenantDataSourceManager dataSourceManager, OutboxRelay outboxRelay) {
        this.dataSourceManager = dataSourceManager;
        this.outboxRelay = outboxRelay;
    }

    @Scheduled(fixedDelayString = "${platform.messaging.outbox-poll-ms:1000}")
    public void drainAllOpenTenants() {
        for (String tenantId : dataSourceManager.outboxTenantIds()) {
            try {
                TenantBoundRunner.run(tenantId, outboxRelay::drainBatch);
            } catch (Exception e) {
                log.warn("Outbox drain skipped for tenant={}: {}", tenantId, e.getMessage());
            }
        }
    }
}
