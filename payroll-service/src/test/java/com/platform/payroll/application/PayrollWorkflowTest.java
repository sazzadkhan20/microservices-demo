package com.platform.payroll.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.messaging.outbox.OutboxRelay;
import com.platform.messaging.outbox.OutboxWriter;
import com.platform.payroll.domain.PayrollLine;
import com.platform.payroll.domain.PayrollOperation;
import com.platform.payroll.domain.PayrollRun;
import com.platform.payroll.domain.PayrollSettings;
import com.platform.payroll.domain.PayslipRevision;
import com.platform.payroll.engine.PayrollEngine;
import com.platform.payroll.engine.PayrollInputs;
import com.platform.payroll.infrastructure.FactsClient;
import com.platform.payroll.infrastructure.persistence.PayrollAdjustmentRepository;
import com.platform.payroll.infrastructure.persistence.PayrollLineRepository;
import com.platform.payroll.infrastructure.persistence.PayrollOperationRepository;
import com.platform.payroll.infrastructure.persistence.PayrollRunRepository;
import com.platform.payroll.infrastructure.persistence.PayslipRevisionRepository;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guide §6 / §13 PAY01–PAY03 and SRS BR-18 / FR-PAY-11/12/14 gates, without a database
 * (docs/PAYROLL_SPEC.md §2.3, §5).
 */
class PayrollWorkflowTest {

    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final PayrollLineRepository lines = mock(PayrollLineRepository.class);
    private final PayrollOperationRepository operations = mock(PayrollOperationRepository.class);
    private final PayslipRevisionRepository revisions = mock(PayslipRevisionRepository.class);
    private final PayrollAdjustmentRepository adjustments = mock(PayrollAdjustmentRepository.class);
    private final PayrollSettingsService settings = mock(PayrollSettingsService.class);
    private final PayrollFactsAssembler assembler = mock(PayrollFactsAssembler.class);
    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final AuditService audit = mock(AuditService.class);
    private final FactsClient facts = mock(FactsClient.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    // "Today" is 10 Nov 2026 in Dhaka.
    private final PayrollClock clock = new PayrollClock(ZoneId.of("Asia/Dhaka"),
            Clock.fixed(Instant.parse("2026-11-10T06:00:00Z"), ZoneId.of("UTC")));
    private final PayrollOperationPolicy policy = new PayrollOperationPolicy(Duration.ofMinutes(5), Duration.ofMinutes(15));
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private PayrollRunService runService;
    private PayrollFinalizeService finalizeService;

    @BeforeEach
    void setUp() {
        Metrics.addRegistry(meters);
        runService = new PayrollRunService(runs, lines, adjustments, operations, assembler, settings, clock, audit,
                mapper, policy, tx);
        @SuppressWarnings("unchecked")
        ObjectProvider<OutboxRelay> relay = mock(ObjectProvider.class);
        finalizeService = new PayrollFinalizeService(runs, lines, operations, revisions, runService, settings, outbox,
                relay, audit, facts, policy, tx);
        when(settings.load()).thenReturn(new PayrollSettings());
        when(settings.rules()).thenReturn(PayrollInputs.Rules.srsDefaults());
        when(runs.findByPeriodLessThanOrderByPeriodAsc(anyString())).thenReturn(List.of());
        when(runs.findByPeriod(anyString())).thenReturn(Optional.empty());
        when(operations.findRunIdByOperationId(anyString())).thenReturn(Optional.of(1L));
    }

    @AfterEach
    void tearDown() {
        Metrics.removeRegistry(meters);
    }

    private static PayrollRun run(long id, String period, String status, long revision) {
        PayrollRun r = new PayrollRun();
        ReflectionTestUtils.setField(r, "id", id);
        r.setPeriod(period);
        r.setStatus(status);
        r.setRevision(revision);
        r.setCalculatedAt(Instant.parse("2026-11-01T00:00:00Z"));
        return r;
    }

    private static PayrollLine line(boolean exception) {
        PayrollLine l = new PayrollLine();
        l.setEmployeeId(31L);
        l.setNetPay(new BigDecimal("13977.27"));
        l.setException(exception);
        return l;
    }

    private static PayrollOperation op(PayrollRun run, String action, String status) {
        PayrollOperation o = new PayrollOperation();
        o.setOperationId("op-1");
        o.setOperationKey(action + ":" + run.getPeriod() + ":r" + run.getRevision());
        o.setRunId(run.getId());
        o.setPeriod(run.getPeriod());
        o.setRevision(run.getRevision());
        o.setAction(action);
        o.setStatus(status);
        return o;
    }

    private PayrollOperation pendingOld(PayrollRun run, String action) {
        PayrollOperation o = op(run, action, PayrollOperation.PENDING);
        ReflectionTestUtils.setField(o, "createdAt", Instant.now().minus(Duration.ofMinutes(20)));
        o.setCommand(Map.of("lines", List.of(Map.of("employeeId", 31))));
        return o;
    }

    /** September for Arpa (Sun–Thu, 15000 BDT, Casual 1): {@code absentDays} absences from 8 Sep. */
    private static PayrollInputs.Line arpaSeptember(int absentDays) {
        YearMonth sep = YearMonth.of(2026, 9);
        List<PayrollInputs.Day> days = new ArrayList<>();
        int marked = 0;
        for (LocalDate d = sep.atDay(1); !d.isAfter(sep.atEndOfMonth()); d = d.plusDays(1)) {
            boolean working = d.getDayOfWeek() != DayOfWeek.FRIDAY && d.getDayOfWeek() != DayOfWeek.SATURDAY;
            boolean absent = working && d.getDayOfMonth() >= 8 && marked < absentDays;
            if (absent) {
                marked++;
            }
            days.add(new PayrollInputs.Day(d, working, false, working ? (absent ? "A" : "P") : "-",
                    absent ? BigDecimal.ONE : BigDecimal.ZERO, working && !absent ? 480 : 0, false));
        }
        return new PayrollInputs.Line(sep, LocalDate.of(2026, 11, 10), PayrollInputs.Rules.srsDefaults(),
                new PayrollInputs.Employee(31, "QII-0050", "Arpa Sen", "BDT", LocalDate.of(2025, 1, 1), null, "FIXED"),
                List.of(new PayrollInputs.Salary(LocalDate.of(2025, 1, 1), "MONTHLY", new BigDecimal("15000"), "BDT")),
                days, List.of(), List.of(new PayrollInputs.CoverBalance("CASUAL", "CASUAL", BigDecimal.ONE),
                new PayrollInputs.CoverBalance("ANNUAL", "ANNUAL", BigDecimal.ZERO)), null);
    }

    /** A stored draft row exactly as calculate() would write it from {@code in}. */
    private static PayrollLine rowFrom(PayrollInputs.Line in) {
        PayrollEngine.Result r = PayrollEngine.calculate(in);
        PayrollLine l = new PayrollLine();
        l.setEmployeeId(in.employee().employeeId());
        l.setEmployeeCode(in.employee().employeeCode());
        l.setEmployeeName(in.employee().fullName());
        l.setPayType(r.payType());
        l.setCurrency(r.currency());
        l.setBasePay(r.basePay());
        l.setAbsentDays(r.absentDays());
        l.setUnpaidLeaveDays(r.unpaidLeaveDays());
        l.setCoveredDays(r.coveredDays());
        l.setDeduction(r.deduction());
        l.setAdjustment(r.adjustment());
        l.setNetPay(r.netPay());
        l.setException(r.exception());
        List<Map<String, Object>> covers = new ArrayList<>();
        r.covers().forEach(c -> covers.add(Map.of("family", c.family(), "category", c.category(), "days", c.days())));
        l.setCovers(covers);
        return l;
    }

    // ---------------------------------------------------------------- BR-18 / FR-PAY-12 blockers

    @Test
    void cannotFinaliseBeforeMonthEnds() {
        List<String> blockers = runService.finalizeBlockers(run(1, "2026-11", PayrollRun.DRAFT, 3), List.of(line(false)));
        assertTrue(blockers.stream().anyMatch(b -> b.startsWith("The month has not ended")), blockers.toString());
    }

    @Test
    void liveDraftCalculatedBeforeMonthEndMustBeRecalculated() {
        PayrollRun oct = run(2, "2026-10", PayrollRun.DRAFT, 1);
        // 28 Oct 2026 in Dhaka: absences on 29–31 Oct were not counted yet.
        oct.setCalculatedAt(Instant.parse("2026-10-28T08:00:00Z"));
        List<String> blockers = runService.finalizeBlockers(oct, List.of(line(false)));
        assertTrue(blockers.stream().anyMatch(b -> b.contains("calculated before the month ended")), blockers.toString());
        // 1 Nov 00:30 in Dhaka is after the month ended (still 31 Oct in UTC).
        oct.setCalculatedAt(Instant.parse("2026-10-31T18:30:00Z"));
        assertTrue(runService.finalizeBlockers(oct, List.of(line(false))).isEmpty());
    }

    @Test
    void afterLeaveRejectedTheDraftMustBeRecalculated() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.DRAFT, 4);
        PayrollOperation rejected = op(run(1, "2026-09", PayrollRun.DRAFT, 3), PayrollOperation.FINALIZE, PayrollOperation.REJECTED);
        rejected.setError("Employee 31: CASUAL has 0 day(s) left");
        rejected.setCompletedAt(Instant.parse("2026-11-02T00:00:00Z"));
        when(operations.findByRunIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(rejected));
        List<String> blockers = runService.finalizeBlockers(sep, List.of(line(false)));
        assertTrue(blockers.stream().anyMatch(b -> b.startsWith("Recalculate — Leave rejected")), blockers.toString());
        sep.setCalculatedAt(Instant.parse("2026-11-03T00:00:00Z"));
        assertTrue(runService.finalizeBlockers(sep, List.of(line(false))).isEmpty());
    }

    @Test
    void monthsFinaliseInOrder() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.DRAFT, 2);
        when(runs.findByPeriodLessThanOrderByPeriodAsc("2026-10")).thenReturn(List.of(sep));
        List<String> blockers = runService.finalizeBlockers(run(2, "2026-10", PayrollRun.DRAFT, 1), List.of(line(false)));
        assertTrue(blockers.contains("Finalise 2026-09 first — months finalise in order"), blockers.toString());
    }

    @Test
    void gapMonthWithoutRunAlsoBlocks() {
        PayrollRun aug = run(1, "2026-08", PayrollRun.FINALIZED, 2);
        when(runs.findByPeriodLessThanOrderByPeriodAsc("2026-10")).thenReturn(List.of(aug));
        List<String> blockers = runService.finalizeBlockers(run(2, "2026-10", PayrollRun.DRAFT, 1), List.of(line(false)));
        assertTrue(blockers.contains("Finalise 2026-09 first — months finalise in order"), blockers.toString());
    }

    @Test
    void exceptionRowsBlockFinalise() {
        List<String> blockers = runService.finalizeBlockers(run(1, "2026-09", PayrollRun.DRAFT, 1), List.of(line(true)));
        assertTrue(blockers.stream().anyMatch(b -> b.contains("need attention")), blockers.toString());
    }

    @Test
    void draftCalculatedBeforePreviousMonthWasFinalisedIsStale() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZED, 2);
        sep.setFinalizedAt(Instant.parse("2026-11-05T00:00:00Z"));
        when(runs.findByPeriodLessThanOrderByPeriodAsc("2026-10")).thenReturn(List.of(sep));
        when(runs.findByPeriod("2026-09")).thenReturn(Optional.of(sep));
        List<String> blockers = runService.finalizeBlockers(run(2, "2026-10", PayrollRun.DRAFT, 1), List.of(line(false)));
        assertTrue(blockers.stream().anyMatch(b -> b.startsWith("Recalculate")), blockers.toString());
    }

    @Test
    void reopenOnlyWhileNextMonthIsNotFinal() {
        PayrollRun oct = run(2, "2026-10", PayrollRun.FINALIZED, 3);
        when(runs.findByPeriod("2026-10")).thenReturn(Optional.of(oct));
        List<String> blockers = runService.reopenBlockers(run(1, "2026-09", PayrollRun.FINALIZED, 2));
        assertEquals(List.of("Reopen 2026-10 first — its leave balances depend on this month"), blockers);
    }

    // ---------------------------------------------------------------- FR-PAY-11/12 freshness

    @Test
    void unchangedFactsHaveNoDrift() {
        PayrollInputs.Line in = arpaSeptember(2);
        assertEquals(List.of(), runService.factsDrift("2026-09", List.of(rowFrom(in)), List.of(in)));
    }

    @Test
    void attendanceChangedAfterCalculationIsDrift() {
        PayrollLine reviewed = rowFrom(arpaSeptember(2)); // 1 covered by Casual, 1 deducted
        List<String> drift = runService.factsDrift("2026-09", List.of(reviewed), List.of(arpaSeptember(3)));
        assertEquals(1, drift.size(), drift.toString());
        assertTrue(drift.get(0).startsWith("Arpa Sen (QII-0050): ") && drift.get(0).contains("absent days 2 → 3")
                && drift.get(0).contains("net pay"), drift.toString());
    }

    @Test
    void joinerOrLeaverChangeIsDrift() {
        PayrollInputs.Line in = arpaSeptember(0);
        PayrollLine other = rowFrom(in);
        other.setEmployeeId(99L);
        other.setEmployeeName("Gone Person");
        other.setEmployeeCode("QII-0099");
        List<String> drift = runService.factsDrift("2026-09", List.of(other), List.of(in));
        assertTrue(drift.contains("Arpa Sen (QII-0050) is employed in 2026-09 but has no row"), drift.toString());
        assertTrue(drift.contains("Gone Person (QII-0099) is no longer employed in 2026-09"), drift.toString());
    }

    @Test
    void finaliseIsRefusedWhenFactsChangedSinceReview() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.DRAFT, 5);
        when(runs.findByPeriod("2026-09")).thenReturn(Optional.of(sep));
        when(runs.lockByPeriod("2026-09")).thenReturn(Optional.of(sep));
        when(operations.findByOperationKey(anyString())).thenReturn(Optional.empty());
        when(lines.findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(1L)).thenReturn(List.of(rowFrom(arpaSeptember(2))));
        when(assembler.assemble(eq(YearMonth.of(2026, 9)), any(), any(), isNull())).thenReturn(List.of(arpaSeptember(3)));
        PayrollException ex = assertThrows(PayrollException.class, () -> finalizeService.finalizeRun("2026-09", true, 5L, 1L));
        assertEquals("FACTS_CHANGED", ex.code());
        assertEquals(PayrollRun.DRAFT, sep.getStatus());
        verify(outbox, never()).enqueue(any(), anyString());
        verify(operations, never()).save(any());
    }

    @Test
    void finaliseWithUnchangedFactsSendsOneKeyedCommand() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.DRAFT, 5);
        PayrollInputs.Line in = arpaSeptember(2);
        when(runs.findByPeriod("2026-09")).thenReturn(Optional.of(sep));
        when(runs.lockByPeriod("2026-09")).thenReturn(Optional.of(sep));
        when(operations.findByOperationKey(anyString())).thenReturn(Optional.empty());
        when(lines.findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(1L)).thenReturn(List.of(rowFrom(in)));
        when(assembler.assemble(eq(YearMonth.of(2026, 9)), any(), any(), isNull())).thenReturn(List.of(in));
        Map<String, Object> view = finalizeService.finalizeRun("2026-09", true, 5L, 1L);
        assertEquals("PENDING", view.get("status"));
        assertEquals("FINALIZE:2026-09:r5", view.get("operationKey"));
        assertEquals(PayrollRun.FINALIZING, sep.getStatus());
        verify(outbox, times(1)).enqueue(any(), eq("payroll.leave-cover.command"));
    }

    @Test
    void repeatedFinaliseReturnsTheSameOperationWithoutRefetchingFacts() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZING, 5);
        PayrollOperation pending = op(sep, PayrollOperation.FINALIZE, PayrollOperation.PENDING);
        when(runs.findByPeriod("2026-09")).thenReturn(Optional.of(sep));
        when(operations.findByOperationKey("FINALIZE:2026-09:r5")).thenReturn(Optional.of(pending));
        Map<String, Object> view = finalizeService.finalizeRun("2026-09", true, 5L, 1L);
        assertEquals("op-1", view.get("operationId"));
        verify(assembler, never()).assemble(any(), any(), any(), any());
        verify(outbox, never()).enqueue(any(), anyString());
    }

    @Test
    void staleRevisionIsRefused() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.DRAFT, 5);
        when(runs.findByPeriod("2026-09")).thenReturn(Optional.of(sep));
        when(runs.lockByPeriod("2026-09")).thenReturn(Optional.of(sep));
        when(operations.findByOperationKey(anyString())).thenReturn(Optional.empty());
        PayrollException ex = assertThrows(PayrollException.class,
                () -> finalizeService.finalizeRun("2026-09", true, 4L, 1L));
        assertEquals("STALE_REVISION", ex.code());
        verify(outbox, never()).enqueue(any(), anyString());
    }

    @Test
    void finaliseNeedsSecondConfirmation() {
        PayrollException ex = assertThrows(PayrollException.class,
                () -> finalizeService.finalizeRun("2026-09", false, 1L, 1L));
        assertEquals("CONFIRM_REQUIRED", ex.code());
    }

    // ---------------------------------------------------------------- Decision D5 (negative net)

    @Test
    void adjustmentThatWouldMakeNetPayNegativeIsRefusedWithTheLimit() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.DRAFT, 3);
        PayrollInputs.Line in = arpaSeptember(2); // base 15000, deduction 681.82 → 14318.18 recoverable
        PayrollLine row = rowFrom(in);
        row.setFacts(mapper.convertValue(in, Map.class));
        when(runs.lockByPeriod("2026-09")).thenReturn(Optional.of(sep));
        when(lines.findByRunIdAndEmployeeId(1L, 31L)).thenReturn(Optional.of(row));
        when(adjustments.findByPeriodAndEmployeeId("2026-09", 31L)).thenReturn(Optional.empty());
        PayrollException ex = assertThrows(PayrollException.class,
                () -> runService.setAdjustment("2026-09", 31L, "-20000", "Advance recovery", 1L));
        assertEquals("NEGATIVE_NET", ex.code());
        assertEquals(new BigDecimal("14318.18"), ex.details().get("maxRecoverable"));
        verify(audit, never()).record(any(), eq("PAYROLL_ADJUSTMENT"), anyString(), anyString(), any(), any());
    }

    @Test
    void adjustmentUpToTheLimitIsAccepted() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.DRAFT, 3);
        PayrollInputs.Line in = arpaSeptember(2);
        PayrollLine row = rowFrom(in);
        row.setFacts(mapper.convertValue(in, Map.class));
        when(runs.lockByPeriod("2026-09")).thenReturn(Optional.of(sep));
        when(runs.findByPeriod("2026-09")).thenReturn(Optional.of(sep));
        when(lines.findByRunIdAndEmployeeId(1L, 31L)).thenReturn(Optional.of(row));
        when(adjustments.findByPeriodAndEmployeeId("2026-09", 31L)).thenReturn(Optional.empty());
        runService.setAdjustment("2026-09", 31L, "-14318.18", "Advance recovery", 1L);
        assertEquals(0, row.getNetPay().signum());
        assertFalse(row.isException());
    }

    // ---------------------------------------------------------------- Decision D3 (currency per month)

    private static com.platform.payroll.domain.SalaryRecord salary(String date, String currency) {
        com.platform.payroll.domain.SalaryRecord r = new com.platform.payroll.domain.SalaryRecord();
        r.setEffectiveDate(LocalDate.parse(date));
        r.setCurrency(currency);
        return r;
    }

    @Test
    void currencyChangeMustStartOnTheFirst() {
        PayrollException ex = assertThrows(PayrollException.class, () -> SalaryService.requireWholeMonthCurrency(
                "AUD", LocalDate.of(2026, 10, 15), salary("2025-01-01", "BDT"), null));
        assertEquals("CURRENCY_CHANGE_MID_MONTH", ex.code());
        SalaryService.requireWholeMonthCurrency("AUD", LocalDate.of(2026, 11, 1), salary("2025-01-01", "BDT"), null);
        SalaryService.requireWholeMonthCurrency("BDT", LocalDate.of(2026, 10, 15), salary("2025-01-01", "BDT"), null);
    }

    @Test
    void insertingARecordCannotTurnTheNextOneIntoAMidMonthCurrencyChange() {
        PayrollException ex = assertThrows(PayrollException.class, () -> SalaryService.requireWholeMonthCurrency(
                "BDT", LocalDate.of(2026, 9, 1), salary("2025-01-01", "BDT"), salary("2026-10-15", "AUD")));
        assertEquals("CURRENCY_CHANGE_MID_MONTH", ex.code());
    }

    // ---------------------------------------------------------------- guide §6 result handling

    @Test
    void duplicateResultIsIgnored() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZED, 2);
        when(runs.lockById(1L)).thenReturn(Optional.of(sep));
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(op(sep, PayrollOperation.FINALIZE, PayrollOperation.SUCCEEDED)));
        finalizeService.onLeaveResult(Map.of("operationId", "op-1", "success", true));
        verify(revisions, never()).save(any());
        assertEquals(0.0, meters.counter(PayrollFinalizeService.RESULT_MISMATCH_METRIC).count());
    }

    @Test
    void resultContradictingAClosedOperationRaisesAnAlertAndChangesNothing() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.ACTION_REQUIRED, 2);
        when(runs.lockById(1L)).thenReturn(Optional.of(sep));
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(op(sep, PayrollOperation.FINALIZE, PayrollOperation.REJECTED)));
        finalizeService.onLeaveResult(Map.of("operationId", "op-1", "success", true, "status", "SUCCEEDED"));
        assertEquals(1.0, meters.counter(PayrollFinalizeService.RESULT_MISMATCH_METRIC).count());
        assertEquals(PayrollRun.ACTION_REQUIRED, sep.getStatus());
        verify(revisions, never()).save(any());
    }

    @Test
    void resultLocksTheRunBeforeReadingTheOperation() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZING, 2);
        sep.setOperationId("op-1");
        when(runs.lockById(1L)).thenReturn(Optional.of(sep));
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(op(sep, PayrollOperation.FINALIZE, PayrollOperation.PENDING)));
        finalizeService.onLeaveResult(Map.of("operationId", "op-1", "success", true));
        InOrder order = inOrder(runs, operations);
        order.verify(runs).lockById(1L);
        order.verify(operations).findByOperationId("op-1");
    }

    @Test
    void staleResultCannotFinaliseANewerRevision() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZING, 7);
        sep.setOperationId("op-1");
        PayrollOperation old = op(run(1, "2026-09", PayrollRun.FINALIZING, 6), PayrollOperation.FINALIZE, PayrollOperation.PENDING);
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(old));
        when(runs.lockById(1L)).thenReturn(Optional.of(sep));
        finalizeService.onLeaveResult(Map.of("operationId", "op-1", "success", true));
        assertEquals(PayrollRun.FINALIZING, sep.getStatus());
        verify(revisions, never()).save(any());
    }

    @Test
    void successfulResultFinalisesAndSnapshotsEveryLineWithItsInputs() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZING, 2);
        sep.setOperationId("op-1");
        PayrollOperation pending = op(sep, PayrollOperation.FINALIZE, PayrollOperation.PENDING);
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(pending));
        when(runs.lockById(1L)).thenReturn(Optional.of(sep));
        PayrollLine a = line(false);
        a.setFacts(new LinkedHashMap<>(Map.of("period", "2026-09", "days", List.of())));
        when(lines.findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(1L)).thenReturn(List.of(a, line(false)));
        finalizeService.onLeaveResult(Map.of("operationId", "op-1", "success", true));
        assertEquals(PayrollRun.FINALIZED, sep.getStatus());
        assertEquals(PayrollOperation.SUCCEEDED, pending.getStatus());
        ArgumentCaptor<PayslipRevision> saved = ArgumentCaptor.forClass(PayslipRevision.class);
        verify(revisions, times(2)).save(saved.capture());
        assertEquals(Map.of("period", "2026-09", "days", List.of()), saved.getAllValues().get(0).getSnapshot().get("facts"));
        verify(outbox, times(2)).enqueue(any(), anyString()); // one "Payslip ready" per employee
    }

    @Test
    void rejectionIsVisibleAndRecoverable() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZING, 2);
        sep.setOperationId("op-1");
        PayrollOperation pending = op(sep, PayrollOperation.FINALIZE, PayrollOperation.PENDING);
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(pending));
        when(runs.lockById(1L)).thenReturn(Optional.of(sep));
        finalizeService.onLeaveResult(Map.of("operationId", "op-1", "success", false,
                "errorMessage", "Employee 31: CASUAL has 0 day(s) left"));
        assertEquals(PayrollRun.ACTION_REQUIRED, sep.getStatus());
        assertEquals(PayrollOperation.REJECTED, pending.getStatus());
        assertTrue(sep.getActionRequiredReason().contains("CASUAL has 0"));
        verify(revisions, never()).save(any());
    }

    // ---------------------------------------------------------------- re-send (reconciler)

    @Test
    void resendLocksTheRunAndSkipsSettledOperations() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZED, 2);
        PayrollOperation done = op(sep, PayrollOperation.FINALIZE, PayrollOperation.SUCCEEDED);
        when(runs.lockById(1L)).thenReturn(Optional.of(sep));
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(done));
        finalizeService.resend(done);
        verify(runs).lockById(1L);
        verify(outbox, never()).enqueue(any(), anyString());
        verify(operations, never()).save(any());
    }

    @Test
    void resendRepeatsTheSameKeyedCommand() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZING, 2);
        PayrollOperation pending = pendingOld(sep, PayrollOperation.FINALIZE);
        when(runs.lockById(1L)).thenReturn(Optional.of(sep));
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(pending));
        finalizeService.resend(pending);
        assertEquals(2, pending.getAttempts());
        verify(outbox, times(1)).enqueue(any(), eq("payroll.leave-cover.command"));
    }

    // ---------------------------------------------------------------- manual resolve (guide §6)

    @Test
    void resolveIsRefusedWhileTheAutomaticResendStillHasItsChance() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZING, 2);
        PayrollOperation young = op(sep, PayrollOperation.FINALIZE, PayrollOperation.PENDING);
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(young));
        PayrollException ex = assertThrows(PayrollException.class, () -> finalizeService.resolve("op-1", null, "stuck", 1L));
        assertEquals("TOO_EARLY", ex.code());
        verify(facts, never()).fenceLeaveOperation(anyString(), anyString());
    }

    @Test
    void resolveNeedsAReason() {
        PayrollException ex = assertThrows(PayrollException.class, () -> finalizeService.resolve("op-1", null, "  ", 1L));
        assertEquals("REASON_REQUIRED", ex.code());
    }

    @Test
    void resolveFencesLeaveAndRejectsWhenLeaveNeverProcessedIt() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZING, 2);
        sep.setOperationId("op-1");
        PayrollOperation pending = pendingOld(sep, PayrollOperation.FINALIZE);
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(pending));
        when(runs.lockById(1L)).thenReturn(Optional.of(sep));
        when(facts.fenceLeaveOperation(eq("FINALIZE:2026-09:r2"), anyString())).thenReturn(Map.of(
                "known", true, "status", "CANCELLED", "success", false,
                "result", Map.of("errorMessage", "Cancelled by payroll before Leave processed it: stuck")));
        // The operator believes Leave applied it — Leave's word wins.
        Map<String, Object> view = finalizeService.resolve("op-1", "SUCCEEDED", "stuck", 7L);
        assertEquals(PayrollOperation.REJECTED, pending.getStatus());
        assertEquals(PayrollRun.ACTION_REQUIRED, sep.getStatus());
        assertEquals(7L, pending.getResolvedBy());
        assertEquals("CANCELLED", view.get("leaveStatus"));
        assertTrue(String.valueOf(view.get("note")).contains("Leave's outcome was applied"));
        verify(revisions, never()).save(any());
        verify(audit).record(eq(7L), eq("PAYROLL_OPERATION_RESOLVE"), anyString(), eq("op-1"), any(), any());
    }

    @Test
    void resolveCompletesWhenLeaveHadAppliedIt() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZING, 2);
        sep.setOperationId("op-1");
        PayrollOperation pending = pendingOld(sep, PayrollOperation.FINALIZE);
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(pending));
        when(runs.lockById(1L)).thenReturn(Optional.of(sep));
        when(lines.findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(1L)).thenReturn(List.of(line(false)));
        when(facts.fenceLeaveOperation(anyString(), anyString())).thenReturn(Map.of(
                "known", true, "status", "SUCCEEDED", "success", true, "result", Map.of("covers", 1)));
        finalizeService.resolve("op-1", null, "result message lost", 7L);
        assertEquals(PayrollOperation.SUCCEEDED, pending.getStatus());
        assertEquals(PayrollRun.FINALIZED, sep.getStatus());
        verify(revisions, times(1)).save(any());
    }

    @Test
    void resolvedReopenThatLeaveNeverAppliedKeepsTheMonthFinalised() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.REOPENING, 2);
        sep.setOperationId("op-1");
        PayrollOperation pending = pendingOld(sep, PayrollOperation.REOPEN);
        PayrollOperation finalised = op(sep, PayrollOperation.FINALIZE, PayrollOperation.SUCCEEDED);
        finalised.setOperationId("op-0");
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(pending));
        when(operations.findByRunIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(pending, finalised));
        when(runs.lockById(1L)).thenReturn(Optional.of(sep));
        when(facts.fenceLeaveOperation(anyString(), anyString())).thenReturn(Map.of("known", true, "status", "CANCELLED", "success", false));
        finalizeService.resolve("op-1", null, "stuck", 7L);
        assertEquals(PayrollRun.FINALIZED, sep.getStatus());
        assertEquals("op-0", sep.getOperationId());
        assertEquals(2, sep.getRevision()); // not reopened: the leave taken by the finalise is still taken
        verify(revisions, never()).findByRunIdAndSupersededAtIsNull(anyLong());
    }

    @Test
    void resolveChangesNothingWhenLeaveCannotBeReached() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZING, 2);
        PayrollOperation pending = pendingOld(sep, PayrollOperation.FINALIZE);
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(pending));
        when(facts.fenceLeaveOperation(anyString(), anyString()))
                .thenThrow(new FactsClient.FactsUnavailableException("Leave payroll facts unavailable"));
        assertThrows(FactsClient.FactsUnavailableException.class, () -> finalizeService.resolve("op-1", null, "stuck", 7L));
        assertEquals(PayrollOperation.PENDING, pending.getStatus());
        assertEquals(PayrollRun.FINALIZING, sep.getStatus());
        verify(runs, never()).lockById(anyLong());
    }

    @Test
    void resolveOfASettledOperationIsRefused() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZED, 2);
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(op(sep, PayrollOperation.FINALIZE, PayrollOperation.SUCCEEDED)));
        PayrollException ex = assertThrows(PayrollException.class, () -> finalizeService.resolve("op-1", null, "x", 1L));
        assertEquals("NOT_PENDING", ex.code());
    }

    // ---------------------------------------------------------------- reconciliation (guide §6)

    private PayrollRun finalisedSeptemberWithArpaCover() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZED, 2);
        sep.setOperationId("op-1");
        when(runs.findByPeriod("2026-09")).thenReturn(Optional.of(sep));
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(op(sep, PayrollOperation.FINALIZE, PayrollOperation.SUCCEEDED)));
        PayrollLine l = line(false);
        l.setCovers(List.of(Map.of("family", "CASUAL", "category", "CASUAL", "days", 1.0)));
        when(lines.findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(1L)).thenReturn(List.of(l));
        return sep;
    }

    private static Map<String, Object> row(String key, long emp, String cat, String held) {
        return Map.of("operationKey", key, "employeeId", emp, "category", cat, "days", held, "heldDays", held);
    }

    @Test
    void reconciliationMatchesWhenLeaveHoldsExactlyTheCurrentCovers() {
        finalisedSeptemberWithArpaCover();
        when(facts.coverLedgerForPeriod("2026-09")).thenReturn(Map.of("rows", List.of(
                row("FINALIZE:2026-09:r1", 31, "CASUAL", "0"),          // reversed by a reopen
                row("FINALIZE:2026-09:r2", 31, "CASUAL", "1.0"))));
        Map<String, Object> r = finalizeService.reconciliation("2026-09");
        assertEquals(true, r.get("matches"), r.toString());
    }

    @Test
    void reconciliationReportsCoverHeldUnderAnotherKey() {
        finalisedSeptemberWithArpaCover();
        when(facts.coverLedgerForPeriod("2026-09")).thenReturn(Map.of("rows", List.of(
                row("FINALIZE:2026-09:r1", 31, "CASUAL", "1.0"),        // orphan: never reversed
                row("FINALIZE:2026-09:r2", 31, "CASUAL", "1.0"))));
        Map<String, Object> r = finalizeService.reconciliation("2026-09");
        assertEquals(false, r.get("matches"));
        assertEquals(Map.of("FINALIZE:2026-09:r1", new BigDecimal("1.0")), r.get("orphanKeys"));
        assertFalse(((List<?>) r.get("differences")).isEmpty());
    }

    @Test
    void draftMonthMustHoldNoCover() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.DRAFT, 3);
        when(runs.findByPeriod("2026-09")).thenReturn(Optional.of(sep));
        when(facts.coverLedgerForPeriod("2026-09")).thenReturn(Map.of("rows", List.of(row("FINALIZE:2026-09:r2", 31, "CASUAL", "1.5"))));
        Map<String, Object> r = finalizeService.reconciliation("2026-09");
        assertEquals(false, r.get("matches"));
        assertNull(r.get("operationKey"));
    }

    @Test
    void reconciliationWaitsForAnOperationInFlight() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZING, 3);
        when(runs.findByPeriod("2026-09")).thenReturn(Optional.of(sep));
        assertEquals(false, finalizeService.reconciliation("2026-09").get("applicable"));
        verify(facts, never()).coverLedgerForPeriod(anyString());
    }
}
