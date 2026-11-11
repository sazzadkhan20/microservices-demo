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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * HR-managed leave balances. Running {@code balance_days} stays in sync with ledger.
 * Display formula: quota + carried + adjustments − taken − pending.
 */
@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository balanceRepository;
    private final LeaveBalanceLedgerRepository ledgerRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final PeopleEmployeeGateway employeeRepository;
    private final DataScopeService dataScopeService;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final LeaveCategoryService leaveCategoryService;
    private final LeaveAppliesToService leaveAppliesToService;
    private final AuditService auditService;
    private final EntityManager entityManager;

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
            LeaveCategoryService leaveCategoryService,
            LeaveAppliesToService leaveAppliesToService,
            AuditService auditService,
            EntityManager entityManager) {
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.employeeRepository = employeeRepository;
        this.dataScopeService = dataScopeService;
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.leaveCategoryService = leaveCategoryService;
        this.leaveAppliesToService = leaveAppliesToService;
        this.auditService = auditService;
        this.entityManager = entityManager;
    }

    @Transactional
    public List<LeaveBalanceResponse> listForEmployee(Long employeeId, Integer year) {
        return listForEmployee(employeeId, year, false);
    }

    /**
     * @param currentOnly when true, only categories whose period covers today and that apply to
     *                    the employee (same gate as the apply form / home dashboard).
     *                    Not read-only: may expire carry and reconcile ledger → balance_days.
     */
    @Transactional
    public List<LeaveBalanceResponse> listForEmployee(Long employeeId, Integer year, boolean currentOnly) {
        dataScopeService.requireAccess(AuthContext.requireEmployeeId(), employeeId, "leave", "view");
        EmployeeView employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        // FR-LV-03 — hourly-schedule staff have no day-based leave balances.
        if (leaveAppliesToService.isHourlySchedule(employee)) {
            return List.of();
        }
        if (currentOnly) {
            return listCurrentPeriodBalances(employee);
        }
        int y = year == null ? LocalDate.now().getYear() : year;
        return balanceRepository.findByEmployeeIdAndYear(employeeId, y).stream()
                .map(b -> toBreakdown(b))
                .toList();
    }

    /** Home / employee self-view: ongoing eligible categories only (no past or future periods). */
    private List<LeaveBalanceResponse> listCurrentPeriodBalances(EmployeeView employee) {
        LocalDate today = LocalDate.now();
        Map<String, LeaveBalanceResponse> byCode = new LinkedHashMap<>();
        for (LeaveCategory category : leaveCategoryService.findAll()) {
            if (category == null || !category.isActive() || category.getCode() == null || category.getCode().isBlank()) {
                continue;
            }
            if (!leaveCategoryService.covers(category, today)) {
                continue;
            }
            if (!leaveAppliesToService.isEligible(employee, category)) {
                continue;
            }
            String code = category.getCode().trim().toUpperCase(Locale.ROOT);
            if (byCode.containsKey(code)) {
                continue;
            }
            int y = leaveCategoryService.balanceYear(category);
            LeaveBalance balance = balanceRepository
                    .findByEmployeeIdAndLeaveTypeCodeAndYear(employee.getId(), code, y)
                    .orElse(null);
            if (balance == null) {
                // Surface a zero row so the dashboard still lists the ongoing category.
                LeaveBalance zero = new LeaveBalance();
                zero.setEmployeeId(employee.getId());
                zero.setLeaveTypeCode(code);
                zero.setYear(y);
                zero.setBalanceDays(BigDecimal.ZERO);
                byCode.put(code, toBreakdown(zero));
            } else {
                byCode.put(code, toBreakdown(balance));
            }
        }
        return List.copyOf(byCode.values());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listLedger(Long employeeId, Integer year, String sourceType) {
        dataScopeService.requireAccess(AuthContext.requireEmployeeId(), employeeId, "leave", "view");
        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        String sourceFilter = sourceType == null || sourceType.isBlank()
                ? null
                : sourceType.trim().toUpperCase(Locale.ROOT);
        return ledgerRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .filter(l -> year == null || l.getYear() == year)
                .filter(l -> sourceFilter == null
                        || (l.getSourceType() != null && sourceFilter.equalsIgnoreCase(l.getSourceType())))
                .map(l -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", l.getId());
                    row.put("employeeId", l.getEmployeeId());
                    row.put("leaveTypeCode", l.getLeaveTypeCode());
                    row.put("year", l.getYear());
                    row.put("deltaDays", l.getDeltaDays());
                    row.put("reason", l.getReason());
                    row.put("actorEmployeeId", l.getActorEmployeeId());
                    row.put("sourceType", l.getSourceType());
                    row.put("sourceId", l.getSourceId());
                    row.put("operationKey", l.getOperationKey());
                    row.put("createdAt", l.getCreatedAt());
                    return row;
                })
                .toList();
    }

    @Transactional
    public LeaveBalanceResponse adjust(LeaveBalanceAdjustRequest request) {
        if (request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("reason is required for balance adjustments");
        }
        LeaveType type = leaveTypeRepository.findByCodeAndActiveTrue(request.leaveTypeCode())
                .orElseThrow(() -> new IllegalArgumentException("Leave type not found: " + request.leaveTypeCode()));
        EmployeeView employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + request.employeeId()));
        if (leaveAppliesToService.isHourlySchedule(employee)) {
            throw new IllegalStateException(
                    "Hourly-schedule employees cannot receive leave balances (FR-LV-03)");
        }
        Long actorId = request.actorEmployeeId() != null
                ? request.actorEmployeeId()
                : AuthContext.getEmployeeId();
        if (request.deltaDays() == null || request.deltaDays().compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("deltaDays must be non-zero");
        }

        int year = request.year() == null ? LocalDate.now().getYear() : request.year();
        LeaveBalance balance = getOrCreate(employee.getId(), type.getCode(), year);
        BigDecimal delta = LeaveDays.of(request.deltaDays());
        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("deltaDays must be a non-zero half-day step (0.5) amount");
        }
        BigDecimal before = LeaveDays.of(balance.getBalanceDays());
        BigDecimal next = LeaveDays.of(before.add(delta));
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Balance would go negative: current="
                    + LeaveDays.format(before) + ", delta=" + LeaveDays.format(delta));
        }
        Instant now = Instant.now();
        balance.setBalanceDays(next);
        balance.setUpdatedAt(now);
        balanceRepository.save(balance);

        writeLedger(
                employee.getId(),
                type.getCode(),
                year,
                delta,
                request.reason().trim(),
                actorId,
                now,
                UUID.randomUUID().toString(),
                LeaveBalanceLedger.SOURCE_ADJUST,
                String.valueOf(employee.getId()));

        auditService.record(actorId, "ADJUST", "LeaveBalance",
                employee.getId() + ":" + type.getCode() + ":" + year,
                Map.of("balanceDays", before),
                Map.of("balanceDays", next, "deltaDays", delta, "reason", request.reason().trim()));

        return toBreakdown(balance);
    }

    /**
     * Opening / join grant — positive only, SOURCE_OPENING, one opening row per employee/type/year
     * unless forceReplace is used via reason containing existing correction path.
     */
    @Transactional
    public LeaveBalanceResponse recordOpening(
            Long employeeId,
            String leaveTypeCode,
            BigDecimal days,
            int year,
            String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required for opening balances");
        }
        BigDecimal grant = LeaveDays.of(days);
        if (grant.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Opening grant must be a positive half-day step amount");
        }
        LeaveType type = leaveTypeRepository.findByCodeAndActiveTrue(leaveTypeCode)
                .orElseThrow(() -> new IllegalArgumentException("Leave type not found: " + leaveTypeCode));
        EmployeeView employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        if (leaveAppliesToService.isHourlySchedule(employee)) {
            throw new IllegalStateException(
                    "Hourly-schedule employees cannot receive leave balances (FR-LV-03)");
        }

        String openKey = "OPENING:" + employeeId + ":" + type.getCode() + ":" + year;
        if (ledgerRepository.findByOperationKey(openKey).isPresent()) {
            throw new IllegalStateException(
                    "Opening balance already recorded for " + type.getCode() + " / " + year
                            + ". Use Adjust balances for later corrections.");
        }
        LeaveLedgerMath.Parts existing = LeaveLedgerMath.classify(
                ledgerRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId),
                type.getCode(),
                year);
        if (existing.quota().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                    "Quota/opening already present for " + type.getCode() + " / " + year
                            + " (" + LeaveDays.format(existing.quota())
                            + " d). Use Adjust balances for later corrections.");
        }

        LeaveBalance balance = getOrCreate(employeeId, type.getCode(), year);
        BigDecimal before = LeaveDays.of(balance.getBalanceDays());
        BigDecimal next = LeaveDays.of(before.add(grant));
        Instant now = Instant.now();
        balance.setBalanceDays(next);
        balance.setUpdatedAt(now);
        balanceRepository.save(balance);

        Long actorId = AuthContext.getEmployeeId();
        writeLedger(
                employeeId,
                type.getCode(),
                year,
                grant,
                reason.trim(),
                actorId,
                now,
                openKey,
                LeaveBalanceLedger.SOURCE_OPENING,
                type.getCode());

        auditService.record(actorId, "OPENING", "LeaveBalance",
                employeeId + ":" + type.getCode() + ":" + year,
                Map.of("balanceDays", before),
                Map.of("balanceDays", next, "deltaDays", grant, "reason", reason.trim()));

        return toBreakdown(balance);
    }

    @Transactional
    public BigDecimal available(Long employeeId, String leaveTypeCode, int year) {
        expireCarriedDaysIfDue(employeeId, leaveTypeCode, year);
        LeaveBalance bal = balanceRepository
                .findByEmployeeIdAndLeaveTypeCodeAndYear(employeeId, leaveTypeCode, year)
                .orElse(null);
        if (bal == null) {
            return BigDecimal.ZERO;
        }
        // Heal any leftover decimal rows so UI and apply checks always match.
        BigDecimal whole = LeaveDays.of(bal.getBalanceDays());
        if (bal.getBalanceDays() == null || bal.getBalanceDays().compareTo(whole) != 0) {
            bal.setBalanceDays(whole);
            bal.setUpdatedAt(Instant.now());
            balanceRepository.save(bal);
        }
        return whole;
    }

    @Transactional
    public BigDecimal availableAfterPending(Long employeeId, String leaveTypeCode, int year) {
        BigDecimal balance = available(employeeId, leaveTypeCode, year);
        return LeaveDays.of(balance.subtract(pendingDays(employeeId, leaveTypeCode, year)));
    }

    @Transactional(readOnly = true)
    public BigDecimal pendingDays(Long employeeId, String leaveTypeCode, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year + 1, 1, 1);
        BigDecimal pending = leaveApplicationRepository
                .findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .filter(a -> leaveTypeCode.equalsIgnoreCase(a.getLeaveTypeCode()))
                .filter(a -> PENDING_STATUSES.contains(a.getStatus()))
                .filter(a -> !a.isBalanceDeducted())
                .filter(a -> a.getStartDate() != null
                        && !a.getStartDate().isBefore(yearStart)
                        && a.getStartDate().isBefore(yearEnd))
                .map(a -> a.getWorkingDaysCounted() == null ? BigDecimal.ZERO : a.getWorkingDaysCounted())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return LeaveDays.of(pending);
    }

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
        BigDecimal restoreAmt = LeaveDays.of(restore);
        balance.setBalanceDays(LeaveDays.of(
                LeaveDays.of(balance.getBalanceDays()).add(restoreAmt)));
        balance.setUpdatedAt(now);
        balanceRepository.save(balance);

        writeLedger(
                employeeId,
                leaveTypeCode,
                year,
                restoreAmt,
                "Leave cancel restore",
                AuthContext.getEmployeeId(),
                now,
                opKey,
                LeaveBalanceLedger.SOURCE_CANCEL,
                sourceId);
    }

    /**
     * Mid-period quota change: apply delta to every existing balance for that type/year.
     * Employees who already took leave keep their taken days; remaining moves with the quota.
     */
    @Transactional
    public int cascadeQuotaDelta(String leaveTypeCode, BigDecimal delta, int year) {
        BigDecimal deltaAmt = LeaveDays.of(delta);
        if (deltaAmt.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        List<LeaveBalance> balances = balanceRepository.findAll().stream()
                .filter(b -> leaveTypeCode.equalsIgnoreCase(b.getLeaveTypeCode()) && b.getYear() == year)
                .toList();
        for (LeaveBalance b : balances) {
            BigDecimal next = LeaveDays.of(LeaveDays.of(b.getBalanceDays()).add(deltaAmt));
            if (next.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException(
                        "Quota change would make balance negative for employee " + b.getEmployeeId()
                                + " type " + leaveTypeCode + " year " + year
                                + " (already used leave reduces remaining below zero). "
                                + "Lower the quota less, or adjust individual balances first.");
            }
        }
        Instant now = Instant.now();
        Long actorId = AuthContext.getEmployeeId();
        for (LeaveBalance b : balances) {
            BigDecimal before = LeaveDays.of(b.getBalanceDays());
            BigDecimal next = LeaveDays.of(before.add(deltaAmt));
            b.setBalanceDays(next);
            b.setUpdatedAt(now);
            balanceRepository.save(b);

            writeLedger(
                    b.getEmployeeId(),
                    leaveTypeCode,
                    year,
                    deltaAmt,
                    "Quota change mid-period",
                    actorId,
                    now,
                    "QUOTA:" + leaveTypeCode + ":" + year + ":" + b.getEmployeeId() + ":" + now.toEpochMilli()
                            + ":" + UUID.randomUUID(),
                    LeaveBalanceLedger.SOURCE_QUOTA,
                    leaveTypeCode);

            auditService.record(actorId, "CASCADE_QUOTA", "LeaveBalance",
                    b.getEmployeeId() + ":" + leaveTypeCode + ":" + year,
                    Map.of("balanceDays", before),
                    Map.of("balanceDays", next, "deltaDays", deltaAmt));
        }
        return balances.size();
    }

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
            expireCarriedDaysIfDue(employeeId, leaveTypeCode, year);
            LeaveBalance balance = getOrCreate(employeeId, leaveTypeCode, year);
            BigDecimal floor = negativeBalanceFloor(leaveTypeCode, year);
            BigDecimal debit = LeaveDays.of(days); // keeps half-day 0.5
            BigDecimal have = LeaveDays.of(balance.getBalanceDays());
            BigDecimal next = LeaveDays.of(have.subtract(debit));
            if (next.compareTo(floor) < 0) {
                throw new IllegalStateException(
                        "Insufficient leave balance for " + leaveTypeCode + ": have "
                                + LeaveDays.format(have) + ", need " + LeaveDays.format(debit)
                                + (floor.compareTo(BigDecimal.ZERO) < 0
                                ? " (negative cap " + LeaveDays.format(floor) + ")"
                                : ""));
            }
            Instant now = Instant.now();
            balance.setBalanceDays(next);
            balance.setUpdatedAt(now);
            balanceRepository.saveAndFlush(balance);

            writeLedger(
                    employeeId,
                    leaveTypeCode,
                    year,
                    debit.negate(),
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
     * Lowest allowed running balance after approve. Category negativeCap of -2 (or 2) means
     * balance may go down to -2.
     */
    private BigDecimal negativeBalanceFloor(String leaveTypeCode, int year) {
        LeaveCategory category = leaveCategoryService.resolveForCodeOn(
                        leaveTypeCode, LocalDate.of(year, 6, 15))
                .or(() -> leaveCategoryService.findAllByCode(leaveTypeCode).stream().findFirst())
                .orElse(null);
        if (category == null || category.getNegativeCap() == null) {
            return BigDecimal.ZERO;
        }
        // Whole-day overdraft only (e.g. -2 or 2 → floor -2).
        return LeaveDays.whole(category.getNegativeCap().abs()).negate();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> yearEndPreview(String leaveTypeCode, int fromYear, int toYear, BigDecimal carryMaxDays) {
        validateYearEnd(leaveTypeCode, fromYear, toYear);
        LeaveCategory category = leaveCategoryService.resolveForCodeOn(
                        leaveTypeCode, LocalDate.of(fromYear, 12, 31))
                .or(() -> leaveCategoryService.findAllByCode(leaveTypeCode).stream().findFirst())
                .orElse(null);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (LeaveBalance bal : balanceRepository.findAll()) {
            if (!leaveTypeCode.equalsIgnoreCase(bal.getLeaveTypeCode()) || bal.getYear() != fromYear) {
                continue;
            }
            BigDecimal carry = LeaveDays.of(resolveCarryDays(bal.getBalanceDays(), category, carryMaxDays));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", bal.getEmployeeId());
            row.put("employeeName", employeeRepository.findById(bal.getEmployeeId()).map(EmployeeView::getFullName).orElse(null));
            row.put("fromYear", fromYear);
            row.put("toYear", toYear);
            row.put("leaveTypeCode", leaveTypeCode);
            row.put("currentBalance", LeaveDays.of(bal.getBalanceDays()));
            row.put("carryDays", carry);
            row.put("carryMode", category != null ? category.getCarryMode() : "CAP");
            row.put("carryCapDays", category != null ? category.getCarryCapDays() : carryMaxDays);
            rows.add(row);
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> yearEndConfirm(String leaveTypeCode, int fromYear, int toYear, BigDecimal carryMaxDays) {
        validateYearEnd(leaveTypeCode, fromYear, toYear);
        LeaveCategory fromCategory = leaveCategoryService.resolveForCodeOn(
                        leaveTypeCode, LocalDate.of(fromYear, 12, 31))
                .or(() -> leaveCategoryService.findAllByCode(leaveTypeCode).stream().findFirst())
                .orElse(null);
        LocalDate carryExpiry = fromCategory != null ? fromCategory.getCarryExpiryDate() : null;

        List<Map<String, Object>> preview = yearEndPreview(leaveTypeCode, fromYear, toYear, carryMaxDays);
        int created = 0;
        Instant now = Instant.now();
        Long actorId = AuthContext.getEmployeeId();
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
            BigDecimal carryAmt = LeaveDays.of(carry);
            BigDecimal before = LeaveDays.of(next.getBalanceDays());
            next.setBalanceDays(LeaveDays.of(before.add(carryAmt)));
            next.setUpdatedAt(now);
            balanceRepository.save(next);

            String sourceId = String.valueOf(fromYear);
            String reason = "Year-end carry from " + fromYear;
            if (carryExpiry != null) {
                sourceId = fromYear + "|" + carryExpiry;
                reason = reason + "; expires " + carryExpiry;
            }
            writeLedger(
                    employeeId,
                    leaveTypeCode,
                    toYear,
                    carryAmt,
                    reason,
                    actorId,
                    now,
                    carryKey,
                    LeaveBalanceLedger.SOURCE_CARRY,
                    sourceId);
            created++;
        }
        auditService.record(actorId, "YEAR_END_CARRY", "LeaveBalance",
                leaveTypeCode + ":" + fromYear + "->" + toYear,
                null,
                Map.of("employeesUpdated", created, "previewCount", preview.size()));
        return Map.of(
                "leaveTypeCode", leaveTypeCode,
                "fromYear", fromYear,
                "toYear", toYear,
                "employeesUpdated", created,
                "previewCount", preview.size()
        );
    }

    public Map<String, Object> yearEndDefaults(String leaveTypeCode) {
        leaveTypeRepository.findByCodeAndActiveTrue(leaveTypeCode)
                .orElseThrow(() -> new IllegalArgumentException("Leave type not found: " + leaveTypeCode));
        int thisYear = LocalDate.now().getYear();
        int fromYear = thisYear - 1;
        int toYear = thisYear;
        LeaveCategory fromCat = leaveCategoryService.resolveForCodeOn(leaveTypeCode, LocalDate.of(fromYear, 6, 15))
                .or(() -> leaveCategoryService.findAllByCode(leaveTypeCode).stream()
                        .filter(c -> c.getPeriodStart() != null && c.getPeriodStart().getYear() == fromYear)
                        .findFirst())
                .orElse(null);
        LeaveCategory any = fromCat != null ? fromCat
                : leaveCategoryService.findAllByCode(leaveTypeCode).stream().findFirst().orElse(null);
        BigDecimal cap = any != null && any.getCarryCapDays() != null
                ? any.getCarryCapDays()
                : new BigDecimal("5");
        String mode = any != null && any.getCarryMode() != null ? any.getCarryMode() : "CAP";
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("leaveTypeCode", leaveTypeCode);
        out.put("fromYear", fromYear);
        out.put("toYear", toYear);
        out.put("carryMaxDays", cap);
        out.put("carryMode", mode);
        out.put("hasFromYearBalances", balanceRepository.findAll().stream()
                .anyMatch(b -> leaveTypeCode.equalsIgnoreCase(b.getLeaveTypeCode()) && b.getYear() == fromYear));
        out.put("hasFromYearCategory", fromCat != null);
        return out;
    }

    private void validateYearEnd(String leaveTypeCode, int fromYear, int toYear) {
        leaveTypeRepository.findByCodeAndActiveTrue(leaveTypeCode)
                .orElseThrow(() -> new IllegalArgumentException("Leave type not found: " + leaveTypeCode));
        if (fromYear >= toYear) {
            throw new IllegalArgumentException("toYear must be after fromYear");
        }
        if (toYear != fromYear + 1) {
            throw new IllegalArgumentException("Year-end carry must be into the next calendar year only (fromYear+1)");
        }
        boolean hasFrom = balanceRepository.findAll().stream()
                .anyMatch(b -> leaveTypeCode.equalsIgnoreCase(b.getLeaveTypeCode()) && b.getYear() == fromYear);
        if (!hasFrom) {
            throw new IllegalStateException(
                    "No leave balances found for " + leaveTypeCode + " in " + fromYear
                            + ". Set / seed that year first.");
        }
        // Overlapping category periods for toYear already existing with conflict vs fromYear period is OK
        // (renew creates next year). Reject if carry already fully posted for every employee? —
        // confirm is idempotent via operation keys; still warn when ALL carries already exist.
        long pendingCarry = balanceRepository.findAll().stream()
                .filter(b -> leaveTypeCode.equalsIgnoreCase(b.getLeaveTypeCode()) && b.getYear() == fromYear)
                .filter(b -> ledgerRepository.findByOperationKey(
                        "CARRY:" + b.getEmployeeId() + ":" + leaveTypeCode + ":" + fromYear + ":" + toYear).isEmpty())
                .count();
        if (pendingCarry == 0) {
            throw new IllegalStateException(
                    "Year-end carry for " + leaveTypeCode + " " + fromYear + "→" + toYear
                            + " is already completed.");
        }

        List<LeaveCategory> cats = leaveCategoryService.findAllByCode(leaveTypeCode);
        for (int i = 0; i < cats.size(); i++) {
            for (int j = i + 1; j < cats.size(); j++) {
                LeaveCategory a = cats.get(i);
                LeaveCategory b = cats.get(j);
                if (a.getPeriodStart() == null || b.getPeriodStart() == null) continue;
                LocalDate as = a.getPeriodStart();
                LocalDate ae = a.getPeriodEnd() != null ? a.getPeriodEnd() : LocalDate.MAX;
                LocalDate bs = b.getPeriodStart();
                LocalDate be = b.getPeriodEnd() != null ? b.getPeriodEnd() : LocalDate.MAX;
                if (!as.isAfter(be) && !bs.isAfter(ae)) {
                    throw new IllegalStateException(
                            "Leave category " + leaveTypeCode
                                    + " has overlapping periods. Fix categories before year-end.");
                }
            }
        }
    }

    private BigDecimal resolveCarryDays(BigDecimal balance, LeaveCategory category, BigDecimal paramCap) {
        if (category != null && category.getCarryMode() != null) {
            return switch (category.getCarryMode().toUpperCase(Locale.ROOT)) {
                case "NONE" -> BigDecimal.ZERO;
                case "ALL" -> balance.max(BigDecimal.ZERO);
                case "CAP" -> {
                    BigDecimal cap = LeaveDays.of(category.getCarryCapDays() != null ? category.getCarryCapDays()
                            : (paramCap != null ? paramCap : new BigDecimal("5")));
                    yield LeaveDays.of(balance.min(cap).max(BigDecimal.ZERO));
                }
                default -> BigDecimal.ZERO;
            };
        }
        BigDecimal cap = LeaveDays.of(paramCap == null ? new BigDecimal("5") : paramCap);
        return LeaveDays.of(balance.min(cap).max(BigDecimal.ZERO));
    }

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
        BigDecimal deltaAmt = LeaveDays.of(delta);
        if (deltaAmt.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal next = LeaveDays.of(LeaveDays.of(balance.getBalanceDays()).add(deltaAmt));
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Insufficient leave balance for system adjust " + leaveTypeCode
                            + " employee " + employeeId + ": have "
                            + LeaveDays.format(balance.getBalanceDays())
                            + ", delta " + LeaveDays.format(deltaAmt));
        }
        Instant now = Instant.now();
        balance.setBalanceDays(next);
        balance.setUpdatedAt(now);
        balanceRepository.save(balance);

        writeLedger(
                employeeId,
                leaveTypeCode,
                year,
                deltaAmt,
                reason == null ? "system-adjust" : reason,
                null,
                now,
                opKey,
                sourceType == null ? LeaveBalanceLedger.SOURCE_ADJUST : sourceType,
                sourceId);
    }

    /**
     * Breakdown for payroll/UI. Ensures:
     * {@code balanceDays = quota + carried + adjustments − taken}
     * and {@code availableDays = balanceDays − pending}.
     */
    @Transactional
    public LeaveBalanceResponse toBreakdown(LeaveBalance b) {
        String code = b.getLeaveTypeCode();
        int year = b.getYear();
        expireCarriedDaysIfDue(b.getEmployeeId(), code, year);
        LeaveBalance fresh = balanceRepository
                .findByEmployeeIdAndLeaveTypeCodeAndYear(b.getEmployeeId(), code, year)
                .orElse(b);

        // Heal half-day quantization on the running balance first.
        BigDecimal whole = LeaveDays.of(fresh.getBalanceDays());
        if (fresh.getBalanceDays() == null || fresh.getBalanceDays().compareTo(whole) != 0) {
            fresh.setBalanceDays(whole);
            fresh.setUpdatedAt(Instant.now());
            balanceRepository.save(fresh);
        }

        backfillBlankSourceTypes(fresh.getEmployeeId(), code, year);
        reconcileLedgerToRunningBalance(fresh);

        fresh = balanceRepository
                .findByEmployeeIdAndLeaveTypeCodeAndYear(fresh.getEmployeeId(), code, year)
                .orElse(fresh);

        String name = leaveTypeRepository.findByCodeIgnoreCase(code).map(LeaveType::getName).orElse(code);
        LeaveLedgerMath.Parts parts = LeaveLedgerMath.classify(
                ledgerRepository.findByEmployeeIdOrderByCreatedAtDesc(fresh.getEmployeeId()),
                code,
                year);
        BigDecimal pending = pendingDays(fresh.getEmployeeId(), code, year);
        return LeaveBalanceResponse.bare(
                fresh, name, parts.quota(), parts.carried(), parts.adjustments(), parts.taken(), pending);
    }

    /**
     * Align ledger composition with {@code balance_days} without changing the running balance.
     * Missing OPENING for legacy seed rows is back-filled; residual gaps become ADJUST.
     */
    @Transactional
    public void reconcileLedgerToRunningBalance(LeaveBalance balance) {
        if (balance == null || balance.getEmployeeId() == null || balance.getLeaveTypeCode() == null) {
            return;
        }
        Long employeeId = balance.getEmployeeId();
        String code = balance.getLeaveTypeCode();
        int year = balance.getYear();

        List<LeaveBalanceLedger> ledgers = ledgerRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .filter(l -> code.equalsIgnoreCase(l.getLeaveTypeCode()) && l.getYear() == year)
                .toList();

        LeaveLedgerMath.Parts parts = LeaveLedgerMath.classify(ledgers, code, year);
        BigDecimal stored = LeaveDays.of(balance.getBalanceDays());
        BigDecimal ledgerNet = parts.netBalance();
        BigDecimal gap = LeaveDays.of(stored.subtract(ledgerNet));
        if (gap.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        boolean hasQuotaGrant = ledgers.stream()
                .anyMatch(l -> LeaveLedgerMath.isQuotaGrant(LeaveLedgerMath.resolveSource(l)));

        Instant now = Instant.now();
        String openKey = "OPENING:" + employeeId + ":" + code + ":" + year;
        if (!hasQuotaGrant
                && gap.compareTo(BigDecimal.ZERO) > 0
                && ledgerRepository.findByOperationKey(openKey).isEmpty()) {
            writeLedger(
                    employeeId,
                    code,
                    year,
                    gap,
                    "Reconciled opening to running balance",
                    null,
                    now,
                    openKey,
                    LeaveBalanceLedger.SOURCE_OPENING,
                    code);
            return;
        }

        String reconKey = "RECONCILE:" + employeeId + ":" + code + ":" + year;
        if (ledgerRepository.findByOperationKey(reconKey).isPresent()) {
            reconKey = reconKey + ":" + now.toEpochMilli();
        }
        writeLedger(
                employeeId,
                code,
                year,
                gap,
                "Reconcile ledger to running balance",
                null,
                now,
                reconKey,
                LeaveBalanceLedger.SOURCE_ADJUST,
                code);
    }

    /** Persist inferred source_type for legacy null/blank ledger rows (idempotent). */
    private void backfillBlankSourceTypes(Long employeeId, String leaveTypeCode, int year) {
        for (LeaveBalanceLedger l : ledgerRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId)) {
            if (!leaveTypeCode.equalsIgnoreCase(l.getLeaveTypeCode()) || l.getYear() != year) {
                continue;
            }
            if (l.getSourceType() != null && !l.getSourceType().isBlank()) {
                continue;
            }
            String inferred = LeaveLedgerMath.resolveSource(l);
            l.setSourceType(inferred);
            if (l.getDeltaDays() != null) {
                l.setDeltaDays(LeaveDays.of(l.getDeltaDays()));
            }
            ledgerRepository.save(l);
        }
    }

    /**
     * Payroll / HR: reconcile every balance for a year so formula matches running days.
     * @return number of balances that needed a ledger correction
     */
    @Transactional
    public int reconcileAllForYear(Integer year) {
        int y = year == null ? LocalDate.now().getYear() : year;
        int fixed = 0;
        for (LeaveBalance bal : balanceRepository.findAll()) {
            if (bal.getYear() != y) {
                continue;
            }
            LeaveLedgerMath.Parts before = LeaveLedgerMath.classify(
                    ledgerRepository.findByEmployeeIdOrderByCreatedAtDesc(bal.getEmployeeId()),
                    bal.getLeaveTypeCode(),
                    bal.getYear());
            BigDecimal gap = LeaveDays.of(LeaveDays.of(bal.getBalanceDays()).subtract(before.netBalance()));
            backfillBlankSourceTypes(bal.getEmployeeId(), bal.getLeaveTypeCode(), bal.getYear());
            reconcileLedgerToRunningBalance(bal);
            if (gap.compareTo(BigDecimal.ZERO) != 0) {
                fixed++;
            }
        }
        return fixed;
    }

    /**
     * When a CARRY ledger's encoded expiry date has passed, remove unused carried days
     * without touching the period's OPENING/QUOTA entitlement.
     * CARRY sourceId formats: {@code fromYear} or {@code fromYear|YYYY-MM-DD}.
     */
    @Transactional
    public void expireCarriedDaysIfDue(Long employeeId, String leaveTypeCode, int year) {
        if (employeeId == null || leaveTypeCode == null || leaveTypeCode.isBlank()) {
            return;
        }
        LocalDate today = LocalDate.now();
        List<LeaveBalanceLedger> ledgers = ledgerRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .filter(l -> leaveTypeCode.equalsIgnoreCase(l.getLeaveTypeCode()) && l.getYear() == year)
                .toList();

        LeaveLedgerMath.Parts parts = LeaveLedgerMath.classify(ledgers, leaveTypeCode, year);
        // Protect remaining annual quota so expiry only burns unused carry.
        BigDecimal protectedFloor = parts.quota().subtract(parts.taken()).max(BigDecimal.ZERO);

        Instant now = Instant.now();
        for (LeaveBalanceLedger carry : ledgers) {
            if (!LeaveBalanceLedger.SOURCE_CARRY.equalsIgnoreCase(
                    carry.getSourceType() == null ? "" : carry.getSourceType())) {
                continue;
            }
            LocalDate expiry = parseCarryExpiry(carry.getSourceId());
            if (expiry == null || today.isBefore(expiry)) {
                continue;
            }
            String expireKey = "EXPIRE:" + (carry.getOperationKey() == null ? carry.getId() : carry.getOperationKey());
            if (ledgerRepository.findByOperationKey(expireKey).isPresent()) {
                continue;
            }
            BigDecimal carryAmt = LeaveDays.of(carry.getDeltaDays());
            if (carryAmt.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LeaveBalance balance = balanceRepository
                    .findByEmployeeIdAndLeaveTypeCodeAndYear(employeeId, leaveTypeCode, year)
                    .orElse(null);
            if (balance == null) {
                continue;
            }
            BigDecimal have = LeaveDays.of(balance.getBalanceDays());
            BigDecimal burnable = have.subtract(LeaveDays.of(protectedFloor)).max(BigDecimal.ZERO);
            BigDecimal toExpire = LeaveDays.of(carryAmt.min(burnable));
            if (toExpire.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            balance.setBalanceDays(LeaveDays.of(have.subtract(toExpire)));
            balance.setUpdatedAt(now);
            balanceRepository.save(balance);
            writeLedger(
                    employeeId,
                    leaveTypeCode,
                    year,
                    toExpire.negate(),
                    "Carry expired on " + expiry,
                    null,
                    now,
                    expireKey,
                    LeaveBalanceLedger.SOURCE_EXPIRE,
                    carry.getSourceId());
            protectedFloor = protectedFloor; // unchanged — quota floor stays
        }
    }

    private static LocalDate parseCarryExpiry(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return null;
        }
        String s = sourceId.trim();
        int pipe = s.indexOf('|');
        if (pipe < 0 || pipe + 1 >= s.length()) {
            return null;
        }
        try {
            return LocalDate.parse(s.substring(pipe + 1).trim());
        } catch (Exception ignored) {
            return null;
        }
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
                && ledger.getReason().toLowerCase(Locale.ROOT).contains("approved");
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
