package com.platform.people.application;

import com.platform.common.security.AuthContext;
import com.platform.people.domain.Department;
import com.platform.people.domain.Designation;
import com.platform.people.domain.Employee;
import com.platform.people.domain.Location;
import com.platform.people.infrastructure.persistence.DepartmentRepository;
import com.platform.people.infrastructure.persistence.DesignationRepository;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.infrastructure.persistence.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AuthFacadeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final LocationRepository locationRepository;
    private final RbacService rbacService;

    public AuthFacadeService(
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            DesignationRepository designationRepository,
            LocationRepository locationRepository,
            RbacService rbacService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.locationRepository = locationRepository;
        this.rbacService = rbacService;
    }

    /**
     * Account picker list (demo login only; gateway gates it with X-Demo-Auth-Key).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAccounts() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Employee e : employeeRepository.findAll()) {
            if (!e.isActive()) continue;
            if (e.getStatus() != null && List.of("Inactive", "Resigned", "Terminated").contains(e.getStatus())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", String.valueOf(e.getId()));
            row.put("name", e.getFullName());
            row.put("email", e.getEmail());
            List<String> roles = rbacService.roleCodesFor(e.getId());
            row.put("role", roles.isEmpty() ? "employee" : String.join(", ", roles));
            departmentRepository.findById(e.getDepartmentId() == null ? -1L : e.getDepartmentId())
                    .map(Department::getName).ifPresent(n -> row.put("department", n));
            out.add(row);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> me() {
        Long employeeId = AuthContext.requireEmployeeId();
        Employee e = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        List<String> roles = rbacService.roleCodesFor(employeeId);
        Set<String> perms = rbacService.permissionKeysFor(employeeId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", String.valueOf(e.getId()));
        m.put("name", e.getFullName());
        m.put("email", e.getEmail());
        m.put("roles", roles);
        m.put("permissions", new ArrayList<>(perms));
        m.put("scopes", rbacService.permissionScopesFor(employeeId));
        m.put("department", e.getDepartmentId() == null ? null :
                departmentRepository.findById(e.getDepartmentId()).map(Department::getName).orElse(null));
        m.put("position", e.getJobTitle() != null ? e.getJobTitle() :
                (e.getDesignationId() == null ? null :
                        designationRepository.findById(e.getDesignationId()).map(Designation::getName).orElse(null)));
        m.put("phoneNumber", e.getPhone());
        m.put("joinDate", e.getStartDate() == null ? null : e.getStartDate().toString());
        m.put("status", e.getStatus());
        Long photoFileId = null;
        Map<String, Object> cf = e.getCustomFields();
        if (cf != null && cf.get("photoFileId") != null && !String.valueOf(cf.get("photoFileId")).isBlank()) {
            try {
                photoFileId = Long.valueOf(String.valueOf(cf.get("photoFileId")).trim());
            } catch (NumberFormatException ignored) {
                photoFileId = null;
            }
        }
        m.put("photoFileId", photoFileId);
        m.put("photoObjectKey", e.getPhotoObjectKey());
        // DR-01 / NFR-16 — FE shows dates/times in the employee's location zone.
        m.put("locationId", e.getLocationId());
        if (e.getLocationId() != null) {
            locationRepository.findById(e.getLocationId()).ifPresentOrElse(loc -> {
                m.put("locationName", loc.getName());
                m.put("locationTimeZone", loc.getTimeZone());
            }, () -> {
                m.put("locationName", null);
                m.put("locationTimeZone", null);
            });
        } else {
            m.put("locationName", null);
            m.put("locationTimeZone", null);
        }
        return m;
    }
}
