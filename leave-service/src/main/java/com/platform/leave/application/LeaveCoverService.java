package com.platform.leave.application;

import com.platform.leave.domain.LeaveBalanceLedger;
import com.platform.leave.domain.LeaveCategory;
import com.platform.leave.infrastructure.persistence.LeaveBalanceLedgerRepository;
import com.platform.leave.infrastructure.persistence.LeaveCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Payroll absence cover against leave balances (SRS FR-PAY-04/12/14, guide §6, docs/PAYROLL_SPEC.md §2.3).
 *
 * <p>Finalise takes covered days from balances as ledger entries keyed
 * {@code COVER:<payrollOperationKey>:<employee>:<category>}; reopen reverses exactly those recorded rows
 * with keys {@code REVERSE:<coverKey>}. Ledger keys are unique, so a re-delivered or re-sent command can
 * never deduct or restore twice. Ledger writes run inside the command listener's transaction, so the
 * inbox key, the ledger rows, the {@code leave_operation} row and the result outbox commit together.
 */
@Service
public class LeaveCoverService {

    public static final String SOURCE_COVER_REVERSAL = LeaveBalanceLedger.SOURCE_COVER_REVERSAL;

    private final LeaveBalanceService balances;
    private final LeaveCategoryRepository categories;
    private final LeaveBalanceLedgerRepository ledger;

    public LeaveCoverService(LeaveBalanceService balances, LeaveCategoryRepository categories,
                             LeaveBalanceLedgerRepository ledger) {
        this.balances = balances;
        this.categories = categories;
        this.ledger = ledger;
    }

    /** A cover category resolved for a payroll month: actual balance code and leave year. */
    public record CoverCategory(String family, String code, int year) {
    }

    /** One covered absence in a payroll finalise command. */
    public record CoverLine(Long employeeId, String category, BigDecimal days) {
    }

    /**
     * Decision D4 — cover family {@code CASUAL} matches category {@code CASUAL} or a renewed copy
     * {@code CASUAL_<suffix>} whose validity period contains the month; only paid, active categories.
     */
    @Transactional(readOnly = true)
    public Optional<CoverCategory> resolve(String family, YearMonth month) {
        String fam = family.toUpperCase(Locale.ROOT);
        LocalDate mid = month.atDay(Math.min(15, month.lengthOfMonth()));
        return categories.findAll().stream()
                .filter(c -> c.getCode() != null)
                .filter(c -> c.getCode().equalsIgnoreCase(fam) || c.getCode().toUpperCase(Locale.ROOT).startsWith(fam + "_"))
                .filter(c -> c.isActive() && c.isPaid())
                .filter(c -> c.getPeriodStart() == null || !c.getPeriodStart().isAfter(month.atEndOfMonth()))
                .filter(c -> c.getPeriodEnd() == null || !c.getPeriodEnd().isBefore(month.atDay(1)))
                .min(Comparator.comparing((LeaveCategory c) -> c.getPeriodStart() == null || !c.getPeriodStart().isAfter(mid) ? 0 : 1)
                        .thenComparing(c -> c.getCode().length()))
                .map(c -> new CoverCategory(fam, c.getCode().toUpperCase(Locale.ROOT),
                        c.getPeriodStart() == null ? month.getYear() : c.getPeriodStart().getYear()));
    }

    /** Usable balance for cover: balance minus days reserved by pending requests (Decision D4). */
    @Transactional(readOnly = true)
    public BigDecimal usable(Long employeeId, CoverCategory cat) {
        return balances.availableAfterPending(employeeId, cat.code(), cat.year()).max(BigDecimal.ZERO);
    }

    /**
     * Parses the {@code lines} of a payroll finalise command. A malformed command fails the same way on
     * every retry, so it is a permanent error that is reported back, never retried.
     *
     * @throws IllegalArgumentException when the command is malformed
     */
    public static List<CoverLine> parseLines(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> lines)) {
            throw new IllegalArgumentException("lines must be a list");
        }
        Map<String, CoverLine> seen = new LinkedHashMap<>();
        for (Object o : lines) {
            if (!(o instanceof Map<?, ?> line)) {
                throw new IllegalArgumentException("each line must be an object");
            }
            Long emp;
            try {
                emp = Long.valueOf(String.valueOf(line.get("employeeId")));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid employeeId " + line.get("employeeId"));
            }
            Object covers = line.get("covers");
            if (covers == null) {
                continue;
            }
            if (!(covers instanceof List<?> list)) {
                throw new IllegalArgumentException("covers of employee " + emp + " must be a list");
            }
            for (Object c : list) {
                if (!(c instanceof Map<?, ?> cover) || cover.get("category") == null) {
                    throw new IllegalArgumentException("invalid cover for employee " + emp);
                }
                String code = String.valueOf(cover.get("category")).trim().toUpperCase(Locale.ROOT);
                BigDecimal days;
                try {
                    days = new BigDecimal(String.valueOf(cover.get("days")));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("invalid days for employee " + emp + " " + code);
                }
                if (code.isEmpty() || days.signum() < 0) {
                    throw new IllegalArgumentException("invalid cover for employee " + emp + " " + code);
                }
                if (days.signum() == 0) {
                    continue;
                }
                String k = emp + ":" + code;
                if (seen.containsKey(k)) {
                    throw new IllegalArgumentException("duplicate cover for employee " + emp + " " + code);
                }
                seen.put(k, new CoverLine(emp, code, days));
            }
        }
        return new ArrayList<>(seen.values());
    }

    /**
     * Applies all covers of one payroll finalisation, all or nothing, inside the caller's transaction
     * (guide §6: inbox, ledger and result outbox in one local transaction). Every cover is validated
     * before anything is written, so a business rejection writes nothing; any other failure is thrown
     * and rolls the whole delivery back so it is retried.
     *
     * @return per-employee problems; empty when everything was applied.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> applyFinalize(String operationKey, String period, List<CoverLine> lines) {
        YearMonth month = YearMonth.parse(period);
        List<String> problems = new ArrayList<>();
        record Item(Long employeeId, CoverCategory cat, BigDecimal days) { }
        List<Item> items = new ArrayList<>();
        for (CoverLine line : lines) {
            CoverCategory cat = resolveByCode(line.category(), month);
            String key = coverKey(operationKey, line.employeeId(), line.category());
            if (ledger.findByOperationKey(key).isPresent()) {
                continue; // already applied by an earlier delivery
            }
            BigDecimal usable = usable(line.employeeId(), cat);
            if (usable.compareTo(line.days()) < 0) {
                problems.add("Employee " + line.employeeId() + ": " + line.category() + " has "
                        + usable.stripTrailingZeros().toPlainString() + " day(s) left but payroll covered "
                        + line.days().stripTrailingZeros().toPlainString() + " — recalculate " + period);
            }
            items.add(new Item(line.employeeId(), cat, line.days()));
        }
        if (!problems.isEmpty()) {
            return problems; // nothing written: reject the whole finalisation explicitly
        }
        for (Item it : items) {
            balances.adjustBySystem(it.employeeId(), it.cat().code(), it.days().negate(),
                    "Payroll absence cover " + period, it.cat().year(),
                    coverKey(operationKey, it.employeeId(), it.cat().code()),
                    LeaveBalanceLedger.SOURCE_COVER, period);
        }
        return List.of();
    }

    /**
     * Reverses exactly the rows recorded for the finalise key (restores once, even if re-delivered),
     * inside the caller's transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int applyReversal(String reverseOfKey, String period) {
        int n = 0;
        for (LeaveBalanceLedger row : ledger.findByOperationKeyStartingWithOrderByIdAsc("COVER:" + reverseOfKey + ":")) {
            String key = "REVERSE:" + row.getOperationKey();
            if (ledger.findByOperationKey(key).isPresent()) {
                continue;
            }
            balances.adjustBySystem(row.getEmployeeId(), row.getLeaveTypeCode(), row.getDeltaDays().negate(),
                    "Payroll " + period + " reopened — absence cover returned", row.getYear(), key,
                    LeaveBalanceLedger.SOURCE_COVER_REVERSAL, period);
            n++;
        }
        return n;
    }

    /** Net days Leave holds for a payroll finalise key, per employee and category (reconciliation). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> ledgerFor(String operationKey) {
        Map<String, BigDecimal> net = new LinkedHashMap<>();
        Map<String, Long> emps = new LinkedHashMap<>();
        Map<String, String> cats = new LinkedHashMap<>();
        for (LeaveBalanceLedger row : ledger.findByOperationKeyStartingWithOrderByIdAsc("COVER:" + operationKey + ":")) {
            String k = row.getEmployeeId() + ":" + row.getLeaveTypeCode();
            BigDecimal taken = row.getDeltaDays().negate();
            if (ledger.findByOperationKey("REVERSE:" + row.getOperationKey()).isPresent()) {
                taken = BigDecimal.ZERO;
            }
            net.merge(k, taken, BigDecimal::add);
            emps.put(k, row.getEmployeeId());
            cats.put(k, row.getLeaveTypeCode());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        net.forEach((k, v) -> out.add(Map.of("employeeId", emps.get(k), "category", cats.get(k), "days", v)));
        return out;
    }

    /**
     * Every payroll cover row Leave recorded for a month, across all finalise keys, with what is still
     * held (not reversed). Lets payroll detect covers that belong to no finalised revision (guide §6).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> ledgerForPeriod(String period) {
        YearMonth.parse(period);
        List<Map<String, Object>> out = new ArrayList<>();
        for (LeaveBalanceLedger row : ledger.findByOperationKeyStartingWithOrderByIdAsc("COVER:FINALIZE:" + period + ":")) {
            String rowKey = row.getOperationKey();
            boolean reversed = ledger.findByOperationKey("REVERSE:" + rowKey).isPresent();
            BigDecimal taken = row.getDeltaDays().negate();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("operationKey", finalizeKeyOf(rowKey));
            m.put("employeeId", row.getEmployeeId());
            m.put("category", row.getLeaveTypeCode());
            m.put("days", taken);
            m.put("reversed", reversed);
            m.put("heldDays", reversed ? BigDecimal.ZERO : taken);
            out.add(m);
        }
        return out;
    }

    /** {@code COVER:FINALIZE:2026-10:r7:31:CASUAL} → {@code FINALIZE:2026-10:r7}. */
    static String finalizeKeyOf(String coverRowKey) {
        String rest = coverRowKey.startsWith("COVER:") ? coverRowKey.substring("COVER:".length()) : coverRowKey;
        int last = rest.lastIndexOf(':');
        int cut = last > 0 ? rest.lastIndexOf(':', last - 1) : -1;
        return cut > 0 ? rest.substring(0, cut) : rest;
    }

    /**
     * Decision D4 — the same code can exist once per leave period (e.g. a 2026 and a 2027 row). The balance
     * year is that of the row whose period contains the payroll month — the same rule {@link #resolve} used
     * when payroll read the balance — never simply the first row with the code.
     */
    CoverCategory resolveByCode(String code, YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        return categories.findByCodeIgnoreCase(code).stream()
                .filter(c -> c.getPeriodStart() == null || !c.getPeriodStart().isAfter(last))
                .filter(c -> c.getPeriodEnd() == null || !c.getPeriodEnd().isBefore(first))
                .max(Comparator.comparing((LeaveCategory c) -> c.getPeriodStart() == null ? LocalDate.MIN : c.getPeriodStart()))
                .map(c -> new CoverCategory(code, code, c.getPeriodStart() == null ? month.getYear() : c.getPeriodStart().getYear()))
                .orElse(new CoverCategory(code, code, month.getYear()));
    }

    private static String coverKey(String operationKey, Long employeeId, String code) {
        return "COVER:" + operationKey + ":" + employeeId + ":" + code;
    }
}
