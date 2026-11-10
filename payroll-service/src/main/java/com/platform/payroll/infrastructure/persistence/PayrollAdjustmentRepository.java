package com.platform.payroll.infrastructure.persistence;

import com.platform.payroll.domain.PayrollAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustment, Long> {
    List<PayrollAdjustment> findByPeriod(String period);

    Optional<PayrollAdjustment> findByPeriodAndEmployeeId(String period, Long employeeId);
}
