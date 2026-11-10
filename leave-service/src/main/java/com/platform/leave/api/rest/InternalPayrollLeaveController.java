package com.platform.leave.api.rest;

import com.platform.common.security.InternalCalls;
import com.platform.leave.application.LeaveCoverService;
import com.platform.leave.domain.LeaveApplication;
import com.platform.leave.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.common.security.PublicEndpoint;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * S2S payroll facts owned by Leave (docs/PAYROLL_SPEC.md §2.1): approved leave in a month and the
 * usable balances of the payroll cover categories, plus the cover ledger for reconciliation.
 */
@RestController
@RequestMapping("/api/internal/payroll")
@Hidden
@PublicEndpoint(reason = "S2S payroll facts; gated by InternalApiInterceptor (X-Internal-Key)")
public class InternalPayrollLeaveController {

    private final LeaveApplicationRepository applications;
    private final LeaveCoverService cover;
    private final String internalKey;

    public InternalPayrollLeaveController(LeaveApplicationRepository applications, LeaveCoverService cover,
                                          @Value("${platform.security.internal-key}") String internalKey) {
        this.applications = applications;
        this.cover = cover;
        this.internalKey = internalKey;
    }

    /** Approved leave spans for many employees in one call (used by Time &amp; Work for day statuses). */
    @GetMapping("/approved-leave")
    @Transactional(readOnly = true)
    public Map<String, List<Map<String, Object>>> approvedLeave(
            @RequestParam String employeeIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        requireInternal(request);
        List<Long> ids = ids(employeeIds);
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        ids.forEach(id -> out.put(String.valueOf(id), new ArrayList<>()));
        for (LeaveApplication a : applications.findApprovedOverlapping(ids, from, to)) {
            out.computeIfAbsent(String.valueOf(a.getEmployeeId()), k -> new ArrayList<>()).add(span(a));
        }
        return out;
    }

    /** Payroll month facts: approved leave + usable cover balances in cover order (Decision D4). */
    @GetMapping("/leave")
    @Transactional(readOnly = true)
    public Map<String, Object> leave(@RequestParam String period,
                                     @RequestParam(required = false, defaultValue = "") String coverOrder,
                                     @RequestParam String employeeIds,
                                     HttpServletRequest request) {
        requireInternal(request);
        YearMonth ym;
        try {
            ym = YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "period must be YYYY-MM");
        }
        List<Long> ids = ids(employeeIds);
        List<LeaveCoverService.CoverCategory> categories = new ArrayList<>();
        for (String family : coverOrder.split(",")) {
            if (!family.isBlank()) {
                Optional<LeaveCoverService.CoverCategory> c = cover.resolve(family.trim(), ym);
                c.ifPresent(categories::add);
            }
        }
        Map<String, Map<String, Object>> byEmp = new LinkedHashMap<>();
        for (Long id : ids) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("leaves", new ArrayList<Map<String, Object>>());
            List<Map<String, Object>> balances = new ArrayList<>();
            for (LeaveCoverService.CoverCategory c : categories) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("family", c.family());
                b.put("category", c.code());
                b.put("year", c.year());
                b.put("available", cover.usable(id, c));
                balances.add(b);
            }
            e.put("coverBalances", balances);
            byEmp.put(String.valueOf(id), e);
        }
        for (LeaveApplication a : applications.findApprovedOverlapping(ids, ym.atDay(1), ym.atEndOfMonth())) {
            Map<String, Object> e = byEmp.get(String.valueOf(a.getEmployeeId()));
            if (e != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> leaves = (List<Map<String, Object>>) e.get("leaves");
                Map<String, Object> s = span(a);
                leaves.add(Map.of("start", s.get("startDate"), "end", s.get("endDate"),
                        "halfDayPart", s.get("halfDayPart"), "paid", s.get("paid"), "category", s.get("leaveTypeCode")));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", ym.toString());
        out.put("coverCategories", categories.stream().map(c -> Map.of("family", c.family(), "category", c.code(), "year", c.year())).toList());
        out.put("employees", byEmp);
        return out;
    }

    /** Net cover days recorded for a payroll finalise operation key (guide §6 reconciliation). */
    @GetMapping("/cover-ledger")
    public Map<String, Object> coverLedger(@RequestParam String operationKey, HttpServletRequest request) {
        requireInternal(request);
        return Map.of("operationKey", operationKey, "rows", cover.ledgerFor(operationKey));
    }

    private static Map<String, Object> span(LeaveApplication a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("leaveTypeCode", a.getLeaveTypeCode() == null ? "UNKNOWN" : a.getLeaveTypeCode().toUpperCase(Locale.ROOT));
        m.put("startDate", a.getStartDate().toString());
        m.put("endDate", a.getEndDate().toString());
        m.put("halfDayPart", a.getHalfDayPart() == null ? "FULL" : a.getHalfDayPart());
        m.put("paid", a.isPaid());
        m.put("workingDaysCounted", a.getWorkingDaysCounted());
        m.put("status", a.getStatus());
        return m;
    }

    private static List<Long> ids(String employeeIds) {
        List<Long> ids = Arrays.stream(employeeIds.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).distinct().toList();
        if (ids.size() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 1000 employees per call");
        }
        return ids;
    }

    private void requireInternal(HttpServletRequest request) {
        if (!InternalCalls.isTrusted(request.getHeader(InternalCalls.HEADER), internalKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Internal-Key required");
        }
    }
}
