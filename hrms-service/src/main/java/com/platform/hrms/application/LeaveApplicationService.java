package com.platform.hrms.application;

import com.platform.common.security.AuthContext;
import com.platform.hrms.api.dto.LeaveApplicationResponse;
import com.platform.hrms.api.dto.LeaveApplyRequest;
import com.platform.hrms.api.dto.LeaveDecisionRequest;
import com.platform.hrms.api.dto.ReliefAssignRequest;
import com.platform.hrms.domain.Department;
import com.platform.hrms.domain.Designation;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.domain.ExtIndexedAttribute;
import com.platform.hrms.domain.LeaveApplication;
import com.platform.hrms.domain.LeaveApproval;
import com.platform.hrms.domain.LeaveBlackout;
import com.platform.hrms.domain.LeaveCategory;
import com.platform.hrms.domain.LeavePolicy;
import com.platform.hrms.domain.LeaveType;
import com.platform.hrms.domain.StoreShiftCoverage;
import com.platform.hrms.extension.config.TenantConfigLoader;
import com.platform.hrms.extension.engine.WorkingDaysCalculator;
import com.platform.hrms.infrastructure.persistence.DepartmentRepository;
import com.platform.hrms.infrastructure.persistence.DesignationRepository;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.infrastructure.persistence.ExtIndexedAttributeRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.hrms.infrastructure.persistence.LeaveApprovalRepository;
import com.platform.hrms.infrastructure.persistence.LeaveCategoryRepository;
import com.platform.hrms.infrastructure.persistence.LeaveTypeRepository;
import com.platform.hrms.infrastructure.persistence.StoreShiftCoverageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Config-driven leave engine shared by all tenants.
 * Behavior comes from leave_policy modes + leave_type flags + approval_chain — not tenant if-else.
 */
@Service
public class LeaveApplicationService {

    private final EmployeeRepository employeeRepository;
    private final DesignationRepository designationRepository;
    private final DepartmentRepository departmentRepository;
    private final ExtIndexedAttributeRepository extRepo;
    private final LeaveApplicationRepository applicationRepository;
    private final LeaveApprovalRepository approvalRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveCategoryRepository leaveCategoryRepository;
    private final StoreShiftCoverageRepository coverageRepository;
    private final TenantConfigLoader configLoader;
    private final WorkingDaysCalculator workingDaysCalculator;
    private final LeaveBalanceService leaveBalanceService;
    private final RbacService rbacService;
    private final AuditService auditService;
    private final LeaveTeamClashService teamClashService;
    private final LeaveNotificationHook notificationHook;

    public LeaveApplicationService(
            EmployeeRepository employeeRepository,
            DesignationRepository designationRepository,
            DepartmentRepository departmentRepository,
            ExtIndexedAttributeRepository extRepo,
            LeaveApplicationRepository applicationRepository,
            LeaveApprovalRepository approvalRepository,
            LeaveTypeRepository leaveTypeRepository,
            LeaveCategoryRepository leaveCategoryRepository,
            StoreShiftCoverageRepository coverageRepository,
            TenantConfigLoader configLoader,
            WorkingDaysCalculator workingDaysCalculator,
            LeaveBalanceService leaveBalanceService,
            RbacService rbacService,
            AuditService auditService,
            LeaveTeamClashService teamClashService,
            LeaveNotificationHook notificationHook) {
        this.employeeRepository = employeeRepository;
        this.designationRepository = designationRepository;
        this.departmentRepository = departmentRepository;
        this.extRepo = extRepo;
        this.applicationRepository = applicationRepository;
        this.approvalRepository = approvalRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
        this.coverageRepository = coverageRepository;
        this.configLoader = configLoader;
        this.workingDaysCalculator = workingDaysCalculator;
        this.leaveBalanceService = leaveBalanceService;
        this.rbacService = rbacService;
        this.auditService = auditService;
        this.teamClashService = teamClashService;
        this.notificationHook = notificationHook;
    }

    @Transactional(readOnly = true)
    public List<LeaveApplicationResponse> listAll() {
        return applicationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(a -> LeaveApplicationResponse.from(a, approvalRepository.findByApplicationIdOrderByStepOrderAsc(a.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public LeaveApplicationResponse get(Long id) {
        LeaveApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Leave application not found: " + id));
        return LeaveApplicationResponse.from(app, approvalRepository.findByApplicationIdOrderByStepOrderAsc(id));
    }

    @Transactional
    public LeaveApplicationResponse apply(LeaveApplyRequest request) {
        Long actorId = AuthContext.requireEmployeeId();
        Long targetId = request.employeeId() == null ? actorId : request.employeeId();
        if (!actorId.equals(targetId) && !rbacService.hasPermission(actorId, "leave", "manage")) {
            throw new IllegalStateException("Cannot apply leave for another employee");
        }

        LeavePolicy policy = configLoader.loadLeavePolicyOrDefault();
        Employee employee = employeeRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + targetId));
        if (!employee.isActive()) {
            throw new IllegalStateException("Inactive employee cannot apply leave");
        }
        if (employee.getDepartmentId() == null || employee.getDesignationId() == null) {
            throw new IllegalStateException("Employee must have department and designation before applying leave");
        }

        LeaveType leaveType = resolveLeaveType(request.leaveTypeCode(), policy);
        LeaveCategory category = leaveCategoryRepository.findByCodeAndActiveTrue(leaveType.getCode()).orElse(null);

        List<LocalDate> workingDates = workingDaysCalculator.listWorkingDates(
                request.startDate(), request.endDate(), policy.getWorkingDays());
        if (workingDates.isEmpty()) {
            throw new IllegalArgumentException(
                    "No working days in range for tenant working_days=" + policy.getWorkingDays());
        }
        BigDecimal daysCounted = BigDecimal.valueOf(workingDates.size());
        if (Boolean.TRUE.equals(request.halfDay())) {
            if (category != null && !category.isHalfDayAllowed()) {
                throw new IllegalStateException("Half-day leave not allowed for category " + leaveType.getCode());
            }
            daysCounted = new BigDecimal("0.5");
        }
        if (category != null && category.getMaxPerRequest() > 0
                && daysCounted.compareTo(BigDecimal.valueOf(category.getMaxPerRequest())) > 0) {
            throw new IllegalStateException(
                    "Request exceeds max_per_request=" + category.getMaxPerRequest()
                            + " for category " + category.getCode());
        }

        boolean unionMember = extRepo.findById(new ExtIndexedAttribute.Pk(employee.getId(), "union_member_id")).isPresent();
        int noticeRequired = resolveNoticeRequired(policy, unionMember);
        if (category != null && category.getNoticeDays() > noticeRequired) {
            noticeRequired = category.getNoticeDays();
        }
        int noticeGiven = (int) ChronoUnit.DAYS.between(LocalDate.now(), request.startDate());
        if (noticeRequired > 0 && noticeGiven < noticeRequired) {
            throw new IllegalStateException(
                    "Notice too short: given=" + noticeGiven + " days, required=" + noticeRequired
                            + " (notice_mode=" + policy.getNoticeMode() + ")");
        }

        teamClashService.enforce(employee, request.startDate(), request.endDate());

        String location = stringField(employee, policy.getLocationField());
        String shift = stringField(employee, policy.getShiftField());

        List<LeaveBlackout> blackouts = configLoader.overlappingBlackouts(location, request.startDate(), request.endDate());
        if (!blackouts.isEmpty()) {
            throw new IllegalStateException("Leave overlaps blackout: " + blackouts.get(0).getReason());
        }

        int year = request.startDate().getYear();
        BigDecimal available = leaveBalanceService.available(employee.getId(), leaveType.getCode(), year);
        if (leaveType.isRequiresBalance() && !leaveType.isAllowWhenZeroBalance()
                && available.compareTo(daysCounted) < 0) {
            throw new IllegalStateException(
                    "Insufficient " + leaveType.getCode() + " balance: available=" + available
                            + ", requested=" + daysCounted);
        }

        Map<String, Object> coverageSnapshot = new HashMap<>();
        boolean coverageOk = true;
        boolean needsRelief = false;
        boolean runCoverage = policy.isRequireCoverageCheck()
                && !LeavePolicy.COVERAGE_NONE.equalsIgnoreCase(policy.getCoverageMode());

        if (runCoverage && location != null) {
            int minStaff = resolveMinStaff(policy, location, shift);
            List<Map<String, Object>> dayReports = new ArrayList<>();
            for (LocalDate day : workingDates) {
                int onDuty = countOnDuty(policy, location, shift, day, employee.getId());
                int afterLeave = onDuty - 1;
                boolean dayOk = afterLeave >= minStaff;
                if (!dayOk) {
                    coverageOk = false;
                }
                dayReports.add(Map.of(
                        "date", day.toString(),
                        "onDutyBefore", onDuty,
                        "afterApplicantAbsent", afterLeave,
                        "minStaff", minStaff,
                        "ok", dayOk
                ));
            }
            coverageSnapshot.put("days", dayReports);
            coverageSnapshot.put("minStaff", minStaff);
            coverageSnapshot.put("coverageMode", policy.getCoverageMode());

            if (!coverageOk) {
                if (leaveType.isAllowWhenCoverageFail()) {
                    coverageSnapshot.put("allowedByLeaveType", leaveType.getCode());
                } else if (policy.isRequireReliefOnCoverageFail()) {
                    needsRelief = true;
                } else {
                    throw new IllegalStateException(
                            "Coverage would fall below min staff; leave type "
                                    + leaveType.getCode() + " is not allowed when coverage fails");
                }
            }
        } else {
            coverageSnapshot.put("skipped", true);
        }

        if (needsRelief && request.reliefEmployeeId() != null) {
            validateRelief(request.reliefEmployeeId(), location, shift, employee.getId(), policy);
            coverageOk = true;
            needsRelief = false;
        }

        Instant now = Instant.now();
        LeaveApplication app = new LeaveApplication();
        app.setEmployeeId(employee.getId());
        app.setLeaveTypeCode(leaveType.getCode());
        app.setPaid(leaveType.isPaid());
        app.setStartDate(request.startDate());
        app.setEndDate(request.endDate());
        app.setWorkingDaysCounted(daysCounted);
        app.setReason(request.reason());
        app.setUnionMember(unionMember);
        app.setNoticeDaysGiven(noticeGiven);
        app.setNoticeDaysRequired(noticeRequired);
        app.setCoverageOk(coverageOk || leaveType.isAllowWhenCoverageFail());
        app.setNeedsRelief(needsRelief && request.reliefEmployeeId() == null);
        app.setReliefEmployeeId(request.reliefEmployeeId());
        app.setStoreId(location);
        app.setShiftPattern(shift);
        app.setCoverageSnapshot(coverageSnapshot);
        app.setCurrentStep(1);
        app.setCreatedAt(now);
        app.setUpdatedAt(now);

        if (app.isNeedsRelief()) {
            app.setStatus(LeaveApplication.STATUS_PENDING_RELIEF);
        } else {
            app.setStatus(LeaveApplication.STATUS_PENDING_APPROVAL);
        }

        LeaveApplication saved = applicationRepository.save(app);
        createApprovalSteps(saved, policy);
        auditService.record(actorId, "CREATE", "LeaveApplication", String.valueOf(saved.getId()), null, Map.of(
                "employeeId", saved.getEmployeeId(),
                "leaveTypeCode", saved.getLeaveTypeCode(),
                "startDate", saved.getStartDate().toString(),
                "endDate", saved.getEndDate().toString(),
                "status", saved.getStatus()
        ));
        return LeaveApplicationResponse.from(
                saved, approvalRepository.findByApplicationIdOrderByStepOrderAsc(saved.getId()));
    }

    @Transactional
    public LeaveApplicationResponse assignRelief(Long applicationId, ReliefAssignRequest request) {
        LeaveApplication app = requireApp(applicationId);
        if (!LeaveApplication.STATUS_PENDING_RELIEF.equals(app.getStatus())) {
            throw new IllegalStateException("Relief only allowed when status=PENDING_RELIEF");
        }
        LeavePolicy policy = configLoader.loadLeavePolicyOrDefault();
        validateRelief(request.reliefEmployeeId(), app.getStoreId(), app.getShiftPattern(), app.getEmployeeId(), policy);
        app.setReliefEmployeeId(request.reliefEmployeeId());
        app.setNeedsRelief(false);
        app.setCoverageOk(true);
        app.setStatus(LeaveApplication.STATUS_PENDING_APPROVAL);
        app.setUpdatedAt(Instant.now());
        applicationRepository.save(app);
        return get(applicationId);
    }

    @Transactional
    public LeaveApplicationResponse approve(Long applicationId, LeaveDecisionRequest request) {
        LeaveApplication app = requireApp(applicationId);
        if (LeaveApplication.STATUS_PENDING_RELIEF.equals(app.getStatus())) {
            throw new IllegalStateException("Assign relief first (or submit with reliefEmployeeId)");
        }
        if (!LeaveApplication.STATUS_PENDING_APPROVAL.equals(app.getStatus())
                && !LeaveApplication.STATUS_SUBMITTED.equals(app.getStatus())) {
            throw new IllegalStateException("Cannot approve status=" + app.getStatus());
        }

        LeaveApproval step = approvalRepository.findByApplicationIdAndStepOrder(applicationId, app.getCurrentStep())
                .orElseThrow(() -> new IllegalStateException("Missing approval step " + app.getCurrentStep()));
        if (!LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
            throw new IllegalStateException("Current step already decided");
        }

        Employee approver = requireActiveApprover(request.approverEmployeeId(), step, app);

        Instant now = Instant.now();
        step.setStatus(LeaveApproval.STATUS_APPROVED);
        step.setApproverEmployeeId(approver.getId());
        step.setActorName(approver.getFullName());
        step.setCommentText(request.comment());
        step.setDecidedAt(now);
        approvalRepository.save(step);

        List<LeaveApproval> all = approvalRepository.findByApplicationIdOrderByStepOrderAsc(applicationId);
        boolean more = all.stream().anyMatch(a -> a.getStepOrder() > app.getCurrentStep()
                && LeaveApproval.STATUS_PENDING.equals(a.getStatus()));
        if (more) {
            app.setCurrentStep(app.getCurrentStep() + 1);
            app.setStatus(LeaveApplication.STATUS_PENDING_APPROVAL);
        } else {
            app.setStatus(LeaveApplication.STATUS_APPROVED);
            deductIfNeeded(app);
        }
        app.setUpdatedAt(now);
        applicationRepository.save(app);
        notificationHook.notifyLeaveDecision(
                app.getEmployeeId(), applicationId, app.getStatus(), "Leave " + app.getStatus());
        return get(applicationId);
    }

    @Transactional
    public LeaveApplicationResponse reject(Long applicationId, LeaveDecisionRequest request) {
        LeaveApplication app = requireApp(applicationId);
        if (LeaveApplication.STATUS_APPROVED.equals(app.getStatus())
                || LeaveApplication.STATUS_REJECTED.equals(app.getStatus())) {
            throw new IllegalStateException("Already final: " + app.getStatus());
        }
        LeaveApproval step = approvalRepository.findByApplicationIdAndStepOrder(applicationId, Math.max(1, app.getCurrentStep()))
                .orElse(null);

        Employee approver;
        if (step != null && LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
            approver = requireActiveApprover(request.approverEmployeeId(), step, app);
        } else {
            approver = requireActiveEmployee(request.approverEmployeeId());
        }

        Instant now = Instant.now();
        if (step != null && LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
            step.setStatus(LeaveApproval.STATUS_REJECTED);
            step.setApproverEmployeeId(approver.getId());
            step.setActorName(approver.getFullName());
            step.setCommentText(request.comment());
            step.setDecidedAt(now);
            approvalRepository.save(step);
        }
        app.setStatus(LeaveApplication.STATUS_REJECTED);
        app.setRejectReason(request.comment());
        app.setUpdatedAt(now);
        applicationRepository.save(app);
        notificationHook.notifyLeaveDecision(
                app.getEmployeeId(), applicationId, LeaveApplication.STATUS_REJECTED, "Leave rejected");
        return get(applicationId);
    }

    private void deductIfNeeded(LeaveApplication app) {
        if (app.isBalanceDeducted() || app.getLeaveTypeCode() == null) {
            return;
        }
        LeaveType type = leaveTypeRepository.findByCodeAndActiveTrue(app.getLeaveTypeCode()).orElse(null);
        if (type == null || !type.isDeductBalance()) {
            return;
        }
        leaveBalanceService.deductOnApprove(
                app.getEmployeeId(),
                app.getLeaveTypeCode(),
                app.getStartDate().getYear(),
                app.getWorkingDaysCounted());
        app.setBalanceDeducted(true);
    }

    private LeaveType resolveLeaveType(String requestedCode, LeavePolicy policy) {
        String code = requestedCode;
        if (code == null || code.isBlank()) {
            code = policy.getDefaultLeaveTypeCode();
        }
        if (code == null || code.isBlank()) {
            List<LeaveType> active = leaveTypeRepository.findByActiveTrueOrderBySortOrderAsc();
            if (active.size() == 1) {
                return active.getFirst();
            }
            throw new IllegalArgumentException("leaveTypeCode is required (tenant has multiple leave types)");
        }
        final String resolved = code;
        return leaveTypeRepository.findByCodeAndActiveTrue(resolved)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive leave type: " + resolved));
    }

    private int resolveNoticeRequired(LeavePolicy policy, boolean unionMember) {
        String mode = policy.getNoticeMode();
        if (LeavePolicy.NOTICE_NONE.equalsIgnoreCase(mode)) {
            return 0;
        }
        if (LeavePolicy.NOTICE_FIXED.equalsIgnoreCase(mode)) {
            return policy.getDefaultNoticeDays();
        }
        // ATTRIBUTE = TechShop-style union / non-union columns
        return unionMember ? policy.getUnionNoticeDays() : policy.getNonUnionNoticeDays();
    }

    private int resolveMinStaff(LeavePolicy policy, String location, String shift) {
        if (LeavePolicy.COVERAGE_LOCATION.equalsIgnoreCase(policy.getCoverageMode())) {
            return coverageRepository.findFirstByStoreId(location)
                    .map(StoreShiftCoverage::getMinStaff)
                    .orElse(1);
        }
        return configLoader.findCoverage(location, shift)
                .map(StoreShiftCoverage::getMinStaff)
                .orElse(1);
    }

    private Employee requireActiveApprover(Long approverEmployeeId, LeaveApproval step, LeaveApplication app) {
        Employee approver = requireActiveEmployee(approverEmployeeId);
        String requiredRole = step.getApproverRole() == null ? "" : step.getApproverRole().toUpperCase(Locale.ROOT);

        if ("MANAGER".equals(requiredRole) || "STORE_MANAGER".equals(requiredRole)) {
            Employee applicant = employeeRepository.findById(app.getEmployeeId())
                    .orElseThrow(() -> new IllegalStateException("Applicant missing"));
            boolean isLineManager = applicant.getManagerId() != null
                    && applicant.getManagerId().equals(approver.getId());
            boolean isAdminOverride = isAdminOrSuperAdmin(approver);
            boolean designationMatches = designationApproverRole(approver)
                    .map(r -> r.equalsIgnoreCase(requiredRole) || "STORE_MANAGER".equalsIgnoreCase(r))
                    .orElse(false);

            if ("MANAGER".equals(requiredRole)) {
                if (!isLineManager && !isAdminOverride) {
                    throw new IllegalStateException(
                            "MANAGER step requires applicant's manager_id or ADMIN/SUPER_ADMIN. "
                                    + "managerId=" + applicant.getManagerId() + ", approverId=" + approver.getId());
                }
                return approver;
            }
            // STORE_MANAGER: line manager with matching designation, or admin override
            if (isAdminOverride) {
                return approver;
            }
            if (!isLineManager || !designationMatches) {
                throw new IllegalStateException(
                        "STORE_MANAGER step requires applicant's manager with matching designation (or ADMIN). "
                                + "managerId=" + applicant.getManagerId());
            }
            return approver;
        }

        if ("ADMIN".equals(requiredRole) || "SUPER_ADMIN".equals(requiredRole)) {
            if (!isAdminOrSuperAdmin(approver)) {
                throw new IllegalStateException("Step requires ADMIN/SUPER_ADMIN authority");
            }
            return approver;
        }

        // HR / REGIONAL_HR / any other role → designation.approver_role must match
        String desigRole = designationApproverRole(approver).orElse(null);
        if (desigRole == null || !desigRole.equalsIgnoreCase(requiredRole)) {
            throw new IllegalStateException(
                    "Step requires role=" + requiredRole
                            + " but approver has approver_role=" + desigRole);
        }
        return approver;
    }

    private boolean isAdminOrSuperAdmin(Employee employee) {
        String role = designationApproverRole(employee).orElse("");
        if ("ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role)) {
            return true;
        }
        if (employee.getDepartmentId() == null) {
            return false;
        }
        return departmentRepository.findById(employee.getDepartmentId())
                .map(Department::getCode)
                .map(c -> "ADMIN".equalsIgnoreCase(c) || "SUPER_ADMIN".equalsIgnoreCase(c))
                .orElse(false);
    }

    private java.util.Optional<String> designationApproverRole(Employee employee) {
        if (employee.getDesignationId() == null) {
            return java.util.Optional.empty();
        }
        return designationRepository.findById(employee.getDesignationId())
                .map(Designation::getApproverRole)
                .filter(r -> r != null && !r.isBlank());
    }

    private Employee requireActiveEmployee(Long id) {
        Employee e = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Approver employee not found: " + id));
        if (!e.isActive()) {
            throw new IllegalStateException("Approver is inactive");
        }
        return e;
    }

    private void createApprovalSteps(LeaveApplication app, LeavePolicy policy) {
        List<Map<String, Object>> chain = policy.getApprovalChain();
        if (chain == null || chain.isEmpty()) {
            chain = List.of(Map.of("step", 1, "role", "HR"));
        }
        for (Map<String, Object> item : chain) {
            LeaveApproval a = new LeaveApproval();
            a.setApplicationId(app.getId());
            a.setStepOrder(((Number) item.get("step")).intValue());
            a.setApproverRole(String.valueOf(item.get("role")));
            a.setStatus(LeaveApproval.STATUS_PENDING);
            approvalRepository.save(a);
        }
    }

    private int countOnDuty(LeavePolicy policy, String location, String shift, LocalDate day, Long excludeId) {
        boolean matchShift = LeavePolicy.COVERAGE_LOCATION_SHIFT.equalsIgnoreCase(policy.getCoverageMode());
        List<Employee> sameBucket = employeeRepository.findAll().stream()
                .filter(Employee::isActive)
                .filter(e -> location.equals(stringField(e, policy.getLocationField())))
                .filter(e -> !matchShift || shift == null || shift.equals(stringField(e, policy.getShiftField())))
                .toList();
        Set<Long> away = new HashSet<>();
        for (LeaveApplication blocking : applicationRepository.findBlockingLeaveOnDay(location, shift, day, excludeId)) {
            away.add(blocking.getEmployeeId());
        }
        int count = 0;
        for (Employee e : sameBucket) {
            if (!away.contains(e.getId())) {
                count++;
            }
        }
        return count;
    }

    private void validateRelief(Long reliefId, String location, String shift, Long applicantId, LeavePolicy policy) {
        if (reliefId.equals(applicantId)) {
            throw new IllegalArgumentException("Relief cannot be the same employee");
        }
        Employee relief = employeeRepository.findById(reliefId)
                .orElseThrow(() -> new IllegalArgumentException("Relief employee not found: " + reliefId));
        if (!relief.isActive()) {
            throw new IllegalStateException("Relief employee inactive");
        }
        if (LeavePolicy.COVERAGE_LOCATION_SHIFT.equalsIgnoreCase(policy.getCoverageMode())
                && shift != null) {
            String reliefShift = stringField(relief, policy.getShiftField());
            if (reliefShift != null && !shift.equals(reliefShift)) {
                throw new IllegalStateException("Relief should match " + policy.getShiftField() + "=" + shift);
            }
        }
    }

    private LeaveApplication requireApp(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Leave application not found: " + id));
    }

    private String stringField(Employee employee, String key) {
        if (key == null) {
            return null;
        }
        Object v = employee.getCustomFields() == null ? null : employee.getCustomFields().get(key);
        return v == null ? null : String.valueOf(v);
    }
}
