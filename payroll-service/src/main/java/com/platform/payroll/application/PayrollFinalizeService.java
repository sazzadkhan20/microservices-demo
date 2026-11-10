package com.platform.payroll.application;

import com.platform.common.messaging.EventTypes;
import com.platform.common.messaging.PlatformEvent;
import com.platform.common.messaging.PlatformQueues;
import com.platform.messaging.correlation.CorrelationContext;
import com.platform.messaging.outbox.OutboxRelay;
import com.platform.messaging.outbox.OutboxWriter;
import com.platform.payroll.domain.PayrollLine;
import com.platform.payroll.domain.PayrollOperation;
import com.platform.payroll.domain.PayrollRun;
import com.platform.payroll.domain.PayslipRevision;
import com.platform.payroll.infrastructure.FactsClient;
import com.platform.payroll.infrastructure.persistence.PayrollLineRepository;
import com.platform.payroll.infrastructure.persistence.PayrollOperationRepository;
import com.platform.payroll.infrastructure.persistence.PayrollRunRepository;
import com.platform.payroll.infrastructure.persistence.PayslipRevisionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Finalise / reopen as an asynchronous, recoverable operation (guide §6, PAY01/PAY02,
 * docs/PAYROLL_SPEC.md §2.3).
 *
 * <ul>
 *   <li>The run state change, the {@code payroll_operation} row and the outbox command commit atomically.</li>
 *   <li>The Leave result is applied only when operation id, run revision and in-flight status all match;
 *       stale or duplicate results are ignored.</li>
 *   <li>A timeout means "pending", never "deduct again": the reconciler re-sends the same keyed command.</li>
 * </ul>
 */
@Service
public class PayrollFinalizeService {

    private static final Logger log = LoggerFactory.getLogger(PayrollFinalizeService.class);

    private final PayrollRunRepository runs;
    private final PayrollLineRepository lines;
    private final PayrollOperationRepository operations;
    private final PayslipRevisionRepository revisions;
    private final PayrollRunService runService;
    private final PayrollSettingsService settings;
    private final OutboxWriter outbox;
    private final ObjectProvider<OutboxRelay> relay;
    private final AuditService audit;
    private final FactsClient facts;
    private final TransactionTemplate tx;

    public PayrollFinalizeService(PayrollRunRepository runs, PayrollLineRepository lines,
                                  PayrollOperationRepository operations, PayslipRevisionRepository revisions,
                                  PayrollRunService runService, PayrollSettingsService settings,
                                  OutboxWriter outbox, ObjectProvider<OutboxRelay> relay, AuditService audit,
                                  FactsClient facts, PlatformTransactionManager txManager) {
        this.runs = runs;
        this.lines = lines;
        this.operations = operations;
        this.revisions = revisions;
        this.runService = runService;
        this.settings = settings;
        this.outbox = outbox;
        this.relay = relay;
        this.audit = audit;
        this.facts = facts;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ finalise (FR-PAY-12)

    public Map<String, Object> finalizeRun(String period, boolean confirm, Long expectedRevision, Long actor) {
        PayrollClock.parse(period);
        if (!confirm) {
            throw PayrollException.rule("CONFIRM_REQUIRED", "Finalising needs a second confirmation (confirm=true)");
        }
        if (expectedRevision == null) {
            throw PayrollException.rule("REVISION_REQUIRED", "Send the run revision you reviewed");
        }
        PayrollOperation op = tx.execute(status -> {
            PayrollRun run = runs.lockByPeriod(period).orElseThrow(() -> PayrollException.notFound("No run for " + period));
            String key = "FINALIZE:" + period + ":r" + run.getRevision();
            // Idempotent retry of the same request returns the same operation.
            PayrollOperation existing = operations.findByOperationKey(key).orElse(null);
            if (existing != null && run.getRevision() == expectedRevision) {
                return existing;
            }
            if (run.getRevision() != expectedRevision) {
                throw PayrollException.conflict("STALE_REVISION", "The run changed since you reviewed it (revision "
                        + run.getRevision() + ", you reviewed " + expectedRevision + ") — review it again");
            }
            List<PayrollLine> all = lines.findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(run.getId());
            List<String> blockers = runService.finalizeBlockers(run, all);
            if (!blockers.isEmpty()) {
                throw PayrollException.rule("CANNOT_FINALIZE", String.join("; ", blockers), Map.of("blockers", blockers));
            }

            List<Map<String, Object>> coverLines = new ArrayList<>();
            for (PayrollLine l : all) {
                if (!l.getCovers().isEmpty()) {
                    List<Map<String, Object>> covers = new ArrayList<>();
                    for (Map<String, Object> c : l.getCovers()) {
                        covers.add(Map.of("category", c.get("category"), "days", c.get("days")));
                    }
                    coverLines.add(Map.of("employeeId", l.getEmployeeId(), "covers", covers));
                }
            }
            PayrollOperation o = newOperation(run, PayrollOperation.FINALIZE, key, null, actor);
            Map<String, Object> cmd = new LinkedHashMap<>();
            cmd.put("operationId", o.getOperationId());
            cmd.put("operationKey", key);
            cmd.put("action", PayrollOperation.FINALIZE);
            cmd.put("period", period);
            cmd.put("revision", run.getRevision());
            cmd.put("lines", coverLines);
            o.setCommand(cmd);
            operations.save(o);
            run.setStatus(PayrollRun.FINALIZING);
            run.setOperationId(o.getOperationId());
            run.setActionRequiredReason(null);
            runs.save(run);
            audit.record(actor, "PAYROLL_FINALIZE_REQUEST", "payroll_run", period, Map.of("status", PayrollRun.DRAFT),
                    Map.of("status", PayrollRun.FINALIZING, "revision", run.getRevision(), "operationId", o.getOperationId(),
                            "employeesWithCover", coverLines.size()));
            if (coverLines.isEmpty()) {
                completeFinalize(run, o, Map.of("success", true, "local", true));
            } else {
                sendCommand(o);
            }
            return o;
        });
        drainSoon();
        return operationView(operations.findByOperationId(op.getOperationId()).orElse(op));
    }

    // ------------------------------------------------------------------ reopen (FR-PAY-14)

    public Map<String, Object> reopenRun(String period, boolean confirm, String reason, Long actor) {
        PayrollClock.parse(period);
        if (!confirm) {
            throw PayrollException.rule("CONFIRM_REQUIRED", "Reopening needs a second confirmation (confirm=true)");
        }
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw PayrollException.rule("REASON_REQUIRED", "A reason is required to reopen a finalised month");
        }
        PayrollOperation op = tx.execute(status -> {
            PayrollRun run = runs.lockByPeriod(period).orElseThrow(() -> PayrollException.notFound("No run for " + period));
            List<String> blockers = runService.reopenBlockers(run);
            if (!blockers.isEmpty()) {
                throw PayrollException.rule("CANNOT_REOPEN", String.join("; ", blockers), Map.of("blockers", blockers));
            }
            PayrollOperation finalizeOp = operations.findByOperationId(run.getOperationId()).orElse(null);
            long attempt = operations.findByRunIdOrderByCreatedAtDesc(run.getId()).stream()
                    .filter(o -> PayrollOperation.REOPEN.equals(o.getAction()) && o.getRevision() == run.getRevision())
                    .count() + 1;
            String key = "REOPEN:" + period + ":r" + run.getRevision() + ":a" + attempt;
            PayrollOperation o = newOperation(run, PayrollOperation.REOPEN, key,
                    finalizeOp == null ? null : finalizeOp.getOperationKey(), actor);
            Map<String, Object> cmd = new LinkedHashMap<>();
            cmd.put("operationId", o.getOperationId());
            cmd.put("operationKey", key);
            cmd.put("action", PayrollOperation.REOPEN);
            cmd.put("period", period);
            cmd.put("revision", run.getRevision());
            cmd.put("reverseOfKey", o.getReverseOfKey());
            o.setCommand(cmd);
            operations.save(o);
            run.setStatus(PayrollRun.REOPENING);
            run.setOperationId(o.getOperationId());
            run.setReopenReason(why);
            run.setActionRequiredReason(null);
            runs.save(run);
            audit.record(actor, "PAYROLL_REOPEN_REQUEST", "payroll_run", period, Map.of("status", PayrollRun.FINALIZED),
                    Map.of("status", PayrollRun.REOPENING, "reason", why, "operationId", o.getOperationId()));
            boolean hadCover = finalizeOp != null && finalizeOp.getCommand().get("lines") instanceof List<?> l && !l.isEmpty();
            if (!hadCover) {
                completeReopen(run, o, Map.of("success", true, "local", true));
            } else {
                sendCommand(o);
            }
            return o;
        });
        drainSoon();
        return operationView(operations.findByOperationId(op.getOperationId()).orElse(op));
    }

    /** ACTION_REQUIRED (leave rejected the cover; nothing was deducted) → DRAFT, revision + 1. */
    public Map<String, Object> returnToDraft(String period, Long actor) {
        PayrollClock.parse(period);
        tx.executeWithoutResult(status -> {
            PayrollRun run = runs.lockByPeriod(period).orElseThrow(() -> PayrollException.notFound("No run for " + period));
            if (!run.is(PayrollRun.ACTION_REQUIRED)) {
                throw PayrollException.conflict("NOT_ACTION_REQUIRED", period + " is " + run.getStatus());
            }
            run.setStatus(PayrollRun.DRAFT);
            run.setRevision(run.getRevision() + 1);
            runs.save(run);
            audit.record(actor, "PAYROLL_RETURN_TO_DRAFT", "payroll_run", period,
                    Map.of("status", PayrollRun.ACTION_REQUIRED, "reason", String.valueOf(run.getActionRequiredReason())),
                    Map.of("status", PayrollRun.DRAFT, "revision", run.getRevision()));
        });
        return runService.runView(period);
    }

    // ------------------------------------------------------------------ result from Leave

    /** Idempotent: duplicate, stale and wrong-revision results are ignored (guide §6 PAY02). */
    public void onLeaveResult(Map<String, Object> payload) {
        String operationId = String.valueOf(payload.get("operationId"));
        tx.executeWithoutResult(status -> {
            PayrollOperation op = operations.findByOperationId(operationId).orElse(null);
            if (op == null) {
                log.warn("Leave cover result for unknown operation {} — ignored", operationId);
                return;
            }
            if (!PayrollOperation.PENDING.equals(op.getStatus())) {
                log.info("Duplicate leave cover result for {} ({}) — ignored", op.getOperationKey(), op.getStatus());
                return;
            }
            PayrollRun run = runs.lockById(op.getRunId()).orElseThrow();
            String expected = PayrollOperation.FINALIZE.equals(op.getAction()) ? PayrollRun.FINALIZING : PayrollRun.REOPENING;
            if (!op.getOperationId().equals(run.getOperationId()) || run.getRevision() != op.getRevision()
                    || !run.is(expected)) {
                log.warn("Stale leave cover result {} (run {} r{} {}) — ignored", op.getOperationKey(),
                        run.getPeriod(), run.getRevision(), run.getStatus());
                return;
            }
            boolean success = Boolean.TRUE.equals(payload.get("success"));
            if (success) {
                if (PayrollOperation.FINALIZE.equals(op.getAction())) {
                    completeFinalize(run, op, payload);
                } else {
                    completeReopen(run, op, payload);
                }
            } else {
                reject(run, op, payload);
            }
        });
    }

    private void completeFinalize(PayrollRun run, PayrollOperation op, Map<String, Object> result) {
        Instant now = Instant.now();
        boolean notify = settings.load().isNotifyPayslipReady();
        for (PayrollLine l : lines.findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(run.getId())) {
            l.setStatus(PayrollLine.FINALIZED);
            lines.save(l);
            PayslipRevision rev = new PayslipRevision();
            rev.setRunId(run.getId());
            rev.setPeriod(run.getPeriod());
            rev.setEmployeeId(l.getEmployeeId());
            rev.setRunRevision(run.getRevision());
            rev.setCurrency(l.getCurrency());
            rev.setNetPay(l.getNetPay());
            Map<String, Object> snap = PayrollRunService.lineMap(l, true);
            snap.put("period", run.getPeriod());
            snap.put("finalizedAt", now.toString());
            rev.setSnapshot(snap);
            rev.setFinalizedAt(now);
            rev.setFinalizedBy(op.getInitiatedBy());
            rev.setOperationId(op.getOperationId());
            revisions.save(rev);
            if (notify) {
                notifyPayslipReady(run, l, op);
            }
        }
        run.setStatus(PayrollRun.FINALIZED);
        run.setFinalizedAt(now);
        run.setFinalizedBy(op.getInitiatedBy());
        runs.save(run);
        op.setStatus(PayrollOperation.SUCCEEDED);
        op.setResult(new HashMap<>(result));
        op.setCompletedAt(now);
        operations.save(op);
        audit.record(op.getInitiatedBy(), "PAYROLL_FINALIZE", "payroll_run", run.getPeriod(),
                Map.of("status", PayrollRun.FINALIZING),
                Map.of("status", PayrollRun.FINALIZED, "revision", run.getRevision(), "operationId", op.getOperationId()));
    }

    private void completeReopen(PayrollRun run, PayrollOperation op, Map<String, Object> result) {
        Instant now = Instant.now();
        for (PayslipRevision rev : revisions.findByRunIdAndSupersededAtIsNull(run.getId())) {
            rev.setSupersededAt(now);
            rev.setSupersededByOperation(op.getOperationId());
            revisions.save(rev);
        }
        for (PayrollLine l : lines.findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(run.getId())) {
            l.setStatus(PayrollLine.DRAFT);
            lines.save(l);
        }
        run.setStatus(PayrollRun.DRAFT);
        run.setRevision(run.getRevision() + 1);
        run.setReopenedAt(now);
        run.setReopenedBy(op.getInitiatedBy());
        run.setFinalizedAt(null);
        run.setFinalizedBy(null);
        runs.save(run);
        op.setStatus(PayrollOperation.SUCCEEDED);
        op.setResult(new HashMap<>(result));
        op.setCompletedAt(now);
        operations.save(op);
        audit.record(op.getInitiatedBy(), "PAYROLL_REOPEN", "payroll_run", run.getPeriod(),
                Map.of("status", PayrollRun.REOPENING),
                Map.of("status", PayrollRun.DRAFT, "revision", run.getRevision(), "reason", String.valueOf(run.getReopenReason()),
                        "operationId", op.getOperationId()));
    }

    private void reject(PayrollRun run, PayrollOperation op, Map<String, Object> result) {
        String reason = String.valueOf(result.getOrDefault("errorMessage", "Leave rejected the operation"));
        op.setStatus(PayrollOperation.REJECTED);
        op.setError(reason);
        op.setResult(new HashMap<>(result));
        op.setCompletedAt(Instant.now());
        operations.save(op);
        if (PayrollOperation.FINALIZE.equals(op.getAction())) {
            // Business rejection stays visible and recoverable; nothing was deducted.
            run.setStatus(PayrollRun.ACTION_REQUIRED);
            run.setActionRequiredReason("Finalise rejected by Leave: " + reason);
        } else {
            // Reversal refused: the month is still finalised and its leave adjustments remain.
            run.setStatus(PayrollRun.FINALIZED);
            run.setOperationId(previousFinalizeOperationId(run));
            run.setActionRequiredReason("Reopen rejected by Leave: " + reason);
        }
        runs.save(run);
        audit.record(op.getInitiatedBy(), "PAYROLL_" + op.getAction() + "_REJECTED", "payroll_run", run.getPeriod(),
                null, Map.of("reason", reason, "operationId", op.getOperationId()));
    }

    private String previousFinalizeOperationId(PayrollRun run) {
        return operations.findByRunIdOrderByCreatedAtDesc(run.getId()).stream()
                .filter(o -> PayrollOperation.FINALIZE.equals(o.getAction()) && PayrollOperation.SUCCEEDED.equals(o.getStatus()))
                .map(PayrollOperation::getOperationId).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ recovery (guide §6)

    /** Re-send the same keyed command (Leave dedupes by operation key). */
    public void resend(PayrollOperation op) {
        tx.executeWithoutResult(status -> {
            PayrollOperation fresh = operations.findByOperationId(op.getOperationId()).orElseThrow();
            if (!PayrollOperation.PENDING.equals(fresh.getStatus())) {
                return;
            }
            fresh.setAttempts(fresh.getAttempts() + 1);
            fresh.setLastSentAt(Instant.now());
            operations.save(fresh);
            sendCommand(fresh);
        });
        drainSoon();
    }

    /** Manual resolution with reason and audit (guide §6, §10). */
    public Map<String, Object> resolve(String operationId, String outcome, String reason, Long actor) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw PayrollException.rule("REASON_REQUIRED", "A reason is required to resolve an operation manually");
        }
        if (!"SUCCEEDED".equals(outcome) && !"REJECTED".equals(outcome)) {
            throw PayrollException.rule("INVALID_OUTCOME", "outcome must be SUCCEEDED (Leave applied it) or REJECTED (Leave did not)");
        }
        tx.executeWithoutResult(status -> {
            PayrollOperation op = operations.findByOperationId(operationId)
                    .orElseThrow(() -> PayrollException.notFound("Operation not found: " + operationId));
            if (!PayrollOperation.PENDING.equals(op.getStatus())) {
                throw PayrollException.conflict("NOT_PENDING", "Operation is " + op.getStatus());
            }
            PayrollRun run = runs.lockById(op.getRunId()).orElseThrow();
            Map<String, Object> result = Map.of("success", "SUCCEEDED".equals(outcome), "manual", true,
                    "errorMessage", "Manually resolved: " + why);
            if ("SUCCEEDED".equals(outcome)) {
                if (PayrollOperation.FINALIZE.equals(op.getAction())) {
                    completeFinalize(run, op, result);
                } else {
                    completeReopen(run, op, result);
                }
            } else {
                reject(run, op, result);
            }
            op.setResolvedBy(actor);
            op.setResolveReason(why);
            operations.save(op);
            audit.record(actor, "PAYROLL_OPERATION_RESOLVE", "payroll_operation", operationId,
                    Map.of("status", PayrollOperation.PENDING), Map.of("outcome", outcome, "reason", why));
        });
        return operationView(operations.findByOperationId(operationId).orElseThrow());
    }

    /** Payroll's covered days per employee/category vs the Leave ledger for the finalise key. */
    public Map<String, Object> reconciliation(String period) {
        PayrollRun run = runs.findByPeriod(period).orElseThrow(() -> PayrollException.notFound("No run for " + period));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period);
        out.put("status", run.getStatus());
        PayrollOperation op = run.getOperationId() == null ? null
                : operations.findByOperationId(run.getOperationId()).orElse(null);
        if (!run.is(PayrollRun.FINALIZED) || op == null || !PayrollOperation.FINALIZE.equals(op.getAction())) {
            out.put("applicable", false);
            out.put("message", "Reconciliation applies to finalised months");
            return out;
        }
        Map<String, BigDecimal> expected = new LinkedHashMap<>();
        for (PayrollLine l : lines.findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(run.getId())) {
            for (Map<String, Object> c : l.getCovers()) {
                expected.merge(l.getEmployeeId() + ":" + c.get("category"), new BigDecimal(String.valueOf(c.get("days"))), BigDecimal::add);
            }
        }
        Map<String, Object> ledger = facts.coverLedger(op.getOperationKey());
        Map<String, BigDecimal> actual = new LinkedHashMap<>();
        if (ledger.get("rows") instanceof List<?> rows) {
            for (Object o : rows) {
                if (o instanceof Map<?, ?> m) {
                    actual.merge(m.get("employeeId") + ":" + m.get("category"),
                            new BigDecimal(String.valueOf(m.get("days"))), BigDecimal::add);
                }
            }
        }
        List<Map<String, Object>> diffs = new ArrayList<>();
        java.util.Set<String> keys = new java.util.TreeSet<>(expected.keySet());
        keys.addAll(actual.keySet());
        for (String k : keys) {
            BigDecimal e = expected.getOrDefault(k, BigDecimal.ZERO);
            BigDecimal a = actual.getOrDefault(k, BigDecimal.ZERO);
            if (e.compareTo(a) != 0) {
                diffs.add(Map.of("key", k, "payrollDays", e, "leaveLedgerDays", a));
            }
        }
        out.put("applicable", true);
        out.put("operationKey", op.getOperationKey());
        out.put("matches", diffs.isEmpty());
        out.put("expected", expected);
        out.put("ledger", actual);
        out.put("differences", diffs);
        if (!diffs.isEmpty()) {
            log.error("PAYROLL RECONCILIATION MISMATCH period={} key={} diffs={}", period, op.getOperationKey(), diffs);
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private PayrollOperation newOperation(PayrollRun run, String action, String key, String reverseOf, Long actor) {
        PayrollOperation o = new PayrollOperation();
        o.setOperationId(UUID.randomUUID().toString());
        o.setOperationKey(key);
        o.setRunId(run.getId());
        o.setPeriod(run.getPeriod());
        o.setRevision(run.getRevision());
        o.setAction(action);
        o.setReverseOfKey(reverseOf);
        o.setCorrelationId(CorrelationContext.getOrCreate());
        o.setInitiatedBy(actor);
        return o;
    }

    private void sendCommand(PayrollOperation op) {
        PlatformEvent event = PlatformEvent.of(EventTypes.PAYROLL_LEAVE_COVER_COMMAND, op.getPeriod(), op.getRevision(),
                op.getCorrelationId() == null ? CorrelationContext.getOrCreate() : op.getCorrelationId(), op.getCommand());
        outbox.enqueue(event, PlatformQueues.PAYROLL_LEAVE_COVER_CMD);
    }

    private void notifyPayslipReady(PayrollRun run, PayrollLine l, PayrollOperation op) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeId", l.getEmployeeId());
        payload.put("title", "Payslip ready");
        payload.put("body", "Your payslip for " + run.getPeriod() + " is ready.");
        payload.put("category", "PAYROLL");
        payload.put("dedupeKey", "payslip-ready:" + run.getPeriod() + ":r" + run.getRevision() + ":" + l.getEmployeeId());
        payload.put("linkUrl", "/payroll?tab=mypay&period=" + run.getPeriod());
        outbox.enqueue(PlatformEvent.of(EventTypes.NOTIFICATION_CREATE, run.getPeriod() + ":" + l.getEmployeeId(),
                run.getRevision(), op.getCorrelationId(), payload), PlatformQueues.NOTIFICATION_CREATE);
    }

    private void drainSoon() {
        OutboxRelay r = relay.getIfAvailable();
        if (r == null) {
            return;
        }
        Runnable drain = () -> {
            try {
                r.drainBatch();
            } catch (Exception e) {
                log.debug("Outbox drain: {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    drain.run();
                }
            });
        } else {
            drain.run();
        }
    }

    static Map<String, Object> operationView(PayrollOperation op) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("operationId", op.getOperationId());
        m.put("operationKey", op.getOperationKey());
        m.put("period", op.getPeriod());
        m.put("revision", op.getRevision());
        m.put("action", op.getAction());
        m.put("status", op.getStatus());
        m.put("attempts", op.getAttempts());
        m.put("error", op.getError());
        m.put("initiatedBy", op.getInitiatedBy());
        m.put("resolvedBy", op.getResolvedBy());
        m.put("resolveReason", op.getResolveReason());
        m.put("createdAt", op.getCreatedAt() == null ? null : op.getCreatedAt().toString());
        m.put("completedAt", op.getCompletedAt() == null ? null : op.getCompletedAt().toString());
        m.put("statusUrl", "/api/payroll/operations/" + op.getOperationId());
        return m;
    }

    public Map<String, Object> operation(String operationId) {
        return operationView(operations.findByOperationId(operationId)
                .orElseThrow(() -> PayrollException.notFound("Operation not found: " + operationId)));
    }

    public List<Map<String, Object>> pendingOperations() {
        return operations.findByStatusOrderByCreatedAtAsc(PayrollOperation.PENDING).stream()
                .map(PayrollFinalizeService::operationView).toList();
    }
}
