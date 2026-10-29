package com.platform.hrms.application;

import com.platform.hrms.api.dto.LeaveBalanceAdjustRequest;
import com.platform.hrms.api.dto.LeaveBalanceResponse;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.LeaveBalance;
import com.platform.hrms.domain.LeaveBalanceLedger;
import com.platform.hrms.domain.LeaveType;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.LeaveBalanceLedgerRepository;
import com.platform.hrms.infrastructure.persistence.LeaveBalanceRepository;
import com.platform.hrms.infrastructure.persistence.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * HR-managed leave balances (add/deduct). Shared across tenants — rules are data.
 */
@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository balanceRepository;
    private final LeaveBalanceLedgerRepository ledgerRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeRepository employeeRepository;

    public LeaveBalanceService(
            LeaveBalanceRepository balanceRepository,
            LeaveBalanceLedgerRepository ledgerRepository,
            LeaveTypeRepository leaveTypeRepository,
            EmployeeRepository employeeRepository) {
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> listForEmployee(Long employeeId, Integer year) {
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
