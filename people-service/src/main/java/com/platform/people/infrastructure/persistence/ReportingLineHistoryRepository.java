package com.platform.people.infrastructure.persistence;

import com.platform.people.domain.ReportingLineHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportingLineHistoryRepository extends JpaRepository<ReportingLineHistory, Long> {
    List<ReportingLineHistory> findByEmployeeIdOrderByFromDateDesc(Long employeeId);

    Optional<ReportingLineHistory> findFirstByEmployeeIdAndToDateIsNullOrderByFromDateDesc(Long employeeId);
}
