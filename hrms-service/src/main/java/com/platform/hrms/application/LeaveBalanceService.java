package com.platform.hrms.application;

import com.platform.common.security.AuthContext;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public LeaveBalanceService(
            LeaveBalanceRepository balanceRepository,
            LeaveBalanceLedgerRepository ledgerRepository,
            LeaveTypeRepository leaveTypeRepository,
            EmployeeRepository employeeRepository,
            DataScopeService dataScopeService) {
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.employeeRepository = employeeRepository;
        this.dataScopeService = dataScopeService;
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
     * Preview year-end carry: for each employee with a balance in fromYear for leaveTypeCode,
     * carry = min(balance, carryMaxDays) into toYear.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> yearEndPreview(String leaveTypeCode, int fromYear, int toYear, BigDecimal carryMaxDays) {
        BigDecimal cap = carryMaxDays == null ? new BigDecimal("5") : carryMaxDays;
        leaveTypeRepository.findByCodeAndActiveTrue(leaveTypeCode)
                .orElseThrow(() -> new IllegalArgumentException("Leave type not found: " + leaveTypeCode));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LeaveBalance bal : balanceRepository.findAll()) {
            if (!leaveTypeCode.equalsIgnoreCase(bal.getLeaveTypeCode()) || bal.getYear() != fromYear) {
                continue;
            }
            BigDecimal carry = bal.getBalanceDays().min(cap).max(BigDecimal.ZERO);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", bal.getEmployeeId());
            row.put("employeeName", employeeRepository.findById(bal.getEmployeeId()).map(Employee::getFullName).orElse(null));
            row.put("fromYear", fromYear);
            row.put("toYear", toYear);
            row.put("leaveTypeCode", leaveTypeCode);
            row.put("currentBalance", bal.getBalanceDays());
            row.put("carryDays", carry);
            row.put("carryMaxDays", cap);
            rows.add(row);
        }
        return rows;
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
