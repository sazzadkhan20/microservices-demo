package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);
    List<AttendanceRecord> findByEmployeeIdOrderByWorkDateDesc(Long employeeId);
    List<AttendanceRecord> findByEmployeeIdAndWorkDateBetween(Long employeeId, LocalDate from, LocalDate to);
    List<AttendanceRecord> findByWorkDate(LocalDate workDate);
}
