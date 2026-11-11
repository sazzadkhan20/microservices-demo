package com.platform.people.application;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.common.security.AuthContext;
import com.platform.people.api.dto.EmployeeRequest;
import com.platform.people.api.dto.EmployeeResponse;
import com.platform.people.application.support.DynamicListSupport;
import com.platform.people.domain.Department;
import com.platform.people.domain.Designation;
import com.platform.people.domain.Employee;
import com.platform.people.domain.ExtIndexedAttribute;
import com.platform.people.domain.Location;
import com.platform.people.domain.ReportingLineHistory;
import com.platform.people.domain.Team;
import com.platform.people.domain.TenantFieldDefinition;
import com.platform.people.domain.TenantSettings;
import com.platform.people.extension.config.TenantConfigLoader;
import com.platform.people.extension.engine.EmployeeQueryEngine;
import com.platform.people.infrastructure.persistence.DepartmentRepository;
import com.platform.people.infrastructure.persistence.DesignationRepository;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.infrastructure.persistence.ExtIndexedAttributeRepository;
import com.platform.people.infrastructure.persistence.LocationRepository;
import com.platform.people.infrastructure.persistence.ReportingLineHistoryRepository;
import com.platform.people.infrastructure.persistence.TeamRepository;
import com.platform.people.infrastructure.persistence.TenantSettingsRepository;
import com.platform.people.messaging.EmployeeProjectionPublisher;
import com.platform.people.client.FilesGatewayClient;
import com.platform.people.client.LeaveClient;
import com.platform.people.client.TimeWorkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

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
    private final ReportingLineService reportingLineService;
    private final EmployeeRulesService employeeRulesService;
    private final EmploymentHistoryService employmentHistoryService;
    private final OffboardingService offboardingService;
    private final ReportingLineDueService reportingLineDueService;
    private final UserAccountService userAccountService;
    private final ChecklistAssignmentService checklistAssignmentService;
    private final FilesGatewayClient filesGatewayClient;
    private final TimeWorkClient timeWorkClient;
    private final LeaveClient leaveClient;
    private final ObjectProvider<EmployeeProjectionPublisher> employeeProjectionPublisher;

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
            ReportingLineHistoryRepository reportingLineHistoryRepository,
            ReportingLineService reportingLineService,
            EmployeeRulesService employeeRulesService,
            EmploymentHistoryService employmentHistoryService,
            OffboardingService offboardingService,
            ReportingLineDueService reportingLineDueService,
            UserAccountService userAccountService,
            ChecklistAssignmentService checklistAssignmentService,
            FilesGatewayClient filesGatewayClient,
            TimeWorkClient timeWorkClient,
            LeaveClient leaveClient,
            ObjectProvider<EmployeeProjectionPublisher> employeeProjectionPublisher) {
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
        this.reportingLineService = reportingLineService;
        this.employeeRulesService = employeeRulesService;
        this.employmentHistoryService = employmentHistoryService;
        this.offboardingService = offboardingService;
        this.reportingLineDueService = reportingLineDueService;
        this.userAccountService = userAccountService;
        this.checklistAssignmentService = checklistAssignmentService;
        this.filesGatewayClient = filesGatewayClient;
        this.timeWorkClient = timeWorkClient;
        this.leaveClient = leaveClient;
        this.employeeProjectionPublisher = employeeProjectionPublisher;
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        String code = resolveEmployeeCodeForCreate(request.employeeCode());
        repository.findByEmployeeCode(code).ifPresent(e -> {
            throw new IllegalStateException("employeeCode already exists: " + code);
        });
        validateEmployeeCodePattern(code);
        String email = normalizeEmail(request.email());
        EmployeeRulesService.Rules rules = employeeRulesService.load();
        employeeRulesService.assertWorkEmail(email, rules);
        assertEmailAvailable(email, null);
        assertContractEndIfRequired(request.employmentType(), request.contractEnd(), rules);
        assertWorkPattern(request.workPattern(), rules);

        Long resolvedDepartmentId = request.departmentId();
        if (request.teamId() != null) {
            Team team = teamRepository.findById(request.teamId())
                    .orElseThrow(() -> new IllegalArgumentException("Team not found: " + request.teamId()));
            // FR-EMP-05 — choosing a team always sets department from the team.
            if (team.getDepartmentId() != null) {
                resolvedDepartmentId = team.getDepartmentId();
            }
        }
        if (resolvedDepartmentId == null) {
            throw new IllegalArgumentException("departmentId is required (or choose a team)");
        }
        // designationId is optional — tenants without a designation catalog (or jobTitle-only packs)
        // still create employees; when provided it must exist (config-driven org structure).
        final Long departmentId = resolvedDepartmentId;

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + departmentId));
        Designation designation = null;
        if (request.designationId() != null) {
            designation = designationRepository.findById(request.designationId())
                    .orElseThrow(() -> new IllegalArgumentException("Designation not found: " + request.designationId()));
        }

        Employee manager = null;
        if (request.managerId() != null && request.managerId() > 0) {
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
        String fallbackTitle = designation != null ? designation.getName() : "Employee";
        employee.setJobTitle(request.jobTitle() != null && !request.jobTitle().isBlank()
                ? request.jobTitle() : fallbackTitle);
        employee.setDepartmentId(department.getId());
        employee.setDesignationId(designation != null ? designation.getId() : null);
        Long createManagerId = (request.managerId() != null && request.managerId() > 0) ? request.managerId() : null;
        employee.setManagerId(createManagerId);
        employee.setTeamId(request.teamId());
        employee.setLocationId(request.locationId());
        employee.setEmploymentType(normalizeEmploymentType(
                request.employmentType() == null || request.employmentType().isBlank()
                        ? "FULL_TIME" : request.employmentType()));
        employee.setStartDate(request.startDate() == null ? java.time.LocalDate.now() : request.startDate());
        employee.setProbationEnd(request.probationEnd());
        employee.setContractEnd(request.contractEnd());
        employee.setPayCurrency(request.payCurrency() == null || request.payCurrency().isBlank()
                ? "BDT" : request.payCurrency());
        String pattern = request.workPattern() == null || request.workPattern().isBlank()
                ? defaultWorkPattern(rules) : request.workPattern().trim();
        employee.setWorkPattern(pattern);
        employee.setPhone(request.phone());
        employee.setPersonalEmail(request.personalEmail());
        applyPersonalColumns(employee, request, jsonbOnly, true);
        if ((request.baseSalary() != null && request.baseSalary().signum() > 0)
                || (request.hourlyRate() != null && request.hourlyRate().signum() > 0)
                || (request.payType() != null && !request.payType().isBlank())) {
            requireSalaryAccess();
        }
        BigDecimal base = request.baseSalary() == null ? BigDecimal.ZERO : request.baseSalary();
        BigDecimal hourly = request.hourlyRate() == null ? BigDecimal.ZERO : request.hourlyRate();
        if ("HOURLY".equalsIgnoreCase(request.payType()) && hourly.signum() == 0 && base.signum() > 0) {
            hourly = base;
            base = BigDecimal.ZERO;
        }
        employee.setBaseSalary(base);
        employee.setHourlyRate(hourly);
        String createStatus = request.status() == null || request.status().isBlank() ? "Active" : request.status();
        employeeRulesService.assertStatusAllowed(createStatus, rules);
        if (employeeRulesService.isInactive(createStatus, rules)) {
            throw new IllegalArgumentException("New employees cannot start as " + createStatus);
        }
        assertRequiredForActive(request, resolvedDepartmentId, rules);
        employee.setStatus(createStatus);
        employee.setActive(true);
        if (request.payType() != null && !request.payType().isBlank()) {
            jsonbOnly.put("payType", request.payType().trim().toUpperCase(Locale.ROOT));
        }
        employee.setCustomFields(jsonbOnly);
        employee.setCreatedAt(now);
        employee.setUpdatedAt(now);
        Employee saved = repository.save(employee);
        // Photo was uploaded before this id existed — bind the file to the new employee.
        bindPhotoSubject(saved);
        userAccountService.ensureForEmployee(saved);
        if (createManagerId != null) {
            LocalDate lineFrom = request.managerEffectiveFrom() != null
                    ? request.managerEffectiveFrom()
                    : saved.getStartDate();
            reportingLineService.seedInitial(saved.getId(), createManagerId, lineFrom);
        }

        for (Map.Entry<String, String> ext : extensionValues.entrySet()) {
            ExtIndexedAttribute row = new ExtIndexedAttribute();
            row.setEmployeeId(saved.getId());
            row.setAttrKey(ext.getKey());
            row.setAttrValue(ext.getValue());
            extIndexedAttributeRepository.save(row);
        }

        List<String> roles = applyCreateRoles(saved.getId(), request.roleCodes());
        // After commit — Time&Work looks the employee up in People; mid-txn the row is invisible.
        scheduleWorkPatternSync(saved.getId(), saved.getWorkPattern(), saved.getLocationId());
        // After commit — Leave seeds pro-rata openings from startDate against current categories.
        scheduleLeaveOpeningSeed(saved.getId());

        if (Boolean.TRUE.equals(request.attachOnboardingChecklist())) {
            try {
                checklistAssignmentService.assignOnboardingBestEffort(saved.getId(), saved.getStartDate());
            } catch (RuntimeException ex) {
                log.warn("Onboarding checklist attach failed for employee {}: {}", saved.getId(), ex.getMessage());
            }
            Map<String, Object> cf = new HashMap<>(
                    saved.getCustomFields() == null ? Map.of() : saved.getCustomFields());
            cf.put("onboardingChecklist", true);
            saved.setCustomFields(cf);
            saved = repository.save(saved);
        }

        Map<String, Object> combined = new HashMap<>(saved.getCustomFields());
        combined.putAll(extensionValues);
        Team team = saved.getTeamId() == null ? null : teamRepository.findById(saved.getTeamId()).orElse(null);
        Location location = saved.getLocationId() == null ? null
                : locationRepository.findById(saved.getLocationId()).orElse(null);
        publishEmployeeProjection(saved, roles);
        EmployeeResponse created = EmployeeResponse.from(
                saved, department, designation, manager, team, location, combined, roles);
        return compensationAccess().test(saved.getId()) ? created : created.withoutCompensation();
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        requireScope(id, "employees", "edit");
        Employee employee = requireEntity(id);
        EmployeeRulesService.Rules rules = employeeRulesService.load();
        Map<String, Object> before = snapshotEmployment(employee);

        if (request.fullName() != null && !request.fullName().isBlank()) {
            employee.setFullName(request.fullName());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = normalizeEmail(request.email());
            employeeRulesService.assertWorkEmail(email, rules);
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
            employee.setEmploymentType(normalizeEmploymentType(request.employmentType()));
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
        String typeForRule = request.employmentType() != null ? request.employmentType() : employee.getEmploymentType();
        LocalDate endForRule = request.contractEnd() != null ? request.contractEnd() : employee.getContractEnd();
        assertContractEndIfRequired(typeForRule, endForRule, rules);
        if (request.payCurrency() != null) {
            employee.setPayCurrency(request.payCurrency());
        }
        if (request.workPattern() != null) {
            assertWorkPattern(request.workPattern(), rules);
            employee.setWorkPattern(request.workPattern());
        }
        if (request.managerId() != null) {
            Long previousManager = employee.getManagerId();
            Long requestedManager = request.managerId();
            // 0 / negative means clear manager
            final Long nextManager = requestedManager <= 0 ? null : requestedManager;
            if (nextManager == null
                    && !employeeRulesService.isOffboarding(
                            request.status() != null ? request.status() : employee.getStatus(), rules)
                    && (rules.requireManagerOnCreate()
                            || rules.requiredForActive().stream().anyMatch(f -> "managerId".equalsIgnoreCase(f)))) {
                throw new IllegalArgumentException(
                        "managerId is required — clear only when offboarding (approvals need a reporting manager)");
            }
            if (nextManager != null) {
                Employee mgr = repository.findById(nextManager)
                        .orElseThrow(() -> new IllegalArgumentException("Manager not found: " + nextManager));
                if (!mgr.isActive()) {
                    throw new IllegalStateException("Manager is inactive");
                }
            }
            if (!java.util.Objects.equals(previousManager, nextManager)) {
                Long applied = reportingLineService.applyManagerChange(
                        employee.getId(),
                        previousManager,
                        nextManager,
                        request.managerEffectiveFrom());
                employee.setManagerId(applied);
                // Same-day / past effective: move this employee's pending leave approvals now.
                // Future effective: ReportingLineDueJob flips manager_id + moves approvals on that date.
                if (!java.util.Objects.equals(previousManager, applied) && applied != null) {
                    reportingLineDueService.moveApprovalsAfterManagerChange(
                            employee.getId(), previousManager, applied);
                }
            }
        }
        if (request.departmentId() != null) {
            employee.setDepartmentId(request.departmentId());
        }
        if (request.teamId() != null) {
            // 0 / negative clears team (same convention as managerId).
            if (request.teamId() <= 0) {
                employee.setTeamId(null);
            } else {
                Team team = teamRepository.findById(request.teamId())
                        .orElseThrow(() -> new IllegalArgumentException("Team not found: " + request.teamId()));
                employee.setTeamId(team.getId());
                // FR-EMP-05 — team always wins over a separately posted departmentId.
                if (team.getDepartmentId() != null) {
                    employee.setDepartmentId(team.getDepartmentId());
                }
            }
        }
        if (request.designationId() != null) {
            employee.setDesignationId(request.designationId());
        }
        if (request.locationId() != null) {
            employee.setLocationId(request.locationId());
        }
        if (request.baseSalary() != null) {
            requireSalaryAccess();
            employee.setBaseSalary(request.baseSalary());
        }
        if (request.hourlyRate() != null) {
            requireSalaryAccess();
            employee.setHourlyRate(request.hourlyRate());
        }
        if (request.payType() != null && !request.payType().isBlank()) {
            requireSalaryAccess();
            Map<String, Object> custom = new HashMap<>(
                    employee.getCustomFields() == null ? Map.of() : employee.getCustomFields());
            custom.put("payType", request.payType().trim().toUpperCase(Locale.ROOT));
            employee.setCustomFields(custom);
        }
        Map<String, Object> personalJson = new HashMap<>(
                employee.getCustomFields() == null ? Map.of() : employee.getCustomFields());
        applyPersonalColumns(employee, request, personalJson, false);
        employee.setCustomFields(personalJson);

        boolean statusViaOffboard = false;
        if (request.status() != null && !request.status().isBlank()) {
            offboardingService.applyStatusChange(
                    employee,
                    request.status(),
                    request.exitDate(),
                    request.exitReason(),
                    request.offboardOverrideReason(),
                    request.reassignReportsTo());
            statusViaOffboard = true;
        }
        employee.setUpdatedAt(Instant.now());
        Employee saved = repository.save(employee);
        userAccountService.syncFromEmployee(saved);
        // Pattern sync only when workPattern is explicitly changed. Location/team edits must not
        // rewrite an existing personal schedule (resolve-only: EMPLOYEE → TEAM → LOCATION).
        if (request.workPattern() != null) {
            scheduleWorkPatternSync(saved.getId(), saved.getWorkPattern(), saved.getLocationId());
        }
        Map<String, Object> after = snapshotEmployment(saved);
        if (statusViaOffboard) {
            // OffboardingService already historied status / exit* — avoid duplicate rows.
            before.put("status", after.get("status"));
            before.put("exitDate", after.get("exitDate"));
            before.put("exitReason", after.get("exitReason"));
        }
        employmentHistoryService.recordMany(saved.getId(), before, after);
        publishEmployeeProjection(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> offboardingHandover(Long id) {
        requireScope(id, "employees", "edit");
        requireEntity(id);
        return offboardingService.handoverPreview(id);
    }

    /**
     * Active people who can be assigned as manager of {@code employeeId} without creating a
     * reporting loop (self / direct / indirect report). Used by the employment form select.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> eligibleManagers(Long employeeId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Employee candidate : repository.findAll()) {
            if (candidate.getId() == null) {
                continue;
            }
            if (employeeId != null && java.util.Objects.equals(candidate.getId(), employeeId)) {
                continue;
            }
            if (!candidate.isActive()) {
                continue;
            }
            if (employeeId != null && reportingLineService.wouldCycle(employeeId, candidate.getId())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", candidate.getId());
            row.put("fullName", candidate.getFullName());
            row.put("employeeCode", candidate.getEmployeeCode());
            out.add(row);
        }
        out.sort((a, b) -> String.valueOf(a.get("fullName"))
                .compareToIgnoreCase(String.valueOf(b.get("fullName"))));
        return out;
    }

    @Transactional
    public EmployeeResponse updateStatus(Long id, String status) {
        return updateStatus(id, status, null, null, null, null);
    }

    @Transactional
    public EmployeeResponse updateStatus(
            Long id,
            String status,
            LocalDate exitDate,
            String exitReason,
            String overrideReason,
            Long reassignReportsTo) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        requireScope(id, "employees", "edit");
        Employee employee = requireEntity(id);
        offboardingService.applyStatusChange(
                employee, status.trim(), exitDate, exitReason, overrideReason, reassignReportsTo);
        Employee saved = repository.save(employee);
        publishEmployeeProjection(saved);
        return toResponse(saved);
    }

    /** FR-EMP-06 — self-edit allow-list from employeeRules.selfEditableFields. */
    @Transactional
    public EmployeeResponse updateOwnPersonal(Map<String, Object> body) {
        Long actor = AuthContext.requireEmployeeId();
        if (!rbacService.hasPermission(actor, "profile_personal", "edit")
                && !rbacService.hasPermission(actor, "employees", "edit")) {
            throw new SecurityException("Forbidden: profile_personal:edit required");
        }
        Employee employee = requireEntity(actor);
        EmployeeRulesService.Rules rules = employeeRulesService.load();
        Set<String> allowed = rules.selfEditableSet();
        Map<String, Object> before = snapshotEmployment(employee);
        Map<String, Object> custom = new HashMap<>(
                employee.getCustomFields() == null ? Map.of() : employee.getCustomFields());

        for (Map.Entry<String, Object> e : body.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey().trim();
            if (!allowed.contains(key.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Field not self-editable: " + key);
            }
            String val = e.getValue() == null ? null : String.valueOf(e.getValue()).trim();
            if (val != null && val.isEmpty()) {
                val = null;
            }
            switch (key.toLowerCase(Locale.ROOT)) {
                case "phone" -> employee.setPhone(val);
                case "personalemail" -> employee.setPersonalEmail(val);
                case "address" -> employee.setAddress(val);
                case "emergencyname" -> employee.setEmergencyName(val);
                case "emergencyrelation", "emergencyrelationship" -> employee.setEmergencyRelation(val);
                case "emergencyphone" -> employee.setEmergencyPhone(val);
                default -> {
                    if (val == null) custom.remove(key);
                    else custom.put(key, val);
                }
            }
        }
        // Keep legacy custom_fields mirrors for older readers.
        syncPersonalCustomMirrors(employee, custom);
        employee.setCustomFields(custom);
        employee.setUpdatedAt(Instant.now());
        Employee saved = repository.save(employee);
        employmentHistoryService.recordMany(saved.getId(), before, snapshotEmployment(saved));
        publishEmployeeProjection(saved);
        return toResponse(saved);
    }

    private void publishEmployeeProjection(Employee employee) {
        publishEmployeeProjection(employee, null);
    }

    private void publishEmployeeProjection(Employee employee, List<String> roleCodes) {
        EmployeeProjectionPublisher publisher = employeeProjectionPublisher.getIfAvailable();
        if (publisher != null) {
            publisher.publishUpserted(employee, roleCodes);
        }
    }

    /** Sync schedule only after the employee row is committed (Time&Work S2S lookup). */
    private void scheduleWorkPatternSync(Long employeeId, String workPattern, Long locationId) {
        if (employeeId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    timeWorkClient.syncEmployeeWorkPattern(employeeId, workPattern, locationId);
                }
            });
        } else {
            timeWorkClient.syncEmployeeWorkPattern(employeeId, workPattern, locationId);
        }
    }

    /** Seed leave openings after hire is visible to Leave's People gateway. */
    private void scheduleLeaveOpeningSeed(Long employeeId) {
        if (employeeId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    leaveClient.ensureOpeningBalances(employeeId);
                }
            });
        } else {
            leaveClient.ensureOpeningBalances(employeeId);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> employmentHistory(Long employeeId) {
        requireScope(employeeId, "employees", "view");
        return employmentHistoryService.list(employeeId);
    }

    /**
     * Salary on create/update: salary:edit or payroll:run only (not payroll:view / HR defaults).
     * Config-driven grants — not a hard-coded role check. Prefer mutating pay via payroll salary_history.
     */
    private void requireSalaryAccess() {
        Long actor = AuthContext.getEmployeeId();
        if (actor == null) {
            throw new IllegalStateException("Forbidden: salary fields require an authenticated employee");
        }
        if (rbacService.hasPermission(actor, "salary", "edit")
                || rbacService.hasPermission(actor, "payroll", "run")) {
            return;
        }
        throw new IllegalStateException("Forbidden: salary fields require salary:edit or payroll:run");
    }

    @Transactional
    public List<String> assignRoles(Long id, List<String> roleCodes) {
        Employee employee = requireEntity(id);
        List<String> assigned = rbacService.assignRoles(id, roleCodes);
        publishEmployeeProjection(employee, assigned);
        return assigned;
    }

    /**
     * Create-form meta for FE — code mode, defaults, and employeeRules the UI needs.
     * Driven entirely by tenant settings / pack (no brand hard-coding).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> createOptions() {
        Map<String, Object> settings = loadSettings();
        String mode = employeeCodeMode(settings);
        EmployeeRulesService.Rules rules = employeeRulesService.load();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeCodeMode", mode);
        out.put("employeeCodePattern", settings.get("employeeCodePattern"));
        out.put("employeeCodePrefix", settings.containsKey("employeeCodePrefix")
                ? settings.get("employeeCodePrefix") : "EMP-");
        out.put("employeeCodeDigits", settings.getOrDefault("employeeCodeDigits", 4));
        out.put("defaultCreateRoleCodes", defaultCreateRoleCodes(settings));
        if (!"MANUAL".equals(mode)) {
            out.put("nextEmployeeCode", nextEmployeeCode());
        }
        out.put("workEmailDomain", rules.workEmailDomain());
        out.put("workEmailDomains", rules.workEmailDomains());
        out.put("workPatterns", rules.workPatterns());
        out.put("contractTypesRequiringEnd", rules.contractTypesRequiringEnd());
        out.put("employmentStatuses", rules.employmentStatuses());
        out.put("employmentTypes", rules.employmentTypes());
        out.put("requiredForActive", rules.requiredForActive());
        out.put("requireManagerOnCreate", rules.requireManagerOnCreate());
        out.put("documentTypes", rules.documentTypes());
        return out;
    }

    /**
     * Next code from tenant settings: {@code employeeCodePrefix} + zero-padded digits
     * ({@code employeeCodeDigits}). Platform-neutral fallback {@code EMP-####} — never a business brand.
     */
    @Transactional(readOnly = true)
    public String nextEmployeeCode() {
        Map<String, Object> settings = loadSettings();
        String prefix;
        if (!settings.containsKey("employeeCodePrefix")) {
            prefix = "EMP-";
        } else {
            Object p = settings.get("employeeCodePrefix");
            prefix = p == null || "null".equalsIgnoreCase(String.valueOf(p)) ? "" : String.valueOf(p);
        }
        int digits = 4;
        Object dig = settings.get("employeeCodeDigits");
        if (dig instanceof Number n) {
            digits = Math.max(1, Math.min(12, n.intValue()));
        } else if (dig != null) {
            try {
                digits = Math.max(1, Math.min(12, Integer.parseInt(dig.toString().trim())));
            } catch (NumberFormatException ignored) { /* keep 4 */ }
        }
        int max = 0;
        for (Employee e : repository.findAll()) {
            Matcher m = Pattern.compile("(\\d+)\\s*$").matcher(e.getEmployeeCode() == null ? "" : e.getEmployeeCode());
            if (m.find()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        return prefix + String.format(Locale.ROOT, "%0" + digits + "d", max + 1);
    }

    /** AUTO = always generate; MANUAL = client must supply; OPTIONAL = generate when blank. */
    private String resolveEmployeeCodeForCreate(String requested) {
        Map<String, Object> settings = loadSettings();
        String mode = employeeCodeMode(settings);
        String trimmed = requested == null ? "" : requested.trim();
        return switch (mode) {
            case "MANUAL" -> {
                if (trimmed.isEmpty()) {
                    throw new IllegalArgumentException("employeeCode is required (employeeCodeMode=MANUAL)");
                }
                yield trimmed;
            }
            case "OPTIONAL" -> trimmed.isEmpty() ? nextEmployeeCode() : trimmed;
            default -> nextEmployeeCode(); // AUTO — ignore client-supplied codes
        };
    }

    private static String employeeCodeMode(Map<String, Object> settings) {
        Object raw = settings.get("employeeCodeMode");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return "AUTO";
        }
        return String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
    }

    private List<String> defaultCreateRoleCodes(Map<String, Object> settings) {
        Object raw = settings.get("defaultCreateRoleCodes");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of("EMPLOYEE");
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null && !String.valueOf(o).isBlank()) {
                out.add(String.valueOf(o).trim());
            }
        }
        return out.isEmpty() ? List.of("EMPLOYEE") : out;
    }

    /**
     * Explicit roleCodes require roles:assign. Empty/omitted → tenant defaultCreateRoleCodes
     * (system policy, no assign permission required — so HR create still lands an EMPLOYEE).
     */
    private List<String> applyCreateRoles(Long employeeId, List<String> requested) {
        boolean explicit = requested != null && !requested.isEmpty();
        if (explicit) {
            Long actor = AuthContext.getEmployeeId();
            if (actor == null || !rbacService.hasPermission(actor, "roles", "assign")) {
                throw new IllegalStateException("Forbidden: assigning roles requires roles:assign");
            }
            return rbacService.assignRoles(employeeId, requested);
        }
        List<String> defaults = defaultCreateRoleCodes(loadSettings());
        if (defaults.isEmpty()) {
            return List.of();
        }
        return rbacService.assignSystemDefaults(employeeId, defaults);
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
     * baseSalary / hourlyRate visible to: self, salary:view/edit holders, or payroll:view/run.
     */
    private Predicate<Long> compensationAccess() {
        Long actor = AuthContext.getEmployeeId();
        if (actor == null) {
            return id -> false;
        }
        DataScopeService.Visibility salaryView = dataScopeService.visibility(actor, "salary", "view");
        DataScopeService.Visibility salaryEdit = dataScopeService.visibility(actor, "salary", "edit");
        DataScopeService.Visibility payrollView = dataScopeService.visibility(actor, "payroll", "view");
        DataScopeService.Visibility payrollRun = dataScopeService.visibility(actor, "payroll", "run");
        return id -> actor.equals(id)
                || salaryView.allows(id)
                || salaryEdit.allows(id)
                || payrollView.allows(id)
                || payrollRun.allows(id);
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

    @Transactional
    public List<Map<String, Object>> reportingLine(Long employeeId) {
        requireScope(employeeId, "employees", "view");
        Employee e = requireEntity(employeeId);
        reportingLineService.syncCurrentManager(e);
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
        if (out.isEmpty() && e.getManagerId() != null) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", null);
            row.put("managerId", e.getManagerId());
            row.put("managerName", repository.findById(e.getManagerId()).map(Employee::getFullName).orElse(null));
            row.put("fromDate", e.getStartDate() == null ? LocalDate.now().toString() : e.getStartDate().toString());
            row.put("toDate", null);
            row.put("changedBy", null);
            out.add(row);
        }
        return out;
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

    private void assertContractEndIfRequired(String employmentType, LocalDate contractEnd, EmployeeRulesService.Rules rules) {
        if (employeeRulesService.requiresContractEnd(employmentType, rules) && contractEnd == null) {
            throw new IllegalArgumentException(
                    "contractEnd is required for employment type " + employmentType);
        }
    }

    private void assertWorkPattern(String pattern, EmployeeRulesService.Rules rules) {
        if (pattern == null || pattern.isBlank()) {
            return;
        }
        boolean ok = rules.workPatterns().stream().anyMatch(p -> p.equalsIgnoreCase(pattern.trim()))
                // legacy aliases still accepted
                || List.of("FIXED", "SHIFT", "FLEXIBLE").stream().anyMatch(p -> p.equalsIgnoreCase(pattern.trim()));
        if (!ok) {
            throw new IllegalArgumentException(
                    "workPattern must be one of: " + String.join(", ", rules.workPatterns()));
        }
    }

    private static String defaultWorkPattern(EmployeeRulesService.Rules rules) {
        return rules.workPatterns().isEmpty() ? "LOCATION_DEFAULT" : rules.workPatterns().get(0);
    }

    private void assertRequiredForActive(EmployeeRequest request, Long departmentId, EmployeeRulesService.Rules rules) {
        for (String field : rules.requiredForActive()) {
            Object val = switch (field) {
                case "fullName" -> request.fullName();
                case "email" -> request.email();
                case "departmentId" -> departmentId;
                case "locationId" -> request.locationId();
                case "startDate" -> request.startDate();
                case "designationId" -> request.designationId();
                case "jobTitle" -> request.jobTitle();
                case "teamId" -> request.teamId();
                case "managerId" -> (request.managerId() != null && request.managerId() > 0)
                        ? request.managerId() : null;
                default -> null;
            };
            if (val == null || String.valueOf(val).isBlank()) {
                throw new IllegalArgumentException(field + " is required before the record is Active");
            }
        }
        if (rules.requireManagerOnCreate()
                && (request.managerId() == null || request.managerId() <= 0)
                && !rules.requiredForActive().stream().anyMatch(f -> "managerId".equalsIgnoreCase(f))) {
            throw new IllegalArgumentException("managerId is required when creating an employee");
        }
    }

    /**
     * Apply first-class personal / photo / DOB fields. On create ({@code create=true}) blank means skip;
     * on update, blank clears the column when the field is present in the request.
     */
    private void applyPersonalColumns(
            Employee employee, EmployeeRequest request, Map<String, Object> jsonb, boolean create) {
        if (request.address() != null) {
            employee.setAddress(blankToNull(request.address()));
        }
        if (request.dateOfBirth() != null) {
            employee.setDateOfBirth(request.dateOfBirth());
        }
        if (request.emergencyName() != null) {
            employee.setEmergencyName(blankToNull(request.emergencyName()));
        }
        String relation = request.resolvedEmergencyRelation();
        if (request.emergencyRelation() != null || request.emergencyRelationship() != null) {
            employee.setEmergencyRelation(relation);
        }
        if (request.emergencyPhone() != null) {
            employee.setEmergencyPhone(blankToNull(request.emergencyPhone()));
        }
        if (request.photoObjectKey() != null) {
            employee.setPhotoObjectKey(blankToNull(request.photoObjectKey()));
        }
        if (request.photoFileId() != null) {
            if (request.photoFileId() <= 0) {
                employee.setPhotoObjectKey(null);
                jsonb.remove("photoFileId");
            } else {
                resolveAndSetPhoto(employee, request.photoFileId(), jsonb);
            }
        }
        syncPersonalCustomMirrors(employee, jsonb);
        if (!create && request.exitDate() == null && request.exitReason() == null) {
            // exit fields are owned by OffboardingService when status changes
        }
    }

    private void resolveAndSetPhoto(Employee employee, Long photoFileId, Map<String, Object> jsonb) {
        filesGatewayClient.assertAttachable(photoFileId);
        Map<String, Object> desc = filesGatewayClient.describe(photoFileId);
        String contentType = desc.get("contentType") == null
                ? "" : String.valueOf(desc.get("contentType")).trim().toLowerCase(Locale.ROOT);
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            throw new IllegalArgumentException(
                    "Employee photo must be JPG or PNG (got '" + contentType + "')");
        }
        Object sizeRaw = desc.get("sizeBytes");
        if (sizeRaw instanceof Number n && n.longValue() > 2L * 1024 * 1024) {
            throw new IllegalArgumentException("Employee photo must be 2 MB or smaller");
        }
        Object classification = desc.get("classification");
        if (classification != null && !"PHOTO".equalsIgnoreCase(String.valueOf(classification).trim())) {
            // Soft preference — still allow GENERAL images that pass type/size for legacy uploads.
            log.debug("Photo fileId={} classification={} (expected PHOTO)", photoFileId, classification);
        }
        Object key = desc.get("storageKey");
        if (key == null || String.valueOf(key).isBlank()) {
            throw new IllegalStateException("File has no storageKey for photoFileId=" + photoFileId);
        }
        employee.setPhotoObjectKey(String.valueOf(key));
        jsonb.put("photoFileId", photoFileId);
        // Edit path has an id already; create path binds after the first save.
        if (employee.getId() != null) {
            filesGatewayClient.bindEmployee(photoFileId, employee.getId());
        }
    }

    private void bindPhotoSubject(Employee employee) {
        if (employee == null || employee.getId() == null || employee.getCustomFields() == null) {
            return;
        }
        Object pf = employee.getCustomFields().get("photoFileId");
        if (pf == null || String.valueOf(pf).isBlank()) {
            return;
        }
        try {
            filesGatewayClient.bindEmployee(Long.valueOf(String.valueOf(pf).trim()), employee.getId());
        } catch (NumberFormatException ignored) {
            // ignore malformed photoFileId in custom fields
        }
    }

    private static void syncPersonalCustomMirrors(Employee employee, Map<String, Object> jsonb) {
        putOrRemove(jsonb, "address", employee.getAddress());
        putOrRemove(jsonb, "emergencyName", employee.getEmergencyName());
        putOrRemove(jsonb, "emergencyRelation", employee.getEmergencyRelation());
        putOrRemove(jsonb, "emergencyRelationship", employee.getEmergencyRelation());
        putOrRemove(jsonb, "emergencyPhone", employee.getEmergencyPhone());
        if (employee.getDateOfBirth() != null) {
            jsonb.put("dateOfBirth", employee.getDateOfBirth().toString());
        }
        if (employee.getPhotoObjectKey() != null) {
            jsonb.put("photoObjectKey", employee.getPhotoObjectKey());
        } else {
            jsonb.remove("photoObjectKey");
        }
    }

    private static void putOrRemove(Map<String, Object> jsonb, String key, String value) {
        if (value == null || value.isBlank()) {
            jsonb.remove(key);
        } else {
            jsonb.put(key, value);
        }
    }

    /** CONTRACTOR is a legacy alias — store CONTRACT only. */
    private static String normalizeEmploymentType(String type) {
        if (type == null || type.isBlank()) {
            return "FULL_TIME";
        }
        String t = type.trim().toUpperCase(Locale.ROOT);
        if ("CONTRACTOR".equals(t)) {
            return "CONTRACT";
        }
        return t;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Map<String, Object> snapshotEmployment(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fullName", e.getFullName());
        m.put("email", e.getEmail());
        m.put("preferredName", e.getPreferredName());
        m.put("jobTitle", e.getJobTitle());
        m.put("departmentId", e.getDepartmentId());
        m.put("designationId", e.getDesignationId());
        m.put("managerId", e.getManagerId());
        m.put("teamId", e.getTeamId());
        m.put("locationId", e.getLocationId());
        m.put("employmentType", e.getEmploymentType());
        m.put("startDate", e.getStartDate());
        m.put("probationEnd", e.getProbationEnd());
        m.put("contractEnd", e.getContractEnd());
        m.put("payCurrency", e.getPayCurrency());
        m.put("workPattern", e.getWorkPattern());
        m.put("baseSalary", e.getBaseSalary());
        m.put("hourlyRate", e.getHourlyRate());
        m.put("status", e.getStatus());
        m.put("phone", e.getPhone());
        m.put("personalEmail", e.getPersonalEmail());
        m.put("address", e.getAddress());
        m.put("dateOfBirth", e.getDateOfBirth());
        m.put("emergencyName", e.getEmergencyName());
        m.put("emergencyRelation", e.getEmergencyRelation());
        m.put("emergencyRelationship", e.getEmergencyRelation());
        m.put("emergencyPhone", e.getEmergencyPhone());
        m.put("photoObjectKey", e.getPhotoObjectKey());
        m.put("exitDate", e.getExitDate());
        m.put("exitReason", e.getExitReason());
        return m;
    }
}
