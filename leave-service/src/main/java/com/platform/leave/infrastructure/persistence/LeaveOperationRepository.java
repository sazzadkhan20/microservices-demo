package com.platform.leave.infrastructure.persistence;

import com.platform.leave.domain.LeaveOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeaveOperationRepository extends JpaRepository<LeaveOperation, Long> {
    Optional<LeaveOperation> findByOperationKey(String operationKey);
}
