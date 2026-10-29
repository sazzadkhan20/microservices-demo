package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.domain.LeaveCategory;
import com.platform.hrms.infrastructure.persistence.LeaveCategoryRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/leave-categories")
@Tag(name = "Leave Categories")
public class LeaveCategoryController {

    private final LeaveCategoryRepository repository;

    public LeaveCategoryController(LeaveCategoryRepository repository) {
        this.repository = repository;
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
    @RequiresPermission(feature = "leave", action = "manage")
    public LeaveCategory create(@RequestBody LeaveCategory body) {
        return repository.save(body);
    }

    @PutMapping("/{id}")
    @RequiresPermission(feature = "leave", action = "manage")
    public LeaveCategory update(@PathVariable Long id, @RequestBody LeaveCategory body) {
        LeaveCategory c = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Leave category not found: " + id));
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
        c.setActive(body.isActive());
        return repository.save(c);
    }
}
