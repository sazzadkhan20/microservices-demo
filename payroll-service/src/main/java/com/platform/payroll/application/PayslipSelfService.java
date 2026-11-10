package com.platform.payroll.application;

import com.platform.payroll.domain.PayslipRevision;
import com.platform.payroll.infrastructure.persistence.PayslipRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** FR-SAL-03 — an employee sees only their own FINALIZED payslips (current revisions). */
@Service
public class PayslipSelfService {

    private final PayslipRevisionRepository revisions;

    public PayslipSelfService(PayslipRevisionRepository revisions) {
        this.revisions = revisions;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long employeeId) {
        return revisions.findByEmployeeIdAndSupersededAtIsNullOrderByPeriodDesc(employeeId).stream()
                .map(PayslipSelfService::summary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long employeeId, String period) {
        PayrollClock.parse(period);
        PayslipRevision rev = revisions.findFirstByEmployeeIdAndPeriodAndSupersededAtIsNull(employeeId, period)
                .orElseThrow(() -> PayrollException.notFound("No finalised payslip for " + period));
        Map<String, Object> m = new LinkedHashMap<>(rev.getSnapshot());
        m.remove("calc");
        m.put("status", "FINALIZED");
        m.put("finalizedAt", rev.getFinalizedAt().toString());
        return m;
    }

    private static Map<String, Object> summary(PayslipRevision r) {
        Map<String, Object> s = r.getSnapshot();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("period", r.getPeriod());
        m.put("currency", r.getCurrency());
        m.put("payType", s.get("payType"));
        m.put("basePay", s.get("basePay"));
        m.put("deduction", s.get("deduction"));
        m.put("adjustment", s.get("adjustment"));
        m.put("netPay", r.getNetPay());
        m.put("status", "FINALIZED");
        m.put("finalizedAt", r.getFinalizedAt().toString());
        return m;
    }
}
