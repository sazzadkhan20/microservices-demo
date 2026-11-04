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
        return repository.findAll().stream().findFirst().orElseGet(this::buildDefault);
    }

    /**
     * Default Qii-pack config — config-driven, no tenant if-else.
     * All knobs can be overridden via PUT /api/payroll/config.
     */
    public PayrollConfigEntity buildDefault() {
        PayrollConfigEntity fallback = new PayrollConfigEntity();
        fallback.setOvertimeMultiplier(new BigDecimal("1.5"));
        fallback.setWeekendMultiplier(new BigDecimal("1.0"));
        fallback.setDeductionRules(List.of(
                // Divisor mode: use schedule working days (HRMS-driven)
                java.util.Map.of("type", "CONFIG", "key", "dayDivisorMode", "value", "SCHEDULE"),
                // Fixed divisor fallback when HRMS unavailable
                java.util.Map.of("type", "CONFIG", "key", "dayDivisor", "value", 30),
                // Absence resolution: consume leave balance first before deducting
                java.util.Map.of("type", "CONFIG", "key", "absenceRule", "value", "BALANCE_FIRST"),
                // Cover order: Casual → Annual
                java.util.Map.of("type", "CONFIG", "key", "absenceCoverOrder",
                        "value", List.of("CASUAL", "ANNUAL")),
                // Unpaid leave is always deducted (FR-PAY-05)
                java.util.Map.of("type", "CONFIG", "key", "unpaidAlwaysDeduct", "value", true),
                // Default daily hours for hourly employee unpaid-leave deduction
                java.util.Map.of("type", "CONFIG", "key", "dailyHours", "value", 8),
                // Qii default: finalize only after calendar month ends
                java.util.Map.of("type", "CONFIG", "key", "periodEndMode", "value", "MONTH_END"),
                java.util.Map.of("type", "CONFIG", "key", "periodEndDay", "value", 31),
                // Late penalty per L/LATE attendance day (0 = track only, no money)
                java.util.Map.of("type", "CONFIG", "key", "latePenaltyPerOccurrence", "value", 200),
                // Notify only the payslip employee on finalize (pack toggle; HRMS PAYSLIP_READY also gates)
                java.util.Map.of("type", "CONFIG", "key", "notifyPayslipFinalized", "value", true),
                java.util.Map.of("type", "CONFIG", "key", "payslipNotificationLink", "value", "/payroll")
        ));
        return fallback;
    }
}
