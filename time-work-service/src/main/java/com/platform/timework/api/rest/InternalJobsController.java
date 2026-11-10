package com.platform.timework.api.rest;

import com.platform.timework.application.MissingWorkLogNotifyJob;
import com.platform.timework.application.WorkLogService;
import com.platform.timework.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manual / npm triggers for Time&amp;Work batch jobs (same logic as cron).
 * Hit Time&amp;Work directly; gateway blocks {@code /api/internal/**}.
 */
@RestController
@RequestMapping("/api/internal/jobs")
@Hidden
public class InternalJobsController {

    private final ObjectProvider<MissingWorkLogNotifyJob> missingNotifyJob;
    private final WorkLogService workLogService;

    public InternalJobsController(
            ObjectProvider<MissingWorkLogNotifyJob> missingNotifyJob,
            WorkLogService workLogService) {
        this.missingNotifyJob = missingNotifyJob;
        this.workLogService = workLogService;
    }

    @PostMapping("/missing-work-log-notify")
    @RequiresPermission(feature = "attendance_settings", action = "edit", allowInternal = true)
    public Map<String, Object> runMissingWorkLogNotify() {
        MissingWorkLogNotifyJob job = missingNotifyJob.getIfAvailable();
        Map<String, Object> result = job != null ? job.runNow() : workLogService.notifyMissing();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job", "missing-work-log-notify");
        out.putAll(result);
        return out;
    }
}
