package com.platform.payroll.infrastructure.persistence;

import com.platform.payroll.domain.PayrollRun;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {
    Optional<PayrollRun> findByPeriod(String period);

    /** Row lock for state transitions (guide §5 — competing updates). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PayrollRun r where r.period = :period")
    Optional<PayrollRun> lockByPeriod(String period);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PayrollRun r where r.id = :id")
    Optional<PayrollRun> lockById(Long id);

    Page<PayrollRun> findAllByOrderByPeriodDesc(Pageable pageable);

    /** Runs before a period, oldest first (BR-18 ordering checks). */
    List<PayrollRun> findByPeriodLessThanOrderByPeriodAsc(String period);

    Optional<PayrollRun> findFirstByOrderByPeriodAsc();
}
