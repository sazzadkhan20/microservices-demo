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
import com.platform.payroll.engine.PayrollInputs;
import com.platform.payroll.infrastructure.FactsClient;
import com.platform.payroll.infrastructure.persistence.PayrollLineRepository;
import com.platform.payroll.infrastructure.persistence.PayrollOperationRepository;
import com.platform.payroll.infrastructure.persistence.PayrollRunRepository;
import com.platform.payroll.infrastructure.persistence.PayslipRevisionRepository;
import io.micrometer.core.instrument.Metrics;
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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Finalise / reopen as an asynchronous, recoverable operation (guide §6, PAY01/PAY02,
 * docs/PAYROLL_SPEC.md §2.3).
 *
 * <ul>
 *   <li>The run state change, the {@code payroll_operation} row and the outbox command commit atomically.</li>
 *   <li>What is finalised is what current facts give: the draft is recomputed from fresh facts first.</li>
 *   <li>The Leave result is applied only when operation id, run revision and in-flight status all match;
 *       stale or duplicate results are ignored, and a result that contradicts a closed operation alerts.</li>
 *   <li>A timeout means "pending", never "deduct again": the reconciler re-sends the same keyed command.</li>
 *   <li>A manual resolve never trusts the operator's word: it fences the key in Leave and applies the
 *       outcome Leave actually holds, so a late command can never deduct or restore leave twice.</li>
 * </ul>
 */
@Service
public class PayrollFinalizeService {

    private static final Logger log = LoggerFactory.getLogger(PayrollFinalizeService.class);
    static final String RESULT_MISMATCH_METRIC = "payroll.operations.result.mismatch";

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
    private final PayrollOperationPolicy policy;
    private final TransactionTemplate tx;

    public PayrollFinalizeService(PayrollRunRepository runs, PayrollLineRepository lines,
                                  PayrollOperationRepository operations, PayslipRevisionRepository revisions,
                                  PayrollRunService runService, PayrollSettingsService settings,
                                  OutboxWriter outbox, ObjectProvider<OutboxRelay> relay, AuditService audit,
                                  FactsClient facts, PayrollOperationPolicy policy, PlatformTransactionManager txManager) {
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
        this.policy = policy;
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
        // Idempotent retry of the same request (double click, client retry) returns the same operation
        // without re-reading any facts.
        PayrollRun current = runs.findByPeriod(period).orElseThrow(() -> PayrollException.notFound("No run for " + period));
        if (current.getRevision() == expectedRevision) {
            PayrollOperation replay = operations.findByOperationKey(finalizeKey(period, expectedRevision)).orElse(null);
            if (replay != null) {
                return operationView(replay, policy);
            }
        }
        // Current facts, fetched outside the transaction; a provider outage refuses the finalise (503).
        List<PayrollInputs.Line> fresh = current.is(PayrollRun.DRAFT) && current.getRevision() == expectedRevision
                ? runService.freshInputs(period) : null;

        PayrollOperation op = tx.execute(status -> {
            PayrollRun run = runs.lockByPeriod(period).orElseThrow(() -> PayrollException.notFound("No run for " + period));
            String key = finalizeKey(period, run.getRevision());
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
            List<String> drift = runService.factsDrift(period, all, fresh == null ? List.of() : fresh);
            if (!drift.isEmpty()) {
                List<String> shown = drift.size() > 20 ? new ArrayList<>(drift.subList(0, 20)) : drift;
                if (drift.size() > 20) {
                    shown.add("… and " + (drift.size() - 20) + " more");
                }
                throw new PayrollException(409, "FACTS_CHANGED", "Attendance, leave, salary or rules changed since this draft "
                        + "was calculated (" + drift.size() + " row(s)) — recalculate and review it again",
                        Map.of("changes", shown));
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
        return operationView(operations.findByOperationId(op.getOperationId()).orElse(op), policy);
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
        if (why.length() > 512) {
            throw PayrollException.rule("REASON_TOO_LONG", "Reason is too long (max 512)");
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
        return operationView(operations.findByOperationId(op.getOperationId()).orElse(op), policy);
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
            Long runId = operations.findRunIdByOperationId(operationId).orElse(null);
            if (runId == null) {
                log.warn("Leave cover result for unknown operation {} — ignored", operationId);
                return;
            }
            // Same lock order as finalise / reopen / resend / resolve: run row first, then the operation
            // (read after the lock, so its state is current).
            PayrollRun run = runs.lockById(runId).orElseThrow();
            PayrollOperation op = operations.findByOperationId(operationId).orElseThrow();
            boolean success = Boolean.TRUE.equals(payload.get("success"));
            if (!PayrollOperation.PENDING.equals(op.getStatus())) {
                boolean closedAsSuccess = PayrollOperation.SUCCEEDED.equals(op.getStatus());
                if (closedAsSuccess != success && !PayrollOperation.RESOLVED.equals(op.getStatus())) {
                    // Never expected (manual resolve fences Leave first): Leave and payroll disagree.
                    Metrics.counter(RESULT_MISMATCH_METRIC).increment();
                    log.error("ALERT payroll/leave outcome mismatch: key={} payroll={} leave={} — run the reconciliation "
                            + "for {}", op.getOperationKey(), op.getStatus(), payload.get("status"), op.getPeriod());
                } else {
                    log.info("Duplicate leave cover result for {} ({}) — ignored", op.getOperationKey(), op.getStatus());
                }
                return;
            }
            if (!matchesRun(run, op)) {
                log.warn("Stale leave cover result {} (run {} r{} {}) — ignored", op.getOperationKey(),
                        run.getPeriod(), run.getRevision(), run.getStatus());
                return;
            }
            settle(run, op, success, payload);
        });
    }

    private static boolean matchesRun(PayrollRun run, PayrollOperation op) {
        String expected = PayrollOperation.FINALIZE.equals(op.getAction()) ? PayrollRun.FINALIZING : PayrollRun.REOPENING;
        return op.getOperationId().equals(run.getOperationId()) && run.getRevision() == op.getRevision() && run.is(expected);
    }

    private void settle(PayrollRun run, PayrollOperation op, boolean success, Map<String, Object> result) {
        if (success) {
            if (PayrollOperation.FINALIZE.equals(op.getAction())) {
                completeFinalize(run, op, result);
            } else {
                completeReopen(run, op, result);
            }
        } else {
            reject(run, op, result);
        }
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
            // Inputs behind the numbers (attendance days, leave, salary records, balances, rules), so a
            // superseded payslip can still be explained after the month is recalculated (FR-PAY-15, §10).
            snap.put("facts", l.getFacts() == null ? Map.of() : new LinkedHashMap<>(l.getFacts()));
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
        run.setActionRequiredReason(null);
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
        run.setActionRequiredReason(null);
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

    /**
     * Re-send the same keyed command (Leave dedupes by operation key). Takes the run lock like
     * {@link #onLeaveResult}, and the operation's version guards the write, so a re-send can never
     * overwrite a result that arrived at the same moment.
     */
    public void resend(PayrollOperation op) {
        tx.executeWithoutResult(status -> {
            runs.lockById(op.getRunId()).orElseThrow();
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

    /**
     * Manual resolution of a stuck operation (guide §6, §10). The operator does not decide what Leave did:
     * payroll fences the key in Leave first (Leave records CANCELLED if it never processed it, so a command
     * still in flight can no longer change any balance) and then applies the outcome Leave holds.
     * {@code expectedOutcome} (optional) is only compared and reported.
     */
    public Map<String, Object> resolve(String operationId, String expectedOutcome, String reason, Long actor) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw PayrollException.rule("REASON_REQUIRED", "A reason is required to resolve an operation manually");
        }
        if (why.length() > 500) {
            throw PayrollException.rule("REASON_TOO_LONG", "Reason is too long (max 500)");
        }
        String expected = expectedOutcome == null || expectedOutcome.isBlank() || "null".equals(expectedOutcome)
                ? null : expectedOutcome.trim().toUpperCase(java.util.Locale.ROOT);
        if (expected != null && !"SUCCEEDED".equals(expected) && !"REJECTED".equals(expected)) {
            throw PayrollException.rule("INVALID_OUTCOME", "outcome, if given, must be SUCCEEDED or REJECTED");
        }
        PayrollOperation op = operations.findByOperationId(operationId)
                .orElseThrow(() -> PayrollException.notFound("Operation not found: " + operationId));
        if (!PayrollOperation.PENDING.equals(op.getStatus())) {
            throw PayrollException.conflict("NOT_PENDING", "Operation is " + op.getStatus());
        }
        Instant resolvableAt = policy.resolvableAt(op.getCreatedAt());
        if (Instant.now().isBefore(resolvableAt)) {
            throw new PayrollException(409, "TOO_EARLY", "The automatic re-send may still deliver this operation — "
                    + "it can be resolved manually from " + resolvableAt, Map.of("resolvableAt", resolvableAt.toString()));
        }

        // Outside the transaction: fence the key in Leave and read the outcome Leave holds (503 if down).
        Map<String, Object> leave = facts.fenceLeaveOperation(op.getOperationKey(), "Resolved by employee " + actor + ": " + why);
        String leaveStatus = String.valueOf(leave.get("status"));
        if (!Boolean.TRUE.equals(leave.get("known"))) {
            throw new FactsClient.FactsUnavailableException("Leave did not confirm the fence for " + op.getOperationKey());
        }
        boolean success = Boolean.TRUE.equals(leave.get("success"));
        Map<String, Object> result = new LinkedHashMap<>();
        if (leave.get("result") instanceof Map<?, ?> m) {
            m.forEach((k, v) -> result.put(String.valueOf(k), v));
        }
        result.put("success", success);
        result.put("leaveStatus", leaveStatus);
        result.put("manual", true);
        result.putIfAbsent("errorMessage", "Leave status " + leaveStatus);

        String outcome = success ? PayrollOperation.SUCCEEDED : PayrollOperation.REJECTED;
        tx.executeWithoutResult(status -> {
            PayrollRun run = runs.lockById(op.getRunId()).orElseThrow();
            PayrollOperation fresh = operations.findByOperationId(operationId).orElseThrow();
            if (!PayrollOperation.PENDING.equals(fresh.getStatus())) {
                return; // Leave's own result arrived meanwhile and settled it — same outcome by construction.
            }
            if (matchesRun(run, fresh)) {
                settle(run, fresh, success, result);
            } else {
                // The run has moved on (should not happen): close the operation without touching the run.
                fresh.setStatus(PayrollOperation.RESOLVED);
                fresh.setResult(new HashMap<>(result));
                fresh.setError("Run " + run.getPeriod() + " is " + run.getStatus() + " r" + run.getRevision()
                        + "; Leave outcome " + leaveStatus + " recorded only");
                fresh.setCompletedAt(Instant.now());
                if (success) {
                    log.error("ALERT resolved operation {} was applied by Leave but no longer matches run {} — "
                            + "check the reconciliation", fresh.getOperationKey(), run.getPeriod());
                }
            }
            fresh.setResolvedBy(actor);
            fresh.setResolveReason(why);
            operations.save(fresh);
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("outcome", fresh.getStatus());
            after.put("leaveStatus", leaveStatus);
            after.put("reason", why);
            if (expected != null) {
                after.put("operatorExpected", expected);
            }
            audit.record(actor, "PAYROLL_OPERATION_RESOLVE", "payroll_operation", operationId,
                    Map.of("status", PayrollOperation.PENDING), after);
        });
        Map<String, Object> view = operationView(operations.findByOperationId(operationId).orElseThrow(), policy);
        view.put("leaveStatus", leaveStatus);
        if (expected != null && !expected.equals(outcome)) {
            view.put("note", "You expected " + expected + " but Leave " + (success ? "had applied it" : "had not applied it")
                    + " — Leave's outcome was applied");
        }
        return view;
    }

    /**
     * Payroll's covered days vs every cover row Leave holds for the month, across all finalise keys
     * (guide §6). A finalised month must hold exactly its line covers under its current finalise key; a
     * draft month must hold none. Rows held under any other key are reported as orphans.
     */
    public Map<String, Object> reconciliation(String period) {
        PayrollClock.parse(period);
        PayrollRun run = runs.findByPeriod(period).orElseThrow(() -> PayrollException.notFound("No run for " + period));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period);
        out.put("status", run.getStatus());
        out.put("revision", run.getRevision());
        if (run.is(PayrollRun.FINALIZING) || run.is(PayrollRun.REOPENING)) {
            out.put("applicable", false);
            out.put("message", "An operation is in flight — reconcile after it completes");
            return out;
        }
        PayrollOperation op = run.getOperationId() == null ? null
                : operations.findByOperationId(run.getOperationId()).orElse(null);
        String currentKey = run.is(PayrollRun.FINALIZED) && op != null && PayrollOperation.FINALIZE.equals(op.getAction())
                ? op.getOperationKey() : null;

        Map<String, BigDecimal> expected = new TreeMap<>();
        if (run.is(PayrollRun.FINALIZED)) {
            for (PayrollLine l : lines.findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(run.getId())) {
                for (Map<String, Object> c : l.getCovers()) {
                    expected.merge(l.getEmployeeId() + ":" + c.get("category"),
                            new BigDecimal(String.valueOf(c.get("days"))), BigDecimal::add);
                }
            }
        }
        Map<String, Object> ledger = facts.coverLedgerForPeriod(period);
        Map<String, BigDecimal> held = new TreeMap<>();
        Map<String, BigDecimal> orphanByKey = new TreeMap<>();
        List<Map<String, Object>> orphanRows = new ArrayList<>();
        if (ledger.get("rows") instanceof List<?> rows) {
            for (Object o : rows) {
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                BigDecimal days = new BigDecimal(String.valueOf(m.get("heldDays")));
                if (days.signum() == 0) {
                    continue;
                }
                String key = String.valueOf(m.get("operationKey"));
                String empCat = m.get("employeeId") + ":" + m.get("category");
                held.merge(empCat, days, BigDecimal::add);
                if (!key.equals(currentKey)) {
                    orphanByKey.merge(key, days, BigDecimal::add);
                    orphanRows.add(Map.of("operationKey", key, "employeeId", m.get("employeeId"),
                            "category", m.get("category"), "days", days));
                }
            }
        }
        List<Map<String, Object>> diffs = new ArrayList<>();
        TreeSet<String> keys = new TreeSet<>(expected.keySet());
        keys.addAll(held.keySet());
        for (String k : keys) {
            BigDecimal e = expected.getOrDefault(k, BigDecimal.ZERO);
            BigDecimal a = held.getOrDefault(k, BigDecimal.ZERO);
            if (e.compareTo(a) != 0) {
                diffs.add(Map.of("key", k, "payrollDays", e, "leaveLedgerDays", a));
            }
        }
        boolean matches = diffs.isEmpty() && orphanRows.isEmpty();
        out.put("applicable", true);
        out.put("operationKey", currentKey);
        out.put("matches", matches);
        out.put("expected", expected);
        out.put("ledger", held);
        out.put("differences", diffs);
        out.put("orphans", orphanRows);
        out.put("orphanKeys", orphanByKey);
        if (!matches) {
            log.error("PAYROLL RECONCILIATION MISMATCH period={} status={} key={} diffs={} orphans={}", period,
                    run.getStatus(), currentKey, diffs, orphanByKey);
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static String finalizeKey(String period, long revision) {
        return "FINALIZE:" + period + ":r" + revision;
    }

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

    static Map<String, Object> operationView(PayrollOperation op, PayrollOperationPolicy policy) {
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
        m.put("lastSentAt", op.getLastSentAt() == null ? null : op.getLastSentAt().toString());
        m.put("completedAt", op.getCompletedAt() == null ? null : op.getCompletedAt().toString());
        m.put("resolvableAt", PayrollOperation.PENDING.equals(op.getStatus())
                ? policy.resolvableAt(op.getCreatedAt()).toString() : null);
        m.put("statusUrl", "/api/payroll/operations/" + op.getOperationId());
        return m;
    }

    public Map<String, Object> operation(String operationId) {
        return operationView(operations.findByOperationId(operationId)
                .orElseThrow(() -> PayrollException.notFound("Operation not found: " + operationId)), policy);
    }

    public List<Map<String, Object>> pendingOperations() {
        return operations.findByStatusOrderByCreatedAtAsc(PayrollOperation.PENDING).stream()
                .map(o -> operationView(o, policy)).toList();
    }
}
