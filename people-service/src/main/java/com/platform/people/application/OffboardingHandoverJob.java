package com.platform.people.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FR-EMP-10 — apply deferred manager / pending-approval handovers on or after exit date.
 * Runs daily at 02:00 (default Asia/Dhaka) so local midnight-adjacent HR cutover is predictable.
 */
@Component
@ConditionalOnProperty(prefix = "people.offboarding", name = "handover-job-enabled", havingValue = "true", matchIfMissing = true)
public class OffboardingHandoverJob {

    private static final Logger log = LoggerFactory.getLogger(OffboardingHandoverJob.class);

    private final OffboardingService offboardingService;

    public OffboardingHandoverJob(OffboardingService offboardingService) {
        this.offboardingService = offboardingService;
    }

    @Scheduled(
            cron = "${people.offboarding.handover-cron:0 0 2 * * *}",
            zone = "${people.offboarding.handover-zone:Asia/Dhaka}")
    public void runDueHandovers() {
        try {
            int n = offboardingService.applyDueScheduledHandovers();
            if (n > 0) {
                log.info("Applied {} scheduled offboarding handover(s)", n);
            }
        } catch (Exception e) {
            log.error("Scheduled offboarding handover job failed: {}", e.getMessage(), e);
        }
    }

    /** Manual / test trigger (same logic as cron). */
    public int runNow() {
        return offboardingService.applyDueScheduledHandovers();
    }
}
