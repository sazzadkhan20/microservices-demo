package com.platform.hrms.application;

import com.platform.common.security.AuthContext;
import com.platform.hrms.domain.Department;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.Location;
import com.platform.hrms.infrastructure.persistence.DepartmentRepository;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds an org chart from reporting lines (effective manager on a given date).
 * Config-reusable: any tenant with {@code employee.manager_id} / reporting-line history.
 */
@Service
public class OrgChartService {

    private static final Set<String> INACTIVE = Set.of("inactive", "resigned", "terminated");

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final ReportingLineService reportingLineService;
    private final RbacService rbacService;
    private final DataScopeService dataScopeService;

    public OrgChartService(
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            LocationRepository locationRepository,
            ReportingLineService reportingLineService,
            RbacService rbacService,
            DataScopeService dataScopeService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.locationRepository = locationRepository;
        this.reportingLineService = reportingLineService;
        this.rbacService = rbacService;
        this.dataScopeService = dataScopeService;
    }

    /**
     * Nested org-chart roots drawn from reporting lines as of {@code asOf} (default today).
     * Scope: full tenant when actor has ALL on employees:view or org:view; otherwise the
     * actor's TEAM subtree (self + reports via reporting lines).
     */
    @Transactional
    public List<Map<String, Object>> chart(LocalDate asOf, Long departmentId, boolean includeInactive) {
        Long actorId = AuthContext.requireEmployeeId();
        if (!rbacService.hasPermission(actorId, "employees", "view")
                && !rbacService.hasPermission(actorId, "org", "view")
                && !rbacService.hasPermission(actorId, "hierarchy", "view")) {
            throw new SecurityException("Forbidden: org chart requires employees:view or org:view");
        }

        LocalDate on = asOf == null ? LocalDate.now() : asOf;
        boolean asOfToday = on.equals(LocalDate.now());

        Map<Long, String> deptNames = departmentRepository.findAll().stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
        Map<Long, String> locNames = locationRepository.findAll().stream()
                .collect(Collectors.toMap(Location::getId, Location::getName, (a, b) -> a));

        List<Employee> all = employeeRepository.findAll();
        Map<Long, Long> managerById = new HashMap<>();
        Map<Long, Employee> byId = new HashMap<>();

        for (Employee e : all) {
            if (e.getId() == null) {
                continue;
            }
            if (!includeInactive && isInactive(e)) {
                continue;
            }
            if (departmentId != null && !departmentId.equals(e.getDepartmentId())) {
                continue;
            }
            byId.put(e.getId(), e);
            Long mgr;
            if (asOfToday) {
                reportingLineService.syncCurrentManager(e);
                mgr = e.getManagerId();
            } else {
                mgr = reportingLineService.managerOn(e.getId(), on);
            }
            managerById.put(e.getId(), mgr);
        }

        // Scope filter: ALL → everyone in byId; else TEAM visibility + descendants in chart
        DataScopeService.Visibility vis = dataScopeService.visibility(actorId, "employees", "view");
        if (!vis.isAll() && !rbacService.hasPermission(actorId, "org", "view")) {
            Set<Long> allowed = subtreeFrom(actorId, managerById);
            allowed.add(actorId);
            // Also include anyone DataScope TEAM already allows (direct reports)
            byId.keySet().removeIf(id -> !allowed.contains(id) && !vis.allows(id));
            managerById.keySet().removeIf(id -> !byId.containsKey(id));
        }

        Map<Long, List<Long>> children = new HashMap<>();
        List<Long> roots = new ArrayList<>();
        for (Long id : byId.keySet()) {
            Long mgr = managerById.get(id);
            if (mgr != null && byId.containsKey(mgr) && !Objects.equals(mgr, id)) {
                children.computeIfAbsent(mgr, k -> new ArrayList<>()).add(id);
            } else {
                roots.add(id);
            }
        }

        Comparator<Long> byName = Comparator.comparing(
                id -> {
                    Employee e = byId.get(id);
                    return e == null || e.getFullName() == null ? "" : e.getFullName().toLowerCase(Locale.ROOT);
                });
        roots.sort(byName);
        for (List<Long> kids : children.values()) {
            kids.sort(byName);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        Set<Long> visiting = new HashSet<>();
        for (Long rootId : roots) {
            Map<String, Object> node = buildNode(rootId, byId, children, managerById, deptNames, locNames, visiting, 0);
            if (node != null) {
                out.add(node);
            }
        }
        return out;
    }

    /** All descendants of {@code root} following manager→report edges (inverted). */
    private Set<Long> subtreeFrom(Long root, Map<Long, Long> managerById) {
        Set<Long> out = new HashSet<>();
        Map<Long, List<Long>> kids = new HashMap<>();
        for (Map.Entry<Long, Long> e : managerById.entrySet()) {
            if (e.getValue() != null) {
                kids.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
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
        return out;
    }

    private Map<String, Object> buildNode(
            Long id,
            Map<Long, Employee> byId,
            Map<Long, List<Long>> children,
            Map<Long, Long> managerById,
            Map<Long, String> deptNames,
            Map<Long, String> locNames,
            Set<Long> visiting,
            int depth) {
        if (depth > 64 || !byId.containsKey(id) || !visiting.add(id)) {
            return null;
        }
        Employee e = byId.get(id);
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", e.getId());
        node.put("employeeCode", e.getEmployeeCode());
        node.put("fullName", e.getFullName());
        node.put("jobTitle", e.getJobTitle());
        node.put("status", e.getStatus());
        node.put("managerId", managerById.get(id));
        node.put("departmentId", e.getDepartmentId());
        node.put("departmentName", e.getDepartmentId() == null ? null : deptNames.get(e.getDepartmentId()));
        node.put("locationId", e.getLocationId());
        node.put("locationName", e.getLocationId() == null ? null : locNames.get(e.getLocationId()));
        List<Map<String, Object>> childNodes = new ArrayList<>();
        for (Long cid : children.getOrDefault(id, List.of())) {
            Map<String, Object> c = buildNode(cid, byId, children, managerById, deptNames, locNames, visiting, depth + 1);
            if (c != null) {
                childNodes.add(c);
            }
        }
        node.put("children", childNodes);
        visiting.remove(id);
        return node;
    }

    private static boolean isInactive(Employee e) {
        if (e == null) {
            return true;
        }
        if (!e.isActive()) {
            return true;
        }
        String st = e.getStatus() == null ? "" : e.getStatus().trim().toLowerCase(Locale.ROOT);
        return INACTIVE.contains(st);
    }
}
