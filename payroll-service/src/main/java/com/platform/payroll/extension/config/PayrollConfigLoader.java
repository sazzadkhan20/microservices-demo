package com.platform.payroll.extension.config;

import com.platform.payroll.domain.PayrollConfigEntity;
import com.platform.payroll.infrastructure.persistence.PayrollConfigRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
public class PayrollConfigLoader {

    private final PayrollConfigRepository repository;

    public PayrollConfigLoader(PayrollConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PayrollConfigEntity loadOrDefault() {
        return repository.findAll().stream().findFirst().orElseGet(() -> {
            PayrollConfigEntity fallback = new PayrollConfigEntity();
            fallback.setOvertimeMultiplier(new BigDecimal("1.5"));
            fallback.setWeekendMultiplier(new BigDecimal("1.0"));
            fallback.setDeductionRules(List.of());
            return fallback;
        });
    }
}
