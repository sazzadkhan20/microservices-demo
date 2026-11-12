package com.platform.leave.application;

import com.platform.leave.domain.LeaveOperation;
import com.platform.leave.infrastructure.persistence.LeaveOperationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The outcome Leave recorded for a payroll finalise/reopen command, and the fence payroll uses before
 * it resolves a stuck operation manually (guide §6 manual resolve, docs/PAYROLL_SPEC.md §2.3).
 *
 * <p>The fence records {@code CANCELLED} for an operation key Leave has not processed yet. The
 * {@code leave_operation.operation_key} column is unique and the command listener checks it before
 * touching any balance, so a command that arrives after the fence replays CANCELLED and never deducts or
 * restores leave. If Leave already processed the key, the fence changes nothing and returns that outcome.
 */
@Service
public class PayrollOperationFenceService {

    private static final Pattern KEY = Pattern.compile("^(FINALIZE|REOPEN):\\d{4}-(0[1-9]|1[0-2]):r\\d+(:a\\d+)?$");

    private final LeaveOperationRepository operations;

    public PayrollOperationFenceService(LeaveOperationRepository operations) {
        this.operations = operations;
    }

    @Transactional(readOnly = true)
    public Optional<LeaveOperation> find(String operationKey) {
        requireKey(operationKey);
        return operations.findByOperationKey(operationKey);
    }

    /**
     * Records CANCELLED unless Leave already has an outcome for the key. A concurrent insert of the same
     * key (the command being processed right now) fails on the unique key; the caller then reads the
     * winner with {@link #find}.
     */
    @Transactional
    public LeaveOperation fence(String operationKey, String reason) {
        requireKey(operationKey);
        Optional<LeaveOperation> existing = operations.findByOperationKey(operationKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        String why = reason == null || reason.isBlank() ? "manual resolve" : reason.trim();
        String[] parts = operationKey.split(":");
        LeaveOperation op = new LeaveOperation();
        op.setOperationKey(operationKey);
        op.setEventId("payroll-fence");
        op.setEmployeeId(0L);
        op.setPeriodLabel(parts[1]);
        op.setReverseFlag("REOPEN".equals(parts[0]));
        op.setStatus(LeaveOperation.STATUS_CANCELLED);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operationKey", operationKey);
        result.put("action", parts[0]);
        result.put("period", parts[1]);
        result.put("success", false);
        result.put("status", LeaveOperation.STATUS_CANCELLED);
        result.put("errorMessage", "Cancelled by payroll before Leave processed it: " + why);
        op.setResultJson(result);
        Instant now = Instant.now();
        op.setCreatedAt(now);
        op.setCompletedAt(now);
        return operations.saveAndFlush(op);
    }

    public static Map<String, Object> view(String operationKey, LeaveOperation op) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("operationKey", operationKey);
        m.put("known", op != null);
        if (op != null) {
            m.put("status", op.getStatus());
            m.put("success", LeaveOperation.STATUS_SUCCEEDED.equals(op.getStatus()));
            m.put("result", op.getResultJson());
            m.put("completedAt", op.getCompletedAt() == null ? null : op.getCompletedAt().toString());
        }
        return m;
    }

    private static void requireKey(String operationKey) {
        if (operationKey == null || !KEY.matcher(operationKey).matches()) {
            throw new IllegalArgumentException("Not a payroll operation key: " + operationKey);
        }
    }
}
