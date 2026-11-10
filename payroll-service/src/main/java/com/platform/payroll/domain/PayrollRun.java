package com.platform.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** One month's payroll run (FR-PAY-11..14); lifecycle in docs/PAYROLL_SPEC.md §1.6. */
@Entity
@Table(name = "payroll_run")
public class PayrollRun {

    public static final String DRAFT = "DRAFT";
    public static final String FINALIZING = "FINALIZING";
    public static final String FINALIZED = "FINALIZED";
    public static final String REOPENING = "REOPENING";
    public static final String ACTION_REQUIRED = "ACTION_REQUIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 7, columnDefinition = "char(7)")
    private String period;

    @Column(nullable = false, length = 16)
    private String status = DRAFT;

    @Column(nullable = false)
    private long revision;

    @Column(name = "calculated_at")
    private Instant calculatedAt;
    @Column(name = "calculated_by")
    private Long calculatedBy;
    @Column(name = "finalized_at")
    private Instant finalizedAt;
    @Column(name = "finalized_by")
    private Long finalizedBy;
    @Column(name = "reopened_at")
    private Instant reopenedAt;
    @Column(name = "reopened_by")
    private Long reopenedBy;
    @Column(name = "reopen_reason", length = 512)
    private String reopenReason;
    @Column(name = "operation_id", length = 64)
    private String operationId;
    @Column(name = "action_required_reason")
    private String actionRequiredReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    public Long getId() { return id; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = Instant.now(); }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(Instant v) { this.calculatedAt = v; }
    public Long getCalculatedBy() { return calculatedBy; }
    public void setCalculatedBy(Long v) { this.calculatedBy = v; }
    public Instant getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(Instant v) { this.finalizedAt = v; }
    public Long getFinalizedBy() { return finalizedBy; }
    public void setFinalizedBy(Long v) { this.finalizedBy = v; }
    public Instant getReopenedAt() { return reopenedAt; }
    public void setReopenedAt(Instant v) { this.reopenedAt = v; }
    public Long getReopenedBy() { return reopenedBy; }
    public void setReopenedBy(Long v) { this.reopenedBy = v; }
    public String getReopenReason() { return reopenReason; }
    public void setReopenReason(String v) { this.reopenReason = v; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String v) { this.operationId = v; }
    public String getActionRequiredReason() { return actionRequiredReason; }
    public void setActionRequiredReason(String v) { this.actionRequiredReason = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public boolean is(String s) {
        return s.equals(status);
    }
}
