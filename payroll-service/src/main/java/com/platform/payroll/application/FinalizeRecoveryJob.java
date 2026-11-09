package com.platform.payroll.application;

import com.platform.messaging.tenant.TenantBoundRunner;
import com.platform.payroll.infrastructure.multitenancy.TenantDataSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Guide §6 — reconcile pending finalize/reopen operations: alert on age, safe leave-cover replay,
 * then escalate to ACTION_REQUIRED when leave never answers.
 */
@Component
@ConditionalOnProperty(prefix = "platform.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FinalizeRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(FinalizeRecoveryJob.class);

    private final TenantDataSourceManager dataSourceManager;
    private final FinalizeRecoveryService recoveryService;

    public FinalizeRecoveryJob(
            TenantDataSourceManager dataSourceManager,
            FinalizeRecoveryService recoveryService) {
        this.dataSourceManager = dataSourceManager;
        this.recoveryService = recoveryService;
    }

    @Scheduled(fixedDelayString = "${platform.payroll.finalize-recovery-interval-ms:60000}")
    public void reconcile() {
        for (String tenantId : dataSourceManager.outboxTenantIds()) {
            try {
                TenantBoundRunner.run(tenantId, recoveryService::reconcileTenant);
            } catch (Exception e) {
                log.warn("Finalize recovery skipped tenant={}: {}", tenantId, e.getMessage());
            }
        }
    }
}
