package com.platform.hrms.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "work_schedule")
public class WorkSchedule {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope_type", nullable = false, length = 16)
    private String scopeType;

    @Column(name = "scope_id", nullable = false)
    private Long scopeId;

    @Column(name = "mode", nullable = false, length = 16)
    private String mode = "FIXED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "working_days", nullable = false, columnDefinition = "jsonb")
    private String workingDays;

    @Column(name = "start_time", length = 8)
    private String startTime;

    @Column(name = "end_time", length = 8)
    private String endTime;

    @Column(name = "grace_minutes")
    private Integer graceMinutes = 15;

    @Column(name = "half_day_hours", precision = 6, scale = 2)
    private BigDecimal halfDayHours;

    @Column(name = "checkout_cutoff", length = 8)
    private String checkoutCutoff = "23:59";

    @Column(name = "weekly_hours", precision = 6, scale = 2)
    private BigDecimal weeklyHours;

    public Long getId() { return id; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public Long getScopeId() { return scopeId; }
    public void setScopeId(Long scopeId) { this.scopeId = scopeId; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getWorkingDays() { return workingDays; }
    public void setWorkingDays(String workingDays) { this.workingDays = workingDays; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public Integer getGraceMinutes() { return graceMinutes; }
    public void setGraceMinutes(Integer graceMinutes) { this.graceMinutes = graceMinutes; }

    public BigDecimal getHalfDayHours() { return halfDayHours; }
    public void setHalfDayHours(BigDecimal halfDayHours) { this.halfDayHours = halfDayHours; }

    public String getCheckoutCutoff() { return checkoutCutoff; }
    public void setCheckoutCutoff(String checkoutCutoff) { this.checkoutCutoff = checkoutCutoff; }

    public BigDecimal getWeeklyHours() { return weeklyHours; }
    public void setWeeklyHours(BigDecimal weeklyHours) { this.weeklyHours = weeklyHours; }
}
