package com.platform.payroll.infrastructure.persistence;

import com.platform.payroll.domain.PayrollOperation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PayrollOperationRepository extends JpaRepository<PayrollOperation, Long> {
    Optional<PayrollOperation> findByOperationId(String operationId);

    Optional<PayrollOperation> findByOperationKey(String operationKey);

    /** Run id without loading the entity, so the run can be locked before the operation is read. */
    @Query("select o.runId from PayrollOperation o where o.operationId = :operationId")
    Optional<Long> findRunIdByOperationId(String operationId);

    List<PayrollOperation> findByStatusAndLastSentAtBefore(String status, Instant before);

    List<PayrollOperation> findByStatusOrderByCreatedAtAsc(String status);

    List<PayrollOperation> findByRunIdOrderByCreatedAtDesc(Long runId);

    Page<PayrollOperation> findByRunIdOrderByCreatedAtDesc(Long runId, Pageable pageable);

    /** Month-scoped leave sync: run id + period (same month) newest first. */
    Page<PayrollOperation> findByRunIdAndPeriodOrderByCreatedAtDesc(Long runId, String period, Pageable pageable);
}
