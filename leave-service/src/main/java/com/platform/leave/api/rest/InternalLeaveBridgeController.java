package com.platform.leave.api.rest;

import com.platform.common.security.InternalCalls;
import com.platform.common.security.PublicEndpoint;
import com.platform.leave.application.LeaveCategorySyncService;
import com.platform.leave.domain.LeaveApplication;
import com.platform.leave.domain.LeaveApproval;
import com.platform.leave.domain.LeaveBalance;
import com.platform.leave.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.leave.infrastructure.persistence.LeaveApprovalRepository;
import com.platform.leave.infrastructure.persistence.LeaveBalanceRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * S2S bridge for People / Payroll (requires X-Internal-Key — SEC-06).
 */
@RestController
@RequestMapping("/api/internal")
@PublicEndpoint(reason = "S2S; gated by InternalApiInterceptor (X-Internal-Key)")
public class InternalLeaveBridgeController {

    private final LeaveApplicationRepository applicationRepository;
    private final LeaveApprovalRepository approvalRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final LeaveCategorySyncService leaveCategorySyncService;
    private final String internalKey;

    public InternalLeaveBridgeController(
            LeaveApplicationRepository applicationRepository,
            LeaveApprovalRepository approvalRepository,
            LeaveBalanceRepository balanceRepository,
            LeaveCategorySyncService leaveCategorySyncService,
            @Value("${platform.security.internal-key}") String internalKey) {
        this.applicationRepository = applicationRepository;
        this.approvalRepository = approvalRepository;
        this.balanceRepository = balanceRepository;
        this.leaveCategorySyncService = leaveCategorySyncService;
        this.internalKey = internalKey;
    }

    /**
     * Seed pro-rata (or full) opening balances for one employee against current-period categories.
     * Called by People after hire commit.
     */
    @PostMapping("/leave-balances/ensure-opening/{employeeId}")
    public Map<String, Object> ensureOpening(
            @PathVariable Long employeeId, HttpServletRequest request) {
        requireInternal(request);
        int seeded = leaveCategorySyncService.ensureOpeningBalancesForEmployee(employeeId);
        return Map.of("employeeId", employeeId, "seeded", seeded);
    }

    @GetMapping("/approvals/pending-for/{employeeId}")
    public Map<String, Object> pendingFor(@PathVariable Long employeeId, HttpServletRequest request) {
        requireInternal(request);
        int count = countPendingApprovals(employeeId);
        return Map.of("pending", count > 0, "count", count);
    }

    @PostMapping("/approvals/reassign")
    public Map<String, Object> reassign(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        requireInternal(request);
        Long leavingId = asLong(body.get("leavingId"));
        Long newApproverId = asLong(body.get("newApproverId"));
        int updated = reassignPendingApprovals(leavingId, newApproverId);
        return Map.of("updated", updated);
    }

    /**
     * Move pending approval steps on one applicant's leave apps from old manager → new manager
     * (used when a reporting-line change becomes effective).
     */
    @PostMapping("/approvals/reassign-for-applicant")
    public Map<String, Object> reassignForApplicant(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        requireInternal(request);
        Long applicantId = asLong(body.get("applicantId"));
        Long fromApproverId = asLong(body.get("fromApproverId"));
        Long toApproverId = asLong(body.get("toApproverId"));
        int updated = reassignPendingApprovalsForApplicant(applicantId, fromApproverId, toApproverId);
        return Map.of("updated", updated);
    }


    @GetMapping("/approved-leave/{employeeId}")
    public List<Map<String, Object>> approvedLeave(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        requireInternal(request);
        List<Map<String, Object>> out = new ArrayList<>();
        for (LeaveApplication a : applicationRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId)) {
            if (!LeaveApplication.STATUS_APPROVED.equalsIgnoreCase(a.getStatus())) {
                continue;
            }
            if (a.getStartDate() == null || a.getEndDate() == null) {
                continue;
            }
            if (a.getEndDate().isBefore(from) || a.getStartDate().isAfter(to)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.getId());
            row.put("leaveTypeCode", a.getLeaveTypeCode());
            row.put("startDate", a.getStartDate().toString());
            row.put("endDate", a.getEndDate().toString());
            row.put("workingDaysCounted", a.getWorkingDaysCounted());
            row.put("paid", a.isPaid());
            row.put("halfDayPart", a.getHalfDayPart() == null ? "FULL" : a.getHalfDayPart());
            row.put("status", a.getStatus());
            out.add(row);
        }
        return out;
    }

    private void requireInternal(HttpServletRequest request) {
        if (!InternalCalls.isTrusted(request.getHeader(InternalCalls.HEADER), internalKey)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "X-Internal-Key required");
        }
    }

    private boolean hasPendingApprovals(Long leavingId) {
        return countPendingApprovals(leavingId) > 0;
    }

    private int countPendingApprovals(Long leavingId) {
        if (leavingId == null) {
            return 0;
        }
        int n = 0;
        for (LeaveApplication app : applicationRepository.findAll()) {
            String st = app.getStatus() == null ? "" : app.getStatus().toUpperCase(Locale.ROOT);
            if (!st.contains("PENDING") && !"SUBMITTED".equals(st) && !"IN_REVIEW".equals(st)) {
                continue;
            }
            for (LeaveApproval step : approvalRepository.findByApplicationIdOrderByStepOrderAsc(app.getId())) {
                if (LeaveApproval.STATUS_PENDING.equals(step.getStatus())
                        && leavingId.equals(step.getApproverEmployeeId())) {
                    n++;
                }
            }
        }
        return n;
    }

    private int reassignPendingApprovals(Long leavingId, Long newApproverId) {
        if (leavingId == null || newApproverId == null) {
            return 0;
        }
        int n = 0;
        for (LeaveApplication app : applicationRepository.findAll()) {
            String st = app.getStatus() == null ? "" : app.getStatus().toUpperCase(Locale.ROOT);
            if (!st.contains("PENDING") && !"SUBMITTED".equals(st) && !"IN_REVIEW".equals(st)) {
                continue;
            }
            for (LeaveApproval step : approvalRepository.findByApplicationIdOrderByStepOrderAsc(app.getId())) {
                if (!LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
                    continue;
                }
                if (leavingId.equals(step.getApproverEmployeeId())) {
                    step.setApproverEmployeeId(newApproverId);
                    approvalRepository.save(step);
                    n++;
                }
            }
        }
        return n;
    }

    /** Only apps owned by {@code applicantId}, pending steps still on {@code fromApproverId}. */
    private int reassignPendingApprovalsForApplicant(
            Long applicantId, Long fromApproverId, Long toApproverId) {
        if (applicantId == null || fromApproverId == null || toApproverId == null) {
            return 0;
        }
        if (fromApproverId.equals(toApproverId)) {
            return 0;
        }
        int n = 0;
        for (LeaveApplication app : applicationRepository.findByEmployeeIdOrderByCreatedAtDesc(applicantId)) {
            String st = app.getStatus() == null ? "" : app.getStatus().toUpperCase(Locale.ROOT);
            if (!st.contains("PENDING") && !"SUBMITTED".equals(st) && !"IN_REVIEW".equals(st)) {
                continue;
            }
            for (LeaveApproval step : approvalRepository.findByApplicationIdOrderByStepOrderAsc(app.getId())) {
                if (!LeaveApproval.STATUS_PENDING.equals(step.getStatus())) {
                    continue;
                }
                if (fromApproverId.equals(step.getApproverEmployeeId())) {
                    step.setApproverEmployeeId(toApproverId);
                    approvalRepository.save(step);
                    n++;
                }
            }
        }
        return n;
    }

    private static Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }
}
