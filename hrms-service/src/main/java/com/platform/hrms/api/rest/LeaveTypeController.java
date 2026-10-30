package com.platform.hrms.api.rest;

import com.platform.hrms.api.dto.LeaveTypeResponse;
import com.platform.hrms.application.LeaveCategorySyncService;
import com.platform.hrms.domain.LeaveCategory;
import com.platform.hrms.domain.LeaveType;
import com.platform.hrms.infrastructure.persistence.LeaveCategoryRepository;
import com.platform.hrms.infrastructure.persistence.LeaveTypeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Leave catalogue for apply forms. Driven by active {@code leave_category} rows —
 * each category is mirrored to {@code leave_type} so balances/apply keep working.
 */
@RestController
@RequestMapping("/api/leave-types")
@Tag(name = "Leave Types", description = "Active leave categories as requestable types")
public class LeaveTypeController {

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveCategoryRepository leaveCategoryRepository;
    private final LeaveCategorySyncService syncService;

    public LeaveTypeController(LeaveTypeRepository leaveTypeRepository,
                               LeaveCategoryRepository leaveCategoryRepository,
                               LeaveCategorySyncService syncService) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
        this.syncService = syncService;
    }

    @GetMapping
    @Operation(summary = "List requestable leave types",
            description = "Active leave categories (synced to leave_type). Orphan categories are repaired on read.")
    public List<LeaveTypeResponse> list() {
        // 1) Ensure every category has a leave_type twin
        syncService.syncAllActiveCategories();

        // 2) Build catalogue from ACTIVE categories (source of truth for admins)
        Map<String, LeaveTypeResponse> byCode = new LinkedHashMap<>();
        for (LeaveCategory c : leaveCategoryRepository.findAll()) {
            if (!c.isActive() || c.getCode() == null || c.getCode().isBlank()) {
                continue;
            }
            String code = c.getCode().trim().toUpperCase(Locale.ROOT);
            LeaveType t = leaveTypeRepository.findByCodeIgnoreCase(code).orElse(null);
            if (t == null || !t.isActive()) {
                t = syncService.upsertLeaveType(c, false);
            }
            byCode.put(code, LeaveTypeResponse.from(t));
        }

        // 3) Keep any active leave_type that has no category (legacy / unpaid edge cases)
        for (LeaveType t : leaveTypeRepository.findByActiveTrueOrderBySortOrderAsc()) {
            if (t.getCode() == null) {
                continue;
            }
            byCode.putIfAbsent(t.getCode().trim().toUpperCase(Locale.ROOT), LeaveTypeResponse.from(t));
        }

        List<LeaveTypeResponse> out = new ArrayList<>(byCode.values());
        out.sort(Comparator.comparingInt(LeaveTypeResponse::sortOrder).thenComparing(LeaveTypeResponse::code));
        return out;
    }
}
