package com.platform.leave.application;

import com.platform.common.security.AuthContext;
import com.platform.leave.api.dto.LeaveBalanceAdjustRequest;
import com.platform.leave.api.dto.LeaveBalanceResponse;
import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.domain.LeaveApplication;
import com.platform.leave.domain.LeaveBalance;
import com.platform.leave.domain.LeaveBalanceLedger;
import com.platform.leave.domain.LeaveCategory;
import com.platform.leave.domain.LeaveType;
import com.platform.leave.client.people.PeopleEmployeeGateway;
import com.platform.leave.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.leave.infrastructure.persistence.LeaveBalanceLedgerRepository;
import com.platform.leave.infrastructure.persistence.LeaveBalanceRepository;
import com.platform.leave.infrastructure.persistence.LeaveCategoryRepository;
import com.platform.leave.infrastructure.persistence.LeaveTypeRepository;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * HR-managed leave balances (add/deduct). Shared across tenants — rules are data.
 */
@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository balanceRepository;
    private final LeaveBalanceLedgerRepository ledgerRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final PeopleEmployeeGateway employeeRepository;
    private final DataScopeService dataScopeService;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final LeaveCategoryRepository leaveCategoryRepository;
    private final EntityManager entityManager;

    /** Holds that have not yet deducted balance (PENDING_CANCEL already deducted — exclude). */
    private static final Set<String> PENDING_STATUSES = Set.of(
            LeaveApplication.STATUS_PENDING_APPROVAL,
            LeaveApplication.STATUS_PENDING_RELIEF,
            LeaveApplication.STATUS_SUBMITTED
    );

    private static final int OPTIMISTIC_ATTEMPTS = 3;

    public LeaveBalanceService(
            LeaveBalanceRepository balanceRepository,
            LeaveBalanceLedgerRepository ledgerRepository,
            LeaveTypeRepository leaveTypeRepository,
            PeopleEmployeeGateway employeeRepository,
            DataScopeService dataScopeService,
            LeaveApplicationRepository leaveApplicationRepository,
            LeaveCategoryRepository leaveCategoryRepository,
            EntityManager entityManager) {
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.employeeRepository = employeeRepository;
        this.dataScopeService = dataScopeService;
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> listForEmployee(Long employeeId, Integer year) {
        dataScopeService.requireAccess(AuthContext.requireEmployeeId(), employeeId, "leave", "view");
        int y = year == null ? LocalDate.now().getYear() : year;
        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        return balanceRepository.findByEmployeeIdAndYear(employeeId, y).stream()
                .map(LeaveBalanceResponse::from)
                .toList();
    }

    @Transactional
    public LeaveBalanceResponse adjust(LeaveBalanceAdjustRequest request) {
        LeaveType type = leaveTypeRepository.findByCodeAndActiveTrue(request.leaveTypeCode())
                .orElseThrow(() -> new IllegalArgumentException("Leave type not found: " + request.leaveTypeCode()));
        EmployeeView employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + request.employeeId()));
        if (request.actorEmployeeId() != null) {
            employeeRepository.findById(request.actorEmployeeId())
                    .orElseThrow(() -> new IllegalArgumentException("Actor employee not found: " + request.actorEmployeeId()));
        }
        if (request.deltaDays() == null || request.deltaDays().compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("deltaDays must be non-zero");
        }

        int year = request.year() == null ? LocalDate.now().getYear() : request.year();
        LeaveBalance balance = getOrCreate(employee.getId(), type.getCode(), year);
        BigDecimal next = balance.getBalanceDays().add(request.deltaDays());
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Balance would go negative: current="
                    + balance.getBalanceDays() + ", delta=" + request.deltaDays());
        }
        Instant now = Instant.now();
        balance.setBalanceDays(next);
        balance.setUpdatedAt(now);
        balanceRepository.save(balance);

        writeLedger(
                employee.getId(),
                type.getCode(),
                year,
                request.deltaDays(),
                request.reason(),
                request.actorEmployeeId(),
                now,
                UUID.randomUUID().toString(),
                LeaveBalanceLedger.SOURCE_ADJUST,
                request.employeeId() == null ? null : String.valueOf(request.employeeId()));

        return LeaveBalanceResponse.from(balance);
    }

    @Transactional
    public BigDecimal available(Long employeeId, String leaveTypeCode, int year) {
        return balanceRepository.findByEmployeeIdAndLeaveTypeCodeAndYear(employeeId, leaveTypeCode, year)
                .map(LeaveBalance::getBalanceDays)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * FR-LV-11 — balance minus pending working days for the same type/year.
     * Counts days from applications in pending statuses.
     */
    @Transactional(readOnly = true)
    public BigDecimal availableAfterPending(Long employeeId, String leaveTypeCode, int year) {
        BigDecimal balance = available(employeeId, leaveTypeCode, year);
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year + 1, 1, 1);
        BigDecimal pendingDays = leaveApplicationRepository
                .findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .filter(a -> leaveTypeCode.equalsIgnoreCase(a.getLeaveTypeCode()))
                .filter(a -> PENDING_STATUSES.contains(a.getStatus()))
                // Skip rows whose days are already deducted (e.g. mis-set flags); cancel chain is APPROVED→PENDING_CANCEL.
                .filter(a -> !a.isBalanceDeducted())
                .filter(a -> a.getStartDate() != null
                        && !a.getStartDate().isBefore(yearStart)
                        && a.getStartDate().isBefore(yearEnd))
                .map(a -> a.getWorkingDaysCounted() == null ? BigDecimal.ZERO : a.getWorkingDaysCounted())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return balance.subtract(pendingDays);
    }

    /**
     * FR-LV-18 / LV-01 — restore exactly what was previously deducted for this application
     * (look up the approve ledger row by source), never invent balance from thin air.
     */
    @Transactional
    public void restoreOnCancel(Long employeeId, String leaveTypeCode, int year, BigDecimal days, Long applicationId) {
        if (days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String opKey = applicationId != null
                ? "CANCEL:" + applicationId
                : UUID.randomUUID().toString();
        if (ledgerRepository.findByOperationKey(opKey).isPresent()) {
            return;
        }
        String sourceId = applicationId == null ? null : String.valueOf(applicationId);
        // Prefer restoring the absolute value of the approve ledger row for this application (LV-01).
        BigDecimal restore = ledgerRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .filter(l -> leaveTypeCode.equalsIgnoreCase(l.getLeaveTypeCode()) && l.getYear() == year)
                .filter(l -> l.getDeltaDays() != null && l.getDeltaDays().compareTo(BigDecimal.ZERO) < 0)
                .filter(l -> matchesApproveSource(l, sourceId))
                .map(l -> l.getDeltaDays().abs())
                .findFirst()
                .orElse(days)
                .min(days);
        if (restore.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        LeaveBalance balance = getOrCreate(employeeId, leaveTypeCode, year);
        Instant now = Instant.now();
        balance.setBalanceDays(balance.getBalanceDays().add(restore));
        balance.setUpdatedAt(now);
        balanceRepository.save(balance);

        writeLedger(
                employeeId,
                leaveTypeCode,
                year,
                restore,
                "Leave cancel restore",
                AuthContext.getEmployeeId(),
                now,
                opKey,
                LeaveBalanceLedger.SOURCE_CANCEL,
                sourceId);
    }

    /**
     * FR-LV-11 / LV-01 — cascade quota without clamp/ledger mismatch; reject if any balance would go negative.
     */
    @Transactional
    public int cascadeQuotaDelta(String leaveTypeCode, BigDecimal delta, int year) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        List<LeaveBalance> balances = balanceRepository.findAll().stream()
                .filter(b -> leaveTypeCode.equalsIgnoreCase(b.getLeaveTypeCode()) && b.getYear() == year)
                .toList();
        for (LeaveBalance b : balances) {
            BigDecimal next = b.getBalanceDays().add(delta);
            if (next.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException(
                        "Quota change would make balance negative for employee " + b.getEmployeeId()
                                + " type " + leaveTypeCode + " year " + year);
            }
        }
        Instant now = Instant.now();
        for (LeaveBalance b : balances) {
            BigDecimal next = b.getBalanceDays().add(delta);
            b.setBalanceDays(next);
            b.setUpdatedAt(now);
            balanceRepository.save(b);

            writeLedger(
                    b.getEmployeeId(),
                    leaveTypeCode,
                    year,
                    delta,
                    "Quota change",
                    AuthContext.getEmployeeId(),
                    now,
                    UUID.randomUUID().toString(),
                    LeaveBalanceLedger.SOURCE_QUOTA,
                    leaveTypeCode);
        }
        return balances.size();
    }

    /** LV-01 — reject approval when balance is insufficient (no silent partial deduct). */
    @Transactional
    public void deductOnApprove(Long employeeId, String leaveTypeCode, int year, BigDecimal days, Long applicationId) {
        if (days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String opKey = applicationId != null
                ? "APPROVE:" + applicationId
                : UUID.randomUUID().toString();
        if (ledgerRepository.findByOperationKey(opKey).isPresent()) {
            return;
        }
        String sourceId = applicationId == null ? null : String.valueOf(applicationId);
        withOptimisticRetry(() -> {
            LeaveBalance balance = getOrCreate(employeeId, leaveTypeCode, year);
            if (balance.getBalanceDays().compareTo(days) < 0) {
                throw new IllegalStateException(
                        "Insufficient leave balance for " + leaveTypeCode + ": have "
                                + balance.getBalanceDays() + ", need " + days);
            }
            Instant now = Instant.now();
            balance.setBalanceDays(balance.getBalanceDays().subtract(days));
            balance.setUpdatedAt(now);
            balanceRepository.saveAndFlush(balance);

            writeLedger(
                    employeeId,
                    leaveTypeCode,
                    year,
                    days.negate(),
                    "Leave approved deduction",
                    AuthContext.getEmployeeId(),
                    now,
                    opKey,
                    LeaveBalanceLedger.SOURCE_APPROVE,
                    sourceId);
            return null;
        });
    }

    /**
     * Preview year-end carry: for each employee with a balance in fromYear for leaveTypeCode.
     * Category carry_mode wins when category exists; otherwise uses carryMaxDays param.
     * carry_mode: NONE=0, ALL=full balance, CAP=min(balance, carry_cap_days).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> yearEndPreview(String leaveTypeCode, int fromYear, int toYear, BigDecimal carryMaxDays) {
        leaveTypeRepository.findByCodeAndActiveTrue(leaveTypeCode)
                .orElseThrow(() -> new IllegalArgumentException("Leave type not found: " + leaveTypeCode));
        LeaveCategory category = leaveCategoryRepository.findByCodeAndActiveTrue(leaveTypeCode).orElse(null);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (LeaveBalance bal : balanceRepository.findAll()) {
            if (!leaveTypeCode.equalsIgnoreCase(bal.getLeaveTypeCode()) || bal.getYear() != fromYear) {
                continue;
            }
            BigDecimal carry = resolveCarryDays(bal.getBalanceDays(), category, carryMaxDays);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", bal.getEmployeeId());
            row.put("employeeName", employeeRepository.findById(bal.getEmployeeId()).map(EmployeeView::getFullName).orElse(null));
            row.put("fromYear", fromYear);
            row.put("toYear", toYear);
            row.put("leaveTypeCode", leaveTypeCode);
            row.put("currentBalance", bal.getBalanceDays());
            row.put("carryDays", carry);
            row.put("carryMode", category != null ? category.getCarryMode() : "CAP");
            rows.add(row);
        }
        return rows;
    }

    private BigDecimal resolveCarryDays(BigDecimal balance, LeaveCategory category, BigDecimal paramCap) {
        if (category != null && category.getCarryMode() != null) {
            return switch (category.getCarryMode().toUpperCase(java.util.Locale.ROOT)) {
                case "NONE" -> BigDecimal.ZERO;
                case "ALL" -> balance.max(BigDecimal.ZERO);
                case "CAP" -> {
                    BigDecimal cap = category.getCarryCapDays() != null ? category.getCarryCapDays()
                            : (paramCap != null ? paramCap : new BigDecimal("5"));
                    yield balance.min(cap).max(BigDecimal.ZERO);
                }
                default -> BigDecimal.ZERO;
            };
        }
        BigDecimal cap = paramCap == null ? new BigDecimal("5") : paramCap;
        return balance.min(cap).max(BigDecimal.ZERO);
    }

    @Transactional
    public Map<String, Object> yearEndConfirm(String leaveTypeCode, int fromYear, int toYear, BigDecimal carryMaxDays) {
        List<Map<String, Object>> preview = yearEndPreview(leaveTypeCode, fromYear, toYear, carryMaxDays);
        int created = 0;
        Instant now = Instant.now();
        for (Map<String, Object> row : preview) {
            BigDecimal carry = (BigDecimal) row.get("carryDays");
            if (carry == null || carry.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Long employeeId = ((Number) row.get("employeeId")).longValue();
            String carryKey = "CARRY:" + employeeId + ":" + leaveTypeCode + ":" + fromYear + ":" + toYear;
            if (ledgerRepository.findByOperationKey(carryKey).isPresent()) {
                continue;
            }
            LeaveBalance next = getOrCreate(employeeId, leaveTypeCode, toYear);
            next.setBalanceDays(next.getBalanceDays().add(carry));
            next.setUpdatedAt(now);
            balanceRepository.save(next);

            writeLedger(
                    employeeId,
                    leaveTypeCode,
                    toYear,
                    carry,
                    "Year-end carry from " + fromYear,
                    AuthContext.getEmployeeId(),
                    now,
                    carryKey,
                    LeaveBalanceLedger.SOURCE_CARRY,
                    String.valueOf(fromYear));
            created++;
        }
        return Map.of(
                "leaveTypeCode", leaveTypeCode,
                "fromYear", fromYear,
                "toYear", toYear,
                "employeesUpdated", created,
                "previewCount", preview.size()
        );
    }

    /**
     * System-initiated balance adjustment (payroll cover finalize/reopen).
     * Does not require an authenticated actor. Allows balance to go slightly negative
     * only when reversing a cover (delta positive) to avoid blocking reopen.
     */
    /**
     * LV-01 — ledger delta must match balance change exactly; never clamp while writing full delta.
     * Insufficient balance on deduction is a business rejection (caller maps to FAILED cover).
     */
    @Transactional
    public void adjustBySystem(Long employeeId, String leaveTypeCode,
                               BigDecimal delta, String reason, int year,
                               String operationKey, String sourceType, String sourceId) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        String opKey = (operationKey == null || operationKey.isBlank())
                ? UUID.randomUUID().toString()
                : operationKey;
        if (ledgerRepository.findByOperationKey(opKey).isPresent()) {
            return;
        }
        LeaveBalance balance = getOrCreate(employeeId, leaveTypeCode, year);
        BigDecimal next = balance.getBalanceDays().add(delta);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Insufficient leave balance for system adjust " + leaveTypeCode
                            + " employee " + employeeId + ": have " + balance.getBalanceDays()
                            + ", delta " + delta);
        }
        Instant now = Instant.now();
        balance.setBalanceDays(next);
        balance.setUpdatedAt(now);
        balanceRepository.save(balance);

        writeLedger(
                employeeId,
                leaveTypeCode,
                year,
                delta,
                reason == null ? "system-adjust" : reason,
                null,
                now,
                opKey,
                sourceType == null ? LeaveBalanceLedger.SOURCE_ADJUST : sourceType,
                sourceId);
    }

    private LeaveBalance getOrCreate(Long employeeId, String leaveTypeCode, int year) {
        return withOptimisticRetry(() -> {
            try {
                return balanceRepository.findByEmployeeIdAndLeaveTypeCodeAndYear(employeeId, leaveTypeCode, year)
                        .orElseGet(() -> insertZeroBalance(employeeId, leaveTypeCode, year));
            } catch (DataIntegrityViolationException e) {
                return balanceRepository.findByEmployeeIdAndLeaveTypeCodeAndYear(employeeId, leaveTypeCode, year)
                        .orElseThrow(() -> e);
            }
        });
    }

    private LeaveBalance insertZeroBalance(Long employeeId, String leaveTypeCode, int year) {
        LeaveBalance b = new LeaveBalance();
        b.setEmployeeId(employeeId);
        b.setLeaveTypeCode(leaveTypeCode);
        b.setYear(year);
        b.setBalanceDays(BigDecimal.ZERO);
        b.setUpdatedAt(Instant.now());
        return balanceRepository.saveAndFlush(b);
    }

    private void writeLedger(
            Long employeeId,
            String leaveTypeCode,
            int year,
            BigDecimal deltaDays,
            String reason,
            Long actorEmployeeId,
            Instant createdAt,
            String operationKey,
            String sourceType,
            String sourceId) {
        LeaveBalanceLedger ledger = new LeaveBalanceLedger();
        ledger.setEmployeeId(employeeId);
        ledger.setLeaveTypeCode(leaveTypeCode);
        ledger.setYear(year);
        ledger.setDeltaDays(deltaDays);
        ledger.setReason(reason);
        ledger.setActorEmployeeId(actorEmployeeId);
        ledger.setCreatedAt(createdAt);
        ledger.setOperationKey(truncate(operationKey, 160));
        ledger.setSourceType(truncate(sourceType, 32));
        ledger.setSourceId(truncate(sourceId, 64));
        ledgerRepository.save(ledger);
    }

    private static boolean matchesApproveSource(LeaveBalanceLedger ledger, String sourceId) {
        if (LeaveBalanceLedger.SOURCE_APPROVE.equals(ledger.getSourceType())
                && sourceId != null
                && sourceId.equals(ledger.getSourceId())) {
            return true;
        }
        return ledger.getReason() != null
                && ledger.getReason().toLowerCase(java.util.Locale.ROOT).contains("approved");
    }

    private <T> T withOptimisticRetry(Supplier<T> action) {
        ObjectOptimisticLockingFailureException last = null;
        for (int attempt = 1; attempt <= OPTIMISTIC_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (ObjectOptimisticLockingFailureException e) {
                last = e;
                if (attempt == OPTIMISTIC_ATTEMPTS) {
                    break;
                }
                detachStaleBalance(e);
                backoff(attempt);
            }
        }
        throw last;
    }

    private void detachStaleBalance(ObjectOptimisticLockingFailureException e) {
        Object id = e.getIdentifier();
        if (id == null) {
            return;
        }
        try {
            LeaveBalance managed = entityManager.find(LeaveBalance.class, id);
            if (managed != null) {
                entityManager.detach(managed);
            }
        } catch (RuntimeException ignored) {
            // session may already be inconsistent after a stale flush
        }
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(50L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during optimistic-lock retry", ie);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
