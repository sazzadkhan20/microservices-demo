package com.platform.hrms.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Create / update employee. {@code employeeCode} optional on create — auto QII-#### from pattern.
 * Choosing {@code teamId} sets department from team when departmentId omitted (FR-EMP-05).
 * {@code roleCodes} assigns RBAC roles on create (onboarding).
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
        Boolean attachOnboardingChecklist,
        List<String> roleCodes,
        Map<String, Object> customFields
) {
}
