package com.platform.leave.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.common.security.AuthContext;
import com.platform.leave.application.AuditService;
import com.platform.leave.application.LeaveAppliesToService;
import com.platform.leave.application.LeaveBalanceService;
import com.platform.leave.application.LeaveCategoryService;
import com.platform.leave.application.LeaveCategorySyncService;
import com.platform.leave.application.support.DynamicListSupport;
import com.platform.leave.domain.LeaveCategory;
import com.platform.leave.infrastructure.persistence.LeaveCategoryRepository;
import com.platform.leave.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
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
    private final LeaveAppliesToService leaveAppliesToService;
    private final LeaveCategoryService leaveCategoryService;
    private final AuditService auditService;
    private final EntityManager entityManager;

    public LeaveCategoryController(LeaveCategoryRepository repository,
                                   LeaveBalanceService leaveBalanceService,
                                   LeaveCategorySyncService syncService,
                                   LeaveAppliesToService leaveAppliesToService,
                                   LeaveCategoryService leaveCategoryService,
                                   AuditService auditService,
                                   EntityManager entityManager) {
        this.repository = repository;
        this.leaveBalanceService = leaveBalanceService;
        this.syncService = syncService;
        this.leaveAppliesToService = leaveAppliesToService;
        this.leaveCategoryService = leaveCategoryService;
        this.auditService = auditService;
        this.entityManager = entityManager;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @RequiresPermission(feature = "leave", action = "view")
    @Operation(summary = "List leave categories (config-driven quotas/rules)")
    public PageResponse<LeaveCategory> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        PageResponse<LeaveCategory> page = DynamicListSupport.page(
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
        // Detach before normalizing so list reads never dirty-flush applies_to rows.
        for (LeaveCategory c : page.content()) {
            withNormalizedAppliesTo(c);
        }
        return page;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @RequiresPermission(feature = "leave", action = "view")
    @Operation(summary = "Get one leave category (includes applies-to scopes)")
    public LeaveCategory get(@PathVariable Long id) {
        LeaveCategory c = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Leave category not found: " + id));
        return withNormalizedAppliesTo(c);
    }

    /** Detach + normalize applies_to for API responses (empty lists = everyone). */
    private LeaveCategory withNormalizedAppliesTo(LeaveCategory c) {
        entityManager.detach(c);
        c.setAppliesTo(leaveAppliesToService.normalize(c.getAppliesTo()));
        return c;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @RequiresPermission(feature = "leave", action = "manage")
    @Operation(summary = "Create leave category and matching leave type")
    public LeaveCategory create(@RequestBody LeaveCategory body) {
        if (body.getCode() == null || body.getCode().isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        body.setCode(body.getCode().trim().toUpperCase(Locale.ROOT));
        if (body.getPeriodStart() == null) {
            int y = LocalDate.now().getYear();
            body.setPeriodStart(LocalDate.of(y, 1, 1));
        }
        if (body.getPeriodEnd() == null) {
            int y = body.getPeriodStart().getYear();
            body.setPeriodEnd(LocalDate.of(y, 12, 31));
        }
        leaveCategoryService.assertNoPeriodOverlap(
                body.getCode(), body.getPeriodStart(), body.getPeriodEnd(), null);

        if (body.getQuotaDays() == null) {
            body.setQuotaDays(BigDecimal.ZERO);
        }
        body.setAppliesTo(leaveAppliesToService.normalize(body.getAppliesTo()));
        LeaveCategory saved = repository.save(body);
        syncService.upsertLeaveType(saved, true);
        auditService.record(AuthContext.getEmployeeId(), "CREATE", "LeaveCategory",
                String.valueOf(saved.getId()), null,
                Map.of("code", saved.getCode(), "name", saved.getName(),
                        "periodStart", String.valueOf(saved.getPeriodStart()),
                        "periodEnd", String.valueOf(saved.getPeriodEnd())));
        return saved;
    }

    @PutMapping("/{id}")
    @Transactional
    @RequiresPermission(feature = "leave", action = "manage")
    public LeaveCategory update(@PathVariable Long id, @RequestBody LeaveCategory body) {
        LeaveCategory c = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Leave category not found: " + id));
        BigDecimal oldQuota = c.getQuotaDays();
        LocalDate oldStart = c.getPeriodStart();
        LocalDate oldEnd = c.getPeriodEnd();

        if (body.getName() != null) c.setName(body.getName());
        if (body.getQuotaDays() != null) c.setQuotaDays(body.getQuotaDays());
        if (body.getPeriodStart() != null) c.setPeriodStart(body.getPeriodStart());
        if (body.getPeriodEnd() != null) c.setPeriodEnd(body.getPeriodEnd());
        leaveCategoryService.requirePeriodDates(c.getPeriodStart(), c.getPeriodEnd());
        leaveCategoryService.assertNoPeriodOverlap(
                c.getCode(), c.getPeriodStart(), c.getPeriodEnd(), c.getId());

        c.setPaid(body.isPaid());
        c.setHalfDayAllowed(body.isHalfDayAllowed());
        if (body.getAttachmentMode() != null) c.setAttachmentMode(body.getAttachmentMode());
        c.setAttachmentAfterDays(body.getAttachmentAfterDays());
        c.setNoticeDays(body.getNoticeDays());
        c.setMaxPerRequest(body.getMaxPerRequest());
        if (body.getNegativeCap() != null) c.setNegativeCap(body.getNegativeCap());
        if (body.getApprovalChain() != null) c.setApprovalChain(body.getApprovalChain());
        c.setAppliesTo(leaveAppliesToService.normalize(body.getAppliesTo()));
        c.setProRata(body.isProRata());
        if (body.getCarryMode() != null) c.setCarryMode(body.getCarryMode());
        if (body.getCarryCapDays() != null) c.setCarryCapDays(body.getCarryCapDays());
        if (body.getCarryExpiryDate() != null) c.setCarryExpiryDate(body.getCarryExpiryDate());
        c.setActive(body.isActive());

        LeaveCategory saved = repository.save(c);
        syncService.upsertLeaveType(saved, false);

        // Mid-period quota change: update every balance for that period year; already-taken leave stays deducted.
        if (body.getQuotaDays() != null && oldQuota != null
                && body.getQuotaDays().compareTo(oldQuota) != 0) {
            BigDecimal delta = body.getQuotaDays().subtract(oldQuota);
            int year = leaveCategoryService.balanceYear(saved);
            int affected = leaveBalanceService.cascadeQuotaDelta(saved.getCode(), delta, year);
            auditService.record(AuthContext.requireEmployeeId(), "CASCADE_QUOTA", "LeaveCategory",
                    String.valueOf(id),
                    Map.of("oldQuota", oldQuota, "year", year,
                            "periodStart", String.valueOf(oldStart), "periodEnd", String.valueOf(oldEnd)),
                    Map.of("newQuota", body.getQuotaDays(), "delta", delta, "employeesAffected", affected));
        } else {
            auditService.record(AuthContext.getEmployeeId(), "UPDATE", "LeaveCategory",
                    String.valueOf(id),
                    Map.of("name", c.getName()),
                    Map.of("name", saved.getName(), "quotaDays", saved.getQuotaDays()));
        }

        return saved;
    }

    /**
     * Renew for next calendar year only — same code + name, new period (editable), once.
     */
    @PostMapping("/{id}/renew")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "leave", action = "manage")
    @Operation(summary = "Renew leave category for next calendar year (same code and name)")
    public LeaveCategory renew(@PathVariable Long id, @RequestBody RenewRequest body) {
        LeaveCategory old = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Leave category not found: " + id));

        int nextYear = LocalDate.now().getYear() + 1;
        LocalDate periodStart = body.periodStart() != null ? body.periodStart() : LocalDate.of(nextYear, 1, 1);
        LocalDate periodEnd = body.periodEnd() != null ? body.periodEnd() : LocalDate.of(nextYear, 12, 31);
        leaveCategoryService.assertRenewAllowed(old, periodStart, periodEnd);

        LeaveCategory newCat = new LeaveCategory();
        newCat.setCode(old.getCode());
        newCat.setName(old.getName());
        newCat.setQuotaDays(body.quotaDays() != null ? body.quotaDays() : old.getQuotaDays());
        newCat.setPeriodStart(periodStart);
        newCat.setPeriodEnd(periodEnd);
        newCat.setPaid(old.isPaid());
        newCat.setHalfDayAllowed(old.isHalfDayAllowed());
        newCat.setAttachmentMode(old.getAttachmentMode());
        newCat.setAttachmentAfterDays(old.getAttachmentAfterDays());
        newCat.setNoticeDays(old.getNoticeDays());
        newCat.setMaxPerRequest(old.getMaxPerRequest());
        newCat.setNegativeCap(old.getNegativeCap());
        newCat.setApprovalChain(old.getApprovalChain());
        newCat.setAppliesTo(leaveAppliesToService.normalize(old.getAppliesTo()));
        newCat.setProRata(old.isProRata());
        newCat.setCarryMode(old.getCarryMode());
        newCat.setCarryCapDays(old.getCarryCapDays());
        newCat.setCarryExpiryDate(old.getCarryExpiryDate());
        newCat.setActive(true);

        LeaveCategory saved = repository.save(newCat);
        // Seed balances for the renewed period year (apply form still hides until period is current).
        syncService.upsertLeaveType(saved, true);
        auditService.record(AuthContext.requireEmployeeId(), "RENEW", "LeaveCategory",
                String.valueOf(id),
                Map.of("sourceCode", old.getCode(), "sourceId", old.getId()),
                Map.of("newId", saved.getId(), "code", saved.getCode(), "name", saved.getName(),
                        "periodStart", String.valueOf(saved.getPeriodStart()),
                        "periodEnd", String.valueOf(saved.getPeriodEnd())));
        return saved;
    }

    public record RenewRequest(LocalDate periodStart, LocalDate periodEnd, BigDecimal quotaDays) {}
}
