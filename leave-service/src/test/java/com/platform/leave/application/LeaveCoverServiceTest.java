package com.platform.leave.application;

import com.platform.leave.domain.LeaveBalanceLedger;
import com.platform.leave.domain.LeaveOperation;
import com.platform.leave.infrastructure.persistence.LeaveBalanceLedgerRepository;
import com.platform.leave.infrastructure.persistence.LeaveCategoryRepository;
import com.platform.leave.infrastructure.persistence.LeaveOperationRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Payroll leave cover in Leave (guide §6, docs/PAYROLL_SPEC.md §2.3): all or nothing, once per key. */
class LeaveCoverServiceTest {

    private final LeaveBalanceService balances = mock(LeaveBalanceService.class);
    private final LeaveCategoryRepository categories = mock(LeaveCategoryRepository.class);
    private final LeaveBalanceLedgerRepository ledger = mock(LeaveBalanceLedgerRepository.class);
    private final LeaveCoverService service = new LeaveCoverService(balances, categories, ledger);

    private static List<LeaveCoverService.CoverLine> covers() {
        return LeaveCoverService.parseLines(List.of(
                Map.of("employeeId", 7, "covers", List.of(Map.of("category", "casual", "days", 1.5))),
                Map.of("employeeId", 12, "covers", List.of(Map.of("category", "ANNUAL", "days", 2)))));
    }

    @Test
    void parsesAndNormalisesCommandLines() {
        List<LeaveCoverService.CoverLine> lines = covers();
        assertEquals(2, lines.size());
        assertEquals(new LeaveCoverService.CoverLine(7L, "CASUAL", new BigDecimal("1.5")), lines.get(0));
    }

    @Test
    void malformedCommandsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> LeaveCoverService.parseLines("x"));
        assertThrows(IllegalArgumentException.class, () -> LeaveCoverService.parseLines(List.of(Map.of("employeeId", "abc"))));
        assertThrows(IllegalArgumentException.class, () -> LeaveCoverService.parseLines(List.of(
                Map.of("employeeId", 7, "covers", List.of(Map.of("category", "CASUAL", "days", -1))))));
        assertThrows(IllegalArgumentException.class, () -> LeaveCoverService.parseLines(List.of(
                Map.of("employeeId", 7, "covers", List.of(Map.of("category", "CASUAL", "days", 1),
                        Map.of("category", "casual", "days", 1))))));
    }

    @Test
    void appliesEveryCoverWithAKeyPerEmployeeAndCategory() {
        when(ledger.findByOperationKey(anyString())).thenReturn(Optional.empty());
        when(balances.availableAfterPending(anyLong(), anyString(), anyInt())).thenReturn(new BigDecimal("5"));
        List<String> problems = service.applyFinalize("FINALIZE:2026-10:r7", "2026-10", covers());
        assertTrue(problems.isEmpty());
        verify(balances).adjustBySystem(eq(7L), eq("CASUAL"), eq(new BigDecimal("-1.5")), anyString(), eq(2026),
                eq("COVER:FINALIZE:2026-10:r7:7:CASUAL"), eq(LeaveBalanceLedger.SOURCE_COVER), eq("2026-10"));
        verify(balances).adjustBySystem(eq(12L), eq("ANNUAL"), eq(new BigDecimal("-2")), anyString(), eq(2026),
                eq("COVER:FINALIZE:2026-10:r7:12:ANNUAL"), eq(LeaveBalanceLedger.SOURCE_COVER), eq("2026-10"));
    }

    @Test
    void oneShortBalanceRejectsTheWholeMonthAndWritesNothing() {
        when(ledger.findByOperationKey(anyString())).thenReturn(Optional.empty());
        when(balances.availableAfterPending(eq(7L), anyString(), anyInt())).thenReturn(new BigDecimal("5"));
        when(balances.availableAfterPending(eq(12L), anyString(), anyInt())).thenReturn(BigDecimal.ZERO);
        List<String> problems = service.applyFinalize("FINALIZE:2026-10:r7", "2026-10", covers());
        assertEquals(List.of("Employee 12: ANNUAL has 0 day(s) left but payroll covered 2 — recalculate 2026-10"), problems);
        verify(balances, never()).adjustBySystem(anyLong(), anyString(), any(), anyString(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void coversAlreadyRecordedForTheKeyAreNotTakenAgain() {
        when(ledger.findByOperationKey(anyString())).thenReturn(Optional.of(new LeaveBalanceLedger()));
        assertTrue(service.applyFinalize("FINALIZE:2026-10:r7", "2026-10", covers()).isEmpty());
        verify(balances, never()).adjustBySystem(anyLong(), anyString(), any(), anyString(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void reversalReturnsExactlyTheRecordedRowsOnce() {
        LeaveBalanceLedger taken = new LeaveBalanceLedger();
        taken.setEmployeeId(7L);
        taken.setLeaveTypeCode("CASUAL");
        taken.setYear(2026);
        taken.setDeltaDays(new BigDecimal("-1.5"));
        taken.setOperationKey("COVER:FINALIZE:2026-10:r7:7:CASUAL");
        when(ledger.findByOperationKeyStartingWithOrderByIdAsc("COVER:FINALIZE:2026-10:r7:")).thenReturn(List.of(taken));
        when(ledger.findByOperationKey("REVERSE:COVER:FINALIZE:2026-10:r7:7:CASUAL")).thenReturn(Optional.empty());
        assertEquals(1, service.applyReversal("FINALIZE:2026-10:r7", "2026-10"));
        verify(balances, times(1)).adjustBySystem(eq(7L), eq("CASUAL"), eq(new BigDecimal("1.5")), anyString(), eq(2026),
                eq("REVERSE:COVER:FINALIZE:2026-10:r7:7:CASUAL"), eq(LeaveBalanceLedger.SOURCE_COVER_REVERSAL), eq("2026-10"));

        when(ledger.findByOperationKey("REVERSE:COVER:FINALIZE:2026-10:r7:7:CASUAL")).thenReturn(Optional.of(new LeaveBalanceLedger()));
        assertEquals(0, service.applyReversal("FINALIZE:2026-10:r7", "2026-10"));
    }

    @Test
    void monthLedgerShowsWhatEachFinaliseKeyStillHolds() {
        LeaveBalanceLedger reversed = new LeaveBalanceLedger();
        reversed.setEmployeeId(7L);
        reversed.setLeaveTypeCode("CASUAL");
        reversed.setDeltaDays(new BigDecimal("-1.5"));
        reversed.setOperationKey("COVER:FINALIZE:2026-10:r7:7:CASUAL");
        LeaveBalanceLedger held = new LeaveBalanceLedger();
        held.setEmployeeId(7L);
        held.setLeaveTypeCode("CASUAL");
        held.setDeltaDays(new BigDecimal("-1.5"));
        held.setOperationKey("COVER:FINALIZE:2026-10:r12:7:CASUAL");
        when(ledger.findByOperationKeyStartingWithOrderByIdAsc("COVER:FINALIZE:2026-10:")).thenReturn(List.of(reversed, held));
        when(ledger.findByOperationKey("REVERSE:COVER:FINALIZE:2026-10:r7:7:CASUAL")).thenReturn(Optional.of(new LeaveBalanceLedger()));
        when(ledger.findByOperationKey("REVERSE:COVER:FINALIZE:2026-10:r12:7:CASUAL")).thenReturn(Optional.empty());
        List<Map<String, Object>> rows = service.ledgerForPeriod("2026-10");
        assertEquals("FINALIZE:2026-10:r7", rows.get(0).get("operationKey"));
        assertEquals(BigDecimal.ZERO, rows.get(0).get("heldDays"));
        assertEquals("FINALIZE:2026-10:r12", rows.get(1).get("operationKey"));
        assertEquals(new BigDecimal("1.5"), rows.get(1).get("heldDays"));
    }

    private static com.platform.leave.domain.LeaveCategory category(String code, String start, String end) {
        com.platform.leave.domain.LeaveCategory c = new com.platform.leave.domain.LeaveCategory();
        c.setCode(code);
        c.setActive(true);
        c.setPaid(true);
        c.setPeriodStart(java.time.LocalDate.parse(start));
        c.setPeriodEnd(java.time.LocalDate.parse(end));
        return c;
    }

    @Test
    void coverUsesTheBalanceYearOfTheCategoryRowValidInThePayrollMonth() {
        // The same code exists for 2026 and 2027; the 2027 row is returned first by the repository.
        when(categories.findByCodeIgnoreCase("CASUAL")).thenReturn(List.of(
                category("CASUAL", "2027-01-01", "2027-12-31"), category("CASUAL", "2026-01-01", "2026-12-31")));
        assertEquals(2026, service.resolveByCode("CASUAL", java.time.YearMonth.of(2026, 12)).year());
        assertEquals(2027, service.resolveByCode("CASUAL", java.time.YearMonth.of(2027, 1)).year());
    }

    @Test
    void decemberFinaliseInJanuaryTakesFromTheDecemberLeaveYear() {
        when(categories.findByCodeIgnoreCase("CASUAL")).thenReturn(List.of(
                category("CASUAL", "2027-01-01", "2027-12-31"), category("CASUAL", "2026-01-01", "2026-12-31")));
        when(ledger.findByOperationKey(anyString())).thenReturn(Optional.empty());
        when(balances.availableAfterPending(7L, "CASUAL", 2026)).thenReturn(new BigDecimal("2"));
        List<String> problems = service.applyFinalize("FINALIZE:2026-12:r3", "2026-12",
                LeaveCoverService.parseLines(List.of(Map.of("employeeId", 7, "covers", List.of(Map.of("category", "CASUAL", "days", 1))))));
        assertTrue(problems.isEmpty(), problems.toString());
        verify(balances).adjustBySystem(eq(7L), eq("CASUAL"), eq(new BigDecimal("-1")), anyString(), eq(2026),
                eq("COVER:FINALIZE:2026-12:r3:7:CASUAL"), anyString(), eq("2026-12"));
    }

    @Test
    void finaliseKeyIsParsedFromACoverRowKey() {
        assertEquals("FINALIZE:2026-10:r7", LeaveCoverService.finalizeKeyOf("COVER:FINALIZE:2026-10:r7:31:CASUAL_2026"));
    }

    // ---------------------------------------------------------------- fence (manual resolve)

    private final LeaveOperationRepository operations = mock(LeaveOperationRepository.class);
    private final PayrollOperationFenceService fences = new PayrollOperationFenceService(operations);

    @Test
    void fenceRecordsCancelledWhenLeaveNeverProcessedTheKey() {
        when(operations.findByOperationKey("FINALIZE:2026-10:r7")).thenReturn(Optional.empty());
        when(operations.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        LeaveOperation op = fences.fence("FINALIZE:2026-10:r7", "stuck");
        assertEquals(LeaveOperation.STATUS_CANCELLED, op.getStatus());
        assertEquals("2026-10", op.getPeriodLabel());
        assertEquals(false, op.getResultJson().get("success"));
        Map<String, Object> view = PayrollOperationFenceService.view("FINALIZE:2026-10:r7", op);
        assertEquals(false, view.get("success"));
        assertEquals(true, view.get("known"));
    }

    @Test
    void fenceKeepsAnOutcomeLeaveAlreadyRecorded() {
        LeaveOperation done = new LeaveOperation();
        done.setStatus(LeaveOperation.STATUS_SUCCEEDED);
        when(operations.findByOperationKey("REOPEN:2026-10:r7:a1")).thenReturn(Optional.of(done));
        assertSame(done, fences.fence("REOPEN:2026-10:r7:a1", "stuck"));
        verify(operations, never()).saveAndFlush(any());
        assertEquals(true, PayrollOperationFenceService.view("REOPEN:2026-10:r7:a1", done).get("success"));
    }

    @Test
    void fenceRefusesAnythingButAPayrollOperationKey() {
        assertThrows(IllegalArgumentException.class, () -> fences.fence("COVER:FINALIZE:2026-10:r7:1:CASUAL", "x"));
        assertThrows(IllegalArgumentException.class, () -> fences.fence("FINALIZE:2026-13:r7", "x"));
    }
}
