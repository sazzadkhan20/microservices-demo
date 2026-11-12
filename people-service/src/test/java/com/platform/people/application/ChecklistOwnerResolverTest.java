package com.platform.people.application;

import com.platform.people.domain.Employee;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChecklistOwnerResolver — HR pool vs Admin/SuperAdmin")
class ChecklistOwnerResolverTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock RbacService rbacService;

    private static Employee emp(long id) {
        Employee e = new Employee();
        ReflectionTestUtils.setField(e, "id", id);
        e.setFullName("E" + id);
        e.setActive(true);
        e.setStatus("ACTIVE");
        return e;
    }

    @Test
    void primaryHrIgnoresAdminAndSuperAdmin() {
        Employee admin = emp(1L);
        Employee hr = emp(5L);
        Employee superAdmin = emp(99L);
        when(employeeRepository.findAll()).thenReturn(List.of(admin, hr, superAdmin));
        when(rbacService.roleCodesFor(1L)).thenReturn(List.of("ADMIN"));
        when(rbacService.roleCodesFor(5L)).thenReturn(List.of("HR"));
        when(rbacService.roleCodesFor(99L)).thenReturn(List.of("SUPERADMIN"));

        ChecklistOwnerResolver resolver = new ChecklistOwnerResolver(employeeRepository, rbacService);
        assertEquals(5L, resolver.primaryHrEmployeeId());
        assertTrue(resolver.holdsHrRole(5L));
        assertFalse(resolver.holdsHrRole(1L));
        assertFalse(resolver.holdsHrRole(99L));
        assertTrue(resolver.needsHrOwnerRepair(1L));
        assertFalse(resolver.needsHrOwnerRepair(5L));
    }

    @Test
    void resolveMapsOwnerTypes() {
        Employee subject = emp(10L);
        subject.setManagerId(4L);
        when(employeeRepository.findAll()).thenReturn(List.of(emp(2L)));
        when(rbacService.roleCodesFor(2L)).thenReturn(List.of("HR"));

        ChecklistOwnerResolver resolver = new ChecklistOwnerResolver(employeeRepository, rbacService);
        assertEquals(2L, resolver.resolve("HR", null, subject));
        assertEquals(4L, resolver.resolve("MANAGER", null, subject));
        assertEquals(10L, resolver.resolve("EMPLOYEE", null, subject));
        assertEquals(7L, resolver.resolve("NAMED", 7L, subject));
        assertNull(resolver.resolve(null, null, subject));
    }
}
