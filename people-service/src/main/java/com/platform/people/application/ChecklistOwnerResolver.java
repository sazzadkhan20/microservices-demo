package com.platform.people.application;

import com.platform.people.domain.Employee;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Resolves checklist item owners.
 * <ul>
 *   <li>{@code HR} — primary active employee with role code {@code HR} (not Admin/SuperAdmin).
 *       Any HR holder may tick; display owner is the primary HR contact.</li>
 *   <li>{@code MANAGER} — subject's manager</li>
 *   <li>{@code EMPLOYEE} — the subject (hire / exit employee)</li>
 *   <li>{@code NAMED} — explicit {@code ownerEmployeeId} on the template item</li>
 * </ul>
 */
@Component
public class ChecklistOwnerResolver {

    private final EmployeeRepository employeeRepository;
    private final RbacService rbacService;

    public ChecklistOwnerResolver(EmployeeRepository employeeRepository, RbacService rbacService) {
        this.employeeRepository = employeeRepository;
        this.rbacService = rbacService;
    }

    public Long resolve(String ownerType, Long namedOwnerId, Employee subject) {
        if (ownerType == null || ownerType.isBlank()) {
            return null;
        }
        String type = ownerType.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "HR" -> primaryHrEmployeeId();
            case "MANAGER" -> subject == null ? null : subject.getManagerId();
            case "EMPLOYEE" -> subject == null ? null : subject.getId();
            case "NAMED" -> namedOwnerId;
            default -> null;
        };
    }

    /** True when the employee holds the dedicated HR role (not Admin / SuperAdmin alone). */
    public boolean holdsHrRole(Long employeeId) {
        if (employeeId == null) {
            return false;
        }
        return rbacService.roleCodesFor(employeeId).stream()
                .anyMatch(c -> c != null && "HR".equalsIgnoreCase(c.trim()));
    }

    /**
     * Primary HR contact for display / assignment: lowest id among active HR holders.
     * Falls back to null when no HR role exists (caller may leave owner unset; manage roles can still tick).
     */
    public Long primaryHrEmployeeId() {
        List<Employee> hrs = new ArrayList<>();
        for (Employee e : employeeRepository.findAll()) {
            if (e == null || e.getId() == null || !isActive(e)) {
                continue;
            }
            if (holdsHrRole(e.getId())) {
                hrs.add(e);
            }
        }
        return hrs.stream()
                .min(Comparator.comparing(Employee::getId))
                .map(Employee::getId)
                .orElse(null);
    }

    public boolean isActive(Employee e) {
        if (e == null) {
            return false;
        }
        if (e.isActive()) {
            String status = e.getStatus() == null ? "" : e.getStatus().trim().toUpperCase(Locale.ROOT);
            return !(status.contains("INACTIVE")
                    || status.contains("TERMINAT")
                    || status.contains("OFFBOARD")
                    || "LEFT".equals(status));
        }
        return false;
    }

    /** True when stored HR owner is missing or is not an actual HR role holder. */
    public boolean needsHrOwnerRepair(Long ownerEmployeeId) {
        if (ownerEmployeeId == null) {
            return true;
        }
        return !holdsHrRole(ownerEmployeeId);
    }

    public boolean sameId(Long a, Long b) {
        return a != null && Objects.equals(a, b);
    }
}
