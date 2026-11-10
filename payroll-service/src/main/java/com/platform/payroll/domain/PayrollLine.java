package com.platform.payroll.domain;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One person's row in a payroll run (FR-PAY-01, FR-PAY-15). Finalised rows are snapshots (DR-03). */
@Entity
@Table(name = "payroll_line")
public class PayrollLine {

    public static final String DRAFT = "DRAFT";
    public static final String FINALIZED = "FINALIZED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "run_id", nullable = false)
    private Long runId;
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;
    @Column(name = "employee_code", length = 64)
    private String employeeCode;
    @Column(name = "employee_name", length = 256)
    private String employeeName;
    @Column(length = 3)
    private String currency;
    @Column(name = "pay_type", length = 16)
    private String payType;
    @Column(name = "working_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal workingDays = BigDecimal.ZERO;
    @Column(name = "days_employed", nullable = false, precision = 6, scale = 2)
    private BigDecimal daysEmployed = BigDecimal.ZERO;
    @Column(name = "per_day_rate", precision = 19, scale = 2)
    private BigDecimal perDayRate;
    @Column(name = "hourly_rate", precision = 19, scale = 2)
    private BigDecimal hourlyRate;
    @Column(name = "hours_worked", nullable = false, precision = 8, scale = 2)
    private BigDecimal hoursWorked = BigDecimal.ZERO;
    @Column(name = "absent_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal absentDays = BigDecimal.ZERO;
    @Column(name = "covered_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal coveredDays = BigDecimal.ZERO;
    @Column(name = "unpaid_leave_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal unpaidLeaveDays = BigDecimal.ZERO;
    @Column(name = "deducted_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal deductedDays = BigDecimal.ZERO;
    @Column(name = "base_pay", nullable = false, precision = 19, scale = 2)
    private BigDecimal basePay = BigDecimal.ZERO;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal deduction = BigDecimal.ZERO;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal adjustment = BigDecimal.ZERO;
    @Column(name = "adjustment_note", length = 512)
    private String adjustmentNote;
    @Column(name = "net_pay", nullable = false, precision = 19, scale = 2)
    private BigDecimal netPay = BigDecimal.ZERO;
    @Column(nullable = false)
    private boolean exception;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> notes = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> covers = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> calc = new HashMap<>();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> facts = new HashMap<>();
    @Column(nullable = false, length = 16)
    private String status = DRAFT;
    @Column(nullable = false)
    private long revision;
    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt = Instant.now();

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long v) { this.runId = v; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { this.employeeId = v; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String v) { this.employeeCode = v; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String v) { this.employeeName = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getPayType() { return payType; }
    public void setPayType(String v) { this.payType = v; }
    public BigDecimal getWorkingDays() { return workingDays; }
    public void setWorkingDays(BigDecimal v) { this.workingDays = v; }
    public BigDecimal getDaysEmployed() { return daysEmployed; }
    public void setDaysEmployed(BigDecimal v) { this.daysEmployed = v; }
    public BigDecimal getPerDayRate() { return perDayRate; }
    public void setPerDayRate(BigDecimal v) { this.perDayRate = v; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal v) { this.hourlyRate = v; }
    public BigDecimal getHoursWorked() { return hoursWorked; }
    public void setHoursWorked(BigDecimal v) { this.hoursWorked = v; }
    public BigDecimal getAbsentDays() { return absentDays; }
    public void setAbsentDays(BigDecimal v) { this.absentDays = v; }
    public BigDecimal getCoveredDays() { return coveredDays; }
    public void setCoveredDays(BigDecimal v) { this.coveredDays = v; }
    public BigDecimal getUnpaidLeaveDays() { return unpaidLeaveDays; }
    public void setUnpaidLeaveDays(BigDecimal v) { this.unpaidLeaveDays = v; }
    public BigDecimal getDeductedDays() { return deductedDays; }
    public void setDeductedDays(BigDecimal v) { this.deductedDays = v; }
    public BigDecimal getBasePay() { return basePay; }
    public void setBasePay(BigDecimal v) { this.basePay = v; }
    public BigDecimal getDeduction() { return deduction; }
    public void setDeduction(BigDecimal v) { this.deduction = v; }
    public BigDecimal getAdjustment() { return adjustment; }
    public void setAdjustment(BigDecimal v) { this.adjustment = v; }
    public String getAdjustmentNote() { return adjustmentNote; }
    public void setAdjustmentNote(String v) { this.adjustmentNote = v; }
    public BigDecimal getNetPay() { return netPay; }
    public void setNetPay(BigDecimal v) { this.netPay = v; }
    public boolean isException() { return exception; }
    public void setException(boolean v) { this.exception = v; }
    public List<String> getNotes() { return notes; }
    public void setNotes(List<String> v) { this.notes = v; }
    public List<Map<String, Object>> getCovers() { return covers; }
    public void setCovers(List<Map<String, Object>> v) { this.covers = v; }
    public Map<String, Object> getCalc() { return calc; }
    public void setCalc(Map<String, Object> v) { this.calc = v; }
    public Map<String, Object> getFacts() { return facts; }
    public void setFacts(Map<String, Object> v) { this.facts = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public long getRevision() { return revision; }
    public void setRevision(long v) { this.revision = v; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(Instant v) { this.calculatedAt = v; }
}
