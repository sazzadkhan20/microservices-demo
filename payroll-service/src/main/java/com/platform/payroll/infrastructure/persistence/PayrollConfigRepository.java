package com.platform.payroll.infrastructure.persistence;

import com.platform.payroll.domain.PayrollConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollConfigRepository extends JpaRepository<PayrollConfigEntity, Long> {
}
