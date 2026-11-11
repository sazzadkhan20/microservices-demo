package com.platform.leave.application;

import com.platform.leave.domain.LeaveBalanceLedger;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaveLedgerMathTest {

    @Test
    void formulaMatchesQuotaCarryAdjMinusTaken() {
        LeaveBalanceLedger opening = row("CASUAL", 2026, "20", LeaveBalanceLedger.SOURCE_OPENING, "Opening quota");
        LeaveBalanceLedger carry = row("CASUAL", 2026, "3", LeaveBalanceLedger.SOURCE_CARRY, "Year-end carry from 2025");
        LeaveBalanceLedger adj = row("CASUAL", 2026, "1.5", LeaveBalanceLedger.SOURCE_ADJUST, "Manual top-up");
        LeaveBalanceLedger approve = row("CASUAL", 2026, "-2", LeaveBalanceLedger.SOURCE_APPROVE, "Leave approved deduction");
        LeaveBalanceLedger cancel = row("CASUAL", 2026, "0.5", LeaveBalanceLedger.SOURCE_CANCEL, "Leave cancel restore");

        LeaveLedgerMath.Parts parts = LeaveLedgerMath.classify(
                List.of(opening, carry, adj, approve, cancel), "CASUAL", 2026);

        assertEquals(0, new BigDecimal("20").compareTo(parts.quota()));
        assertEquals(0, new BigDecimal("3").compareTo(parts.carried()));
        assertEquals(0, new BigDecimal("1.5").compareTo(parts.adjustments()));
        assertEquals(0, new BigDecimal("1.5").compareTo(parts.taken())); // 2 - 0.5
        assertEquals(0, new BigDecimal("23").compareTo(parts.netBalance())); // 20+3+1.5-1.5
    }

    @Test
    void infersLegacyNullSourceFromReason() {
        LeaveBalanceLedger approve = row("CASUAL", 2026, "-1", null, "Leave approved deduction");
        LeaveBalanceLedger cancel = row("CASUAL", 2026, "1", null, "Leave cancel restore");
        LeaveBalanceLedger cover = row("CASUAL", 2026, "-2", null, "Payroll absence cover 2026-10");
        LeaveBalanceLedger opening = row("CASUAL", 2026, "10", null, "Opening quota (pro-rata)");

        LeaveLedgerMath.Parts parts = LeaveLedgerMath.classify(
                List.of(approve, cancel, cover, opening), "CASUAL", 2026);

        assertEquals(0, new BigDecimal("10").compareTo(parts.quota()));
        assertEquals(0, new BigDecimal("-2").compareTo(parts.adjustments()));
        assertEquals(0, BigDecimal.ZERO.compareTo(parts.taken())); // approve 1 - cancel 1
        assertEquals(0, new BigDecimal("8").compareTo(parts.netBalance()));
    }

    @Test
    void coverReversalCountsAsAdjustment() {
        LeaveBalanceLedger cover = row("ANNUAL", 2026, "-2", LeaveBalanceLedger.SOURCE_COVER, "cover");
        LeaveBalanceLedger rev = row("ANNUAL", 2026, "2", LeaveBalanceLedger.SOURCE_COVER_REVERSAL, "reopen");
        LeaveLedgerMath.Parts parts = LeaveLedgerMath.classify(List.of(cover, rev), "ANNUAL", 2026);
        assertEquals(0, BigDecimal.ZERO.compareTo(parts.adjustments()));
        assertEquals(0, BigDecimal.ZERO.compareTo(parts.netBalance()));
    }

    private static LeaveBalanceLedger row(String code, int year, String days, String source, String reason) {
        LeaveBalanceLedger l = new LeaveBalanceLedger();
        l.setEmployeeId(1L);
        l.setLeaveTypeCode(code);
        l.setYear(year);
        l.setDeltaDays(new BigDecimal(days));
        l.setSourceType(source);
        l.setReason(reason);
        return l;
    }
}
