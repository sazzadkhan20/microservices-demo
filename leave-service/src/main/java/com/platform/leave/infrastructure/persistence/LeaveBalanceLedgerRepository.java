package com.platform.leave.infrastructure.persistence;

import com.platform.leave.domain.LeaveBalanceLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveBalanceLedgerRepository extends JpaRepository<LeaveBalanceLedger, Long> {
    List<LeaveBalanceLedger> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
}
