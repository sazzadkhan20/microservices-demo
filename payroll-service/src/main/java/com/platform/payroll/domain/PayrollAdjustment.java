package com.platform.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** FR-PAY-08 — one manual adjustment per person per month; survives recalculation. */
@Entity
@Table(name = "payroll_adjustment")
public class PayrollAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 7, columnDefinition = "char(7)")
    private String period;
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 512)
    private String note;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_by")
    private Long updatedBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public String getPeriod() { return period; }
    public void setPeriod(String v) { this.period = v; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { this.employeeId = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long v) { this.createdBy = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long v) { this.updatedBy = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
