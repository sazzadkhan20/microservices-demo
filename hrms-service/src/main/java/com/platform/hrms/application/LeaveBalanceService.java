package com.platform.hrms.application;

import com.platform.common.security.AuthContext;
import com.platform.hrms.api.dto.LeaveBalanceAdjustRequest;
import com.platform.hrms.api.dto.LeaveBalanceResponse;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.LeaveApplication;
import com.platform.hrms.domain.LeaveBalance;
import com.platform.hrms.domain.LeaveBalanceLedger;
import com.platform.hrms.domain.LeaveCategory;
import com.platform.hrms.domain.LeaveType;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.hrms.infrastructure.persistence.LeaveBalanceLedgerRepository;
import com.platform.hrms.infrastructure.persistence.LeaveBalanceRepository;
import com.platform.hrms.infrastructure.persistence.LeaveCategoryRepository;
import com.platform.hrms.infrastructure.persistence.LeaveTypeRepository;
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

/**
 * HR-managed leave balances (add/deduct). Shared across tenants — rules are data.
 */
@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository balanceRepository;
    private final LeaveBalanceLedgerRepository ledgerRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final DataScopeService dataScopeService;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final LeaveCategoryRepository leaveCategoryRepository;

    private static final Set<String> PENDING_STATUSES = Set.of(
            LeaveApplication.STATUS_PENDING_APPROVAL,
            LeaveApplication.STATUS_PENDING_RELIEF,
            LeaveApplication.STATUS_SUBMITTED,
            LeaveApplication.STATUS_PENDING_CANCEL
    );

    public LeaveBalanceService(
            LeaveBalanceRepository balanceRepository,
            LeaveBalanceLedgerRepository ledgerRepository,
            LeaveTypeRepository leaveTypeRepository,
            EmployeeRepository employeeRepository,
            DataScopeService dataScopeService,
            LeaveApplicationRepository leaveApplicationRepository,
            LeaveCategoryRepository leaveCategoryRepository) {
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.employeeRepository = employeeRepository;
        this.dataScopeService = dataScopeService;
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
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
        Employee employee = employeeRepository.findById(request.employeeId())
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

        LeaveBalanceLedger ledger = new LeaveBalanceLedger();
        ledger.setEmployeeId(employee.getId());
        ledger.setLeaveTypeCode(type.getCode());
        ledger.setYear(year);
        ledger.setDeltaDays(request.deltaDays());
        ledger.setReason(request.reason());
        ledger.setActorEmployeeId(request.actorEmployeeId());
        ledger.setCreatedAt(now);
        ledgerRepository.save(ledger);

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
                .filter(a -> a.getStartDate() != null
                        && !a.getStartDate().isBefore(yearStart)
                        && a.getStartDate().isBefore(yearEnd))
                .map(a -> a.getWorkingDaysCounted() == null ? BigDecimal.ZERO : a.getWorkingDaysCounted())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return balance.subtract(pendingDays);
    }

    /**
     * FR-LV-18 — restore balance on cancel-approved.
     */
    @Transactional
    public void restoreOnCancel(Long employeeId, String leaveTypeCode, int year, BigDecimal days) {
        if (days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        LeaveBalance balance = getOrCreate(employeeId, leaveTypeCode, year);
        Instant now = Instant.now();
        balance.setBalanceDays(balance.getBalanceDays().add(days));
        balance.setUpdatedAt(now);
        balanceRepository.save(balance);

        LeaveBalanceLedger ledger = new LeaveBalanceLedger();
        ledger.setEmployeeId(employeeId);
        ledger.setLeaveTypeCode(leaveTypeCode);
        ledger.setYear(year);
        ledger.setDeltaDays(days);
        ledger.setReason("Leave cancel restore");
        ledger.setActorEmployeeId(AuthContext.getEmployeeId());
        ledger.setCreatedAt(now);
        ledgerRepository.save(ledger);
    }

    /**
     * FR-LV-11 / carry: cascade a quota change to all existing balances for the current year.
     */
    @Transactional
    public int cascadeQuotaDelta(String leaveTypeCode, BigDecimal delta, int year) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        List<LeaveBalance> balances = balanceRepository.findAll().stream()
                .filter(b -> leaveTypeCode.equalsIgnoreCase(b.getLeaveTypeCode()) && b.getYear() == year)
                .toList();
        Instant now = Instant.now();
        for (LeaveBalance b : balances) {
            BigDecimal next = b.getBalanceDays().add(delta);
            if (next.compareTo(BigDecimal.ZERO) < 0) {
                next = BigDecimal.ZERO;
            }
            b.setBalanceDays(next);
            b.setUpdatedAt(now);
            balanceRepository.save(b);

            LeaveBalanceLedger ledger = new LeaveBalanceLedger();
            ledger.setEmployeeId(b.getEmployeeId());
            ledger.setLeaveTypeCode(leaveTypeCode);
            ledger.setYear(year);
            ledger.setDeltaDays(delta);
            ledger.setReason("Quota change");
            ledger.setActorEmployeeId(AuthContext.getEmployeeId());
            ledger.setCreatedAt(now);
            ledgerRepository.save(ledger);
        }
        return balances.size();
    }

    @Transactional
    public void deductOnApprove(Long employeeId, String leaveTypeCode, int year, BigDecimal days) {
        if (days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        LeaveBalance balance = getOrCreate(employeeId, leaveTypeCode, year);
        BigDecimal deduct = days.min(balance.getBalanceDays());
        if (deduct.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Instant now = Instant.now();
        balance.setBalanceDays(balance.getBalanceDays().subtract(deduct));
        balance.setUpdatedAt(now);
        balanceRepository.save(balance);

        LeaveBalanceLedger ledger = new LeaveBalanceLedger();
        ledger.setEmployeeId(employeeId);
        ledger.setLeaveTypeCode(leaveTypeCode);
        ledger.setYear(year);
        ledger.setDeltaDays(deduct.negate());
        ledger.setReason("Leave approved deduction");
        ledger.setCreatedAt(now);
        ledgerRepository.save(ledger);
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
            row.put("employeeName", employeeRepository.findById(bal.getEmployeeId()).map(Employee::getFullName).orElse(null));
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
            LeaveBalance next = getOrCreate(employeeId, leaveTypeCode, toYear);
            next.setBalanceDays(next.getBalanceDays().add(carry));
            next.setUpdatedAt(now);
            balanceRepository.save(next);

            LeaveBalanceLedger ledger = new LeaveBalanceLedger();
            ledger.setEmployeeId(employeeId);
            ledger.setLeaveTypeCode(leaveTypeCode);
            ledger.setYear(toYear);
            ledger.setDeltaDays(carry);
            ledger.setReason("Year-end carry from " + fromYear);
            ledger.setActorEmployeeId(AuthContext.getEmployeeId());
            ledger.setCreatedAt(now);
            ledgerRepository.save(ledger);
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
    @Transactional
    public void adjustBySystem(Long employeeId, String leaveTypeCode,
                               BigDecimal delta, String reason, int year) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        LeaveBalance balance = getOrCreate(employeeId, leaveTypeCode, year);
        BigDecimal next = balance.getBalanceDays().add(delta);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            next = BigDecimal.ZERO; // clamp to zero on deduction
        }
        Instant now = Instant.now();
        balance.setBalanceDays(next);
        balance.setUpdatedAt(now);
        balanceRepository.save(balance);

        LeaveBalanceLedger ledger = new LeaveBalanceLedger();
        ledger.setEmployeeId(employeeId);
        ledger.setLeaveTypeCode(leaveTypeCode);
        ledger.setYear(year);
        ledger.setDeltaDays(delta);
        ledger.setReason(reason);
        ledger.setCreatedAt(now);
        ledgerRepository.save(ledger);
    }

    private LeaveBalance getOrCreate(Long employeeId, String leaveTypeCode, int year) {
        return balanceRepository.findByEmployeeIdAndLeaveTypeCodeAndYear(employeeId, leaveTypeCode, year)
                .orElseGet(() -> {
                    LeaveBalance b = new LeaveBalance();
                    b.setEmployeeId(employeeId);
                    b.setLeaveTypeCode(leaveTypeCode);
                    b.setYear(year);
                    b.setBalanceDays(BigDecimal.ZERO);
                    b.setUpdatedAt(Instant.now());
                    return balanceRepository.save(b);
                });
    }
}
