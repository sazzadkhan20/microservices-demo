package com.platform.people.application;

import com.platform.common.security.AuthContext;
import com.platform.people.domain.Employee;
import com.platform.people.domain.rbac.AppRole;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.infrastructure.persistence.TenantSettingsRepository;
import com.platform.people.domain.TenantSettings;
import com.platform.people.client.LeaveClient;
import com.platform.people.client.SessionRevokeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * FR-EMP-10 / FR-EMP-11 — config-driven offboarding: exit date/reason, checklist or override,
 * reassign reports + pending approvals; block self-deactivation and last protected-role holder.
 *
 * <p>When {@code exitDate} is in the future, status/exit fields are applied immediately but
 * manager + pending-approval handover is deferred until that date (daily 02:00 job).
 * When {@code exitDate} is today or past, handover runs in the same request.
 */
@Service
public class OffboardingService {

    private static final Logger log = LoggerFactory.getLogger(OffboardingService.class);

    /** custom_fields key — durable schedule for deferred handover (idempotent job). */
    public static final String SCHEDULED_HANDOVER_KEY = "scheduledHandover";
    public static final String HANDOVER_PENDING = "PENDING";
    public static final String HANDOVER_APPLIED = "APPLIED";
    public static final String HANDOVER_FAILED = "FAILED";

    private final EmployeeRepository employeeRepository;
    private final LeaveClient leaveClient;
    private final SessionRevokeClient sessionRevokeClient;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final RbacService rbacService;
    private final ReportingLineService reportingLineService;
    private final EmploymentHistoryService employmentHistoryService;
    private final EmployeeRulesService rulesService;
    private final UserAccountService userAccountService;
    private final Clock clock;
    /** Must match {@code people.offboarding.handover-zone} so "today" agrees with the 02:00 cron. */
    private final ZoneId handoverZone;
    /** Spring proxy — required so {@link #applyOneDueHandover} gets a real REQUIRES_NEW boundary. */
    private final ObjectProvider<OffboardingService> self;

    public OffboardingService(
            EmployeeRepository employeeRepository,
            LeaveClient leaveClient,
            SessionRevokeClient sessionRevokeClient,
            TenantSettingsRepository tenantSettingsRepository,
            RbacService rbacService,
            ReportingLineService reportingLineService,
            EmploymentHistoryService employmentHistoryService,
            EmployeeRulesService rulesService,
            UserAccountService userAccountService,
            Clock clock,
            @Value("${people.offboarding.handover-zone:Asia/Dhaka}") String handoverZone,
            ObjectProvider<OffboardingService> self) {
        this.employeeRepository = employeeRepository;
        this.leaveClient = leaveClient;
        this.sessionRevokeClient = sessionRevokeClient;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.rbacService = rbacService;
        this.reportingLineService = reportingLineService;
        this.employmentHistoryService = employmentHistoryService;
        this.rulesService = rulesService;
        this.userAccountService = userAccountService;
        this.clock = clock;
        this.handoverZone = ZoneId.of(handoverZone);
        this.self = self;
    }

    /** Calendar "today" in the handover timezone (cron zone), not the JVM default. */
    public LocalDate handoverToday() {
        return LocalDate.now(clock.withZone(handoverZone));
    }

    @Transactional
    public void applyStatusChange(
            Employee employee,
            String newStatus,
            LocalDate exitDate,
            String exitReason,
            String overrideReason,
            Long reassignReportsTo) {
        EmployeeRulesService.Rules rules = rulesService.load();
        rulesService.assertStatusAllowed(newStatus, rules);

        Long actor = AuthContext.getEmployeeId();
        String prev = employee.getStatus();
        boolean offboarding = rulesService.isOffboarding(newStatus, rules)
                || rulesService.isInactive(newStatus, rules);
        LocalDate today = handoverToday();

        if (offboarding) {
            if (actor != null && actor.equals(employee.getId())) {
                throw new IllegalStateException("Nobody shall deactivate their own account");
            }
            assertNotLastProtectedHolder(employee.getId());

            if (rulesService.isOffboarding(newStatus, rules)) {
                if (rules.requireExitDate() && exitDate == null) {
                    throw new IllegalArgumentException("exitDate is required for " + newStatus);
                }
                if (rules.requireExitReason() && (exitReason == null || exitReason.isBlank())) {
                    throw new IllegalArgumentException("exitReason is required for " + newStatus);
                }
                assertChecklistOrOverride(employee.getId(), overrideReason, rules);

                if (rules.requireReportReassignment()) {
                    List<Employee> reports = employeeRepository.findByManagerId(employee.getId());
                    int pendingCount = leaveClient.countPendingApprovals(employee.getId());
                    boolean needsHandover = !reports.isEmpty() || pendingCount > 0;
                    if (needsHandover && reassignReportsTo == null) {
                        throw new IllegalArgumentException(
                                "reassignReportsTo is required: this employee has "
                                        + reports.size() + " direct report(s) and "
                                        + pendingCount + " pending approval(s)");
                    }
                    if (needsHandover) {
                        assertEligibleHandoverTarget(employee.getId(), reports, reassignReportsTo);
                        boolean defer = exitDate != null && exitDate.isAfter(today);
                        if (defer) {
                            // Schedule reporting-line history for exitDate; approvals wait for the job.
                            scheduleManagerLines(employee.getId(), reports, reassignReportsTo, exitDate);
                            putScheduledHandover(employee, reassignReportsTo, exitDate, reports, pendingCount);
                        } else {
                            clearScheduledHandover(employee);
                            applyHandoverNow(employee.getId(), reports, reassignReportsTo, pendingCount,
                                    exitDate != null ? exitDate : today);
                        }
                    } else {
                        clearScheduledHandover(employee);
                    }
                }
            }
        }

        Object prevExitDate = employee.getExitDate() != null
                ? employee.getExitDate().toString()
                : custom(employee, "exitDate");
        Object prevExitReason = employee.getExitReason() != null
                ? employee.getExitReason()
                : custom(employee, "exitReason");
        String nextExitDate = exitDate == null ? null : exitDate.toString();
        String nextExitReason = blankToNull(exitReason);

        employmentHistoryService.record(employee.getId(), "status", prev, newStatus);
        if (exitDate != null || prevExitDate != null) {
            employmentHistoryService.record(employee.getId(), "exitDate", prevExitDate, nextExitDate);
        }
        if (exitReason != null || prevExitReason != null) {
            employmentHistoryService.record(employee.getId(), "exitReason", prevExitReason, nextExitReason);
        }

        employee.setExitDate(exitDate);
        employee.setExitReason(nextExitReason);
        putCustom(employee, "exitDate", nextExitDate);
        putCustom(employee, "exitReason", nextExitReason);
        putCustom(employee, "offboardOverrideReason", blankToNull(overrideReason));

        employee.setStatus(newStatus.trim());
        employee.setActive(!rulesService.isInactive(newStatus, rules));
        employee.setUpdatedAt(Instant.now(clock));
        userAccountService.syncFromEmployee(employee);

        if (!employee.isActive()) {
            Long revokeId = employee.getId();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sessionRevokeClient.revokeEmployee(revokeId);
                    }
                });
            } else {
                sessionRevokeClient.revokeEmployee(revokeId);
            }
        }
    }

    /**
     * List employees with PENDING/FAILED scheduledHandover due on/before handover-zone "today".
     * Read-only scan — each id is then applied in its own {@link Propagation#REQUIRES_NEW} txn.
     */
    @Transactional(readOnly = true)
    public List<Long> findDueHandoverEmployeeIds() {
        LocalDate today = handoverToday();
        List<Long> due = new ArrayList<>();
        for (Employee leaving : employeeRepository.findAll()) {
            Map<String, Object> sched = readScheduledHandover(leaving);
            if (sched == null) {
                continue;
            }
            String st = String.valueOf(sched.getOrDefault("status", "")).trim().toUpperCase(Locale.ROOT);
            if (!HANDOVER_PENDING.equals(st) && !HANDOVER_FAILED.equals(st)) {
                continue;
            }
            LocalDate effectiveOn = parseDate(sched.get("effectiveOn"));
            if (effectiveOn == null || effectiveOn.isAfter(today)) {
                continue;
            }
            due.add(leaving.getId());
        }
        return due;
    }

    /**
     * Daily job entry — apply every due scheduled handover in isolated transactions
     * so one failure cannot roll back another employee's successful handover.
     */
    public int applyDueScheduledHandovers() {
        OffboardingService proxy = self != null ? self.getIfAvailable() : null;
        if (proxy == null) {
            proxy = this;
        }
        int applied = 0;
        for (Long employeeId : findDueHandoverEmployeeIds()) {
            try {
                if (proxy.applyOneDueHandover(employeeId)) {
                    applied++;
                }
            } catch (RuntimeException ex) {
                log.warn("Scheduled handover failed for employeeId={}: {}", employeeId, ex.getMessage());
            }
        }
        return applied;
    }

    /**
     * Apply one due scheduled handover. Isolated txn — safe for the daily job loop.
     *
     * @return true when newly marked APPLIED
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean applyOneDueHandover(Long leavingId) {
        Employee leaving = employeeRepository.findById(leavingId).orElse(null);
        if (leaving == null) {
            return false;
        }
        Map<String, Object> sched = readScheduledHandover(leaving);
        if (sched == null) {
            return false;
        }
        String st = String.valueOf(sched.getOrDefault("status", "")).trim().toUpperCase(Locale.ROOT);
        if (HANDOVER_APPLIED.equals(st)) {
            return false;
        }
        if (!HANDOVER_PENDING.equals(st) && !HANDOVER_FAILED.equals(st)) {
            return false;
        }
        LocalDate today = handoverToday();
        LocalDate effectiveOn = parseDate(sched.get("effectiveOn"));
        if (effectiveOn == null || effectiveOn.isAfter(today)) {
            return false;
        }
        Long reassignTo = asLong(sched.get("reassignReportsTo"));
        if (reassignTo == null) {
            markHandoverFailed(leaving, sched, "scheduledHandover.reassignReportsTo missing");
            employeeRepository.save(leaving);
            return false;
        }
        try {
            List<Long> plannedIds = asLongList(sched.get("directReportIds"));
            List<Employee> reports = resolveReportsForHandover(leaving.getId(), plannedIds);
            assertEligibleHandoverTarget(leaving.getId(), reports, reassignTo);

            for (Employee r : reports) {
                // Prefer flipping via already-scheduled reporting-line history (exitDate line).
                reportingLineService.syncCurrentManager(r, today);
                Employee fresh = employeeRepository.findById(r.getId()).orElse(r);
                Long prev = fresh.getManagerId();
                if (Objects.equals(prev, leaving.getId())) {
                    Long appliedMgr = reportingLineService.applyManagerChange(
                            fresh.getId(), prev, reassignTo, today);
                    fresh.setManagerId(appliedMgr);
                    fresh.setUpdatedAt(Instant.now(clock));
                    employeeRepository.save(fresh);
                    if (!Objects.equals(prev, appliedMgr)) {
                        employmentHistoryService.record(fresh.getId(), "managerId", prev, appliedMgr);
                    }
                }
            }

            int pendingLeft = leaveClient.countPendingApprovals(leaving.getId());
            int moved = 0;
            if (pendingLeft > 0) {
                moved = leaveClient.reassignPendingApprovals(leaving.getId(), reassignTo);
                if (moved <= 0) {
                    throw new IllegalStateException(
                            "Failed to reassign pending approvals — none were moved (count=" + pendingLeft + ")");
                }
            }

            sched.put("status", HANDOVER_APPLIED);
            sched.put("appliedAt", Instant.now(clock).toString());
            sched.put("lastError", null);
            sched.put("approvalsMovedOnApply", moved);
            sched.put("appliedOn", today.toString());
            putCustom(leaving, SCHEDULED_HANDOVER_KEY, sched);
            leaving.setUpdatedAt(Instant.now(clock));
            employeeRepository.save(leaving);
            log.info("Scheduled handover applied for employeeId={} → {} (reports={}, approvalsMoved={})",
                    leaving.getId(), reassignTo, reports.size(), moved);
            return true;
        } catch (RuntimeException ex) {
            markHandoverFailed(leaving, sched, ex.getMessage());
            employeeRepository.save(leaving);
            throw ex;
        }
    }

    private List<Employee> resolveReportsForHandover(Long leavingId, List<Long> plannedIds) {
        List<Employee> out = new ArrayList<>();
        if (plannedIds != null && !plannedIds.isEmpty()) {
            for (Long id : plannedIds) {
                employeeRepository.findById(id).ifPresent(out::add);
            }
        }
        // Also include anyone still reporting to the leaver (hired after schedule / missed id).
        for (Employee r : employeeRepository.findByManagerId(leavingId)) {
            if (out.stream().noneMatch(e -> Objects.equals(e.getId(), r.getId()))) {
                out.add(r);
            }
        }
        return out;
    }

    private void scheduleManagerLines(
            Long leavingId, List<Employee> reports, Long newManagerId, LocalDate effectiveOn) {
        for (Employee r : reports) {
            Long prev = r.getManagerId();
            // Future-dated line: manager_id stays on leaver until effectiveOn; job syncs pointer.
            reportingLineService.applyManagerChange(r.getId(), prev, newManagerId, effectiveOn);
            employmentHistoryService.record(r.getId(), "managerId", prev, newManagerId);
        }
    }

    private void applyHandoverNow(
            Long leavingId,
            List<Employee> reports,
            Long newManagerId,
            int pendingCount,
            LocalDate effectiveOn) {
        for (Employee r : reports) {
            Long prev = r.getManagerId();
            Long applied = reportingLineService.applyManagerChange(r.getId(), prev, newManagerId, effectiveOn);
            r.setManagerId(applied);
            r.setUpdatedAt(Instant.now(clock));
            employeeRepository.save(r);
            employmentHistoryService.record(r.getId(), "managerId", prev, applied);
        }
        if (pendingCount > 0) {
            int moved = leaveClient.reassignPendingApprovals(leavingId, newManagerId);
            if (moved <= 0) {
                throw new IllegalStateException(
                        "Failed to reassign pending approvals — none were moved to the handover person");
            }
        }
    }

    private void putScheduledHandover(
            Employee leaving,
            Long reassignTo,
            LocalDate effectiveOn,
            List<Employee> reports,
            int pendingCount) {
        Map<String, Object> sched = new LinkedHashMap<>();
        sched.put("reassignReportsTo", reassignTo);
        sched.put("effectiveOn", effectiveOn.toString());
        sched.put("status", HANDOVER_PENDING);
        sched.put("createdAt", Instant.now(clock).toString());
        sched.put("appliedAt", null);
        sched.put("lastError", null);
        sched.put("pendingApprovalsAtSchedule", pendingCount);
        List<Long> ids = new ArrayList<>();
        for (Employee r : reports) {
            ids.add(r.getId());
        }
        sched.put("directReportIds", ids);
        sched.put("directReportCount", ids.size());
        putCustom(leaving, SCHEDULED_HANDOVER_KEY, sched);
    }

    private void clearScheduledHandover(Employee employee) {
        putCustom(employee, SCHEDULED_HANDOVER_KEY, null);
    }

    private void markHandoverFailed(Employee leaving, Map<String, Object> sched, String error) {
        Map<String, Object> next = new LinkedHashMap<>(sched);
        next.put("status", HANDOVER_FAILED);
        next.put("lastError", error == null ? "unknown" : error);
        next.put("failedAt", Instant.now(clock).toString());
        putCustom(leaving, SCHEDULED_HANDOVER_KEY, next);
        leaving.setUpdatedAt(Instant.now(clock));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readScheduledHandover(Employee e) {
        Object raw = custom(e, SCHEDULED_HANDOVER_KEY);
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            m.forEach((k, v) -> copy.put(String.valueOf(k), v));
            return copy;
        }
        return null;
    }

    private void assertNotLastProtectedHolder(Long employeeId) {
        for (AppRole role : rbacService.rolesFor(employeeId)) {
            if (!role.isProtectFromOffboard() || !role.isActive()) {
                continue;
            }
            int min = Math.max(1, role.getMinHolders());
            long activeHolders = employeeRepository.findAll().stream()
                    .filter(Employee::isActive)
                    .filter(e -> !isInactiveStatus(e.getStatus()))
                    .filter(e -> rbacService.roleCodesFor(e.getId()).stream()
                            .anyMatch(c -> c.equalsIgnoreCase(role.getCode())))
                    .count();
            if (activeHolders <= min) {
                throw new IllegalStateException(
                        "Cannot deactivate: would leave fewer than " + min
                                + " active holder(s) of protected role " + role.getCode());
            }
        }
    }

    private boolean isInactiveStatus(String status) {
        return rulesService.isInactive(status, rulesService.load());
    }

    private void assertChecklistOrOverride(Long employeeId, String overrideReason, EmployeeRulesService.Rules rules) {
        if (!rules.requireOffboardingChecklist()) {
            return;
        }
        if (overrideReason != null && !overrideReason.isBlank()) {
            Long actor = AuthContext.getEmployeeId();
            if (actor == null || (!rbacService.hasWildcard(actor)
                    && !rbacService.hasPermission(actor, "employees", "edit"))) {
                throw new IllegalStateException("Offboarding checklist override requires employees:edit");
            }
            return;
        }
        if (!offboardingChecklistCompleted(employeeId)) {
            throw new IllegalStateException(
                    "Offboarding checklist must be completed (or provide offboardOverrideReason)");
        }
    }

    @SuppressWarnings("unchecked")
    private boolean offboardingChecklistCompleted(Long employeeId) {
        Employee e = employeeRepository.findById(employeeId).orElse(null);
        if (e != null && e.getCustomFields() != null) {
            Object own = e.getCustomFields().get("offboardingChecklist");
            if (own instanceof Map<?, ?> m && "COMPLETED".equalsIgnoreCase(String.valueOf(m.get("status")))) {
                return true;
            }
        }
        Map<String, Object> settings = tenantSettingsRepository.findAll().stream()
                .findFirst().map(TenantSettings::getSettings).orElse(Map.of());
        Object raw = settings.get("checklists");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> c)) {
                    continue;
                }
                if (!"OFFBOARDING".equalsIgnoreCase(String.valueOf(c.get("type")))) {
                    continue;
                }
                Object emp = c.get("employeeId");
                if (emp != null && String.valueOf(employeeId).equals(String.valueOf(emp))
                        && "COMPLETED".equalsIgnoreCase(String.valueOf(c.get("status")))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Preview what must be handed over when offboarding, plus who is a safe takeover target
     * (active, not the leaver, and would not create a reporting loop for any direct report).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> handoverPreview(Long employeeId) {
        Employee leaving = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        List<Employee> reports = employeeRepository.findByManagerId(employeeId);
        int pendingCount = leaveClient.countPendingApprovals(employeeId);
        boolean requires = !reports.isEmpty() || pendingCount > 0;

        List<Map<String, Object>> reportRows = new ArrayList<>();
        for (Employee r : reports) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("fullName", r.getFullName());
            row.put("employeeCode", r.getEmployeeCode());
            reportRows.add(row);
        }

        List<Map<String, Object>> eligible = new ArrayList<>();
        if (requires) {
            for (Employee candidate : employeeRepository.findAll()) {
                if (!isEligibleHandoverTarget(leaving.getId(), reports, candidate)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", candidate.getId());
                row.put("fullName", candidate.getFullName());
                row.put("employeeCode", candidate.getEmployeeCode());
                eligible.add(row);
            }
            eligible.sort((a, b) -> String.valueOf(a.get("fullName"))
                    .compareToIgnoreCase(String.valueOf(b.get("fullName"))));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeId", employeeId);
        out.put("directReportCount", reports.size());
        out.put("directReports", reportRows);
        out.put("pendingApprovalCount", pendingCount);
        out.put("requiresReassignment", requires);
        out.put("eligibleHandoverTo", eligible);
        out.put("today", handoverToday().toString());
        Map<String, Object> sched = readScheduledHandover(leaving);
        if (sched != null) {
            out.put("scheduledHandover", sched);
            String st = String.valueOf(sched.getOrDefault("status", "")).toUpperCase(Locale.ROOT);
            out.put("handoverDeferred", HANDOVER_PENDING.equals(st) || HANDOVER_FAILED.equals(st));
        } else {
            out.put("handoverDeferred", false);
        }
        return out;
    }

    private void assertEligibleHandoverTarget(Long leavingId, List<Employee> reports, Long newManagerId) {
        if (newManagerId == null) {
            throw new IllegalArgumentException("reassignReportsTo is required");
        }
        Employee neu = employeeRepository.findById(newManagerId)
                .orElseThrow(() -> new IllegalArgumentException("reassignReportsTo manager not found"));
        if (!isEligibleHandoverTarget(leavingId, reports, neu)) {
            throw new IllegalStateException(
                    "Cannot hand over to that person — inactive, is the leaving employee, "
                            + "or would create a reporting loop for one or more direct reports");
        }
    }

    private boolean isEligibleHandoverTarget(Long leavingId, List<Employee> reports, Employee candidate) {
        if (candidate == null || candidate.getId() == null) {
            return false;
        }
        if (Objects.equals(leavingId, candidate.getId())) {
            return false;
        }
        if (!candidate.isActive() || isInactiveStatus(candidate.getStatus())) {
            return false;
        }
        for (Employee r : reports) {
            if (Objects.equals(r.getId(), candidate.getId())) {
                continue;
            }
            if (reportingLineService.wouldCycle(r.getId(), candidate.getId())) {
                return false;
            }
        }
        if (reports.stream().anyMatch(r -> Objects.equals(r.getId(), candidate.getId()))) {
            return false;
        }
        return true;
    }

    private static void putCustom(Employee e, String key, Object value) {
        Map<String, Object> next = new HashMap<>(e.getCustomFields() == null ? Map.of() : e.getCustomFields());
        if (value == null || (value instanceof String s && s.isBlank())) {
            next.remove(key);
        } else {
            next.put(key, value);
        }
        e.setCustomFields(next);
    }

    private static Object custom(Employee e, String key) {
        return e.getCustomFields() == null ? null : e.getCustomFields().get(key);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static LocalDate parseDate(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(raw).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Long asLong(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Long> asLongList(Object raw) {
        List<Long> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            Long id = asLong(o);
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }
}
