package com.platform.people.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.people.api.dto.AggregatedAuditRow;
import com.platform.people.application.AuditFederationService;
import com.platform.people.application.support.DynamicListSupport;
import com.platform.people.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit")
public class AuditController {

    private final AuditFederationService federationService;

    public AuditController(AuditFederationService federationService) {
        this.federationService = federationService;
    }

    /**
     * Federated searchable audit log (People + Leave + Time&amp;Work + Payroll + Files +
     * Notification + Report). Sorted most-recent-first across all owning services.
     * Read-only. Searchable by person ({@code filter.actorEmployeeId}), action, record type,
     * service, record id, and free-text {@code q}.
     */
    @GetMapping
    @RequiresPermission(feature = "audit", action = "view")
    @Operation(summary = "Federated audit log (all services, newest first)")
    public PageResponse<AggregatedAuditRow> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        List<AggregatedAuditRow> all = federationService.collectAll();

        return DynamicListSupport.page(
                all,
                query,
                a -> DynamicListSupport.containsIgnoreCase(a.getAction(), query.q())
                        || DynamicListSupport.containsIgnoreCase(a.getEntity(), query.q())
                        || DynamicListSupport.containsIgnoreCase(a.getEntityId(), query.q())
                        || DynamicListSupport.containsIgnoreCase(a.getService(), query.q())
                        || DynamicListSupport.containsIgnoreCase(a.getIp(), query.q())
                        || DynamicListSupport.containsIgnoreCase(
                                a.getActorEmployeeId() == null ? null : String.valueOf(a.getActorEmployeeId()),
                                query.q())
                        || DynamicListSupport.containsIgnoreCase(a.getBeforeJson(), query.q())
                        || DynamicListSupport.containsIgnoreCase(a.getAfterJson(), query.q()),
                a -> Map.of(
                        "action", a.getAction() == null ? "" : a.getAction(),
                        "entity", a.getEntity() == null ? "" : a.getEntity(),
                        "entityId", a.getEntityId() == null ? "" : a.getEntityId(),
                        "service", a.getService() == null ? "" : a.getService(),
                        "actorEmployeeId", a.getActorEmployeeId() == null ? "" : String.valueOf(a.getActorEmployeeId()),
                        "ip", a.getIp() == null ? "" : a.getIp()
                )
        );
    }
}
