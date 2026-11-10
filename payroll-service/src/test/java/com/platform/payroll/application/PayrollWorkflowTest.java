package com.platform.payroll.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.messaging.outbox.OutboxRelay;
import com.platform.messaging.outbox.OutboxWriter;
import com.platform.payroll.domain.PayrollLine;
import com.platform.payroll.domain.PayrollOperation;
import com.platform.payroll.domain.PayrollRun;
import com.platform.payroll.domain.PayrollSettings;
import com.platform.payroll.infrastructure.FactsClient;
import com.platform.payroll.infrastructure.persistence.PayrollAdjustmentRepository;
import com.platform.payroll.infrastructure.persistence.PayrollLineRepository;
import com.platform.payroll.infrastructure.persistence.PayrollOperationRepository;
import com.platform.payroll.infrastructure.persistence.PayrollRunRepository;
import com.platform.payroll.infrastructure.persistence.PayslipRevisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Guide §6 / §13 PAY01–PAY02 and SRS BR-18 / FR-PAY-12 gates, without a database. */
class PayrollWorkflowTest {

    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final PayrollLineRepository lines = mock(PayrollLineRepository.class);
    private final PayrollOperationRepository operations = mock(PayrollOperationRepository.class);
    private final PayslipRevisionRepository revisions = mock(PayslipRevisionRepository.class);
    private final PayrollSettingsService settings = mock(PayrollSettingsService.class);
    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final AuditService audit = mock(AuditService.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    // "Today" is 10 Nov 2026 in Dhaka.
    private final PayrollClock clock = new PayrollClock(ZoneId.of("Asia/Dhaka"),
            Clock.fixed(Instant.parse("2026-11-10T06:00:00Z"), ZoneId.of("UTC")));
    private PayrollRunService runService;
    private PayrollFinalizeService finalizeService;

    @BeforeEach
    void setUp() {
        runService = new PayrollRunService(runs, lines, mock(PayrollAdjustmentRepository.class), operations,
                mock(PayrollFactsAssembler.class), settings, clock, audit, new ObjectMapper(), tx);
        @SuppressWarnings("unchecked")
        ObjectProvider<OutboxRelay> relay = mock(ObjectProvider.class);
        finalizeService = new PayrollFinalizeService(runs, lines, operations, revisions, runService, settings, outbox,
                relay, audit, mock(FactsClient.class), tx);
        when(settings.load()).thenReturn(new PayrollSettings());
        when(runs.findByPeriodLessThanOrderByPeriodAsc(anyString())).thenReturn(List.of());
        when(runs.findByPeriod(anyString())).thenReturn(Optional.empty());
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

    // ---------------------------------------------------------------- BR-18 / FR-PAY-12

    @Test
    void cannotFinaliseBeforeMonthEnds() {
        List<String> blockers = runService.finalizeBlockers(run(1, "2026-11", PayrollRun.DRAFT, 3), List.of(line(false)));
        assertTrue(blockers.stream().anyMatch(b -> b.startsWith("The month has not ended")), blockers.toString());
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

    @Test
    void staleRevisionIsRefused() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.DRAFT, 5);
        when(runs.lockByPeriod("2026-09")).thenReturn(Optional.of(sep));
        when(operations.findByOperationKey(anyString())).thenReturn(Optional.empty());
        PayrollException ex = org.junit.jupiter.api.Assertions.assertThrows(PayrollException.class,
                () -> finalizeService.finalizeRun("2026-09", true, 4L, 1L));
        assertEquals("STALE_REVISION", ex.code());
        verify(outbox, never()).enqueue(any(), anyString());
    }

    @Test
    void finaliseNeedsSecondConfirmation() {
        PayrollException ex = org.junit.jupiter.api.Assertions.assertThrows(PayrollException.class,
                () -> finalizeService.finalizeRun("2026-09", false, 1L, 1L));
        assertEquals("CONFIRM_REQUIRED", ex.code());
    }

    // ---------------------------------------------------------------- guide §6 result handling

    @Test
    void duplicateResultIsIgnored() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZED, 2);
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(op(sep, PayrollOperation.FINALIZE, PayrollOperation.SUCCEEDED)));
        finalizeService.onLeaveResult(Map.of("operationId", "op-1", "success", true));
        verify(runs, never()).lockById(anyLong());
        verify(revisions, never()).save(any());
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
    void successfulResultFinalisesAndSnapshotsEveryLine() {
        PayrollRun sep = run(1, "2026-09", PayrollRun.FINALIZING, 2);
        sep.setOperationId("op-1");
        PayrollOperation pending = op(sep, PayrollOperation.FINALIZE, PayrollOperation.PENDING);
        when(operations.findByOperationId("op-1")).thenReturn(Optional.of(pending));
        when(runs.lockById(1L)).thenReturn(Optional.of(sep));
        when(lines.findByRunIdOrderByEmployeeNameAscEmployeeIdAsc(1L)).thenReturn(List.of(line(false), line(false)));
        finalizeService.onLeaveResult(Map.of("operationId", "op-1", "success", true));
        assertEquals(PayrollRun.FINALIZED, sep.getStatus());
        assertEquals(PayrollOperation.SUCCEEDED, pending.getStatus());
        verify(revisions, times(2)).save(any());
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
}
