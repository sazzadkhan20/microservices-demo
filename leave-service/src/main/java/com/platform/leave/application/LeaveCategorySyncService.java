package com.platform.leave.application;

import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.domain.LeaveBalance;
import com.platform.leave.domain.LeaveBalanceLedger;
import com.platform.leave.domain.LeaveCategory;
import com.platform.leave.domain.LeaveType;
import com.platform.leave.client.people.PeopleEmployeeGateway;
import com.platform.leave.infrastructure.persistence.LeaveBalanceLedgerRepository;
import com.platform.leave.infrastructure.persistence.LeaveBalanceRepository;
import com.platform.leave.infrastructure.persistence.LeaveCategoryRepository;
import com.platform.leave.infrastructure.persistence.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * Keeps {@code leave_type} (apply / balance catalogue) in sync with {@code leave_category}
 * (quota / rules). Admins create categories in the UI; employees request leave by leave_type.
 *
 * Opening / mid-period quota grants use {@link LeaveCategoryService#effectiveQuota} so
 * pro-rata joiners receive a fraction of the annual quota, not a flat cascade.
 */
@Service
public class LeaveCategorySyncService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveCategoryRepository leaveCategoryRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveBalanceLedgerRepository leaveBalanceLedgerRepository;
    private final PeopleEmployeeGateway employeeRepository;
    private final LeaveAppliesToService leaveAppliesToService;
    private final LeaveCategoryService leaveCategoryService;

    public LeaveCategorySyncService(
            LeaveTypeRepository leaveTypeRepository,
            LeaveCategoryRepository leaveCategoryRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            LeaveBalanceLedgerRepository leaveBalanceLedgerRepository,
            PeopleEmployeeGateway employeeRepository,
            LeaveAppliesToService leaveAppliesToService,
            LeaveCategoryService leaveCategoryService) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveBalanceLedgerRepository = leaveBalanceLedgerRepository;
        this.employeeRepository = employeeRepository;
        this.leaveAppliesToService = leaveAppliesToService;
        this.leaveCategoryService = leaveCategoryService;
    }

    @Transactional
    public void syncAllActiveCategories() {
        for (LeaveCategory c : leaveCategoryRepository.findAll()) {
            if (c.getCode() == null || c.getCode().isBlank()) {
                continue;
            }
            upsertLeaveType(c, false);
        }
    }

    @Transactional
    public LeaveType upsertLeaveType(LeaveCategory category, boolean seedBalances) {
        String code = category.getCode() == null ? "" : category.getCode().trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Leave category code is required");
        }
        LeaveType type = leaveTypeRepository.findByCodeIgnoreCase(code).orElseGet(LeaveType::new);
        boolean isNew = type.getId() == null;
        type.setCode(code);
        // Prefer current-period category name when syncing; renew keeps same name anyway.
        if (isNew || leaveCategoryService.isCurrentPeriod(category) || type.getName() == null || type.getName().isBlank()) {
            type.setName(category.getName() == null || category.getName().isBlank() ? code : category.getName().trim());
        }
        type.setPaid(category.isPaid());
        // leave_type stays active if ANY period row is active
        boolean anyActive = leaveCategoryService.findAllByCode(code).stream().anyMatch(LeaveCategory::isActive);
        type.setActive(anyActive);
        boolean unpaid = !category.isPaid();
        type.setRequiresBalance(!unpaid);
        type.setAllowWhenZeroBalance(unpaid);
        type.setDeductBalance(!unpaid);
        type.setAllowWhenCoverageFail(true);
        if (isNew) {
            int nextOrder = leaveTypeRepository.findAll().stream()
                    .mapToInt(LeaveType::getSortOrder)
                    .max()
                    .orElse(0) + 1;
            type.setSortOrder(nextOrder);
        }
        LeaveType saved = leaveTypeRepository.save(type);

        if (seedBalances) {
            resyncOpeningQuotas(category);
        }
        return saved;
    }

    /**
     * Align every eligible employee's OPENING+QUOTA ledger total to {@code effectiveQuota}
     * (pro-rata aware). Safe to call on create, renew, and mid-period quota / pro-rata edits.
     *
     * @return number of employees whose balance changed
     */
    @Transactional
    public int resyncOpeningQuotas(LeaveCategory category) {
        if (category == null || !category.isActive()
                || category.getCode() == null || category.getCode().isBlank()) {
            return 0;
        }
        String leaveTypeCode = category.getCode().trim().toUpperCase(Locale.ROOT);
        int year = leaveCategoryService.balanceYear(category);
        Instant now = Instant.now();
        int changed = 0;

        for (EmployeeView e : employeeRepository.findAll()) {
            if (!e.isActive()) {
                continue;
            }
            if (!leaveAppliesToService.isEligible(e, category)) {
                continue;
            }
            if (leaveAppliesToService.isHourlySchedule(e)) {
                continue;
            }
            BigDecimal target = LeaveDays.of(leaveCategoryService.effectiveQuota(category, e));
            if (target.compareTo(BigDecimal.ZERO) < 0) {
                target = BigDecimal.ZERO;
            }

            LeaveBalance balance = leaveBalanceRepository
                    .findByEmployeeIdAndLeaveTypeCodeAndYear(e.getId(), leaveTypeCode, year)
                    .orElseGet(() -> {
                        LeaveBalance b = new LeaveBalance();
                        b.setEmployeeId(e.getId());
                        b.setLeaveTypeCode(leaveTypeCode);
                        b.setYear(year);
                        b.setBalanceDays(BigDecimal.ZERO);
                        return b;
                    });

            LedgerParts parts = ledgerParts(e.getId(), leaveTypeCode, year);
            BigDecimal ledgerQuota = LeaveDays.of(parts.quota());
            // Expected running balance = target quota + carry + adjustments − taken
            BigDecimal expected = LeaveDays.of(
                    target.add(LeaveDays.of(parts.carried()))
                            .add(LeaveDays.of(parts.adjustments()))
                            .subtract(LeaveDays.of(parts.taken())));
            if (expected.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException(
                        "Quota/pro-rata sync would make balance negative for employee " + e.getId()
                                + " type " + leaveTypeCode + " year " + year
                                + " (targetQuota=" + target + ", taken=" + parts.taken()
                                + ", balance=" + balance.getBalanceDays() + "). "
                                + "Adjust individual balances first, then retry.");
            }

            BigDecimal balanceDelta = expected.subtract(LeaveDays.of(balance.getBalanceDays()));
            BigDecimal quotaDelta = LeaveDays.of(target.subtract(ledgerQuota));
            if (balanceDelta.compareTo(BigDecimal.ZERO) == 0 && quotaDelta.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            if (balanceDelta.compareTo(BigDecimal.ZERO) != 0) {
                balance.setBalanceDays(LeaveDays.of(expected));
                balance.setUpdatedAt(now);
                leaveBalanceRepository.save(balance);
            }

            if (quotaDelta.compareTo(BigDecimal.ZERO) != 0) {
                boolean firstOpening = ledgerQuota.compareTo(BigDecimal.ZERO) == 0
                        && quotaDelta.compareTo(BigDecimal.ZERO) > 0
                        && leaveBalanceLedgerRepository.findByOperationKey(
                                "OPENING:" + e.getId() + ":" + leaveTypeCode + ":" + year).isEmpty();

                LeaveBalanceLedger ledger = new LeaveBalanceLedger();
                ledger.setEmployeeId(e.getId());
                ledger.setLeaveTypeCode(leaveTypeCode);
                ledger.setYear(year);
                ledger.setDeltaDays(quotaDelta);
                if (firstOpening) {
                    ledger.setReason(category.isProRata() ? "Opening quota (pro-rata)" : "Opening quota");
                    ledger.setSourceType(LeaveBalanceLedger.SOURCE_OPENING);
                    ledger.setOperationKey("OPENING:" + e.getId() + ":" + leaveTypeCode + ":" + year);
                } else {
                    ledger.setReason(category.isProRata()
                            ? "Quota sync (pro-rata)"
                            : "Quota sync");
                    ledger.setSourceType(LeaveBalanceLedger.SOURCE_QUOTA);
                    ledger.setOperationKey("QUOTA-SYNC:" + e.getId() + ":" + leaveTypeCode + ":" + year
                            + ":" + now.toEpochMilli());
                }
                ledger.setSourceId(leaveTypeCode);
                ledger.setCreatedAt(now);
                leaveBalanceLedgerRepository.save(ledger);
            } else if (balanceDelta.compareTo(BigDecimal.ZERO) != 0) {
                // Balance drifted from ledger math — record a correcting quota row of 0? skip ledger.
            }
            changed++;
        }
        return changed;
    }

    /**
     * New hire / employee upsert: seed opening balances for every active category whose period
     * covers today (pro-rata from join / start date).
     */
    @Transactional
    public int ensureOpeningBalancesForEmployee(Long employeeId) {
        if (employeeId == null) {
            return 0;
        }
        Optional<EmployeeView> empOpt = employeeRepository.findById(employeeId);
        if (empOpt.isEmpty()) {
            return 0;
        }
        EmployeeView employee = empOpt.get();
        if (!employee.isActive() || leaveAppliesToService.isHourlySchedule(employee)) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        int changed = 0;
        for (LeaveCategory category : leaveCategoryRepository.findAll()) {
            if (category == null || !category.isActive()
                    || category.getCode() == null || category.getCode().isBlank()) {
                continue;
            }
            if (!leaveCategoryService.covers(category, today)) {
                continue;
            }
            if (!leaveAppliesToService.isEligible(employee, category)) {
                continue;
            }
            BigDecimal target = LeaveDays.of(leaveCategoryService.effectiveQuota(category, employee));
            if (target.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String code = category.getCode().trim().toUpperCase(Locale.ROOT);
            int year = leaveCategoryService.balanceYear(category);
            BigDecimal currentQuota = ledgerParts(employeeId, code, year).quota();
            if (currentQuota.compareTo(BigDecimal.ZERO) > 0) {
                continue; // already has opening/quota for this period
            }
            // Reuse single-employee path via temporary full resync would be heavy; write directly.
            Instant now = Instant.now();
            LeaveBalance balance = leaveBalanceRepository
                    .findByEmployeeIdAndLeaveTypeCodeAndYear(employeeId, code, year)
                    .orElseGet(() -> {
                        LeaveBalance b = new LeaveBalance();
                        b.setEmployeeId(employeeId);
                        b.setLeaveTypeCode(code);
                        b.setYear(year);
                        b.setBalanceDays(BigDecimal.ZERO);
                        return b;
                    });
            BigDecimal next = LeaveDays.of(
                    (balance.getBalanceDays() == null ? BigDecimal.ZERO : balance.getBalanceDays()).add(target));
            balance.setBalanceDays(next);
            balance.setUpdatedAt(now);
            leaveBalanceRepository.save(balance);

            String openKey = "OPENING:" + employeeId + ":" + code + ":" + year;
            if (leaveBalanceLedgerRepository.findByOperationKey(openKey).isEmpty()) {
                LeaveBalanceLedger ledger = new LeaveBalanceLedger();
                ledger.setEmployeeId(employeeId);
                ledger.setLeaveTypeCode(code);
                ledger.setYear(year);
                ledger.setDeltaDays(LeaveDays.of(target));
                ledger.setReason(category.isProRata() ? "Opening quota (pro-rata)" : "Opening quota");
                ledger.setSourceType(LeaveBalanceLedger.SOURCE_OPENING);
                ledger.setSourceId(code);
                ledger.setOperationKey(openKey);
                ledger.setCreatedAt(now);
                leaveBalanceLedgerRepository.save(ledger);
                changed++;
            }
        }
        return changed;
    }

    private record LedgerParts(BigDecimal quota, BigDecimal carried, BigDecimal adjustments, BigDecimal taken) {}

    private LedgerParts ledgerParts(Long employeeId, String leaveTypeCode, int year) {
        LeaveLedgerMath.Parts parts = LeaveLedgerMath.classify(
                leaveBalanceLedgerRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId),
                leaveTypeCode,
                year);
        return new LedgerParts(parts.quota(), parts.carried(), parts.adjustments(), parts.taken());
    }
}
