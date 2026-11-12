package com.platform.people.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.people.application.AuditService;
import com.platform.people.application.DataScopeService;
import com.platform.people.application.EmployeeRulesService;
import com.platform.people.client.FilesGatewayClient;
import com.platform.people.domain.Employee;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SEC01 / guide §4: document metadata requires feature permission AND record scope. */
class EmployeeDocumentScopeTest {

    private EmployeeRepository employees;
    private DataScopeService scope;
    private EmployeeDocumentController controller;

    @BeforeEach
    void setUp() {
        employees = mock(EmployeeRepository.class);
        scope = mock(DataScopeService.class);
        Employee e = new Employee();
        Map<String, Object> cf = new HashMap<>();
        cf.put("documents", List.of(Map.of("fileId", "33", "type", "Contract", "name", "c.pdf")));
        e.setCustomFields(cf);
        when(employees.findById(anyLong())).thenReturn(Optional.of(e));
        controller = new EmployeeDocumentController(employees, mock(AuditService.class),
                mock(EmployeeRulesService.class), mock(FilesGatewayClient.class), scope);
    }

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    @Test
    void colleagueCannotListAnotherEmployeesDocuments() {
        AuthContext.setEmployeeId(6L);
        assertThrows(SecurityException.class, () -> controller.list(15L));
    }

    @Test
    void colleagueCannotRemoveAnotherEmployeesDocument() {
        AuthContext.setEmployeeId(6L);
        assertThrows(SecurityException.class, () -> controller.remove(15L, "33"));
        verify(employees, never()).save(any());
    }

    @Test
    void colleagueCannotAttachToAnotherEmployee() {
        AuthContext.setEmployeeId(6L);
        assertThrows(SecurityException.class, () -> controller.add(15L, Map.of("fileId", "1", "type", "Contract", "name", "x")));
        verify(employees, never()).save(any());
    }

    @Test
    void selfAlwaysAllowed() {
        AuthContext.setEmployeeId(15L);
        assertDoesNotThrow(() -> controller.list(15L));
    }

    @Test
    void managerTeamScopeAndHrAllScopeAllowed() {
        when(scope.canAccess(eq(4L), eq(6L), eq("employees"), eq("view"))).thenReturn(true);
        AuthContext.setEmployeeId(4L);
        assertDoesNotThrow(() -> controller.list(6L));

        when(scope.canAccess(eq(2L), eq(15L), eq("documents"), eq("delete"))).thenReturn(true);
        AuthContext.setEmployeeId(2L);
        assertDoesNotThrow(() -> controller.remove(15L, "33"));
    }
}
