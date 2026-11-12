package com.platform.timework.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * FR-NT — periodic sweep for no-check-in (start+1h) and missing check-out at cut-off.
 */
@Component
@ConditionalOnProperty(
        name = "timework.attendance.reminder-job-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AttendanceReminderJob {

    private static final Logger log = LoggerFactory.getLogger(AttendanceReminderJob.class);

    private final AttendanceReminderService reminderService;

    public AttendanceReminderJob(AttendanceReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(
            cron = "${timework.attendance.reminder-cron:0 */15 * * * *}",
            zone = "${timework.attendance.reminder-zone:UTC}")
    public void run() {
        try {
            Map<String, Object> result = reminderService.runReminders();
            log.info("Attendance reminder job finished: {}", result);
        } catch (Exception e) {
            log.warn("Attendance reminder job failed: {}", e.getMessage(), e);
        }
    }

    public Map<String, Object> runNow() {
        return reminderService.runReminders();
    }
}
