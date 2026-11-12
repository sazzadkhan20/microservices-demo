package com.platform.payroll.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Timing rules for finalise / reopen operations (guide §6, docs/PAYROLL_SPEC.md §2.3).
 *
 * <ul>
 *   <li>{@code resolveMinAge} — a manual resolve is refused until the automatic re-send has had its
 *       chance (default 5 minutes, beyond the first re-send at 2 minutes).</li>
 *   <li>{@code alertAge} — a pending operation older than this raises an alert.</li>
 * </ul>
 */
@Component
public class PayrollOperationPolicy {

    private final Duration resolveMinAge;
    private final Duration alertAge;

    @Autowired
    public PayrollOperationPolicy(
            @Value("${payroll.operations.resolve-min-age-seconds:300}") long resolveMinAgeSeconds,
            @Value("${payroll.operations.alert-age-minutes:15}") long alertAgeMinutes) {
        this(Duration.ofSeconds(Math.max(0, resolveMinAgeSeconds)), Duration.ofMinutes(Math.max(1, alertAgeMinutes)));
    }

    public PayrollOperationPolicy(Duration resolveMinAge, Duration alertAge) {
        this.resolveMinAge = resolveMinAge;
        this.alertAge = alertAge;
    }

    public Duration resolveMinAge() {
        return resolveMinAge;
    }

    public Duration alertAge() {
        return alertAge;
    }

    /** Earliest moment an operation created at {@code createdAt} may be resolved manually. */
    public Instant resolvableAt(Instant createdAt) {
        return (createdAt == null ? Instant.EPOCH : createdAt).plus(resolveMinAge);
    }
}
