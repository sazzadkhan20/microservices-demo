package com.platform.payroll.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.payroll.domain.PayrollAdjustment;
import com.platform.payroll.domain.PayrollLine;
import com.platform.payroll.domain.PayrollOperation;
import com.platform.payroll.domain.PayrollRun;
import com.platform.payroll.engine.PayrollEngine;
import com.platform.payroll.engine.PayrollInputs;
import com.platform.payroll.infrastructure.persistence.PayrollAdjustmentRepository;
import com.platform.payroll.infrastructure.persistence.PayrollLineRepository;
import com.platform.payroll.infrastructure.persistence.PayrollOperationRepository;
import com.platform.payroll.infrastructure.persistence.PayrollRunRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Monthly payroll run: live draft calculation, adjustments, views, readiness and CSV export
 * (FR-PAY-01..16, docs/PAYROLL_SPEC.md §1.6–§1.8). Facts are fetched outside the database transaction;
 * the run row is then locked for a short write (guide §5/§7).
 */
@Service
public class PayrollRunService {

    private final PayrollRunRepository runs;
    private final PayrollLineRepository lines;
    private final PayrollAdjustmentRepository adjustments;
    private final PayrollOperationRepository operations;
    private final PayrollFactsAssembler assembler;
    private final PayrollSettingsService settings;
    private final PayrollClock clock;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public PayrollRunService(PayrollRunRepository runs, PayrollLineRepository lines,
                             PayrollAdjustmentRepository adjustments, PayrollOperationRepository operations,
                             PayrollFactsAssembler assembler, PayrollSettingsService settings, PayrollClock clock,
                             AuditService audit, ObjectMapper mapper, PlatformTransactionManager txManager) {
        this.runs = runs;
        this.lines = lines;
        this.adjustments = adjustments;
        this.operations = operations;
        this.assembler = assembler;
        this.settings = settings;
        this.clock = clock;
        this.audit = audit;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ calculate

    /** FR-PAY-11 — (re)calculate the month's live draft for everyone employed in it. */
    public Map<String, Object> calculate(String period, Long actor) {
        YearMonth ym = PayrollClock.parse(period);
        if (ym.isAfter(clock.currentPeriod())) {
            throw PayrollException.rule("FUTURE_PERIOD", "Cannot calculate a future month (" + period + ")");
        }
        runs.findByPeriod(period).ifPresent(PayrollRunService::requireDraft);

        PayrollInputs.Rules rules = settings.rules();
        List<PayrollInputs.Line> inputs = assembler.assemble(ym, clock.today(), rules, null);
        ensureRun(period);

        tx.executeWithoutResult(status -> {
            PayrollRun run = runs.lockByPeriod(period).orElseThrow();
            requireDraft(run);
            // Adjustments saved while facts were loading win (they recalculate under this same lock).
            Map<Long, PayrollAdjustment> adj = new HashMap<>();
            adjustments.findByPeriod(period).forEach(a -> adj.put(a.getEmployeeId(), a));
            Map<Long, PayrollLine> existing = new HashMap<>();
            lines.findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(run.getId()).forEach(l -> existing.put(l.getEmployeeId(), l));

            long revision = run.getRevision() + 1;
            Instant now = Instant.now();
            List<Long> keep = new ArrayList<>();
            for (PayrollInputs.Line in : inputs) {
                PayrollAdjustment a = adj.get(in.employee().employeeId());
                PayrollInputs.Line effective = withAdjustment(in, a);
                PayrollLine line = existing.getOrDefault(in.employee().employeeId(), new PayrollLine());
                apply(line, run.getId(), effective, PayrollEngine.calculate(effective), revision, now);
                lines.save(line);
                keep.add(in.employee().employeeId());
            }
            if (keep.isEmpty()) {
                lines.deleteByRunId(run.getId());
            } else {
                lines.deleteByRunIdAndEmployeeIdNotIn(run.getId(), keep);
            }
            run.setRevision(revision);
            run.setCalculatedAt(now);
            run.setCalculatedBy(actor);
            run.setActionRequiredReason(null);
            run.setStatus(PayrollRun.DRAFT);
            runs.save(run);
            audit.record(actor, "PAYROLL_CALCULATE", "payroll_run", period, null,
                    Map.of("revision", revision, "lines", keep.size()));
        });
        return runView(period);
    }

    // ------------------------------------------------------------------ adjustments (FR-PAY-08)

    public Map<String, Object> setAdjustment(String period, Long employeeId, Object rawAmount, Object rawNote, Long actor) {
        PayrollClock.parse(period);
        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(rawAmount)).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            throw PayrollException.rule("INVALID_ADJUSTMENT", "Adjustment amount must be a number");
        }
        if (amount.signum() == 0) {
            throw PayrollException.rule("INVALID_ADJUSTMENT", "Adjustment cannot be 0 — remove it instead");
        }
        String note = rawNote == null ? "" : String.valueOf(rawNote).trim();
        if (note.isEmpty()) {
            throw PayrollException.rule("INVALID_ADJUSTMENT", "A note is required; it is shown on the payslip");
        }
        if (note.length() > 512) {
            throw PayrollException.rule("INVALID_ADJUSTMENT", "Note is too long (max 512)");
        }
        final BigDecimal amt = amount;
        tx.executeWithoutResult(status -> {
            PayrollRun run = runs.lockByPeriod(period).orElseThrow(() ->
                    PayrollException.rule("NO_RUN", "Calculate " + period + " before adding adjustments"));
            requireDraft(run);
            PayrollLine line = lines.findByRunIdAndEmployeeId(run.getId(), employeeId).orElseThrow(() ->
                    PayrollException.notFound("Employee " + employeeId + " has no row in " + period));
            PayrollAdjustment a = adjustments.findByPeriodAndEmployeeId(period, employeeId).orElse(null);
            Map<String, Object> before = a == null ? null : Map.of("amount", a.getAmount(), "note", a.getNote());
            if (a == null) {
                a = new PayrollAdjustment();
                a.setPeriod(period);
                a.setEmployeeId(employeeId);
                a.setCreatedBy(actor);
            }
            a.setAmount(amt);
            a.setNote(note);
            a.setUpdatedBy(actor);
            a.setUpdatedAt(Instant.now());
            try {
                adjustments.saveAndFlush(a);
            } catch (DataIntegrityViolationException e) {
                throw PayrollException.conflict("ADJUSTMENT_CONFLICT", "Adjustment was changed concurrently — retry");
            }
            recalcFromSnapshot(run, line, new PayrollInputs.Adjustment(amt, note));
            audit.record(actor, "PAYROLL_ADJUSTMENT", "payroll_adjustment", period + ":" + employeeId, before,
                    Map.of("amount", amt, "note", note));
        });
        return lineView(period, employeeId);
    }

    public Map<String, Object> deleteAdjustment(String period, Long employeeId, Long actor) {
        PayrollClock.parse(period);
        tx.executeWithoutResult(status -> {
            PayrollRun run = runs.lockByPeriod(period).orElseThrow(() -> PayrollException.notFound("No run for " + period));
            requireDraft(run);
            PayrollAdjustment a = adjustments.findByPeriodAndEmployeeId(period, employeeId).orElse(null);
            if (a == null) {
                return;
            }
            adjustments.delete(a);
            lines.findByRunIdAndEmployeeId(run.getId(), employeeId)
                    .ifPresent(line -> recalcFromSnapshot(run, line, null));
            audit.record(actor, "PAYROLL_ADJUSTMENT_REMOVE", "payroll_adjustment", period + ":" + employeeId,
                    Map.of("amount", a.getAmount(), "note", a.getNote()), null);
        });
        return lineView(period, employeeId);
    }

    /** Re-run the pure engine on the stored input snapshot — no remote call inside the transaction. */
    private void recalcFromSnapshot(PayrollRun run, PayrollLine line, PayrollInputs.Adjustment adjustment) {
        PayrollInputs.Line in = mapper.convertValue(line.getFacts(), PayrollInputs.Line.class);
        PayrollInputs.Line updated = new PayrollInputs.Line(in.period(), in.asOf(), in.rules(), in.employee(),
                in.salaries(), in.days(), in.leaves(), in.coverBalances(), adjustment);
        long revision = run.getRevision() + 1;
        apply(line, run.getId(), updated, PayrollEngine.calculate(updated), revision, line.getCalculatedAt());
        lines.save(line);
        run.setRevision(revision);
        runs.save(run);
    }

    // ------------------------------------------------------------------ views

    @Transactional(readOnly = true)
    public Map<String, Object> listRuns(int page, int size) {
        Page<PayrollRun> p = runs.findAllByOrderByPeriodDesc(PageRequest.of(Math.max(0, page), clampSize(size)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", p.getContent().stream().map(r -> {
            Map<String, Object> m = runSummary(r);
            m.put("totals", totals(lines.findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(r.getId())));
            return m;
        }).toList());
        out.put("page", p.getNumber());
        out.put("size", p.getSize());
        out.put("totalElements", p.getTotalElements());
        out.put("totalPages", p.getTotalPages());
        return out;
    }

    /** Run header, per-currency totals, warnings and finalise/reopen readiness. */
    @Transactional(readOnly = true)
    public Map<String, Object> runView(String period) {
        YearMonth ym = PayrollClock.parse(period);
        PayrollRun run = runs.findByPeriod(period).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period);
        out.put("monthEnded", clock.monthEnded(ym));
        out.put("today", clock.today().toString());
        if (run == null) {
            out.put("exists", false);
            out.put("status", null);
            out.put("warnings", previousMonthWarnings(ym, null));
            out.put("canCalculate", !ym.isAfter(clock.currentPeriod()));
            return out;
        }
        List<PayrollLine> all = lines.findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(run.getId());
        out.put("exists", true);
        out.putAll(runSummary(run));
        out.put("lineCount", all.size());
        out.put("exceptionCount", all.stream().filter(PayrollLine::isException).count());
        out.put("totals", totals(all));
        out.put("leaveToTake", leaveToTake(all));
        List<String> warnings = previousMonthWarnings(ym, run);
        if (run.getActionRequiredReason() != null) {
            warnings.add(0, run.getActionRequiredReason());
        }
        if (!clock.monthEnded(ym)) {
            warnings.add("Month not over — this is a live draft; absences are counted up to today");
        }
        out.put("warnings", warnings);
        List<String> finalizeBlockers = finalizeBlockers(run, all);
        out.put("canFinalize", finalizeBlockers.isEmpty());
        out.put("finalizeBlockers", finalizeBlockers);
        List<String> reopenBlockers = reopenBlockers(run);
        out.put("canReopen", reopenBlockers.isEmpty());
        out.put("reopenBlockers", reopenBlockers);
        out.put("canCalculate", run.is(PayrollRun.DRAFT));
        List<PayrollOperation> ops = operations.findByRunIdOrderByCreatedAtDesc(run.getId());
        out.put("operations", ops.stream().limit(10).map(PayrollFinalizeService::operationView).toList());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> lines(String period, String q, boolean exceptionsOnly, int page, int size) {
        PayrollClock.parse(period);
        PayrollRun run = runs.findByPeriod(period).orElseThrow(() -> PayrollException.notFound("No run for " + period));
        String like = q == null || q.isBlank() ? "%" : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Page<PayrollLine> p = lines.search(run.getId(), like, exceptionsOnly,
                PageRequest.of(Math.max(0, page), clampSize(size),
                        Sort.by("exception").descending().and(Sort.by("employeeName")).and(Sort.by("employeeId"))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", p.getContent().stream().map(l -> lineMap(l, false)).toList());
        out.put("page", p.getNumber());
        out.put("size", p.getSize());
        out.put("totalElements", p.getTotalElements());
        out.put("totalPages", p.getTotalPages());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> lineView(String period, Long employeeId) {
        PayrollRun run = runs.findByPeriod(period).orElseThrow(() -> PayrollException.notFound("No run for " + period));
        PayrollLine l = lines.findByRunIdAndEmployeeId(run.getId(), employeeId)
                .orElseThrow(() -> PayrollException.notFound("Employee " + employeeId + " has no row in " + period));
        Map<String, Object> m = lineMap(l, true);
        m.put("period", period);
        m.put("runStatus", run.getStatus());
        m.put("finalizedAt", run.getFinalizedAt() == null ? null : run.getFinalizedAt().toString());
        return m;
    }

    /** FR-SAL-03 — live month-to-date estimate for one employee; never persisted. */
    public Map<String, Object> estimate(Long employeeId) {
        YearMonth ym = clock.currentPeriod();
        List<PayrollInputs.Line> inputs = assembler.assemble(ym, clock.today(), settings.rules(), List.of(employeeId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", ym.toString());
        out.put("asOf", clock.today().toString());
        if (inputs.isEmpty()) {
            out.put("estimate", null);
            return out;
        }
        PayrollInputs.Line in = inputs.get(0);
        PayrollLine scratch = new PayrollLine();
        apply(scratch, null, in, PayrollEngine.calculate(in), 0, Instant.now());
        Map<String, Object> m = lineMap(scratch, false);
        m.remove("id");
        m.put("estimate", true);
        out.put("estimate", m);
        return out;
    }

    // ------------------------------------------------------------------ export (FR-PAY-16)

    @Transactional
    public String exportCsv(String period, Long actor) {
        PayrollClock.parse(period);
        PayrollRun run = runs.findByPeriod(period).orElseThrow(() -> PayrollException.notFound("No run for " + period));
        List<PayrollLine> all = lines.findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(run.getId());
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("period,employee_code,employee_name,currency,pay_type,working_days,days_employed,per_day_rate,"
                + "hours_worked,hourly_rate,absent_days,covered_days,unpaid_leave_days,deducted_days,base_pay,"
                + "deduction,adjustment,adjustment_note,net_pay,status,notes\r\n");
        for (PayrollLine l : all) {
            sb.append(String.join(",",
                    period, csv(l.getEmployeeCode()), csv(l.getEmployeeName()), csv(l.getCurrency()), csv(l.getPayType()),
                    num(l.getWorkingDays()), num(l.getDaysEmployed()), num(l.getPerDayRate()), num(l.getHoursWorked()),
                    num(l.getHourlyRate()), num(l.getAbsentDays()), num(l.getCoveredDays()), num(l.getUnpaidLeaveDays()),
                    num(l.getDeductedDays()), num(l.getBasePay()), num(l.getDeduction()), num(l.getAdjustment()),
                    csv(l.getAdjustmentNote()), num(l.getNetPay()), run.getStatus(),
                    csv(String.join(" | ", l.getNotes())))).append("\r\n");
        }
        for (Map<String, Object> t : totals(all)) {
            sb.append("TOTAL,").append(csv(String.valueOf(t.get("currency")))).append(",,,,,,,,,,,,,")
                    .append(num((BigDecimal) t.get("basePay"))).append(',')
                    .append(num((BigDecimal) t.get("deduction"))).append(',')
                    .append(num((BigDecimal) t.get("adjustment"))).append(",,")
                    .append(num((BigDecimal) t.get("netPay"))).append(',').append(run.getStatus()).append(",\r\n");
        }
        audit.record(actor, "PAYROLL_EXPORT", "payroll_run", period, null,
                Map.of("rows", all.size(), "status", run.getStatus(), "revision", run.getRevision()));
        return sb.toString();
    }

    // ------------------------------------------------------------------ rules for finalise / reopen

    /** BR-18 / FR-PAY-12 preconditions; empty = can finalise. */
    List<String> finalizeBlockers(PayrollRun run, List<PayrollLine> all) {
        List<String> out = new ArrayList<>();
        YearMonth ym = YearMonth.parse(run.getPeriod());
        if (!run.is(PayrollRun.DRAFT)) {
            out.add("Run is " + run.getStatus());
        }
        if (!clock.monthEnded(ym)) {
            out.add("The month has not ended yet (finalise from " + ym.atEndOfMonth().plusDays(1) + ")");
        }
        if (all.isEmpty()) {
            out.add("The run has no rows — calculate it first");
        }
        long exceptions = all.stream().filter(PayrollLine::isException).count();
        if (exceptions > 0) {
            out.add(exceptions + " row(s) need attention (missing salary or negative net pay)");
        }
        String gap = firstUnfinalizedBefore(ym);
        if (gap != null) {
            out.add("Finalise " + gap + " first — months finalise in order");
        }
        PayrollRun prev = runs.findByPeriod(ym.minusMonths(1).toString()).orElse(null);
        if (prev != null && prev.getFinalizedAt() != null && run.getCalculatedAt() != null
                && run.getCalculatedAt().isBefore(prev.getFinalizedAt())) {
            out.add("Recalculate — " + prev.getPeriod() + " was finalised after this draft was calculated, so leave balances changed");
        }
        return out;
    }

    /** FR-PAY-14 + reverse order: a month can be reopened only while the next month is not finalised. */
    List<String> reopenBlockers(PayrollRun run) {
        List<String> out = new ArrayList<>();
        if (!run.is(PayrollRun.FINALIZED)) {
            out.add("Only a finalised month can be reopened");
            return out;
        }
        String next = YearMonth.parse(run.getPeriod()).plusMonths(1).toString();
        runs.findByPeriod(next).ifPresent(n -> {
            if (!n.is(PayrollRun.DRAFT)) {
                out.add("Reopen " + next + " first — its leave balances depend on this month");
            }
        });
        return out;
    }

    /** The earliest month between the first payroll run and {@code ym - 1} that is not FINALIZED. */
    private String firstUnfinalizedBefore(YearMonth ym) {
        List<PayrollRun> earlier = runs.findByPeriodLessThanOrderByPeriodAsc(ym.toString());
        if (earlier.isEmpty()) {
            return null;
        }
        Map<String, PayrollRun> byPeriod = new TreeMap<>();
        earlier.forEach(r -> byPeriod.put(r.getPeriod(), r));
        for (YearMonth m = YearMonth.parse(earlier.get(0).getPeriod()); m.isBefore(ym); m = m.plusMonths(1)) {
            PayrollRun r = byPeriod.get(m.toString());
            if (r == null || !r.is(PayrollRun.FINALIZED)) {
                return m.toString();
            }
        }
        return null;
    }

    private List<String> previousMonthWarnings(YearMonth ym, PayrollRun run) {
        List<String> out = new ArrayList<>();
        String prevPeriod = ym.minusMonths(1).toString();
        PayrollRun prev = runs.findByPeriod(prevPeriod).orElse(null);
        boolean anyEarlier = !runs.findByPeriodLessThanOrderByPeriodAsc(ym.toString()).isEmpty();
        if ((prev != null && !prev.is(PayrollRun.FINALIZED)) || (prev == null && anyEarlier)) {
            out.add(prevPeriod + " is not finalised — its covered absences will change the leave balances used here (FR-PAY-13)");
        }
        if (run != null && prev != null && prev.getFinalizedAt() != null && run.getCalculatedAt() != null
                && run.is(PayrollRun.DRAFT) && run.getCalculatedAt().isBefore(prev.getFinalizedAt())) {
            out.add("Recalculate — " + prevPeriod + " was finalised after this draft was calculated");
        }
        return out;
    }

    static void requireDraft(PayrollRun run) {
        switch (run.getStatus()) {
            case PayrollRun.DRAFT -> { }
            case PayrollRun.FINALIZED -> throw PayrollException.conflict("RUN_FINALIZED",
                    run.getPeriod() + " is finalised — reopen it to make changes");
            case PayrollRun.ACTION_REQUIRED -> throw PayrollException.conflict("RUN_ACTION_REQUIRED",
                    run.getPeriod() + " needs attention — return it to draft first");
            default -> throw PayrollException.conflict("RUN_BUSY",
                    run.getPeriod() + " is " + run.getStatus().toLowerCase(Locale.ROOT) + " — wait for it to finish");
        }
    }

    // ------------------------------------------------------------------ mapping helpers

    private void ensureRun(String period) {
        if (runs.findByPeriod(period).isPresent()) {
            return;
        }
        try {
            tx.executeWithoutResult(s -> {
                PayrollRun r = new PayrollRun();
                r.setPeriod(period);
                runs.saveAndFlush(r);
            });
        } catch (DataIntegrityViolationException ignored) {
            // created concurrently — fine
        }
    }

    private static PayrollInputs.Line withAdjustment(PayrollInputs.Line in, PayrollAdjustment a) {
        PayrollInputs.Adjustment adj = a == null ? null : new PayrollInputs.Adjustment(a.getAmount(), a.getNote());
        if (Objects.equals(adj, in.adjustment())) {
            return in;
        }
        return new PayrollInputs.Line(in.period(), in.asOf(), in.rules(), in.employee(), in.salaries(), in.days(),
                in.leaves(), in.coverBalances(), adj);
    }

    @SuppressWarnings("unchecked")
    private void apply(PayrollLine l, Long runId, PayrollInputs.Line in, PayrollEngine.Result r, long revision, Instant at) {
        l.setRunId(runId);
        l.setEmployeeId(in.employee().employeeId());
        l.setEmployeeCode(in.employee().employeeCode());
        l.setEmployeeName(in.employee().fullName());
        l.setCurrency(r.currency());
        l.setPayType(r.payType());
        l.setWorkingDays(r.workingDays());
        l.setDaysEmployed(r.daysEmployed());
        l.setPerDayRate(r.perDayRate());
        l.setHourlyRate(r.hourlyRate());
        l.setHoursWorked(r.hoursWorked());
        l.setAbsentDays(r.absentDays());
        l.setCoveredDays(r.coveredDays());
        l.setUnpaidLeaveDays(r.unpaidLeaveDays());
        l.setDeductedDays(r.deductedDays());
        l.setBasePay(r.basePay());
        l.setDeduction(r.deduction());
        l.setAdjustment(r.adjustment());
        l.setAdjustmentNote(r.adjustmentNote());
        l.setNetPay(r.netPay());
        l.setException(r.exception());
        l.setNotes(new ArrayList<>(r.notes()));
        List<Map<String, Object>> covers = new ArrayList<>();
        for (PayrollEngine.Cover c : r.covers()) {
            covers.add(new LinkedHashMap<>(Map.of("family", c.family(), "category", c.category(), "days", c.days())));
        }
        l.setCovers(covers);
        l.setCalc(new LinkedHashMap<>(r.calc()));
        l.setFacts(mapper.convertValue(in, Map.class));
        l.setStatus(PayrollLine.DRAFT);
        l.setRevision(revision);
        l.setCalculatedAt(at == null ? Instant.now() : at);
    }

    static Map<String, Object> lineMap(PayrollLine l, boolean withCalc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("employeeId", l.getEmployeeId());
        m.put("employeeCode", l.getEmployeeCode());
        m.put("employeeName", l.getEmployeeName());
        m.put("currency", l.getCurrency());
        m.put("payType", l.getPayType());
        m.put("workingDays", l.getWorkingDays());
        m.put("daysEmployed", l.getDaysEmployed());
        m.put("perDayRate", l.getPerDayRate());
        m.put("hourlyRate", l.getHourlyRate());
        m.put("hoursWorked", l.getHoursWorked());
        m.put("absentDays", l.getAbsentDays());
        m.put("coveredDays", l.getCoveredDays());
        m.put("covers", l.getCovers());
        m.put("unpaidLeaveDays", l.getUnpaidLeaveDays());
        m.put("deductedDays", l.getDeductedDays());
        m.put("basePay", l.getBasePay());
        m.put("deduction", l.getDeduction());
        m.put("adjustment", l.getAdjustment());
        m.put("adjustmentNote", l.getAdjustmentNote());
        m.put("netPay", l.getNetPay());
        m.put("exception", l.isException());
        m.put("notes", l.getNotes());
        m.put("status", l.getStatus());
        m.put("revision", l.getRevision());
        m.put("calculatedAt", l.getCalculatedAt() == null ? null : l.getCalculatedAt().toString());
        if (withCalc) {
            m.put("calc", l.getCalc());
        }
        return m;
    }

    static Map<String, Object> runSummary(PayrollRun r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("period", r.getPeriod());
        m.put("status", r.getStatus());
        m.put("revision", r.getRevision());
        m.put("calculatedAt", str(r.getCalculatedAt()));
        m.put("calculatedBy", r.getCalculatedBy());
        m.put("finalizedAt", str(r.getFinalizedAt()));
        m.put("finalizedBy", r.getFinalizedBy());
        m.put("reopenedAt", str(r.getReopenedAt()));
        m.put("reopenedBy", r.getReopenedBy());
        m.put("reopenReason", r.getReopenReason());
        m.put("operationId", r.getOperationId());
        m.put("actionRequiredReason", r.getActionRequiredReason());
        return m;
    }

    /** FR-PAY-09 — totals per currency (no conversion, DR-02). */
    static List<Map<String, Object>> totals(List<PayrollLine> all) {
        Map<String, Map<String, Object>> byCur = new TreeMap<>();
        for (PayrollLine l : all) {
            String cur = l.getCurrency() == null ? "—" : l.getCurrency();
            Map<String, Object> t = byCur.computeIfAbsent(cur, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("currency", k);
                m.put("employees", 0);
                m.put("basePay", BigDecimal.ZERO.setScale(2));
                m.put("deduction", BigDecimal.ZERO.setScale(2));
                m.put("adjustment", BigDecimal.ZERO.setScale(2));
                m.put("netPay", BigDecimal.ZERO.setScale(2));
                return m;
            });
            t.put("employees", (Integer) t.get("employees") + 1);
            t.put("basePay", ((BigDecimal) t.get("basePay")).add(l.getBasePay()));
            t.put("deduction", ((BigDecimal) t.get("deduction")).add(l.getDeduction()));
            t.put("adjustment", ((BigDecimal) t.get("adjustment")).add(l.getAdjustment()));
            t.put("netPay", ((BigDecimal) t.get("netPay")).add(l.getNetPay()));
        }
        return new ArrayList<>(byCur.values());
    }

    /** Leave that finalising will take from balances, per category. */
    static List<Map<String, Object>> leaveToTake(List<PayrollLine> all) {
        Map<String, BigDecimal> byCat = new TreeMap<>();
        Map<String, Integer> people = new TreeMap<>();
        for (PayrollLine l : all) {
            for (Map<String, Object> c : l.getCovers()) {
                String cat = String.valueOf(c.get("category"));
                byCat.merge(cat, new BigDecimal(String.valueOf(c.get("days"))), BigDecimal::add);
                people.merge(cat, 1, Integer::sum);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        byCat.forEach((k, v) -> out.add(Map.of("category", k, "days", v, "employees", people.get(k))));
        return out;
    }

    private static int clampSize(int size) {
        return size <= 0 ? 50 : Math.min(size, 200);
    }

    private static String str(Instant i) {
        return i == null ? null : i.toString();
    }

    private static String num(BigDecimal v) {
        return v == null ? "" : v.toPlainString();
    }

    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        String v = s;
        // CSV injection guard for spreadsheet consumers.
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
