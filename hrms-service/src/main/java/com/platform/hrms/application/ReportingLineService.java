package com.platform.hrms.application;

import com.platform.common.security.AuthContext;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.ReportingLineHistory;
import com.platform.hrms.extension.config.TenantConfigLoader;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.ReportingLineHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Config-reusable reporting-line rules: cycle detection, effective-dated history,
 * and resolving who managed whom on a given date (past approvals / scopes stay correct).
 *
 * <p>Tenant knobs live under {@code tenant_settings.reportingLine} (see {@link #loadRules()}).
 */
@Service
public class ReportingLineService {

    private static final int MAX_DEPTH = 64;

    private final EmployeeRepository employeeRepository;
    private final ReportingLineHistoryRepository historyRepository;
    private final TenantConfigLoader configLoader;

    public ReportingLineService(
            EmployeeRepository employeeRepository,
            ReportingLineHistoryRepository historyRepository,
            TenantConfigLoader configLoader) {
        this.employeeRepository = employeeRepository;
        this.historyRepository = historyRepository;
        this.configLoader = configLoader;
    }

    /**
     * True when assigning {@code newManagerId} as manager of {@code employeeId}
     * would make the employee report (directly or indirectly) to themselves.
     */
    @Transactional(readOnly = true)
    public boolean wouldCycle(Long employeeId, Long newManagerId) {
        if (employeeId == null || newManagerId == null) {
            return false;
        }
        if (Objects.equals(employeeId, newManagerId)) {
            return true;
        }
        Long cur = newManagerId;
        int guard = 0;
        while (cur != null && guard++ < MAX_DEPTH) {
            if (Objects.equals(cur, employeeId)) {
                return true;
            }
            cur = employeeRepository.findById(cur).map(Employee::getManagerId).orElse(null);
        }
        return false;
    }

    @Transactional(readOnly = true)
    public void assertNoCycle(Long employeeId, Long newManagerId) {
        if (wouldCycle(employeeId, newManagerId)) {
            String mgrName = employeeRepository.findById(newManagerId)
                    .map(Employee::getFullName)
                    .orElse("that person");
            String empName = employeeRepository.findById(employeeId)
                    .map(Employee::getFullName)
                    .orElse("this employee");
            throw new IllegalStateException(
                    mgrName + " reports to " + empName
                            + " (directly or indirectly), so this would create a reporting loop.");
        }
    }

    /** Manager in effect on {@code onDate}: prefer history, else current {@code manager_id}. */
    @Transactional(readOnly = true)
    public Long managerOn(Long employeeId, LocalDate onDate) {
        if (employeeId == null) {
            return null;
        }
        LocalDate date = onDate == null ? LocalDate.now() : onDate;
        List<ReportingLineHistory> lines = historyRepository.findByEmployeeIdOrderByFromDateDesc(employeeId);
        Optional<ReportingLineHistory> active = lines.stream()
                .filter(h -> h.getFromDate() != null && !h.getFromDate().isAfter(date))
                .filter(h -> h.getToDate() == null || !h.getToDate().isBefore(date))
                .findFirst();
        if (active.isPresent()) {
            return active.get().getManagerId();
        }
        Optional<ReportingLineHistory> earliest = lines.stream()
                .filter(h -> h.getFromDate() != null)
                .min(Comparator.comparing(ReportingLineHistory::getFromDate));
        if (earliest.isPresent() && date.isBefore(earliest.get().getFromDate())) {
            return earliest.get().getManagerId();
        }
        return employeeRepository.findById(employeeId).map(Employee::getManagerId).orElse(null);
    }

    /**
     * Seed the first open history row (create / first manager). No-op when manager is null.
     */
    @Transactional
    public void seedInitial(Long employeeId, Long managerId, LocalDate fromDate) {
        if (employeeId == null || managerId == null) {
            return;
        }
        if (historyRepository.findFirstByEmployeeIdAndToDateIsNullOrderByFromDateDesc(employeeId).isPresent()) {
            return;
        }
        if (!historyRepository.findByEmployeeIdOrderByFromDateDesc(employeeId).isEmpty()) {
            return;
        }
        ReportingLineHistory row = new ReportingLineHistory();
        row.setEmployeeId(employeeId);
        row.setManagerId(managerId);
        row.setFromDate(fromDate == null ? LocalDate.now() : fromDate);
        row.setToDate(null);
        row.setChangedBy(AuthContext.getEmployeeId());
        row.setCreatedAt(Instant.now());
        historyRepository.save(row);
    }

    /**
     * Apply a manager change with an effective date. Updates history so past ranges stay intact.
     * Returns the manager id that should be on {@code employee.manager_id} for "today"
     * (unchanged when the change is scheduled in the future).
     *
     * @param currentManagerId current {@code employee.manager_id} before the change
     * @param nextManagerId    new manager (null clears)
     * @param effectiveFrom    when the new line starts — required when rules.requireEffectiveDate
     */
    @Transactional
    public Long applyManagerChange(
            Long employeeId,
            Long currentManagerId,
            Long nextManagerId,
            LocalDate effectiveFrom) {
        if (employeeId == null) {
            throw new IllegalArgumentException("employeeId is required");
        }
        if (Objects.equals(currentManagerId, nextManagerId)) {
            return currentManagerId;
        }
        if (nextManagerId != null) {
            assertNoCycle(employeeId, nextManagerId);
        }

        ReportingLineRules rules = loadRules();
        LocalDate today = LocalDate.now();
        LocalDate eff = resolveAndValidateEffectiveDate(effectiveFrom, today, rules);

        // Ensure there is an open line for the current manager before rewriting ranges.
        ensureOpenLineForCurrent(employeeId, currentManagerId, eff);

        List<ReportingLineHistory> lines = new ArrayList<>(
                historyRepository.findByEmployeeIdOrderByFromDateDesc(employeeId));

        // Drop any lines that start on/after the new effective date (re-planned future / same-day redo).
        // History strictly before eff is preserved so past approvals/scopes stay correct.
        for (ReportingLineHistory h : lines) {
            if (h.getFromDate() != null && !h.getFromDate().isBefore(eff)) {
                historyRepository.delete(h);
            }
        }
        lines.removeIf(h -> h.getFromDate() != null && !h.getFromDate().isBefore(eff));

        // Close remaining lines that were open through the effective date.
        LocalDate closeTo = eff.minusDays(1);
        for (ReportingLineHistory h : lines) {
            if (h.getToDate() == null || !h.getToDate().isBefore(eff)) {
                if (h.getFromDate() != null && h.getFromDate().isAfter(closeTo)) {
                    historyRepository.delete(h);
                } else {
                    h.setToDate(closeTo);
                    historyRepository.save(h);
                }
            }
        }

        ReportingLineHistory neu = new ReportingLineHistory();
        neu.setEmployeeId(employeeId);
        neu.setManagerId(nextManagerId);
        neu.setFromDate(eff);
        neu.setToDate(null);
        neu.setChangedBy(AuthContext.getEmployeeId());
        neu.setCreatedAt(Instant.now());
        historyRepository.save(neu);

        // Current pointer: only flip when the change is already in effect.
        if (!eff.isAfter(today)) {
            return nextManagerId;
        }
        return currentManagerId;
    }

    /**
     * Align {@code employee.manager_id} with the history line active today
     * (applies due future-dated changes when the date arrives).
     */
    @Transactional
    public void syncCurrentManager(Employee employee) {
        if (employee == null || employee.getId() == null) {
            return;
        }
        Long due = managerOn(employee.getId(), LocalDate.now());
        if (!Objects.equals(due, employee.getManagerId())) {
            employee.setManagerId(due);
            employee.setUpdatedAt(Instant.now());
            employeeRepository.save(employee);
        }
    }

    /** Public for tests / settings UI. */
    public ReportingLineRules loadRules() {
        Map<String, Object> settings = configLoader.loadSettingsMap();
        Object raw = settings.get("reportingLine");
        Map<String, Object> cfg = raw instanceof Map<?, ?> m ? castMap(m) : Map.of();

        boolean requireEffectiveDate = bool(cfg.get("requireEffectiveDate"), true);
        boolean allowPast = bool(cfg.get("allowPastEffectiveDate"), false);
        boolean allowFuture = bool(cfg.get("allowFutureEffectiveDate"), true);
        int maxPastDays = intVal(cfg.get("maxPastDays"), 0);
        int maxFutureDays = intVal(cfg.get("maxFutureDays"), 3650);
        return new ReportingLineRules(requireEffectiveDate, allowPast, allowFuture, maxPastDays, maxFutureDays);
    }

    LocalDate resolveAndValidateEffectiveDate(LocalDate effectiveFrom, LocalDate today, ReportingLineRules rules) {
        if (effectiveFrom == null) {
            if (rules.requireEffectiveDate()) {
                throw new IllegalArgumentException(
                        "managerEffectiveFrom is required when changing the reporting manager.");
            }
            return today;
        }
        if (effectiveFrom.isBefore(LocalDate.of(1970, 1, 1))) {
            throw new IllegalArgumentException("managerEffectiveFrom is invalid");
        }

        long daysFromToday = ChronoUnit.DAYS.between(today, effectiveFrom);

        if (effectiveFrom.isBefore(today)) {
            if (!rules.allowPastEffectiveDate()) {
                throw new IllegalArgumentException(
                        "Manager change effective date cannot be in the past (today is "
                                + today + "). Choose today or a future date so history for past approvals stays correct.");
            }
            long pastDays = ChronoUnit.DAYS.between(effectiveFrom, today);
            if (rules.maxPastDays() >= 0 && pastDays > rules.maxPastDays()) {
                throw new IllegalArgumentException(
                        "Manager change effective date is more than " + rules.maxPastDays()
                                + " day(s) in the past.");
            }
        } else if (effectiveFrom.isAfter(today)) {
            if (!rules.allowFutureEffectiveDate()) {
                throw new IllegalArgumentException(
                        "Manager change effective date cannot be in the future.");
            }
            if (rules.maxFutureDays() >= 0 && daysFromToday > rules.maxFutureDays()) {
                throw new IllegalArgumentException(
                        "Manager change effective date is more than " + rules.maxFutureDays()
                                + " day(s) in the future.");
            }
        }
        return effectiveFrom;
    }

    private void ensureOpenLineForCurrent(Long employeeId, Long currentManagerId, LocalDate beforeEff) {
        if (currentManagerId == null) {
            return;
        }
        List<ReportingLineHistory> lines = historyRepository.findByEmployeeIdOrderByFromDateDesc(employeeId);
        boolean covers = lines.stream().anyMatch(h ->
                h.getFromDate() != null
                        && !h.getFromDate().isAfter(beforeEff.minusDays(1))
                        && (h.getToDate() == null || !h.getToDate().isBefore(beforeEff.minusDays(1))));
        if (covers) {
            return;
        }
        if (historyRepository.findFirstByEmployeeIdAndToDateIsNullOrderByFromDateDesc(employeeId).isPresent()) {
            return;
        }
        Employee emp = employeeRepository.findById(employeeId).orElse(null);
        LocalDate from = emp != null && emp.getStartDate() != null ? emp.getStartDate() : beforeEff.minusDays(1);
        if (!from.isBefore(beforeEff)) {
            from = beforeEff.minusDays(1);
        }
        ReportingLineHistory row = new ReportingLineHistory();
        row.setEmployeeId(employeeId);
        row.setManagerId(currentManagerId);
        row.setFromDate(from);
        row.setToDate(null);
        row.setChangedBy(AuthContext.getEmployeeId());
        row.setCreatedAt(Instant.now());
        historyRepository.save(row);
    }

    private static boolean bool(Object v, boolean def) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return Boolean.parseBoolean(v.toString().trim());
        }
        return def;
    }

    private static int intVal(Object v, int def) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(v.toString().trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private static Map<String, Object> castMap(Map<?, ?> m) {
        java.util.HashMap<String, Object> out = new java.util.HashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    /** Config snapshot for reporting-line manager changes. */
    public record ReportingLineRules(
            boolean requireEffectiveDate,
            boolean allowPastEffectiveDate,
            boolean allowFutureEffectiveDate,
            int maxPastDays,
            int maxFutureDays
    ) {}
}
