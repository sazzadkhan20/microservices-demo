package com.platform.payroll.application;

import com.platform.payroll.domain.PayrollSettings;
import com.platform.payroll.engine.PayrollInputs;
import com.platform.payroll.infrastructure.persistence.PayrollSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Admin payroll rules (docs/PAYROLL_SPEC.md §1.9, NFR-17). Defaults are the SRS defaults. */
@Service
public class PayrollSettingsService {

    private static final Set<String> BASES = Set.of(PayrollInputs.Rules.WORKING_DAYS, PayrollInputs.Rules.CALENDAR_30);
    private static final Set<String> RULES = Set.of(PayrollInputs.Rules.BALANCE_FIRST, PayrollInputs.Rules.DIRECT);

    /** Rules fixed by the SRS — shown read-only to admins. */
    public static final List<Map<String, String>> FIXED_RULES = List.of(
            Map.of("id", "BR-17", "rule", "Approved unpaid leave is always deducted"),
            Map.of("id", "BR-18", "rule", "A month can be finalised only after it ends, and months finalise in order"),
            Map.of("id", "BR-19", "rule", "Hourly shifts without a check-out are not paid until corrected"),
            Map.of("id", "BR-24", "rule", "No money penalty for lateness"),
            Map.of("id", "§16.2", "rule", "No tax, provident fund, statutory deductions or overtime pay in v1"));

    private final PayrollSettingsRepository repository;
    private final AuditService auditService;

    public PayrollSettingsService(PayrollSettingsRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public PayrollSettings load() {
        return repository.findById(PayrollSettings.SINGLETON_ID)
                .orElseGet(() -> repository.save(new PayrollSettings()));
    }

    @Transactional(readOnly = true)
    public PayrollInputs.Rules rules() {
        PayrollSettings s = repository.findById(PayrollSettings.SINGLETON_ID).orElseGet(PayrollSettings::new);
        return new PayrollInputs.Rules(s.getPerDayRateBasis(), s.getAbsenceRule(), List.copyOf(s.getAbsenceCoverOrder()));
    }

    @Transactional
    public PayrollSettings update(Map<String, Object> body, Long actor) {
        PayrollSettings s = load();
        Map<String, Object> before = view(s);
        if (body.containsKey("perDayRateBasis")) {
            String v = upper(body.get("perDayRateBasis"));
            if (!BASES.contains(v)) {
                throw PayrollException.rule("INVALID_SETTING", "perDayRateBasis must be WORKING_DAYS or CALENDAR_30");
            }
            s.setPerDayRateBasis(v);
        }
        if (body.containsKey("absenceRule")) {
            String v = upper(body.get("absenceRule"));
            if (!RULES.contains(v)) {
                throw PayrollException.rule("INVALID_SETTING", "absenceRule must be BALANCE_FIRST or DIRECT");
            }
            s.setAbsenceRule(v);
        }
        if (body.containsKey("absenceCoverOrder")) {
            if (!(body.get("absenceCoverOrder") instanceof List<?> raw)) {
                throw PayrollException.rule("INVALID_SETTING", "absenceCoverOrder must be a list of leave category codes");
            }
            Set<String> codes = new LinkedHashSet<>();
            for (Object o : raw) {
                String code = upper(o);
                if (!code.matches("[A-Z0-9_]{1,64}")) {
                    throw PayrollException.rule("INVALID_SETTING", "Invalid leave category code: " + o);
                }
                codes.add(code);
            }
            if (codes.size() > 6) {
                throw PayrollException.rule("INVALID_SETTING", "At most 6 cover categories");
            }
            s.setAbsenceCoverOrder(new ArrayList<>(codes));
        }
        if (body.containsKey("notifyPayslipReady")) {
            s.setNotifyPayslipReady(Boolean.parseBoolean(String.valueOf(body.get("notifyPayslipReady"))));
        }
        s.setUpdatedAt(java.time.Instant.now());
        s.setUpdatedBy(actor);
        PayrollSettings saved = repository.save(s);
        auditService.record(actor, "PAYROLL_SETTINGS_UPDATE", "payroll_settings", "1", before, view(saved));
        return saved;
    }

    public static Map<String, Object> view(PayrollSettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("perDayRateBasis", s.getPerDayRateBasis());
        m.put("absenceRule", s.getAbsenceRule());
        m.put("absenceCoverOrder", s.getAbsenceCoverOrder());
        m.put("notifyPayslipReady", s.isNotifyPayslipReady());
        m.put("updatedAt", s.getUpdatedAt() == null ? null : s.getUpdatedAt().toString());
        m.put("updatedBy", s.getUpdatedBy());
        return m;
    }

    private static String upper(Object o) {
        return o == null ? "" : String.valueOf(o).trim().toUpperCase(Locale.ROOT);
    }
}
