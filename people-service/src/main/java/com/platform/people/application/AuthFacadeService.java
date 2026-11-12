package com.platform.people.application;

import com.platform.common.security.AuthContext;
import com.platform.people.domain.Department;
import com.platform.people.domain.Designation;
import com.platform.people.domain.Employee;
import com.platform.people.domain.Location;
import com.platform.people.client.TimeWorkClient;
import com.platform.people.infrastructure.persistence.DepartmentRepository;
import com.platform.people.infrastructure.persistence.DesignationRepository;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.infrastructure.persistence.LocationRepository;
import org.springframework.stereotype.Service;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AuthFacadeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final LocationRepository locationRepository;
    private final RbacService rbacService;
    private final TimeWorkClient timeWorkClient;
    private final TransactionTemplate readTx;
    /**
     * Schedule mode per employee (Time&amp;Work owns schedules). Successful lookups only, 30 s:
     * /me is the hottest People endpoint and must not wait on Time&amp;Work for every page view.
     */
    private final Cache<Long, String> scheduleModes = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    public AuthFacadeService(
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            DesignationRepository designationRepository,
            LocationRepository locationRepository,
            RbacService rbacService,
            TimeWorkClient timeWorkClient,
            PlatformTransactionManager transactionManager) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.locationRepository = locationRepository;
        this.rbacService = rbacService;
        this.timeWorkClient = timeWorkClient;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
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

    /**
     * Guide §7: the database work runs in a short read-only transaction; the Time&amp;Work schedule
     * lookup happens after it, so no People connection is held while waiting on a remote call.
     */
    public Map<String, Object> me() {
        Long employeeId = AuthContext.requireEmployeeId();
        Map<String, Object> m = readTx.execute(st -> meCore(employeeId));
        String workPattern = m.remove("__workPattern") instanceof String wp ? wp : "";
        String scheduleMode;
        if ("HOURLY".equalsIgnoreCase(workPattern)) {
            scheduleMode = "HOURLY";
        } else {
            scheduleMode = scheduleModeFor(employeeId);
        }
        boolean leaveEligible = !"HOURLY".equalsIgnoreCase(scheduleMode);
        m.put("workPattern", workPattern.isEmpty() ? null : workPattern);
        m.put("scheduleMode", scheduleMode.isEmpty() ? null : scheduleMode.toUpperCase(Locale.ROOT));
        m.put("leaveEligible", leaveEligible);
        return m;
    }

    private String scheduleModeFor(Long employeeId) {
        String hit = scheduleModes.getIfPresent(employeeId);
        if (hit != null) {
            return hit;
        }
        String mode = timeWorkClient.resolveScheduleMode(employeeId);
        // "" means unreachable or no schedule — not cached so the next call retries.
        if (mode != null && !mode.isEmpty()) {
            scheduleModes.put(employeeId, mode);
        }
        return mode == null ? "" : mode;
    }

    private Map<String, Object> meCore(Long employeeId) {
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
        // FR-LV-03 — leave UI uses schedule mode (not payroll pay type); resolved in me() after the tx.
        m.put("__workPattern", e.getWorkPattern() == null ? "" : e.getWorkPattern().trim());
        return m;
    }
}
