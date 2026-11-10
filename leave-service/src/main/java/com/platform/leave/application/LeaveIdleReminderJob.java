package com.platform.leave.application;

import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.client.people.PeopleAuthzClient;
import com.platform.leave.domain.LeaveApplication;
import com.platform.leave.domain.LeaveApproval;
import com.platform.leave.extension.config.TenantConfigLoader;
import com.platform.leave.client.people.PeopleEmployeeGateway;
import com.platform.leave.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.leave.infrastructure.persistence.LeaveApprovalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FR-LV-16 — idle approval reminder after N working days (default 2).
 */
@Component
public class LeaveIdleReminderJob {

    private static final Logger log = LoggerFactory.getLogger(LeaveIdleReminderJob.class);
    private static final Set<DayOfWeek> WEEKDAYS = EnumSet.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private final LeaveApplicationRepository applicationRepository;
    private final LeaveApprovalRepository approvalRepository;
    private final PeopleEmployeeGateway employeeRepository;
    private final LeaveNotificationHook notificationHook;
    private final TenantConfigLoader configLoader;
    private final PeopleAuthzClient rbacService;

    public LeaveIdleReminderJob(
            LeaveApplicationRepository applicationRepository,
            LeaveApprovalRepository approvalRepository,
            PeopleEmployeeGateway employeeRepository,
            LeaveNotificationHook notificationHook,
            TenantConfigLoader configLoader,
            PeopleAuthzClient rbacService) {
        this.applicationRepository = applicationRepository;
        this.approvalRepository = approvalRepository;
        this.employeeRepository = employeeRepository;
        this.notificationHook = notificationHook;
        this.configLoader = configLoader;
        this.rbacService = rbacService;
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void run() {
        try {
            remindIdleApprovals();
        } catch (Exception e) {
            log.warn("LeaveIdleReminderJob failed: {}", e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public void remindIdleApprovals() {
        Map<String, Object> settings = configLoader.loadSettingsMap();
        Object reminderCfg = settings.get("leaveIdleReminder");
        boolean enabled = true;
        int idleWorkingDays = 2;
        if (reminderCfg instanceof Map<?, ?> cfg) {
            Object e = cfg.get("enabled");
            if (e instanceof Boolean b) enabled = b;
            Object d = cfg.get("idleWorkingDays");
            if (d instanceof Number n) idleWorkingDays = n.intValue();
        }
        if (!enabled || idleWorkingDays <= 0) {
            return;
        }

        // Wide calendar window; filter to working-day idle threshold below.
        Instant wideCutoff = Instant.now().minus(idleWorkingDays * 3L + 2, ChronoUnit.DAYS);
        List<LeaveApplication> candidates = applicationRepository.findPendingApprovalOlderThan(wideCutoff);
        if (candidates.isEmpty()) {
            return;
        }

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Set<Long> admins = employeeRepository.findAll().stream()
                .filter(emp -> emp.isActive()
                        && rbacService.hasPermission(emp.getId(), "leave", "manage"))
                .map(EmployeeView::getId)
                .collect(Collectors.toSet());

        int processed = 0;
        for (LeaveApplication app : candidates) {
            try {
                Instant updated = app.getUpdatedAt() == null ? app.getCreatedAt() : app.getUpdatedAt();
                if (updated == null) continue;
                LocalDate since = LocalDate.ofInstant(updated, zone);
                if (workingDaysBetween(since, today) < idleWorkingDays) {
                    continue;
                }
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
                processed++;
            } catch (Exception ex) {
                log.warn("Idle reminder failed for application {}: {}", app.getId(), ex.getMessage());
            }
        }
        log.info("LeaveIdleReminderJob: reminded {} idle applications", processed);
    }

    /** Inclusive of start exclusive of end — Mon–Fri only (holiday-agnostic reminder cadence). */
    static int workingDaysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return 0;
        }
        int count = 0;
        for (LocalDate d = start.plusDays(1); !d.isAfter(end); d = d.plusDays(1)) {
            if (WEEKDAYS.contains(d.getDayOfWeek())) {
                count++;
            }
        }
        return count;
    }
}
