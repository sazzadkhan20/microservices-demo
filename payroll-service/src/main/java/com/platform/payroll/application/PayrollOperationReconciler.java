package com.platform.payroll.application;

import com.platform.payroll.domain.PayrollOperation;
import com.platform.payroll.domain.PayrollRun;
import com.platform.payroll.infrastructure.persistence.PayrollOperationRepository;
import com.platform.payroll.infrastructure.persistence.PayrollRunRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Reconciles pending finalise/reopen operations (guide §6, §9): re-sends the same keyed command with
 * exponential backoff (capped) and raises an alert (ERROR log, at most once per alert window, plus
 * metrics) when an operation is older than the alert age. Never creates a new deduction: Leave
 * deduplicates by operation key. A periodic sweep reconciles recent months against the Leave ledger.
 *
 * <p>Only one payroll instance runs each job at a time ({@code payroll_job_lease}), so several instances
 * do not re-send or alert in parallel.
 */
@Component
@ConditionalOnProperty(prefix = "platform.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PayrollOperationReconciler {

    private static final Logger log = LoggerFactory.getLogger(PayrollOperationReconciler.class);
    static final String OPERATIONS_LEASE = "payroll-operations";
    static final String RECONCILIATION_LEASE = "payroll-reconciliation";

    private final PayrollOperationRepository operations;
    private final PayrollRunRepository runs;
    private final PayrollFinalizeService finalizeService;
    private final PayrollOperationPolicy policy;
    private final JdbcTemplate jdbc;
    private final Duration firstRetry;
    private final Duration maxBackoff;
    private final Duration operationsLeaseTtl;
    private final Duration sweepLeaseTtl;
    private final int sweepMonths;
    private final String instanceId = ManagementFactory.getRuntimeMXBean().getName() + ":" + UUID.randomUUID();
    private final Map<String, Instant> lastAlert = new ConcurrentHashMap<>();
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();
    private final AtomicLong mismatches = new AtomicLong();

    public PayrollOperationReconciler(
            PayrollOperationRepository operations,
            PayrollRunRepository runs,
            PayrollFinalizeService finalizeService,
            PayrollOperationPolicy policy,
            JdbcTemplate jdbc,
            MeterRegistry meters,
            @Value("${payroll.operations.first-retry-seconds:120}") long firstRetrySeconds,
            @Value("${payroll.operations.max-backoff-seconds:1800}") long maxBackoffSeconds,
            @Value("${payroll.operations.reconcile-interval-ms:60000}") long reconcileIntervalMs,
            @Value("${payroll.operations.reconciliation-sweep-ms:3600000}") long sweepIntervalMs,
            @Value("${payroll.operations.reconciliation-sweep-months:24}") int sweepMonths) {
        this.operations = operations;
        this.runs = runs;
        this.finalizeService = finalizeService;
        this.policy = policy;
        this.jdbc = jdbc;
        this.firstRetry = Duration.ofSeconds(firstRetrySeconds);
        this.maxBackoff = Duration.ofSeconds(maxBackoffSeconds);
        this.operationsLeaseTtl = Duration.ofMillis(Math.max(reconcileIntervalMs * 2, 90_000));
        this.sweepLeaseTtl = Duration.ofMillis(Math.max(sweepIntervalMs / 2, 120_000));
        this.sweepMonths = Math.max(1, sweepMonths);
        Gauge.builder("payroll.operations.pending", pending, AtomicLong::get)
                .description("Payroll finalise/reopen operations waiting for Leave").register(meters);
        Gauge.builder("payroll.operations.oldest.age.seconds", oldestAgeSeconds, AtomicLong::get)
                .description("Age of the oldest pending payroll operation").register(meters);
        Gauge.builder("payroll.reconciliation.mismatches", mismatches, AtomicLong::get)
                .description("Months whose payroll covers do not match the Leave ledger (last sweep)").register(meters);
    }

    @Scheduled(fixedDelayString = "${payroll.operations.reconcile-interval-ms:60000}", initialDelay = 30000)
    public void reconcile() {
        if (!acquire(OPERATIONS_LEASE, operationsLeaseTtl)) {
            return;
        }
        List<PayrollOperation> list = operations.findByStatusOrderByCreatedAtAsc(PayrollOperation.PENDING);
        pending.set(list.size());
        Instant now = Instant.now();
        oldestAgeSeconds.set(list.isEmpty() ? 0 : Duration.between(list.get(0).getCreatedAt(), now).toSeconds());
        Set<String> ids = list.stream().map(PayrollOperation::getOperationId).collect(Collectors.toSet());
        lastAlert.keySet().retainAll(ids);
        for (PayrollOperation op : list) {
            Duration age = Duration.between(op.getCreatedAt(), now);
            if (age.compareTo(policy.alertAge()) > 0 && shouldAlert(op.getOperationId(), now)) {
                log.error("ALERT payroll operation stuck: key={} age={}m attempts={} — check Leave / broker; it can be "
                        + "resolved via POST /api/payroll/operations/{}/resolve (Leave is fenced first)",
                        op.getOperationKey(), age.toMinutes(), op.getAttempts(), op.getOperationId());
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

    /** Guide §6 reconciliation, on a schedule: recent months vs every cover row Leave holds for them. */
    @Scheduled(fixedDelayString = "${payroll.operations.reconciliation-sweep-ms:3600000}", initialDelay = 90000)
    public void sweep() {
        if (!acquire(RECONCILIATION_LEASE, sweepLeaseTtl)) {
            return;
        }
        long bad = 0;
        for (PayrollRun run : runs.findAllByOrderByPeriodDesc(PageRequest.of(0, sweepMonths)).getContent()) {
            try {
                Map<String, Object> r = finalizeService.reconciliation(run.getPeriod());
                if (Boolean.TRUE.equals(r.get("applicable")) && !Boolean.TRUE.equals(r.get("matches"))) {
                    bad++;
                }
            } catch (Exception e) {
                log.warn("Reconciliation sweep skipped {}: {}", run.getPeriod(), e.getMessage());
            }
        }
        mismatches.set(bad);
    }

    boolean shouldAlert(String operationId, Instant now) {
        Instant last = lastAlert.get(operationId);
        if (last != null && Duration.between(last, now).compareTo(policy.alertAge()) < 0) {
            return false;
        }
        lastAlert.put(operationId, now);
        return true;
    }

    /** Takes or renews the named lease for {@code ttl}; false while another instance holds it. */
    boolean acquire(String lease, Duration ttl) {
        try {
            int n = jdbc.update("UPDATE payroll_job_lease SET holder = ?, locked_until = now() + (? * interval '1 millisecond') "
                    + "WHERE name = ? AND (locked_until < now() OR holder = ?)", instanceId, ttl.toMillis(), lease, instanceId);
            return n == 1;
        } catch (Exception e) {
            log.warn("Job lease {} unavailable: {}", lease, e.getMessage());
            return false;
        }
    }

    Duration backoff(int attempts) {
        long factor = 1L << Math.min(10, Math.max(0, attempts - 1));
        Duration d = firstRetry.multipliedBy(factor);
        return d.compareTo(maxBackoff) > 0 ? maxBackoff : d;
    }
}
