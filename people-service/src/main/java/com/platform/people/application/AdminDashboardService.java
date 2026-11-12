package com.platform.people.application;

import com.platform.common.security.AuthContext;
import com.platform.people.domain.Employee;
import com.platform.people.domain.Location;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.infrastructure.persistence.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * People slice of the admin dashboard — headcount and contracts ending soon,
 * always limited to the caller's employees:view (or reports:view) report scope.
 */
@Service
public class AdminDashboardService {

    private final EmployeeRepository employeeRepository;
    private final LocationRepository locationRepository;
    private final DataScopeService dataScopeService;

    public AdminDashboardService(
            EmployeeRepository employeeRepository,
            LocationRepository locationRepository,
            DataScopeService dataScopeService) {
        this.employeeRepository = employeeRepository;
        this.locationRepository = locationRepository;
        this.dataScopeService = dataScopeService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> admin(LocalDate date) {
        Long actor = AuthContext.requireEmployeeId();
        LocalDate today = date == null ? LocalDate.now() : date;
        LocalDate horizon = today.plusDays(30);

        DataScopeService.Visibility vis = widestPeopleVisibility(actor);
        Map<Long, String> locationNames = new LinkedHashMap<>();
        for (Location loc : locationRepository.findAll()) {
            if (loc.getId() != null) {
                locationNames.put(loc.getId(), loc.getName() == null ? ("#" + loc.getId()) : loc.getName());
            }
        }

        Map<String, Integer> byLocation = new LinkedHashMap<>();
        Map<String, Integer> byType = new LinkedHashMap<>();
        List<Map<String, Object>> endingSoon = new ArrayList<>();
        int total = 0;
        int active = 0;

        for (Employee e : employeeRepository.findAll()) {
            if (e == null || e.getId() == null || !vis.allows(e.getId())) {
                continue;
            }
            total++;
            if (e.isActive()) {
                active++;
            }
            String locName = e.getLocationId() == null
                    ? "No location"
                    : locationNames.getOrDefault(e.getLocationId(), "#" + e.getLocationId());
            byLocation.merge(locName, 1, Integer::sum);

            String type = normalizeType(e.getEmploymentType());
            byType.merge(type, 1, Integer::sum);

            if (!e.isActive()) {
                continue;
            }
            addEnding(endingSoon, e, e.getProbationEnd(), "PROBATION", "Probation", today, horizon);
            String kind = type.contains("INTERN") ? "INTERNSHIP" : "CONTRACT";
            String label = type.contains("INTERN") ? "Internship" : "Contract";
            if (e.getContractEnd() != null) {
                addEnding(endingSoon, e, e.getContractEnd(), kind, label, today, horizon);
            }
        }

        endingSoon.sort(Comparator
                .comparing((Map<String, Object> m) -> String.valueOf(m.get("endDate")))
                .thenComparing(m -> String.valueOf(m.get("employeeName"))));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", today.toString());
        out.put("scope", vis.scope());
        out.put("headcountTotal", total);
        out.put("headcountActive", active);
        out.put("headcountByLocation", toSortedBars(byLocation));
        out.put("headcountByType", toSortedBars(byType));
        out.put("endingIn30Days", endingSoon);
        out.put("endingIn30DaysCount", endingSoon.size());
        out.put("endingBreakdown", Map.of(
                "probation", endingSoon.stream().filter(m -> "PROBATION".equals(m.get("kind"))).count(),
                "contract", endingSoon.stream().filter(m -> "CONTRACT".equals(m.get("kind"))).count(),
                "internship", endingSoon.stream().filter(m -> "INTERNSHIP".equals(m.get("kind"))).count()
        ));
        return out;
    }

    private DataScopeService.Visibility widestPeopleVisibility(Long actor) {
        DataScopeService.Visibility reports = dataScopeService.visibility(actor, "reports", "view");
        DataScopeService.Visibility employees = dataScopeService.visibility(actor, "employees", "view");
        if (reports.isAll() || (!employees.isAll() && "TEAM".equals(reports.scope())
                && ("OWN".equals(employees.scope()) || "NONE".equals(employees.scope())))) {
            return reports.scope() == null || "NONE".equals(reports.scope()) ? employees : reports;
        }
        if (employees.isAll()) {
            return employees;
        }
        if ("TEAM".equals(employees.scope())) {
            return employees;
        }
        if ("TEAM".equals(reports.scope())) {
            return reports;
        }
        // Prefer any non-NONE scope.
        if (!"NONE".equals(employees.scope())) {
            return employees;
        }
        if (!"NONE".equals(reports.scope())) {
            return reports;
        }
        // Fall back to attendance managers who can still open the dashboard.
        return dataScopeService.visibility(actor, "attendance", "view");
    }

    private static void addEnding(
            List<Map<String, Object>> out,
            Employee e,
            LocalDate end,
            String kind,
            String label,
            LocalDate today,
            LocalDate horizon) {
        if (end == null || end.isBefore(today) || end.isAfter(horizon)) {
            return;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", e.getId());
        m.put("employeeName", e.getFullName());
        m.put("employeeCode", e.getEmployeeCode());
        m.put("kind", kind);
        m.put("label", label);
        m.put("endDate", end.toString());
        m.put("daysLeft", java.time.temporal.ChronoUnit.DAYS.between(today, end));
        m.put("employmentType", e.getEmploymentType());
        out.add(m);
    }

    private static List<Map<String, Object>> toSortedBars(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("label", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .toList();
    }

    private static String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UNSPECIFIED";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
