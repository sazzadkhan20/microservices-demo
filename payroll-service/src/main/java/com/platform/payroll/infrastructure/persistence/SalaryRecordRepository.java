package com.platform.payroll.infrastructure.persistence;

import com.platform.payroll.domain.SalaryRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface SalaryRecordRepository extends JpaRepository<SalaryRecord, Long> {
    List<SalaryRecord> findByEmployeeIdOrderByEffectiveDateDesc(Long employeeId);

    List<SalaryRecord> findByEmployeeIdInAndEffectiveDateLessThanEqualOrderByEffectiveDateAsc(
            Collection<Long> employeeIds, LocalDate onOrBefore);

    boolean existsByEmployeeIdAndEffectiveDate(Long employeeId, LocalDate effectiveDate);
}
