package com.platform.payroll.infrastructure.persistence;

import com.platform.payroll.domain.PayslipRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayslipRevisionRepository extends JpaRepository<PayslipRevision, Long> {
    /** Current (not superseded) finalised payslips of one employee, newest month first. */
    List<PayslipRevision> findByEmployeeIdAndSupersededAtIsNullOrderByPeriodDesc(Long employeeId);

    Optional<PayslipRevision> findFirstByEmployeeIdAndPeriodAndSupersededAtIsNull(Long employeeId, String period);

    List<PayslipRevision> findByRunIdAndSupersededAtIsNull(Long runId);

    List<PayslipRevision> findByRunIdOrderByRunRevisionDescEmployeeIdAsc(Long runId);
}
