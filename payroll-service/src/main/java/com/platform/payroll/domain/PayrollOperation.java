package com.platform.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Recoverable finalise / reopen operation (guide §6). The key {@code FINALIZE:<period>:r<revision>} or
 * {@code REOPEN:<period>:r<revision>} is unique, so a retried request or re-sent command can never
 * create a second leave deduction.
 */
@Entity
@Table(name = "payroll_operation")
public class PayrollOperation {

    public static final String FINALIZE = "FINALIZE";
    public static final String REOPEN = "REOPEN";

    public static final String PENDING = "PENDING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String REJECTED = "REJECTED";
    public static final String RESOLVED = "RESOLVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "operation_id", nullable = false, length = 64)
    private String operationId;
    @Column(name = "operation_key", nullable = false, length = 160)
    private String operationKey;
    @Column(name = "run_id", nullable = false)
    private Long runId;
    @Column(nullable = false, length = 7, columnDefinition = "char(7)")
    private String period;
    @Column(nullable = false)
    private long revision;
    @Column(nullable = false, length = 16)
    private String action;
    @Column(nullable = false, length = 16)
    private String status = PENDING;
    @Column(name = "reverse_of_key", length = 160)
    private String reverseOfKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> command = new HashMap<>();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> result;
    @Column
    private String error;
    @Column(nullable = false)
    private int attempts = 1;
    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt = Instant.now();
    @Column(name = "correlation_id", length = 64)
    private String correlationId;
    @Column(name = "initiated_by")
    private Long initiatedBy;
    @Column(name = "resolved_by")
    private Long resolvedBy;
    @Column(name = "resolve_reason", length = 512)
    private String resolveReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Version
    @Column(nullable = false)
    private long version;
    @Column(name = "completed_at")
    private Instant completedAt;

    public Long getId() { return id; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String v) { this.operationId = v; }
    public String getOperationKey() { return operationKey; }
    public void setOperationKey(String v) { this.operationKey = v; }
    public Long getRunId() { return runId; }
    public void setRunId(Long v) { this.runId = v; }
    public String getPeriod() { return period; }
    public void setPeriod(String v) { this.period = v; }
    public long getRevision() { return revision; }
    public void setRevision(long v) { this.revision = v; }
    public String getAction() { return action; }
    public void setAction(String v) { this.action = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getReverseOfKey() { return reverseOfKey; }
    public void setReverseOfKey(String v) { this.reverseOfKey = v; }
    public Map<String, Object> getCommand() { return command; }
    public void setCommand(Map<String, Object> v) { this.command = v; }
    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> v) { this.result = v; }
    public String getError() { return error; }
    public void setError(String v) { this.error = v; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int v) { this.attempts = v; }
    public Instant getLastSentAt() { return lastSentAt; }
    public void setLastSentAt(Instant v) { this.lastSentAt = v; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String v) { this.correlationId = v; }
    public Long getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(Long v) { this.initiatedBy = v; }
    public Long getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Long v) { this.resolvedBy = v; }
    public String getResolveReason() { return resolveReason; }
    public void setResolveReason(String v) { this.resolveReason = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { this.completedAt = v; }
}
