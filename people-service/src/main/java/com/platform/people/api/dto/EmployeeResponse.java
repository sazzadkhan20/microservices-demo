package com.platform.people.api.dto;

import com.platform.people.domain.Department;
import com.platform.people.domain.Designation;
import com.platform.people.domain.Employee;
import com.platform.people.domain.Location;
import com.platform.people.domain.Team;

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
        String address,
        LocalDate dateOfBirth,
        String emergencyName,
        String emergencyRelation,
        /** FE alias for emergencyRelation. */
        String emergencyRelationship,
        String emergencyPhone,
        String photoObjectKey,
        Long photoFileId,
        LocalDate exitDate,
        String exitReason,
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
                address, dateOfBirth, emergencyName, emergencyRelation, emergencyRelationship,
                emergencyPhone, photoObjectKey, photoFileId, exitDate, exitReason,
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
        Map<String, Object> cf = customFields == null ? Map.of() : customFields;
        String relation = e.getEmergencyRelation();
        if (relation == null || relation.isBlank()) {
            Object legacy = cf.get("emergencyRelation");
            if (legacy == null) {
                legacy = cf.get("emergencyRelationship");
            }
            relation = legacy == null ? null : String.valueOf(legacy);
        }
        Long photoFileId = null;
        Object pf = cf.get("photoFileId");
        if (pf != null && !String.valueOf(pf).isBlank()) {
            try {
                photoFileId = Long.valueOf(String.valueOf(pf).trim());
            } catch (NumberFormatException ignored) {
                photoFileId = null;
            }
        }
        String address = e.getAddress() != null ? e.getAddress()
                : (cf.get("address") == null ? null : String.valueOf(cf.get("address")));
        String emergencyName = e.getEmergencyName() != null ? e.getEmergencyName()
                : (cf.get("emergencyName") == null ? null : String.valueOf(cf.get("emergencyName")));
        String emergencyPhone = e.getEmergencyPhone() != null ? e.getEmergencyPhone()
                : (cf.get("emergencyPhone") == null ? null : String.valueOf(cf.get("emergencyPhone")));
        LocalDate exitDate = e.getExitDate();
        if (exitDate == null && cf.get("exitDate") != null && !String.valueOf(cf.get("exitDate")).isBlank()) {
            try {
                exitDate = LocalDate.parse(String.valueOf(cf.get("exitDate")));
            } catch (Exception ignored) {
                exitDate = null;
            }
        }
        String exitReason = e.getExitReason() != null ? e.getExitReason()
                : (cf.get("exitReason") == null ? null : String.valueOf(cf.get("exitReason")));
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
                address,
                e.getDateOfBirth(),
                emergencyName,
                relation,
                relation,
                emergencyPhone,
                e.getPhotoObjectKey(),
                photoFileId,
                exitDate,
                exitReason,
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
