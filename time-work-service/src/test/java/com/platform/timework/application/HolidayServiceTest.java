package com.platform.timework.application;

import com.platform.timework.client.people.EmployeeView;
import com.platform.timework.client.people.PeopleEmployeeGateway;
import com.platform.timework.domain.Holiday;
import com.platform.timework.infrastructure.persistence.HolidayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    /** Fixed "today" = 2026-11-12 in Dhaka. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T06:00:00Z"), DHAKA);

    @Mock HolidayRepository holidayRepository;
    @Mock AuditService auditService;
    @Mock PeopleEmployeeGateway people;
    @Mock TimeWorkNotificationHook notificationHook;

    HolidayService service;

    @BeforeEach
    void setUp() {
        service = new HolidayService(holidayRepository, auditService, people, notificationHook, CLOCK);
    }

    private Holiday holiday(long id, long locationId, LocalDate start, LocalDate end) {
        Holiday h = new Holiday();
        h.setLocationId(locationId);
        // id via reflection-free path: save returns same instance; set through repo mock
        try {
            var f = Holiday.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(h, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        h.setName("Company break");
        h.setStartDate(start);
        h.setEndDate(end);
        h.setHolidayType("COMPANY");
        return h;
    }

    private EmployeeView emp(long id, long locationId, String tz) {
        EmployeeView e = new EmployeeView();
        e.setId(id);
        e.setLocationId(locationId);
        e.setLocationTimeZone(tz);
        e.setActive(true);
        e.setFullName("Emp " + id);
        return e;
    }

    @Test
    @DisplayName("cannot delete past or ongoing holiday")
    void delete_rejectsStarted() {
        Holiday past = holiday(1, 1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5));
        when(holidayRepository.findById(1L)).thenReturn(Optional.of(past));
        when(people.findAll()).thenReturn(List.of(emp(10, 1L, "Asia/Dhaka")));

        assertThrows(IllegalArgumentException.class, () -> service.delete(1L));
        verify(holidayRepository, never()).delete(any());

        Holiday ongoing = holiday(2, 1L, LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 15));
        when(holidayRepository.findById(2L)).thenReturn(Optional.of(ongoing));
        assertThrows(IllegalArgumentException.class, () -> service.delete(2L));
    }

    @Test
    @DisplayName("future holiday can be deleted")
    void delete_allowsFuture() {
        Holiday future = holiday(3, 1L, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2));
        when(holidayRepository.findById(3L)).thenReturn(Optional.of(future));
        when(people.findAll()).thenReturn(List.of(emp(10, 1L, "Asia/Dhaka")));

        service.delete(3L);
        verify(holidayRepository).delete(future);
    }

    @Test
    @DisplayName("ongoing: can shorten end to today, not before today")
    void update_ongoingShortenRules() {
        Holiday ongoing = holiday(4, 1L, LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 15));
        when(holidayRepository.findById(4L)).thenReturn(Optional.of(ongoing));
        when(holidayRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(people.findAll()).thenReturn(List.of(
                emp(10, 1L, "Asia/Dhaka"),
                emp(20, 3L, "Australia/Sydney")));

        // Reject end before today (11 only)
        assertThrows(IllegalArgumentException.class, () -> service.update(4L, Map.of(
                "startDate", "2026-11-11",
                "endDate", "2026-11-11")));

        // Allow end = today
        Holiday saved = service.update(4L, Map.of(
                "startDate", "2026-11-11",
                "endDate", "2026-11-12"));
        assertEquals(LocalDate.of(2026, 11, 12), saved.getEndDate());

        ArgumentCaptor<List<EmployeeView>> cap = ArgumentCaptor.forClass(List.class);
        verify(notificationHook).notifyHolidayShortened(
                eq(1L),
                cap.capture(),
                eq(4L),
                eq("Company break"),
                eq(LocalDate.of(2026, 11, 15)),
                eq(LocalDate.of(2026, 11, 12)));
        assertEquals(1, cap.getValue().size());
        assertEquals(10L, cap.getValue().get(0).getId());
    }

    @Test
    @DisplayName("past holiday dates are locked")
    void update_pastDatesLocked() {
        Holiday past = holiday(5, 1L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
        when(holidayRepository.findById(5L)).thenReturn(Optional.of(past));
        when(people.findAll()).thenReturn(List.of(emp(10, 1L, "Asia/Dhaka")));

        assertThrows(IllegalArgumentException.class, () -> service.update(5L, Map.of(
                "startDate", "2026-10-01",
                "endDate", "2026-10-02")));
        verify(notificationHook, never()).notifyHolidayShortened(
                anyLong(), anyList(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("static helpers for phase / delete")
    void helpers() {
        LocalDate today = LocalDate.of(2026, 11, 12);
        assertFalse(HolidayService.canDelete(LocalDate.of(2026, 11, 11), today));
        assertFalse(HolidayService.canDelete(LocalDate.of(2026, 11, 12), today));
        assertTrue(HolidayService.canDelete(LocalDate.of(2026, 11, 13), today));
        assertTrue(HolidayService.isOngoing(
                LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 15), today));
    }
}
