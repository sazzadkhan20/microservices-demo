package com.platform.payroll.infrastructure.persistence;

import com.platform.payroll.domain.PayrollSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollSettingsRepository extends JpaRepository<PayrollSettings, Short> {
}
