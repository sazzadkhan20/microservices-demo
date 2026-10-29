package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.AttendanceCorrection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, Long> {
    List<AttendanceCorrection> findAllByOrderByCreatedAtDesc();
    List<AttendanceCorrection> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
    boolean existsByEmployeeIdAndWorkDateAndStatus(Long employeeId, LocalDate workDate, String status);
}
