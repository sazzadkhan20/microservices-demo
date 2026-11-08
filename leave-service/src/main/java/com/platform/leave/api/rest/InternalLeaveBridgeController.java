package com.platform.leave.api.rest;

import com.platform.leave.domain.LeaveApplication;
import com.platform.leave.domain.LeaveApproval;
import com.platform.leave.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.leave.infrastructure.persistence.LeaveApprovalRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * S2S bridge for People / Time&amp;Work (service-key). No cross-DB access.
 */
@RestController
@RequestMapping("/api/internal")
public class InternalLeaveBridgeController {

    private final LeaveApplicationRepository applicationRepository;
    private final LeaveApprovalRepository approvalRepository;

    public InternalLeaveBridgeController(
            LeaveApplicationRepository applicationRepository,
            LeaveApprovalRepository approvalRepository) {
        this.applicationRepository = applicationRepository;
        this.approvalRepository = approvalRepository;
    }

    @GetMapping("/approvals/pending-for/{employeeId}")
    public Map<String, Object> pendingFor(@PathVariable Long employeeId) {
        return Map.of("pending", hasPendingApprovals(employeeId));
    }

    @PostMapping("/approvals/reassign")
    public Map<String, Object> reassign(@RequestBody Map<String, Object> body) {
        Long leavingId = asLong(body.get("leavingId"));
        Long newApproverId = asLong(body.get("newApproverId"));
        int updated = reassignPendingApprovals(leavingId, newApproverId);
        return Map.of("updated", updated);
    }

    @GetMapping("/leave-facts/{employeeId}")
    public Map<String, Object> leaveFacts(
            @PathVariable Long employeeId,
            @RequestParam String period) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeId", employeeId);
        out.put("period", period);
        out.put("leaveDays", 0);
        out.put("consumedByType", Map.of());
        return out;
    }

    @GetMapping("/approved-leave/{employeeId}")
    public List<Map<String, Object>> approvedLeave(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
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
            row.put("startDate", a.getStartDate().toString());
            row.put("endDate", a.getEndDate().toString());
            row.put("workingDaysCounted", a.getWorkingDaysCounted());
            row.put("status", a.getStatus());
            out.add(row);
        }
        return out;
    }

    private boolean hasPendingApprovals(Long leavingId) {
        for (LeaveApplication app : applicationRepository.findAll()) {
            String st = app.getStatus() == null ? "" : app.getStatus().toUpperCase(Locale.ROOT);
            if (!st.contains("PENDING") && !"SUBMITTED".equals(st) && !"IN_REVIEW".equals(st)) {
                continue;
            }
            for (LeaveApproval step : approvalRepository.findByApplicationIdOrderByStepOrderAsc(app.getId())) {
                if (LeaveApproval.STATUS_PENDING.equals(step.getStatus())
                        && leavingId.equals(step.getApproverEmployeeId())) {
                    return true;
                }
            }
        }
        return false;
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

    private static Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(v));
    }
}
