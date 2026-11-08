package com.platform.timework.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local notification hook for Time&amp;Work (no LeaveNotificationHook coupling).
 */
@Component
public class TimeWorkNotificationHook {

    private static final Logger log = LoggerFactory.getLogger(TimeWorkNotificationHook.class);

    public void notifyMissingWorkLog(Long employeeId, String date) {
        log.info("Missing work-log notice employeeId={} date={}", employeeId, date);
    }

    public void push(Long employeeId, String title, String body, String type, String dedupeKey, String link) {
        log.info("TimeWork notify emp={} title={} type={} key={}", employeeId, title, type, dedupeKey);
    }
}
