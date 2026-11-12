package com.platform.timework.api.rest;

import com.platform.common.security.PublicEndpoint;
import com.platform.timework.domain.AuditLog;
import com.platform.timework.infrastructure.persistence.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** S2S audit export for People federation (own {@code timework_audit_log} only). */
@RestController
@RequestMapping("/api/internal/audit")
@PublicEndpoint(reason = "S2S audit export; gated by InternalApiInterceptor (X-Internal-Key)")
public class InternalAuditController {

    private final AuditLogRepository repository;

    public InternalAuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "500") int limit) {
        int size = Math.max(1, Math.min(limit, 2000));
        List<AuditLog> rows = repository.findAllByOrderByAtDesc(PageRequest.of(0, size));
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (AuditLog a : rows) {
            out.add(toMap(a));
        }
        return out;
    }

    private static Map<String, Object> toMap(AuditLog a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("at", a.getAt() == null ? null : a.getAt().toString());
        m.put("actorEmployeeId", a.getActorEmployeeId());
        m.put("action", a.getAction());
        m.put("entity", a.getEntity());
        m.put("entityId", a.getEntityId());
        m.put("beforeJson", a.getBeforeJson());
        m.put("afterJson", a.getAfterJson());
        m.put("ip", a.getIp());
        m.put("service", "time-work");
        return m;
    }
}
