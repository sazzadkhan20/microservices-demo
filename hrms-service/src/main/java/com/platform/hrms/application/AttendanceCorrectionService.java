package com.platform.hrms.application;

import com.platform.common.security.AuthContext;
import com.platform.hrms.domain.AttendanceCorrection;
import com.platform.hrms.infrastructure.persistence.AttendanceCorrectionRepository;
import com.platform.hrms.infrastructure.persistence.AttendanceRecordRepository;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Attendance correction workflow: employee requests, HR / manager (attendance:correct) approves or rejects.
 * Approval delegates to {@link AttendanceService#correct}.
 */
@Service
public class AttendanceCorrectionService {

    private final AttendanceCorrectionRepository repository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceService attendanceService;
    private final AuditService auditService;
    private final DataScopeService dataScopeService;

    public AttendanceCorrectionService(
            AttendanceCorrectionRepository repository,
            AttendanceRecordRepository attendanceRecordRepository,
            EmployeeRepository employeeRepository,
            AttendanceService attendanceService,
            AuditService auditService,
            DataScopeService dataScopeService) {
        this.repository = repository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceService = attendanceService;
        this.auditService = auditService;
        this.dataScopeService = dataScopeService;
    }

    @Transactional
    public Map<String, Object> request(LocalDate workDate, Instant checkIn, Instant checkOut, String reason) {
        Long actorId = AuthContext.requireEmployeeId();
        if (workDate == null) {
            throw new IllegalArgumentException("workDate is required");
        }
        if (workDate.isAfter(LocalDate.now().plusDays(1))) {
            throw new IllegalArgumentException("workDate cannot be in the future");
        }
        if (checkIn == null && checkOut == null) {
            throw new IllegalArgumentException("requestedCheckIn or requestedCheckOut is required");
        }
        if (checkIn != null && checkOut != null && !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("requestedCheckOut must be after requestedCheckIn (overnight OK)");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        employeeRepository.findById(actorId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + actorId));
        if (repository.existsByEmployeeIdAndWorkDateAndStatus(actorId, workDate, AttendanceCorrection.STATUS_PENDING)) {
            throw new IllegalStateException("A pending correction already exists for " + workDate);
        }
        Instant now = Instant.now();
        AttendanceCorrection c = new AttendanceCorrection();
        c.setEmployeeId(actorId);
        c.setWorkDate(workDate);
        c.setRequestedCheckIn(checkIn);
        c.setRequestedCheckOut(checkOut);
        // FR-ATT-07 — keep the original punches on the correction row (record is overwritten on approve).
        attendanceRecordRepository.findByEmployeeIdAndWorkDate(actorId, workDate).ifPresent(rec -> {
            c.setOriginalCheckIn(rec.getCheckInAt());
            c.setOriginalCheckOut(rec.getCheckOutAt());
            c.setOriginalStatusCode(rec.getStatusCode());
        });
        c.setReason(trim(reason.trim(), 512));
        c.setStatus(AttendanceCorrection.STATUS_PENDING);
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        AttendanceCorrection saved = repository.save(c);
        auditService.record(actorId, "ATTENDANCE_CORRECTION_REQUEST", "attendance_correction",
                String.valueOf(saved.getId()), null, toView(saved));
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String status) {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                        || status.equalsIgnoreCase(c.getStatus()))
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine() {
        Long actorId = AuthContext.requireEmployeeId();
        return repository.findByEmployeeIdOrderByCreatedAtDesc(actorId).stream().map(this::toView).toList();
    }

    @Transactional
    public Map<String, Object> approve(Long id, String note) {
        Long reviewerId = AuthContext.requireEmployeeId();
        AttendanceCorrection c = requirePending(id);
        if (reviewerId.equals(c.getEmployeeId())) {
            throw new IllegalStateException("Not allowed: cannot review your own correction request");
        }
        dataScopeService.requireAccess(reviewerId, c.getEmployeeId(), "attendance", "correct");
        Map<String, Object> before = toView(c);
        String attNote = "Correction #" + c.getId() + ": " + c.getReason();
        attendanceService.correct(c.getEmployeeId(), c.getWorkDate(),
                c.getRequestedCheckIn(), c.getRequestedCheckOut(), null, null, attNote);
        finish(c, AttendanceCorrection.STATUS_APPROVED, reviewerId, note);
        auditService.record(reviewerId, "ATTENDANCE_CORRECTION_APPROVE", "attendance_correction",
                String.valueOf(c.getId()), before, toView(c));
        return toView(c);
    }

    @Transactional
    public Map<String, Object> reject(Long id, String note) {
        Long reviewerId = AuthContext.requireEmployeeId();
        AttendanceCorrection c = requirePending(id);
        if (reviewerId.equals(c.getEmployeeId())) {
            throw new IllegalStateException("Not allowed: cannot review your own correction request");
        }
        dataScopeService.requireAccess(reviewerId, c.getEmployeeId(), "attendance", "correct");
        Map<String, Object> before = toView(c);
        finish(c, AttendanceCorrection.STATUS_REJECTED, reviewerId, note);
        auditService.record(reviewerId, "ATTENDANCE_CORRECTION_REJECT", "attendance_correction",
                String.valueOf(c.getId()), before, toView(c));
        return toView(c);
    }

    private void finish(AttendanceCorrection c, String status, Long reviewerId, String note) {
        c.setStatus(status);
        c.setReviewerId(reviewerId);
        c.setReviewNote(note == null || note.isBlank() ? null : trim(note.trim(), 512));
        c.setUpdatedAt(Instant.now());
        repository.save(c);
    }

    private AttendanceCorrection requirePending(Long id) {
        AttendanceCorrection c = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Attendance correction not found: " + id));
        if (!AttendanceCorrection.STATUS_PENDING.equals(c.getStatus())) {
            throw new IllegalStateException("Correction already " + c.getStatus());
        }
        return c;
    }

    private Map<String, Object> toView(AttendanceCorrection c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("employeeId", c.getEmployeeId());
        m.put("employeeName", employeeRepository.findById(c.getEmployeeId())
                .map(e -> e.getFullName()).orElse(null));
        m.put("workDate", c.getWorkDate().toString());
        m.put("requestedCheckIn", c.getRequestedCheckIn() == null ? null : c.getRequestedCheckIn().toString());
        m.put("requestedCheckOut", c.getRequestedCheckOut() == null ? null : c.getRequestedCheckOut().toString());
        m.put("originalCheckIn", c.getOriginalCheckIn() == null ? null : c.getOriginalCheckIn().toString());
        m.put("originalCheckOut", c.getOriginalCheckOut() == null ? null : c.getOriginalCheckOut().toString());
        m.put("originalStatusCode", c.getOriginalStatusCode());
        m.put("reason", c.getReason());
        m.put("status", c.getStatus());
        m.put("reviewerId", c.getReviewerId());
        m.put("reviewNote", c.getReviewNote());
        m.put("createdAt", c.getCreatedAt() == null ? null : c.getCreatedAt().toString());
        m.put("updatedAt", c.getUpdatedAt() == null ? null : c.getUpdatedAt().toString());
        return m;
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
