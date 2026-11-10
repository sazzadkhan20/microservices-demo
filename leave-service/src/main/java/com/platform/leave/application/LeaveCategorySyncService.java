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
import java.util.Locale;

/**
 * Keeps {@code leave_type} (apply / balance catalogue) in sync with {@code leave_category}
 * (quota / rules). Admins create categories in the UI; employees request leave by leave_type.
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

        if (seedBalances && category.isActive()
                && category.getQuotaDays() != null
                && category.getQuotaDays().compareTo(BigDecimal.ZERO) > 0) {
            seedOpeningBalances(category, code);
        }
        return saved;
    }

    private void seedOpeningBalances(LeaveCategory category, String leaveTypeCode) {
        int year = leaveCategoryService.balanceYear(category);
        Instant now = Instant.now();
        for (EmployeeView e : employeeRepository.findAll()) {
            if (!e.isActive()) {
                continue;
            }
            if (!leaveAppliesToService.isEligible(e, category)) {
                continue;
            }
            if (leaveBalanceRepository.findByEmployeeIdAndLeaveTypeCodeAndYear(e.getId(), leaveTypeCode, year)
                    .isPresent()) {
                continue;
            }
            BigDecimal grant = leaveCategoryService.effectiveQuota(category, e);
            if (grant.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LeaveBalance b = new LeaveBalance();
            b.setEmployeeId(e.getId());
            b.setLeaveTypeCode(leaveTypeCode);
            b.setYear(year);
            b.setBalanceDays(grant);
            b.setUpdatedAt(now);
            leaveBalanceRepository.save(b);

            LeaveBalanceLedger ledger = new LeaveBalanceLedger();
            ledger.setEmployeeId(e.getId());
            ledger.setLeaveTypeCode(leaveTypeCode);
            ledger.setYear(year);
            ledger.setDeltaDays(grant);
            ledger.setReason(category.isProRata() ? "Opening quota (pro-rata)" : "Opening quota");
            ledger.setCreatedAt(now);
            ledger.setOperationKey("OPENING:" + e.getId() + ":" + leaveTypeCode + ":" + year);
            ledger.setSourceType(LeaveBalanceLedger.SOURCE_OPENING);
            ledger.setSourceId(leaveTypeCode);
            leaveBalanceLedgerRepository.save(ledger);