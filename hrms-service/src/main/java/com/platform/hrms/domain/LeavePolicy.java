package com.platform.hrms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tenant leave configuration (DATA). Modes + chain + working days — no tenant if-else in Java.
 */
@Entity
@Table(name = "leave_policy")
public class LeavePolicy {

    public static final String NOTICE_NONE = "NONE";
    public static final String NOTICE_FIXED = "FIXED";
    public static final String NOTICE_ATTRIBUTE = "ATTRIBUTE";

    public static final String COVERAGE_NONE = "NONE";
    public static final String COVERAGE_LOCATION = "LOCATION";
    public static final String COVERAGE_LOCATION_SHIFT = "LOCATION_SHIFT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "annual_days", nullable = false)
    private int annualDays;

    @Column(name = "work_week_days", nullable = false)
    private int workWeekDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "working_days", nullable = false, columnDefinition = "jsonb")
    private List<String> workingDays = new ArrayList<>();

    @Column(name = "union_notice_days", nullable = false)
    private int unionNoticeDays = 7;

    @Column(name = "non_union_notice_days", nullable = false)
    private int nonUnionNoticeDays = 3;

    @Column(name = "require_coverage_check", nullable = false)
    private boolean requireCoverageCheck = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "approval_chain", columnDefinition = "jsonb")
    private List<Map<String, Object>> approvalChain = new ArrayList<>();

    @Column(name = "notice_mode", length = 32)
    private String noticeMode = NOTICE_ATTRIBUTE;

    @Column(name = "default_notice_days")
    private int defaultNoticeDays = 0;

    @Column(name = "coverage_mode", length = 32)
    private String coverageMode = COVERAGE_LOCATION_SHIFT;

    @Column(name = "location_field", length = 64)
    private String locationField = "store_id";

    @Column(name = "shift_field", length = 64)
    private String shiftField = "shift_pattern";

    @Column(name = "require_relief_on_coverage_fail")
    private boolean requireReliefOnCoverageFail = true;

    @Column(name = "default_leave_type_code", length = 32)
    private String defaultLeaveTypeCode;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public int getAnnualDays() {
        return annualDays;
    }

    public void setAnnualDays(int annualDays) {
        this.annualDays = annualDays;
    }

    public int getWorkWeekDays() {
        return workWeekDays;
    }

    public void setWorkWeekDays(int workWeekDays) {
        this.workWeekDays = workWeekDays;
    }

    public List<String> getWorkingDays() {
        return workingDays;
    }

    public void setWorkingDays(List<String> workingDays) {
        this.workingDays = workingDays == null ? new ArrayList<>() : workingDays;
    }

    public int getUnionNoticeDays() {
        return unionNoticeDays;
    }

    public void setUnionNoticeDays(int unionNoticeDays) {
        this.unionNoticeDays = unionNoticeDays;
    }

    public int getNonUnionNoticeDays() {
        return nonUnionNoticeDays;
    }

    public void setNonUnionNoticeDays(int nonUnionNoticeDays) {
        this.nonUnionNoticeDays = nonUnionNoticeDays;
    }

    public boolean isRequireCoverageCheck() {
        return requireCoverageCheck;
    }

    public void setRequireCoverageCheck(boolean requireCoverageCheck) {
        this.requireCoverageCheck = requireCoverageCheck;
    }

    public List<Map<String, Object>> getApprovalChain() {
        return approvalChain;
    }

    public void setApprovalChain(List<Map<String, Object>> approvalChain) {
        this.approvalChain = approvalChain == null ? new ArrayList<>() : approvalChain;
    }

    public String getNoticeMode() {
        return noticeMode == null ? NOTICE_ATTRIBUTE : noticeMode;
    }

    public void setNoticeMode(String noticeMode) {
        this.noticeMode = noticeMode;
    }

    public int getDefaultNoticeDays() {
        return defaultNoticeDays;
    }

    public void setDefaultNoticeDays(int defaultNoticeDays) {
        this.defaultNoticeDays = defaultNoticeDays;
    }

    public String getCoverageMode() {
        return coverageMode == null ? COVERAGE_LOCATION_SHIFT : coverageMode;
    }

    public void setCoverageMode(String coverageMode) {
        this.coverageMode = coverageMode;
    }

    public String getLocationField() {
        return locationField == null ? "store_id" : locationField;
    }

    public void setLocationField(String locationField) {
        this.locationField = locationField;
    }

    public String getShiftField() {
        return shiftField == null ? "shift_pattern" : shiftField;
    }

    public void setShiftField(String shiftField) {
        this.shiftField = shiftField;
    }

    public boolean isRequireReliefOnCoverageFail() {
        return requireReliefOnCoverageFail;
    }

    public void setRequireReliefOnCoverageFail(boolean requireReliefOnCoverageFail) {
        this.requireReliefOnCoverageFail = requireReliefOnCoverageFail;
    }

    public String getDefaultLeaveTypeCode() {
        return defaultLeaveTypeCode;
    }

    public void setDefaultLeaveTypeCode(String defaultLeaveTypeCode) {
        this.defaultLeaveTypeCode = defaultLeaveTypeCode;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
