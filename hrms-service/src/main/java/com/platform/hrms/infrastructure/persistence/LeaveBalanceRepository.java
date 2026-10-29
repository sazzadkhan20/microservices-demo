package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {
    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeCodeAndYear(Long employeeId, String leaveTypeCode, int year);

    List<LeaveBalance> findByEmployeeIdAndYear(Long employeeId, int year);
}
