package com.platform.hrms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Config-driven leave product type. Flags decide zero-balance / coverage / paid behavior.
 */
@Entity
@Table(name = "leave_type")
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private boolean paid = true;

    @Column(name = "requires_balance", nullable = false)
    private boolean requiresBalance = true;

    @Column(name = "allow_when_zero_balance", nullable = false)
    private boolean allowWhenZeroBalance = false;

    @Column(name = "allow_when_coverage_fail", nullable = false)
    private boolean allowWhenCoverageFail = false;

    @Column(name = "deduct_balance", nullable = false)
    private boolean deductBalance = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public boolean isRequiresBalance() {
        return requiresBalance;
    }

    public void setRequiresBalance(boolean requiresBalance) {
        this.requiresBalance = requiresBalance;
    }

    public boolean isAllowWhenZeroBalance() {
        return allowWhenZeroBalance;
    }

    public void setAllowWhenZeroBalance(boolean allowWhenZeroBalance) {
        this.allowWhenZeroBalance = allowWhenZeroBalance;
    }

    public boolean isAllowWhenCoverageFail() {
        return allowWhenCoverageFail;
    }

    public void setAllowWhenCoverageFail(boolean allowWhenCoverageFail) {
        this.allowWhenCoverageFail = allowWhenCoverageFail;
    }

    public boolean isDeductBalance() {
        return deductBalance;
    }

    public void setDeductBalance(boolean deductBalance) {
        this.deductBalance = deductBalance;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
