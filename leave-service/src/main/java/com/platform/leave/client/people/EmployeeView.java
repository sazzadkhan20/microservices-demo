package com.platform.leave.client.people;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * People-owned employee view used by Leave (fetched via People API — not a local table).
 */
public class EmployeeView {

    private Long id;
    private String employeeCode;
    private String fullName;
    private String email;
    private Long departmentId;
    private Long designationId;
    private Long managerId;
    private String preferredName;
    private String jobTitle;
    private Long locationId;
    private Long teamId;
    private String designationApproverRole;
    private String teamCode;
    private Integer teamMaxOnLeave;
    private String teamClashAction;
    private Long teamLeadEmployeeId;
    private String employmentType;
    private LocalDate startDate;
    private LocalDate probationEnd;
    private LocalDate contractEnd;
    private String payCurrency;
    private String personalEmail;
    private String status = "Active";
    private String phone;
    private String workPattern = "FIXED";
    private BigDecimal baseSalary = BigDecimal.ZERO;
    private BigDecimal hourlyRate = BigDecimal.ZERO;
    private Map<String, Object> customFields = new HashMap<>();
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getDesignationId() { return designationId; }
    public void setDesignationId(Long designationId) { this.designationId = designationId; }
    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }
    public String getPreferredName() { return preferredName; }
    public void setPreferredName(String preferredName) { this.preferredName = preferredName; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getDesignationApproverRole() { return designationApproverRole; }
    public void setDesignationApproverRole(String designationApproverRole) {
        this.designationApproverRole = designationApproverRole;
    }
    public String getTeamCode() { return teamCode; }
    public void setTeamCode(String teamCode) { this.teamCode = teamCode; }
    public Integer getTeamMaxOnLeave() { return teamMaxOnLeave; }
    public void setTeamMaxOnLeave(Integer teamMaxOnLeave) { this.teamMaxOnLeave = teamMaxOnLeave; }
    public String getTeamClashAction() { return teamClashAction; }
    public void setTeamClashAction(String teamClashAction) { this.teamClashAction = teamClashAction; }
    public Long getTeamLeadEmployeeId() { return teamLeadEmployeeId; }
    public void setTeamLeadEmployeeId(Long teamLeadEmployeeId) { this.teamLeadEmployeeId = teamLeadEmployeeId; }
    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getProbationEnd() { return probationEnd; }
    public void setProbationEnd(LocalDate probationEnd) { this.probationEnd = probationEnd; }
    public LocalDate getContractEnd() { return contractEnd; }
    public void setContractEnd(LocalDate contractEnd) { this.contractEnd = contractEnd; }
    public String getPayCurrency() { return payCurrency; }
    public void setPayCurrency(String payCurrency) { this.payCurrency = payCurrency; }
    public String getPersonalEmail() { return personalEmail; }
    public void setPersonalEmail(String personalEmail) { this.personalEmail = personalEmail; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getWorkPattern() { return workPattern; }
    public void setWorkPattern(String workPattern) { this.workPattern = workPattern; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }
    public Map<String, Object> getCustomFields() { return customFields; }
    public void setCustomFields(Map<String, Object> customFields) {
        this.customFields = customFields == null ? new HashMap<>() : customFields;
    }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
