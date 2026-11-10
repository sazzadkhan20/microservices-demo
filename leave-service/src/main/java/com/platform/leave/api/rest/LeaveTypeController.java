package com.platform.leave.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.leave.api.dto.LeaveTypeResponse;
import com.platform.leave.application.LeaveAppliesToService;
import com.platform.leave.application.LeaveBalanceService;
import com.platform.leave.application.LeaveCategoryService;
import com.platform.leave.application.LeaveCategorySyncService;
import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.client.people.PeopleAuthzClient;
import com.platform.leave.client.people.PeopleEmployeeGateway;
import com.platform.leave.domain.LeaveCategory;
import com.platform.leave.domain.LeaveType;
import com.platform.leave.infrastructure.persistence.LeaveCategoryRepository;
import com.platform.leave.infrastructure.persistence.LeaveTypeRepository;
import com.platform.leave.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Leave catalogue endpoints.
 * {@code GET /leave-types} — apply form: current period + eligible + balances (deduped).
 * {@code GET /leave-types/catalogue} — HR setup pickers: all active leave types.
 */
@RestController
@RequestMapping("/api/leave-types")
@Tag(name = "Leave Types", description = "Active leave categories as requestable types")
public class LeaveTypeController {

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveCategoryRepository leaveCategoryRepository;
    private final LeaveCategorySyncService syncService;
    private final LeaveAppliesToService leaveAppliesToService;
    private final LeaveCategoryService leaveCategoryService;
    private final LeaveBalanceService leaveBalanceService;
    private final PeopleEmployeeGateway employeeRepository;
    private final PeopleAuthzClient rbacService;

    public LeaveTypeController(
            LeaveTypeRepository leaveTypeRepository,
            LeaveCategoryRepository leaveCategoryRepository,
            LeaveCategorySyncService syncService,
            LeaveAppliesToService leaveAppliesToService,
            LeaveCategoryService leaveCategoryService,
            LeaveBalanceService leaveBalanceService,
            PeopleEmployeeGateway employeeRepository,
            PeopleAuthzClient rbacService) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
        this.syncService = syncService;
        this.leaveAppliesToService = leaveAppliesToService;
        this.leaveCategoryService = leaveCategoryService;
        this.leaveBalanceService = leaveBalanceService;
        this.employeeRepository = employeeRepository;
        this.rbacService = rbacService;
    }

    @GetMapping
    @RequiresPermission(feature = "leave", action = "view")
    @Operation(summary = "List requestable leave types for the caller (current period, eligible, deduped)")
    public List<LeaveTypeResponse> list() {
        syncService.syncAllActiveCategories();

        Long actorId = AuthContext.getEmployeeId();
        EmployeeView actor = actorId == null ? null : employeeRepository.findById(actorId).orElse(null);
        if (actor != null && leaveAppliesToService.isHourly(actor)) {
            return List.of();
        }

        LocalDate today = LocalDate.now();
        Map<String, LeaveTypeResponse> byCode = new LinkedHashMap<>();

        for (LeaveCategory c : leaveCategoryRepository.findAll()) {
            if (!c.isActive() || c.getCode() == null || c.getCode().isBlank()) {
                continue;
            }
            // Current-date period only; open-ended (null start/end) always covers.
            if (!leaveCategoryService.covers(c, today)) {
                continue;
            }
            if (actor != null && !leaveAppliesToService.isEligible(actor, c)) {
                continue;
            }
            String code = c.getCode().trim().toUpperCase(Locale.ROOT);
            // Dedup overlapping scopes (employee + team + location + employment type) — one row per code.
            if (byCode.containsKey(code)) {
                continue;
            }
            LeaveType t = leaveTypeRepository.findByCodeIgnoreCase(code).orElse(null);
            if (t == null || !t.isActive()) {
                t = syncService.upsertLeaveType(c, false);
            }
            int year = leaveCategoryService.balanceYear(c);
            BigDecimal balance = BigDecimal.ZERO;
            BigDecimal pending = BigDecimal.ZERO;
            BigDecimal available = BigDecimal.ZERO;
            if (actorId != null) {
                balance = leaveBalanceService.available(actorId, code, year);
                pending = leaveBalanceService.pendingDays(actorId, code, year);
                available = balance.subtract(pending);
            }
            byCode.put(code, LeaveTypeResponse.enrich(t, c, available, balance, pending));
        }

        if (actor == null) {
            for (LeaveType t : leaveTypeRepository.findByActiveTrueOrderBySortOrderAsc()) {
                if (t.getCode() == null) continue;
                byCode.putIfAbsent(t.getCode().trim().toUpperCase(Locale.ROOT), LeaveTypeResponse.from(t));
            }
        }

        List<LeaveTypeResponse> out = new ArrayList<>(byCode.values());
        out.sort(Comparator.comparingInt(LeaveTypeResponse::sortOrder).thenComparing(LeaveTypeResponse::code));
        return out;
    }

    @GetMapping("/catalogue")
    @RequiresPermission(feature = "leave", action = "manage")
    @Operation(summary = "HR leave-type catalogue for setup (adjust / opening / year-end)")
    public List<LeaveTypeResponse> catalogue() {
        syncService.syncAllActiveCategories();
        Long actorId = AuthContext.getEmployeeId();
        if (actorId != null && !rbacService.hasPermission(actorId, "leave", "manage")) {
            throw new IllegalStateException("Forbidden: leave:manage required");
        }
        LocalDate today = LocalDate.now();
        Map<String, LeaveTypeResponse> byCode = new LinkedHashMap<>();
        for (LeaveType t : leaveTypeRepository.findByActiveTrueOrderBySortOrderAsc()) {
            if (t.getCode() == null || t.getCode().isBlank()) continue;
            String code = t.getCode().trim().toUpperCase(Locale.ROOT);
            LeaveCategory current = leaveCategoryService.resolveForCodeOn(code, today).orElse(null);
            byCode.put(code, LeaveTypeResponse.enrich(t, current, null, null, null));
        }
        List<LeaveTypeResponse> out = new ArrayList<>(byCode.values());
        out.sort(Comparator.comparingInt(LeaveTypeResponse::sortOrder).thenComparing(LeaveTypeResponse::code));
        return out;
    }
}
