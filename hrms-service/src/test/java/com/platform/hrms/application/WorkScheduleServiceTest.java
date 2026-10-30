package com.platform.hrms.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.WorkSchedule;
import com.platform.hrms.extension.config.TenantConfigLoader;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.HolidayRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.hrms.infrastructure.persistence.WorkScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkScheduleService — FR-SCH resolve + expected hours")
class WorkScheduleServiceTest {

    @Mock WorkScheduleRepository repository;
    @Mock EmployeeRepository employeeRepository;
    @Mock HolidayRepository holidayRepository;
    @Mock LeaveApplicationRepository leaveApplicationRepository;
    @Mock TenantConfigLoader configLoader;
    @Mock AuditService auditService;

    WorkScheduleService service;

    @BeforeEach
    void setUp() {
        service = new WorkScheduleService(
                repository, employeeRepository, holidayRepository, leaveApplicationRepository,
                configLoader, auditService, new ObjectMapper());
        when(configLoader.loadSettingsMap()).thenReturn(Map.of());
        when(holidayRepository.findAll()).thenReturn(List.of());
        when(leaveApplicationRepository.findByEmployeeIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> {
            WorkSchedule s = inv.getArgument(0);
            if (s.getId() == null) ReflectionTestUtils.setField(s, "id", 99L);
            return s;
        });
    }

    private static Employee emp(long id, Long teamId, Long locationId) {
        Employee e = new Employee();
        ReflectionTestUtils.setField(e, "id", id);
        e.setTeamId(teamId);
        e.setLocationId(locationId);
        e.setActive(true);
        return e;
    }

    private static WorkSchedule sch(String scope, long scopeId, String mode, String days, String start, String end) {
        WorkSchedule s = new WorkSchedule();
        ReflectionTestUtils.setField(s, "id", scopeId + 100);
        s.setScopeType(scope);
        s.setScopeId(scopeId);
        s.setMode(mode);
        s.setWorkingDays(days);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setGraceMinutes(15);
        s.setHalfDayHours(new BigDecimal("4"));
        return s;
    }

    @Test
    void resolvePrefersEmployeeOverTeamOverLocation() {
        Employee e = emp(5L, 2L, 1L);
        when(repository.findByScopeTypeAndScopeId("EMPLOYEE", 5L))
                .thenReturn(Optional.of(sch("EMPLOYEE", 5L, "FIXED", "[\"SUN\"]", "09:00", "13:00")));
        when(repository.findByScopeTypeAndScopeId("TEAM", 2L))
                .thenReturn(Optional.of(sch("TEAM", 2L, "FIXED", "[\"SUN\",\"MON\"]", "10:00", "18:00")));
        when(repository.findByScopeTypeAndScopeId("LOCATION", 1L))
                .thenReturn(Optional.of(sch("LOCATION", 1L, "FIXED", "[\"SUN\",\"MON\",\"TUE\"]", "10:00", "18:00")));

        WorkSchedule resolved = service.resolveForEmployee(e).orElseThrow();
        assertEquals("EMPLOYEE", resolved.getScopeType());
        assertEquals(5L, resolved.getScopeId());
    }

    @Test
    void fixedExpectedHoursAreEndMinusStartOnWorkingDay() {
        Employee e = emp(1L, null, 10L);
        WorkSchedule loc = sch("LOCATION", 10L, "FIXED",
                "[\"SUN\",\"MON\",\"TUE\",\"WED\",\"THU\"]", "10:00", "18:00");
        when(repository.findByScopeTypeAndScopeId("LOCATION", 10L)).thenReturn(Optional.of(loc));

        // Sunday 2026-11-01 is SUN
        BigDecimal hours = service.expectedHoursForDay(e, LocalDate.of(2026, 11, 1));
        assertEquals(0, hours.compareTo(new BigDecimal("8.0000")));

        // Friday — off
        BigDecimal fri = service.expectedHoursForDay(e, LocalDate.of(2026, 11, 6));
        assertEquals(0, fri.compareTo(BigDecimal.ZERO));
    }

    @Test
    void hourlyExpectedHoursAreWeeklyTimesDaysOverSeven() {
        Employee e = emp(1L, null, 10L);
        WorkSchedule loc = sch("LOCATION", 10L, "HOURLY", "[]", null, null);
        loc.setWeeklyHours(new BigDecimal("35"));
        when(repository.findByScopeTypeAndScopeId("LOCATION", 10L)).thenReturn(Optional.of(loc));

        BigDecimal period = service.expectedHoursForPeriod(e, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7));
        // 35 * 7 / 7 = 35
        assertEquals(0, period.compareTo(new BigDecimal("35.0000")));
    }

    @Test
    void createRejectsDuplicateScope() {
        when(repository.findByScopeTypeAndScopeId("LOCATION", 1L))
                .thenReturn(Optional.of(sch("LOCATION", 1L, "FIXED", "[\"SUN\"]", "10:00", "18:00")));
        WorkSchedule incoming = sch("LOCATION", 1L, "FIXED", "[\"SUN\",\"MON\"]", "10:00", "18:00");
        ReflectionTestUtils.setField(incoming, "id", null);
        assertThrows(IllegalStateException.class, () -> service.create(incoming));
        verify(repository, never()).save(any());
    }

    @Test
    void hourlyCreateClearsFixedFields() {
        when(repository.findByScopeTypeAndScopeId(anyString(), anyLong())).thenReturn(Optional.empty());
        WorkSchedule incoming = new WorkSchedule();
        incoming.setScopeType("EMPLOYEE");
        incoming.setScopeId(7L);
        incoming.setMode("HOURLY");
        incoming.setWorkingDays("[\"MON\"]");
        incoming.setStartTime("09:00");
        incoming.setEndTime("17:00");
        incoming.setWeeklyHours(new BigDecimal("40"));

        WorkSchedule saved = service.create(incoming);
        assertEquals("HOURLY", saved.getMode());
        assertTrue(saved.getStartTime() == null || saved.getStartTime().isBlank());
        assertEquals(0, saved.getWeeklyHours().compareTo(new BigDecimal("40")));
        verify(auditService).record(any(), eq("CREATE"), eq("WorkSchedule"), any(), eq(null), any());
    }

    @Test
    void syncFromWorkPatternCreatesDays6EmployeeOverride() {
        when(configLoader.loadSettingsMap()).thenReturn(Map.of(
                "scheduleRules", Map.of(
                        "patternTemplates", Map.of(
                                "DAYS_6", Map.of(
                                        "mode", "FIXED",
                                        "workingDays", List.of("SUN", "MON", "TUE", "WED", "THU", "FRI"),
                                        "inheritLocationHours", true
                                )
                        )
                )
        ));
        Employee e = emp(9L, 2L, 1L);
        e.setWorkPattern("DAYS_6");
        WorkSchedule loc = sch("LOCATION", 1L, "FIXED",
                "[\"SUN\",\"MON\",\"TUE\",\"WED\",\"THU\"]", "10:00", "18:00");
        when(repository.findByScopeTypeAndScopeId("LOCATION", 1L)).thenReturn(Optional.of(loc));
        when(repository.findByScopeTypeAndScopeId("EMPLOYEE", 9L)).thenReturn(Optional.empty());

        service.syncFromWorkPattern(e);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(s ->
                "EMPLOYEE".equals(s.getScopeType())
                        && Long.valueOf(9L).equals(s.getScopeId())
                        && "FIXED".equals(s.getMode())
                        && "10:00".equals(s.getStartTime())
                        && s.getWorkingDays() != null
                        && s.getWorkingDays().contains("FRI")));
    }
}
