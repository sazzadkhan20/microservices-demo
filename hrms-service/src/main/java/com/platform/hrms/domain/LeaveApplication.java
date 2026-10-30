package com.platform.hrms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "leave_application")
public class LeaveApplication {

    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_PENDING_RELIEF = "PENDING_RELIEF";
    public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "leave_type_code", length = 32)
    private String leaveTypeCode;

    @Column(nullable = false)
    private boolean paid = true;

    @Column(name = "balance_deducted", nullable = false)
    private boolean balanceDeducted = false;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "working_days_counted", nullable = false, precision = 10, scale = 2)
    private BigDecimal workingDaysCounted;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 512)
    private String reason;

    @Column(name = "union_member", nullable = false)
    private boolean unionMember;

    @Column(name = "notice_days_given", nullable = false)
    private int noticeDaysGiven;

    @Column(name = "notice_days_required", nullable = false)
    private int noticeDaysRequired;

    @Column(name = "coverage_ok", nullable = false)
    private boolean coverageOk;

    @Column(name = "needs_relief", nullable = false)
    private boolean needsRelief;

    @Column(name = "relief_employee_id")
    private Long reliefEmployeeId;

    @Column(name = "store_id", length = 64)
    private String storeId;

    @Column(name = "shift_pattern", length = 64)
    private String shiftPattern;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "coverage_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> coverageSnapshot = new HashMap<>();

    @Column(name = "current_step", nullable = false)
    private int currentStep;

    @Column(name = "reject_reason", length = 512)
    private String rejectReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "half_day_part", length = 16)
    private String halfDayPart = "FULL";

    @Column(name = "attachment_ref", length = 512)
    private String attachmentRef;

    @Column(name = "team_clash_alert", nullable = false)
    private boolean teamClashAlert = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", columnDefinition = "jsonb")
    private Map<String, Object> warnings = new HashMap<>();

    public static final String STATUS_PENDING_CANCEL = "PENDING_CANCEL";

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getLeaveTypeCode() {
        return leaveTypeCode;
    }

    public void setLeaveTypeCode(String leaveTypeCode) {
        this.leaveTypeCode = leaveTypeCode;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public boolean isBalanceDeducted() {
        return balanceDeducted;
    }

    public void setBalanceDeducted(boolean balanceDeducted) {
        this.balanceDeducted = balanceDeducted;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getWorkingDaysCounted() {
        return workingDaysCounted;
    }

    public void setWorkingDaysCounted(BigDecimal workingDaysCounted) {
        this.workingDaysCounted = workingDaysCounted;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isUnionMember() {
        return unionMember;
    }

    public void setUnionMember(boolean unionMember) {
        this.unionMember = unionMember;
    }

    public int getNoticeDaysGiven() {
        return noticeDaysGiven;
    }

    public void setNoticeDaysGiven(int noticeDaysGiven) {
        this.noticeDaysGiven = noticeDaysGiven;
    }

    public int getNoticeDaysRequired() {
        return noticeDaysRequired;
    }

    public void setNoticeDaysRequired(int noticeDaysRequired) {
        this.noticeDaysRequired = noticeDaysRequired;
    }

    public boolean isCoverageOk() {
        return coverageOk;
    }

    public void setCoverageOk(boolean coverageOk) {
        this.coverageOk = coverageOk;
    }

    public boolean isNeedsRelief() {
        return needsRelief;
    }

    public void setNeedsRelief(boolean needsRelief) {
        this.needsRelief = needsRelief;
    }

    public Long getReliefEmployeeId() {
        return reliefEmployeeId;
    }

    public void setReliefEmployeeId(Long reliefEmployeeId) {
        this.reliefEmployeeId = reliefEmployeeId;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getShiftPattern() {
        return shiftPattern;
    }

    public void setShiftPattern(String shiftPattern) {
        this.shiftPattern = shiftPattern;
    }

    public Map<String, Object> getCoverageSnapshot() {
        return coverageSnapshot;
    }

    public void setCoverageSnapshot(Map<String, Object> coverageSnapshot) {
        this.coverageSnapshot = coverageSnapshot == null ? new HashMap<>() : coverageSnapshot;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(int currentStep) {
        this.currentStep = currentStep;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getHalfDayPart() { return halfDayPart; }
    public void setHalfDayPart(String halfDayPart) { this.halfDayPart = halfDayPart == null ? "FULL" : halfDayPart; }

    public String getAttachmentRef() { return attachmentRef; }
    public void setAttachmentRef(String attachmentRef) { this.attachmentRef = attachmentRef; }

    public boolean isTeamClashAlert() { return teamClashAlert; }
    public void setTeamClashAlert(boolean teamClashAlert) { this.teamClashAlert = teamClashAlert; }

    public Map<String, Object> getWarnings() { return warnings; }
    public void setWarnings(Map<String, Object> warnings) { this.warnings = warnings == null ? new HashMap<>() : warnings; }
}
