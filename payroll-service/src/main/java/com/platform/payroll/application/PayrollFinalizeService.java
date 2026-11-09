package com.platform.payroll.application;

import com.platform.common.messaging.EventTypes;
import com.platform.common.messaging.PlatformEvent;
import com.platform.common.messaging.PlatformQueues;
import com.platform.common.security.AuthContext;
import com.platform.common.tenant.TenantContext;
import com.platform.messaging.config.MessagingProperties;
import com.platform.messaging.correlation.CorrelationContext;
import com.platform.messaging.outbox.OutboxRelay;
import com.platform.messaging.outbox.OutboxWriter;
import com.platform.payroll.api.dto.CalculatePayrollResponse;
import com.platform.payroll.domain.FinalizeOperation;
import com.platform.payroll.domain.PayrollConfigEntity;
import com.platform.payroll.domain.Payslip;
import com.platform.payroll.extension.config.PayrollConfigLoader;
import com.platform.payroll.extension.engine.PayrollPeriodPolicy;
import com.platform.payroll.infrastructure.persistence.FinalizeOperationRepository;
import com.platform.payroll.infrastructure.persistence.PayslipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Async finalize/reopen workflow (guide § finalize 202 + outbox leave cover).
 * <p>Default {@code wait=true} keeps Qii / existing clients working while the
 * broker path runs; set {@code wait=false} for pure 202 Accepted.
 */
@Service
public class PayrollFinalizeService {

    private static final Logger log = LoggerFactory.getLogger(PayrollFinalizeService.class);

    public static final String STATUS_DRAFT = PayrollCalculationService.STATUS_DRAFT;
    public static final String STATUS_FINALIZED = PayrollCalculationService.STATUS_FINALIZED;
    public static final String STATUS_FINALIZING = "FINALIZING";
    public static final String STATUS_REOPENING = "REOPENING";
    public static final String STATUS_ACTION_REQUIRED = "ACTION_REQUIRED";

    private final PayslipRepository payslipRepository;
    private final FinalizeOperationRepository operationRepository;
    private final PayrollConfigLoader configLoader;
    private final PayrollNotificationHook notificationHook;
    private final OutboxWriter outboxWriter;
    private final ObjectProvider<OutboxRelay> outboxRelay;
    private final MessagingProperties messagingProperties;
    private final boolean messagingEnabled;
    private final PayrollCalculationService calculationService;
    private final AuditService auditService;

    public PayrollFinalizeService(
            PayslipRepository payslipRepository,
            FinalizeOperationRepository operationRepository,
            PayrollConfigLoader configLoader,
            PayrollNotificationHook notificationHook,
            OutboxWriter outboxWriter,
            ObjectProvider<OutboxRelay> outboxRelay,
            MessagingProperties messagingProperties,
            @Value("${platform.messaging.enabled:true}") boolean messagingEnabled,
            @Lazy PayrollCalculationService calculationService,
            AuditService auditService) {
        this.payslipRepository = payslipRepository;
        this.operationRepository = operationRepository;
        this.configLoader = configLoader;
        this.notificationHook = notificationHook;
        this.outboxWriter = outboxWriter;
        this.outboxRelay = outboxRelay;
        this.messagingProperties = messagingProperties;
        this.messagingEnabled = messagingEnabled;
        this.calculationService = calculationService;
        this.auditService = auditService;
    }

    public CalculatePayrollResponse finalize(Long payslipId, boolean confirm, boolean wait) {
        if (!confirm) {
            throw new IllegalArgumentException("Pass confirm=true to finalize a payslip");
        }
        if (!messagingEnabled) {
            throw new IllegalStateException(
                    "Payroll finalize requires platform.messaging.enabled=true; the legacy sync path is retired");
        }
        FinalizeOperation op = startFinalize(payslipId);
        drainOutboxSoon();
        if (wait) {
            return waitForPayslip(op.getOperationId(), payslipId);
        }
        return operationResponse(op, payslipId);
    }

    public CalculatePayrollResponse reopen(Long payslipId, boolean wait) {
        if (!messagingEnabled) {
            throw new IllegalStateException(
                    "Payroll reopen requires platform.messaging.enabled=true; the legacy sync path is retired");
        }
        FinalizeOperation op = startReopen(payslipId);
        drainOutboxSoon();
        if (wait) {
            return waitForPayslip(op.getOperationId(), payslipId);
        }
        return operationResponse(op, payslipId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOperation(String operationId) {
        FinalizeOperation op = operationRepository.findByOperationId(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + operationId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("operationId", op.getOperationId());
        m.put("operationKey", op.getOperationKey());
        m.put("payslipId", op.getPayslipId());
        m.put("action", op.getAction());
        m.put("status", op.getStatus());
        m.put("revision", op.getRevision());
        m.put("correlationId", op.getCorrelationId());
        m.put("errorMessage", op.getErrorMessage());
        m.put("createdAt", op.getCreatedAt() == null ? null : op.getCreatedAt().toString());
        m.put("completedAt", op.getCompletedAt() == null ? null : op.getCompletedAt().toString());
        return m;
    }

    @Transactional
    public FinalizeOperation startFinalize(Long payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new IllegalArgumentException("Payslip not found: " + payslipId));
        String current = effectiveStatus(payslip);
        if (STATUS_FINALIZED.equalsIgnoreCase(current)) {
            return existingSucceeded(payslip, FinalizeOperation.ACTION_FINALIZE);
        }
        if (STATUS_FINALIZING.equalsIgnoreCase(current) && payslip.getOperationId() != null) {
            return operationRepository.findByOperationId(payslip.getOperationId())
                    .orElseThrow(() -> new IllegalStateException("Finalize already in progress"));
        }

        String period = payslip.getPeriodLabel();
        PayrollConfigEntity config = configLoader.loadOrDefault();
        PayrollPeriodPolicy periodPolicy = PayrollPeriodPolicy.from(config);
        LocalDate today = LocalDate.now();
        if (period != null && period.length() >= 7 && !periodPolicy.canFinalize(period, today)) {
            throw new IllegalStateException(periodPolicy.finalizeBlockedReason(period, today));
        }

        long revision = payslip.getRevision() <= 0 ? 1L : payslip.getRevision();
        String operationId = UUID.randomUUID().toString();
        String operationKey = "FINALIZE:" + payslipId + ":r" + revision;
        operationRepository.findByOperationKey(operationKey).ifPresent(existing -> {
            throw new IllegalStateException("Finalize operation already exists for this revision: " + operationKey);
        });

        String correlationId = CorrelationContext.getOrCreate();
        String tenantId = requireTenant();

        FinalizeOperation op = new FinalizeOperation();
        op.setOperationId(operationId);
        op.setOperationKey(operationKey);
        op.setPayslipId(payslipId);
        op.setEmployeeId(payslip.getEmployeeId());
        op.setPeriodLabel(period);
        op.setRevision(revision);
        op.setAction(FinalizeOperation.ACTION_FINALIZE);
        op.setCorrelationId(correlationId);
        op.setInitiatedByEmployeeId(AuthContext.getEmployeeId());
        op.setCreatedAt(Instant.now());
        op.setUpdatedAt(Instant.now());

        Map<String, Object> details = new HashMap<>(payslip.getDetailsJson() == null ? Map.of() : payslip.getDetailsJson());
        Map<String, BigDecimal> consumedByType = extractConsumedByType(details);

        payslip.setStatus(STATUS_FINALIZING);
        details.put("status", STATUS_FINALIZING);
        details.put("operationId", operationId);
        payslip.setDetailsJson(details);
        payslip.setOperationId(operationId);
        payslipRepository.save(payslip);

        if (consumedByType.isEmpty()) {
            completeFinalizeLocal(payslip, op, config, false);
            return operationRepository.save(op);
        }

        op.setStatus(FinalizeOperation.STATUS_WAITING_LEAVE);
        operationRepository.save(op);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", operationId);
        payload.put("operationKey", operationKey);
        payload.put("payslipId", payslipId);
        payload.put("employeeId", payslip.getEmployeeId());
        payload.put("periodLabel", period);
        payload.put("revision", revision);
        payload.put("reverse", false);
        payload.put("consumedByType", consumedByType);

        PlatformEvent event = PlatformEvent.of(
                EventTypes.PAYROLL_LEAVE_COVER_COMMAND,
                String.valueOf(payslipId),
                revision,
                correlationId,
                tenantId,
                payload);
        outboxWriter.enqueue(event, PlatformQueues.PAYROLL_LEAVE_COVER_CMD);
        return op;
    }

    @Transactional
    public FinalizeOperation startReopen(Long payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new IllegalArgumentException("Payslip not found: " + payslipId));
        String current = effectiveStatus(payslip);
        if (STATUS_DRAFT.equalsIgnoreCase(current)) {
            return existingSucceeded(payslip, FinalizeOperation.ACTION_REOPEN);
        }
        if (STATUS_REOPENING.equalsIgnoreCase(current) && payslip.getOperationId() != null) {
            return operationRepository.findByOperationId(payslip.getOperationId())
                    .orElseThrow(() -> new IllegalStateException("Reopen already in progress"));
        }

        long revision = payslip.getRevision() <= 0 ? 1L : payslip.getRevision();
        String operationId = UUID.randomUUID().toString();
        String operationKey = "REOPEN:" + payslipId + ":r" + revision;
        String correlationId = CorrelationContext.getOrCreate();
        String tenantId = requireTenant();

        FinalizeOperation op = new FinalizeOperation();
        op.setOperationId(operationId);
        op.setOperationKey(operationKey);
        op.setPayslipId(payslipId);
        op.setEmployeeId(payslip.getEmployeeId());
        op.setPeriodLabel(payslip.getPeriodLabel());
        op.setRevision(revision);
        op.setAction(FinalizeOperation.ACTION_REOPEN);
        op.setCorrelationId(correlationId);
        op.setInitiatedByEmployeeId(AuthContext.getEmployeeId());
        op.setCreatedAt(Instant.now());
        op.setUpdatedAt(Instant.now());

        Map<String, Object> details = new HashMap<>(payslip.getDetailsJson() == null ? Map.of() : payslip.getDetailsJson());
        boolean coverApplied = Boolean.TRUE.equals(details.get("leaveCoverApplied"));
        Map<String, BigDecimal> consumedByType = extractConsumedByType(details);

        // Guide §6: retain the original final; only open a new editable revision after reversal confirms.
        details.put("retainedFinal", retainedFinalSnapshot(payslip, details, revision));
        payslip.setStatus(STATUS_REOPENING);
        details.put("status", STATUS_REOPENING);
        details.put("operationId", operationId);
        payslip.setDetailsJson(details);
        payslip.setOperationId(operationId);
        payslipRepository.save(payslip);

        if (!coverApplied || consumedByType.isEmpty()) {
            completeReopenLocal(payslip, op);
            return operationRepository.save(op);
        }

        op.setStatus(FinalizeOperation.STATUS_WAITING_LEAVE);
        operationRepository.save(op);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", operationId);
        payload.put("operationKey", operationKey);
        payload.put("payslipId", payslipId);
        payload.put("employeeId", payslip.getEmployeeId());
        payload.put("periodLabel", payslip.getPeriodLabel());
        payload.put("revision", revision);
        payload.put("reverse", true);
        payload.put("consumedByType", consumedByType);

        PlatformEvent event = PlatformEvent.of(
                EventTypes.PAYROLL_LEAVE_COVER_COMMAND,
                String.valueOf(payslipId),
                revision,
                correlationId,
                tenantId,
                payload);
        outboxWriter.enqueue(event, PlatformQueues.PAYROLL_LEAVE_COVER_CMD);
        return op;
    }

    @Transactional
    public void onLeaveCoverResult(Map<String, Object> envelope) {
        Map<String, Object> payload = payloadOf(envelope);
        String operationId = str(payload.get("operationId"));
        boolean success = Boolean.TRUE.equals(payload.get("success"))
                || "SUCCEEDED".equalsIgnoreCase(str(payload.get("status")));
        String error = str(payload.get("errorMessage"));

        FinalizeOperation op = operationRepository.findByOperationId(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown operationId: " + operationId));
        if (FinalizeOperation.STATUS_SUCCEEDED.equals(op.getStatus())
                || FinalizeOperation.STATUS_FAILED.equals(op.getStatus())) {
            return;
        }

        Payslip payslip = payslipRepository.findById(op.getPayslipId())
                .orElseThrow(() -> new IllegalStateException("Payslip missing for op " + operationId));

        // PAY-05 — ignore stale results (wrong operation / revision / status)
        if (payslip.getOperationId() == null || !payslip.getOperationId().equals(operationId)) {
            log.warn("Ignoring stale leave-cover result op={} payslipOp={}",
                    operationId, payslip.getOperationId());
            return;
        }
        if (op.getRevision() > 0 && payslip.getRevision() != op.getRevision()) {
            log.warn("Ignoring stale leave-cover result op={} opRev={} payslipRev={}",
                    operationId, op.getRevision(), payslip.getRevision());
            return;
        }
        String pst = payslip.getStatus() == null ? "" : payslip.getStatus();
        if (!"FINALIZING".equalsIgnoreCase(pst)
                && !"REOPENING".equalsIgnoreCase(pst)
                && !"ACTION_REQUIRED".equalsIgnoreCase(pst)) {
            log.warn("Ignoring leave-cover result for payslip status={} op={}", pst, operationId);
            return;
        }

        if (!success) {
            failOperation(op, payslip, error.isBlank() ? "Leave cover failed" : error);
            return;
        }

        if (FinalizeOperation.ACTION_FINALIZE.equals(op.getAction())) {
            PayrollConfigEntity config = configLoader.loadOrDefault();
            completeFinalizeLocal(payslip, op, config, true);
        } else {
            completeReopenLocal(payslip, op);
        }
        operationRepository.save(op);
        payslipRepository.save(payslip);
    }

    private void completeFinalizeLocal(Payslip payslip, FinalizeOperation op,
                                       PayrollConfigEntity config, boolean leaveCoverApplied) {
        Map<String, Object> details = new HashMap<>(payslip.getDetailsJson() == null ? Map.of() : payslip.getDetailsJson());
        if (leaveCoverApplied) {
            details.put("leaveCoverApplied", true);
        }
        Long actor = op.getInitiatedByEmployeeId() != null
                ? op.getInitiatedByEmployeeId() : AuthContext.getEmployeeId();
        details.put("status", STATUS_FINALIZED);
        details.put("finalizedAt", Instant.now().toString());
        details.put("finalizedBy", actor);
        details.put("notificationSentToEmployeeId", payslip.getEmployeeId());
        payslip.setDetailsJson(details);
        payslip.setStatus(STATUS_FINALIZED);
        payslip.setRevision(payslip.getRevision() + 1);
        payslip.setOperationId(op.getOperationId());
        payslipRepository.save(payslip);

        op.setStatus(FinalizeOperation.STATUS_SUCCEEDED);
        op.setUpdatedAt(Instant.now());
        op.setCompletedAt(Instant.now());
        op.setErrorMessage(null);

        auditService.record(actor, "FINALIZE", "Payslip",
                String.valueOf(payslip.getId()), null, Map.of(
                        "periodLabel", payslip.getPeriodLabel(),
                        "employeeId", payslip.getEmployeeId(),
                        "revision", payslip.getRevision(),
                        "operationId", op.getOperationId()));

        enqueueNotification(payslip, config, op);
    }

    private void completeReopenLocal(Payslip payslip, FinalizeOperation op) {
        Map<String, Object> details = new HashMap<>(payslip.getDetailsJson() == null ? Map.of() : payslip.getDetailsJson());
        Object retained = details.get("retainedFinal");
        if (retained != null) {
            // Keep an append-only history of prior finals for audit / restore drills.
            @SuppressWarnings("unchecked")
            java.util.List<Object> history = details.get("priorFinals") instanceof java.util.List<?> list
                    ? new java.util.ArrayList<>((java.util.List<Object>) list)
                    : new java.util.ArrayList<>();
            history.add(retained);
            details.put("priorFinals", history);
        }
        details.remove("leaveCoverApplied");
        details.remove("retainedFinal");
        Long actor = op.getInitiatedByEmployeeId() != null
                ? op.getInitiatedByEmployeeId() : AuthContext.getEmployeeId();
        details.put("status", STATUS_DRAFT);
        details.remove("finalizedAt");
        details.remove("finalizedBy");
        details.put("reopenedAt", Instant.now().toString());
        details.put("reopenedBy", actor);
        payslip.setDetailsJson(details);
        payslip.setStatus(STATUS_DRAFT);
        payslip.setRevision(payslip.getRevision() + 1);
        payslip.setOperationId(op.getOperationId());
        payslipRepository.save(payslip);

        op.setStatus(FinalizeOperation.STATUS_SUCCEEDED);
        op.setUpdatedAt(Instant.now());
        op.setCompletedAt(Instant.now());
        op.setErrorMessage(null);

        auditService.record(actor, "REOPEN", "Payslip",
                String.valueOf(payslip.getId()), STATUS_FINALIZED, Map.of(
                        "periodLabel", payslip.getPeriodLabel(),
                        "employeeId", payslip.getEmployeeId(),
                        "revision", payslip.getRevision(),
                        "operationId", op.getOperationId()));
    }

    private void failOperation(FinalizeOperation op, Payslip payslip, String error) {
        // Guide §6: failed leave cover → ACTION_REQUIRED (not silent draft). Reopen failure restores final.
        boolean finalizeAction = FinalizeOperation.ACTION_FINALIZE.equals(op.getAction());
        op.setStatus(finalizeAction
                ? FinalizeOperation.STATUS_ACTION_REQUIRED
                : FinalizeOperation.STATUS_FAILED);
        op.setErrorMessage(error);
        op.setUpdatedAt(Instant.now());
        op.setCompletedAt(Instant.now());
        operationRepository.save(op);

        Map<String, Object> details = new HashMap<>(payslip.getDetailsJson() == null ? Map.of() : payslip.getDetailsJson());
        if (finalizeAction) {
            payslip.setStatus(STATUS_ACTION_REQUIRED);
            details.put("status", STATUS_ACTION_REQUIRED);
        } else {
            payslip.setStatus(STATUS_FINALIZED);
            details.put("status", STATUS_FINALIZED);
            details.remove("retainedFinal");
        }
        details.put("finalizeError", error);
        payslip.setDetailsJson(details);
        payslipRepository.save(payslip);
        log.warn("Finalize/reopen failed op={} payslip={}: {}", op.getOperationId(), payslip.getId(), error);
    }

    private static Map<String, Object> retainedFinalSnapshot(Payslip payslip, Map<String, Object> details, long revision) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("revision", revision);
        snap.put("status", STATUS_FINALIZED);
        snap.put("grossAmount", payslip.getGrossAmount());
        snap.put("deductionAmount", payslip.getDeductionAmount());
        snap.put("netAmount", payslip.getNetAmount());
        snap.put("finalizedAt", details.get("finalizedAt"));
        snap.put("finalizedBy", details.get("finalizedBy"));
        snap.put("retainedAt", Instant.now().toString());
        return snap;
    }

    private void enqueueNotification(Payslip payslip, PayrollConfigEntity config, FinalizeOperation op) {
        String currency = null;
        Map<String, Object> details = payslip.getDetailsJson();
        if (details != null && details.get("currency") != null) {
            currency = String.valueOf(details.get("currency"));
        }
        // Keep HTTP hook for config/HRMS checks; also enqueue durable broker command.
        notificationHook.notifyPayslipFinalized(
                config,
                payslip.getEmployeeId(),
                payslip.getId(),
                payslip.getPeriodLabel(),
                payslip.getNetAmount(),
                currency);

        String tenantId = requireTenant();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeId", payslip.getEmployeeId());
        payload.put("title", "Payslip ready");
        payload.put("body", "Your payslip for " + payslip.getPeriodLabel() + " has been finalized.");
        payload.put("category", "PAYROLL");
        payload.put("dedupeKey", "payslip-final-" + payslip.getId());
        payload.put("linkUrl", "/payroll");
        PlatformEvent event = PlatformEvent.of(
                EventTypes.NOTIFICATION_CREATE,
                String.valueOf(payslip.getId()),
                payslip.getRevision(),
                op.getCorrelationId(),
                tenantId,
                payload);
        try {
            outboxWriter.enqueue(event, PlatformQueues.NOTIFICATION_CREATE);
        } catch (Exception e) {
            log.warn("Notification outbox enqueue failed (HTTP hook may still deliver): {}", e.getMessage());
        }
    }

    private FinalizeOperation existingSucceeded(Payslip payslip, String action) {
        FinalizeOperation op = new FinalizeOperation();
        op.setOperationId(payslip.getOperationId() == null ? UUID.randomUUID().toString() : payslip.getOperationId());
        op.setOperationKey(action + ":" + payslip.getId() + ":done");
        op.setPayslipId(payslip.getId());
        op.setEmployeeId(payslip.getEmployeeId());
        op.setPeriodLabel(payslip.getPeriodLabel());
        op.setRevision(payslip.getRevision());
        op.setAction(action);
        op.setStatus(FinalizeOperation.STATUS_SUCCEEDED);
        op.setCorrelationId(CorrelationContext.getOrCreate());
        op.setCreatedAt(Instant.now());
        op.setUpdatedAt(Instant.now());
        op.setCompletedAt(Instant.now());
        return op;
    }

    private CalculatePayrollResponse waitForPayslip(String operationId, Long payslipId) {
        long deadline = System.currentTimeMillis() + messagingProperties.getFinalizeWaitSeconds() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            FinalizeOperation op = operationRepository.findByOperationId(operationId).orElse(null);
            if (op != null && FinalizeOperation.STATUS_SUCCEEDED.equals(op.getStatus())) {
                Payslip p = payslipRepository.findById(payslipId)
                        .orElseThrow(() -> new IllegalStateException("Payslip missing after finalize"));
                return calculationService.toPublicResponse(p);
            }
            if (op != null && FinalizeOperation.STATUS_FAILED.equals(op.getStatus())) {
                throw new IllegalStateException(op.getErrorMessage() == null
                        ? "Finalize failed" : op.getErrorMessage());
            }
            drainOutboxNow();
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted waiting for finalize");
            }
        }
        throw new IllegalStateException(
                "Finalize still in progress (operationId=" + operationId
                        + "). Poll GET /api/payroll/operations/" + operationId);
    }

    private CalculatePayrollResponse operationResponse(FinalizeOperation op, Long payslipId) {
        Payslip p = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new IllegalArgumentException("Payslip not found: " + payslipId));
        CalculatePayrollResponse base = calculationService.toPublicResponse(p);
        Map<String, Object> details = new HashMap<>(base.details() == null ? Map.of() : base.details());
        details.put("operationId", op.getOperationId());
        details.put("operationStatus", op.getStatus());
        return new CalculatePayrollResponse(
                base.payslipId(),
                base.employeeId(),
                base.employeeCode(),
                base.periodLabel(),
                base.overtimeMultiplier(),
                base.weekendMultiplier(),
                base.grossAmount(),
                base.deductionAmount(),
                base.netAmount(),
                base.deductions(),
                details);
    }

    private void drainOutboxSoon() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    drainOutboxNow();
                }
            });
        } else {
            drainOutboxNow();
        }
    }

    private void drainOutboxNow() {
        OutboxRelay relay = outboxRelay.getIfAvailable();
        if (relay == null) {
            return;
        }
        try {
            relay.drainBatch();
        } catch (Exception e) {
            log.debug("Outbox drain: {}", e.getMessage());
        }
    }

    private static String requireTenant() {
        String t = TenantContext.getTenantId();
        if (t == null || t.isBlank()) {
            throw new IllegalStateException("TenantContext required");
        }
        return t;
    }

    private static String effectiveStatus(Payslip p) {
        if (p.getStatus() != null && !p.getStatus().isBlank()) {
            return p.getStatus();
        }
        if (p.getDetailsJson() == null) return STATUS_DRAFT;
        Object s = p.getDetailsJson().get("status");
        return s == null ? STATUS_DRAFT : String.valueOf(s);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, BigDecimal> extractConsumedByType(Map<String, Object> details) {
        Object raw = details.get("consumedByType");
        if (!(raw instanceof Map<?, ?> m)) return Map.of();
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        m.forEach((k, v) -> {
            if (k != null && v != null) {
                try {
                    BigDecimal bd = v instanceof BigDecimal b ? b
                            : v instanceof Number n ? BigDecimal.valueOf(n.doubleValue())
                            : new BigDecimal(String.valueOf(v));
                    out.put(String.valueOf(k).toUpperCase(Locale.ROOT), bd);
                } catch (Exception ignored) { /* skip */ }
            }
        });
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payloadOf(Map<String, Object> envelope) {
        if (envelope == null) return Map.of();
        Object p = envelope.get("payload");
        if (p instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return envelope;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
