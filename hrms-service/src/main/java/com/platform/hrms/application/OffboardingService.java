package com.platform.hrms.application;

import com.platform.common.security.AuthContext;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.LeaveApplication;
import com.platform.hrms.domain.LeaveApproval;
import com.platform.hrms.domain.rbac.AppRole;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApprovalRepository;
import com.platform.hrms.infrastructure.persistence.TenantSettingsRepository;
import com.platform.hrms.domain.TenantSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * FR-EMP-10 / FR-EMP-11 — config-driven offboarding: exit date/reason, checklist or override,
 * reassign reports + pending approvals; block self-deactivation and last protected-role holder.
 */
@Service
public class OffboardingService {

    private final EmployeeRepository employeeRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final LeaveApprovalRepository leaveApprovalRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final RbacService rbacService;
    private final ReportingLineService reportingLineService;
    private final EmploymentHistoryService employmentHistoryService;
    private final EmployeeRulesService rulesService;

    public OffboardingService(
            EmployeeRepository employeeRepository,
            LeaveApplicationRepository leaveApplicationRepository,
            LeaveApprovalRepository leaveApprovalRepository,
            TenantSettingsRepository tenantSettingsRepository,
            RbacService rbacService,
            ReportingLineService reportingLineService,
            EmploymentHistoryService employmentHistoryService,
            EmployeeRulesService rulesService) {
        this.employeeRepository = employeeRepository;
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.leaveApprovalRepository = leaveApprovalRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.rbacService = rbacService;
        this.reportingLineService = reportingLineService;
        this.employmentHistoryService = employmentHistoryService;
        this.rulesService = rulesService;
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
                    boolean hasPending = hasPendingApprovals(employee.getId());
                    if ((!reports.isEmpty() || hasPending) && reassignReportsTo == null) {
                        throw new IllegalArgumentException(
                                "reassignReportsTo is required to reassign direct reports / pending approvals");
                    }
                    if (reassignReportsTo != null) {
                        reassignDirectReports(employee.getId(), reports, reassignReportsTo);
                        reassignPendingApprovals(employee.getId(), reassignReportsTo);
                    }
                }
            }
        }

        Object prevExitDate = custom(employee, "exitDate");
        Object prevExitReason = custom(employee, "exitReason");
        String nextExitDate = exitDate == null ? null : exitDate.toString();
        String nextExitReason = blankToNull(exitReason);

        employmentHistoryService.record(employee.getId(), "status", prev, newStatus);
        if (exitDate != null || prevExitDate != null) {
            employmentHistoryService.record(employee.getId(), "exitDate", prevExitDate, nextExitDate);
        }
        if (exitReason != null || prevExitReason != null) {
            employmentHistoryService.record(employee.getId(), "exitReason", prevExitReason, nextExitReason);
        }

        putCustom(employee, "exitDate", nextExitDate);
        putCustom(employee, "exitReason", nextExitReason);
        putCustom(employee, "offboardOverrideReason", blankToNull(overrideReason));

        employee.setStatus(newStatus.trim());
        employee.setActive(!rulesService.isInactive(newStatus, rules));
        employee.setUpdatedAt(Instant.now());
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
        // Per-employee instance in custom_fields.offboardingChecklist
        Employee e = employeeRepository.findById(employeeId).orElse(null);
        if (e != null && e.getCustomFields() != null) {
            Object own = e.getCustomFields().get("offboardingChecklist");
            if (own instanceof Map<?, ?> m && "COMPLETED".equalsIgnoreCase(String.valueOf(m.get("status")))) {
                return true;
            }
        }
        // Tenant template checklists of type OFFBOARDING tagged with this employeeId
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

    private void reassignDirectReports(Long leavingId, List<Employee> reports, Long newManagerId) {
        if (reports == null || reports.isEmpty()) {
            return;
        }
        if (Objects.equals(leavingId, newManagerId)) {
            throw new IllegalArgumentException("reassignReportsTo cannot be the leaving employee");
        }
        Employee neu = employeeRepository.findById(newManagerId)
                .orElseThrow(() -> new IllegalArgumentException("reassignReportsTo manager not found"));
        if (!neu.isActive()) {
            throw new IllegalStateException("reassignReportsTo manager is inactive");
        }
        LocalDate today = LocalDate.now();
        for (Employee r : reports) {
            if (reportingLineService.wouldCycle(r.getId(), newManagerId)) {
                throw new IllegalStateException(
                        "Cannot reassign report " + r.getFullName() + " — would create a reporting loop");
            }
            Long prev = r.getManagerId();
            Long applied = reportingLineService.applyManagerChange(r.getId(), prev, newManagerId, today);
            r.setManagerId(applied);
            r.setUpdatedAt(Instant.now());
            employeeRepository.save(r);
            employmentHistoryService.record(r.getId(), "managerId", prev, applied);
        }
    }

    private boolean hasPendingApprovals(Long leavingId) {
        for (LeaveApplication app : leaveApplicationRepository.findAll()) {
            String st = app.getStatus() == null ? "" : app.getStatus().toUpperCase(Locale.ROOT);
            if (!st.contains("PENDING") && !"SUBMITTED".equals(st) && !"IN_REVIEW".equals(st)) {
                continue;
            }
            for (LeaveApproval step : leaveApprovalRepository.findByApplicationIdOrderByStepOrderAsc(app.getId())) {
                if (LeaveApproval.STATUS_PENDING.equals(step.getStatus())
                        && leavingId.equals(step.getApproverEmployeeId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void reassignPendingApprovals(Long leavingId, Long newApproverId) {
        if (newApproverId == null) {
            return;
        }
        for (LeaveApplication app : leaveApplicationRepository.findAll()) {
            String st = app.getStatus() == null ? "" : app.getStatus().toUpperCase(Locale.ROOT);
            if (!st.contains("PENDING") && !"SUBMITTED".equals(st) && !"IN_REVIEW".equals(st)) {
                continue;
            }
            for (LeaveApproval step : leaveApprovalRepository.findByApplicationIdOrderByStepOrderAsc(app.getId())) {
                if (!LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
                    continue;
                }
                if (leavingId.equals(step.getApproverEmployeeId())) {
                    step.setApproverEmployeeId(newApproverId);
                    leaveApprovalRepository.save(step);
                }
            }
        }
    }

    private static void putCustom(Employee e, String key, Object value) {
        Map<String, Object> next = new HashMap<>(e.getCustomFields() == null ? Map.of() : e.getCustomFields());
        if (value == null || String.valueOf(value).isBlank()) {
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
}
