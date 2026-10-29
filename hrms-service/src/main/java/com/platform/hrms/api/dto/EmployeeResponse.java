package com.platform.hrms.api.dto;

import com.platform.hrms.domain.Department;
import com.platform.hrms.domain.Designation;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.Location;
import com.platform.hrms.domain.Team;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record EmployeeResponse(
        Long id,
        String employeeCode,
        String fullName,
        String preferredName,
        String email,
        String jobTitle,
        Long departmentId,
        String departmentCode,
        String departmentName,
        Long designationId,
        String designationCode,
        String designationName,
        String approverRole,
        Long managerId,
        String managerName,
        Long teamId,
        String teamName,
        Long locationId,
        String locationName,
        String locationCode,
        String employmentType,
        LocalDate startDate,
        LocalDate probationEnd,
        LocalDate contractEnd,
        String payCurrency,
        String workPattern,
        String status,
        String phone,
        String personalEmail,
        BigDecimal baseSalary,
        BigDecimal hourlyRate,
        Map<String, Object> customFields,
        boolean active,
        List<String> roles,
        Instant createdAt,
        Instant updatedAt
) {
    /** Copy with {@code baseSalary} / {@code hourlyRate} nulled (caller may not see compensation). */
    public EmployeeResponse withoutCompensation() {
        return new EmployeeResponse(
                id, employeeCode, fullName, preferredName, email, jobTitle,
                departmentId, departmentCode, departmentName,
                designationId, designationCode, designationName, approverRole,
                managerId, managerName, teamId, teamName,
                locationId, locationName, locationCode,
                employmentType, startDate, probationEnd, contractEnd,
                payCurrency, workPattern, status, phone, personalEmail,
                null, null,
                customFields, active, roles, createdAt, updatedAt);
    }

    public static EmployeeResponse from(
            Employee e,
            Department dept,
            Designation desig,
            Employee manager,
            Team team,
            Location location,
            Map<String, Object> customFields,
            List<String> roles) {
        return new EmployeeResponse(
                e.getId(),
                e.getEmployeeCode(),
                e.getFullName(),
                e.getPreferredName(),
                e.getEmail(),
                e.getJobTitle(),
                e.getDepartmentId(),
                dept == null ? null : dept.getCode(),
                dept == null ? null : dept.getName(),
                e.getDesignationId(),
                desig == null ? null : desig.getCode(),
                desig == null ? null : desig.getName(),
                desig == null ? null : desig.getApproverRole(),
                e.getManagerId(),
                manager == null ? null : manager.getFullName(),
                e.getTeamId(),
                team == null ? null : team.getName(),
                e.getLocationId(),
                location == null ? null : location.getName(),
                location == null ? null : location.getCode(),
                e.getEmploymentType(),
                e.getStartDate(),
                e.getProbationEnd(),
                e.getContractEnd(),
                e.getPayCurrency(),
                e.getWorkPattern(),
                e.getStatus(),
                e.getPhone(),
                e.getPersonalEmail(),
                e.getBaseSalary(),
                e.getHourlyRate(),
                customFields,
                e.isActive(),
                roles == null ? List.of() : roles,
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
