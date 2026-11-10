package com.platform.timework.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.timework.client.people.EmployeeView;
import com.platform.timework.domain.Holiday;
import com.platform.timework.domain.WorkSchedule;
import com.platform.timework.extension.config.TenantConfigLoader;
import com.platform.timework.client.people.PeopleEmployeeGateway;
import com.platform.timework.infrastructure.persistence.HolidayRepository;
import com.platform.timework.infrastructure.persistence.WorkScheduleRepository;
import com.platform.timework.client.LeaveClient;
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
    @Mock PeopleEmployeeGateway employeeRepository;
    @Mock HolidayRepository holidayRepository;
    @Mock LeaveClient leaveClient;
    @Mock TenantConfigLoader configLoader;
    @Mock AuditService auditService;

    WorkScheduleService service;

    @BeforeEach
    void setUp() {
        service = new WorkScheduleService(
                repository, employeeRepository, holidayRepository, leaveClient,
                configLoader, auditService, new ObjectMapper());
        when(configLoader.loadSettingsMap()).thenReturn(Map.of());
        when(holidayRepository.findAll()).thenReturn(List.of());
        when(leaveClient.leaveImpact(anyLong(), any())).thenReturn(LeaveClient.LeaveImpact.NONE);
        when(repository.save(any())).thenAnswer(inv -> {
            WorkSchedule s = inv.getArgument(0);
            if (s.getId() == null) ReflectionTestUtils.setField(s, "id", 99L);
            return s;
        });
    }

    private static EmployeeView emp(long id, Long teamId, Long locationId) {
        EmployeeView e = new EmployeeView();
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
        EmployeeView e = emp(5L, 2L, 1L);
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
        EmployeeView e = emp(1L, null, 10L);
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
        EmployeeView e = emp(1L, null, 10L);
        WorkSchedule loc = sch("LOCATION", 10L, "HOURLY", "[]", null, null);
        loc.setWeeklyHours(new BigDecimal("35"));
        when(repository.findByScopeTypeAndScopeId("LOCATION", 10L)).thenReturn(Optional.of(loc));

        BigDecimal period = service.expectedHoursForPeriod(e, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7));
        // 35 * 7 / 7 = 35
        assertEquals(0, period.compareTo(new BigDecimal("35.0000")));
    }

    @Test
    @DisplayName("FR-HOL-02 — non-optional holiday zeros expected hours (FIXED + HOURLY)")
    void nonOptionalHolidayZerosExpectedHours() {
        EmployeeView e = emp(1L, null, 10L);
        WorkSchedule fixed = sch("LOCATION", 10L, "FIXED",
                "[\"SUN\",\"MON\",\"TUE\",\"WED\",\"THU\"]", "10:00", "18:00");
        when(repository.findByScopeTypeAndScopeId("LOCATION", 10L)).thenReturn(Optional.of(fixed));

        Holiday h = new Holiday();
        h.setLocationId(10L);
        h.setName("Victory Day");
        h.setStartDate(LocalDate.of(2026, 11, 2)); // MON
        h.setEndDate(LocalDate.of(2026, 11, 2));
        h.setHolidayType("PUBLIC");
        h.setOptionalFlag(false);
        when(holidayRepository.findAll()).thenReturn(List.of(h));

        assertEquals(0, service.expectedHoursForDay(e, LocalDate.of(2026, 11, 2)).compareTo(BigDecimal.ZERO));
        assertTrue(service.isHoliday(10L, LocalDate.of(2026, 11, 2)));

        WorkSchedule hourly = sch("LOCATION", 10L, "HOURLY", "[]", null, null);
        hourly.setWeeklyHours(new BigDecimal("35"));
        when(repository.findByScopeTypeAndScopeId("LOCATION", 10L)).thenReturn(Optional.of(hourly));
        assertEquals(0, service.expectedHoursForDay(e, LocalDate.of(2026, 11, 2)).compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("FR-HOL-02 — optional holiday still counts as expected hours")
    void optionalHolidayDoesNotZeroExpectedHours() {
        EmployeeView e = emp(1L, null, 10L);
        WorkSchedule fixed = sch("LOCATION", 10L, "FIXED",
                "[\"SUN\",\"MON\",\"TUE\",\"WED\",\"THU\"]", "10:00", "18:00");
        when(repository.findByScopeTypeAndScopeId("LOCATION", 10L)).thenReturn(Optional.of(fixed));

        Holiday h = new Holiday();
        h.setLocationId(10L);
        h.setName("Optional day");
        h.setStartDate(LocalDate.of(2026, 11, 2));
        h.setEndDate(LocalDate.of(2026, 11, 2));
        h.setHolidayType("COMPANY");
        h.setOptionalFlag(true);
        when(holidayRepository.findAll()).thenReturn(List.of(h));

        assertTrue(!service.isHoliday(10L, LocalDate.of(2026, 11, 2)));
        assertEquals(0, service.expectedHoursForDay(e, LocalDate.of(2026, 11, 2)).compareTo(new BigDecimal("8.0000")));
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
        EmployeeView e = emp(9L, 2L, 1L);
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

    @Test
    void syncFromWorkPatternDoesNotOverwriteExistingEmployeeSchedule() {
        when(configLoader.loadSettingsMap()).thenReturn(Map.of(
                "scheduleRules", Map.of(
                        "patternTemplates", Map.of(
                                "DAYS_5", Map.of(
                                        "mode", "FIXED",
                                        "workingDays", List.of("SUN", "MON", "TUE", "WED", "THU"),
                                        "inheritLocationHours", true
                                )
                        )
                )
        ));
        EmployeeView e = emp(9L, 2L, 1L);
        e.setWorkPattern("DAYS_5");
        WorkSchedule personal = sch("EMPLOYEE", 9L, "FIXED", "[\"SUN\"]", "11:00", "15:00");
        when(repository.findByScopeTypeAndScopeId("EMPLOYEE", 9L)).thenReturn(Optional.of(personal));

        service.syncFromWorkPattern(e);

        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void resolvePrefersTeamOverLocationWhenNoEmployeeRow() {
        EmployeeView e = emp(5L, 2L, 1L);
        when(repository.findByScopeTypeAndScopeId("EMPLOYEE", 5L)).thenReturn(Optional.empty());
        when(repository.findByScopeTypeAndScopeId("TEAM", 2L))
                .thenReturn(Optional.of(sch("TEAM", 2L, "FIXED", "[\"SUN\",\"MON\"]", "09:00", "17:00")));
        when(repository.findByScopeTypeAndScopeId("LOCATION", 1L))
                .thenReturn(Optional.of(sch("LOCATION", 1L, "FIXED", "[\"SUN\"]", "10:00", "18:00")));

        WorkSchedule resolved = service.resolveForEmployee(e).orElseThrow();
        assertEquals("TEAM", resolved.getScopeType());
        assertEquals(2L, resolved.getScopeId());
    }

    @Test
    void createAutoSetsCutoffEndPlusSixHours() {
        when(repository.findByScopeTypeAndScopeId(anyString(), anyLong())).thenReturn(Optional.empty());
        WorkSchedule incoming = sch("LOCATION", 3L, "FIXED", "[\"SUN\",\"MON\"]", "10:00", "18:00");
        ReflectionTestUtils.setField(incoming, "id", null);
        incoming.setCheckoutCutoff(null);

        WorkSchedule saved = service.create(incoming);
        assertEquals("00:00", saved.getCheckoutCutoff());
    }

    @Test
    void createRejectsCutoffBeforeEnd() {
        when(repository.findByScopeTypeAndScopeId(anyString(), anyLong())).thenReturn(Optional.empty());
        WorkSchedule incoming = sch("LOCATION", 3L, "FIXED", "[\"SUN\",\"MON\"]", "10:00", "18:00");
        ReflectionTestUtils.setField(incoming, "id", null);
        incoming.setCheckoutCutoff("17:00");
        assertThrows(IllegalArgumentException.class, () -> service.create(incoming));
    }

    @Test
    void createRejectsHalfDayAtOrAboveOfficeLength() {
        when(repository.findByScopeTypeAndScopeId(anyString(), anyLong())).thenReturn(Optional.empty());
        WorkSchedule incoming = sch("LOCATION", 3L, "FIXED", "[\"SUN\",\"MON\"]", "10:00", "18:00");
        ReflectionTestUtils.setField(incoming, "id", null);
        incoming.setCheckoutCutoff("00:00");
        incoming.setHalfDayHours(new BigDecimal("8"));
        assertThrows(IllegalArgumentException.class, () -> service.create(incoming));
    }
}
