package com.platform.people.domain;

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
import java.util.HashMap;
import java.util.Map;

/**
 * Core employee with mandatory org fields (department, designation, manager).
 * Tenant-specific extras stay in {@link #customFields} / extension tables.
 */
@Entity
@Table(name = "employee")
@jakarta.persistence.EntityListeners(com.platform.people.application.RbacCacheInvalidationListener.class)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, unique = true, length = 64)
    private String employeeCode;

    @Column(name = "full_name", nullable = false, length = 256)
    private String fullName;

    @Column(nullable = false, length = 256)
    private String email;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "designation_id")
    private Long designationId;

    /** Real manager user (another employee row). */
    @Column(name = "manager_id")
    private Long managerId;

    @Column(name = "preferred_name", length = 128)
    private String preferredName;

    @Column(name = "job_title", length = 128)
    private String jobTitle;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "employment_type", length = 32)
    private String employmentType;

    @Column(name = "start_date")
    private java.time.LocalDate startDate;

    @Column(name = "probation_end")
    private java.time.LocalDate probationEnd;

    @Column(name = "contract_end")
    private java.time.LocalDate contractEnd;

    @Column(name = "pay_currency", length = 8)
    private String payCurrency;

    @Column(name = "personal_email", length = 256)
    private String personalEmail;

    /** MinIO storage key ({@code bucket/object}) for the employee photo. */
    @Column(name = "photo_object_key", length = 512)
    private String photoObjectKey;

    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    @Column(length = 512)
    private String address;

    @Column(name = "emergency_name", length = 128)
    private String emergencyName;

    @Column(name = "emergency_relation", length = 64)
    private String emergencyRelation;

    @Column(name = "emergency_phone", length = 64)
    private String emergencyPhone;

    @Column(name = "exit_date")
    private java.time.LocalDate exitDate;

    @Column(name = "exit_reason", length = 512)
    private String exitReason;

    @Column(length = 32)
    private String status = "Active";

    @Column(length = 64)
    private String phone;

    @Column(name = "work_pattern", length = 32)
    private String workPattern = "FIXED";

    @Column(name = "base_salary", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseSalary;

    @Column(name = "hourly_rate", nullable = false, precision = 19, scale = 4)
    private BigDecimal hourlyRate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> customFields = new HashMap<>();

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Long getDesignationId() {
        return designationId;
    }

    public void setDesignationId(Long designationId) {
        this.designationId = designationId;
    }

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long managerId) {
        this.managerId = managerId;
    }

    public String getPreferredName() { return preferredName; }
    public void setPreferredName(String preferredName) { this.preferredName = preferredName; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }
    public java.time.LocalDate getStartDate() { return startDate; }
    public void setStartDate(java.time.LocalDate startDate) { this.startDate = startDate; }
    public java.time.LocalDate getProbationEnd() { return probationEnd; }
    public void setProbationEnd(java.time.LocalDate probationEnd) { this.probationEnd = probationEnd; }
    public java.time.LocalDate getContractEnd() { return contractEnd; }
    public void setContractEnd(java.time.LocalDate contractEnd) { this.contractEnd = contractEnd; }
    public String getPayCurrency() { return payCurrency; }
    public void setPayCurrency(String payCurrency) { this.payCurrency = payCurrency; }
    public String getPersonalEmail() { return personalEmail; }
    public void setPersonalEmail(String personalEmail) { this.personalEmail = personalEmail; }
    public String getPhotoObjectKey() { return photoObjectKey; }
    public void setPhotoObjectKey(String photoObjectKey) { this.photoObjectKey = photoObjectKey; }
    public java.time.LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(java.time.LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getEmergencyName() { return emergencyName; }
    public void setEmergencyName(String emergencyName) { this.emergencyName = emergencyName; }
    public String getEmergencyRelation() { return emergencyRelation; }
    public void setEmergencyRelation(String emergencyRelation) { this.emergencyRelation = emergencyRelation; }
    public String getEmergencyPhone() { return emergencyPhone; }
    public void setEmergencyPhone(String emergencyPhone) { this.emergencyPhone = emergencyPhone; }
    public java.time.LocalDate getExitDate() { return exitDate; }
    public void setExitDate(java.time.LocalDate exitDate) { this.exitDate = exitDate; }
    public String getExitReason() { return exitReason; }
    public void setExitReason(String exitReason) { this.exitReason = exitReason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getWorkPattern() { return workPattern; }
    public void setWorkPattern(String workPattern) { this.workPattern = workPattern; }

    public BigDecimal getBaseSalary() {
        return baseSalary;
    }

    public void setBaseSalary(BigDecimal baseSalary) {
        this.baseSalary = baseSalary;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(BigDecimal hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public Map<String, Object> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, Object> customFields) {
        this.customFields = customFields == null ? new HashMap<>() : customFields;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
