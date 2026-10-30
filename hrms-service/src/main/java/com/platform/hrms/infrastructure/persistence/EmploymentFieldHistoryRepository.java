package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.EmploymentFieldHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmploymentFieldHistoryRepository extends JpaRepository<EmploymentFieldHistory, Long> {
    List<EmploymentFieldHistory> findByEmployeeIdOrderByChangedAtDesc(Long employeeId);
}
