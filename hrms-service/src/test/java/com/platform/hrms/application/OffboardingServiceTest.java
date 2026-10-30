package com.platform.hrms.application;

import com.platform.common.security.AuthContext;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.rbac.AppRole;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApprovalRepository;
import com.platform.hrms.infrastructure.persistence.TenantSettingsRepository;
import org.junit.jupiter.api.AfterEach;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OffboardingService — FR-EMP-10 / FR-EMP-11")
class OffboardingServiceTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock LeaveApplicationRepository leaveApplicationRepository;
    @Mock LeaveApprovalRepository leaveApprovalRepository;
    @Mock TenantSettingsRepository tenantSettingsRepository;
    @Mock RbacService rbacService;
    @Mock ReportingLineService reportingLineService;
    @Mock EmploymentHistoryService employmentHistoryService;
    @Mock EmployeeRulesService rulesService;
    @InjectMocks OffboardingService service;

    private EmployeeRulesService.Rules rules;

    @BeforeEach
    void setUp() {
        rules = new EmployeeRulesService.Rules(
                "qiicreative.com.au",
                List.of("qiicreative.com.au"),
                EmployeeRulesService.DEFAULT_STATUSES,
                EmployeeRulesService.DEFAULT_OFFBOARD,
                EmployeeRulesService.DEFAULT_INACTIVE,
                EmployeeRulesService.DEFAULT_CONTRACT_TYPES,
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
        when(leaveApplicationRepository.findAll()).thenReturn(List.of());
        when(employeeRepository.findByManagerId(anyLong())).thenReturn(List.of());
        when(rbacService.rolesFor(anyLong())).thenReturn(List.of());
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
        e.setCustomFields(Map.of());
        return e;
    }

    @Test
    void refusesSelfDeactivation() {
        AuthContext.setEmployeeId(9L);
        Employee target = emp(9L, "Active");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.applyStatusChange(target, "Resigned", LocalDate.now(), "Left", "override", null));
        assertTrue(ex.getMessage().toLowerCase().contains("own account"));
    }

    @Test
    void requiresExitDateAndReasonForOffboarding() {
        Employee target = emp(2L, "Active");
        when(rbacService.hasPermission(1L, "employees", "edit")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () ->
                service.applyStatusChange(target, "Resigned", null, "Bye", "override ok", null));
        assertThrows(IllegalArgumentException.class, () ->
                service.applyStatusChange(target, "Resigned", LocalDate.now(), "  ", "override ok", null));
    }

    @Test
    void acceptsOffboardWithOverrideAndRecordsHistory() {
        Employee target = emp(2L, "Active");
        when(rbacService.hasPermission(1L, "employees", "edit")).thenReturn(true);
        service.applyStatusChange(target, "Resigned", LocalDate.of(2026, 10, 1), "Relocation", "admin override", null);
        assertEquals("Resigned", target.getStatus());
        assertEquals(false, target.isActive());
        verify(employmentHistoryService).record(eq(2L), eq("status"), eq("Active"), eq("Resigned"));
        verify(employmentHistoryService).record(eq(2L), eq("exitDate"), eq(null), eq("2026-10-01"));
        verify(employmentHistoryService).record(eq(2L), eq("exitReason"), eq(null), eq("Relocation"));
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
        when(rbacService.hasPermission(1L, "employees", "edit")).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.applyStatusChange(target, "Terminated", LocalDate.now(), "Gone", "override", null));
        assertTrue(ex.getMessage().toLowerCase().contains("protected"));
        verify(employmentHistoryService, never()).record(anyLong(), eq("status"), any(), any());
    }
}
