package com.platform.people.application;

import com.platform.common.security.AuthContext;
import com.platform.people.domain.Employee;
import com.platform.people.domain.TenantSettings;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.infrastructure.persistence.TenantSettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChecklistAssignmentService — FR-CL-02 attach with owners + due dates")
class ChecklistAssignmentServiceTest {

    @Mock TenantSettingsRepository settingsRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock AuditService auditService;
    @Mock RbacService rbacService;

    ChecklistOwnerResolver ownerResolver;
    ChecklistAssignmentService service;

    @BeforeEach
    void setUp() {
        ownerResolver = new ChecklistOwnerResolver(employeeRepository, rbacService);
        service = new ChecklistAssignmentService(settingsRepository, employeeRepository, auditService, ownerResolver);
        AuthContext.setEmployeeId(99L); // SuperAdmin assigner — must NOT become HR owner
        org.mockito.Mockito.lenient().when(rbacService.roleCodesFor(99L)).thenReturn(List.of("SUPERADMIN"));
        org.mockito.Mockito.lenient().when(rbacService.roleCodesFor(2L)).thenReturn(List.of("HR"));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private static Employee emp(long id, Long managerId) {
        Employee e = new Employee();
        ReflectionTestUtils.setField(e, "id", id);
        e.setFullName("E" + id);
        e.setManagerId(managerId);
        e.setActive(true);
        e.setStatus("ACTIVE");
        return e;
    }

    @Test
    void assignResolvesHrToPrimaryHrNotAssigner() {
        Employee subject = emp(10L, 4L);
        Employee hr = emp(2L, null);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(subject));
        when(employeeRepository.findAll()).thenReturn(List.of(hr, subject, emp(99L, null)));

        TenantSettings row = new TenantSettings();
        row.setSettings(new HashMap<>()); // empty → default templates
        when(settingsRepository.findAll()).thenReturn(List.of(row));
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDate start = LocalDate.of(2026, 11, 1);
        Map<String, Object> assigned = service.assign(10L, "ONBOARDING", start);

        assertEquals(10L, Long.valueOf(String.valueOf(assigned.get("employeeId"))));
        assertEquals("2026-11-01", String.valueOf(assigned.get("baseDate")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) assigned.get("items");
        assertNotNull(items);
        assertFalse(items.isEmpty());

        // First default item: HR owner = primary HR (2), NOT SuperAdmin assigner (99)
        assertEquals("HR", String.valueOf(items.get(0).get("ownerType")));
        assertEquals(2L, Long.valueOf(String.valueOf(items.get(0).get("ownerEmployeeId"))));
        assertEquals("2026-11-01", String.valueOf(items.get(0).get("dueDate")));

        // Manager item exists with owner = manager 4
        boolean managerOwned = items.stream().anyMatch(i ->
                "MANAGER".equalsIgnoreCase(String.valueOf(i.get("ownerType")))
                        && "4".equals(String.valueOf(i.get("ownerEmployeeId"))));
        assertTrue(managerOwned);

        // Employee-owned item
        boolean empOwned = items.stream().anyMatch(i ->
                "EMPLOYEE".equalsIgnoreCase(String.valueOf(i.get("ownerType")))
                        && "10".equals(String.valueOf(i.get("ownerEmployeeId"))));
        assertTrue(empOwned);

        ArgumentCaptor<TenantSettings> cap = ArgumentCaptor.forClass(TenantSettings.class);
        verify(settingsRepository).save(cap.capture());
        Object raw = cap.getValue().getSettings().get("checklists");
        assertTrue(raw instanceof List<?> list && !list.isEmpty());
    }

    @Test
    void repairMisassignedHrOwnersFixesAdminOwnedHrItems() {
        Employee hr = emp(2L, null);
        when(employeeRepository.findAll()).thenReturn(List.of(hr));
        when(rbacService.roleCodesFor(2L)).thenReturn(List.of("HR"));
        when(rbacService.roleCodesFor(1L)).thenReturn(List.of("SUPERADMIN"));

        Map<String, Object> badItem = new HashMap<>();
        badItem.put("title", "Laptop ready");
        badItem.put("ownerType", "HR");
        badItem.put("ownerEmployeeId", 1L); // Asman / SuperAdmin wrongly stored
        badItem.put("completed", false);

        Map<String, Object> assigned = new HashMap<>();
        assigned.put("id", "5");
        assigned.put("type", "ONBOARDING");
        assigned.put("employeeId", 10L);
        assigned.put("items", new ArrayList<>(List.of(badItem)));

        TenantSettings row = new TenantSettings();
        Map<String, Object> settings = new HashMap<>();
        settings.put("checklists", new ArrayList<>(List.of(assigned)));
        row.setSettings(settings);
        when(settingsRepository.findAll()).thenReturn(List.of(row));
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int fixed = service.repairMisassignedHrOwners();
        assertEquals(1, fixed);

        ArgumentCaptor<TenantSettings> cap = ArgumentCaptor.forClass(TenantSettings.class);
        verify(settingsRepository).save(cap.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> saved = (List<Map<String, Object>>) cap.getValue().getSettings().get("checklists");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fixedItems = (List<Map<String, Object>>) saved.get(0).get("items");
        assertEquals(2L, Long.valueOf(String.valueOf(fixedItems.get(0).get("ownerEmployeeId"))));
    }

    @Test
    void offboardingAttachIsIdempotent() {
        List<Map<String, Object>> existing = new ArrayList<>();
        Map<String, Object> already = new HashMap<>();
        already.put("id", "9");
        already.put("type", "OFFBOARDING");
        already.put("employeeId", 10L);
        already.put("items", List.of());
        existing.add(already);

        TenantSettings row = new TenantSettings();
        Map<String, Object> settings = new HashMap<>();
        settings.put("checklists", existing);
        row.setSettings(settings);
        when(settingsRepository.findAll()).thenReturn(List.of(row));

        assertTrue(service.hasAssignedChecklist(10L, "OFFBOARDING"));
        service.assignOffboardingBestEffort(10L, LocalDate.of(2026, 12, 1));
        // Already assigned → assign() not called → no save
        verify(settingsRepository, org.mockito.Mockito.never()).save(any());
    }
}
