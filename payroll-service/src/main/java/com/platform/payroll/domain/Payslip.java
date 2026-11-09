package com.platform.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(
        name = "payslip",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_payslip_employee_period",
                columnNames = {"employee_id", "period_label"}))
public class Payslip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "period_label", nullable = false, length = 32)
    private String periodLabel;

    @Column(name = "regular_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal regularHours;

    @Column(name = "overtime_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal overtimeHours;

    @Column(name = "weekend_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal weekendHours;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "deduction_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal deductionAmount;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> detailsJson = new HashMap<>();

    @Column(nullable = false)
    private long revision = 1L;

    /** PAY-04 — optimistic concurrency for competing calculate/finalize. */
    @Version
    @Column(name = "row_version")
    private Long rowVersion;

    @Column(nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "operation_id", length = 64)
    private String operationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "facts_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> factsSnapshot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getPeriodLabel() {
        return periodLabel;
    }

    public void setPeriodLabel(String periodLabel) {
        this.periodLabel = periodLabel;
    }

    public BigDecimal getRegularHours() {
        return regularHours;
    }

    public void setRegularHours(BigDecimal regularHours) {
        this.regularHours = regularHours;
    }

    public BigDecimal getOvertimeHours() {
        return overtimeHours;
    }

    public void setOvertimeHours(BigDecimal overtimeHours) {
        this.overtimeHours = overtimeHours;
    }

    public BigDecimal getWeekendHours() {
        return weekendHours;
    }

    public void setWeekendHours(BigDecimal weekendHours) {
        this.weekendHours = weekendHours;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public BigDecimal getDeductionAmount() {
        return deductionAmount;
    }

    public void setDeductionAmount(BigDecimal deductionAmount) {
        this.deductionAmount = deductionAmount;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }

    public Map<String, Object> getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(Map<String, Object> detailsJson) {
        this.detailsJson = detailsJson == null ? new HashMap<>() : detailsJson;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long rowVersion) {
        this.rowVersion = rowVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public Map<String, Object> getFactsSnapshot() {
        return factsSnapshot;
    }

    public void setFactsSnapshot(Map<String, Object> factsSnapshot) {
        this.factsSnapshot = factsSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
