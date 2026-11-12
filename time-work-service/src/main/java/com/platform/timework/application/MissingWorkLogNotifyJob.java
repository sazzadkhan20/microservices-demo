package com.platform.timework.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * FR-WL-06 — hourly UTC sweep; {@link WorkLogService#notifyMissing()} evaluates each
 * employee in their location time zone (Dhaka / Darwin / Sydney / …). Idempotent notify
 * keys keep one nag per missing day. Today is included only after check-out.
 */
@Component
@ConditionalOnProperty(name = "timework.worklog.missing-notify-job-enabled", havingValue = "true", matchIfMissing = true)
public class MissingWorkLogNotifyJob {

    private static final Logger log = LoggerFactory.getLogger(MissingWorkLogNotifyJob.class);

    private final WorkLogService workLogService;

    public MissingWorkLogNotifyJob(WorkLogService workLogService) {
        this.workLogService = workLogService;
    }

    @Scheduled(
            cron = "${timework.worklog.missing-notify-cron:0 0 * * * *}",
            zone = "${timework.worklog.missing-notify-zone:UTC}")
    public void runScheduled() {
        runNow();
    }

    /** Manual / test trigger (same logic as cron). */
    public Map<String, Object> runNow() {
        Map<String, Object> result = workLogService.notifyMissing();
        log.info("Missing work-log notify job finished: {}", result);
        return result;
    }
}
