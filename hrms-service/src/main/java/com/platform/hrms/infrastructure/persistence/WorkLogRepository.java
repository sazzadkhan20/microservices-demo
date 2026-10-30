package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.WorkLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WorkLogRepository extends JpaRepository<WorkLog, Long> {
    List<WorkLog> findByEmployeeIdOrderByWorkDateDesc(Long employeeId);

    List<WorkLog> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    List<WorkLog> findByEmployeeIdAndWorkDateBetween(Long employeeId, LocalDate from, LocalDate to);
}
