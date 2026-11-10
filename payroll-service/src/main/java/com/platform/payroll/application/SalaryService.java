package com.platform.payroll.application;

import com.platform.payroll.domain.SalaryRecord;
import com.platform.payroll.infrastructure.persistence.SalaryRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Salary records (FR-SAL-01..03). Append-only; every change is audited with before/after. */
@Service
public class SalaryService {

    private static final Set<String> PAY_TYPES = Set.of("MONTHLY", "HOURLY");
    private static final Set<String> CURRENCIES = Set.of("BDT", "AUD");

    private final SalaryRecordRepository repository;
    private final AuditService auditService;
    private final PayrollClock clock;

    public SalaryService(SalaryRecordRepository repository, AuditService auditService, PayrollClock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> forEmployee(Long employeeId) {
        List<SalaryRecord> history = repository.findByEmployeeIdOrderByEffectiveDateDesc(employeeId);
        LocalDate today = clock.today();
        SalaryRecord current = history.stream()
                .filter(r -> !r.getEffectiveDate().isAfter(today)).findFirst().orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeId", employeeId);
        out.put("current", current == null ? null : view(current));
        out.put("upcoming", history.stream().filter(r -> r.getEffectiveDate().isAfter(today)).map(SalaryService::view).toList());
        out.put("history", history.stream().map(SalaryService::view).toList());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> current(Long employeeId) {
        Map<String, Object> all = forEmployee(employeeId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeId", employeeId);
        out.put("current", all.get("current"));
        out.put("upcoming", all.get("upcoming"));
        return out;
    }

    @Transactional
    public Map<String, Object> record(Long employeeId, Map<String, Object> body, Long actor) {
        String payType = upper(body.get("payType"));
        if (!PAY_TYPES.contains(payType)) {
            throw PayrollException.rule("INVALID_SALARY", "Pay type must be MONTHLY or HOURLY");
        }
        String currency = upper(body.get("currency"));
        if (!CURRENCIES.contains(currency)) {
            throw PayrollException.rule("INVALID_SALARY", "Currency must be BDT or AUD");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(body.get("amount"))).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            throw PayrollException.rule("INVALID_SALARY", "Amount must be a number");
        }
        if (amount.signum() <= 0) {
            throw PayrollException.rule("INVALID_SALARY", "Amount must be greater than 0");
        }
        LocalDate effective;
        try {
            effective = LocalDate.parse(String.valueOf(body.get("effectiveDate")));
        } catch (DateTimeParseException e) {
            throw PayrollException.rule("INVALID_SALARY", "Effective date must be YYYY-MM-DD");
        }
        String reason = body.get("reason") == null ? "" : String.valueOf(body.get("reason")).trim();
        if (reason.isEmpty()) {
            throw PayrollException.rule("INVALID_SALARY", "A reason is required for every salary change");
        }
        if (reason.length() > 512) {
            throw PayrollException.rule("INVALID_SALARY", "Reason is too long (max 512)");
        }
        if (repository.existsByEmployeeIdAndEffectiveDate(employeeId, effective)) {
            throw PayrollException.conflict("SALARY_DATE_TAKEN",
                    "A salary change is already recorded for " + effective + "; choose another effective date");
        }
        List<SalaryRecord> history = repository.findByEmployeeIdOrderByEffectiveDateDesc(employeeId);
        SalaryRecord before = history.stream()
                .filter(r -> !r.getEffectiveDate().isAfter(effective)).findFirst().orElse(null);

        SalaryRecord rec = new SalaryRecord();
        rec.setEmployeeId(employeeId);
        rec.setEffectiveDate(effective);
        rec.setPayType(payType);
        rec.setAmount(amount);
        rec.setCurrency(currency);
        rec.setReason(reason);
        rec.setSetBy(actor);
        SalaryRecord saved;
        try {
            saved = repository.saveAndFlush(rec);
        } catch (DataIntegrityViolationException e) {
            throw PayrollException.conflict("SALARY_DATE_TAKEN",
                    "A salary change is already recorded for " + effective);
        }
        auditService.record(actor, "SALARY_CHANGE", "salary_record", String.valueOf(saved.getId()),
                before == null ? null : view(before), view(saved));
        return view(saved);
    }

    public static Map<String, Object> view(SalaryRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("employeeId", r.getEmployeeId());
        m.put("effectiveDate", r.getEffectiveDate().toString());
        m.put("payType", r.getPayType());
        m.put("amount", r.getAmount());
        m.put("currency", r.getCurrency());
        m.put("reason", r.getReason());
        m.put("setBy", r.getSetBy());
        m.put("createdAt", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
        return m;
    }

    private static String upper(Object o) {
        return o == null ? "" : String.valueOf(o).trim().toUpperCase(Locale.ROOT);
    }
}
