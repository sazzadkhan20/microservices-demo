package com.platform.hrms.application;

import com.platform.hrms.domain.Employee;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns an RBAC data scope (OWN / TEAM / ALL) into "may this actor touch that employee's data?".
 *
 * <ul>
 *   <li>{@code ALL}  – every employee in the tenant;</li>
 *   <li>{@code TEAM} – the actor plus all descendants in the reporting tree
 *       (direct and indirect reports via {@code manager_id});</li>
 *   <li>{@code OWN}  – only the actor;</li>
 *   <li>{@code NONE} – nobody (permission not granted).</li>
 * </ul>
 */
@Service
public class DataScopeService {

    private final RbacService rbacService;
    private final EmployeeRepository employeeRepository;

    public DataScopeService(RbacService rbacService, EmployeeRepository employeeRepository) {
        this.rbacService = rbacService;
        this.employeeRepository = employeeRepository;
    }

    /** Resolve once per request, then call {@link Visibility#allows(Long)} per row. */
    @Transactional(readOnly = true)
    public Visibility visibility(Long actorId, String feature, String action) {
        String scope = rbacService.widestScope(actorId, feature, action);
        Set<Long> team = Set.of();
        if ("TEAM".equals(scope)) {
            team = subtreeIncludingSelf(actorId);
        }
        return new Visibility(scope, actorId, team);
    }

    @Transactional(readOnly = true)
    public boolean canAccess(Long actorId, Long targetEmployeeId, String feature, String action) {
        return visibility(actorId, feature, action).allows(targetEmployeeId);
    }

    /** @throws SecurityException (mapped to HTTP 403) when the target is outside the actor's scope. */
    @Transactional(readOnly = true)
    public void requireAccess(Long actorId, Long targetEmployeeId, String feature, String action) {
        if (!canAccess(actorId, targetEmployeeId, feature, action)) {
            throw new SecurityException("Forbidden: " + feature + ":" + action
                    + " does not cover employee " + targetEmployeeId);
        }
    }

    /** Actor + all descendants following manager→report edges. */
    private Set<Long> subtreeIncludingSelf(Long root) {
        Set<Long> out = new HashSet<>();
        out.add(root);
        Map<Long, List<Long>> kids = new HashMap<>();
        for (Employee e : employeeRepository.findAll()) {
            if (e.getManagerId() != null) {
                kids.computeIfAbsent(e.getManagerId(), k -> new ArrayList<>()).add(e.getId());
            }
        }
        ArrayList<Long> stack = new ArrayList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            Long id = stack.remove(stack.size() - 1);
            for (Long c : kids.getOrDefault(id, List.of())) {
                if (out.add(c)) {
                    stack.add(c);
                }
            }
        }
        return Set.copyOf(out);
    }

    public record Visibility(String scope, Long actorId, Set<Long> teamIds) {

        public boolean allows(Long targetEmployeeId) {
            if (targetEmployeeId == null) {
                return false;
            }
            return switch (scope) {
                case "ALL" -> true;
                case "TEAM" -> teamIds.contains(targetEmployeeId);
                case "OWN" -> actorId.equals(targetEmployeeId);
                default -> false;
            };
        }

        public boolean isAll() {
            return "ALL".equals(scope);
        }
    }
}
