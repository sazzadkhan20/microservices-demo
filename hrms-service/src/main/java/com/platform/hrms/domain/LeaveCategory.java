package com.platform.hrms.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "leave_category")
public class LeaveCategory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "quota_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal quotaDays;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "paid", nullable = false)
    private boolean paid = true;

    @Column(name = "half_day_allowed", nullable = false)
    private boolean halfDayAllowed = true;

    @Column(name = "attachment_mode", length = 16)
    private String attachmentMode = "NEVER";

    @Column(name = "attachment_after_days")
    private int attachmentAfterDays = 0;

    @Column(name = "notice_days")
    private int noticeDays = 0;

    @Column(name = "max_per_request")
    private int maxPerRequest = 0;

    @Column(name = "negative_cap", precision = 10, scale = 2)
    private BigDecimal negativeCap;

    @Column(name = "approval_chain", columnDefinition = "JSONB")
    private String approvalChain;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getId() { return id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getQuotaDays() { return quotaDays; }
    public void setQuotaDays(BigDecimal quotaDays) { this.quotaDays = quotaDays; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }

    public boolean isHalfDayAllowed() { return halfDayAllowed; }
    public void setHalfDayAllowed(boolean halfDayAllowed) { this.halfDayAllowed = halfDayAllowed; }

    public String getAttachmentMode() { return attachmentMode; }
    public void setAttachmentMode(String attachmentMode) { this.attachmentMode = attachmentMode; }

    public int getAttachmentAfterDays() { return attachmentAfterDays; }
    public void setAttachmentAfterDays(int attachmentAfterDays) { this.attachmentAfterDays = attachmentAfterDays; }

    public int getNoticeDays() { return noticeDays; }
    public void setNoticeDays(int noticeDays) { this.noticeDays = noticeDays; }

    public int getMaxPerRequest() { return maxPerRequest; }
    public void setMaxPerRequest(int maxPerRequest) { this.maxPerRequest = maxPerRequest; }

    public BigDecimal getNegativeCap() { return negativeCap; }
    public void setNegativeCap(BigDecimal negativeCap) { this.negativeCap = negativeCap; }

    public String getApprovalChain() { return approvalChain; }
    public void setApprovalChain(String approvalChain) { this.approvalChain = approvalChain; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
