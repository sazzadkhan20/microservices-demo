package com.platform.leave.application;

import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.domain.LeaveApplication;
import com.platform.leave.domain.LeaveApproval;
import com.platform.leave.extension.config.TenantConfigLoader;
import com.platform.leave.infrastructure.jobs.TenantJobRunner;
import com.platform.leave.client.people.PeopleEmployeeGateway;
import com.platform.leave.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.leave.infrastructure.persistence.LeaveApprovalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.platform.leave.client.people.PeopleAuthzClient;

/**
 * FR-LV-16 — daily idle approval reminder (per-tenant via {@link TenantJobRunner}).
 */
@Component
public class LeaveIdleReminderJob {

    private static final Logger log = LoggerFactory.getLogger(LeaveIdleReminderJob.class);

    private final LeaveApplicationRepository applicationRepository;
    private final LeaveApprovalRepository approvalRepository;
    private final PeopleEmployeeGateway employeeRepository;
    private final LeaveNotificationHook notificationHook;
    private final TenantConfigLoader configLoader;
    private final PeopleAuthzClient rbacService;
    private final TenantJobRunner tenantJobRunner;

    public LeaveIdleReminderJob(
            LeaveApplicationRepository applicationRepository,
            LeaveApprovalRepository approvalRepository,
            PeopleEmployeeGateway employeeRepository,
            LeaveNotificationHook notificationHook,
            TenantConfigLoader configLoader,
            PeopleAuthzClient rbacService,
            TenantJobRunner tenantJobRunner) {
        this.applicationRepository = applicationRepository;
        this.approvalRepository = approvalRepository;
        this.employeeRepository = employeeRepository;
        this.notificationHook = notificationHook;
        this.configLoader = configLoader;
        this.rbacService = rbacService;
        this.tenantJobRunner = tenantJobRunner;
    }

    @Scheduled(cron = "0 0 8 * * *") // daily at 08:00
    public void run() {
        tenantJobRunner.runForEachActiveTenant("LeaveIdleReminderJob", this::runForTenant);
    }

    @Transactional(readOnly = true)
    protected void runForTenant(String tenantId) {
        Map<String, Object> settings = configLoader.loadSettingsMap();
        Object reminderCfg = settings.get("leaveIdleReminder");
        boolean enabled = true;
        int idleDays = 2;
        if (reminderCfg instanceof Map<?, ?> cfg) {
            Object e = cfg.get("enabled");
            if (e instanceof Boolean b) enabled = b;
            Object d = cfg.get("idleWorkingDays");
            if (d instanceof Number n) idleDays = n.intValue();
        }
        if (!enabled) {
            return;
        }

        Instant cutoff = Instant.now().minus(idleDays, ChronoUnit.DAYS);
        List<LeaveApplication> idle = applicationRepository.findPendingApprovalOlderThan(cutoff);
        if (idle.isEmpty()) {
            return;
        }

        Set<Long> admins = employeeRepository.findAll().stream()
                .filter(emp -> emp.isActive()
                        && rbacService.hasPermission(emp.getId(), "leave", "manage"))
                .map(EmployeeView::getId)
                .collect(Collectors.toSet());

        for (LeaveApplication app : idle) {
            try {
                List<LeaveApproval> stepRows = approvalRepository
                        .findByApplicationIdAndStepOrderOrderByIdAsc(
                                app.getId(), Math.max(1, app.getCurrentStep()));
                LeaveApproval step = stepRows.stream()
                        .filter(s -> LeaveApproval.STATUS_PENDING.equals(s.getStatus()))
                        .reduce((a, b) -> b)
                        .orElse(stepRows.isEmpty() ? null : stepRows.get(stepRows.size() - 1));
                if (step == null || !LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
                    continue;
                }
                Long expectedId = step.getExpectedApproverId();
                if (expectedId != null) {
                    notificationHook.notifyIdleApproval(expectedId, app.getId());
                }
                for (Long adminId : admins) {
                    if (!adminId.equals(expectedId)) {
                        notificationHook.notifyIdleApproval(adminId, app.getId());
                    }
                }
            } catch (Exception ex) {
                log.warn("Idle reminder failed for application {} tenant {}: {}",
                        app.getId(), tenantId, ex.getMessage());
            }
        }
        log.info("LeaveIdleReminderJob tenant={}: processed {} idle applications", tenantId, idle.size());
    }
}
