package com.platform.leave.application;

import com.platform.leave.domain.LeaveBalanceLedger;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Shared leave-ledger classification used by balance breakdown, quota sync, and expiry.
 * Formula: {@code balance = quota + carried + adjustments − taken}
 * (available = balance − pending).
 */
public final class LeaveLedgerMath {

    private LeaveLedgerMath() {}

    public record Parts(BigDecimal quota, BigDecimal carried, BigDecimal adjustments, BigDecimal taken) {
        public BigDecimal netBalance() {
            return LeaveDays.of(quota.add(carried).add(adjustments).subtract(taken));
        }
    }

    /**
     * Resolve effective source type. Blank / null rows are inferred from reason text so
     * legacy seed / pre-sourceType ledgers still compose correctly for payroll.
     */
    public static String resolveSource(LeaveBalanceLedger ledger) {
        String src = ledger.getSourceType() == null ? "" : ledger.getSourceType().trim().toUpperCase(Locale.ROOT);
        if (!src.isEmpty()) {
            if ("COVER_REVERSAL".equals(src)) {
                return LeaveBalanceLedger.SOURCE_COVER;
            }
            return src;
        }
        String reason = ledger.getReason() == null ? "" : ledger.getReason().toLowerCase(Locale.ROOT);
        BigDecimal d = ledger.getDeltaDays() == null ? BigDecimal.ZERO : ledger.getDeltaDays();
        if (reason.contains("opening") || reason.contains("join grant")) {
            return LeaveBalanceLedger.SOURCE_OPENING;
        }
        if (reason.contains("quota sync") || reason.contains("quota change") || reason.contains("pro-rata")) {
            // "Opening quota (pro-rata)" already matched opening above.
            if (reason.contains("opening")) {
                return LeaveBalanceLedger.SOURCE_OPENING;
            }
            return LeaveBalanceLedger.SOURCE_QUOTA;
        }
        if (reason.contains("year-end carry") || reason.contains("carry from")) {
            return LeaveBalanceLedger.SOURCE_CARRY;
        }
        if (reason.contains("carry expired") || reason.contains("expired on")) {
            return LeaveBalanceLedger.SOURCE_EXPIRE;
        }
        if (reason.contains("approved deduction") || reason.contains("leave approved")) {
            return LeaveBalanceLedger.SOURCE_APPROVE;
        }
        if (reason.contains("cancel restore") || reason.contains("leave cancel")) {
            return LeaveBalanceLedger.SOURCE_CANCEL;
        }
        if (reason.contains("absence cover") || reason.contains("payroll cover") || reason.contains("cover")) {
            return LeaveBalanceLedger.SOURCE_COVER;
        }
        if (reason.contains("adjust") || reason.contains("reconcil") || reason.contains("top-up")) {
            return LeaveBalanceLedger.SOURCE_ADJUST;
        }
        // Unknown legacy: credits behave like adjustments; debits like taken.
        return d.compareTo(BigDecimal.ZERO) >= 0
                ? LeaveBalanceLedger.SOURCE_ADJUST
                : LeaveBalanceLedger.SOURCE_APPROVE;
    }

    public static Parts classify(Iterable<LeaveBalanceLedger> ledgers, String leaveTypeCode, int year) {
        BigDecimal quota = BigDecimal.ZERO;
        BigDecimal carried = BigDecimal.ZERO;
        BigDecimal adjustments = BigDecimal.ZERO;
        BigDecimal taken = BigDecimal.ZERO;

        for (LeaveBalanceLedger l : ledgers) {
            if (l == null) {
                continue;
            }
            if (!leaveTypeCode.equalsIgnoreCase(l.getLeaveTypeCode()) || l.getYear() != year) {
                continue;
            }
            BigDecimal d = LeaveDays.of(l.getDeltaDays());
            String src = resolveSource(l);
            switch (src) {
                case LeaveBalanceLedger.SOURCE_OPENING, LeaveBalanceLedger.SOURCE_QUOTA -> quota = quota.add(d);
                case LeaveBalanceLedger.SOURCE_CARRY, LeaveBalanceLedger.SOURCE_EXPIRE -> carried = carried.add(d);
                case LeaveBalanceLedger.SOURCE_ADJUST, LeaveBalanceLedger.SOURCE_COVER -> adjustments = adjustments.add(d);
                case LeaveBalanceLedger.SOURCE_APPROVE -> taken = taken.add(d.abs());
                case LeaveBalanceLedger.SOURCE_CANCEL -> taken = taken.subtract(d.abs());
                default -> {
                    if (d.compareTo(BigDecimal.ZERO) >= 0) {
                        adjustments = adjustments.add(d);
                    } else {
                        taken = taken.add(d.abs());
                    }
                }
            }
        }

        // Cancel surplus beyond taken is a credit — keep it in the formula via adjustments.
        if (taken.compareTo(BigDecimal.ZERO) < 0) {
            adjustments = adjustments.add(taken.negate());
            taken = BigDecimal.ZERO;
        }
        // Expire beyond carry is already a debit on balance; fold into adjustments so net stays exact.
        if (carried.compareTo(BigDecimal.ZERO) < 0) {
            adjustments = adjustments.add(carried);
            carried = BigDecimal.ZERO;
        }

        return new Parts(
                LeaveDays.of(quota),
                LeaveDays.of(carried),
                LeaveDays.of(adjustments),
                LeaveDays.of(taken));
    }

    public static boolean isQuotaGrant(String sourceType) {
        if (sourceType == null) {
            return false;
        }
        String s = sourceType.trim().toUpperCase(Locale.ROOT);
        return LeaveBalanceLedger.SOURCE_OPENING.equals(s) || LeaveBalanceLedger.SOURCE_QUOTA.equals(s);
    }
}
