package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.common.security.AuthContext;
import com.platform.hrms.api.dto.LeaveApplicationResponse;
import com.platform.hrms.api.dto.LeaveApplyRequest;
import com.platform.hrms.application.LeaveApplicationService;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * Frontend-friendly leave aliases used by Qii Next.js app.
 */
@RestController
@RequestMapping("/api/leave")
@Tag(name = "Leave (FE)", description = "Product UI leave endpoints")
public class LeaveFacadeController {

    private final LeaveApplicationService leaveApplicationService;
    private final LeaveApplicationRepository applicationRepository;

    public LeaveFacadeController(
            LeaveApplicationService leaveApplicationService,
            LeaveApplicationRepository applicationRepository) {
        this.leaveApplicationService = leaveApplicationService;
        this.applicationRepository = applicationRepository;
    }

    @GetMapping("/my")
    @RequiresPermission(feature = "leave", action = "view")
    @Operation(summary = "My leave applications (paginated; optional status filter)")
    public PageResponse<LeaveApplicationResponse> my(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        Long empId = AuthContext.requireEmployeeId();
        var all = applicationRepository.findByEmployeeIdOrderByCreatedAtDesc(empId).stream()
                .map(a -> leaveApplicationService.get(a.getId()))
                .toList();
        return DynamicListSupport.page(
                all,
                query,
                a -> DynamicListSupport.containsIgnoreCase(a.leaveTypeCode(), query.q())
                        || DynamicListSupport.containsIgnoreCase(a.reason(), query.q()),
                a -> Map.of("status", a.status() == null ? "" : a.status())
        );
    }

    @GetMapping("/employee/{id}")
    @RequiresPermission(feature = "leave", action = "view")
    @Operation(summary = "Leave applications for one employee")
    public PageResponse<LeaveApplicationResponse> forEmployee(@PathVariable Long id,
                                                              @RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        var all = applicationRepository.findByEmployeeIdOrderByCreatedAtDesc(id).stream()
                .map(a -> leaveApplicationService.get(a.getId()))
                .toList();
        return DynamicListSupport.page(
                all,
                query,
                a -> DynamicListSupport.containsIgnoreCase(a.leaveTypeCode(), query.q())
                        || DynamicListSupport.containsIgnoreCase(a.status(), query.q()),
                a -> Map.of("status", a.status() == null ? "" : a.status())
        );
    }

    @PostMapping("/request")
    @RequiresPermission(feature = "leave", action = "request")
    @Operation(summary = "Apply for leave as current employee")
    public LeaveApplicationResponse request(@RequestBody Map<String, Object> body) {
        Long empId = AuthContext.requireEmployeeId();
        String type = str(body, "leaveTypeCode", str(body, "type", str(body, "leaveType", "CASUAL")));
        LocalDate start = LocalDate.parse(str(body, "startDate", str(body, "from", null)));
        LocalDate end = LocalDate.parse(str(body, "endDate", str(body, "to", start.toString())));
        String reason = str(body, "reason", str(body, "comment", ""));
        Boolean halfDay = body.get("halfDay") instanceof Boolean b ? b : null;
        return leaveApplicationService.apply(new LeaveApplyRequest(empId, type, start, end, reason, null, halfDay));
    }

    private static String str(Map<String, Object> body, String key, String fallback) {
        Object v = body.get(key);
        if (v == null) return fallback;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equals(s) ? fallback : s;
    }
}
