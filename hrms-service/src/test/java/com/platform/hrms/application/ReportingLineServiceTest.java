package com.platform.hrms.application;

import com.platform.hrms.domain.Employee;
import com.platform.hrms.extension.config.TenantConfigLoader;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.ReportingLineHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportingLineService — cycle + effective date rules")
class ReportingLineServiceTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock ReportingLineHistoryRepository historyRepository;
    @Mock TenantConfigLoader configLoader;
    @InjectMocks ReportingLineService service;

    private static Employee emp(long id, Long managerId) {
        Employee e = new Employee();
        ReflectionTestUtils.setField(e, "id", id);
        e.setFullName("E" + id);
        e.setManagerId(managerId);
        e.setActive(true);
        return e;
    }

    @Test
    void refusesSelfAsManager() {
        assertTrue(service.wouldCycle(5L, 5L));
    }

    @Test
    void refusesDirectReportAsManager() {
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(emp(2L, 1L)));
        assertTrue(service.wouldCycle(1L, 2L));
    }

    @Test
    void refusesIndirectReportAsManager() {
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(emp(3L, 2L)));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(emp(2L, 1L)));
        assertTrue(service.wouldCycle(1L, 3L));
    }

    @Test
    void allowsUnrelatedManager() {
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(emp(9L, null)));
        assertFalse(service.wouldCycle(1L, 9L));
    }

    @Test
    void assertNoCycleThrowsReadableMessage() {
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(emp(2L, 1L)));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp(1L, null)));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.assertNoCycle(1L, 2L));
        assertTrue(ex.getMessage().toLowerCase().contains("loop"));
    }

    @Test
    void refusesPastEffectiveDateByDefault() {
        when(configLoader.loadSettingsMap()).thenReturn(Map.of());
        LocalDate today = LocalDate.now();
        ReportingLineService.ReportingLineRules rules = service.loadRules();
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveAndValidateEffectiveDate(today.minusDays(1), today, rules));
        assertTrue(ex.getMessage().toLowerCase().contains("past"));
    }

    @Test
    void allowsTodayAndFutureByDefault() {
        when(configLoader.loadSettingsMap()).thenReturn(Map.of());
        LocalDate today = LocalDate.now();
        ReportingLineService.ReportingLineRules rules = service.loadRules();
        assertEquals(today, service.resolveAndValidateEffectiveDate(today, today, rules));
        assertEquals(today.plusDays(7), service.resolveAndValidateEffectiveDate(today.plusDays(7), today, rules));
    }

    @Test
    void requireEffectiveDate_rejectsNull() {
        when(configLoader.loadSettingsMap()).thenReturn(Map.of(
                "reportingLine", Map.of("requireEffectiveDate", true)
        ));
        LocalDate today = LocalDate.now();
        ReportingLineService.ReportingLineRules rules = service.loadRules();
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveAndValidateEffectiveDate(null, today, rules));
        assertTrue(ex.getMessage().toLowerCase().contains("required"));
    }

    @Test
    void configCanAllowPastWithinWindow() {
        when(configLoader.loadSettingsMap()).thenReturn(Map.of(
                "reportingLine", Map.of(
                        "allowPastEffectiveDate", true,
                        "maxPastDays", 30
                )
        ));
        LocalDate today = LocalDate.now();
        ReportingLineService.ReportingLineRules rules = service.loadRules();
        assertEquals(today.minusDays(10), service.resolveAndValidateEffectiveDate(today.minusDays(10), today, rules));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveAndValidateEffectiveDate(today.minusDays(31), today, rules));
    }
}
