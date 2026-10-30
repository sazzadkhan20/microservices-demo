package com.platform.hrms.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.security.AuthContext;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.Holiday;
import com.platform.hrms.domain.LeaveApplication;
import com.platform.hrms.domain.WorkSchedule;
import com.platform.hrms.extension.config.TenantConfigLoader;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.HolidayRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.hrms.infrastructure.persistence.WorkScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FR-SCH-01…06 — config-driven work schedules.
 * Resolve order (default): EMPLOYEE → TEAM → LOCATION.
 * Modes: FIXED (days + office hours) and HOURLY (optional weekly hours).
 */
@Service
public class WorkScheduleService {

    public static final List<String> DEFAULT_MODES = List.of("FIXED", "HOURLY");
    public static final List<String> DEFAULT_SCOPES = List.of("LOCATION", "TEAM", "EMPLOYEE");
    public static final List<String> DEFAULT_RESOLVE = List.of("EMPLOYEE", "TEAM", "LOCATION");

    private final WorkScheduleRepository repository;
    private final EmployeeRepository employeeRepository;
    private final HolidayRepository holidayRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final TenantConfigLoader configLoader;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public WorkScheduleService(
            WorkScheduleRepository repository,
            EmployeeRepository employeeRepository,
            HolidayRepository holidayRepository,
            LeaveApplicationRepository leaveApplicationRepository,
            TenantConfigLoader configLoader,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.holidayRepository = holidayRepository;
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.configLoader = configLoader;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Rules loadRules() {
        Map<String, Object> cfg = map(configLoader.loadSettingsMap().get("scheduleRules"));
        return new Rules(
                list(cfg.get("modes"), DEFAULT_MODES),
                list(cfg.get("scopes"), DEFAULT_SCOPES),
                list(cfg.get("resolveOrder"), DEFAULT_RESOLVE),
                intVal(cfg.get("defaultGraceMinutes"), 15),
                bd(cfg.get("defaultHalfDayHours"), new BigDecimal("4")),
                str(cfg.get("defaultCheckoutCutoff"), "23:59"),
                bool(cfg.get("requireUniqueScope"), true),
                map(cfg.get("patternTemplates"))
        );
    }

    /**
     * FR-EMP-12 + FR-SCH-03 — when an employee’s work pattern is not LOCATION_DEFAULT,
     * upsert an EMPLOYEE-scoped schedule from scheduleRules.patternTemplates so 5-day / 6-day /
     * hourly people coexist under one location/team without manual schedule rows.
     * LOCATION_DEFAULT clears the auto employee override so location/team wins.
     */
    @Transactional
    public void syncFromWorkPattern(Employee emp) {
        if (emp == null || emp.getId() == null) {
            return;
        }
        Rules rules = loadRules();
        String pattern = emp.getWorkPattern() == null ? "" : emp.getWorkPattern().trim().toUpperCase(Locale.ROOT);
        if (pattern.isBlank() || "LOCATION_DEFAULT".equals(pattern)) {
            repository.findByScopeTypeAndScopeId("EMPLOYEE", emp.getId()).ifPresent(existing -> {
                Map<String, Object> before = snapshot(existing);
                repository.delete(existing);
                auditService.record(AuthContext.getEmployeeId(), "DELETE", "WorkSchedule",
                        String.valueOf(existing.getId()), before, Map.of(
                                "reason", "workPattern=LOCATION_DEFAULT",
                                "employeeId", emp.getId()));
            });
            return;
        }

        Map<String, Object> templates = rules.patternTemplates();
        Map<String, Object> tpl = map(templates.get(pattern));
        if (tpl.isEmpty()) {
            // Unknown pattern codes do not invent a schedule; admin sets EMPLOYEE override manually.
            return;
        }

        String mode = normalizeMode(str(tpl.get("mode"), "FIXED"));
        WorkSchedule target = repository.findByScopeTypeAndScopeId("EMPLOYEE", emp.getId())
                .orElseGet(WorkSchedule::new);
        boolean creating = target.getId() == null;
        Map<String, Object> before = creating ? null : snapshot(target);

        target.setScopeType("EMPLOYEE");
        target.setScopeId(emp.getId());
        target.setMode(mode);

        WorkSchedule locationHours = emp.getLocationId() == null ? null
                : repository.findByScopeTypeAndScopeId("LOCATION", emp.getLocationId()).orElse(null);

        if ("HOURLY".equals(mode)) {
            target.setWorkingDays(toJsonDays(List.of()));
            target.setStartTime(null);
            target.setEndTime(null);
            target.setWeeklyHours(bd(tpl.get("weeklyHours"),
                    locationHours != null && locationHours.getWeeklyHours() != null
                            ? locationHours.getWeeklyHours()
                            : new BigDecimal("40")));
            target.setGraceMinutes(0);
            target.setHalfDayHours(null);
            target.setCheckoutCutoff(null);
        } else {
            List<String> days = list(tpl.get("workingDays"), List.of());
            if (days.isEmpty()) {
                throw new IllegalStateException("patternTemplates." + pattern + ".workingDays is required");
            }
            target.setWorkingDays(toJsonDays(days.stream()
                    .map(d -> d.trim().toUpperCase(Locale.ROOT)).toList()));
            boolean inherit = bool(tpl.get("inheritLocationHours"), true);
            if (inherit && locationHours != null
                    && locationHours.getStartTime() != null && locationHours.getEndTime() != null) {
                target.setStartTime(locationHours.getStartTime());
                target.setEndTime(locationHours.getEndTime());
                target.setGraceMinutes(locationHours.getGraceMinutes() != null
                        ? locationHours.getGraceMinutes() : rules.defaultGraceMinutes());
                target.setHalfDayHours(locationHours.getHalfDayHours() != null
                        ? locationHours.getHalfDayHours() : rules.defaultHalfDayHours());
                target.setCheckoutCutoff(locationHours.getCheckoutCutoff() != null
                        ? locationHours.getCheckoutCutoff() : rules.defaultCheckoutCutoff());
            } else {
                target.setStartTime(str(tpl.get("startTime"), "10:00"));
                target.setEndTime(str(tpl.get("endTime"), "18:00"));
                target.setGraceMinutes(intVal(tpl.get("graceMinutes"), rules.defaultGraceMinutes()));
                target.setHalfDayHours(bd(tpl.get("halfDayHours"), rules.defaultHalfDayHours()));
                target.setCheckoutCutoff(str(tpl.get("checkoutCutoff"), rules.defaultCheckoutCutoff()));
            }
            target.setWeeklyHours(null);
        }

        normalizeAndValidate(target, rules, creating);
        WorkSchedule saved = repository.save(target);
        auditService.record(AuthContext.getEmployeeId(),
                creating ? "CREATE" : "UPDATE", "WorkSchedule",
                String.valueOf(saved.getId()), before, snapshot(saved));
    }

    @Transactional(readOnly = true)
    public List<WorkSchedule> listAll() {
        return repository.findAll();
    }

    @Transactional
    public WorkSchedule create(WorkSchedule incoming) {
        Rules rules = loadRules();
        normalizeAndValidate(incoming, rules, true);
        if (rules.requireUniqueScope()) {
            repository.findByScopeTypeAndScopeId(incoming.getScopeType(), incoming.getScopeId())
                    .ifPresent(existing -> {
                        throw new IllegalStateException(
                                "A schedule already exists for " + incoming.getScopeType()
                                        + " #" + incoming.getScopeId()
                                        + " (id=" + existing.getId() + "). Edit that row instead.");
                    });
        }
        applyDefaults(incoming, rules);
        WorkSchedule saved = repository.save(incoming);
        auditService.record(AuthContext.getEmployeeId(), "CREATE", "WorkSchedule",
                String.valueOf(saved.getId()), null, snapshot(saved));
        return saved;
    }

    @Transactional
    public WorkSchedule update(Long id, WorkSchedule patch) {
        Rules rules = loadRules();
        WorkSchedule existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + id));
        Map<String, Object> before = snapshot(existing);

        if (patch.getScopeType() != null) existing.setScopeType(patch.getScopeType());
        if (patch.getScopeId() != null) existing.setScopeId(patch.getScopeId());
        if (patch.getMode() != null) existing.setMode(patch.getMode());
        if (patch.getWorkingDays() != null) existing.setWorkingDays(patch.getWorkingDays());
        if (patch.getStartTime() != null) existing.setStartTime(blankToNull(patch.getStartTime()));
        if (patch.getEndTime() != null) existing.setEndTime(blankToNull(patch.getEndTime()));
        if (patch.getGraceMinutes() != null) existing.setGraceMinutes(patch.getGraceMinutes());
        if (patch.getHalfDayHours() != null) existing.setHalfDayHours(patch.getHalfDayHours());
        if (patch.getCheckoutCutoff() != null) existing.setCheckoutCutoff(blankToNull(patch.getCheckoutCutoff()));
        if (patch.getWeeklyHours() != null) existing.setWeeklyHours(patch.getWeeklyHours());

        normalizeAndValidate(existing, rules, false);
        if (rules.requireUniqueScope()) {
            repository.findByScopeTypeAndScopeId(existing.getScopeType(), existing.getScopeId())
                    .filter(other -> !Objects.equals(other.getId(), existing.getId()))
                    .ifPresent(other -> {
                        throw new IllegalStateException(
                                "A schedule already exists for " + existing.getScopeType()
                                        + " #" + existing.getScopeId());
                    });
        }
        WorkSchedule saved = repository.save(existing);
        auditService.record(AuthContext.getEmployeeId(), "UPDATE", "WorkSchedule",
                String.valueOf(saved.getId()), before, snapshot(saved));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        WorkSchedule existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + id));
        Map<String, Object> before = snapshot(existing);
        repository.delete(existing);
        auditService.record(AuthContext.getEmployeeId(), "DELETE", "WorkSchedule",
                String.valueOf(id), before, null);
    }

    /** FR-SCH-01 — person overrides team overrides location. */
    @Transactional(readOnly = true)
    public Optional<WorkSchedule> resolveForEmployee(Employee emp) {
        if (emp == null) {
            return Optional.empty();
        }
        Rules rules = loadRules();
        for (String scope : rules.resolveOrder()) {
            Long scopeId = switch (scope.toUpperCase(Locale.ROOT)) {
                case "EMPLOYEE" -> emp.getId();
                case "TEAM" -> emp.getTeamId();
                case "LOCATION" -> emp.getLocationId();
                default -> null;
            };
            if (scopeId == null) {
                continue;
            }
            Optional<WorkSchedule> hit = repository.findByScopeTypeAndScopeId(scope.toUpperCase(Locale.ROOT), scopeId);
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<WorkSchedule> resolveForEmployeeId(Long employeeId) {
        return employeeRepository.findById(employeeId).flatMap(this::resolveForEmployee);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resolveView(Long employeeId) {
        Employee emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        Optional<WorkSchedule> resolved = resolveForEmployee(emp);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeId", employeeId);
        out.put("locationId", emp.getLocationId());
        out.put("teamId", emp.getTeamId());
        out.put("workPattern", emp.getWorkPattern());
        if (resolved.isEmpty()) {
            out.put("resolved", null);
            out.put("source", null);
            return out;
        }
        WorkSchedule s = resolved.get();
        out.put("resolved", snapshot(s));
        out.put("source", s.getScopeType());
        out.put("sourceId", s.getScopeId());
        return out;
    }

    /**
     * FR-SCH-05 / FR-SCH-06 — expected hours for one calendar day for an employee.
     * FIXED: end−start on working days; 0 off/holiday/full leave; half on half-day leave.
     * HOURLY: weeklyHours / 7 (pro-rata per calendar day).
     */
    @Transactional(readOnly = true)
    public BigDecimal expectedHoursForDay(Employee emp, LocalDate day) {
        if (emp == null || day == null) {
            return BigDecimal.ZERO;
        }
        Optional<WorkSchedule> opt = resolveForEmployee(emp);
        if (opt.isEmpty()) {
            return BigDecimal.ZERO;
        }
        WorkSchedule schedule = opt.get();
        String mode = normalizeMode(schedule.getMode());

        LeaveImpact leave = leaveImpact(emp.getId(), day);
        if (leave == LeaveImpact.FULL) {
            return BigDecimal.ZERO;
        }

        if ("HOURLY".equals(mode)) {
            BigDecimal weekly = schedule.getWeeklyHours() == null ? BigDecimal.ZERO : schedule.getWeeklyHours();
            BigDecimal daily = weekly.divide(BigDecimal.valueOf(7), 4, RoundingMode.HALF_UP);
            return leave == LeaveImpact.HALF
                    ? daily.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP)
                    : daily;
        }

        // FIXED (and legacy SHIFT/FLEXIBLE)
        if (isHoliday(emp.getLocationId(), day)) {
            return BigDecimal.ZERO;
        }
        if (!isWorkingDay(schedule, day)) {
            return BigDecimal.ZERO;
        }
        BigDecimal full = fixedDayHours(schedule);
        return leave == LeaveImpact.HALF
                ? full.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP)
                : full;
    }

    /** Period expected hours (sum of daily for FIXED; weekly×days/7 for HOURLY). */
    @Transactional(readOnly = true)
    public BigDecimal expectedHoursForPeriod(Employee emp, LocalDate from, LocalDate to) {
        if (emp == null || from == null || to == null || to.isBefore(from)) {
            return BigDecimal.ZERO;
        }
        Optional<WorkSchedule> opt = resolveForEmployee(emp);
        if (opt.isEmpty()) {
            return BigDecimal.ZERO;
        }
        WorkSchedule schedule = opt.get();
        if ("HOURLY".equals(normalizeMode(schedule.getMode()))) {
            BigDecimal weekly = schedule.getWeeklyHours() == null ? BigDecimal.ZERO : schedule.getWeeklyHours();
            long days = ChronoUnit.DAYS.between(from, to) + 1;
            return weekly.multiply(BigDecimal.valueOf(days))
                    .divide(BigDecimal.valueOf(7), 4, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            sum = sum.add(expectedHoursForDay(emp, d));
        }
        return sum.setScale(4, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<String> workingDayCodesForEmployee(Employee emp) {
        return resolveForEmployee(emp)
                .map(this::parseWorkingDays)
                .filter(list -> !list.isEmpty())
                .orElseGet(() -> {
                    var policy = configLoader.loadLeavePolicyOrDefault();
                    return policy.getWorkingDays() == null ? List.of() : policy.getWorkingDays();
                });
    }

    public BigDecimal fixedDayHours(WorkSchedule schedule) {
        if (schedule.getStartTime() == null || schedule.getEndTime() == null
                || schedule.getStartTime().isBlank() || schedule.getEndTime().isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            LocalTime start = LocalTime.parse(schedule.getStartTime().trim());
            LocalTime end = LocalTime.parse(schedule.getEndTime().trim());
            long minutes = Duration.between(start, end).toMinutes();
            if (minutes <= 0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public boolean isWorkingDay(WorkSchedule schedule, LocalDate day) {
        Set<DayOfWeek> days = toDaySet(parseWorkingDays(schedule));
        return days.contains(day.getDayOfWeek());
    }

    /**
     * Scheduled length of one working day in minutes.
     * FIXED schedules use end−start; anything without office hours (including HOURLY) uses 480.
     */
    @Transactional(readOnly = true)
    public long scheduledDayMinutes(Employee emp) {
        if (emp == null) {
            return 480L;
        }
        Optional<WorkSchedule> opt = resolveForEmployee(emp);
        if (opt.isEmpty()) {
            return 480L;
        }
        BigDecimal hours = fixedDayHours(opt.get());
        if (hours == null || hours.signum() <= 0) {
            return 480L;
        }
        return hours.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * Working day on the resolved schedule, excluding holidays and HOURLY (no day grid).
     * Falls back to policy working-day codes when the person has no FIXED schedule days.
     */
    @Transactional(readOnly = true)
    public boolean isScheduledWorkingDay(Employee emp, LocalDate day) {
        if (emp == null || day == null) {
            return false;
        }
        if (isHoliday(emp.getLocationId(), day)) {
            return false;
        }
        Optional<WorkSchedule> opt = resolveForEmployee(emp);
        if (opt.isPresent() && "HOURLY".equals(normalizeMode(opt.get().getMode()))) {
            return false;
        }
        if (opt.isPresent() && !parseWorkingDays(opt.get()).isEmpty()) {
            return isWorkingDay(opt.get(), day);
        }
        List<String> codes = workingDayCodesForEmployee(emp);
        if (codes == null || codes.isEmpty()) {
            return false;
        }
        return toDaySet(codes).contains(day.getDayOfWeek());
    }

    public boolean isHoliday(Long locationId, LocalDate day) {
        if (locationId == null || day == null) {
            return false;
        }
        for (Holiday h : holidayRepository.findAll()) {
            if (!locationId.equals(h.getLocationId())) {
                continue;
            }
            if (h.isOptionalFlag()) {
                continue; // optional holidays do not zero expected hours by default
            }
            if (h.getStartDate() != null && h.getEndDate() != null
                    && !day.isBefore(h.getStartDate()) && !day.isAfter(h.getEndDate())) {
                return true;
            }
        }
        return false;
    }

    private LeaveImpact leaveImpact(Long employeeId, LocalDate day) {
        for (LeaveApplication a : leaveApplicationRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId)) {
            if (!LeaveApplication.STATUS_APPROVED.equalsIgnoreCase(a.getStatus())) {
                continue;
            }
            if (a.getStartDate() == null || a.getEndDate() == null) {
                continue;
            }
            if (day.isBefore(a.getStartDate()) || day.isAfter(a.getEndDate())) {
                continue;
            }
            BigDecimal counted = a.getWorkingDaysCounted() == null ? BigDecimal.ONE : a.getWorkingDaysCounted();
            if (a.getStartDate().equals(a.getEndDate())
                    && counted.compareTo(BigDecimal.ONE) < 0
                    && counted.compareTo(BigDecimal.ZERO) > 0) {
                return LeaveImpact.HALF;
            }
            return LeaveImpact.FULL;
        }
        return LeaveImpact.NONE;
    }

    private void normalizeAndValidate(WorkSchedule s, Rules rules, boolean creating) {
        if (s.getScopeType() == null || s.getScopeType().isBlank()) {
            throw new IllegalArgumentException("scopeType is required (LOCATION, TEAM, or EMPLOYEE)");
        }
        String scope = s.getScopeType().trim().toUpperCase(Locale.ROOT);
        if (rules.scopes().stream().noneMatch(x -> x.equalsIgnoreCase(scope))) {
            throw new IllegalArgumentException("scopeType must be one of: " + String.join(", ", rules.scopes()));
        }
        s.setScopeType(scope);
        if (s.getScopeId() == null || s.getScopeId() <= 0) {
            throw new IllegalArgumentException("scopeId is required");
        }

        String mode = normalizeMode(s.getMode() == null || s.getMode().isBlank() ? "FIXED" : s.getMode());
        boolean modeOk = rules.modes().stream().anyMatch(m -> m.equalsIgnoreCase(mode))
                || List.of("SHIFT", "FLEXIBLE").contains(mode);
        if (!modeOk) {
            throw new IllegalArgumentException("mode must be one of: " + String.join(", ", rules.modes()));
        }
        s.setMode(mode);

        List<String> days = parseWorkingDays(s);
        if ("HOURLY".equals(mode)) {
            // FR-SCH-04 — no fixed days/hours required
            s.setWorkingDays(toJsonDays(List.of()));
            s.setStartTime(null);
            s.setEndTime(null);
            if (s.getWeeklyHours() != null && s.getWeeklyHours().signum() < 0) {
                throw new IllegalArgumentException("weeklyHours must be ≥ 0");
            }
        } else {
            if (days.isEmpty()) {
                throw new IllegalArgumentException("workingDays is required for FIXED schedules (any count of days)");
            }
            s.setWorkingDays(toJsonDays(days));
            if (s.getStartTime() == null || s.getStartTime().isBlank()) {
                throw new IllegalArgumentException("startTime is required for FIXED schedules");
            }
            if (s.getEndTime() == null || s.getEndTime().isBlank()) {
                throw new IllegalArgumentException("endTime is required for FIXED schedules");
            }
            LocalTime start;
            LocalTime end;
            try {
                start = LocalTime.parse(s.getStartTime().trim());
                end = LocalTime.parse(s.getEndTime().trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("startTime/endTime must be HH:mm");
            }
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("endTime must be after startTime");
            }
            s.setStartTime(String.format("%02d:%02d", start.getHour(), start.getMinute()));
            s.setEndTime(String.format("%02d:%02d", end.getHour(), end.getMinute()));
        }

        if (s.getGraceMinutes() != null && (s.getGraceMinutes() < 0 || s.getGraceMinutes() > 240)) {
            throw new IllegalArgumentException("graceMinutes must be between 0 and 240");
        }
        if (s.getHalfDayHours() != null && s.getHalfDayHours().signum() < 0) {
            throw new IllegalArgumentException("halfDayHours must be ≥ 0");
        }
        if (s.getCheckoutCutoff() != null && !s.getCheckoutCutoff().isBlank()) {
            try {
                LocalTime.parse(s.getCheckoutCutoff().trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("checkoutCutoff must be HH:mm");
            }
        }
        if (creating && s.getScopeType() != null) {
            // touch unused to keep creating flag meaningful for future hooks
        }
    }

    private void applyDefaults(WorkSchedule s, Rules rules) {
        if (s.getGraceMinutes() == null) {
            s.setGraceMinutes(rules.defaultGraceMinutes());
        }
        if (s.getHalfDayHours() == null && "FIXED".equals(normalizeMode(s.getMode()))) {
            s.setHalfDayHours(rules.defaultHalfDayHours());
        }
        if ((s.getCheckoutCutoff() == null || s.getCheckoutCutoff().isBlank())
                && "FIXED".equals(normalizeMode(s.getMode()))) {
            s.setCheckoutCutoff(rules.defaultCheckoutCutoff());
        }
        if (s.getWorkingDays() == null) {
            s.setWorkingDays(toJsonDays(List.of()));
        }
    }

    public List<String> parseWorkingDays(WorkSchedule s) {
        return parseWorkingDaysRaw(s == null ? null : s.getWorkingDays());
    }

    private List<String> parseWorkingDaysRaw(Object raw) {
        if (raw == null) {
            return List.of();
        }
        try {
            if (raw instanceof List<?> list) {
                return list.stream().filter(Objects::nonNull).map(o -> String.valueOf(o).trim().toUpperCase(Locale.ROOT))
                        .filter(x -> !x.isBlank()).toList();
            }
            String text = String.valueOf(raw).trim();
            if (text.isEmpty()) {
                return List.of();
            }
            if (text.startsWith("[")) {
                List<String> parsed = objectMapper.readValue(text, new TypeReference<>() {});
                return parsed.stream().map(d -> d.trim().toUpperCase(Locale.ROOT)).toList();
            }
            List<String> out = new ArrayList<>();
            for (String p : text.split("[,\\s]+")) {
                if (!p.isBlank()) {
                    out.add(p.trim().toUpperCase(Locale.ROOT));
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJsonDays(List<String> days) {
        try {
            return objectMapper.writeValueAsString(days == null ? List.of() : days);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Set<DayOfWeek> toDaySet(List<String> codes) {
        Set<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
        for (String code : codes) {
            set.add(parseDow(code));
        }
        return set;
    }

    private static DayOfWeek parseDow(String code) {
        return switch (code.trim().toUpperCase(Locale.ROOT)) {
            case "MON", "MONDAY" -> DayOfWeek.MONDAY;
            case "TUE", "TUESDAY" -> DayOfWeek.TUESDAY;
            case "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY;
            case "THU", "THURSDAY" -> DayOfWeek.THURSDAY;
            case "FRI", "FRIDAY" -> DayOfWeek.FRIDAY;
            case "SAT", "SATURDAY" -> DayOfWeek.SATURDAY;
            case "SUN", "SUNDAY" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Unknown weekday code: " + code);
        };
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "FIXED";
        }
        String m = mode.trim().toUpperCase(Locale.ROOT);
        if ("SHIFT".equals(m) || "FLEXIBLE".equals(m)) {
            return "FIXED";
        }
        return m;
    }

    private Map<String, Object> snapshot(WorkSchedule s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("scopeType", s.getScopeType());
        m.put("scopeId", s.getScopeId());
        m.put("mode", s.getMode());
        m.put("workingDays", parseWorkingDays(s));
        m.put("startTime", s.getStartTime());
        m.put("endTime", s.getEndTime());
        m.put("graceMinutes", s.getGraceMinutes());
        m.put("halfDayHours", s.getHalfDayHours());
        m.put("checkoutCutoff", s.getCheckoutCutoff());
        m.put("weeklyHours", s.getWeeklyHours());
        return m;
    }

    private static Map<String, Object> map(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return Map.of();
        }
        Map<String, Object> out = new HashMap<>();
        m.forEach((k, v) -> {
            if (k != null) {
                out.put(String.valueOf(k), v);
            }
        });
        return out;
    }

    private static List<String> list(Object raw, List<String> def) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return def;
        }
        List<String> out = list.stream()
                .filter(Objects::nonNull)
                .map(o -> String.valueOf(o).trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        return out.isEmpty() ? def : out;
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static int intVal(Object v, int def) {
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try {
                return Integer.parseInt(v.toString().trim());
            } catch (NumberFormatException ignored) { /* def */ }
        }
        return def;
    }

    private static BigDecimal bd(Object v, BigDecimal def) {
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (v != null) {
            try {
                return new BigDecimal(v.toString().trim());
            } catch (Exception ignored) { /* def */ }
        }
        return def;
    }

    private static boolean bool(Object v, boolean def) {
        if (v instanceof Boolean b) return b;
        if (v != null) return Boolean.parseBoolean(v.toString().trim());
        return def;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private enum LeaveImpact { NONE, HALF, FULL }

    public record Rules(
            List<String> modes,
            List<String> scopes,
            List<String> resolveOrder,
            int defaultGraceMinutes,
            BigDecimal defaultHalfDayHours,
            String defaultCheckoutCutoff,
            boolean requireUniqueScope,
            Map<String, Object> patternTemplates
    ) {}
}
