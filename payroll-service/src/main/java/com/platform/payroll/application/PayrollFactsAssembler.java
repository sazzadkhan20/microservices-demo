package com.platform.payroll.application;

import com.platform.payroll.domain.PayrollAdjustment;
import com.platform.payroll.domain.SalaryRecord;
import com.platform.payroll.engine.PayrollInputs;
import com.platform.payroll.infrastructure.FactsClient;
import com.platform.payroll.infrastructure.persistence.PayrollAdjustmentRepository;
import com.platform.payroll.infrastructure.persistence.SalaryRecordRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gathers the inputs for a month from each owner (People, Time &amp; Work, Leave) plus Payroll's own
 * salary records and adjustments. Runs OUTSIDE any database transaction (guide §7).
 */
@Component
public class PayrollFactsAssembler {

    private final FactsClient facts;
    private final SalaryRecordRepository salaries;
    private final PayrollAdjustmentRepository adjustments;

    public PayrollFactsAssembler(FactsClient facts, SalaryRecordRepository salaries,
                                 PayrollAdjustmentRepository adjustments) {
        this.facts = facts;
        this.salaries = salaries;
        this.adjustments = adjustments;
    }

    public List<PayrollInputs.Line> assemble(YearMonth period, LocalDate asOf, PayrollInputs.Rules rules,
                                             Collection<Long> onlyEmployees) {
        String p = period.toString();
        List<Map<String, Object>> employees = facts.employees(p, onlyEmployees);
        if (employees.isEmpty()) {
            return List.of();
        }
        List<Long> ids = employees.stream().map(e -> asLong(e.get("id"))).toList();
        Map<String, Map<String, Object>> attendance = facts.attendance(p, ids);
        Map<String, Map<String, Object>> leave = facts.leave(p, rules.coverOrder(), ids);

        Map<Long, List<PayrollInputs.Salary>> salaryByEmp = new HashMap<>();
        for (SalaryRecord r : salaries.findByEmployeeIdInAndEffectiveDateLessThanEqualOrderByEffectiveDateAsc(
                ids, period.atEndOfMonth())) {
            salaryByEmp.computeIfAbsent(r.getEmployeeId(), k -> new ArrayList<>())
                    .add(new PayrollInputs.Salary(r.getEffectiveDate(), r.getPayType(), r.getAmount(), r.getCurrency()));
        }
        Map<Long, PayrollAdjustment> adjByEmp = new HashMap<>();
        for (PayrollAdjustment a : adjustments.findByPeriod(p)) {
            adjByEmp.put(a.getEmployeeId(), a);
        }

        List<PayrollInputs.Line> out = new ArrayList<>(employees.size());
        for (Map<String, Object> e : employees) {
            long id = asLong(e.get("id"));
            Map<String, Object> att = attendance.get(String.valueOf(id));
            if (att == null) {
                throw new FactsClient.FactsUnavailableException(
                        "Time & Work has no schedule/attendance for employee " + id + " — run is not calculated");
            }
            Map<String, Object> lv = leave.getOrDefault(String.valueOf(id), Map.of());
            PayrollInputs.Employee emp = new PayrollInputs.Employee(
                    id,
                    str(e.get("employeeCode")),
                    str(e.get("fullName")),
                    str(e.get("payCurrency")),
                    date(e.get("startDate")),
                    date(e.get("exitDate")),
                    str(att.get("scheduleMode")));
            PayrollAdjustment adj = adjByEmp.get(id);
            out.add(new PayrollInputs.Line(
                    period,
                    asOf,
                    rules,
                    emp,
                    salaryByEmp.getOrDefault(id, List.of()),
                    days(att.get("days")),
                    leaves(lv.get("leaves")),
                    covers(lv.get("coverBalances")),
                    adj == null ? null : new PayrollInputs.Adjustment(adj.getAmount(), adj.getNote())));
        }
        return out;
    }

    private static List<PayrollInputs.Day> days(Object raw) {
        List<PayrollInputs.Day> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add(new PayrollInputs.Day(
                            date(m.get("date")),
                            Boolean.TRUE.equals(m.get("working")),
                            Boolean.TRUE.equals(m.get("holiday")),
                            str(m.get("status")),
                            dec(m.get("absence")),
                            m.get("workedMinutes") instanceof Number n ? n.intValue() : 0,
                            Boolean.TRUE.equals(m.get("missingCheckout"))));
                }
            }
        }
        return out;
    }

    private static List<PayrollInputs.Leave> leaves(Object raw) {
        List<PayrollInputs.Leave> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add(new PayrollInputs.Leave(date(m.get("start")), date(m.get("end")),
                            str(m.get("halfDayPart")), !Boolean.FALSE.equals(m.get("paid")), str(m.get("category"))));
                }
            }
        }
        return out;
    }

    private static List<PayrollInputs.CoverBalance> covers(Object raw) {
        List<PayrollInputs.CoverBalance> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add(new PayrollInputs.CoverBalance(str(m.get("family")), str(m.get("category")),
                            dec(m.get("available"))));
                }
            }
        }
        return out;
    }

    static long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(o));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static LocalDate date(Object o) {
        return o == null || String.valueOf(o).isBlank() ? null : LocalDate.parse(String.valueOf(o).substring(0, 10));
    }

    private static BigDecimal dec(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        return o instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(o));
    }
}
