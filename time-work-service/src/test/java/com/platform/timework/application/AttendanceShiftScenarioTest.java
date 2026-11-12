package com.platform.timework.application;

import com.platform.common.security.AuthContext;
import com.platform.timework.client.LeaveClient;
import com.platform.timework.client.people.EmployeeView;
import com.platform.timework.client.people.PeopleAuthzClient;
import com.platform.timework.client.people.PeopleEmployeeGateway;
import com.platform.timework.domain.AttendanceRecord;
import com.platform.timework.domain.TenantSettings;
import com.platform.timework.domain.WorkSchedule;
import com.platform.timework.infrastructure.persistence.AttendanceCorrectionRepository;
import com.platform.timework.infrastructure.persistence.AttendanceRecordRepository;
import com.platform.timework.infrastructure.persistence.TenantSettingsRepository;
import com.platform.timework.infrastructure.persistence.WorkLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Seed-like scenarios: Dhaka day shift + overnight 21:00–05:00 with cut-off 11:00.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Attendance shift scenarios — day + overnight")
class AttendanceShiftScenarioTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final Long EMP = 16L;

    @Mock AttendanceRecordRepository repository;
    @Mock AttendanceCorrectionRepository correctionRepository;
    @Mock PeopleEmployeeGateway employeeRepository;
    @Mock WorkScheduleService workScheduleService;
    @Mock WorkLogRepository workLogRepository;
    @Mock WorkLogService workLogService;
    @Mock LeaveClient leaveClient;
    @Mock TenantSettingsRepository tenantSettingsRepository;
    @Mock AuditService auditService;
    @Mock PeopleAuthzClient rbacService;
    @Mock DataScopeService dataScopeService;

    final AtomicReference<Instant> instant = new AtomicReference<>();
    Clock clock;
    AttendanceService service;
    EmployeeView emp;
    WorkSchedule daySchedule;
    WorkSchedule overnight;
    HttpServletRequest request;

    @BeforeEach
    void setUp() {
        instant.set(Instant.parse("2026-11-11T04:00:00Z")); // 10:00 Dhaka
        clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) {
                return Clock.fixed(instant.get(), zone);
            }
            @Override public Instant instant() { return instant.get(); }
        };
        TimeWorkNotificationHook notificationHook = org.mockito.Mockito.mock(TimeWorkNotificationHook.class);
        service = new AttendanceService(
                repository, correctionRepository, employeeRepository, workScheduleService,
                workLogRepository, workLogService, leaveClient, tenantSettingsRepository,
                auditService, rbacService, dataScopeService, notificationHook, clock);

        emp = new EmployeeView();
        ReflectionTestUtils.setField(emp, "id", EMP);
        emp.setEmployeeCode("T-16");
        emp.setFullName("With Manager Test");
        emp.setLocationId(1L);
        emp.setLocationTimeZone("Asia/Dhaka");
        emp.setActive(true);

        daySchedule = fixed("10:00", "18:00", 15, "23:59");
        overnight = fixed("21:00", "05:00", 15, "11:00");

        when(employeeRepository.findById(EMP)).thenReturn(Optional.of(emp));
        TenantSettings settings = new TenantSettings();
        settings.setSettings(Map.of("workLogRule", "OFF"));
        when(tenantSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(leaveClient.leaveImpact(anyLong(), any())).thenReturn(LeaveClient.LeaveImpact.NONE);
        when(workScheduleService.isHoliday(any(), any())).thenReturn(false);
        when(workScheduleService.isWorkingDay(any(), any())).thenReturn(true);
        when(workScheduleService.expectedHoursForDay(any(), any())).thenReturn(new BigDecimal("8"));
        when(workScheduleService.expectedHoursForPeriod(any(), any(), any())).thenReturn(new BigDecimal("40"));
        when(workLogRepository.findByEmployeeIdAndWorkDate(anyLong(), any())).thenReturn(List.of());
        when(workLogRepository.findByEmployeeIdAndWorkDateBetween(anyLong(), any(), any())).thenReturn(List.of());
        // Default: no punches for any date (early-open scan looks at yesterday/today/tomorrow).
        lenient().when(repository.findByEmployeeIdAndWorkDate(eq(EMP), any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> {
            AttendanceRecord r = inv.getArgument(0);
            if (r.getId() == null) ReflectionTestUtils.setField(r, "id", 900L);
            return r;
        });

        request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");

        AuthContext.setEmployeeId(EMP);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private void setDhaka(LocalDateTime ldt) {
        instant.set(ldt.atZone(DHAKA).toInstant());
    }

    private static WorkSchedule fixed(String start, String end, int grace, String cutoff) {
        WorkSchedule s = new WorkSchedule();
        s.setMode("FIXED");
        s.setStartTime(start);
        s.setEndTime(end);
        s.setGraceMinutes(grace);
        s.setCheckoutCutoff(cutoff);
        s.setHalfDayHours(new BigDecimal("4"));
        s.setWorkingDays("[\"SUN\",\"MON\",\"TUE\",\"WED\",\"THU\",\"FRI\",\"SAT\"]");
        return s;
    }

    private void useSchedule(WorkSchedule s) {
        when(workScheduleService.resolveForEmployee(emp)).thenReturn(Optional.of(s));
    }

    private void noExisting(LocalDate... dates) {
        for (LocalDate d : dates) {
            when(repository.findByEmployeeIdAndWorkDate(EMP, d)).thenReturn(Optional.empty());
        }
    }

    @Nested
    @DisplayName("Day shift 10:00–18:00")
    class DayShift {
        @BeforeEach
        void sched() {
            useSchedule(daySchedule);
        }

        @Test
        void beforeEarlyOpen_rejected() {
            // 10:00 start → early open 09:00; 08:50 is still too early
            setDhaka(LocalDateTime.of(2026, 11, 11, 8, 50));
            noExisting(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12));
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.checkIn(request, null, null, null, null, null, null));
            assertTrue(ex.getMessage().toLowerCase().contains("opens")
                    || ex.getMessage().toLowerCase().contains("outside"));
        }

        @Test
        void earlyWithin60Minutes_present() {
            setDhaka(LocalDateTime.of(2026, 11, 11, 9, 15));
            noExisting(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12));
            Map<String, Object> view = service.checkIn(request, null, null, null, null, null, null);
            ArgumentCaptor<AttendanceRecord> cap = ArgumentCaptor.forClass(AttendanceRecord.class);
            verify(repository).save(cap.capture());
            assertEquals(LocalDate.of(2026, 11, 11), cap.getValue().getWorkDate());
            assertEquals("P", cap.getValue().getStatusCode());
            assertEquals("2026-11-11", view.get("attributedDate"));
        }

        @Test
        void onTime_present() {
            setDhaka(LocalDateTime.of(2026, 11, 11, 10, 10));
            noExisting(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12));
            Map<String, Object> view = service.checkIn(request, null, null, null, null, null, null);
            ArgumentCaptor<AttendanceRecord> cap = ArgumentCaptor.forClass(AttendanceRecord.class);
            verify(repository).save(cap.capture());
            assertEquals(LocalDate.of(2026, 11, 11), cap.getValue().getWorkDate());
            assertEquals("P", cap.getValue().getStatusCode());
            assertEquals("2026-11-11", view.get("attributedDate"));
        }

        @Test
        void afterGrace_late() {
            setDhaka(LocalDateTime.of(2026, 11, 11, 10, 20));
            noExisting(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12));
            service.checkIn(request, null, null, null, null, null, null);
            ArgumentCaptor<AttendanceRecord> cap = ArgumentCaptor.forClass(AttendanceRecord.class);
            verify(repository).save(cap.capture());
            assertEquals("L", cap.getValue().getStatusCode());
        }

        @Test
        void checkout_sameWorkDate() {
            setDhaka(LocalDateTime.of(2026, 11, 11, 10, 10));
            AttendanceRecord open = openRec(LocalDate.of(2026, 11, 11),
                    LocalDateTime.of(2026, 11, 11, 10, 10).atZone(DHAKA).toInstant());
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 10)))
                    .thenReturn(Optional.empty());
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 11)))
                    .thenReturn(Optional.of(open));

            setDhaka(LocalDateTime.of(2026, 11, 11, 17, 0));
            Map<String, Object> view = service.checkOut(request, null, null, null, null, null, null);
            assertEquals(LocalDate.of(2026, 11, 11), open.getWorkDate());
            assertEquals("2026-11-11", view.get("attributedDate"));
            assertFalse(Boolean.TRUE.equals(view.get("lateNightCheckout")));
        }
    }

    @Nested
    @DisplayName("Non-working day punch — never Half Day")
    class NonWorkingPunch {
        @BeforeEach
        void sched() {
            useSchedule(daySchedule);
        }

        @Test
        void weeklyOff_checkInAndOut_staysWorkedNotHalfDay() {
            when(workScheduleService.isWorkingDay(any(), any())).thenReturn(false);
            setDhaka(LocalDateTime.of(2026, 11, 14, 10, 10)); // Saturday-like
            noExisting(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 14));
            service.checkIn(request, null, null, null, null, null, null);
            ArgumentCaptor<AttendanceRecord> cap = ArgumentCaptor.forClass(AttendanceRecord.class);
            verify(repository).save(cap.capture());
            AttendanceRecord open = cap.getValue();
            assertEquals("W", open.getStatusCode());
            assertTrue(open.getStatusLabel().toLowerCase().contains("weekly"));

            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 13)))
                    .thenReturn(Optional.empty());
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 14)))
                    .thenReturn(Optional.of(open));
            setDhaka(LocalDateTime.of(2026, 11, 14, 10, 20)); // short stay
            service.checkOut(request, null, null, null, null, null, null);
            assertEquals("W", open.getStatusCode());
            assertTrue(open.getStatusLabel().toLowerCase().contains("weekly"));
        }

        @Test
        void holiday_checkInAndOut_staysWorkedNotHalfDay() {
            when(workScheduleService.isHoliday(eq(1L), any())).thenReturn(true);
            setDhaka(LocalDateTime.of(2026, 11, 11, 10, 10));
            noExisting(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11));
            service.checkIn(request, null, null, null, null, null, null);
            ArgumentCaptor<AttendanceRecord> cap = ArgumentCaptor.forClass(AttendanceRecord.class);
            verify(repository).save(cap.capture());
            AttendanceRecord open = cap.getValue();
            assertEquals("W", open.getStatusCode());

            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 10)))
                    .thenReturn(Optional.empty());
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 11)))
                    .thenReturn(Optional.of(open));
            setDhaka(LocalDateTime.of(2026, 11, 11, 10, 15));
            service.checkOut(request, null, null, null, null, null, null);
            assertEquals("W", open.getStatusCode());
            assertTrue(open.getStatusLabel().toLowerCase().contains("holiday"));
        }

        @Test
        void onLeave_checkInAndOut_staysWorkedNotHalfDay() {
            when(leaveClient.leaveImpact(eq(EMP), any())).thenReturn(LeaveClient.LeaveImpact.FULL);
            setDhaka(LocalDateTime.of(2026, 11, 11, 10, 10));
            noExisting(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11));
            service.checkIn(request, null, null, null, null, null, null);
            ArgumentCaptor<AttendanceRecord> cap = ArgumentCaptor.forClass(AttendanceRecord.class);
            verify(repository).save(cap.capture());
            AttendanceRecord open = cap.getValue();
            assertEquals("W", open.getStatusCode());

            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 10)))
                    .thenReturn(Optional.empty());
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 11)))
                    .thenReturn(Optional.of(open));
            setDhaka(LocalDateTime.of(2026, 11, 11, 10, 12));
            service.checkOut(request, null, null, null, null, null, null);
            assertEquals("W", open.getStatusCode());
            assertTrue(open.getStatusLabel().toLowerCase().contains("leave"));
        }

        @Test
        void priority_weeklyOffBeatsHolidayAndLeave() {
            when(workScheduleService.isWorkingDay(any(), any())).thenReturn(false);
            when(workScheduleService.isHoliday(eq(1L), any())).thenReturn(true);
            when(leaveClient.leaveImpact(eq(EMP), any())).thenReturn(LeaveClient.LeaveImpact.FULL);
            assertEquals(AttendanceService.NonWorkingDayKind.WEEKLY_OFF,
                    service.classifyNonWorkingDay(emp, LocalDate.of(2026, 11, 14)));
        }

        @Test
        void overnightHolidayShift_checkoutKeepsWorked() {
            useSchedule(overnight);
            when(workScheduleService.isHoliday(eq(1L), eq(LocalDate.of(2026, 11, 11)))).thenReturn(true);
            setDhaka(LocalDateTime.of(2026, 11, 11, 22, 0));
            noExisting(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11));
            service.checkIn(request, null, null, null, null, null, null);
            ArgumentCaptor<AttendanceRecord> cap = ArgumentCaptor.forClass(AttendanceRecord.class);
            verify(repository).save(cap.capture());
            AttendanceRecord open = cap.getValue();
            assertEquals(LocalDate.of(2026, 11, 11), open.getWorkDate());
            assertEquals("W", open.getStatusCode());

            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 11)))
                    .thenReturn(Optional.of(open));
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 12)))
                    .thenReturn(Optional.empty());
            setDhaka(LocalDateTime.of(2026, 11, 12, 5, 0));
            service.checkOut(request, null, null, null, null, null, null);
            assertEquals("W", open.getStatusCode());
            assertEquals(LocalDate.of(2026, 11, 11), open.getWorkDate());
        }
    }

    @Nested
    @DisplayName("Overnight 21:00–05:00 cut-off 11:00")
    class Overnight {
        @BeforeEach
        void sched() {
            useSchedule(overnight);
        }

        @Test
        void checkInAt22_onShiftStartDate_late() {
            setDhaka(LocalDateTime.of(2026, 11, 11, 22, 0));
            noExisting(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11));
            Map<String, Object> view = service.checkIn(request, null, null, null, null, null, null);
            ArgumentCaptor<AttendanceRecord> cap = ArgumentCaptor.forClass(AttendanceRecord.class);
            verify(repository).save(cap.capture());
            assertEquals(LocalDate.of(2026, 11, 11), cap.getValue().getWorkDate());
            assertEquals("L", cap.getValue().getStatusCode());
            assertEquals("2026-11-11", view.get("attributedDate"));
        }

        @Test
        void checkInAt06NextMorning_stillPreviousShiftDate_late() {
            setDhaka(LocalDateTime.of(2026, 11, 12, 6, 0));
            noExisting(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12));
            Map<String, Object> view = service.checkIn(request, null, null, null, null, null, null);
            ArgumentCaptor<AttendanceRecord> cap = ArgumentCaptor.forClass(AttendanceRecord.class);
            verify(repository).save(cap.capture());
            assertEquals(LocalDate.of(2026, 11, 11), cap.getValue().getWorkDate());
            assertEquals("L", cap.getValue().getStatusCode());
            assertEquals("2026-11-11", view.get("attributedDate"));
        }

        @Test
        void checkoutAt05NextDay_keepsShiftStartWorkDate() {
            AttendanceRecord open = openRec(LocalDate.of(2026, 11, 11),
                    LocalDateTime.of(2026, 11, 11, 22, 0).atZone(DHAKA).toInstant());
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 11)))
                    .thenReturn(Optional.of(open));
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 12)))
                    .thenReturn(Optional.empty());

            setDhaka(LocalDateTime.of(2026, 11, 12, 5, 0));
            Map<String, Object> view = service.checkOut(request, null, null, null, null, null, null);
            assertEquals(LocalDate.of(2026, 11, 11), open.getWorkDate());
            assertEquals("2026-11-11", view.get("attributedDate"));
            assertTrue(Boolean.TRUE.equals(view.get("lateNightCheckout")));
        }

        @Test
        void openPunch_blocksNewCheckIn() {
            AttendanceRecord open = openRec(LocalDate.of(2026, 11, 11),
                    LocalDateTime.of(2026, 11, 11, 22, 0).atZone(DHAKA).toInstant());
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 11)))
                    .thenReturn(Optional.of(open));
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 12)))
                    .thenReturn(Optional.empty());
            setDhaka(LocalDateTime.of(2026, 11, 12, 6, 0));
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.checkIn(request, null, null, null, null, null, null));
            assertTrue(ex.getMessage().contains("Already checked in"));
        }

        @Test
        void afterCutoff_marksMissing_thenHolidayDayWindow() {
            AttendanceRecord open = openRec(LocalDate.of(2026, 11, 11),
                    LocalDateTime.of(2026, 11, 11, 22, 0).atZone(DHAKA).toInstant());
            open.setStatusCode("P");
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 11)))
                    .thenReturn(Optional.of(open));
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 12)))
                    .thenReturn(Optional.empty());

            // Past 11:00 cut-off on 12/11 — prior shift Missing; day shift not configured here
            // so with overnight schedule only, 12/11 window starts 21:00.
            setDhaka(LocalDateTime.of(2026, 11, 12, 12, 0));
            when(workScheduleService.isHoliday(eq(1L), eq(LocalDate.of(2026, 11, 12)))).thenReturn(true);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.checkOut(request, null, null, null, null, null, null));
            assertTrue(ex.getMessage().toLowerCase().contains("cut-off"));
            assertEquals("M", open.getStatusCode());

            // Before next overnight early-open (20:00) on holiday calendar day
            IllegalStateException before = assertThrows(IllegalStateException.class,
                    () -> service.checkIn(request, null, null, null, null, null, null));
            String msg = before.getMessage().toLowerCase();
            assertTrue(msg.contains("opens") || msg.contains("outside") || msg.contains("not started"), msg);
        }

        @Test
        void today_showsPriorShiftCheckout() {
            AttendanceRecord open = openRec(LocalDate.of(2026, 11, 11),
                    LocalDateTime.of(2026, 11, 11, 22, 0).atZone(DHAKA).toInstant());
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 11)))
                    .thenReturn(Optional.of(open));
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 12)))
                    .thenReturn(Optional.empty());
            setDhaka(LocalDateTime.of(2026, 11, 12, 5, 30));

            Map<String, Object> today = service.today();
            assertEquals(true, today.get("canCheckOut"));
            assertEquals(false, today.get("canCheckIn"));
            assertEquals(true, today.get("lateNightCheckoutAvailable"));
            assertEquals("2026-11-11", today.get("activeWorkDate"));
        }
    }

    @Nested
    @DisplayName("Short overnight 00:00–03:00 cut-off 03:50")
    class ShortOvernightDarwin {
        WorkSchedule shortOvernight;

        @BeforeEach
        void sched() {
            shortOvernight = fixed("00:00", "03:00", 15, "03:50");
            useSchedule(shortOvernight);
        }

        @Test
        void afterCutoff_todayHidesCheckoutAndCheckIn() {
            AttendanceRecord open = openRec(LocalDate.of(2026, 11, 12),
                    LocalDateTime.of(2026, 11, 12, 0, 10).atZone(DHAKA).toInstant());
            open.setStatusCode("P");
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 11)))
                    .thenReturn(Optional.empty());
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 12)))
                    .thenReturn(Optional.of(open));
            when(repository.findByEmployeeIdAndWorkDate(EMP, LocalDate.of(2026, 11, 13)))
                    .thenReturn(Optional.empty());

            setDhaka(LocalDateTime.of(2026, 11, 12, 4, 0));
            Map<String, Object> today = service.today();
            assertEquals("M", open.getStatusCode());
            assertEquals(false, today.get("canCheckOut"));
            assertEquals(false, today.get("canCheckIn"));
            assertEquals(false, today.get("lateNightCheckoutAvailable"));
        }

        @Test
        void earlyOpen_60MinBeforeMidnight() {
            noExisting(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13));
            setDhaka(LocalDateTime.of(2026, 11, 11, 23, 0));
            Map<String, Object> today = service.today();
            assertEquals(true, today.get("canCheckIn"));
            assertEquals(false, today.get("canCheckOut"));
            assertEquals("2026-11-12", today.get("activeWorkDate"));
        }

        @Test
        void tooEarly_before60MinWindow_noCheckIn() {
            noExisting(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13));
            setDhaka(LocalDateTime.of(2026, 11, 11, 22, 50));
            Map<String, Object> today = service.today();
            assertEquals(false, today.get("canCheckIn"));
            assertEquals(false, today.get("canCheckOut"));
        }
    }

    private AttendanceRecord openRec(LocalDate workDate, Instant checkIn) {
        AttendanceRecord r = new AttendanceRecord();
        ReflectionTestUtils.setField(r, "id", 501L);
        r.setEmployeeId(EMP);
        r.setWorkDate(workDate);
        r.setCheckInAt(checkIn);
        r.setStatusCode("P");
        r.setStatusLabel("Present");
        r.setTimeZone("Asia/Dhaka");
        r.setCreatedAt(checkIn);
        r.setUpdatedAt(checkIn);
        return r;
    }
}
