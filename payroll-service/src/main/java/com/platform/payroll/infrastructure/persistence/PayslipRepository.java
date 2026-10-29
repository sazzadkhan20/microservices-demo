package com.platform.payroll.infrastructure.persistence;

import com.platform.payroll.domain.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {
    List<Payslip> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    java.util.Optional<Payslip> findByEmployeeIdAndPeriodLabel(Long employeeId, String periodLabel);
}
