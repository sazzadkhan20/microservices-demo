package com.platform.people.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Daily FR-NT lifecycle notifications (contract/probation, docs, checklist due).
 */
@Component
@ConditionalOnProperty(
        prefix = "people.lifecycle-notify",
        name = "job-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PeopleLifecycleNotifyJob {

    private static final Logger log = LoggerFactory.getLogger(PeopleLifecycleNotifyJob.class);

    private final PeopleLifecycleNotifyService service;

    public PeopleLifecycleNotifyJob(PeopleLifecycleNotifyService service) {
        this.service = service;
    }

    @Scheduled(
            cron = "${people.lifecycle-notify.cron:0 30 8 * * *}",
            zone = "${people.lifecycle-notify.zone:Asia/Dhaka}")
    public void run() {
        try {
            Map<String, Object> result = service.runAll();
            log.info("People lifecycle notify finished: {}", result);
        } catch (Exception e) {
            log.warn("People lifecycle notify failed: {}", e.getMessage(), e);
        }
    }

    public Map<String, Object> runNow() {
        return service.runAll();
    }
}
