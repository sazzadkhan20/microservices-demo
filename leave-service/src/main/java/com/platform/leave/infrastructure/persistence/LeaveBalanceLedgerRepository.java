package com.platform.leave.infrastructure.persistence;

import com.platform.leave.domain.LeaveBalanceLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceLedgerRepository extends JpaRepository<LeaveBalanceLedger, Long> {
    List<LeaveBalanceLedger> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    Optional<LeaveBalanceLedger> findByOperationKey(String operationKey);

    List<LeaveBalanceLedger> findByOperationKeyStartingWithOrderByIdAsc(String prefix);
}
