package com.platform.hrms.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.security.AuthContext;
import com.platform.hrms.api.dto.LeaveApplicationResponse;
import com.platform.hrms.api.dto.LeaveApplyRequest;
import com.platform.hrms.api.dto.LeaveDecisionRequest;
import com.platform.hrms.api.dto.ReliefAssignRequest;
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
import java.util.LinkedHashMap;
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
    private final DataScopeService dataScopeService;
    private final ApprovalAuthorityService approvalAuthorityService;
    private final WorkScheduleService workScheduleService;

    public LeaveApplicationService(
            EmployeeRepository employeeRepository,
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
            LeaveNotificationHook notificationHook,
            DataScopeService dataScopeService,
            ApprovalAuthorityService approvalAuthorityService,
            WorkScheduleService workScheduleService) {
        this.employeeRepository = employeeRepository;
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
        this.dataScopeService = dataScopeService;
        this.approvalAuthorityService = approvalAuthorityService;
        this.workScheduleService = workScheduleService;
    }

    /** Unscoped (internal use only). HTTP callers must use {@link #listForCaller()}. */
    @Transactional(readOnly = true)
    public List<LeaveApplicationResponse> listAll() {
        return applicationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(a -> LeaveApplicationResponse.from(a, approvalRepository.findByApplicationIdOrderByStepOrderAsc(a.getId())))
                .toList();
    }

    /** leave:view data scope: OWN = own requests, TEAM = self + direct reports, ALL = everyone. */
    @Transactional(readOnly = true)
    public List<LeaveApplicationResponse> listForCaller() {
        DataScopeService.Visibility visible = dataScopeService.visibility(
                AuthContext.requireEmployeeId(), "leave", "view");
        return applicationRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> visible.allows(a.getEmployeeId()))
                .map(a -> LeaveApplicationResponse.from(a, approvalRepository.findByApplicationIdOrderByStepOrderAsc(a.getId())))
                .toList();
    }

    /**
     * Pending approvals the caller can actually decide now: data scope + current-step authority.
     * HR with leave:approve does not see manager-step items until the reporting manager has acted.
     */
    @Transactional(readOnly = true)
    public List<LeaveApplicationResponse> pendingForCaller() {
        Long actorId = AuthContext.requireEmployeeId();
        DataScopeService.Visibility visible = dataScopeService.visibility(actorId, "leave", "approve");
        return applicationRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> {
                    String s = a.getStatus() == null ? "" : a.getStatus().toUpperCase(Locale.ROOT);
                    return LeaveApplication.STATUS_PENDING_APPROVAL.equals(s)
                            || "SUBMITTED".equals(s)
                            || "IN_REVIEW".equals(s);
                })
                .filter(a -> visible.allows(a.getEmployeeId()))
                .filter(a -> {
                    LeaveApproval step = approvalRepository
                            .findByApplicationIdAndStepOrder(a.getId(), Math.max(1, a.getCurrentStep()))
                            .orElse(null);
                    return step != null
                            && LeaveApproval.STATUS_PENDING.equals(step.getStatus())
                            && approvalAuthorityService.canApprove(actorId, step, a);
                })
                .map(a -> LeaveApplicationResponse.from(a, approvalRepository.findByApplicationIdOrderByStepOrderAsc(a.getId())))
                .toList();
    }

    /** Applications of one employee (leave:view scope enforced → 403 when out of scope). */
    @Transactional(readOnly = true)
    public List<LeaveApplicationResponse> listForEmployee(Long employeeId) {
        dataScopeService.requireAccess(AuthContext.requireEmployeeId(), employeeId, "leave", "view");
        return applicationRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(a -> LeaveApplicationResponse.from(a, approvalRepository.findByApplicationIdOrderByStepOrderAsc(a.getId())))
                .toList();
    }

    /** Single application with leave:view scope enforced (owner of the request is the scope target). */
    @Transactional(readOnly = true)
    public LeaveApplicationResponse getForCaller(Long id) {
        LeaveApplication app = applicationRepository.findById(id).orElse(null);
        Long actor = AuthContext.requireEmployeeId();
        if (app == null || !dataScopeService.canAccess(actor, app.getEmployeeId(), "leave", "view")) {
            // Same answer for "missing" and "not yours" so ids cannot be probed.
            throw new SecurityException("Forbidden: leave application " + id + " is not visible to you");
        }
        return LeaveApplicationResponse.from(app, approvalRepository.findByApplicationIdOrderByStepOrderAsc(id));
    }

    @Transactional(readOnly = true)
    public LeaveApplicationResponse get(Long id) {
        LeaveApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Leave application not found: " + id));
        return LeaveApplicationResponse.from(app, approvalRepository.findByApplicationIdOrderByStepOrderAsc(id));
    }

    @Transactional
    public LeaveApplicationResponse apply(LeaveApplyRequest request) {
        return doApply(request, false);
    }

    /** FR-LV-09 — preview: runs all validations but does not persist anything. */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(LeaveApplyRequest request) {
        // We call doApply with previewOnly=true; it will throw before save and return data.
        // Instead we call buildApplyContext to get everything without saving.
        ApplyContext ctx = buildApplyContext(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workingDaysCounted", ctx.daysCounted());
        BigDecimal balance = leaveBalanceService.available(ctx.employee().getId(), ctx.leaveType().getCode(), ctx.year());
        BigDecimal afterPending = leaveBalanceService.availableAfterPending(ctx.employee().getId(), ctx.leaveType().getCode(), ctx.year());
        result.put("available", afterPending);
        result.put("pendingDays", balance.subtract(afterPending));
        result.put("balanceAfter", afterPending.subtract(ctx.daysCounted()));
        result.put("warnings", ctx.warnings());
        result.put("teamClashAlert", ctx.teamClashAlert());

        // Approver preview
        List<Map<String, Object>> policy_chain = resolveApprovalChain(ctx.category(), ctx.policy());
        List<Map<String, Object>> approvers = new ArrayList<>();
        for (Map<String, Object> step : policy_chain) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("role", step.get("role"));
            a.put("resolver", step.get("resolver"));
            a.put("step", step.get("step"));
            Long expectedId = resolveExpectedApprover(ctx.employee(), step);
            a.put("expectedEmployeeId", expectedId);
            if (expectedId != null) {
                a.put("expectedName", employeeRepository.findById(expectedId).map(Employee::getFullName).orElse(null));
            }
            approvers.add(a);
        }
        result.put("approvers", approvers);
        return result;
    }

    private LeaveApplicationResponse doApply(LeaveApplyRequest request, boolean previewOnly) {
        Long actorId = AuthContext.requireEmployeeId();
        Long targetId = request.employeeId() == null ? actorId : request.employeeId();
        if (!actorId.equals(targetId) && !rbacService.hasPermission(actorId, "leave", "manage")) {
            throw new IllegalStateException("Cannot apply leave for another employee");
        }

        ApplyContext ctx = buildApplyContext(request);
        Employee employee = ctx.employee();
        LeaveType leaveType = ctx.leaveType();
        LeaveCategory category = ctx.category();
        LeavePolicy policy = ctx.policy();
        BigDecimal daysCounted = ctx.daysCounted();
        List<LocalDate> workingDates = ctx.workingDates();
        String location = ctx.location();
        String shift = ctx.shift();
        Map<String, Object> warnings = ctx.warnings();
        boolean teamClashAlert = ctx.teamClashAlert();
        int year = ctx.year();

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

        List<Map<String, Object>> chain = resolveApprovalChain(category, policy);
        assertReportingManagerPresent(employee, chain);

        // Resolve half-day part
        String halfDayPart = resolveHalfDayPart(request);

        Instant now = Instant.now();
        LeaveApplication app = new LeaveApplication();
        app.setEmployeeId(employee.getId());
        app.setLeaveTypeCode(leaveType.getCode());
        app.setPaid(leaveType.isPaid());
        app.setStartDate(request.startDate());
        app.setEndDate(request.endDate());
        app.setWorkingDaysCounted(daysCounted);
        app.setReason(request.reason());
        app.setUnionMember(ctx.unionMember());
        app.setNoticeDaysGiven(ctx.noticeGiven());
        app.setNoticeDaysRequired(ctx.noticeRequired());
        app.setCoverageOk(coverageOk || leaveType.isAllowWhenCoverageFail());
        app.setNeedsRelief(needsRelief && request.reliefEmployeeId() == null);
        app.setReliefEmployeeId(request.reliefEmployeeId());
        app.setStoreId(location);
        app.setShiftPattern(shift);
        app.setCoverageSnapshot(coverageSnapshot);
        app.setCurrentStep(1);
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        app.setHalfDayPart(halfDayPart);
        app.setAttachmentRef(request.attachmentRef());
        app.setTeamClashAlert(teamClashAlert);
        app.setWarnings(warnings);

        if (app.isNeedsRelief()) {
            app.setStatus(LeaveApplication.STATUS_PENDING_RELIEF);
        } else {
            app.setStatus(LeaveApplication.STATUS_PENDING_APPROVAL);
        }

        LeaveApplication saved = applicationRepository.save(app);
        createApprovalSteps(saved, chain, employee);
        auditService.record(actorId, "CREATE", "LeaveApplication", String.valueOf(saved.getId()), null, Map.of(
                "employeeId", saved.getEmployeeId(),
                "leaveTypeCode", saved.getLeaveTypeCode(),
                "startDate", saved.getStartDate().toString(),
                "endDate", saved.getEndDate().toString(),
                "status", saved.getStatus()
        ));
        if (teamClashAlert) {
            notificationHook.notifyTeamClash(employee.getId(), saved.getId(),
                    warnings.getOrDefault("teamClash", "Team clash alert").toString());
        }
        return LeaveApplicationResponse.from(
                saved, approvalRepository.findByApplicationIdOrderByStepOrderAsc(saved.getId()));
    }

    /** Validates and builds context for apply/preview without persisting. */
    private ApplyContext buildApplyContext(LeaveApplyRequest request) {
        Long actorId = AuthContext.requireEmployeeId();
        Long targetId = request.employeeId() == null ? actorId : request.employeeId();

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

        // FR-LV-01: applies_to eligibility
        if (category != null && category.getAppliesTo() != null && !category.getAppliesTo().isBlank()) {
            checkAppliesTo(employee, category);
        }

        // FR-LV-03: refuse day-based leave for HOURLY employees
        workScheduleService.resolveForEmployee(employee).ifPresent(schedule -> {
            String mode = schedule.getMode() == null ? "" : schedule.getMode().toUpperCase(Locale.ROOT);
            if ("HOURLY".equals(mode)) {
                throw new IllegalStateException(
                        "Employee schedule is HOURLY; day-based leave categories are not supported");
            }
        });

        // FR-LV-11: period eligibility
        if (category != null) {
            LocalDate now = LocalDate.now();
            if (category.getPeriodStart() != null && request.startDate().isBefore(category.getPeriodStart())) {
                throw new IllegalStateException("Leave start is before category period start " + category.getPeriodStart());
            }
            if (category.getPeriodEnd() != null && request.endDate().isAfter(category.getPeriodEnd())) {
                throw new IllegalStateException("Leave end is after category period end " + category.getPeriodEnd());
            }
        }

        // FR-SCH-03 — count against the person's resolved schedule (5-day and 6-day can coexist).
        List<String> personWorkingDays = workScheduleService.workingDayCodesForEmployee(employee);
        if (personWorkingDays == null || personWorkingDays.isEmpty()) {
            personWorkingDays = policy.getWorkingDays();
        }

        // FR-LV-10: build holiday set for employee's location
        Set<LocalDate> locationHolidays = buildHolidaySet(employee.getLocationId(),
                request.startDate(), request.endDate());

        List<LocalDate> workingDates = workingDaysCalculator.listWorkingDates(
                request.startDate(), request.endDate(), personWorkingDays, locationHolidays);

        // FR-LV-08: halfDay handling
        String halfDayPart = resolveHalfDayPart(request);
        BigDecimal daysCounted;
        if (!"FULL".equals(halfDayPart)) {
            if (category != null && !category.isHalfDayAllowed()) {
                throw new IllegalStateException("Half-day leave not allowed for category " + leaveType.getCode());
            }
            if (!request.startDate().equals(request.endDate())) {
                throw new IllegalStateException("Half-day leave must be a single date (startDate == endDate)");
            }
            daysCounted = new BigDecimal("0.5");
        } else if (Boolean.TRUE.equals(request.halfDay())) {
            if (category != null && !category.isHalfDayAllowed()) {
                throw new IllegalStateException("Half-day leave not allowed for category " + leaveType.getCode());
            }
            daysCounted = new BigDecimal("0.5");
        } else {
            if (workingDates.isEmpty()) {
                throw new IllegalArgumentException(
                        "No working days in range for schedule working_days=" + personWorkingDays);
            }
            daysCounted = BigDecimal.valueOf(workingDates.size());
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

        // FR-LV-11: overlap check
        List<?> overlapping = applicationRepository.findOverlappingForEmployee(
                employee.getId(), request.startDate(), request.endDate(), null);
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException("Leave overlaps with an existing PENDING or APPROVED leave application");
        }

        // FR-LV-11: attachment check
        if (category != null && category.getAttachmentMode() != null) {
            switch (category.getAttachmentMode().toUpperCase(Locale.ROOT)) {
                case "ALWAYS" -> {
                    if (request.attachmentRef() == null || request.attachmentRef().isBlank()) {
                        throw new IllegalStateException("Attachment required for category " + category.getCode());
                    }
                }
                case "AFTER_N" -> {
                    if (daysCounted.compareTo(BigDecimal.valueOf(category.getAttachmentAfterDays())) > 0
                            && (request.attachmentRef() == null || request.attachmentRef().isBlank())) {
                        throw new IllegalStateException("Attachment required when leave exceeds "
                                + category.getAttachmentAfterDays() + " days for category " + category.getCode());
                    }
                }
                default -> { /* NEVER — no check */ }
            }
        }

        // FR-LV-14: team clash
        Map<String, Object> warnings = new HashMap<>();
        boolean teamClashAlert = false;
        LeaveTeamClashService.ClashResult clashResult = teamClashService.check(employee, request.startDate(), request.endDate());
        if (clashResult.blocked()) {
            throw new IllegalStateException(clashResult.message());
        }
        if (clashResult.alert()) {
            teamClashAlert = true;
            warnings.put("teamClash", clashResult.message());
        }

        String location = stringField(employee, policy.getLocationField());
        String shift = stringField(employee, policy.getShiftField());

        List<LeaveBlackout> blackouts = configLoader.overlappingBlackouts(location, request.startDate(), request.endDate());
        if (!blackouts.isEmpty()) {
            throw new IllegalStateException("Leave overlaps blackout: " + blackouts.get(0).getReason());
        }

        int year = request.startDate().getYear();
        // FR-LV-11: use availableAfterPending for balance check; negativeCap
        BigDecimal availableAfterPending = leaveBalanceService.availableAfterPending(employee.getId(), leaveType.getCode(), year);
        if (leaveType.isRequiresBalance() && !leaveType.isAllowWhenZeroBalance()) {
            BigDecimal effectiveAvailable = availableAfterPending;
            if (category != null && category.getNegativeCap() != null) {
                effectiveAvailable = availableAfterPending.add(category.getNegativeCap());
            }
            if (effectiveAvailable.compareTo(daysCounted) < 0) {
                throw new IllegalStateException(
                        "Insufficient " + leaveType.getCode() + " balance: available=" + availableAfterPending
                                + ", requested=" + daysCounted);
            }
        }

        return new ApplyContext(employee, leaveType, category, policy, daysCounted, workingDates,
                location, shift, warnings, teamClashAlert, year, unionMember, noticeGiven, noticeRequired);
    }

    private record ApplyContext(
            Employee employee,
            LeaveType leaveType,
            LeaveCategory category,
            LeavePolicy policy,
            BigDecimal daysCounted,
            List<LocalDate> workingDates,
            String location,
            String shift,
            Map<String, Object> warnings,
            boolean teamClashAlert,
            int year,
            boolean unionMember,
            int noticeGiven,
            int noticeRequired
    ) {}

    /**
     * FR-LV-18 — request cancellation of an APPROVED leave; starts a cancel approval chain.
     */
    @Transactional
    public LeaveApplicationResponse requestCancelApproved(Long applicationId) {
        Long actorId = AuthContext.requireEmployeeId();
        LeaveApplication app = requireApp(applicationId);
        if (!actorId.equals(app.getEmployeeId()) && !rbacService.hasPermission(actorId, "leave", "manage")) {
            throw new IllegalStateException("Forbidden: only the requester or leave:manage can request cancel");
        }
        if (!LeaveApplication.STATUS_APPROVED.equals(app.getStatus())) {
            throw new IllegalStateException("Cancel-request only allowed for APPROVED leaves; status=" + app.getStatus());
        }
        // Wipe prior approval steps so cancel chain step_order 1..n is unambiguous (FR-LV-18).
        approvalRepository.findByApplicationIdOrderByStepOrderAsc(applicationId)
                .forEach(approvalRepository::delete);

        app.setStatus(LeaveApplication.STATUS_PENDING_CANCEL);
        app.setCurrentStep(1);
        app.setUpdatedAt(Instant.now());
        applicationRepository.save(app);

        LeavePolicy policy = configLoader.loadLeavePolicyOrDefault();
        LeaveCategory category = app.getLeaveTypeCode() != null
                ? leaveCategoryRepository.findByCodeAndActiveTrue(app.getLeaveTypeCode()).orElse(null) : null;
        Employee employee = employeeRepository.findById(app.getEmployeeId()).orElse(null);
        List<Map<String, Object>> chain = resolveApprovalChain(category, policy);
        createApprovalSteps(app, chain, employee);

        auditService.record(actorId, "CANCEL_REQUEST", "LeaveApplication", String.valueOf(applicationId),
                Map.of("status", LeaveApplication.STATUS_APPROVED),
                Map.of("status", LeaveApplication.STATUS_PENDING_CANCEL));
        return get(applicationId);
    }

    /**
     * FR-LV-01 — applies_to eligibility: employee must match at least one non-empty list.
     * Empty object / all empty lists = everyone eligible.
     */
    @SuppressWarnings("unchecked")
    private void checkAppliesTo(Employee employee, LeaveCategory category) {
        String raw = category.getAppliesTo();
        if (raw == null || raw.isBlank()) {
            return;
        }
        Map<String, Object> appliesTo;
        try {
            appliesTo = new ObjectMapper().readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            return; // invalid JSON → no restriction
        }
        List<Object> locations = asList(appliesTo.get("locations"));
        List<Object> employmentTypes = asList(appliesTo.get("employmentTypes"));
        List<Object> teams = asList(appliesTo.get("teams"));
        List<Object> employeeIds = asList(appliesTo.get("employeeIds"));

        boolean anyRestriction = !locations.isEmpty() || !employmentTypes.isEmpty()
                || !teams.isEmpty() || !employeeIds.isEmpty();
        if (!anyRestriction) {
            return; // no restrictions → everyone
        }

        boolean matched = false;
        if (!locations.isEmpty() && employee.getLocationId() != null) {
            matched |= locations.stream().anyMatch(v -> String.valueOf(v).equals(String.valueOf(employee.getLocationId())));
        }
        if (!employmentTypes.isEmpty() && employee.getEmploymentType() != null) {
            matched |= employmentTypes.stream().anyMatch(v -> String.valueOf(v).equalsIgnoreCase(employee.getEmploymentType()));
        }
        if (!teams.isEmpty() && employee.getTeamId() != null) {
            matched |= teams.stream().anyMatch(v -> String.valueOf(v).equals(String.valueOf(employee.getTeamId())));
        }
        if (!employeeIds.isEmpty()) {
            matched |= employeeIds.stream().anyMatch(v -> String.valueOf(v).equals(String.valueOf(employee.getId())));
        }
        if (!matched) {
            throw new IllegalStateException("Employee is not eligible for category " + category.getCode()
                    + " (applies_to restriction)");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object val) {
        if (val instanceof List<?> l) return (List<Object>) l;
        return List.of();
    }

    private Set<LocalDate> buildHolidaySet(Long locationId, LocalDate start, LocalDate end) {
        if (locationId == null) return Set.of();
        Set<LocalDate> holidays = new HashSet<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (workScheduleService.isHoliday(locationId, d)) {
                holidays.add(d);
            }
        }
        return holidays;
    }

    private static String resolveHalfDayPart(LeaveApplyRequest request) {
        if (request.halfDayPart() != null && !request.halfDayPart().isBlank()) {
            String p = request.halfDayPart().toUpperCase(Locale.ROOT);
            return switch (p) {
                case "FIRST_HALF", "SECOND_HALF" -> p;
                default -> "FULL";
            };
        }
        if (Boolean.TRUE.equals(request.halfDay())) {
            return "FIRST_HALF";
        }
        return "FULL";
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
        boolean isCancelChain = LeaveApplication.STATUS_PENDING_CANCEL.equals(app.getStatus());
        if (!LeaveApplication.STATUS_PENDING_APPROVAL.equals(app.getStatus())
                && !LeaveApplication.STATUS_SUBMITTED.equals(app.getStatus())
                && !isCancelChain) {
            throw new IllegalStateException("Cannot approve status=" + app.getStatus());
        }

        LeaveApproval step = approvalRepository.findByApplicationIdAndStepOrder(applicationId, app.getCurrentStep())
                .orElseThrow(() -> new IllegalStateException("Missing approval step " + app.getCurrentStep()));
        if (!LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
            throw new IllegalStateException("Current step already decided");
        }

        // BE-03: the approver is ALWAYS the authenticated employee; any approverEmployeeId in the body is ignored.
        Employee approver = requireActiveApprover(AuthContext.requireEmployeeId(), step, app);

        Instant now = Instant.now();
        step.setStatus(LeaveApproval.STATUS_APPROVED);
        step.setApproverEmployeeId(approver.getId());
        step.setActorName(approver.getFullName());
        step.setCommentText(request == null ? null : request.comment());
        step.setDecidedAt(now);
        approvalRepository.save(step);

        List<LeaveApproval> all = approvalRepository.findByApplicationIdOrderByStepOrderAsc(applicationId);
        boolean more = all.stream().anyMatch(a -> a.getStepOrder() > app.getCurrentStep()
                && LeaveApproval.STATUS_PENDING.equals(a.getStatus()));
        if (more) {
            app.setCurrentStep(app.getCurrentStep() + 1);
            // Keep status (PENDING_APPROVAL or PENDING_CANCEL) as-is
        } else {
            if (isCancelChain) {
                // FR-LV-18: final approval of cancel chain → restore balance and set CANCELLED
                app.setStatus(LeaveApplication.STATUS_CANCELLED);
                if (app.getLeaveTypeCode() != null && app.isBalanceDeducted()) {
                    leaveBalanceService.restoreOnCancel(
                            app.getEmployeeId(),
                            app.getLeaveTypeCode(),
                            app.getStartDate().getYear(),
                            app.getWorkingDaysCounted());
                }
            } else {
                app.setStatus(LeaveApplication.STATUS_APPROVED);
                deductIfNeeded(app);
            }
        }
        app.setUpdatedAt(now);
        applicationRepository.save(app);
        notificationHook.notifyLeaveDecision(
                app.getEmployeeId(), applicationId, app.getStatus(), "Leave " + app.getStatus());
        return get(applicationId);
    }

    @Transactional
    public LeaveApplicationResponse reject(Long applicationId, LeaveDecisionRequest request) {
        // FR-LV-15: reject requires non-blank comment ≥3 chars
        String comment = request == null ? null : request.comment();
        if (comment == null || comment.trim().length() < 3) {
            throw new IllegalArgumentException("Reject comment is required and must be at least 3 characters");
        }
        LeaveApplication app = requireApp(applicationId);
        if (LeaveApplication.STATUS_APPROVED.equals(app.getStatus())
                || LeaveApplication.STATUS_REJECTED.equals(app.getStatus())) {
            throw new IllegalStateException("Already final: " + app.getStatus());
        }
        LeaveApproval step = approvalRepository.findByApplicationIdAndStepOrder(applicationId, Math.max(1, app.getCurrentStep()))
                .orElse(null);

        // BE-03: approver identity comes from the JWT/AuthContext, never from the request body.
        Long approverId = AuthContext.requireEmployeeId();
        Employee approver;
        if (step != null && LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
            approver = requireActiveApprover(approverId, step, app);
        } else {
            approver = requireActiveEmployee(approverId);
        }

        Instant now = Instant.now();
        if (step != null && LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
            step.setStatus(LeaveApproval.STATUS_REJECTED);
            step.setApproverEmployeeId(approver.getId());
            step.setActorName(approver.getFullName());
            step.setCommentText(request == null ? null : request.comment());
            step.setDecidedAt(now);
            approvalRepository.save(step);
        }
        app.setStatus(LeaveApplication.STATUS_REJECTED);
        app.setRejectReason(request == null ? null : request.comment());
        app.setUpdatedAt(now);
        applicationRepository.save(app);
        notificationHook.notifyLeaveDecision(
                app.getEmployeeId(), applicationId, LeaveApplication.STATUS_REJECTED, "Leave rejected");
        return get(applicationId);
    }

    @Transactional // requester or leave:manage; pending statuses only
    public LeaveApplicationResponse withdraw(Long applicationId) {
        Long actorId = AuthContext.requireEmployeeId();
        LeaveApplication app = requireApp(applicationId);
        if (!actorId.equals(app.getEmployeeId()) && !rbacService.hasPermission(actorId, "leave", "manage")) {
            throw new IllegalStateException("Forbidden: only the requester or leave:manage can withdraw");
        }
        String before = app.getStatus();
        if (!Set.of(LeaveApplication.STATUS_PENDING_APPROVAL, LeaveApplication.STATUS_PENDING_RELIEF,
                LeaveApplication.STATUS_SUBMITTED).contains(before)) {
            throw new IllegalStateException("Cannot withdraw status=" + before + " (only pending requests)");
        }
        app.setStatus(LeaveApplication.STATUS_WITHDRAWN);
        app.setUpdatedAt(Instant.now());
        applicationRepository.save(app);
        auditService.record(actorId, "WITHDRAW", "LeaveApplication", String.valueOf(applicationId),
                Map.of("status", before), Map.of("status", LeaveApplication.STATUS_WITHDRAWN));
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
        // BE-02: the applicant must choose the leave type explicitly — no silent tenant/CASUAL default.
        if (requestedCode == null || requestedCode.isBlank()) {
            throw new IllegalArgumentException("leaveTypeCode is required");
        }
        final String resolved = requestedCode.trim();
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
        return approvalAuthorityService.assertCanApprove(approverEmployeeId, step, app);
    }

    private Employee requireActiveEmployee(Long id) {
        Employee e = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Approver employee not found: " + id));
        if (!e.isActive()) {
            throw new IllegalStateException("Approver is inactive");
        }
        return e;
    }

    /**
     * Category chain wins when present (per-type config); else leave_policy.approval_chain.
     * Each step may carry resolver / allowOverride for ApprovalAuthorityService.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveApprovalChain(LeaveCategory category, LeavePolicy policy) {
        if (category != null && category.getApprovalChain() != null && !category.getApprovalChain().isBlank()) {
            try {
                Object parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(category.getApprovalChain(), List.class);
                if (parsed instanceof List<?> list && !list.isEmpty()) {
                    List<Map<String, Object>> out = new ArrayList<>();
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> m) {
                            Map<String, Object> row = new HashMap<>();
                            m.forEach((k, v) -> {
                                if (k != null) {
                                    row.put(String.valueOf(k), v);
                                }
                            });
                            if (!row.isEmpty()) {
                                out.add(row);
                            }
                        }
                    }
                    if (!out.isEmpty()) {
                        return normalizeChainSteps(out);
                    }
                }
            } catch (Exception ignored) {
                // fall through to policy chain
            }
        }
        List<Map<String, Object>> chain = policy.getApprovalChain();
        if (chain == null || chain.isEmpty()) {
            return List.of(Map.of("step", 1, "role", "HR"));
        }
        return normalizeChainSteps(chain);
    }

    private static List<Map<String, Object>> normalizeChainSteps(List<Map<String, Object>> chain) {
        List<Map<String, Object>> out = new ArrayList<>();
        int i = 1;
        for (Map<String, Object> item : chain) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            Map<String, Object> copy = new HashMap<>(item);
            Object step = copy.get("step");
            if (!(step instanceof Number)) {
                copy.put("step", i);
            }
            if (copy.get("role") == null || String.valueOf(copy.get("role")).isBlank()) {
                copy.put("role", "HR");
            }
            out.add(copy);
            i++;
        }
        return out.isEmpty() ? List.of(Map.of("step", 1, "role", "HR")) : out;
    }

    private void assertReportingManagerPresent(Employee employee, List<Map<String, Object>> chain) {
        boolean needsManager = false;
        for (Map<String, Object> item : chain) {
            String resolver = item.get("resolver") == null ? "" : String.valueOf(item.get("resolver")).trim().toUpperCase(Locale.ROOT);
            String role = item.get("role") == null ? "" : String.valueOf(item.get("role")).trim().toUpperCase(Locale.ROOT);
            if (ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER.equals(resolver)
                    || ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER_AND_DESIGNATION.equals(resolver)
                    || "MANAGER".equals(role)
                    || "STORE_MANAGER".equals(role)) {
                needsManager = true;
                break;
            }
        }
        // Role defaults may still map MANAGER → REPORTING_MANAGER when chain omits resolver.
        if (!needsManager) {
            return;
        }
        if (employee.getManagerId() == null || employee.getManagerId() <= 0) {
            throw new IllegalStateException(
                    "Cannot request leave: employee has no reporting manager. "
                            + "Assign a manager before applying (required for the configured approval chain).");
        }
    }

    /**
     * FR-LV-12/13 — materialize approval chain with skip logic.
     * Resolvers: MANAGER, REPORTING_MANAGER, MANAGER_OF_MANAGER, NAMED_EMPLOYEE, RBAC_ROLE, DESIGNATION_OR_RBAC.
     * Skip when: expected approver is null/inactive, equals requester, or equals previous step's expected approver.
     * Max 4 steps; throws if no steps remain.
     */
    private void createApprovalSteps(LeaveApplication app, List<Map<String, Object>> chain, Employee employee) {
        Long requesterId = app.getEmployeeId();
        List<LeaveApproval> steps = new ArrayList<>();
        Long prevExpectedId = null;
        int stepNum = 1;

        for (Map<String, Object> item : chain) {
            if (stepNum > 4) break;
            String resolver = item.get("resolver") != null ? String.valueOf(item.get("resolver")).trim().toUpperCase(Locale.ROOT) : "";
            String role = item.get("role") != null ? String.valueOf(item.get("role")).trim() : "HR";

            // Determine resolver from role defaults if not set
            if (resolver.isEmpty()) {
                resolver = resolverFromRole(role);
            }

            Long expectedId = null;
            Long namedId = null;

            if (resolver.equals(ApprovalAuthorityService.RESOLVER_NAMED_EMPLOYEE)) {
                Object nid = item.get("namedEmployeeId");
                if (nid instanceof Number n) namedId = n.longValue();
                expectedId = namedId;
            } else if (resolver.equals(ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER)
                    || resolver.equals(ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER_AND_DESIGNATION)
                    || "MANAGER".equalsIgnoreCase(role) || "STORE_MANAGER".equalsIgnoreCase(role)) {
                expectedId = employee != null ? employee.getManagerId() : null;
            } else if (resolver.equals(ApprovalAuthorityService.RESOLVER_MANAGER_OF_MANAGER)) {
                expectedId = resolveManagerOfManager(employee);
            }
            // For RBAC_ROLE and DESIGNATION_OR_RBAC, expectedId stays null (any matching user)

            // Skip conditions
            if (expectedId != null) {
                // null/inactive
                Employee exp = employeeRepository.findById(expectedId).orElse(null);
                if (exp == null || !exp.isActive()) continue;
                // equals requester
                if (expectedId.equals(requesterId)) continue;
                // equals previous step's expected approver
                if (expectedId.equals(prevExpectedId)) continue;
            }

            LeaveApproval a = new LeaveApproval();
            a.setApplicationId(app.getId());
            a.setStepOrder(stepNum);
            a.setApproverRole(role);
            a.setStatus(LeaveApproval.STATUS_PENDING);
            a.setResolver(resolver.isEmpty() ? null : resolver);
            a.setNamedEmployeeId(namedId);
            a.setExpectedApproverId(expectedId);
            approvalRepository.save(a);
            steps.add(a);
            prevExpectedId = expectedId;
            stepNum++;
        }

        if (steps.isEmpty()) {
            throw new IllegalStateException("No valid approval steps could be created for this leave request. "
                    + "Check approval chain configuration and employee manager assignments.");
        }
    }

    private String resolverFromRole(String role) {
        return switch (role.toUpperCase(Locale.ROOT)) {
            case "MANAGER" -> ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER;
            case "MANAGER_OF_MANAGER" -> ApprovalAuthorityService.RESOLVER_MANAGER_OF_MANAGER;
            case "STORE_MANAGER" -> ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER_AND_DESIGNATION;
            case "NAMED_EMPLOYEE" -> ApprovalAuthorityService.RESOLVER_NAMED_EMPLOYEE;
            default -> ApprovalAuthorityService.RESOLVER_DESIGNATION_OR_RBAC;
        };
    }

    private Long resolveManagerOfManager(Employee employee) {
        if (employee == null || employee.getManagerId() == null) return null;
        return employeeRepository.findById(employee.getManagerId())
                .map(Employee::getManagerId)
                .orElse(null);
    }

    /** Resolve expected approver for preview (non-persisted). */
    private Long resolveExpectedApprover(Employee employee, Map<String, Object> step) {
        String resolver = step.get("resolver") != null ? String.valueOf(step.get("resolver")).trim().toUpperCase(Locale.ROOT) : "";
        String role = step.get("role") != null ? String.valueOf(step.get("role")).trim() : "HR";
        if (resolver.isEmpty()) resolver = resolverFromRole(role);
        return switch (resolver) {
            case ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER,
                    ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER_AND_DESIGNATION -> employee.getManagerId();
            case ApprovalAuthorityService.RESOLVER_MANAGER_OF_MANAGER -> resolveManagerOfManager(employee);
            case ApprovalAuthorityService.RESOLVER_NAMED_EMPLOYEE -> {
                Object nid = step.get("namedEmployeeId");
                yield nid instanceof Number n ? n.longValue() : null;
            }
            default -> null;
        };
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
