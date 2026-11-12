package com.platform.people.api.rest;

import com.platform.common.security.InternalCalls;
import com.platform.people.application.RbacService;
import com.platform.people.domain.Designation;
import com.platform.people.domain.Employee;
import com.platform.people.domain.Location;
import com.platform.people.domain.Team;
import com.platform.people.infrastructure.persistence.DesignationRepository;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.infrastructure.persistence.LocationRepository;
import com.platform.people.infrastructure.persistence.TeamRepository;
import com.platform.people.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * S2S employee + RBAC directory for Leave / Time&amp;Work (guide §3 — People owns employees).
 */
@RestController
@RequestMapping("/api/internal/employees")
@Hidden
public class InternalEmployeeDirectoryController {

    private final EmployeeRepository employeeRepository;
    private final DesignationRepository designationRepository;
    private final TeamRepository teamRepository;
    private final LocationRepository locationRepository;
    private final RbacService rbacService;
    private final String internalKey;

    public InternalEmployeeDirectoryController(
            EmployeeRepository employeeRepository,
            DesignationRepository designationRepository,
            TeamRepository teamRepository,
            LocationRepository locationRepository,
            RbacService rbacService,
            @Value("${platform.security.internal-key}") String internalKey) {
        this.employeeRepository = employeeRepository;
        this.designationRepository = designationRepository;
        this.teamRepository = teamRepository;
        this.locationRepository = locationRepository;
        this.rbacService = rbacService;
        this.internalKey = internalKey;
    }

    @GetMapping("/{id}")
    @RequiresPermission(feature = "employees", action = "view", allowInternal = true)
    public Map<String, Object> one(@PathVariable Long id, HttpServletRequest request) {
        requireInternal(request);
        Employee emp = employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        return toView(emp);
    }

    @GetMapping
    @RequiresPermission(feature = "employees", action = "view", allowInternal = true)
    public List<Map<String, Object>> list(
            HttpServletRequest request,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false) Boolean active) {
        requireInternal(request);
        Stream<Employee> stream = employeeRepository.findAll().stream();
        if (teamId != null) {
            stream = stream.filter(e -> teamId.equals(e.getTeamId()));
        }
        if (managerId != null) {
            stream = stream.filter(e -> managerId.equals(e.getManagerId()));
        }
        if (Boolean.TRUE.equals(active)) {
            stream = stream.filter(Employee::isActive);
        } else if (Boolean.FALSE.equals(active)) {
            stream = stream.filter(e -> !e.isActive());
        }
        // Reference data loaded once per list (was 3 lookups per employee row).
        Lookups lookups = new Lookups(
                toMap(locationRepository.findAll(), Location::getId),
                toMap(designationRepository.findAll(), Designation::getId),
                toMap(teamRepository.findAll(), Team::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        stream.forEach(e -> out.add(toView(e, lookups)));
        return out;
    }

    @GetMapping("/{id}/authz")
    @RequiresPermission(feature = "employees", action = "view", allowInternal = true)
    public Map<String, Object> authz(@PathVariable Long id, HttpServletRequest request) {
        requireInternal(request);
        if (!employeeRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeId", id);
        out.put("roleCodes", rbacService.roleCodesFor(id));
        out.put("grantsWildcard", rbacService.hasWildcard(id));
        out.put("permissions", rbacService.permissionKeysFor(id));
        out.put("permissionScopes", rbacService.permissionScopesFor(id));
        return out;
    }

    private record Lookups(Map<Long, Location> locations, Map<Long, Designation> designations, Map<Long, Team> teams) {
    }

    private static <T> Map<Long, T> toMap(List<T> rows, java.util.function.Function<T, Long> id) {
        Map<Long, T> out = new java.util.HashMap<>();
        for (T r : rows) {
            Long k = id.apply(r);
            if (k != null) {
                out.put(k, r);
            }
        }
        return out;
    }

    private Map<String, Object> toView(Employee e) {
        return toView(e, null);
    }

    /**
     * Directory view shared with Leave / Time&amp;Work / Documents. Salary fields are deliberately NOT
     * included (guide §3 data minimisation, audit MSG-08): no consumer of this directory uses them;
     * Payroll reads pay data through its own {@code /api/internal/payroll} contract.
     */
    private Map<String, Object> toView(Employee e, Lookups lookups) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("employeeCode", e.getEmployeeCode());
        m.put("fullName", e.getFullName());
        m.put("email", e.getEmail());
        m.put("departmentId", e.getDepartmentId());
        m.put("designationId", e.getDesignationId());
        m.put("managerId", e.getManagerId());
        m.put("preferredName", e.getPreferredName());
        m.put("jobTitle", e.getJobTitle());
        m.put("locationId", e.getLocationId());
        if (e.getLocationId() != null) {
            (lookups != null ? java.util.Optional.ofNullable(lookups.locations().get(e.getLocationId()))
                    : locationRepository.findById(e.getLocationId()))
                    .map(Location::getTimeZone)
                    .ifPresent(tz -> m.put("locationTimeZone", tz));
        }
        m.put("teamId", e.getTeamId());
        m.put("employmentType", e.getEmploymentType());
        m.put("startDate", e.getStartDate() == null ? null : e.getStartDate().toString());
        m.put("probationEnd", e.getProbationEnd() == null ? null : e.getProbationEnd().toString());
        m.put("contractEnd", e.getContractEnd() == null ? null : e.getContractEnd().toString());
        m.put("payCurrency", e.getPayCurrency());
        m.put("personalEmail", e.getPersonalEmail());
        m.put("status", e.getStatus());
        m.put("phone", e.getPhone());
        m.put("workPattern", e.getWorkPattern());
        m.put("customFields", e.getCustomFields());
        m.put("active", e.isActive());
        m.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        m.put("updatedAt", e.getUpdatedAt() == null ? null : e.getUpdatedAt().toString());
        if (e.getDesignationId() != null) {
            (lookups != null ? java.util.Optional.ofNullable(lookups.designations().get(e.getDesignationId()))
                    : designationRepository.findById(e.getDesignationId()))
                    .map(Designation::getApproverRole)
                    .ifPresent(r -> m.put("designationApproverRole", r));
        }
        if (e.getTeamId() != null) {
            (lookups != null ? java.util.Optional.ofNullable(lookups.teams().get(e.getTeamId()))
                    : teamRepository.findById(e.getTeamId())).ifPresent(team -> putTeam(m, team));
        }
        return m;
    }

    private static void putTeam(Map<String, Object> m, Team team) {
        m.put("teamCode", team.getCode());
        m.put("teamMaxOnLeave", team.getMaxOnLeave());
        m.put("teamClashAction", team.getClashAction());
        m.put("teamLeadEmployeeId", team.getLeadEmployeeId());
    }

    private void requireInternal(HttpServletRequest request) {
        if (!InternalCalls.isTrusted(request.getHeader(InternalCalls.HEADER), internalKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal key required");
        }
    }
}
