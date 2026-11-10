package com.platform.payroll.application;

import com.platform.payroll.domain.PayrollOperation;
import com.platform.payroll.infrastructure.persistence.PayrollOperationRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reconciles pending finalise/reopen operations (guide §6, §9): re-sends the same keyed command with
 * exponential backoff (capped), and raises an alert (ERROR log + metrics) when an operation is older
 * than the age threshold. Never creates a new deduction: Leave deduplicates by operation key.
 */
@Component
@ConditionalOnProperty(prefix = "platform.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PayrollOperationReconciler {

    private static final Logger log = LoggerFactory.getLogger(PayrollOperationReconciler.class);

    private final PayrollOperationRepository operations;
    private final PayrollFinalizeService finalizeService;
    private final Duration firstRetry;
    private final Duration maxBackoff;
    private final Duration alertAge;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();

    public PayrollOperationReconciler(
            PayrollOperationRepository operations,
            PayrollFinalizeService finalizeService,
            MeterRegistry meters,
            @Value("${payroll.operations.first-retry-seconds:120}") long firstRetrySeconds,
            @Value("${payroll.operations.max-backoff-seconds:1800}") long maxBackoffSeconds,
            @Value("${payroll.operations.alert-age-minutes:15}") long alertAgeMinutes) {
        this.operations = operations;
        this.finalizeService = finalizeService;
        this.firstRetry = Duration.ofSeconds(firstRetrySeconds);
        this.maxBackoff = Duration.ofSeconds(maxBackoffSeconds);
        this.alertAge = Duration.ofMinutes(alertAgeMinutes);
        Gauge.builder("payroll.operations.pending", pending, AtomicLong::get)
                .description("Payroll finalise/reopen operations waiting for Leave").register(meters);
        Gauge.builder("payroll.operations.oldest.age.seconds", oldestAgeSeconds, AtomicLong::get)
                .description("Age of the oldest pending payroll operation").register(meters);
    }

    @Scheduled(fixedDelayString = "${payroll.operations.reconcile-interval-ms:60000}", initialDelay = 30000)
    public void reconcile() {
        List<PayrollOperation> list = operations.findByStatusOrderByCreatedAtAsc(PayrollOperation.PENDING);
        pending.set(list.size());
        Instant now = Instant.now();
        oldestAgeSeconds.set(list.isEmpty() ? 0 : Duration.between(list.get(0).getCreatedAt(), now).toSeconds());
        for (PayrollOperation op : list) {
            Duration age = Duration.between(op.getCreatedAt(), now);
            if (age.compareTo(alertAge) > 0) {
                log.error("ALERT payroll operation stuck: key={} age={}m attempts={} — check Leave / broker, "
                        + "then resolve via POST /api/payroll/operations/{}/resolve", op.getOperationKey(),
                        age.toMinutes(), op.getAttempts(), op.getOperationId());
            }
            if (Duration.between(op.getLastSentAt(), now).compareTo(backoff(op.getAttempts())) >= 0) {
                try {
                    log.warn("Re-sending payroll operation {} (attempt {})", op.getOperationKey(), op.getAttempts() + 1);
                    finalizeService.resend(op);
                } catch (Exception e) {
                    log.warn("Re-send failed for {}: {}", op.getOperationKey(), e.getMessage());
                }
            }
        }
    }

    Duration backoff(int attempts) {
        long factor = 1L << Math.min(10, Math.max(0, attempts - 1));
        Duration d = firstRetry.multipliedBy(factor);
        return d.compareTo(maxBackoff) > 0 ? maxBackoff : d;
    }
}
