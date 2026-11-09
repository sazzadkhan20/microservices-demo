package com.platform.payroll.infrastructure.persistence;

import com.platform.payroll.domain.FinalizeOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FinalizeOperationRepository extends JpaRepository<FinalizeOperation, Long> {
    Optional<FinalizeOperation> findByOperationId(String operationId);

    Optional<FinalizeOperation> findByOperationKey(String operationKey);

    @Query("select o from FinalizeOperation o where o.status in :statuses and o.createdAt < :before")
    List<FinalizeOperation> findStale(
            @Param("statuses") Collection<String> statuses,
            @Param("before") Instant before);
}
