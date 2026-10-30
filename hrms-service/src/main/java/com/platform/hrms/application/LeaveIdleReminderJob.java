package com.platform.hrms.application;

import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.LeaveApplication;
import com.platform.hrms.domain.LeaveApproval;
import com.platform.hrms.extension.config.TenantConfigLoader;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApprovalRepository;
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

/**
 * FR-LV-16 — daily idle approval reminder.
 * Finds PENDING_APPROVAL leaves where the current step has been pending for
 * ≥ leaveIdleReminder.idleWorkingDays (default 2) calendar days and notifies the expected approver.
 * Config key: {@code leaveIdleReminder.enabled} (default true),
 *             {@code leaveIdleReminder.idleWorkingDays} (default 2).
 */
@Component
public class LeaveIdleReminderJob {

    private static final Logger log = LoggerFactory.getLogger(LeaveIdleReminderJob.class);

    private final LeaveApplicationRepository applicationRepository;
    private final LeaveApprovalRepository approvalRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveNotificationHook notificationHook;
    private final TenantConfigLoader configLoader;
    private final RbacService rbacService;

    public LeaveIdleReminderJob(
            LeaveApplicationRepository applicationRepository,
            LeaveApprovalRepository approvalRepository,
            EmployeeRepository employeeRepository,
            LeaveNotificationHook notificationHook,
            TenantConfigLoader configLoader,
            RbacService rbacService) {
        this.applicationRepository = applicationRepository;
        this.approvalRepository = approvalRepository;
        this.employeeRepository = employeeRepository;
        this.notificationHook = notificationHook;
        this.configLoader = configLoader;
        this.rbacService = rbacService;
    }

    @Scheduled(cron = "0 0 8 * * *") // daily at 08:00
    @Transactional(readOnly = true)
    public void run() {
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

        // Find admin/superadmin employee IDs (leave:manage)
        Set<Long> admins = employeeRepository.findAll().stream()
                .filter(emp -> emp.isActive()
                        && rbacService.hasPermission(emp.getId(), "leave", "manage"))
                .map(Employee::getId)
                .collect(Collectors.toSet());

        for (LeaveApplication app : idle) {
            try {
                LeaveApproval step = approvalRepository
                        .findByApplicationIdAndStepOrder(app.getId(), Math.max(1, app.getCurrentStep()))
                        .orElse(null);
                if (step == null || !LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
                    continue;
                }
                // Notify expected approver
                Long expectedId = step.getExpectedApproverId();
                if (expectedId != null) {
                    notificationHook.notifyIdleApproval(expectedId, app.getId());
                }
                // Notify admins
                for (Long adminId : admins) {
                    if (!adminId.equals(expectedId)) {
                        notificationHook.notifyIdleApproval(adminId, app.getId());
                    }
                }
            } catch (Exception ex) {
                log.warn("Idle reminder failed for application {}: {}", app.getId(), ex.getMessage());
            }
        }
        log.info("LeaveIdleReminderJob: processed {} idle applications", idle.size());
    }
}
