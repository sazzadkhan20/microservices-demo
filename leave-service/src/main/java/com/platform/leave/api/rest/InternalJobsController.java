package com.platform.leave.api.rest;

import com.platform.leave.application.LeaveIdleReminderJob;
import com.platform.leave.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manual / npm triggers for leave batch jobs (same logic as cron).
 * Hit Leave directly; gateway blocks {@code /api/internal/**}.
 */
@RestController
@RequestMapping("/api/internal/jobs")
@Hidden
public class InternalJobsController {

    private final ObjectProvider<LeaveIdleReminderJob> idleJob;

    public InternalJobsController(ObjectProvider<LeaveIdleReminderJob> idleJob) {
        this.idleJob = idleJob;
    }

    @PostMapping("/idle-approval-reminders")
    @RequiresPermission(feature = "leave", action = "manage", allowInternal = true)
    public Map<String, Object> runIdleReminders() {
        LeaveIdleReminderJob job = idleJob.getIfAvailable();
        if (job != null) {
            job.remindIdleApprovals();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job", "idle-approval-reminders");
        out.put("triggered", job != null);
        return out;
    }
}
