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
import java.util.List;
import java.util.Map;

/**
 * Tenant payroll configuration (platform defaults until customized per business).
 */
@Entity
@Table(name = "payroll_config")
public class PayrollConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "overtime_multiplier", nullable = false, precision = 10, scale = 4)
    private BigDecimal overtimeMultiplier;

    @Column(name = "weekend_multiplier", nullable = false, precision = 10, scale = 4)
    private BigDecimal weekendMultiplier;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deduction_rules", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> deductionRules = new ArrayList<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public BigDecimal getOvertimeMultiplier() {
        return overtimeMultiplier;
    }

    public void setOvertimeMultiplier(BigDecimal overtimeMultiplier) {
        this.overtimeMultiplier = overtimeMultiplier;
    }

    public BigDecimal getWeekendMultiplier() {
        return weekendMultiplier;
    }

    public void setWeekendMultiplier(BigDecimal weekendMultiplier) {
        this.weekendMultiplier = weekendMultiplier;
    }

    public List<Map<String, Object>> getDeductionRules() {
        return deductionRules;
    }

    public void setDeductionRules(List<Map<String, Object>> deductionRules) {
        this.deductionRules = deductionRules == null ? new ArrayList<>() : deductionRules;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
