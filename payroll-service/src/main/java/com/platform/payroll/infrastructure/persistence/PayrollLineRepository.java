package com.platform.payroll.infrastructure.persistence;

import com.platform.payroll.domain.PayrollLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PayrollLineRepository extends JpaRepository<PayrollLine, Long> {
    List<PayrollLine> findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(Long runId);

    Optional<PayrollLine> findByRunIdAndEmployeeId(Long runId, Long employeeId);

    @Query("select l from PayrollLine l where l.runId = :runId"
            + " and (:exceptionsOnly = false or l.exception = true)"
            + " and (lower(coalesce(l.employeeName, '')) like :q or lower(coalesce(l.employeeCode, '')) like :q)")
    Page<PayrollLine> search(Long runId, String q, boolean exceptionsOnly, Pageable pageable);

    long countByRunIdAndExceptionTrue(Long runId);

    @Modifying
    @Query("delete from PayrollLine l where l.runId = :runId and l.employeeId not in :keep")
    int deleteByRunIdAndEmployeeIdNotIn(Long runId, List<Long> keep);

    @Modifying
    @Query("delete from PayrollLine l where l.runId = :runId")
    int deleteByRunId(Long runId);
}
