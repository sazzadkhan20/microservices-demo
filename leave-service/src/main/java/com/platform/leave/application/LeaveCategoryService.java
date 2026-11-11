package com.platform.leave.application;

import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.domain.LeaveCategory;
import com.platform.leave.infrastructure.persistence.LeaveCategoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Period-aware leave category resolution.
 * Same code may exist for multiple non-overlapping periods (renew keeps name+code).
 */
@Service
public class LeaveCategoryService {

    private final LeaveCategoryRepository repository;

    public LeaveCategoryService(LeaveCategoryRepository repository) {
        this.repository = repository;
    }

    public List<LeaveCategory> findAll() {
        return repository.findAll();
    }

    public List<LeaveCategory> findAllByCode(String code) {
        if (code == null || code.isBlank()) return List.of();
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return findAll().stream()
                .filter(c -> c.getCode() != null && normalized.equalsIgnoreCase(c.getCode().trim()))
                .sorted(Comparator
                        .comparing(LeaveCategory::getPeriodStart, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LeaveCategory::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Active category whose period covers {@code on} (inclusive). */
    public Optional<LeaveCategory> resolveForCodeOn(String code, LocalDate on) {
        LocalDate day = on == null ? LocalDate.now() : on;
        return findAllByCode(code).stream()
                .filter(LeaveCategory::isActive)
                .filter(c -> covers(c, day))
                .findFirst();
    }

    public boolean covers(LeaveCategory c, LocalDate on) {
        if (c == null || on == null) return false;
        if (c.getPeriodStart() != null && on.isBefore(c.getPeriodStart())) return false;
        if (c.getPeriodEnd() != null && on.isAfter(c.getPeriodEnd())) return false;
        // Null period = open-ended current catalogue row (legacy).
        return true;
    }

    public boolean isCurrentPeriod(LeaveCategory c) {
        return covers(c, LocalDate.now());
    }

    public int balanceYear(LeaveCategory c) {
        if (c != null && c.getPeriodStart() != null) {
            return c.getPeriodStart().getYear();
        }
        return LocalDate.now().getYear();
    }

    public void requirePeriodDates(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("periodStart and periodEnd are required");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("periodEnd must be on/after periodStart");
        }
    }

    /** Reject overlapping periods for the same code (excludeId for updates). */
    public void assertNoPeriodOverlap(String code, LocalDate start, LocalDate end, Long excludeId) {
        requirePeriodDates(start, end);
        for (LeaveCategory existing : findAllByCode(code)) {
            if (excludeId != null && excludeId.equals(existing.getId())) continue;
            if (existing.getPeriodStart() == null && existing.getPeriodEnd() == null) continue;
            LocalDate es = existing.getPeriodStart() != null ? existing.getPeriodStart() : LocalDate.MIN;
            LocalDate ee = existing.getPeriodEnd() != null ? existing.getPeriodEnd() : LocalDate.MAX;
            boolean overlap = !start.isAfter(ee) && !end.isBefore(es);
            if (overlap) {
                throw new IllegalStateException(
                        "Leave category " + code + " already has a period overlapping "
                                + start + "–" + end
                                + (existing.getPeriodStart() != null
                                ? " (existing " + existing.getPeriodStart() + "–" + existing.getPeriodEnd() + ")"
                                : ""));
            }
        }
    }

    /**
     * Renew target must be next calendar year only (relative to today), and only once per code.
     */
    public void assertRenewAllowed(LeaveCategory source, LocalDate periodStart, LocalDate periodEnd) {
        requirePeriodDates(periodStart, periodEnd);
        int nextYear = LocalDate.now().getYear() + 1;
        if (periodStart.getYear() != nextYear) {
            throw new IllegalArgumentException(
                    "Renew period must start in next calendar year (" + nextYear + ")");
        }
        if (periodEnd.getYear() != nextYear) {
            throw new IllegalArgumentException(
                    "Renew period must end in next calendar year (" + nextYear + ")");
        }
        String code = source.getCode();
        boolean already = findAllByCode(code).stream()
                .filter(c -> !c.getId().equals(source.getId()))
                .anyMatch(c -> c.getPeriodStart() != null && c.getPeriodStart().getYear() == nextYear);
        if (already) {
            throw new IllegalStateException(
                    "Category " + code + " is already renewed for " + nextYear);
        }
        assertNoPeriodOverlap(code, periodStart, periodEnd, source.getId());
    }

    /**
     * Pro-rata annual quota from join date within the category period.
     * Full quota when proRata is off, or when the employee started on/before period start.
     */
    public BigDecimal effectiveQuota(LeaveCategory category, EmployeeView employee) {
        if (category == null || category.getQuotaDays() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal quota = LeaveDays.of(category.getQuotaDays());
        if (!category.isProRata() || employee == null) {
            return quota;
        }
        LocalDate periodStart = category.getPeriodStart() != null
                ? category.getPeriodStart()
                : LocalDate.of(LocalDate.now().getYear(), 1, 1);
        LocalDate periodEnd = category.getPeriodEnd() != null
                ? category.getPeriodEnd()
                : LocalDate.of(LocalDate.now().getYear(), 12, 31);
        LocalDate join = employee.getStartDate() != null ? employee.getStartDate() : periodStart;
        if (!join.isAfter(periodStart)) {
            return quota;
        }
        if (join.isAfter(periodEnd)) {
            return BigDecimal.ZERO;
        }
        long totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        long remaining = ChronoUnit.DAYS.between(join, periodEnd) + 1;
        if (totalDays <= 0) {
            return LeaveDays.of(quota);
        }
        // Exact fraction, then sell to nearest half-day (same rule as balances / UI).
        BigDecimal raw = quota.multiply(BigDecimal.valueOf(remaining))
                .divide(BigDecimal.valueOf(totalDays), 6, RoundingMode.HALF_UP);
        return LeaveDays.of(raw);
    }
}
