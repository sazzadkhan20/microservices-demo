package com.platform.people.api.rest;

import com.platform.people.application.OffboardingHandoverJob;
import com.platform.people.application.OffboardingService;
import com.platform.people.application.PeopleLifecycleNotifyJob;
import com.platform.people.application.PeopleLifecycleNotifyService;
import com.platform.people.application.ReportingLineDueJob;
import com.platform.people.application.ReportingLineDueService;
import com.platform.people.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manual / npm triggers for deferred jobs (same logic as the 02:00 Asia/Dhaka cron).
 * Hit People directly ({@code :8081}); gateway blocks {@code /api/internal/**}.
 * Auth: {@code X-Service-Key} + {@code X-Internal-Key}.
 */
@RestController
@RequestMapping("/api/internal/jobs")
@Hidden
public class InternalJobsController {

    private final ObjectProvider<OffboardingHandoverJob> handoverJob;
    private final OffboardingService offboardingService;
    private final ObjectProvider<ReportingLineDueJob> reportingLineDueJob;
    private final ReportingLineDueService reportingLineDueService;
    private final ObjectProvider<PeopleLifecycleNotifyJob> lifecycleNotifyJob;
    private final PeopleLifecycleNotifyService lifecycleNotifyService;

    public InternalJobsController(
            ObjectProvider<OffboardingHandoverJob> handoverJob,
            OffboardingService offboardingService,
            ObjectProvider<ReportingLineDueJob> reportingLineDueJob,
            ReportingLineDueService reportingLineDueService,
            ObjectProvider<PeopleLifecycleNotifyJob> lifecycleNotifyJob,
            PeopleLifecycleNotifyService lifecycleNotifyService) {
        this.handoverJob = handoverJob;
        this.offboardingService = offboardingService;
        this.reportingLineDueJob = reportingLineDueJob;
        this.reportingLineDueService = reportingLineDueService;
        this.lifecycleNotifyJob = lifecycleNotifyJob;
        this.lifecycleNotifyService = lifecycleNotifyService;
    }

    @PostMapping("/offboarding-handover")
    @RequiresPermission(feature = "employees", action = "edit", allowInternal = true)
    public Map<String, Object> runOffboardingHandover() {
        OffboardingHandoverJob job = handoverJob.getIfAvailable();
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Offboarding handover job is disabled (people.offboarding.handover-job-enabled=false)");
        }
        List<Long> dueBefore = offboardingService.findDueHandoverEmployeeIds();
        int applied = job.runNow();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job", "offboarding-handover");
        out.put("dueBefore", dueBefore);
        out.put("applied", applied);
        out.put("today", offboardingService.handoverToday().toString());
        return out;
    }

    @PostMapping("/reporting-line-due")
    @RequiresPermission(feature = "employees", action = "edit", allowInternal = true)
    public Map<String, Object> runReportingLineDue() {
        ReportingLineDueJob job = reportingLineDueJob.getIfAvailable();
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Reporting-line due job is disabled (people.reporting-line.due-job-enabled=false)");
        }
        List<Long> dueBefore = reportingLineDueService.findDueEmployeeIds();
        int applied = job.runNow();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job", "reporting-line-due");
        out.put("dueBefore", dueBefore);
        out.put("applied", applied);
        out.put("today", reportingLineDueService.today().toString());
        return out;
    }

    /** Run both deferred jobs (manager effective-date sync + offboarding handover). */
    @PostMapping("/deferred-people")
    @RequiresPermission(feature = "employees", action = "edit", allowInternal = true)
    public Map<String, Object> runAllDeferred() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reportingLineDue", runReportingLineDue());
        out.put("offboardingHandover", runOffboardingHandover());
        return out;
    }

    @PostMapping("/lifecycle-notify")
    @RequiresPermission(feature = "employees", action = "edit", allowInternal = true)
    public Map<String, Object> runLifecycleNotify() {
        PeopleLifecycleNotifyJob job = lifecycleNotifyJob.getIfAvailable();
        Map<String, Object> result = job != null ? job.runNow() : lifecycleNotifyService.runAll();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job", "lifecycle-notify");
        out.putAll(result);
        return out;
    }
}
