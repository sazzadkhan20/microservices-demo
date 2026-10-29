package com.platform.hrms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Generic indexed extension attributes for frequent filters.
 * Businesses later map field definitions to keys stored here.
 */
@Entity
@Table(name = "ext_indexed_attribute")
@IdClass(ExtIndexedAttribute.Pk.class)
public class ExtIndexedAttribute {

    @Id
    @Column(name = "employee_id")
    private Long employeeId;

    @Id
    @Column(name = "attr_key", length = 64)
    private String attrKey;

    @Column(name = "attr_value", nullable = false, length = 256)
    private String attrValue;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getAttrKey() {
        return attrKey;
    }

    public void setAttrKey(String attrKey) {
        this.attrKey = attrKey;
    }

    public String getAttrValue() {
        return attrValue;
    }

    public void setAttrValue(String attrValue) {
        this.attrValue = attrValue;
    }

    public static class Pk implements Serializable {
        private Long employeeId;
        private String attrKey;

        public Pk() {
        }

        public Pk(Long employeeId, String attrKey) {
            this.employeeId = employeeId;
            this.attrKey = attrKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(employeeId, pk.employeeId) && Objects.equals(attrKey, pk.attrKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(employeeId, attrKey);
        }
    }
}
