package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.common.security.AuthContext;
import com.platform.hrms.application.AuditService;
import com.platform.hrms.application.LeaveBalanceService;
import com.platform.hrms.application.LeaveCategorySyncService;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.domain.LeaveCategory;
import com.platform.hrms.infrastructure.persistence.LeaveCategoryRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/leave-categories")
@Tag(name = "Leave Categories")
public class LeaveCategoryController {

    private final LeaveCategoryRepository repository;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveCategorySyncService syncService;
    private final AuditService auditService;

    public LeaveCategoryController(LeaveCategoryRepository repository,
                                   LeaveBalanceService leaveBalanceService,
                                   LeaveCategorySyncService syncService,
                                   AuditService auditService) {
        this.repository = repository;
        this.leaveBalanceService = leaveBalanceService;
        this.syncService = syncService;
        this.auditService = auditService;
    }

    @GetMapping
    @RequiresPermission(feature = "leave", action = "view")
    @Operation(summary = "List leave categories (config-driven quotas/rules)")
    public PageResponse<LeaveCategory> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        return DynamicListSupport.page(
                repository.findAll(),
                query,
                c -> DynamicListSupport.containsIgnoreCase(c.getCode(), query.q())
                        || DynamicListSupport.containsIgnoreCase(c.getName(), query.q()),
                c -> Map.of(
                        "code", c.getCode() == null ? "" : c.getCode(),
                        "active", String.valueOf(c.isActive()),
                        "paid", String.valueOf(c.isPaid())
                )
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @RequiresPermission(feature = "leave", action = "manage")
    @Operation(summary = "Create leave category and matching leave type (so it appears on apply forms)")
    public LeaveCategory create(@RequestBody LeaveCategory body) {
        if (body.getCode() == null || body.getCode().isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        body.setCode(body.getCode().trim().toUpperCase(Locale.ROOT));
        if (repository.findByCodeAndActiveTrue(body.getCode()).isPresent()
                || repository.findAll().stream().anyMatch(c -> body.getCode().equalsIgnoreCase(c.getCode()))) {
            throw new IllegalStateException("Leave category code already exists: " + body.getCode());
        }
        if (body.getQuotaDays() == null) {
            body.setQuotaDays(BigDecimal.ZERO);
        }
        LeaveCategory saved = repository.save(body);
        // Apply UI lists leave-types — keep catalogue in sync and seed opening balances.
        syncService.upsertLeaveType(saved, true);
        auditService.record(AuthContext.getEmployeeId(), "CREATE", "LeaveCategory",
                String.valueOf(saved.getId()), null,
                Map.of("code", saved.getCode(), "name", saved.getName()));
        return saved;
    }

    @PutMapping("/{id}")
    @Transactional
    @RequiresPermission(feature = "leave", action = "manage")
    public LeaveCategory update(@PathVariable Long id, @RequestBody LeaveCategory body) {
        LeaveCategory c = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Leave category not found: " + id));
        BigDecimal oldQuota = c.getQuotaDays();

        if (body.getName() != null) c.setName(body.getName());
        if (body.getQuotaDays() != null) c.setQuotaDays(body.getQuotaDays());
        if (body.getPeriodStart() != null) c.setPeriodStart(body.getPeriodStart());
        if (body.getPeriodEnd() != null) c.setPeriodEnd(body.getPeriodEnd());
        c.setPaid(body.isPaid());
        c.setHalfDayAllowed(body.isHalfDayAllowed());
        if (body.getAttachmentMode() != null) c.setAttachmentMode(body.getAttachmentMode());
        c.setAttachmentAfterDays(body.getAttachmentAfterDays());
        c.setNoticeDays(body.getNoticeDays());
        c.setMaxPerRequest(body.getMaxPerRequest());
        if (body.getNegativeCap() != null) c.setNegativeCap(body.getNegativeCap());
        if (body.getApprovalChain() != null) c.setApprovalChain(body.getApprovalChain());
        if (body.getAppliesTo() != null) c.setAppliesTo(body.getAppliesTo());
        c.setProRata(body.isProRata());
        if (body.getCarryMode() != null) c.setCarryMode(body.getCarryMode());
        if (body.getCarryCapDays() != null) c.setCarryCapDays(body.getCarryCapDays());
        if (body.getCarryExpiryDate() != null) c.setCarryExpiryDate(body.getCarryExpiryDate());
        c.setActive(body.isActive());

        LeaveCategory saved = repository.save(c);
        syncService.upsertLeaveType(saved, false);

        // Cascade quota delta if quotaDays changed
        if (body.getQuotaDays() != null && oldQuota != null
                && body.getQuotaDays().compareTo(oldQuota) != 0) {
            BigDecimal delta = body.getQuotaDays().subtract(oldQuota);
            int year = LocalDate.now().getYear();
            int affected = leaveBalanceService.cascadeQuotaDelta(saved.getCode(), delta, year);
            auditService.record(AuthContext.requireEmployeeId(), "CASCADE_QUOTA", "LeaveCategory",
                    String.valueOf(id),
                    Map.of("oldQuota", oldQuota, "year", year),
                    Map.of("newQuota", body.getQuotaDays(), "delta", delta, "employeesAffected", affected));
        }

        return saved;
    }

    /**
     * FR-LV-06 — copy category rules into a new period row. Current category stays active
     * (so mid-year renew for next year does not break ongoing leave). New code = CODE_YYYY.
     */
    @PostMapping("/{id}/renew")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "leave", action = "manage")
    @Operation(summary = "Renew a leave category for a new period (copies rules; keeps current active)")
    public LeaveCategory renew(@PathVariable Long id, @RequestBody RenewRequest body) {
        LeaveCategory old = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Leave category not found: " + id));
        if (body.periodStart() == null || body.periodEnd() == null) {
            throw new IllegalArgumentException("periodStart and periodEnd are required");
        }
        if (body.periodEnd().isBefore(body.periodStart())) {
            throw new IllegalArgumentException("periodEnd must be on/after periodStart");
        }

        String suffix = String.valueOf(body.periodStart().getYear());
        String baseCode = old.getCode() == null ? "LEAVE" : old.getCode();
        // Strip prior _YYYY suffix if renewing a renewed row
        String root = baseCode.replaceAll("_\\d{4}$", "");
        String newCode = root + "_" + suffix;
        if (repository.findByCodeAndActiveTrue(newCode).isPresent()
                || repository.findAll().stream().anyMatch(c -> newCode.equalsIgnoreCase(c.getCode()))) {
            throw new IllegalStateException("Renewed category already exists: " + newCode);
        }

        LeaveCategory newCat = new LeaveCategory();
        newCat.setCode(newCode);
        newCat.setName(old.getName() + " (" + suffix + ")");
        newCat.setQuotaDays(body.quotaDays() != null ? body.quotaDays() : old.getQuotaDays());
        newCat.setPeriodStart(body.periodStart());
        newCat.setPeriodEnd(body.periodEnd());
        newCat.setPaid(old.isPaid());
        newCat.setHalfDayAllowed(old.isHalfDayAllowed());
        newCat.setAttachmentMode(old.getAttachmentMode());
        newCat.setAttachmentAfterDays(old.getAttachmentAfterDays());
        newCat.setNoticeDays(old.getNoticeDays());
        newCat.setMaxPerRequest(old.getMaxPerRequest());
        newCat.setNegativeCap(old.getNegativeCap());
        newCat.setApprovalChain(old.getApprovalChain());
        newCat.setAppliesTo(old.getAppliesTo());
        newCat.setProRata(old.isProRata());
        newCat.setCarryMode(old.getCarryMode());
        newCat.setCarryCapDays(old.getCarryCapDays());
        newCat.setCarryExpiryDate(old.getCarryExpiryDate());
        newCat.setActive(true);

        LeaveCategory saved = repository.save(newCat);
        // Mirror to leave_type + seed opening balances so renew appears on apply forms immediately.
        syncService.upsertLeaveType(saved, true);
        auditService.record(AuthContext.requireEmployeeId(), "RENEW", "LeaveCategory",
                String.valueOf(id), Map.of("sourceCode", old.getCode()),
                Map.of("newId", saved.getId(), "newCode", saved.getCode()));
        return saved;
    }

    public record RenewRequest(LocalDate periodStart, LocalDate periodEnd, BigDecimal quotaDays) {}
}
