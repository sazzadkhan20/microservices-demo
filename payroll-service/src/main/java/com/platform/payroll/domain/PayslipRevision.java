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
import java.util.Map;

/** Append-only finalised payslip snapshot (DR-03, guide §5/§6). Reopen supersedes, never deletes. */
@Entity
@Table(name = "payslip_revision")
public class PayslipRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "run_id", nullable = false)
    private Long runId;
    @Column(nullable = false, length = 7, columnDefinition = "char(7)")
    private String period;
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;
    @Column(name = "run_revision", nullable = false)
    private long runRevision;
    @Column(length = 3)
    private String currency;
    @Column(name = "net_pay", nullable = false, precision = 19, scale = 2)
    private BigDecimal netPay;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> snapshot;
    @Column(name = "finalized_at", nullable = false)
    private Instant finalizedAt;
    @Column(name = "finalized_by")
    private Long finalizedBy;
    @Column(name = "operation_id", nullable = false, length = 64)
    private String operationId;
    @Column(name = "superseded_at")
    private Instant supersededAt;
    @Column(name = "superseded_by_operation", length = 64)
    private String supersededByOperation;

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long v) { this.runId = v; }
    public String getPeriod() { return period; }
    public void setPeriod(String v) { this.period = v; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { this.employeeId = v; }
    public long getRunRevision() { return runRevision; }
    public void setRunRevision(long v) { this.runRevision = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public BigDecimal getNetPay() { return netPay; }
    public void setNetPay(BigDecimal v) { this.netPay = v; }
    public Map<String, Object> getSnapshot() { return snapshot; }
    public void setSnapshot(Map<String, Object> v) { this.snapshot = v; }
    public Instant getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(Instant v) { this.finalizedAt = v; }
    public Long getFinalizedBy() { return finalizedBy; }
    public void setFinalizedBy(Long v) { this.finalizedBy = v; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String v) { this.operationId = v; }
    public Instant getSupersededAt() { return supersededAt; }
    public void setSupersededAt(Instant v) { this.supersededAt = v; }
    public String getSupersededByOperation() { return supersededByOperation; }
    public void setSupersededByOperation(String v) { this.supersededByOperation = v; }
}
