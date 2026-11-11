package com.platform.leave.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.leave.client.people.PeopleEmployeeGateway;
import com.platform.leave.client.timework.TimeWorkScheduleGateway;
import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.domain.LeaveCategory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FR-LV-01 — who a leave category applies to.
 * {@code applies_to}: locations / employmentTypes / teams / employeeIds.
 * Empty (or all empty lists) = everyone except <b>hourly-schedule</b> staff (FR-LV-03).
 * <p>Gate is the resolved work schedule mode (EMPLOYEE → TEAM → LOCATION), not payroll pay type.
 * A day-based (FIXED) schedule may still take leave even when salary pay type is HOURLY.
 */
@Service
public class LeaveAppliesToService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TimeWorkScheduleGateway workScheduleService;
    private final PeopleEmployeeGateway employeeRepository;

    public LeaveAppliesToService(
            TimeWorkScheduleGateway workScheduleService,
            PeopleEmployeeGateway employeeRepository) {
        this.workScheduleService = workScheduleService;
        this.employeeRepository = employeeRepository;
    }

    /**
     * FR-LV-03 — no day-based leave when the person's <em>work schedule</em> is HOURLY.
     * Includes LOCATION_DEFAULT when the location (or team) schedule resolves to HOURLY.
     * Does <b>not</b> use employment type or payroll pay type.
     */
    public boolean isHourly(EmployeeView employee) {
        return isHourlySchedule(employee);
    }

    public boolean isHourlySchedule(EmployeeView employee) {
        if (employee == null) {
            return false;
        }
        // Explicit HOURLY work pattern (syncs a personal HOURLY schedule).
        if (employee.getWorkPattern() != null
                && "HOURLY".equalsIgnoreCase(employee.getWorkPattern().trim())) {
            return true;
        }
        // Resolved schedule: EMPLOYEE → TEAM → LOCATION (location default covers the location).
        return workScheduleService.resolveForEmployee(employee)
                .map(s -> s.getMode() != null && "HOURLY".equalsIgnoreCase(s.getMode().trim()))
                .orElse(false);
    }

    /** Resolved schedule mode for APIs / FE (HOURLY, FIXED, or empty when unknown). */
    public String resolvedScheduleMode(EmployeeView employee) {
        if (employee == null) {
            return "";
        }
        if (employee.getWorkPattern() != null
                && "HOURLY".equalsIgnoreCase(employee.getWorkPattern().trim())) {
            return "HOURLY";
        }
        return workScheduleService.resolveForEmployee(employee)
                .map(s -> s.getMode() == null ? "" : s.getMode().trim().toUpperCase(Locale.ROOT))
                .orElse("");
    }

    public boolean isEligible(EmployeeView employee, LeaveCategory category) {
        if (employee == null || category == null || !category.isActive()) {
            return false;
        }
        // Hourly-schedule staff never receive leave categories (FR-LV-03).
        if (isHourlySchedule(employee)) {
            return false;
        }
        return matchesAppliesTo(employee, normalize(category.getAppliesTo()));
    }

    public void requireEligible(EmployeeView employee, LeaveCategory category) {
        if (isHourlySchedule(employee)) {
            throw new IllegalStateException(
                    "Hourly-schedule employees cannot request leave (FR-LV-03)");
        }
        if (category == null) {
            return;
        }
        Map<String, Object> applies = normalize(category.getAppliesTo());
        if (!matchesAppliesTo(employee, applies)) {
            throw new IllegalStateException(
                    "Employee is not eligible for category " + category.getCode()
                            + " (applies_to restriction)");
        }
    }

    /**
     * Normalize incoming payload (Map, JSON string, or null) into a stable bag.
     * Always includes empty lists for the four dimensions.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> normalize(Object raw) {
        Map<String, Object> src = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> m) {
            m.forEach((k, v) -> src.put(String.valueOf(k), v));
        } else if (raw instanceof String s && !s.isBlank()) {
            try {
                Object parsed = MAPPER.readValue(s, new TypeReference<Map<String, Object>>() {});
                if (parsed instanceof Map<?, ?> m) {
                    m.forEach((k, v) -> src.put(String.valueOf(k), v));
                }
            } catch (Exception ignored) {
                // treat as unrestricted
            }
        }
        List<Long> namedPeople = toIdList(src.get("employeeIds")).stream()
                .filter(id -> employeeRepository.findById(id).map(e -> !isHourly(e)).orElse(true))
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("locations", toIdList(src.get("locations")));
        out.put("employmentTypes", toStringList(src.get("employmentTypes")));
        out.put("teams", toIdList(src.get("teams")));
        out.put("employeeIds", namedPeople);
        // Product rule: hourly-schedule staff never get leave — keep explicit for clients/tools.
        out.put("blockHourlySchedule", true);
        out.put("blockHourly", true); // legacy alias
        return out;
    }

    public boolean isUnrestricted(Map<String, Object> applies) {
        if (applies == null || applies.isEmpty()) return true;
        return listOf(applies.get("locations")).isEmpty()
                && listOf(applies.get("employmentTypes")).isEmpty()
                && listOf(applies.get("teams")).isEmpty()
                && listOf(applies.get("employeeIds")).isEmpty();
    }

    /** Match if unrestricted, or employee hits at least one configured dimension (union). */
    private boolean matchesAppliesTo(EmployeeView employee, Map<String, Object> applies) {
        if (isUnrestricted(applies)) {
            return true;
        }
        boolean matched = false;
        List<?> locations = listOf(applies.get("locations"));
        List<?> employmentTypes = listOf(applies.get("employmentTypes"));
        List<?> teams = listOf(applies.get("teams"));
        List<?> employeeIds = listOf(applies.get("employeeIds"));

        if (!locations.isEmpty() && employee.getLocationId() != null) {
            matched |= locations.stream().anyMatch(v -> String.valueOf(v).equals(String.valueOf(employee.getLocationId())));
        }
        if (!employmentTypes.isEmpty() && employee.getEmploymentType() != null) {
            matched |= employmentTypes.stream().anyMatch(v ->
                    String.valueOf(v).equalsIgnoreCase(employee.getEmploymentType().trim()));
        }
        if (!teams.isEmpty() && employee.getTeamId() != null) {
            matched |= teams.stream().anyMatch(v -> String.valueOf(v).equals(String.valueOf(employee.getTeamId())));
        }
        if (!employeeIds.isEmpty() && employee.getId() != null) {
            matched |= employeeIds.stream().anyMatch(v -> String.valueOf(v).equals(String.valueOf(employee.getId())));
        }
        return matched;
    }

    private static List<?> listOf(Object val) {
        if (val instanceof List<?> l) return l;
        return List.of();
    }

    private static List<Long> toIdList(Object val) {
        List<Long> out = new ArrayList<>();
        if (!(val instanceof Collection<?> c)) return out;
        for (Object o : c) {
            if (o == null || String.valueOf(o).isBlank()) continue;
            try {
                out.add(Long.valueOf(String.valueOf(o).trim()));
            } catch (NumberFormatException ignored) {
                /* skip */
            }
        }
        return out;
    }

    private static List<String> toStringList(Object val) {
        List<String> out = new ArrayList<>();
        if (!(val instanceof Collection<?> c)) return out;
        for (Object o : c) {
            if (o == null) continue;
            String s = String.valueOf(o).trim().toUpperCase(Locale.ROOT);
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }
}
