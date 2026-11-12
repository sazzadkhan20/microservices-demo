package com.platform.leave.application;

import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.domain.LeaveApplication;
import com.platform.leave.domain.LeaveApproval;
import com.platform.leave.domain.LeavePolicy;
import com.platform.leave.extension.config.TenantConfigLoader;
import com.platform.leave.client.people.PeopleEmployeeGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import com.platform.leave.client.people.PeopleAuthzClient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ApprovalAuthorityService — step match + idle on-behalf")
class ApprovalAuthorityServiceTest {

    @Mock PeopleEmployeeGateway employeeRepository;
    @Mock TenantConfigLoader configLoader;
    @Mock PeopleAuthzClient rbacService;
    @InjectMocks ApprovalAuthorityService service;

    private EmployeeView applicant;
    private EmployeeView manager;
    private LeaveApplication app;
    private LeavePolicy policy;

    @BeforeEach
    void setUp() {
        applicant = emp(1L, 10L, null, null);
        manager = emp(10L, null, 100L, "MANAGER");
        app = new LeaveApplication();
        ReflectionTestUtils.setField(app, "id", 50L);
        app.setEmployeeId(1L);
        app.setCreatedAt(Instant.now());
        app.setUpdatedAt(Instant.now());

        policy = new LeavePolicy();
        policy.setApprovalChain(List.of(
                Map.of("step", 1, "role", "MANAGER"),
                Map.of("step", 2, "role", "HR")
        ));
        when(configLoader.loadLeavePolicyOrDefault()).thenReturn(policy);
        when(configLoader.loadSettingsMap()).thenReturn(Map.of(
                "approvalAuthority", ApprovalAuthorityService.defaultApprovalAuthority(),
                "leaveIdleReminder", Map.of("enabled", true, "idleWorkingDays", 2)
        ));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(applicant));
    }

    @Test
    void reportingManager_mayApprove() {
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(manager));
        when(rbacService.hasWildcard(10L)).thenReturn(false);
        when(rbacService.hasPermission(eq(10L), eq("leave"), eq("manage"))).thenReturn(false);
        when(rbacService.roleCodesFor(10L)).thenReturn(List.of("MANAGER"));

        LeaveApproval step = step(1, "MANAGER");
        ApprovalAuthorityService.Decision d = service.assertCanApprove(10L, step, app);
        assertEquals(ApprovalAuthorityService.Authority.STEP, d.authority());
    }

    @Test
    @DisplayName("Requester never decides own request even with leave:manage")
    void requester_cannotDecideOwn() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(applicant));
        when(rbacService.hasWildcard(1L)).thenReturn(false);
        when(rbacService.hasPermission(eq(1L), eq("leave"), eq("manage"))).thenReturn(true);
        when(rbacService.roleCodesFor(1L)).thenReturn(List.of("HR", "ADMIN"));
        app.setUpdatedAt(Instant.now().minus(14, ChronoUnit.DAYS));

        LeaveApproval step = step(1, "MANAGER");
        assertThrows(IllegalStateException.class, () -> service.assertCanApprove(1L, step, app));
    }

    @Test
    @DisplayName("SUPERADMIN may on-behalf only after idle working days")
    void wildcard_onBehalf_requiresIdle() {
        EmployeeView stranger = emp(99L, null, null, null);
        when(employeeRepository.findById(99L)).thenReturn(Optional.of(stranger));
        when(rbacService.hasWildcard(99L)).thenReturn(true);

        LeaveApproval step = step(1, "MANAGER");
        assertThrows(IllegalStateException.class, () -> service.assertCanApprove(99L, step, app));

        app.setUpdatedAt(Instant.now().minus(14, ChronoUnit.DAYS));
        ApprovalAuthorityService.Decision d = service.assertCanApprove(99L, step, app);
        assertEquals(ApprovalAuthorityService.Authority.ON_BEHALF, d.authority());
    }

    @Test
    @DisplayName("HR leave:manage cannot skip manager step immediately")
    void hrLeaveManage_cannotSkipManagerWithoutIdle() {
        EmployeeView hr = emp(20L, null, 200L, "HR");
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(hr));
        when(rbacService.hasWildcard(20L)).thenReturn(false);
        when(rbacService.hasPermission(eq(20L), eq("leave"), eq("manage"))).thenReturn(true);
        when(rbacService.roleCodesFor(20L)).thenReturn(List.of("HR"));

        LeaveApproval step = step(1, "MANAGER");
        assertThrows(IllegalStateException.class, () -> service.assertCanApprove(20L, step, app));
    }

    @Test
    @DisplayName("HR leave:manage may on-behalf after idle")
    void hrLeaveManage_onBehalfWhenIdle() {
        EmployeeView hr = emp(20L, null, 200L, "HR");
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(hr));
        when(rbacService.hasWildcard(20L)).thenReturn(false);
        when(rbacService.hasPermission(eq(20L), eq("leave"), eq("manage"))).thenReturn(true);
        when(rbacService.roleCodesFor(20L)).thenReturn(List.of("HR"));
        app.setUpdatedAt(Instant.now().minus(14, ChronoUnit.DAYS));

        LeaveApproval step = step(1, "MANAGER");
        ApprovalAuthorityService.Decision d = service.assertCanApprove(20L, step, app);
        assertEquals(ApprovalAuthorityService.Authority.ON_BEHALF, d.authority());
    }

    @Test
    @DisplayName("HR can approve HR step as normal STEP authority")
    void hr_mayApproveHrStep() {
        EmployeeView hr = emp(20L, null, 200L, "HR");
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(hr));
        when(rbacService.hasWildcard(20L)).thenReturn(false);
        when(rbacService.hasPermission(eq(20L), eq("leave"), eq("manage"))).thenReturn(true);
        when(rbacService.roleCodesFor(20L)).thenReturn(List.of("HR"));

        LeaveApproval step = step(2, "HR");
        ApprovalAuthorityService.Decision d = service.assertCanApprove(20L, step, app);
        assertEquals(ApprovalAuthorityService.Authority.STEP, d.authority());
    }

    @Test
    @DisplayName("HR cannot approve ADMIN step without being ADMIN or idle on-behalf")
    void hr_cannotApproveAdminStepImmediately() {
        EmployeeView hr = emp(20L, null, 200L, "HR");
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(hr));
        when(rbacService.hasWildcard(20L)).thenReturn(false);
        when(rbacService.hasPermission(eq(20L), eq("leave"), eq("manage"))).thenReturn(true);
        when(rbacService.roleCodesFor(20L)).thenReturn(List.of("HR"));

        LeaveApproval step = step(3, "ADMIN");
        step.setResolver(ApprovalAuthorityService.RESOLVER_RBAC_ROLE);
        assertThrows(IllegalStateException.class, () -> service.assertCanApprove(20L, step, app));
    }

    @Test
    @DisplayName("ADMIN may not skip manager step until idle")
    void adminOverride_requiresIdle() {
        EmployeeView admin = emp(5L, null, null, null);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(admin));
        when(rbacService.hasWildcard(5L)).thenReturn(false);
        when(rbacService.hasPermission(eq(5L), eq("leave"), eq("manage"))).thenReturn(false);
        when(rbacService.roleCodesFor(5L)).thenReturn(List.of("ADMIN"));

        LeaveApproval step = step(1, "MANAGER");
        assertThrows(IllegalStateException.class, () -> service.assertCanApprove(5L, step, app));

        app.setUpdatedAt(Instant.now().minus(14, ChronoUnit.DAYS));
        assertDoesNotThrow(() -> service.assertCanApprove(5L, step, app));
    }

    @Test
    void designationOrRbac_hrByDesignation() {
        EmployeeView hr = emp(20L, null, 200L, "HR");
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(hr));
        when(rbacService.hasWildcard(20L)).thenReturn(false);
        when(rbacService.hasPermission(eq(20L), eq("leave"), eq("manage"))).thenReturn(false);
        when(rbacService.roleCodesFor(20L)).thenReturn(List.of("HR"));

        LeaveApproval step = step(2, "HR");
        assertDoesNotThrow(() -> service.assertCanApprove(20L, step, app));
    }

    @Test
    void storeManager_requiresLineManagerAndDesignation() {
        policy.setApprovalChain(List.of(
                Map.of("step", 1, "role", "STORE_MANAGER")
        ));
        EmployeeView sm = emp(10L, null, 100L, "STORE_MANAGER");
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(sm));
        when(rbacService.hasWildcard(10L)).thenReturn(false);
        when(rbacService.hasPermission(eq(10L), eq("leave"), eq("manage"))).thenReturn(false);
        when(rbacService.roleCodesFor(anyLong())).thenReturn(List.of("MANAGER"));

        LeaveApproval step = step(1, "STORE_MANAGER");
        assertDoesNotThrow(() -> service.assertCanApprove(10L, step, app));
    }

    @Test
    void storeManager_deniedWithoutDesignationMatch() {
        policy.setApprovalChain(List.of(
                Map.of("step", 1, "role", "STORE_MANAGER")
        ));
        EmployeeView sm = emp(10L, null, 100L, "MANAGER");
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(sm));
        when(rbacService.hasWildcard(10L)).thenReturn(false);
        when(rbacService.hasPermission(eq(10L), eq("leave"), eq("manage"))).thenReturn(false);
        when(rbacService.roleCodesFor(anyLong())).thenReturn(List.of("MANAGER"));

        LeaveApproval step = step(1, "STORE_MANAGER");
        assertThrows(IllegalStateException.class, () -> service.assertCanApprove(10L, step, app));
    }

    @Test
    void rbacRole_adminStep() {
        policy.setApprovalChain(List.of(Map.of("step", 1, "role", "ADMIN")));
        EmployeeView admin = emp(5L, null, null, null);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(admin));
        when(rbacService.hasWildcard(5L)).thenReturn(false);
        when(rbacService.hasPermission(eq(5L), eq("leave"), eq("manage"))).thenReturn(false);
        when(rbacService.roleCodesFor(5L)).thenReturn(List.of("ADMIN"));

        LeaveApproval step = step(1, "ADMIN");
        ApprovalAuthorityService.Decision d = service.assertCanApprove(5L, step, app);
        assertEquals(ApprovalAuthorityService.Authority.STEP, d.authority());
    }

    @Test
    @DisplayName("RBAC role-pool step with null expectedApprover notifies all active holders")
    void recipientIds_rolePool_includesHrHolders() {
        EmployeeView hr1 = emp(20L, null, 200L, "HR");
        EmployeeView hr2 = emp(21L, null, 200L, "HR");
        EmployeeView other = emp(30L, null, null, null);
        when(employeeRepository.findAll()).thenReturn(List.of(applicant, manager, hr1, hr2, other));
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(hr1));
        when(employeeRepository.findById(21L)).thenReturn(Optional.of(hr2));
        when(employeeRepository.findById(30L)).thenReturn(Optional.of(other));
        when(rbacService.roleCodesFor(20L)).thenReturn(List.of("HR"));
        when(rbacService.roleCodesFor(21L)).thenReturn(List.of("HR"));
        when(rbacService.roleCodesFor(30L)).thenReturn(List.of("EMPLOYEE"));
        when(rbacService.roleCodesFor(1L)).thenReturn(List.of("EMPLOYEE"));
        when(rbacService.roleCodesFor(10L)).thenReturn(List.of("MANAGER"));

        LeaveApproval step = step(2, "HR");
        step.setResolver(ApprovalAuthorityService.RESOLVER_RBAC_ROLE);
        step.setExpectedApproverId(null);

        List<Long> ids = service.recipientIdsForStep(step, app);
        assertEquals(List.of(20L, 21L), ids);
    }

    @Test
    @DisplayName("Person-bound step notifies only expectedApproverId")
    void recipientIds_personBound_usesExpected() {
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(manager));
        LeaveApproval step = step(1, "MANAGER");
        step.setExpectedApproverId(10L);
        assertEquals(List.of(10L), service.recipientIdsForStep(step, app));
    }

    private static LeaveApproval step(int order, String role) {
        LeaveApproval s = new LeaveApproval();
        s.setStepOrder(order);
        s.setApproverRole(role);
        s.setStatus(LeaveApproval.STATUS_PENDING);
        return s;
    }

    private static EmployeeView emp(long id, Long managerId, Long designationId, String approverRole) {
        EmployeeView e = new EmployeeView();
        ReflectionTestUtils.setField(e, "id", id);
        e.setFullName("E" + id);
        e.setManagerId(managerId);
        e.setDesignationId(designationId);
        e.setDesignationApproverRole(approverRole);
        e.setActive(true);
        return e;
    }
}
