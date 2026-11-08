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
     */
    @GetMapping
    @RequiresPermission(feature = "settings", action = "manage")
    @Operation(summary = "Federated audit log (all services, newest first)")
    public PageResponse<AggregatedAuditRow> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);

        String actorParam = params.get("actorEmployeeId");
        String actionParam = params.get("action");
        String entityParam = params.get("entity");
        String serviceParam = params.get("service");

        List<AggregatedAuditRow> all = federationService.collectAll();

        if (actorParam != null && !actorParam.isBlank()) {
            String actorStr = actorParam.trim();
            all = all.stream()
                    .filter(a -> actorStr.equals(
                            a.getActorEmployeeId() == null ? "" : String.valueOf(a.getActorEmployeeId())))
                    .toList();
        }
        if (actionParam != null && !actionParam.isBlank()) {
            String act = actionParam.trim().toLowerCase();
            all = all.stream()
                    .filter(a -> a.getAction() != null && act.equalsIgnoreCase(a.getAction()))
                    .toList();
        }
        if (entityParam != null && !entityParam.isBlank()) {
            String ent = entityParam.trim();
            all = all.stream()
                    .filter(a -> a.getEntity() != null && (
                            ent.equalsIgnoreCase(a.getEntity())
                                    || DynamicListSupport.containsIgnoreCase(a.getEntity(), ent.replace("*", ""))))
                    .toList();
        }
        if (serviceParam != null && !serviceParam.isBlank()) {
            String svc = serviceParam.trim().toLowerCase();
            all = all.stream()
                    .filter(a -> a.getService() != null && svc.equalsIgnoreCase(a.getService()))
                    .toList();
        }

        return DynamicListSupport.page(
                all,
                query,
                a -> DynamicListSupport.containsIgnoreCase(a.getAction(), query.q())
                        || DynamicListSupport.containsIgnoreCase(a.getEntity(), query.q())
                        || DynamicListSupport.containsIgnoreCase(a.getEntityId(), query.q())
                        || DynamicListSupport.containsIgnoreCase(a.getService(), query.q()),
                a -> Map.of(
                        "action", a.getAction() == null ? "" : a.getAction(),
                        "entity", a.getEntity() == null ? "" : a.getEntity(),
                        "service", a.getService() == null ? "" : a.getService(),
                        "actorEmployeeId", a.getActorEmployeeId() == null ? "" : String.valueOf(a.getActorEmployeeId())
                )
        );
    }
}
