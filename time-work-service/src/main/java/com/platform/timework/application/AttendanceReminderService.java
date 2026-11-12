package com.platform.timework.application;

import com.platform.timework.client.people.EmployeeView;
import com.platform.timework.client.people.PeopleEmployeeGateway;
import com.platform.timework.domain.AttendanceRecord;
import com.platform.timework.domain.WorkSchedule;
import com.platform.timework.infrastructure.persistence.AttendanceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-NT — no check-in by start+1h, and missing check-out at cut-off (with notify).
 */
@Service
public class AttendanceReminderService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceReminderService.class);
    private static final long NO_CHECKIN_GRACE_SECONDS = 3600L;

    private final PeopleEmployeeGateway employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceService attendanceService;
    private final WorkScheduleService workScheduleService;
    private final TimeWorkNotificationHook notificationHook;

    public AttendanceReminderService(
            PeopleEmployeeGateway employeeRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            AttendanceService attendanceService,
            WorkScheduleService workScheduleService,
            TimeWorkNotificationHook notificationHook) {
        this.employeeRepository = employeeRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceService = attendanceService;
        this.workScheduleService = workScheduleService;
        this.notificationHook = notificationHook;
    }

    /**
     * Sweep: mark past-cutoff opens as Missing (notifies employee), then nag no-check-in past start+1h.
     */
    @Transactional
    public Map<String, Object> runReminders() {
        Map<String, Object> missingOut = attendanceService.closeMissingCheckouts(null);
        int noCheckin = notifyNoCheckins();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("missingCheckout", missingOut);
        out.put("noCheckinNotified", noCheckin);
        return out;
    }

    @Transactional(readOnly = true)
    public int notifyNoCheckins() {
        Instant now = Instant.now();
        int sent = 0;
        List<EmployeeView> employees = employeeRepository.findAll();
        for (EmployeeView emp : employees) {
            if (emp == null || emp.getId() == null || !emp.isActive()) {
                continue;
            }
            try {
                if (notifyNoCheckinIfDue(emp, now)) {
                    sent++;
                }
            } catch (Exception e) {
                log.warn("No-checkin notify failed for emp={}: {}", emp.getId(), e.getMessage());
            }
        }
        return sent;
    }

    private boolean notifyNoCheckinIfDue(EmployeeView emp, Instant now) {
        ZoneId zone;
        try {
            zone = (emp.getLocationTimeZone() != null && !emp.getLocationTimeZone().isBlank())
                    ? ZoneId.of(emp.getLocationTimeZone().trim())
                    : ZoneId.of("Asia/Dhaka");
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Dhaka");
        }
        LocalDate today = LocalDate.now(zone);
        if (attendanceService.classifyNonWorkingDay(emp, today) != AttendanceService.NonWorkingDayKind.NONE) {
            return false;
        }
        WorkSchedule schedule = workScheduleService.resolveForEmployee(emp).orElse(null);
        ShiftWindow window = ShiftWindow.forSchedule(today, schedule, zone, 15, "23:59");
        if (window == null || !window.fixedHours()) {
            return false; // hourly / open window — no fixed start+1h rule
        }
        Instant dueAt = window.startAt().plusSeconds(NO_CHECKIN_GRACE_SECONDS);
        if (now.isBefore(dueAt)) {
            return false;
        }
        AttendanceRecord rec = attendanceRecordRepository
                .findByEmployeeIdAndWorkDate(emp.getId(), today)
                .orElse(null);
        if (rec != null && rec.getCheckInAt() != null) {
            return false;
        }
        return notificationHook.notifyNoCheckin(emp.getId(), today.toString());
    }
}
