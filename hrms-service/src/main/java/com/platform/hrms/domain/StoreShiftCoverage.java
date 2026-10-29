package com.platform.hrms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "store_shift_coverage")
public class StoreShiftCoverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, length = 64)
    private String storeId;

    @Column(name = "shift_pattern", nullable = false, length = 64)
    private String shiftPattern;

    @Column(name = "min_staff", nullable = false)
    private int minStaff;

    public Long getId() {
        return id;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getShiftPattern() {
        return shiftPattern;
    }

    public void setShiftPattern(String shiftPattern) {
        this.shiftPattern = shiftPattern;
    }

    public int getMinStaff() {
        return minStaff;
    }

    public void setMinStaff(int minStaff) {
        this.minStaff = minStaff;
    }
}
