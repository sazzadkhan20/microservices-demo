package com.platform.leave.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "leave_balance_ledger")
public class LeaveBalanceLedger {

    public static final String SOURCE_ADJUST = "ADJUST";
    public static final String SOURCE_APPROVE = "APPROVE";
    public static final String SOURCE_CANCEL = "CANCEL";
    public static final String SOURCE_QUOTA = "QUOTA";
    public static final String SOURCE_CARRY = "CARRY";
    public static final String SOURCE_OPENING = "OPENING";
    public static final String SOURCE_COVER = "COVER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "leave_type_code", nullable = false, length = 32)
    private String leaveTypeCode;

    @Column(nullable = false)
    private int year;

    @Column(name = "delta_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal deltaDays;

    @Column(length = 512)
    private String reason;

    @Column(name = "actor_employee_id")
    private Long actorEmployeeId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "operation_key", length = 160)
    private String operationKey;

    @Column(name = "source_type", length = 32)
    private String sourceType;

    @Column(name = "source_id", length = 64)
    private String sourceId;

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

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public BigDecimal getDeltaDays() {
        return deltaDays;
    }

    public void setDeltaDays(BigDecimal deltaDays) {
        this.deltaDays = deltaDays;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getActorEmployeeId() {
        return actorEmployeeId;
    }

    public void setActorEmployeeId(Long actorEmployeeId) {
        this.actorEmployeeId = actorEmployeeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getOperationKey() {
        return operationKey;
    }

    public void setOperationKey(String operationKey) {
        this.operationKey = operationKey;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }
}
