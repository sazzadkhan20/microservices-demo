package com.platform.leave.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.security.AuthContext;
import com.platform.leave.api.dto.LeaveApplicationResponse;
import com.platform.leave.api.dto.LeaveApplyRequest;
import com.platform.leave.api.dto.LeaveDecisionRequest;
import com.platform.leave.api.dto.ReliefAssignRequest;
import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.domain.LeaveApplication;
import com.platform.leave.domain.LeaveApproval;
import com.platform.leave.domain.LeaveBlackout;
import com.platform.leave.domain.LeaveCategory;
import com.platform.leave.domain.LeavePolicy;
import com.platform.leave.domain.LeaveType;
import com.platform.leave.domain.StoreShiftCoverage;
import com.platform.leave.extension.config.TenantConfigLoader;
import com.platform.leave.extension.engine.WorkingDaysCalculator;
import com.platform.leave.client.people.PeopleEmployeeGateway;
import com.platform.leave.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.leave.infrastructure.persistence.LeaveApprovalRepository;
import com.platform.leave.infrastructure.persistence.LeaveCategoryRepository;
import com.platform.leave.infrastructure.persistence.LeaveTypeRepository;
import com.platform.leave.infrastructure.persistence.StoreShiftCoverageRepository;
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
import java.util.Objects;
import java.util.Set;
import com.platform.leave.client.people.PeopleAuthzClient;
import com.platform.leave.client.timework.TimeWorkScheduleGateway;

/**
 * Config-driven leave engine shared by all tenants.
 * Behavior comes from leave_policy modes + leave_type flags + approval_chain — not tenant if-else.
 */
@Service
public class LeaveApplicationService {

    private final PeopleEmployeeGateway employeeRepository;
    private final LeaveApplicationRepository applicationRepository;
    private final LeaveApprovalRepository approvalRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveCategoryRepository leaveCategoryRepository;
    private final StoreShiftCoverageRepository coverageRepository;
    private final TenantConfigLoader configLoader;
    private final WorkingDaysCalculator workingDaysCalculator;
    private final LeaveBalanceService leaveBalanceService;
    private final PeopleAuthzClient rbacService;
    private final AuditService auditService;
    private final LeaveTeamClashService teamClashService;
    private final LeaveNotificationHook notificationHook;
    private final DataScopeService dataScopeService;
    private final ApprovalAuthorityService approvalAuthorityService;
    private final TimeWorkScheduleGateway workScheduleService;
    private final LeaveAppliesToService leaveAppliesToService;
    private final LeaveCategoryService leaveCategoryService;
    private final LeaveApprovalChainBuilder approvalChains;

    public LeaveApplicationService(
            PeopleEmployeeGateway employeeRepository,
            LeaveApplicationRepository applicationRepository,
            LeaveApprovalRepository approvalRepository,
            LeaveTypeRepository leaveTypeRepository,
            LeaveCategoryRepository leaveCategoryRepository,
            StoreShiftCoverageRepository coverageRepository,
            TenantConfigLoader configLoader,
            WorkingDaysCalculator workingDaysCalculator,
            LeaveBalanceService leaveBalanceService,
            PeopleAuthzClient rbacService,
            AuditService auditService,
            LeaveTeamClashService teamClashService,
            LeaveNotificationHook notificationHook,
            DataScopeService dataScopeService,
            ApprovalAuthorityService approvalAuthorityService,
            TimeWorkScheduleGateway workScheduleService,
            LeaveAppliesToService leaveAppliesToService,
            LeaveCategoryService leaveCategoryService,
            LeaveApprovalChainBuilder approvalChains) {
        this.employeeRepository = employeeRepository;
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
        this.leaveAppliesToService = leaveAppliesToService;
        this.leaveCategoryService = leaveCategoryService;
        this.approvalChains = approvalChains;
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
     * Statuses that wait on an approval decision (apply chain or cancel chain).
     * Shared by pending queue, idle reminders, and decision gates — config-agnostic.
     */
    public static boolean isAwaitingApprovalDecision(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        return LeaveApplication.STATUS_PENDING_APPROVAL.equals(s)
                || LeaveApplication.STATUS_PENDING_CANCEL.equals(s)
                || "SUBMITTED".equals(s)
                || "IN_REVIEW".equals(s);
    }

    /**
     * Pending approvals the caller can decide now — driven by current-step authority, not org-tree
     * data scope. Named / Manager's-manager / Admin assignees must see their item even when the
     * applicant is outside {@code leave:approve} TEAM/OWN scope (notifications already target
     * {@code expectedApproverId}; Approvals must match).
     * Includes {@code PENDING_CANCEL} (FR-LV-18 cancel chain uses the same resolvers).
     * HR with leave:approve does not see manager-step items until the reporting manager has acted.
     * <p>Legacy/seed rows awaiting a decision with zero approval steps are rebuilt from the
     * tenant leave category/policy chain (same engine as apply/cancel) so packs stay config-driven.
     */
    @Transactional
    public List<LeaveApplicationResponse> pendingForCaller() {
        Long actorId = AuthContext.requireEmployeeId();
        return applicationRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> isAwaitingApprovalDecision(a.getStatus()))
                .peek(this::ensureApprovalStepsIfMissing)
                .map(a -> {
                    LeaveApproval step = currentApprovalStep(a.getId(), Math.max(1, a.getCurrentStep()));
                    if (step == null || !LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
                        return null;
                    }
                    ApprovalAuthorityService.Authority auth =
                            approvalAuthorityService.authorityOf(actorId, step, a);
                    if (auth == ApprovalAuthorityService.Authority.NONE) {
                        return null;
                    }
                    return LeaveApplicationResponse.from(
                            a,
                            approvalRepository.findByApplicationIdOrderByStepOrderAsc(a.getId()),
                            auth.name());
                })
                .filter(Objects::nonNull)
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

    /**
     * Single application: {@code leave:view} scope, or current-step / on-behalf authority
     * (named approvers outside the applicant's org tree must open deep links from notifications).
     */
    @Transactional(readOnly = true)
    public LeaveApplicationResponse getForCaller(Long id) {
        LeaveApplication app = applicationRepository.findById(id).orElse(null);
        Long actor = AuthContext.requireEmployeeId();
        if (app == null || !canCallerSeeApplication(actor, app)) {
            // Same answer for "missing" and "not yours" so ids cannot be probed.
            throw new SecurityException("Forbidden: leave application " + id + " is not visible to you");
        }
        return LeaveApplicationResponse.from(app, approvalRepository.findByApplicationIdOrderByStepOrderAsc(id));
    }

    /**
     * Visible via leave:view data scope, or because the caller is the designated current-step
     * (or idle on-behalf) approver for an application still awaiting a decision.
     */
    boolean canCallerSeeApplication(Long actorEmployeeId, LeaveApplication app) {
        if (actorEmployeeId == null || app == null) {
            return false;
        }
        if (dataScopeService.canAccess(actorEmployeeId, app.getEmployeeId(), "leave", "view")) {
            return true;
        }
        if (!isAwaitingApprovalDecision(app.getStatus())) {
            return false;
        }
        LeaveApproval step = currentApprovalStep(app.getId(), Math.max(1, app.getCurrentStep()));
        if (step == null || !LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
            return false;
        }
        return approvalAuthorityService.authorityOf(actorEmployeeId, step, app)
                != ApprovalAuthorityService.Authority.NONE;
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
        BigDecimal requested = LeaveDays.of(ctx.daysCounted()); // half-day stays 0.5
        result.put("workingDaysCounted", requested);
        BigDecimal balance = leaveBalanceService.available(ctx.employee().getId(), ctx.leaveType().getCode(), ctx.year());
        BigDecimal afterPending = leaveBalanceService.availableAfterPending(ctx.employee().getId(), ctx.leaveType().getCode(), ctx.year());
        result.put("available", afterPending);
        result.put("pendingDays", LeaveDays.of(balance.subtract(afterPending)));
        result.put("balanceAfter", LeaveDays.of(afterPending.subtract(requested)));
        result.put("warnings", ctx.warnings());
        result.put("teamClashAlert", ctx.teamClashAlert());

        // Approver preview — same skip rules as createApprovalSteps
        List<Map<String, Object>> policyChain = approvalChains.resolveApprovalChain(ctx.category(), ctx.policy());
        result.put("approvers", approvalChains.previewApprovers(ctx.employee(), policyChain));
        if (ctx.category() != null) {
            result.put("attachmentMode", ctx.category().getAttachmentMode());
            result.put("attachmentAfterDays", ctx.category().getAttachmentAfterDays());
            result.put("noticeDays", ctx.category().getNoticeDays());
            result.put("maxPerRequest", ctx.category().getMaxPerRequest());
            result.put("halfDayAllowed", ctx.category().isHalfDayAllowed());
        }
        result.put("noticeGiven", ctx.noticeGiven());
        result.put("noticeRequired", ctx.noticeRequired());
        result.put("canSubmit", true);
        return result;
    }

    private LeaveApplicationResponse doApply(LeaveApplyRequest request, boolean previewOnly) {
        Long actorId = AuthContext.requireEmployeeId();
        Long targetId = request.employeeId() == null ? actorId : request.employeeId();
        if (!actorId.equals(targetId) && !rbacService.hasPermission(actorId, "leave", "manage")) {
            throw new IllegalStateException("Cannot apply leave for another employee");
        }
        ApplyContext ctx = buildApplyContext(request);
        EmployeeView employee = ctx.employee();
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

        List<Map<String, Object>> chain = approvalChains.resolveApprovalChain(category, policy);
        approvalChains.assertReportingManagerPresent(employee, chain);

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
        approvalChains.createApprovalSteps(saved, chain, employee);
        auditService.record(actorId, "CREATE", "LeaveApplication", String.valueOf(saved.getId()), null, Map.of(
                "employeeId", saved.getEmployeeId(),
                "leaveTypeCode", saved.getLeaveTypeCode(),
                "startDate", saved.getStartDate().toString(),
                "endDate", saved.getEndDate().toString(),
                "status", saved.getStatus()
        ));
        List<LeaveApproval> createdSteps =
                approvalRepository.findByApplicationIdOrderByStepOrderAsc(saved.getId());
        // FR-NT-01 — notify current-step expected approver(s)
        notifyCurrentStepApprovers(saved, createdSteps, employee.getFullName(), false);
        if (teamClashAlert) {
            String clashMsg = warnings.getOrDefault("teamClash", "Team clash alert").toString();
            Set<Long> clashRecipients = new HashSet<>();
            if (employee.getManagerId() != null) {
                clashRecipients.add(employee.getManagerId());
            }
            if (employee.getTeamLeadEmployeeId() != null) {
                clashRecipients.add(employee.getTeamLeadEmployeeId());
            }
            for (LeaveApproval step : createdSteps) {
                if (step.getExpectedApproverId() != null) {
                    clashRecipients.add(step.getExpectedApproverId());
                    break; // first step only
                }
            }
            clashRecipients.remove(employee.getId());
            for (Long recipientId : clashRecipients) {
                notificationHook.notifyTeamClash(recipientId, saved.getId(), clashMsg);
            }
        }
        return LeaveApplicationResponse.from(saved, createdSteps);
    }

    /** Validates and builds context for apply/preview without persisting. */
    private ApplyContext buildApplyContext(LeaveApplyRequest request) {
        Long actorId = AuthContext.requireEmployeeId();
        Long targetId = request.employeeId() == null ? actorId : request.employeeId();

        LeavePolicy policy = configLoader.loadLeavePolicyOrDefault();
        EmployeeView employee = employeeRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + targetId));
        if (!employee.isActive()) {
            throw new IllegalStateException("Inactive employee cannot apply leave");
        }
        if (employee.getDepartmentId() == null || employee.getDesignationId() == null) {
            throw new IllegalStateException("EmployeeView must have department and designation before applying leave");
        }

        LeaveType leaveType = resolveLeaveType(request.leaveTypeCode(), policy);
        LeaveCategory category = leaveCategoryService
                .resolveForCodeOn(leaveType.getCode(), request.startDate())
                .orElse(null);

        // Prefer a category that covers the whole request window when period bounds exist.
        if (category != null && request.endDate() != null
                && !leaveCategoryService.covers(category, request.endDate())) {
            category = leaveCategoryService.resolveForCodeOn(leaveType.getCode(), request.endDate()).orElse(null);
        }

        boolean hasAnyCategory = !leaveCategoryService.findAllByCode(leaveType.getCode()).isEmpty();
        if (hasAnyCategory && category == null) {
            throw new IllegalStateException(
                    "No active leave category covers these dates for " + leaveType.getCode()
                            + ". Choose dates inside the category period.");
        }

        // FR-LV-01 / FR-LV-03 — applies_to + hourly-schedule never get leave categories
        // (pay type HOURLY with a FIXED/day schedule may still request leave).
        if (category != null) {
            leaveAppliesToService.requireEligible(employee, category);
        } else if (leaveAppliesToService.isHourlySchedule(employee)) {
            throw new IllegalStateException(
                    "Hourly-schedule employees cannot request leave (FR-LV-03)");
        }

        // FR-LV-11: period eligibility (open-ended when start/end null)
        if (category != null) {
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

        // FR-LV-08: halfDay handling (0.5 on a working day only)
        String halfDayPart = resolveHalfDayPart(request);
        BigDecimal daysCounted;
        boolean half = !"FULL".equals(halfDayPart) || Boolean.TRUE.equals(request.halfDay());
        if (half) {
            if (category != null && !category.isHalfDayAllowed()) {
                throw new IllegalStateException("Half-day leave not allowed for category " + leaveType.getCode());
            }
            if (!request.startDate().equals(request.endDate())) {
                throw new IllegalStateException("Half-day leave must be a single date (startDate == endDate)");
            }
            if (workingDates.isEmpty() || !workingDates.contains(request.startDate())) {
                throw new IllegalStateException(
                        "Half-day leave must fall on a working day (not a weekly day off or holiday)");
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

        // People-owned extension attrs arrive on the employee view (customFields), not a Leave table.
        boolean unionMember = employee.getCustomFields() != null
                && employee.getCustomFields().containsKey("union_member_id");
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

        // FR-LV-11: overlap check (own pending/approved only — not team clash)
        String requestHalf = halfDayPart == null ? "FULL" : halfDayPart.trim().toUpperCase(Locale.ROOT);
        List<LeaveApplication> overlapping = applicationRepository.findOverlappingForEmployee(
                        employee.getId(), request.startDate(), request.endDate(), null)
                .stream()
                .filter(existing -> !isComplementaryHalfDay(existing, request.startDate(), request.endDate(), requestHalf))
                .toList();
        if (!overlapping.isEmpty()) {
            LeaveApplication clash = overlapping.get(0);
            throw new IllegalStateException(
                    "Leave overlaps with your existing " + clash.getStatus()
                            + " request #" + clash.getId()
                            + " (" + clash.getLeaveTypeCode()
                            + " " + clash.getStartDate() + " → " + clash.getEndDate()
                            + "). Withdraw or cancel that request first, or choose other dates.");
        }

        // FR-LV-11: attachment check (FE uses AFTER_DAYS; legacy AFTER_N accepted)
        if (category != null && category.getAttachmentMode() != null) {
            String mode = category.getAttachmentMode().trim().toUpperCase(Locale.ROOT);
            boolean missing = request.attachmentRef() == null || request.attachmentRef().isBlank();
            switch (mode) {
                case "ALWAYS" -> {
                    if (missing) {
                        throw new IllegalStateException("Attachment required for category " + category.getCode());
                    }
                }
                case "AFTER_DAYS", "AFTER_N" -> {
                    int after = Math.max(0, category.getAttachmentAfterDays());
                    if (daysCounted.compareTo(BigDecimal.valueOf(after)) > 0 && missing) {
                        throw new IllegalStateException("Attachment required when leave exceeds "
                                + after + " days for category " + category.getCode());
                    }
                }
                default -> { /* NEVER — no check */ }
            }
        }

        // FR-LV-14: team clash — only on working days in the request
        Map<String, Object> warnings = new HashMap<>();
        boolean teamClashAlert = false;
        LeaveTeamClashService.ClashResult clashResult = teamClashService.check(
                employee, request.startDate(), request.endDate(), workingDates);
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

        int year = category != null
                ? leaveCategoryService.balanceYear(category)
                : request.startDate().getYear();
        // FR-LV-11: whole-day balance check (same integer the UI shows).
        // negativeCap -2 (or 2) = may overdraw by 2 days → effective = available + 2
        BigDecimal availableAfterPending = leaveBalanceService.availableAfterPending(
                employee.getId(), leaveType.getCode(), year);
        BigDecimal requested = LeaveDays.of(daysCounted); // half-day stays 0.5
        if (leaveType.isRequiresBalance() && !leaveType.isAllowWhenZeroBalance()) {
            BigDecimal effectiveAvailable = availableAfterPending;
            BigDecimal overdraft = BigDecimal.ZERO;
            if (category != null && category.getNegativeCap() != null) {
                overdraft = LeaveDays.whole(category.getNegativeCap().abs()); // whole-day overdraft
                effectiveAvailable = availableAfterPending.add(overdraft);
            }
            if (effectiveAvailable.compareTo(requested) < 0) {
                throw new IllegalStateException(
                        "Insufficient " + leaveType.getCode() + " balance: available="
                                + LeaveDays.format(availableAfterPending)
                                + ", requested=" + LeaveDays.format(requested)
                                + (overdraft.signum() > 0
                                ? " (negative cap allows +" + LeaveDays.format(overdraft) + ")"
                                : ""));
            }
        }

        return new ApplyContext(employee, leaveType, category, policy, daysCounted, workingDates,
                location, shift, warnings, teamClashAlert, year, unionMember, noticeGiven, noticeRequired);
    }

    private record ApplyContext(
            EmployeeView employee,
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
        // Must flush-delete: orphaned duplicates make findByApplicationIdAndStepOrder throw NonUniqueResult.
        approvalRepository.deleteByApplicationId(applicationId);

        app.setStatus(LeaveApplication.STATUS_PENDING_CANCEL);
        app.setCurrentStep(1);
        app.setUpdatedAt(Instant.now());
        applicationRepository.save(app);

        LeavePolicy policy = configLoader.loadLeavePolicyOrDefault();
        LeaveCategory category = app.getLeaveTypeCode() != null
                ? leaveCategoryService.resolveForCodeOn(
                        app.getLeaveTypeCode(),
                        app.getStartDate() != null ? app.getStartDate() : LocalDate.now()).orElse(null)
                : null;
        EmployeeView employee = employeeRepository.findById(app.getEmployeeId()).orElse(null);
        List<Map<String, Object>> chain = approvalChains.resolveApprovalChain(category, policy);
        approvalChains.assertReportingManagerPresent(employee, chain);
        approvalChains.createApprovalSteps(app, chain, employee);

        // Same chain engine as apply — notify current-step expected approver(s).
        List<LeaveApproval> createdSteps =
                approvalRepository.findByApplicationIdOrderByStepOrderAsc(applicationId);
        String applicantName = employee == null ? null : employee.getFullName();
        notifyCurrentStepApprovers(app, createdSteps, applicantName, true);

        auditService.record(actorId, "CANCEL_REQUEST", "LeaveApplication", String.valueOf(applicationId),
                Map.of("status", LeaveApplication.STATUS_APPROVED),
                Map.of("status", LeaveApplication.STATUS_PENDING_CANCEL));
        return get(applicationId);
    }

    private Set<LocalDate> buildHolidaySet(Long locationId, LocalDate start, LocalDate end) {
        return workScheduleService.holidaysInRange(locationId, start, end);
    }

    private static String resolveHalfDayPart(LeaveApplyRequest request) {
        if (request.halfDayPart() != null && !request.halfDayPart().isBlank()) {
            String p = request.halfDayPart().toUpperCase(Locale.ROOT);
            return switch (p) {
                case "FIRST_HALF", "AM" -> "FIRST_HALF";
                case "SECOND_HALF", "PM" -> "SECOND_HALF";
                default -> "FULL";
            };
        }
        if (Boolean.TRUE.equals(request.halfDay())) {
            return "FIRST_HALF";
        }
        return "FULL";
    }

    /**
     * Same calendar day + opposite half (FIRST vs SECOND) do not overlap.
     * Full-day leave always conflicts with any leave on that day.
     */
    private static boolean isComplementaryHalfDay(
            LeaveApplication existing,
            LocalDate requestStart,
            LocalDate requestEnd,
            String requestHalf) {
        if (existing == null || requestStart == null || requestEnd == null) {
            return false;
        }
        if (!requestStart.equals(requestEnd)) {
            return false;
        }
        if (existing.getStartDate() == null || existing.getEndDate() == null) {
            return false;
        }
        if (!existing.getStartDate().equals(existing.getEndDate())
                || !existing.getStartDate().equals(requestStart)) {
            return false;
        }
        String existingHalf = existing.getHalfDayPart() == null
                ? "FULL"
                : existing.getHalfDayPart().trim().toUpperCase(Locale.ROOT);
        String req = requestHalf == null ? "FULL" : requestHalf.trim().toUpperCase(Locale.ROOT);
        if ("FULL".equals(existingHalf) || "FULL".equals(req)) {
            return false;
        }
        return ("FIRST_HALF".equals(existingHalf) && "SECOND_HALF".equals(req))
                || ("SECOND_HALF".equals(existingHalf) && "FIRST_HALF".equals(req));
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

        ensureApprovalStepsIfMissing(app);
        LeaveApproval step = currentApprovalStep(applicationId, app.getCurrentStep());
        if (step == null) {
            throw new IllegalStateException("Missing approval step " + app.getCurrentStep());
        }
        if (!LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
            throw new IllegalStateException("Current step already decided");
        }

        // BE-03: the approver is ALWAYS the authenticated employee; any approverEmployeeId in the body is ignored.
        ApprovalAuthorityService.Decision decision =
                approvalAuthorityService.assertCanApprove(AuthContext.requireEmployeeId(), step, app);
        EmployeeView approver = decision.actor();

        Instant now = Instant.now();
        step.setStatus(LeaveApproval.STATUS_APPROVED);
        step.setApproverEmployeeId(approver.getId());
        step.setActorName(approver.getFullName());
        step.setActedOnBehalf(decision.onBehalf());
        String comment = request == null ? null : request.comment();
        if (decision.onBehalf()) {
            String note = comment == null || comment.isBlank() ? "Admin on-behalf after idle threshold" : comment.trim();
            step.setCommentText("[ON_BEHALF] " + note);
        } else {
            step.setCommentText(comment);
        }
        step.setDecidedAt(now);
        approvalRepository.save(step);

        List<LeaveApproval> all = approvalRepository.findByApplicationIdOrderByStepOrderAsc(applicationId);
        boolean more = all.stream().anyMatch(a -> a.getStepOrder() > app.getCurrentStep()
                && LeaveApproval.STATUS_PENDING.equals(a.getStatus()));
        if (more) {
            app.setCurrentStep(app.getCurrentStep() + 1);
            app.setUpdatedAt(now);
            // Keep status (PENDING_APPROVAL or PENDING_CANCEL) as-is
            applicationRepository.save(app);
            // Notify next step's expected approver (apply + cancel chains).
            EmployeeView applicant = employeeRepository.findById(app.getEmployeeId()).orElse(null);
            notifyCurrentStepApprovers(
                    app,
                    approvalRepository.findByApplicationIdOrderByStepOrderAsc(applicationId),
                    applicant == null ? null : applicant.getFullName(),
                    isCancelChain);
            notificationHook.notifyLeaveDecision(
                    app.getEmployeeId(),
                    applicationId,
                    app.getStatus(),
                    isCancelChain ? "Leave cancellation still pending approval" : "Leave still pending approval");
            return get(applicationId);
        }
        if (isCancelChain) {
            // FR-LV-18: final approval of cancel chain → restore balance and set CANCELLED
            app.setStatus(LeaveApplication.STATUS_CANCELLED);
            if (app.getLeaveTypeCode() != null && app.isBalanceDeducted()) {
                leaveBalanceService.restoreOnCancel(
                        app.getEmployeeId(),
                        app.getLeaveTypeCode(),
                        app.getStartDate().getYear(),
                        app.getWorkingDaysCounted(),
                        app.getId());
            }
        } else {
            app.setStatus(LeaveApplication.STATUS_APPROVED);
            deductIfNeeded(app);
        }
        app.setUpdatedAt(now);
        applicationRepository.save(app);
        notificationHook.notifyLeaveDecision(
                app.getEmployeeId(),
                applicationId,
                app.getStatus(),
                isCancelChain ? "Leave cancellation approved" : "Leave " + app.getStatus());
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
                || LeaveApplication.STATUS_REJECTED.equals(app.getStatus())
                || LeaveApplication.STATUS_CANCELLED.equals(app.getStatus())) {
            throw new IllegalStateException("Already final: " + app.getStatus());
        }
        boolean isCancelChain = LeaveApplication.STATUS_PENDING_CANCEL.equals(app.getStatus());
        ensureApprovalStepsIfMissing(app);
        LeaveApproval step = currentApprovalStep(applicationId, Math.max(1, app.getCurrentStep()));

        // BE-03: approver identity comes from the JWT/AuthContext, never from the request body.
        Long approverId = AuthContext.requireEmployeeId();
        EmployeeView approver;
        boolean onBehalf = false;
        if (step != null && LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
            ApprovalAuthorityService.Decision decision =
                    approvalAuthorityService.assertCanApprove(approverId, step, app);
            approver = decision.actor();
            onBehalf = decision.onBehalf();
        } else if (isCancelChain) {
            // Cancel reject must still go through current-step authority when a step exists.
            throw new IllegalStateException("Missing pending cancel-approval step");
        } else {
            approver = requireActiveEmployee(approverId);
        }

        Instant now = Instant.now();
        if (step != null && LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
            step.setStatus(LeaveApproval.STATUS_REJECTED);
            step.setApproverEmployeeId(approver.getId());
            step.setActorName(approver.getFullName());
            step.setActedOnBehalf(onBehalf);
            String rejectComment = request == null ? null : request.comment();
            if (onBehalf && rejectComment != null) {
                step.setCommentText("[ON_BEHALF] " + rejectComment.trim());
            } else {
                step.setCommentText(rejectComment);
            }
            step.setDecidedAt(now);
            approvalRepository.save(step);
        }
        if (isCancelChain) {
            // Deny cancellation → leave remains APPROVED; balance stays deducted.
            app.setStatus(LeaveApplication.STATUS_APPROVED);
            app.setRejectReason(null);
            app.setCurrentStep(0);
            notificationHook.notifyLeaveDecision(
                    app.getEmployeeId(),
                    applicationId,
                    LeaveApplication.STATUS_APPROVED,
                    "Leave cancellation denied — leave remains approved");
        } else {
            app.setStatus(LeaveApplication.STATUS_REJECTED);
            app.setRejectReason(request == null ? null : request.comment());
            notificationHook.notifyLeaveDecision(
                    app.getEmployeeId(), applicationId, LeaveApplication.STATUS_REJECTED, "Leave rejected");
        }
        app.setUpdatedAt(now);
        applicationRepository.save(app);
        return get(applicationId);
    }

    @Transactional // requester or leave:manage; pending statuses only
    public LeaveApplicationResponse withdraw(Long applicationId) {
        Long actorId = AuthContext.requireEmployeeId();
        LeaveApplication app = requireApp(applicationId);
        if (!actorId.equals(app.getEmployeeId()) && !rbacService.hasPermission(actorId, "leave", "manage")) {
            throw new IllegalStateException("Forbidden: only the requester or leave:manage can withdraw");
        }
        // Withdraw a cancel request → restore APPROVED (leave stands).
        if (LeaveApplication.STATUS_PENDING_CANCEL.equals(app.getStatus())) {
            String before = app.getStatus();
            approvalRepository.deleteByApplicationId(applicationId);
            app.setStatus(LeaveApplication.STATUS_APPROVED);
            app.setCurrentStep(0);
            app.setUpdatedAt(Instant.now());
            applicationRepository.save(app);
            auditService.record(actorId, "CANCEL_WITHDRAW", "LeaveApplication", String.valueOf(applicationId),
                    Map.of("status", before), Map.of("status", LeaveApplication.STATUS_APPROVED));
            notificationHook.notifyLeaveDecision(
                    app.getEmployeeId(),
                    applicationId,
                    LeaveApplication.STATUS_APPROVED,
                    "Leave cancellation withdrawn — leave remains approved");
            return get(applicationId);
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
                app.getWorkingDaysCounted(),
                app.getId());
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

    private EmployeeView requireActiveEmployee(Long id) {
        EmployeeView e = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Approver employee not found: " + id));
        if (!e.isActive()) {
            throw new IllegalStateException("Approver is inactive");
        }
        return e;
    }

    /**
     * Resolve the current decision step safely when duplicates exist (legacy cancel bug).
     * Prefers {@code PENDING}, else the latest row by id.
     */
    LeaveApproval currentApprovalStep(Long applicationId, int stepOrder) {
        List<LeaveApproval> rows = approvalRepository
                .findByApplicationIdAndStepOrderOrderByIdAsc(applicationId, stepOrder);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.stream()
                .filter(s -> LeaveApproval.STATUS_PENDING.equals(s.getStatus()))
                .reduce((a, b) -> b) // last pending
                .orElse(rows.get(rows.size() - 1));
    }

    /**
     * Notify the expected actor(s) for the application's current pending step.
     * Used for apply submit, cancel request, and mid-chain advancement — same chain engine for all packs.
     */
    private void notifyCurrentStepApprovers(
            LeaveApplication app,
            List<LeaveApproval> steps,
            String applicantName,
            boolean cancelChain) {
        if (app == null || steps == null || steps.isEmpty()) {
            return;
        }
        int current = Math.max(1, app.getCurrentStep());
        for (LeaveApproval step : steps) {
            if (step.getStepOrder() != current
                    || !LeaveApproval.STATUS_PENDING.equals(step.getStatus())
                    || step.getExpectedApproverId() == null) {
                continue;
            }
            if (cancelChain) {
                notificationHook.notifyLeaveCancelRequested(
                        step.getExpectedApproverId(), app.getId(), applicantName);
            } else {
                notificationHook.notifyLeaveSubmitted(
                        step.getExpectedApproverId(), app.getId(), applicantName);
            }
        }
    }

    /**
     * Rebuild approval steps from leave category/policy config when an awaiting application has none.
     * Keeps tenants/packs config-driven: seed gaps and pre-chain rows heal without tenant-specific code.
     */
    private void ensureApprovalStepsIfMissing(LeaveApplication app) {
        if (app == null || !isAwaitingApprovalDecision(app.getStatus())) {
            return;
        }
        List<LeaveApproval> existing = approvalRepository.findByApplicationIdOrderByStepOrderAsc(app.getId());
        if (existing != null && !existing.isEmpty()) {
            return;
        }
        LeavePolicy policy = configLoader.loadLeavePolicyOrDefault();
        LeaveCategory category = app.getLeaveTypeCode() != null
                ? leaveCategoryService.resolveForCodeOn(
                        app.getLeaveTypeCode(),
                        app.getStartDate() != null ? app.getStartDate() : LocalDate.now()).orElse(null)
                : null;
        EmployeeView employee = employeeRepository.findById(app.getEmployeeId()).orElse(null);
        List<Map<String, Object>> chain = approvalChains.resolveApprovalChain(category, policy);
        approvalChains.assertReportingManagerPresent(employee, chain);
        approvalChains.createApprovalSteps(app, chain, employee);
        if (app.getCurrentStep() < 1) {
            app.setCurrentStep(1);
            app.setUpdatedAt(Instant.now());
            applicationRepository.save(app);
        }
    }

    private int countOnDuty(LeavePolicy policy, String location, String shift, LocalDate day, Long excludeId) {
        boolean matchShift = LeavePolicy.COVERAGE_LOCATION_SHIFT.equalsIgnoreCase(policy.getCoverageMode());
        List<EmployeeView> sameBucket = employeeRepository.findAll().stream()
                .filter(EmployeeView::isActive)
                .filter(e -> location.equals(stringField(e, policy.getLocationField())))
                .filter(e -> !matchShift || shift == null || shift.equals(stringField(e, policy.getShiftField())))
                .toList();
        Set<Long> away = new HashSet<>();
        for (LeaveApplication blocking : applicationRepository.findBlockingLeaveOnDay(location, shift, day, excludeId)) {
            away.add(blocking.getEmployeeId());
        }
        int count = 0;
        for (EmployeeView e : sameBucket) {
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
        EmployeeView relief = employeeRepository.findById(reliefId)
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

    private String stringField(EmployeeView employee, String key) {
        if (key == null) {
            return null;
        }
        Object v = employee.getCustomFields() == null ? null : employee.getCustomFields().get(key);
        return v == null ? null : String.valueOf(v);
    }
}
