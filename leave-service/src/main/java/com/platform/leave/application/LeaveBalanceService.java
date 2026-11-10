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
            AuditService auditService,
            EntityManager entityManager) {
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.employeeRepository = employeeRepository;
        this.dataScopeService = dataScopeService;
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.leaveCategoryService = leaveCategoryService;
        this.auditService = auditService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> listForEmployee(Long employeeId, Integer year) {
        dataScopeService.requireAccess(AuthContext.requireEmployeeId(), employeeId, "leave", "view");
        int y = year == null ? LocalDate.now().getYear() : year;
        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        return balanceRepository.findByEmployeeIdAndYear(employeeId, y).stream()
                .map(b -> toBreakdown(b))
                .toList();
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
        Long actorId = request.actorEmployeeId() != null
                ? request.actorEmployeeId()
                : AuthContext.getEmployeeId();
        if (request.deltaDays() == null || request.deltaDays().compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("deltaDays must be non-zero");
        }

        int year = request.year() == null ? LocalDate.now().getYear() : request.year();
        LeaveBalance balance = getOrCreate(employee.getId(), type.getCode(), year);
        BigDecimal before = balance.getBalanceDays();
        BigDecimal next = before.add(request.deltaDays());
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Balance would go negative: current="
                    + before + ", delta=" + request.deltaDays());
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
                request.reason().trim(),
                actorId,
                now,
                UUID.randomUUID().toString(),
                LeaveBalanceLedger.SOURCE_ADJUST,
                String.valueOf(employee.getId()));

        auditService.record(actorId, "ADJUST", "LeaveBalance",
                employee.getId() + ":" + type.getCode() + ":" + year,
                Map.of("balanceDays", before),
                Map.of("balanceDays", next, "deltaDays", request.deltaDays(), "reason", request.reason().trim()));

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
        if (days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Opening grant must be a positive number of days");
        }
        LeaveType type = leaveTypeRepository.findByCodeAndActiveTrue(leaveTypeCode)
                .orElseThrow(() -> new IllegalArgumentException("Leave type not found: " + leaveTypeCode));
        EmployeeView employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        String openKey = "OPENING:" + employeeId + ":" + type.getCode() + ":" + year;
        if (ledgerRepository.findByOperationKey(openKey).isPresent()) {
            throw new IllegalStateException(
                    "Opening balance already recorded for " + type.getCode() + " / " + year
                            + ". Use Adjust balances for later corrections.");
        }

        LeaveBalance balance = getOrCreate(employeeId, type.getCode(), year);
        BigDecimal before = balance.getBalanceDays();
        BigDecimal next = before.add(days);
        Instant now = Instant.now();
        balance.setBalanceDays(next);
        balance.setUpdatedAt(now);
        balanceRepository.save(balance);

        Long actorId = AuthContext.getEmployeeId();
        writeLedger(
                employeeId,
                type.getCode(),
                year,
                days,
                reason.trim(),
                actorId,
                now,
                openKey,
                LeaveBalanceLedger.SOURCE_OPENING,
                type.getCode());

        auditService.record(actorId, "OPENING", "LeaveBalance",
                employeeId + ":" + type.getCode() + ":" + year,
                Map.of("balanceDays", before),
                Map.of("balanceDays", next, "deltaDays", days, "reason", reason.trim()));

        return toBreakdown(balance);
    }

    @Transactional
    public BigDecimal available(Long employeeId, String leaveTypeCode, int year) {
        return balanceRepository.findByEmployeeIdAndLeaveTypeCodeAndYear(employeeId, leaveTypeCode, year)
                .map(LeaveBalance::getBalanceDays)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public BigDecimal availableAfterPending(Long employeeId, String leaveTypeCode, int year) {
        BigDecimal balance = available(employeeId, leaveTypeCode, year);
        return balance.subtract(pendingDays(employeeId, leaveTypeCode, year));
    }

    @Transactional(readOnly = true)
    public BigDecimal pendingDays(Long employeeId, String leaveTypeCode, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year + 1, 1, 1);
        return leaveApplicationRepository
                .findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .filter(a -> leaveTypeCode.equalsIgnoreCase(a.getLeaveTypeCode()))
                .filter(a -> PENDING_STATUSES.contains(a.getStatus()))
                .filter(a -> !a.isBalanceDeducted())
                .filter(a -> a.getStartDate() != null
                        && !a.getStartDate().isBefore(yearStart)
                        && a.getStartDate().isBefore(yearEnd))
                .map(a -> a.getWorkingDaysCounted() == null ? BigDecimal.ZERO : a.getWorkingDaysCounted())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
     * Mid-period quota change: apply delta to every existing balance for that type/year.
     * Employees who already took leave keep their taken days; remaining moves with the quota.
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
                                + " type " + leaveTypeCode + " year " + year
                                + " (already used leave reduces remaining below zero). "
                                + "Lower the quota less, or adjust individual balances first.");
            }
        }
        Instant now = Instant.now();
        Long actorId = AuthContext.getEmployeeId();
        for (LeaveBalance b : balances) {
            BigDecimal before = b.getBalanceDays();
            BigDecimal next = before.add(delta);
            b.setBalanceDays(next);
            b.setUpdatedAt(now);
            balanceRepository.save(b);

            writeLedger(
                    b.getEmployeeId(),
                    leaveTypeCode,
                    year,
                    delta,
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
                    Map.of("balanceDays", next, "deltaDays", delta));
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
            row.put("carryCapDays", category != null ? category.getCarryCapDays() : carryMaxDays);
            rows.add(row);
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> yearEndConfirm(String leaveTypeCode, int fromYear, int toYear, BigDecimal carryMaxDays) {
        validateYearEnd(leaveTypeCode, fromYear, toYear);
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
            BigDecimal before = next.getBalanceDays();
            next.setBalanceDays(before.add(carry));
            next.setUpdatedAt(now);
            balanceRepository.save(next);

            writeLedger(
                    employeeId,
                    leaveTypeCode,
                    toYear,
                    carry,
                    "Year-end carry from " + fromYear,
                    actorId,
                    now,
                    carryKey,
                    LeaveBalanceLedger.SOURCE_CARRY,
                    String.valueOf(fromYear));
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

    public LeaveBalanceResponse toBreakdown(LeaveBalance b) {
        String code = b.getLeaveTypeCode();
        int year = b.getYear();
        String name = leaveTypeRepository.findByCodeIgnoreCase(code).map(LeaveType::getName).orElse(code);

        BigDecimal quota = BigDecimal.ZERO;
        BigDecimal carried = BigDecimal.ZERO;
        BigDecimal adjustments = BigDecimal.ZERO;
        BigDecimal taken = BigDecimal.ZERO;

        for (LeaveBalanceLedger l : ledgerRepository.findByEmployeeIdOrderByCreatedAtDesc(b.getEmployeeId())) {
            if (!code.equalsIgnoreCase(l.getLeaveTypeCode()) || l.getYear() != year) continue;
            BigDecimal d = l.getDeltaDays() == null ? BigDecimal.ZERO : l.getDeltaDays();
            String src = l.getSourceType() == null ? "" : l.getSourceType().toUpperCase(Locale.ROOT);
            switch (src) {
                case LeaveBalanceLedger.SOURCE_OPENING, LeaveBalanceLedger.SOURCE_QUOTA -> quota = quota.add(d);
                case LeaveBalanceLedger.SOURCE_CARRY -> carried = carried.add(d);
                case LeaveBalanceLedger.SOURCE_ADJUST, LeaveBalanceLedger.SOURCE_COVER -> adjustments = adjustments.add(d);
                case LeaveBalanceLedger.SOURCE_APPROVE -> taken = taken.add(d.abs());
                case LeaveBalanceLedger.SOURCE_CANCEL -> taken = taken.subtract(d.abs());
                default -> {
                    // unknown: treat positive as adjustment-like for display safety
                    if (d.compareTo(BigDecimal.ZERO) >= 0) adjustments = adjustments.add(d);
                    else taken = taken.add(d.abs());
                }
            }
        }
        if (taken.compareTo(BigDecimal.ZERO) < 0) taken = BigDecimal.ZERO;
        BigDecimal pending = pendingDays(b.getEmployeeId(), code, year);
        return LeaveBalanceResponse.bare(b, name, quota, carried, adjustments, taken, pending);
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
