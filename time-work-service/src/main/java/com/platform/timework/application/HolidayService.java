package com.platform.timework.application;

import com.platform.common.security.AuthContext;
import com.platform.timework.domain.Holiday;
import com.platform.timework.infrastructure.persistence.HolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
 */
@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final AuditService auditService;

    public HolidayService(HolidayRepository holidayRepository, AuditService auditService) {
        this.holidayRepository = holidayRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Holiday create(Map<String, Object> body) {
        Holiday h = new Holiday();
        applyBody(h, body, true);
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
        applyBody(h, body, false);
        Holiday saved = holidayRepository.save(h);
        auditService.record(AuthContext.getEmployeeId(), "UPDATE", "Holiday",
                String.valueOf(saved.getId()), before, snapshot(saved));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Holiday h = holidayRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Holiday not found: " + id));
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

    private void applyBody(Holiday h, Map<String, Object> body, boolean creating) {
        Object loc = body.get("locationId");
        if (creating) {
            if (loc == null || String.valueOf(loc).isBlank()) {
                throw new IllegalArgumentException("locationId is required");
            }
            h.setLocationId(Long.valueOf(String.valueOf(loc)));
        } else if (loc != null && !String.valueOf(loc).isBlank()) {
            h.setLocationId(Long.valueOf(String.valueOf(loc)));
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
        if (creating || (startObj != null && !String.valueOf(startObj).isBlank())) {
            if (startObj == null || String.valueOf(startObj).isBlank()) {
                throw new IllegalArgumentException("startDate is required");
            }
            h.setStartDate(LocalDate.parse(String.valueOf(startObj)));
        }

        Object endObj = body.get("endDate");
        if (endObj != null && !String.valueOf(endObj).isBlank()) {
            h.setEndDate(LocalDate.parse(String.valueOf(endObj)));
        } else if (creating || startObj != null) {
            // Single-day holiday when end omitted.
            h.setEndDate(h.getStartDate());
        }
        if (h.getStartDate() == null || h.getEndDate() == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (h.getEndDate().isBefore(h.getStartDate())) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }

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
