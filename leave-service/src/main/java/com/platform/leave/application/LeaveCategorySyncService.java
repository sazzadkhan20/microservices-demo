package com.platform.leave.application;

import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.domain.LeaveBalance;
import com.platform.leave.domain.LeaveCategory;
import com.platform.leave.domain.LeaveType;
import com.platform.leave.client.people.PeopleEmployeeGateway;
import com.platform.leave.infrastructure.persistence.LeaveBalanceRepository;
import com.platform.leave.infrastructure.persistence.LeaveCategoryRepository;
import com.platform.leave.infrastructure.persistence.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    private final PeopleEmployeeGateway employeeRepository;

    public LeaveCategorySyncService(
            LeaveTypeRepository leaveTypeRepository,
            LeaveCategoryRepository leaveCategoryRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            PeopleEmployeeGateway employeeRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.employeeRepository = employeeRepository;
    }

    /** Ensure every active category has a matching leave_type row (repairs orphans). */
    @Transactional
    public void syncAllActiveCategories() {
        for (LeaveCategory c : leaveCategoryRepository.findAll()) {
            if (c.getCode() == null || c.getCode().isBlank()) {
                continue;
            }
            upsertLeaveType(c, false);
        }
    }

    /**
     * Upsert leave_type from category. When {@code seedBalances} and quota &gt; 0,
     * creates current-year balances for active employees that lack a row.
     */
    @Transactional
    public LeaveType upsertLeaveType(LeaveCategory category, boolean seedBalances) {
        String code = category.getCode() == null ? "" : category.getCode().trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Leave category code is required");
        }
        LeaveType type = leaveTypeRepository.findByCodeIgnoreCase(code).orElseGet(LeaveType::new);
        boolean isNew = type.getId() == null;
        type.setCode(code);
        type.setName(category.getName() == null || category.getName().isBlank() ? code : category.getName().trim());
        type.setPaid(category.isPaid());
        type.setActive(category.isActive());
        // Unpaid / zero-quota categories: still requestable; balance rules follow paid flag.
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
            seedOpeningBalances(code, category.getQuotaDays());
        }
        return saved;
    }

    private void seedOpeningBalances(String leaveTypeCode, BigDecimal quota) {
        int year = LocalDate.now().getYear();
        Instant now = Instant.now();
        for (EmployeeView e : employeeRepository.findAll()) {
            if (!e.isActive()) {
                continue;
            }
            if (leaveBalanceRepository.findByEmployeeIdAndLeaveTypeCodeAndYear(e.getId(), leaveTypeCode, year)
                    .isPresent()) {
                continue;
            }
            LeaveBalance b = new LeaveBalance();
            b.setEmployeeId(e.getId());
            b.setLeaveTypeCode(leaveTypeCode);
            b.setYear(year);
            b.setBalanceDays(quota);
            b.setUpdatedAt(now);
            leaveBalanceRepository.save(b);
        }
    }
}
