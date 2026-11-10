package com.platform.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Single-row admin payroll rules (docs/PAYROLL_SPEC.md §1.9). Defaults = SRS defaults. */
@Entity
@Table(name = "payroll_settings")
public class PayrollSettings {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Column(name = "per_day_rate_basis", nullable = false, length = 16)
    private String perDayRateBasis = "WORKING_DAYS";

    @Column(name = "absence_rule", nullable = false, length = 16)
    private String absenceRule = "BALANCE_FIRST";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "absence_cover_order", nullable = false, columnDefinition = "jsonb")
    private List<String> absenceCoverOrder = new ArrayList<>(List.of("CASUAL", "ANNUAL"));

    @Column(name = "notify_payslip_ready", nullable = false)
    private boolean notifyPayslipReady = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    private long version;

    public Short getId() { return id; }
    public String getPerDayRateBasis() { return perDayRateBasis; }
    public void setPerDayRateBasis(String v) { this.perDayRateBasis = v; }
    public String getAbsenceRule() { return absenceRule; }
    public void setAbsenceRule(String v) { this.absenceRule = v; }
    public List<String> getAbsenceCoverOrder() { return absenceCoverOrder; }
    public void setAbsenceCoverOrder(List<String> v) { this.absenceCoverOrder = v; }
    public boolean isNotifyPayslipReady() { return notifyPayslipReady; }
    public void setNotifyPayslipReady(boolean v) { this.notifyPayslipReady = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long v) { this.updatedBy = v; }
    public long getVersion() { return version; }
}
