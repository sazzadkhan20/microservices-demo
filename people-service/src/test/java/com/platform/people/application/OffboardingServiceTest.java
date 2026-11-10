package com.platform.people.application;

import com.platform.common.security.AuthContext;
import com.platform.people.domain.Employee;
import com.platform.people.domain.rbac.AppRole;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.infrastructure.persistence.TenantSettingsRepository;
import com.platform.people.client.LeaveClient;
import com.platform.people.client.SessionRevokeClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OffboardingService — FR-EMP-10 / FR-EMP-11 + deferred handover")
class OffboardingServiceTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock LeaveClient leaveClient;
    @Mock SessionRevokeClient sessionRevokeClient;
    @Mock TenantSettingsRepository tenantSettingsRepository;
    @Mock RbacService rbacService;
    @Mock ReportingLineService reportingLineService;
    @Mock EmploymentHistoryService employmentHistoryService;
    @Mock EmployeeRulesService rulesService;
    @Mock UserAccountService userAccountService;

    private OffboardingService service;
    private Clock clock;
    private EmployeeRulesService.Rules rules;

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        service = new OffboardingService(
                employeeRepository,
                leaveClient,
                sessionRevokeClient,
                tenantSettingsRepository,
                rbacService,
                reportingLineService,
                employmentHistoryService,
                rulesService,
                userAccountService,
                clock,
                "UTC",
                null);
        rules = new EmployeeRulesService.Rules(
                "qiicreative.com.au",
                List.of("qiicreative.com.au"),
                EmployeeRulesService.DEFAULT_STATUSES,
                EmployeeRulesService.DEFAULT_OFFBOARD,
                EmployeeRulesService.DEFAULT_INACTIVE,
                EmployeeRulesService.DEFAULT_CONTRACT_TYPES,
                EmployeeRulesService.DEFAULT_EMPLOYMENT_TYPES,
                EmployeeRulesService.DEFAULT_WORK_PATTERNS,
                EmployeeRulesService.DEFAULT_DOC_TYPES,
                EmployeeRulesService.DEFAULT_SELF_EDIT,
                EmployeeRulesService.DEFAULT_EMPLOYMENT_TRACKED,
                List.of("fullName", "email"),
                true,
                true,
                true,
                true,
                true
        );
        when(rulesService.load()).thenReturn(rules);
        when(rulesService.isOffboarding(eq("Resigned"), any())).thenReturn(true);
        when(rulesService.isOffboarding(eq("Terminated"), any())).thenReturn(true);
        when(rulesService.isInactive(eq("Resigned"), any())).thenReturn(true);
        when(rulesService.isInactive(eq("Terminated"), any())).thenReturn(true);
        when(rulesService.isInactive(eq("Inactive"), any())).thenReturn(true);
        when(rulesService.isInactive(eq("Active"), any())).thenReturn(false);
        when(leaveClient.countPendingApprovals(anyLong())).thenReturn(0);
        when(leaveClient.hasPendingApprovals(anyLong())).thenReturn(false);
        when(employeeRepository.findByManagerId(anyLong())).thenReturn(List.of());
        when(rbacService.rolesFor(anyLong())).thenReturn(List.of());
        when(rbacService.hasPermission(1L, "employees", "edit")).thenReturn(true);
        AuthContext.setEmployeeId(1L);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private static Employee emp(long id, String status) {
        Employee e = new Employee();
        ReflectionTestUtils.setField(e, "id", id);
        e.setFullName("E" + id);
        e.setStatus(status);
        e.setActive(true);
        e.setCustomFields(new HashMap<>());
        return e;
    }

    @Test
    void refusesSelfDeactivation() {
        AuthContext.setEmployeeId(9L);
        Employee target = emp(9L, "Active");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.applyStatusChange(target, "Resigned", TODAY, "Left", "override", null));
        assertTrue(ex.getMessage().toLowerCase().contains("own account"));
    }

    @Test
    void acceptsOffboardWithOverrideWhenNoHandoverNeeded() {
        Employee target = emp(2L, "Active");
        service.applyStatusChange(target, "Resigned", TODAY, "Relocation", "admin override", null);
        assertEquals("Resigned", target.getStatus());
        assertFalse(target.isActive());
        verify(leaveClient, never()).reassignPendingApprovals(anyLong(), anyLong());
        assertNull(target.getCustomFields().get(OffboardingService.SCHEDULED_HANDOVER_KEY));
    }

    @Test
    void refusesLastProtectedRoleHolder() {
        Employee target = emp(2L, "Active");
        AppRole role = new AppRole();
        role.setCode("SUPERADMIN");
        role.setActive(true);
        role.setProtectFromOffboard(true);
        role.setMinHolders(1);
        when(rbacService.rolesFor(2L)).thenReturn(List.of(role));
        when(employeeRepository.findAll()).thenReturn(List.of(target));
        when(rbacService.roleCodesFor(2L)).thenReturn(List.of("SUPERADMIN"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.applyStatusChange(target, "Terminated", TODAY, "Gone", "override", null));
        assertTrue(ex.getMessage().toLowerCase().contains("protected"));
        verify(employmentHistoryService, never()).record(anyLong(), eq("status"), any(), any());
    }

    @Test
    void requiresHandoverWhenHasDirectReports() {
        Employee leaving = emp(2L, "Active");
        Employee report = emp(3L, "Active");
        report.setManagerId(2L);
        when(employeeRepository.findByManagerId(2L)).thenReturn(List.of(report));
        when(leaveClient.countPendingApprovals(2L)).thenReturn(0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.applyStatusChange(leaving, "Terminated", TODAY, "Exit", "override", null));
        assertTrue(ex.getMessage().contains("reassignReportsTo"));
        verify(leaveClient, never()).reassignPendingApprovals(anyLong(), anyLong());
    }

    @Test
    void requiresHandoverWhenHasPendingApprovalsOnly() {
        Employee leaving = emp(2L, "Active");
        when(leaveClient.countPendingApprovals(2L)).thenReturn(30);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.applyStatusChange(leaving, "Terminated", TODAY, "Exit", "override", null));
        assertTrue(ex.getMessage().contains("30 pending"));
        verify(leaveClient, never()).reassignPendingApprovals(anyLong(), anyLong());
    }

    @Test
    void immediateHandoverWhenExitDateIsToday() {
        Employee leaving = emp(2L, "Active");
        Employee report = emp(3L, "Active");
        report.setManagerId(2L);
        Employee takeover = emp(4L, "Active");
        when(employeeRepository.findByManagerId(2L)).thenReturn(List.of(report));
        when(employeeRepository.findById(4L)).thenReturn(Optional.of(takeover));
        when(leaveClient.countPendingApprovals(2L)).thenReturn(30);
        when(leaveClient.reassignPendingApprovals(2L, 4L)).thenReturn(30);
        when(reportingLineService.wouldCycle(3L, 4L)).thenReturn(false);
        when(reportingLineService.applyManagerChange(eq(3L), eq(2L), eq(4L), eq(TODAY))).thenReturn(4L);

        service.applyStatusChange(leaving, "Terminated", TODAY, "Exit", "override", 4L);

        assertEquals("Terminated", leaving.getStatus());
        assertEquals(4L, report.getManagerId());
        verify(leaveClient).reassignPendingApprovals(2L, 4L);
        assertNull(leaving.getCustomFields().get(OffboardingService.SCHEDULED_HANDOVER_KEY));
    }

    @Test
    void futureExitDateDefersApprovalsAndSchedulesManagerLines() {
        LocalDate exit = TODAY.plusDays(10);
        Employee leaving = emp(2L, "Active");
        Employee report = emp(3L, "Active");
        report.setManagerId(2L);
        Employee takeover = emp(4L, "Active");
        when(employeeRepository.findByManagerId(2L)).thenReturn(List.of(report));
        when(employeeRepository.findById(4L)).thenReturn(Optional.of(takeover));
        when(leaveClient.countPendingApprovals(2L)).thenReturn(30);
        when(reportingLineService.wouldCycle(3L, 4L)).thenReturn(false);
        when(reportingLineService.applyManagerChange(eq(3L), eq(2L), eq(4L), eq(exit))).thenReturn(2L);

        service.applyStatusChange(leaving, "Terminated", exit, "Notice period", "override", 4L);

        assertEquals("Terminated", leaving.getStatus());
        // Approvals must NOT move yet
        verify(leaveClient, never()).reassignPendingApprovals(anyLong(), anyLong());
        // Manager history scheduled for exit date; current pointer may stay on leaver until then
        verify(reportingLineService).applyManagerChange(eq(3L), eq(2L), eq(4L), eq(exit));
        assertEquals(2L, report.getManagerId());

        @SuppressWarnings("unchecked")
        Map<String, Object> sched = (Map<String, Object>) leaving.getCustomFields()
                .get(OffboardingService.SCHEDULED_HANDOVER_KEY);
        assertNotNull(sched);
        assertEquals(OffboardingService.HANDOVER_PENDING, sched.get("status"));
        assertEquals(exit.toString(), sched.get("effectiveOn"));
        assertEquals(4L, ((Number) sched.get("reassignReportsTo")).longValue());
        assertEquals(30, ((Number) sched.get("pendingApprovalsAtSchedule")).intValue());
    }

    @Test
    void jobAppliesDueScheduledHandoverAndMovesApprovals() {
        LocalDate exit = TODAY; // due today
        Employee leaving = emp(2L, "Terminated");
        leaving.setActive(false);
        Employee report = emp(3L, "Active");
        report.setManagerId(2L);
        Employee takeover = emp(4L, "Active");

        Map<String, Object> sched = new HashMap<>();
        sched.put("status", OffboardingService.HANDOVER_PENDING);
        sched.put("effectiveOn", exit.toString());
        sched.put("reassignReportsTo", 4L);
        sched.put("directReportIds", List.of(3L));
        sched.put("pendingApprovalsAtSchedule", 30);
        leaving.getCustomFields().put(OffboardingService.SCHEDULED_HANDOVER_KEY, sched);

        when(employeeRepository.findAll()).thenReturn(List.of(leaving, report, takeover));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(leaving));
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(report));
        when(employeeRepository.findById(4L)).thenReturn(Optional.of(takeover));
        when(employeeRepository.findByManagerId(2L)).thenReturn(List.of(report));
        when(reportingLineService.wouldCycle(3L, 4L)).thenReturn(false);
        when(reportingLineService.applyManagerChange(eq(3L), eq(2L), eq(4L), eq(TODAY))).thenReturn(4L);
        when(leaveClient.countPendingApprovals(2L)).thenReturn(30);
        when(leaveClient.reassignPendingApprovals(2L, 4L)).thenReturn(30);

        int n = service.applyDueScheduledHandovers();
        assertEquals(1, n);
        assertEquals(4L, report.getManagerId());
        verify(reportingLineService).syncCurrentManager(eq(report), eq(TODAY));
        verify(leaveClient).reassignPendingApprovals(2L, 4L);

        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) leaving.getCustomFields()
                .get(OffboardingService.SCHEDULED_HANDOVER_KEY);
        assertEquals(OffboardingService.HANDOVER_APPLIED, after.get("status"));
        assertNotNull(after.get("appliedAt"));
    }

    @Test
    void jobSkipsFutureSchedulesAndIsIdempotentForApplied() {
        Employee future = emp(2L, "Terminated");
        Map<String, Object> pending = new HashMap<>();
        pending.put("status", OffboardingService.HANDOVER_PENDING);
        pending.put("effectiveOn", TODAY.plusDays(5).toString());
        pending.put("reassignReportsTo", 4L);
        pending.put("directReportIds", List.of());
        future.getCustomFields().put(OffboardingService.SCHEDULED_HANDOVER_KEY, pending);

        Employee done = emp(5L, "Terminated");
        Map<String, Object> applied = new HashMap<>();
        applied.put("status", OffboardingService.HANDOVER_APPLIED);
        applied.put("effectiveOn", TODAY.minusDays(1).toString());
        applied.put("reassignReportsTo", 4L);
        done.getCustomFields().put(OffboardingService.SCHEDULED_HANDOVER_KEY, applied);

        when(employeeRepository.findAll()).thenReturn(List.of(future, done));

        assertEquals(0, service.applyDueScheduledHandovers());
        verify(leaveClient, never()).reassignPendingApprovals(anyLong(), anyLong());
    }

    @Test
    void jobFailsClosedWhenApprovalsNotMovedAndRetriesLater() {
        Employee leaving = emp(2L, "Terminated");
        Employee takeover = emp(4L, "Active");
        Map<String, Object> sched = new HashMap<>();
        sched.put("status", OffboardingService.HANDOVER_PENDING);
        sched.put("effectiveOn", TODAY.toString());
        sched.put("reassignReportsTo", 4L);
        sched.put("directReportIds", List.of());
        leaving.getCustomFields().put(OffboardingService.SCHEDULED_HANDOVER_KEY, sched);

        when(employeeRepository.findAll()).thenReturn(List.of(leaving, takeover));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(leaving));
        when(employeeRepository.findById(4L)).thenReturn(Optional.of(takeover));
        when(employeeRepository.findByManagerId(2L)).thenReturn(List.of());
        when(leaveClient.countPendingApprovals(2L)).thenReturn(5);
        when(leaveClient.reassignPendingApprovals(2L, 4L)).thenReturn(0);

        assertEquals(0, service.applyDueScheduledHandovers());

        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) leaving.getCustomFields()
                .get(OffboardingService.SCHEDULED_HANDOVER_KEY);
        assertEquals(OffboardingService.HANDOVER_FAILED, after.get("status"));
        assertNotNull(after.get("lastError"));
    }

    @Test
    void refusesHandoverTargetThatWouldCreateLoop() {
        Employee leaving = emp(2L, "Active");
        Employee report = emp(3L, "Active");
        report.setManagerId(2L);
        Employee bad = emp(5L, "Active");
        when(employeeRepository.findByManagerId(2L)).thenReturn(List.of(report));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(bad));
        when(reportingLineService.wouldCycle(3L, 5L)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.applyStatusChange(leaving, "Terminated", TODAY, "Exit", "override", 5L));
        assertTrue(ex.getMessage().toLowerCase().contains("loop")
                || ex.getMessage().toLowerCase().contains("hand over"));
        verify(leaveClient, never()).reassignPendingApprovals(anyLong(), anyLong());
    }

    @Test
    void handoverPreviewExposesDeferredFlag() {
        Employee leaving = emp(2L, "Terminated");
        Map<String, Object> sched = new HashMap<>();
        sched.put("status", OffboardingService.HANDOVER_PENDING);
        sched.put("effectiveOn", TODAY.plusDays(3).toString());
        sched.put("reassignReportsTo", 4L);
        leaving.getCustomFields().put(OffboardingService.SCHEDULED_HANDOVER_KEY, sched);
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(leaving));
        when(employeeRepository.findByManagerId(2L)).thenReturn(List.of());
        when(leaveClient.countPendingApprovals(2L)).thenReturn(0);

        Map<String, Object> preview = service.handoverPreview(2L);
        assertEquals(true, preview.get("handoverDeferred"));
        assertEquals(TODAY.toString(), preview.get("today"));
    }
}
