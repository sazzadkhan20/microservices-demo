package com.platform.payroll.infrastructure.persistence;

import com.platform.payroll.domain.FinalizeOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinalizeOperationRepository extends JpaRepository<FinalizeOperation, Long> {
    Optional<FinalizeOperation> findByOperationId(String operationId);

    Optional<FinalizeOperation> findByOperationKey(String operationKey);
}
