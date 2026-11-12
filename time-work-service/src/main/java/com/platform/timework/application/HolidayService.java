package com.platform.timework.application;

import com.platform.common.security.AuthContext;
import com.platform.timework.client.people.EmployeeView;
import com.platform.timework.client.people.PeopleEmployeeGateway;
import com.platform.timework.domain.Holiday;
import com.platform.timework.infrastructure.persistence.HolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * FR-HOL — one holiday calendar per location.
 * Types: PUBLIC | COMPANY. {@code optionalFlag} is orthogonal (optional days are still worked/expected).
 * Non-optional holidays zero expected hours, are excluded from leave working-day counts, and are never Absent.
 *
 * <p>Past / ongoing guardrails (location-local "today"):
 * <ul>
 *   <li>Started or past holidays cannot be deleted.</li>
 *   <li>Fully past holidays cannot change dates.</li>
 *   <li>Ongoing holidays keep their start; end may be shortened only to today or later
 *       (future days removed), never before today.</li>
 *   <li>Shortening an ongoing holiday notifies active employees at that location only.</li>
 * </ul>
 */
@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final AuditService auditService;
    private final PeopleEmployeeGateway people;
    private final TimeWorkNotificationHook notificationHook;
    private final Clock clock;

    public HolidayService(
            HolidayRepository holidayRepository,
            AuditService auditService,
            PeopleEmployeeGateway people,
            TimeWorkNotificationHook notificationHook,
            Clock clock) {
        this.holidayRepository = holidayRepository;
        this.auditService = auditService;
        this.people = people;
        this.notificationHook = notificationHook;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional
    public Holiday create(Map<String, Object> body) {
        Holiday h = new Holiday();
        applyBody(h, body, true, null);
        Holiday saved = holidayRepository.save(h);
        auditService.record(AuthContext.getEmployeeId(), "CREATE", "Holiday",
                String.valueOf(saved.getId()), null, snapshot(saved));
        return saved;
    }

    @Transactional
    public Holiday update(Long id, Map<String, Object> body) {
        Holiday h = holidayRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Holiday not found: " + id));
        Map<String, Object> before = snapshot(h);
        LocalDate oldStart = h.getStartDate();
        LocalDate oldEnd = h.getEndDate();
        Long oldLocationId = h.getLocationId();
        LocalDate today = todayFor(oldLocationId);
        boolean wasOngoing = isOngoing(oldStart, oldEnd, today);

        applyBody(h, body, false, today);

        LocalDate newStart = h.getStartDate();
        LocalDate newEnd = h.getEndDate();
        boolean shortened = oldEnd != null && newEnd != null && newEnd.isBefore(oldEnd);

        Holiday saved = holidayRepository.save(h);
        auditService.record(AuthContext.getEmployeeId(), "UPDATE", "Holiday",
                String.valueOf(saved.getId()), before, snapshot(saved));

        // Notify only when an ongoing holiday loses future days — location-scoped.
        if (wasOngoing && shortened && Objects.equals(oldLocationId, saved.getLocationId())
                && Objects.equals(oldStart, newStart)) {
            notificationHook.notifyHolidayShortened(
                    saved.getLocationId(),
                    peopleAtLocation(saved.getLocationId()),
                    saved.getId(),
                    saved.getName(),
                    oldEnd,
                    newEnd);
        }
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Holiday h = holidayRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Holiday not found: " + id));
        LocalDate today = todayFor(h.getLocationId());
        if (!canDelete(h.getStartDate(), today)) {
            throw new IllegalArgumentException(
                    "Cannot delete a holiday that has already started or passed (location date "
                            + today + ")");
        }
        Map<String, Object> before = snapshot(h);
        holidayRepository.delete(h);
        auditService.record(AuthContext.getEmployeeId(), "DELETE", "Holiday",
                String.valueOf(id), before, null);
    }

    /**
     * Copy holidays from {@code fromYear} → {@code toYear} (dates shifted).
     * Skips rows that already exist in the target year (same location + name + dates).
     */
    @Transactional
    public Map<String, Object> copyYear(int fromYear, int toYear, Long locationId) {
        if (fromYear == toYear) {
            throw new IllegalArgumentException("fromYear and toYear must differ");
        }
        long yearDiff = (long) (toYear - fromYear);
        List<Holiday> source = holidayRepository.findAll().stream()
                .filter(h -> overlapsYear(h, fromYear))
                .filter(h -> locationId == null || locationId.equals(h.getLocationId()))
                .toList();

        List<Holiday> created = new ArrayList<>();
        int skipped = 0;
        for (Holiday src : source) {
            LocalDate start = src.getStartDate() == null ? null : src.getStartDate().plusYears(yearDiff);
            LocalDate end = src.getEndDate() == null ? null : src.getEndDate().plusYears(yearDiff);
            if (start == null || end == null) {
                continue;
            }
            if (existsDuplicate(src.getLocationId(), src.getName(), start, end)) {
                skipped++;
                continue;
            }
            Holiday h = new Holiday();
            h.setLocationId(src.getLocationId());
            h.setName(src.getName());
            h.setStartDate(start);
            h.setEndDate(end);
            h.setHolidayType(normalizeType(src.getHolidayType(), false));
            h.setOptionalFlag(src.isOptionalFlag() || isLegacyOptionalType(src.getHolidayType()));
            Holiday saved = holidayRepository.save(h);
            auditService.record(AuthContext.getEmployeeId(), "CREATE", "Holiday",
                    String.valueOf(saved.getId()), null,
                    Map.of("name", saved.getName(),
                            "startDate", String.valueOf(saved.getStartDate()),
                            "copiedFromYear", fromYear,
                            "toYear", toYear));
            created.add(saved);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", created.size());
        out.put("skipped", skipped);
        out.put("created", created);
        return out;
    }

    /**
     * @param today when non-null (updates), enforce past/ongoing date rules against this location-local day
     */
    private void applyBody(Holiday h, Map<String, Object> body, boolean creating, LocalDate today) {
        LocalDate priorStart = h.getStartDate();
        LocalDate priorEnd = h.getEndDate();
        Long priorLocationId = h.getLocationId();

        Object loc = body.get("locationId");
        if (creating) {
            if (loc == null || String.valueOf(loc).isBlank()) {
                throw new IllegalArgumentException("locationId is required");
            }
            h.setLocationId(Long.valueOf(String.valueOf(loc)));
        } else if (loc != null && !String.valueOf(loc).isBlank()) {
            Long nextLoc = Long.valueOf(String.valueOf(loc));
            if (today != null && priorStart != null && !priorStart.isAfter(today)
                    && !Objects.equals(priorLocationId, nextLoc)) {
                throw new IllegalArgumentException(
                        "Cannot move a started or past holiday to another location");
            }
            h.setLocationId(nextLoc);
        }
        if (h.getLocationId() == null || h.getLocationId() <= 0) {
            throw new IllegalArgumentException("locationId is required");
        }

        Object name = body.get("name");
        if (creating || (name != null && !String.valueOf(name).isBlank())) {
            String n = name == null ? "" : String.valueOf(name).trim();
            if (n.length() < 2) {
                throw new IllegalArgumentException("name is required");
            }
            h.setName(n);
        }

        Object startObj = body.get("startDate") != null ? body.get("startDate") : body.get("date");
        Object endObj = body.get("endDate");

        LocalDate nextStart = h.getStartDate();
        LocalDate nextEnd = h.getEndDate();
        boolean touchStart = creating || (startObj != null && !String.valueOf(startObj).isBlank());
        if (touchStart) {
            if (startObj == null || String.valueOf(startObj).isBlank()) {
                throw new IllegalArgumentException("startDate is required");
            }
            nextStart = LocalDate.parse(String.valueOf(startObj));
        }
        if (endObj != null && !String.valueOf(endObj).isBlank()) {
            nextEnd = LocalDate.parse(String.valueOf(endObj));
        } else if (creating || touchStart) {
            // Single-day holiday when end omitted.
            nextEnd = nextStart;
        }
        if (nextStart == null || nextEnd == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (nextEnd.isBefore(nextStart)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }

        if (!creating && today != null && priorStart != null && priorEnd != null) {
            enforceDateEditRules(priorStart, priorEnd, nextStart, nextEnd, today);
        }
        h.setStartDate(nextStart);
        h.setEndDate(nextEnd);

        Object type = body.get("holidayType") != null ? body.get("holidayType") : body.get("type");
        boolean legacyOptional = isLegacyOptionalType(type == null ? h.getHolidayType() : String.valueOf(type));
        if (creating || (type != null && !String.valueOf(type).isBlank())) {
            h.setHolidayType(normalizeType(type == null ? null : String.valueOf(type), true));
        } else if (h.getHolidayType() != null) {
            h.setHolidayType(normalizeType(h.getHolidayType(), false));
        }

        if (body.containsKey("optionalFlag")) {
            h.setOptionalFlag(asBoolean(body.get("optionalFlag")));
        } else if (creating && legacyOptional) {
            h.setOptionalFlag(true);
        }
    }

    /**
     * Past: dates locked. Ongoing: start locked; end may shrink only to today or later.
     * Future: free date edits.
     */
    static void enforceDateEditRules(
            LocalDate priorStart,
            LocalDate priorEnd,
            LocalDate newStart,
            LocalDate newEnd,
            LocalDate today) {
        if (priorEnd.isBefore(today)) {
            if (!Objects.equals(priorStart, newStart) || !Objects.equals(priorEnd, newEnd)) {
                throw new IllegalArgumentException(
                        "Cannot change dates of a holiday that has already ended");
            }
            return;
        }
        if (!priorStart.isAfter(today) && !priorEnd.isBefore(today)) {
            // Ongoing
            if (!Objects.equals(priorStart, newStart)) {
                throw new IllegalArgumentException(
                        "Cannot change the start date of an ongoing holiday");
            }
            if (newEnd.isBefore(today)) {
                throw new IllegalArgumentException(
                        "Ongoing holiday can be shortened only to today or a later date (not before "
                                + today + ")");
            }
            return;
        }
        // Future — no extra date constraints
    }

    static boolean canDelete(LocalDate startDate, LocalDate today) {
        return startDate != null && today != null && startDate.isAfter(today);
    }

    static boolean isOngoing(LocalDate start, LocalDate end, LocalDate today) {
        return start != null && end != null && today != null
                && !start.isAfter(today) && !end.isBefore(today);
    }

    LocalDate todayFor(Long locationId) {
        return LocalDate.now(clock.withZone(zoneForLocation(locationId)));
    }

    private ZoneId zoneForLocation(Long locationId) {
        if (locationId == null) {
            return ZoneId.of("UTC");
        }
        try {
            for (EmployeeView e : people.findAll()) {
                if (e == null || !e.isActive() || !locationId.equals(e.getLocationId())) {
                    continue;
                }
                String tz = e.getLocationTimeZone();
                if (tz != null && !tz.isBlank()) {
                    return ZoneId.of(tz.trim());
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return ZoneId.of("UTC");
    }

    private List<EmployeeView> peopleAtLocation(Long locationId) {
        if (locationId == null) {
            return List.of();
        }
        try {
            return people.findAll().stream()
                    .filter(Objects::nonNull)
                    .filter(EmployeeView::isActive)
                    .filter(e -> locationId.equals(e.getLocationId()))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean existsDuplicate(Long locationId, String name, LocalDate start, LocalDate end) {
        return holidayRepository.findAll().stream().anyMatch(e ->
                Objects.equals(e.getLocationId(), locationId)
                        && Objects.equals(e.getName(), name)
                        && Objects.equals(e.getStartDate(), start)
                        && Objects.equals(e.getEndDate(), end));
    }

    private static boolean overlapsYear(Holiday h, int year) {
        if (h.getStartDate() != null && h.getStartDate().getYear() == year) return true;
        return h.getEndDate() != null && h.getEndDate().getYear() == year;
    }

    static String normalizeType(String type, boolean strict) {
        if (type == null || type.isBlank()) {
            if (strict) {
                throw new IllegalArgumentException("holidayType must be PUBLIC or COMPANY");
            }
            return "PUBLIC";
        }
        String t = type.trim().toUpperCase(Locale.ROOT);
        if ("PUBLIC".equals(t) || "COMPANY".equals(t)) {
            return t;
        }
        // Create/update: only Public|Company accepted.
        if (strict) {
            throw new IllegalArgumentException("holidayType must be PUBLIC or COMPANY");
        }
        // Soft map for copy / existing legacy rows.
        if ("NATIONAL".equals(t)) {
            return "PUBLIC";
        }
        if ("OPTIONAL".equals(t) || "RELIGIOUS".equals(t)) {
            return "COMPANY";
        }
        return "PUBLIC";
    }

    private static boolean isLegacyOptionalType(Object type) {
        return type != null && "OPTIONAL".equalsIgnoreCase(String.valueOf(type).trim());
    }

    private static boolean asBoolean(Object v) {
        if (v instanceof Boolean b) return b;
        if (v == null) return false;
        String s = String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
    }

    private static Map<String, Object> snapshot(Holiday h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("locationId", h.getLocationId());
        m.put("name", h.getName());
        m.put("startDate", String.valueOf(h.getStartDate()));
        m.put("endDate", String.valueOf(h.getEndDate()));
        m.put("holidayType", h.getHolidayType());
        m.put("optionalFlag", h.isOptionalFlag());
        return m;
    }
}
