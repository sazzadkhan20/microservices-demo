package com.platform.people.application;

import com.platform.people.client.LeaveClient;
import com.platform.people.domain.Employee;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportingLineDueService — apply due manager + move applicant approvals")
class ReportingLineDueServiceTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock ReportingLineService reportingLineService;
    @Mock EmploymentHistoryService employmentHistoryService;
    @Mock LeaveClient leaveClient;

    private ReportingLineDueService service;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @BeforeEach
    void setUp() {
        service = new ReportingLineDueService(
                employeeRepository, reportingLineService, employmentHistoryService, leaveClient, null);
        when(reportingLineService.businessToday()).thenReturn(TODAY);
    }

    private static Employee emp(long id, Long managerId) {
        Employee e = new Employee();
        ReflectionTestUtils.setField(e, "id", id);
        e.setManagerId(managerId);
        e.setActive(true);
        return e;
    }

    @Test
    void findsEmployeesWhereHistoryDiffersFromPointer() {
        Employee stale = emp(3L, 2L); // pointer still old mgr
        Employee ok = emp(4L, 1L);
        when(employeeRepository.findAll()).thenReturn(List.of(stale, ok));
        when(reportingLineService.managerOn(3L, TODAY)).thenReturn(1L); // due new mgr
        when(reportingLineService.managerOn(4L, TODAY)).thenReturn(1L);

        assertEquals(List.of(3L), service.findDueEmployeeIds());
    }

    @Test
    void applyOneFlipsManagerAndMovesApprovals() {
        Employee e = emp(3L, 2L);
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(e));
        when(reportingLineService.managerOn(3L, TODAY)).thenReturn(1L);
        when(reportingLineService.wouldCycle(3L, 1L)).thenReturn(false);
        when(leaveClient.reassignApprovalsForApplicant(3L, 2L, 1L)).thenReturn(2);

        assertTrue(service.applyOneDueManagerChange(3L));
        assertEquals(1L, e.getManagerId());
        verify(employmentHistoryService).record(eq(3L), eq("managerId"), eq(2L), eq(1L));
        verify(leaveClient).reassignApprovalsForApplicant(3L, 2L, 1L);
        verify(employeeRepository).save(e);
    }

    @Test
    void skipsWhenWouldCreateLoop() {
        Employee e = emp(3L, 2L);
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(e));
        when(reportingLineService.managerOn(3L, TODAY)).thenReturn(9L);
        when(reportingLineService.wouldCycle(3L, 9L)).thenReturn(true);

        assertFalse(service.applyOneDueManagerChange(3L));
        assertEquals(2L, e.getManagerId());
        verify(leaveClient, never()).reassignApprovalsForApplicant(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }
}
