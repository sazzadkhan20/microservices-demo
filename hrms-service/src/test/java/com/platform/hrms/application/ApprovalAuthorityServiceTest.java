package com.platform.hrms.application;

import com.platform.hrms.domain.Designation;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.LeaveApplication;
import com.platform.hrms.domain.LeaveApproval;
import com.platform.hrms.domain.LeavePolicy;
import com.platform.hrms.extension.config.TenantConfigLoader;
import com.platform.hrms.infrastructure.persistence.DesignationRepository;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ApprovalAuthorityService — config-driven resolvers")
class ApprovalAuthorityServiceTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock DesignationRepository designationRepository;
    @Mock TenantConfigLoader configLoader;
    @Mock RbacService rbacService;
    @InjectMocks ApprovalAuthorityService service;

    private Employee applicant;
    private Employee manager;
    private Employee stranger;
    private LeaveApplication app;
    private LeavePolicy policy;

    @BeforeEach
    void setUp() {
        applicant = emp(1L, 10L, null);
        manager = emp(10L, null, 100L);
        stranger = emp(99L, null, null);
        app = new LeaveApplication();
        ReflectionTestUtils.setField(app, "id", 50L);
        app.setEmployeeId(1L);

        policy = new LeavePolicy();
        policy.setApprovalChain(List.of(
                Map.of("step", 1, "role", "MANAGER"),
                Map.of("step", 2, "role", "HR")
        ));
        when(configLoader.loadLeavePolicyOrDefault()).thenReturn(policy);
        when(configLoader.loadSettingsMap()).thenReturn(Map.of(
                "approvalAuthority", ApprovalAuthorityService.defaultApprovalAuthority()
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
        assertDoesNotThrow(() -> service.assertCanApprove(10L, step, app));
    }

    @Test
    void nonManager_deniedOnReportingManagerStep() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.of(stranger));
        when(rbacService.hasWildcard(99L)).thenReturn(false);
        when(rbacService.hasPermission(eq(99L), eq("leave"), eq("manage"))).thenReturn(false);
        when(rbacService.roleCodesFor(99L)).thenReturn(List.of("EMPLOYEE"));

        LeaveApproval step = step(1, "MANAGER");
        assertThrows(IllegalStateException.class, () -> service.assertCanApprove(99L, step, app));
    }

    @Test
    void wildcardOverride_allowsNonManager() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.of(stranger));
        when(rbacService.hasWildcard(99L)).thenReturn(true);

        LeaveApproval step = step(1, "MANAGER");
        assertDoesNotThrow(() -> service.assertCanApprove(99L, step, app));
    }

    @Test
    @DisplayName("HR with leave:manage still cannot decide MANAGER step (not reporting manager)")
    void hrLeaveManage_cannotSkipManagerStep() {
        Employee hr = emp(20L, null, 200L);
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(hr));
        when(rbacService.hasWildcard(20L)).thenReturn(false);
        when(rbacService.hasPermission(eq(20L), eq("leave"), eq("manage"))).thenReturn(true);
        when(rbacService.roleCodesFor(20L)).thenReturn(List.of("HR"));
        Designation d = new Designation();
        d.setApproverRole("HR");
        when(designationRepository.findById(200L)).thenReturn(Optional.of(d));

        LeaveApproval step = step(1, "MANAGER");
        assertThrows(IllegalStateException.class, () -> service.assertCanApprove(20L, step, app));
    }

    @Test
    @DisplayName("HR can approve HR step after manager has acted")
    void hr_mayApproveHrStep() {
        Employee hr = emp(20L, null, 200L);
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(hr));
        when(rbacService.hasWildcard(20L)).thenReturn(false);
        when(rbacService.hasPermission(eq(20L), eq("leave"), eq("manage"))).thenReturn(true);
        when(rbacService.roleCodesFor(20L)).thenReturn(List.of("HR"));
        Designation d = new Designation();
        d.setApproverRole("HR");
        when(designationRepository.findById(200L)).thenReturn(Optional.of(d));

        LeaveApproval step = step(2, "HR");
        assertDoesNotThrow(() -> service.assertCanApprove(20L, step, app));
    }

    @Test
    @DisplayName("ADMIN overrideRoleCodes may skip manager step when allowOverride=true")
    void adminOverride_maySkipManagerStep() {
        Employee admin = emp(5L, null, null);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(admin));
        when(rbacService.hasWildcard(5L)).thenReturn(false);
        when(rbacService.hasPermission(eq(5L), eq("leave"), eq("manage"))).thenReturn(false);
        when(rbacService.roleCodesFor(5L)).thenReturn(List.of("ADMIN"));

        LeaveApproval step = step(1, "MANAGER");
        assertDoesNotThrow(() -> service.assertCanApprove(5L, step, app));
    }

    @Test
    void designationOrRbac_hrByDesignation() {
        Employee hr = emp(20L, null, 200L);
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(hr));
        when(rbacService.hasWildcard(20L)).thenReturn(false);
        when(rbacService.hasPermission(eq(20L), eq("leave"), eq("manage"))).thenReturn(false);
        when(rbacService.roleCodesFor(20L)).thenReturn(List.of("HR"));
        Designation d = new Designation();
        d.setApproverRole("HR");
        when(designationRepository.findById(200L)).thenReturn(Optional.of(d));

        LeaveApproval step = step(2, "HR");
        assertDoesNotThrow(() -> service.assertCanApprove(20L, step, app));
    }

    @Test
    void storeManager_requiresLineManagerAndDesignation() {
        policy.setApprovalChain(List.of(
                Map.of("step", 1, "role", "STORE_MANAGER")
        ));
        Employee sm = emp(10L, null, 100L);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(sm));
        when(rbacService.hasWildcard(10L)).thenReturn(false);
        when(rbacService.hasPermission(eq(10L), eq("leave"), eq("manage"))).thenReturn(false);
        when(rbacService.roleCodesFor(anyLong())).thenReturn(List.of("MANAGER"));
        Designation d = new Designation();
        d.setApproverRole("STORE_MANAGER");
        when(designationRepository.findById(100L)).thenReturn(Optional.of(d));

        LeaveApproval step = step(1, "STORE_MANAGER");
        assertDoesNotThrow(() -> service.assertCanApprove(10L, step, app));
    }

    @Test
    void storeManager_deniedWithoutDesignationMatch() {
        policy.setApprovalChain(List.of(
                Map.of("step", 1, "role", "STORE_MANAGER")
        ));
        Employee sm = emp(10L, null, 100L);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(sm));
        when(rbacService.hasWildcard(10L)).thenReturn(false);
        when(rbacService.hasPermission(eq(10L), eq("leave"), eq("manage"))).thenReturn(false);
        when(rbacService.roleCodesFor(anyLong())).thenReturn(List.of("MANAGER"));
        Designation d = new Designation();
        d.setApproverRole("MANAGER");
        when(designationRepository.findById(100L)).thenReturn(Optional.of(d));

        LeaveApproval step = step(1, "STORE_MANAGER");
        assertThrows(IllegalStateException.class, () -> service.assertCanApprove(10L, step, app));
    }

    @Test
    void rbacRole_adminStep() {
        policy.setApprovalChain(List.of(Map.of("step", 1, "role", "ADMIN")));
        Employee admin = emp(5L, null, null);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(admin));
        when(rbacService.hasWildcard(5L)).thenReturn(false);
        when(rbacService.hasPermission(eq(5L), eq("leave"), eq("manage"))).thenReturn(false);
        when(rbacService.roleCodesFor(5L)).thenReturn(List.of("ADMIN"));

        LeaveApproval step = step(1, "ADMIN");
        assertDoesNotThrow(() -> service.assertCanApprove(5L, step, app));
    }

    private static LeaveApproval step(int order, String role) {
        LeaveApproval s = new LeaveApproval();
        s.setStepOrder(order);
        s.setApproverRole(role);
        s.setStatus(LeaveApproval.STATUS_PENDING);
        return s;
    }

    private static Employee emp(long id, Long managerId, Long designationId) {
        Employee e = new Employee();
        ReflectionTestUtils.setField(e, "id", id);
        e.setFullName("E" + id);
        e.setManagerId(managerId);
        e.setDesignationId(designationId);
        e.setActive(true);
        return e;
    }
}
