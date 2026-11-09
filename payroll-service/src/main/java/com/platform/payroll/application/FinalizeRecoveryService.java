package com.platform.payroll.application;

import com.platform.common.messaging.EventTypes;
import com.platform.common.messaging.PlatformEvent;
import com.platform.common.messaging.PlatformQueues;
import com.platform.common.tenant.TenantContext;
import com.platform.messaging.outbox.OutboxRelay;
import com.platform.messaging.outbox.OutboxWriter;
import com.platform.payroll.domain.FinalizeOperation;
import com.platform.payroll.domain.Payslip;
import com.platform.payroll.infrastructure.persistence.FinalizeOperationRepository;
import com.platform.payroll.infrastructure.persistence.PayslipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tenant-scoped finalize recovery. Lives in its own bean so {@code @Transactional} applies
 * when invoked from {@link FinalizeRecoveryJob} (self-invocation on the job would skip the proxy).
 */
@Service
@ConditionalOnProperty(prefix = "platform.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FinalizeRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(FinalizeRecoveryService.class);

    private final FinalizeOperationRepository operationRepository;
    private final PayslipRepository payslipRepository;
    private final OutboxWriter outboxWriter;
    private final long warnAfterMinutes;
    private final long actionRequiredAfterMinutes;

    public FinalizeRecoveryService(
            FinalizeOperationRepository operationRepository,
            PayslipRepository payslipRepository,
            OutboxWriter outboxWriter,
            @Value("${platform.payroll.finalize-warn-minutes:5}") long warnAfterMinutes,
            @Value("${platform.payroll.finalize-action-required-minutes:30}") long actionRequiredAfterMinutes) {
        this.operationRepository = operationRepository;
        this.payslipRepository = payslipRepository;
        this.outboxWriter = outboxWriter;
        this.warnAfterMinutes = Math.max(1L, warnAfterMinutes);
        this.actionRequiredAfterMinutes = Math.max(warnAfterMinutes + 1, actionRequiredAfterMinutes);
    }

    @Transactional
    public void reconcileTenant() {
        Instant warnBefore = Instant.now().minus(warnAfterMinutes, ChronoUnit.MINUTES);
        Instant failBefore = Instant.now().minus(actionRequiredAfterMinutes, ChronoUnit.MINUTES);
        List<FinalizeOperation> stale = operationRepository.findStale(
                Set.of(FinalizeOperation.STATUS_WAITING_LEAVE, FinalizeOperation.STATUS_PENDING),
                warnBefore);
        for (FinalizeOperation op : stale) {
            long ageMin = ChronoUnit.MINUTES.between(op.getCreatedAt(), Instant.now());
            log.warn("Pending finalize/reopen ageMinutes={} op={} payslip={} status={} tenant={}",
                    ageMin, op.getOperationId(), op.getPayslipId(), op.getStatus(), TenantContext.getTenantId());
            if (FinalizeOperation.STATUS_WAITING_LEAVE.equals(op.getStatus())) {
                replayLeaveCover(op);
            }
            if (op.getCreatedAt().isBefore(failBefore)) {
                escalateActionRequired(op);
            }
        }
    }

    private void replayLeaveCover(FinalizeOperation op) {
        Instant last = op.getUpdatedAt() != null ? op.getUpdatedAt() : op.getCreatedAt();
        long ageMinutes = Math.max(1L, ChronoUnit.MINUTES.between(op.getCreatedAt(), Instant.now()));
        int attempt = (int) Math.max(1L, ageMinutes / warnAfterMinutes);
        long waitMs = OutboxRelay.backoffMs(attempt, 0.5);
        if (last != null && Instant.now().isBefore(last.plusMillis(waitMs))) {
            return;
        }
        Payslip payslip = payslipRepository.findById(op.getPayslipId()).orElse(null);
        if (payslip == null) {
            return;
        }
        Map<String, Object> details = payslip.getDetailsJson() == null ? Map.of() : payslip.getDetailsJson();
        Map<String, BigDecimal> consumed = extractConsumedByType(details);
        if (consumed.isEmpty()) {
            return;
        }
        String tenantId = TenantContext.getTenantId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", op.getOperationId());
        payload.put("operationKey", op.getOperationKey());
        payload.put("payslipId", op.getPayslipId());
        payload.put("employeeId", op.getEmployeeId());
        payload.put("periodLabel", op.getPeriodLabel());
        payload.put("revision", op.getRevision());
        payload.put("reverse", FinalizeOperation.ACTION_REOPEN.equals(op.getAction()));
        payload.put("consumedByType", consumed);
        payload.put("recoveryReplay", true);

        PlatformEvent event = PlatformEvent.of(
                EventTypes.PAYROLL_LEAVE_COVER_COMMAND,
                String.valueOf(op.getPayslipId()),
                op.getRevision(),
                op.getCorrelationId(),
                tenantId,
                payload);
        outboxWriter.enqueue(event, PlatformQueues.PAYROLL_LEAVE_COVER_CMD);
        op.setUpdatedAt(Instant.now());
        operationRepository.save(op);
        log.info("Replayed leave-cover command for stuck op={}", op.getOperationId());
    }

    private void escalateActionRequired(FinalizeOperation op) {
        if (FinalizeOperation.STATUS_ACTION_REQUIRED.equals(op.getStatus())
                || FinalizeOperation.STATUS_SUCCEEDED.equals(op.getStatus())
                || FinalizeOperation.STATUS_FAILED.equals(op.getStatus())) {
            return;
        }
        Payslip payslip = payslipRepository.findById(op.getPayslipId()).orElse(null);
        op.setStatus(FinalizeOperation.STATUS_ACTION_REQUIRED);
        op.setErrorMessage("Timed out waiting for leave-cover result; manual resolution required");
        op.setUpdatedAt(Instant.now());
        op.setCompletedAt(Instant.now());
        operationRepository.save(op);
        if (payslip != null) {
            Map<String, Object> details = new HashMap<>(
                    payslip.getDetailsJson() == null ? Map.of() : payslip.getDetailsJson());
            if (FinalizeOperation.ACTION_FINALIZE.equals(op.getAction())) {
                payslip.setStatus(PayrollFinalizeService.STATUS_ACTION_REQUIRED);
                details.put("status", PayrollFinalizeService.STATUS_ACTION_REQUIRED);
            } else {
                payslip.setStatus(PayrollFinalizeService.STATUS_FINALIZED);
                details.put("status", PayrollFinalizeService.STATUS_FINALIZED);
                details.remove("retainedFinal");
            }
            details.put("finalizeError", op.getErrorMessage());
            payslip.setDetailsJson(details);
            payslipRepository.save(payslip);
        }
        log.error("Escalated finalize op={} to ACTION_REQUIRED", op.getOperationId());
    }

    private static Map<String, BigDecimal> extractConsumedByType(Map<String, Object> details) {
        Object raw = details.get("consumedByType");
        if (!(raw instanceof Map<?, ?> m)) {
            return Map.of();
        }
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        m.forEach((k, v) -> {
            if (k == null || v == null) {
                return;
            }
            try {
                BigDecimal bd = v instanceof BigDecimal b ? b
                        : v instanceof Number n ? BigDecimal.valueOf(n.doubleValue())
                        : new BigDecimal(String.valueOf(v));
                out.put(String.valueOf(k).toUpperCase(Locale.ROOT), bd);
            } catch (Exception ignored) {
                // skip bad entries
            }
        });
        return out;
    }
}
