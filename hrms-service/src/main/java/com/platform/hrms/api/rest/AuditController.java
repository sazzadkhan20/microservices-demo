package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.domain.AuditLog;
import com.platform.hrms.infrastructure.persistence.AuditLogRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit")
public class AuditController {

    private final AuditLogRepository repository;

    public AuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @RequiresPermission(feature = "settings", action = "manage")
    @Operation(summary = "Searchable audit log")
    public PageResponse<AuditLog> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        List<AuditLog> all = repository.findAll().stream()
                .sorted(Comparator.comparing(AuditLog::getAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return DynamicListSupport.page(
                all,
                query,
                a -> DynamicListSupport.containsIgnoreCase(a.getAction(), query.q())
                        || DynamicListSupport.containsIgnoreCase(a.getEntity(), query.q())
                        || DynamicListSupport.containsIgnoreCase(a.getEntityId(), query.q()),
                a -> Map.of(
                        "action", a.getAction() == null ? "" : a.getAction(),
                        "entity", a.getEntity() == null ? "" : a.getEntity(),
                        "actorEmployeeId", a.getActorEmployeeId() == null ? "" : String.valueOf(a.getActorEmployeeId())
                )
        );
    }
}
