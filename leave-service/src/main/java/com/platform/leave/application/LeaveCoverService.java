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
 * never deduct or restore twice.
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
     * Applies all covers of one payroll finalisation atomically (REQUIRES_NEW so a business rejection
     * rolls back only the ledger work; the caller still records the REJECTED result).
     *
     * @return per-employee problems; empty when everything was applied.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> applyFinalize(String operationKey, String period, List<Map<String, Object>> lines) {
        YearMonth month = YearMonth.parse(period);
        List<String> problems = new ArrayList<>();
        record Item(Long employeeId, CoverCategory cat, BigDecimal days) { }
        List<Item> items = new ArrayList<>();
        for (Map<String, Object> line : lines) {
            Long emp = Long.valueOf(String.valueOf(line.get("employeeId")));
            Object raw = line.get("covers");
            if (!(raw instanceof List<?> covers)) {
                continue;
            }
            for (Object o : covers) {
                if (!(o instanceof Map<?, ?> c)) {
                    continue;
                }
                String code = String.valueOf(c.get("category")).toUpperCase(Locale.ROOT);
                BigDecimal days = new BigDecimal(String.valueOf(c.get("days")));
                if (days.signum() <= 0) {
                    continue;
                }
                CoverCategory cat = resolveByCode(code, month);
                String key = coverKey(operationKey, emp, code);
                if (ledger.findByOperationKey(key).isPresent()) {
                    continue; // already applied by an earlier delivery
                }
                BigDecimal usable = usable(emp, cat);
                if (usable.compareTo(days) < 0) {
                    problems.add("Employee " + emp + ": " + code + " has " + usable.stripTrailingZeros().toPlainString()
                            + " day(s) left but payroll covered " + days.stripTrailingZeros().toPlainString()
                            + " — recalculate " + period);
                }
                items.add(new Item(emp, cat, days));
            }
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

    /** Reverses exactly the rows recorded for the finalise key (restores once, even if re-delivered). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    private CoverCategory resolveByCode(String code, YearMonth month) {
        return categories.findByCodeIgnoreCase(code).stream()
                .findFirst()
                .map(c -> new CoverCategory(code, code, c.getPeriodStart() == null ? month.getYear() : c.getPeriodStart().getYear()))
                .orElse(new CoverCategory(code, code, month.getYear()));
    }

    private static String coverKey(String operationKey, Long employeeId, String code) {
        return "COVER:" + operationKey + ":" + employeeId + ":" + code;
    }
}
