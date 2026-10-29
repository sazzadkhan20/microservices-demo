package com.platform.hrms.application;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.common.security.AuthContext;
import com.platform.hrms.api.dto.EmployeeRequest;
import com.platform.hrms.api.dto.EmployeeResponse;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.domain.Department;
import com.platform.hrms.domain.Designation;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.ExtIndexedAttribute;
import com.platform.hrms.domain.Location;
import com.platform.hrms.domain.ReportingLineHistory;
import com.platform.hrms.domain.Team;
import com.platform.hrms.domain.TenantFieldDefinition;
import com.platform.hrms.domain.TenantSettings;
import com.platform.hrms.extension.config.TenantConfigLoader;
import com.platform.hrms.extension.engine.EmployeeQueryEngine;
import com.platform.hrms.infrastructure.persistence.DepartmentRepository;
import com.platform.hrms.infrastructure.persistence.DesignationRepository;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.ExtIndexedAttributeRepository;
import com.platform.hrms.infrastructure.persistence.LocationRepository;
import com.platform.hrms.infrastructure.persistence.ReportingLineHistoryRepository;
import com.platform.hrms.infrastructure.persistence.TeamRepository;
import com.platform.hrms.infrastructure.persistence.TenantSettingsRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Service
public class EmployeeService {

    private final EmployeeRepository repository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final TeamRepository teamRepository;
    private final LocationRepository locationRepository;
    private final ExtIndexedAttributeRepository extIndexedAttributeRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final EmployeeQueryEngine queryEngine;
    private final TenantConfigLoader configLoader;
    private final RbacService rbacService;
    private final DataScopeService dataScopeService;
    private final ReportingLineHistoryRepository reportingLineHistoryRepository;

    public EmployeeService(
            EmployeeRepository repository,
            DepartmentRepository departmentRepository,
            DesignationRepository designationRepository,
            TeamRepository teamRepository,
            LocationRepository locationRepository,
            ExtIndexedAttributeRepository extIndexedAttributeRepository,
            TenantSettingsRepository tenantSettingsRepository,
            EmployeeQueryEngine queryEngine,
            TenantConfigLoader configLoader,
            RbacService rbacService,
            DataScopeService dataScopeService,
            ReportingLineHistoryRepository reportingLineHistoryRepository) {
        this.repository = repository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.teamRepository = teamRepository;
        this.locationRepository = locationRepository;
        this.extIndexedAttributeRepository = extIndexedAttributeRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.queryEngine = queryEngine;
        this.configLoader = configLoader;
        this.rbacService = rbacService;
        this.dataScopeService = dataScopeService;
        this.reportingLineHistoryRepository = reportingLineHistoryRepository;
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        String code = request.employeeCode() == null || request.employeeCode().isBlank()
                ? nextEmployeeCode()
                : request.employeeCode().trim();
        repository.findByEmployeeCode(code).ifPresent(e -> {
            throw new IllegalStateException("employeeCode already exists: " + code);
        });
        validateEmployeeCodePattern(code);
        String email = normalizeEmail(request.email());
        assertEmailAvailable(email, null);

        Long resolvedDepartmentId = request.departmentId();
        if (request.teamId() != null) {
            Team team = teamRepository.findById(request.teamId())
                    .orElseThrow(() -> new IllegalArgumentException("Team not found: " + request.teamId()));
            if (resolvedDepartmentId == null && team.getDepartmentId() != null) {
                resolvedDepartmentId = team.getDepartmentId(); // FR-EMP-05
            }
        }
        if (resolvedDepartmentId == null) {
            throw new IllegalArgumentException("departmentId is required (or choose a team)");
        }
        if (request.designationId() == null) {
            throw new IllegalArgumentException("designationId is required");
        }
        final Long departmentId = resolvedDepartmentId;

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + departmentId));
        Designation designation = designationRepository.findById(request.designationId())
                .orElseThrow(() -> new IllegalArgumentException("Designation not found: " + request.designationId()));

        Employee manager = null;
        if (request.managerId() != null) {
            manager = repository.findById(request.managerId())
                    .orElseThrow(() -> new IllegalArgumentException("Manager not found: " + request.managerId()));
            if (!manager.isActive()) {
                throw new IllegalStateException("Manager is inactive");
            }
        }

        Map<String, Object> incoming = request.customFields() == null ? Map.of() : request.customFields();
        List<TenantFieldDefinition> definitions = configLoader.loadFieldDefinitions();
        validateRequiredAndUniqueFields(incoming, definitions);

        Map<String, String> extensionValues = new HashMap<>();
        Map<String, Object> jsonbOnly = new HashMap<>();
        Map<String, TenantFieldDefinition> byName = new HashMap<>();
        for (TenantFieldDefinition def : definitions) {
            byName.put(def.getFieldName(), def);
        }
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            TenantFieldDefinition def = byName.get(entry.getKey());
            if (def != null && "EXTENSION_TABLE".equalsIgnoreCase(def.getStorageType())) {
                if (entry.getValue() != null) {
                    extensionValues.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            } else {
                jsonbOnly.put(entry.getKey(), entry.getValue());
            }
        }

        Instant now = Instant.now();
        Employee employee = new Employee();
        employee.setEmployeeCode(code);
        employee.setFullName(request.fullName());
        employee.setEmail(email);
        employee.setPreferredName(request.preferredName());
        employee.setJobTitle(request.jobTitle() != null && !request.jobTitle().isBlank()
                ? request.jobTitle() : designation.getName());
        employee.setDepartmentId(department.getId());
        employee.setDesignationId(designation.getId());
        employee.setManagerId(request.managerId());
        employee.setTeamId(request.teamId());
        employee.setLocationId(request.locationId());
        employee.setEmploymentType(request.employmentType() == null || request.employmentType().isBlank()
                ? "FULL_TIME" : request.employmentType());
        employee.setStartDate(request.startDate() == null ? java.time.LocalDate.now() : request.startDate());
        employee.setProbationEnd(request.probationEnd());
        employee.setContractEnd(request.contractEnd());
        employee.setPayCurrency(request.payCurrency() == null || request.payCurrency().isBlank()
                ? "BDT" : request.payCurrency());
        employee.setWorkPattern(request.workPattern() == null || request.workPattern().isBlank()
                ? "FIXED" : request.workPattern());
        employee.setPhone(request.phone());
        employee.setPersonalEmail(request.personalEmail());
        employee.setBaseSalary(request.baseSalary() == null ? BigDecimal.ZERO : request.baseSalary());
        employee.setHourlyRate(request.hourlyRate() == null ? BigDecimal.ZERO : request.hourlyRate());
        employee.setStatus(request.status() == null || request.status().isBlank() ? "Active" : request.status());
        if (Boolean.TRUE.equals(request.attachOnboardingChecklist())) {
            jsonbOnly.put("onboardingChecklist", true);
        }
        if (request.address() != null && !request.address().isBlank()) {
            jsonbOnly.put("address", request.address());
        }
        employee.setCustomFields(jsonbOnly);
        employee.setActive(!"Inactive".equalsIgnoreCase(employee.getStatus())
                && !"Resigned".equalsIgnoreCase(employee.getStatus())
                && !"Terminated".equalsIgnoreCase(employee.getStatus()));
        employee.setCreatedAt(now);
        employee.setUpdatedAt(now);
        Employee saved = repository.save(employee);

        for (Map.Entry<String, String> ext : extensionValues.entrySet()) {
            ExtIndexedAttribute row = new ExtIndexedAttribute();
            row.setEmployeeId(saved.getId());
            row.setAttrKey(ext.getKey());
            row.setAttrValue(ext.getValue());
            extIndexedAttributeRepository.save(row);
        }

        List<String> roles = List.of();
        if (request.roleCodes() != null && !request.roleCodes().isEmpty()) {
            roles = rbacService.assignRoles(saved.getId(), request.roleCodes());
        }

        Map<String, Object> combined = new HashMap<>(saved.getCustomFields());
        combined.putAll(extensionValues);
        Team team = saved.getTeamId() == null ? null : teamRepository.findById(saved.getTeamId()).orElse(null);
        Location location = saved.getLocationId() == null ? null
                : locationRepository.findById(saved.getLocationId()).orElse(null);
        EmployeeResponse created = EmployeeResponse.from(
                saved, department, designation, manager, team, location, combined, roles);
        return compensationAccess().test(saved.getId()) ? created : created.withoutCompensation();
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        requireScope(id, "employees", "edit");
        Employee employee = requireEntity(id);
        if (request.fullName() != null && !request.fullName().isBlank()) {
            employee.setFullName(request.fullName());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = normalizeEmail(request.email());
            if (!email.equalsIgnoreCase(employee.getEmail())) {
                assertEmailAvailable(email, id);
            }
            employee.setEmail(email);
        }
        if (request.preferredName() != null) {
            employee.setPreferredName(request.preferredName());
        }
        if (request.jobTitle() != null) {
            employee.setJobTitle(request.jobTitle());
        }
        if (request.phone() != null) {
            employee.setPhone(request.phone());
        }
        if (request.personalEmail() != null) {
            employee.setPersonalEmail(request.personalEmail());
        }
        if (request.employmentType() != null) {
            employee.setEmploymentType(request.employmentType());
        }
        if (request.startDate() != null) {
            employee.setStartDate(request.startDate());
        }
        if (request.probationEnd() != null) {
            employee.setProbationEnd(request.probationEnd());
        }
        if (request.contractEnd() != null) {
            employee.setContractEnd(request.contractEnd());
        }
        if (request.payCurrency() != null) {
            employee.setPayCurrency(request.payCurrency());
        }
        if (request.workPattern() != null) {
            employee.setWorkPattern(request.workPattern());
        }
        if (request.managerId() != null) {
            Long previousManager = employee.getManagerId();
            Long requestedManager = request.managerId();
            // 0 / negative means clear manager
            final Long nextManager = (requestedManager != null && requestedManager <= 0) ? null : requestedManager;
            if (nextManager != null) {
                repository.findById(nextManager)
                        .orElseThrow(() -> new IllegalArgumentException("Manager not found: " + nextManager));
            }
            if (!java.util.Objects.equals(previousManager, nextManager)) {
                recordManagerChange(employee.getId(), previousManager, nextManager);
            }
            employee.setManagerId(nextManager);
        }
        if (request.teamId() != null) {
            Team team = teamRepository.findById(request.teamId())
                    .orElseThrow(() -> new IllegalArgumentException("Team not found: " + request.teamId()));
            employee.setTeamId(team.getId());
            if (team.getDepartmentId() != null) {
                employee.setDepartmentId(team.getDepartmentId());
            }
        }
        if (request.departmentId() != null) {
            employee.setDepartmentId(request.departmentId());
        }
        if (request.designationId() != null) {
            employee.setDesignationId(request.designationId());
        }
        if (request.locationId() != null) {
            employee.setLocationId(request.locationId());
        }
        if (request.baseSalary() != null) {
            employee.setBaseSalary(request.baseSalary());
        }
        if (request.hourlyRate() != null) {
            employee.setHourlyRate(request.hourlyRate());
        }
        if (request.status() != null && !request.status().isBlank()) {
            employee.setStatus(request.status());
            employee.setActive(!"Inactive".equalsIgnoreCase(request.status())
                    && !"Resigned".equalsIgnoreCase(request.status())
                    && !"Terminated".equalsIgnoreCase(request.status()));
        }
        employee.setUpdatedAt(Instant.now());
        return toResponse(repository.save(employee));
    }

    @Transactional
    public EmployeeResponse updateStatus(Long id, String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        requireScope(id, "employees", "edit");
        Employee employee = requireEntity(id);
        employee.setStatus(status.trim());
        employee.setActive(!"Inactive".equalsIgnoreCase(status)
                && !"Resigned".equalsIgnoreCase(status)
                && !"Terminated".equalsIgnoreCase(status));
        employee.setUpdatedAt(Instant.now());
        return toResponse(repository.save(employee));
    }

    @Transactional
    public List<String> assignRoles(Long id, List<String> roleCodes) {
        requireEntity(id);
        return rbacService.assignRoles(id, roleCodes);
    }

    /** Next code from tenant pattern, default QII-####. */
    @Transactional(readOnly = true)
    public String nextEmployeeCode() {
        int max = 0;
        for (Employee e : repository.findAll()) {
            Matcher m = Pattern.compile("(\\d+)\\s*$").matcher(e.getEmployeeCode() == null ? "" : e.getEmployeeCode());
            if (m.find()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        return String.format(Locale.ROOT, "QII-%04d", max + 1);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long id) {
        // IDOR guard: OWN = self, TEAM = self + direct reports, ALL = tenant-wide (else 403)
        requireScope(id, "employees", "view");
        return toResponse(requireEntity(id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> list(Map<String, String> filters) {
        Long actor = AuthContext.requireEmployeeId();
        DataScopeService.Visibility visible = dataScopeService.visibility(actor, "employees", "view");
        Predicate<Long> compensation = compensationAccess();
        Specification<Employee> spec = queryEngine.buildSpec(filters);
        return repository.findAll(spec).stream()
                .filter(e -> visible.allows(e.getId()))
                .map(this::withExtensionFields)
                .map(e -> toResponse(e, compensation))
                .toList();
    }

    /**
     * Config-driven list: PageQuery supplies page/size/q/filter.*; EmployeeQueryEngine handles
     * known extension/core filters; DynamicListSupport applies q + remaining equality filters.
     */
    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(PageQuery query) {
        List<EmployeeResponse> all = list(query.filters());
        return DynamicListSupport.page(
                all,
                query,
                e -> DynamicListSupport.containsIgnoreCase(e.fullName(), query.q())
                        || DynamicListSupport.containsIgnoreCase(e.employeeCode(), query.q())
                        || DynamicListSupport.containsIgnoreCase(e.email(), query.q()),
                e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("active", String.valueOf(e.active()));
                    m.put("status", e.status() == null ? "" : e.status());
                    m.put("departmentId", e.departmentId() == null ? "" : String.valueOf(e.departmentId()));
                    m.put("designationId", e.designationId() == null ? "" : String.valueOf(e.designationId()));
                    m.put("teamId", e.teamId() == null ? "" : String.valueOf(e.teamId()));
                    m.put("locationId", e.locationId() == null ? "" : String.valueOf(e.locationId()));
                    m.put("employmentType", e.employmentType() == null ? "" : e.employmentType());
                    m.put("payCurrency", e.payCurrency() == null ? "" : e.payCurrency());
                    m.put("workPattern", e.workPattern() == null ? "" : e.workPattern());
                    m.put("employeeCode", e.employeeCode() == null ? "" : e.employeeCode());
                    m.put("managerId", e.managerId() == null ? "" : String.valueOf(e.managerId()));
                    return m;
                }
        );
    }

    @Transactional(readOnly = true)
    public Employee requireEntity(Long id) {
        return withExtensionFields(repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + id)));
    }

    private void validateEmployeeCodePattern(String employeeCode) {
        Map<String, Object> settings = loadSettings();
        Object patternObj = settings.get("employeeCodePattern");
        if (patternObj == null) {
            return;
        }
        String regex = String.valueOf(patternObj);
        if (!Pattern.compile(regex).matcher(employeeCode).matches()) {
            throw new IllegalArgumentException("employeeCode must match pattern: " + regex);
        }
    }

    private void validateRequiredAndUniqueFields(Map<String, Object> incoming, List<TenantFieldDefinition> definitions) {
        for (TenantFieldDefinition def : definitions) {
            Object value = incoming.get(def.getFieldName());
            if (def.isRequiredFlag() && (value == null || String.valueOf(value).isBlank())) {
                throw new IllegalArgumentException("Required custom field missing: " + def.getFieldName());
            }
            if (value != null && def.getValidationRegex() != null && !def.getValidationRegex().isBlank()) {
                if (!Pattern.compile(def.getValidationRegex()).matcher(String.valueOf(value)).matches()) {
                    throw new IllegalArgumentException(
                            "Field " + def.getFieldName() + " must match: " + def.getValidationRegex());
                }
            }
            if (def.isUniqueActive() && value != null) {
                String str = String.valueOf(value);
                if ("EXTENSION_TABLE".equalsIgnoreCase(def.getStorageType())) {
                    boolean taken = extIndexedAttributeRepository.findAll().stream()
                            .filter(a -> def.getFieldName().equals(a.getAttrKey()))
                            .filter(a -> str.equalsIgnoreCase(a.getAttrValue()))
                            .anyMatch(a -> repository.findById(a.getEmployeeId())
                                    .map(Employee::isActive)
                                    .orElse(false));
                    if (taken) {
                        throw new IllegalStateException(
                                "Active employee already uses " + def.getFieldName() + "=" + str);
                    }
                }
            }
        }
    }

    private Map<String, Object> loadSettings() {
        return tenantSettingsRepository.findAll().stream().findFirst()
                .map(TenantSettings::getSettings)
                .orElse(Map.of());
    }

    private EmployeeResponse toResponse(Employee employee) {
        return toResponse(employee, compensationAccess());
    }

    private EmployeeResponse toResponse(Employee employee, Predicate<Long> compensationAccess) {
        EmployeeResponse full = buildResponse(employee);
        return compensationAccess.test(employee.getId()) ? full : full.withoutCompensation();
    }

    /**
     * baseSalary / hourlyRate are visible only to: the employee themself, or callers holding
     * payroll:view, payroll:run or employees:edit (each limited to the employees its data scope covers).
     */
    private Predicate<Long> compensationAccess() {
        Long actor = AuthContext.getEmployeeId();
        if (actor == null) {
            return id -> false;
        }
        DataScopeService.Visibility payrollView = dataScopeService.visibility(actor, "payroll", "view");
        DataScopeService.Visibility payrollRun = dataScopeService.visibility(actor, "payroll", "run");
        DataScopeService.Visibility employeesEdit = dataScopeService.visibility(actor, "employees", "edit");
        return id -> actor.equals(id)
                || payrollView.allows(id)
                || payrollRun.allows(id)
                || employeesEdit.allows(id);
    }

    private void requireScope(Long targetEmployeeId, String feature, String action) {
        dataScopeService.requireAccess(AuthContext.requireEmployeeId(), targetEmployeeId, feature, action);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        return email.trim();
    }

    /** Email is unique per tenant (DB-per-tenant), case-insensitive; also backed by a DB unique index. */
    private void assertEmailAvailable(String email, Long excludeEmployeeId) {
        boolean taken = excludeEmployeeId == null
                ? repository.existsByEmailIgnoreCase(email)
                : repository.existsByEmailIgnoreCaseAndIdNot(email, excludeEmployeeId);
        if (taken) {
            throw new IllegalStateException("Employee email already exists: " + email);
        }
    }

    private EmployeeResponse buildResponse(Employee employee) {
        Department dept = employee.getDepartmentId() == null ? null
                : departmentRepository.findById(employee.getDepartmentId()).orElse(null);
        Designation desig = employee.getDesignationId() == null ? null
                : designationRepository.findById(employee.getDesignationId()).orElse(null);
        Employee manager = employee.getManagerId() == null ? null
                : repository.findById(employee.getManagerId()).orElse(null);
        Team team = employee.getTeamId() == null ? null
                : teamRepository.findById(employee.getTeamId()).orElse(null);
        Location location = employee.getLocationId() == null ? null
                : locationRepository.findById(employee.getLocationId()).orElse(null);
        List<String> roles = employee.getId() == null ? List.of() : rbacService.roleCodesFor(employee.getId());
        return EmployeeResponse.from(
                employee, dept, desig, manager, team, location, employee.getCustomFields(), roles);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> reportingLine(Long employeeId) {
        requireScope(employeeId, "employees", "view");
        List<Map<String, Object>> out = new ArrayList<>();
        for (ReportingLineHistory h : reportingLineHistoryRepository.findByEmployeeIdOrderByFromDateDesc(employeeId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", h.getId());
            row.put("managerId", h.getManagerId());
            row.put("managerName", h.getManagerId() == null ? null
                    : repository.findById(h.getManagerId()).map(Employee::getFullName).orElse(null));
            row.put("fromDate", h.getFromDate() == null ? null : h.getFromDate().toString());
            row.put("toDate", h.getToDate() == null ? null : h.getToDate().toString());
            row.put("changedBy", h.getChangedBy());
            out.add(row);
        }
        // If history empty, synthesise current manager as open line for UX
        if (out.isEmpty()) {
            Employee e = requireEntity(employeeId);
            if (e.getManagerId() != null) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", null);
                row.put("managerId", e.getManagerId());
                row.put("managerName", repository.findById(e.getManagerId()).map(Employee::getFullName).orElse(null));
                row.put("fromDate", e.getStartDate() == null ? LocalDate.now().toString() : e.getStartDate().toString());
                row.put("toDate", null);
                row.put("changedBy", null);
                out.add(row);
            }
        }
        return out;
    }

    private void recordManagerChange(Long employeeId, Long previousManager, Long nextManager) {
        LocalDate today = LocalDate.now();
        reportingLineHistoryRepository.findFirstByEmployeeIdAndToDateIsNullOrderByFromDateDesc(employeeId)
                .ifPresent(open -> {
                    open.setToDate(today);
                    reportingLineHistoryRepository.save(open);
                });
        ReportingLineHistory row = new ReportingLineHistory();
        row.setEmployeeId(employeeId);
        row.setManagerId(nextManager);
        row.setFromDate(today);
        row.setToDate(null);
        row.setChangedBy(AuthContext.getEmployeeId());
        row.setCreatedAt(Instant.now());
        reportingLineHistoryRepository.save(row);
        // previousManager unused except for audit trail in history rows
    }

    private Employee withExtensionFields(Employee employee) {
        Map<String, Object> combined = new HashMap<>(employee.getCustomFields() == null ? Map.of() : employee.getCustomFields());
        for (TenantFieldDefinition def : configLoader.loadFieldDefinitions()) {
            if (!"EXTENSION_TABLE".equalsIgnoreCase(def.getStorageType())) {
                continue;
            }
            extIndexedAttributeRepository.findById(new ExtIndexedAttribute.Pk(employee.getId(), def.getFieldName()))
                    .ifPresent(attr -> combined.put(attr.getAttrKey(), attr.getAttrValue()));
        }
        employee.setCustomFields(combined);
        return employee;
    }
}
