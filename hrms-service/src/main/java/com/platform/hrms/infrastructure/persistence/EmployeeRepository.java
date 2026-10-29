package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    java.util.List<Employee> findByTeamIdAndActiveTrue(Long teamId);
    Optional<Employee> findByEmployeeCode(String employeeCode);

    /** Direct reports of a manager (TEAM data scope). */
    java.util.List<Employee> findByManagerId(Long managerId);

    /** Tenant-scoped (database-per-tenant) case-insensitive email uniqueness checks. */
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
