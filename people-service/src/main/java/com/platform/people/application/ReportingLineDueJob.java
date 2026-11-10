package com.platform.people.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Applies future-dated manager changes once their effective date arrives (same cron/zone as
 * offboarding handover). Flips {@code manager_id} and moves that employee's pending leave
 * approvals to the new manager.
 */
@Component
@ConditionalOnProperty(
        prefix = "people.reporting-line",
        name = "due-job-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ReportingLineDueJob {

    private static final Logger log = LoggerFactory.getLogger(ReportingLineDueJob.class);

    private final ReportingLineDueService reportingLineDueService;

    public ReportingLineDueJob(ReportingLineDueService reportingLineDueService) {
        this.reportingLineDueService = reportingLineDueService;
    }

    @Scheduled(
            cron = "${people.reporting-line.due-cron:0 0 2 * * *}",
            zone = "${people.reporting-line.due-zone:Asia/Dhaka}")
    public void runDueManagerChanges() {
        try {
            int n = reportingLineDueService.applyDueManagerChanges();
            if (n > 0) {
                log.info("Applied {} due reporting-line manager change(s)", n);
            }
        } catch (Exception e) {
            log.error("Reporting-line due job failed: {}", e.getMessage(), e);
        }
    }

    public int runNow() {
        return reportingLineDueService.applyDueManagerChanges();
    }
}
