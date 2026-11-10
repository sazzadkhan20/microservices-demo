package com.platform.payroll.infrastructure.persistence;

import com.platform.payroll.domain.PayrollOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PayrollOperationRepository extends JpaRepository<PayrollOperation, Long> {
    Optional<PayrollOperation> findByOperationId(String operationId);

    Optional<PayrollOperation> findByOperationKey(String operationKey);

    List<PayrollOperation> findByStatusAndLastSentAtBefore(String status, Instant before);

    List<PayrollOperation> findByStatusOrderByCreatedAtAsc(String status);

    List<PayrollOperation> findByRunIdOrderByCreatedAtDesc(Long runId);
}
