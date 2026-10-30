package com.platform.hrms.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Employee-initiated request to fix a missed / wrong check-in or check-out; approved by HR/manager. */
@Entity
@Table(name = "attendance_correction")
public class AttendanceCorrection {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "requested_check_in")
    private Instant requestedCheckIn;

    @Column(name = "requested_check_out")
    private Instant requestedCheckOut;

    /** FR-ATT-07 — original punches kept when a correction is requested. */
    @Column(name = "original_check_in")
    private Instant originalCheckIn;

    @Column(name = "original_check_out")
    private Instant originalCheckOut;

    @Column(name = "original_status_code", length = 8)
    private String originalStatusCode;

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_PENDING;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "review_note", length = 512)
    private String reviewNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }

    public Instant getRequestedCheckIn() { return requestedCheckIn; }
    public void setRequestedCheckIn(Instant requestedCheckIn) { this.requestedCheckIn = requestedCheckIn; }

    public Instant getRequestedCheckOut() { return requestedCheckOut; }
    public void setRequestedCheckOut(Instant requestedCheckOut) { this.requestedCheckOut = requestedCheckOut; }

    public Instant getOriginalCheckIn() { return originalCheckIn; }
    public void setOriginalCheckIn(Instant originalCheckIn) { this.originalCheckIn = originalCheckIn; }

    public Instant getOriginalCheckOut() { return originalCheckOut; }
    public void setOriginalCheckOut(Instant originalCheckOut) { this.originalCheckOut = originalCheckOut; }

    public String getOriginalStatusCode() { return originalStatusCode; }
    public void setOriginalStatusCode(String originalStatusCode) { this.originalStatusCode = originalStatusCode; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
