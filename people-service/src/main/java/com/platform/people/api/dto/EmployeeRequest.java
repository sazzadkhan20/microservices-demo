package com.platform.people.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Create / update employee. On create, {@code employeeCode} is governed by tenant
 * {@code employeeCodeMode}: AUTO (ignore client, generate from prefix/digits/pattern),
 * MANUAL (required), or OPTIONAL (generate when blank).
 * Choosing {@code teamId} always sets department from the team (FR-EMP-05).
 * Empty {@code roleCodes} on create applies {@code defaultCreateRoleCodes} (e.g. EMPLOYEE).
 * {@code managerEffectiveFrom} — when managerId changes, reporting-line history starts on this date
 * (defaults to today). Future dates keep the current manager until then.
 * {@code photoFileId} resolves to {@code photoObjectKey} via file-service (MinIO key).
 * {@code emergencyRelationship} is accepted as an alias for {@code emergencyRelation}.
 */
public record EmployeeRequest(
        String employeeCode,
        @NotBlank String fullName,
        @NotBlank @Email String email,
        String preferredName,
        String jobTitle,
        Long departmentId,
        Long designationId,
        Long managerId,
        LocalDate managerEffectiveFrom,
        Long teamId,
        Long locationId,
        String employmentType,
        LocalDate startDate,
        LocalDate probationEnd,
        LocalDate contractEnd,
        String payCurrency,
        String workPattern,
        BigDecimal baseSalary,
        BigDecimal hourlyRate,
        String status,
        String phone,
        String personalEmail,
        String address,
        LocalDate dateOfBirth,
        String emergencyName,
        String emergencyRelation,
        /** Alias accepted from older clients / FE forms. */
        String emergencyRelationship,
        String emergencyPhone,
        String photoObjectKey,
        Long photoFileId,
        /** FR-EMP-10 offboarding */
        LocalDate exitDate,
        String exitReason,
        String offboardOverrideReason,
        Long reassignReportsTo,
        /** MONTHLY / HOURLY when salary permission is used on create */
        String payType,
        Boolean attachOnboardingChecklist,
        List<String> roleCodes,
        Map<String, Object> customFields
) {
    public String resolvedEmergencyRelation() {
        if (emergencyRelation != null && !emergencyRelation.isBlank()) {
            return emergencyRelation.trim();
        }
        if (emergencyRelationship != null && !emergencyRelationship.isBlank()) {
            return emergencyRelationship.trim();
        }
        return null;
    }
}
