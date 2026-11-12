package com.platform.people.application;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.InternalCalls;
import com.platform.people.api.dto.AggregatedAuditRow;
import com.platform.people.domain.AuditLog;
import com.platform.people.infrastructure.persistence.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Guide §3 — each service owns its audit table. People federates sibling /api/internal/audit
 * feeds over HTTP (never cross-DB) and returns one date-sorted stream for the admin UI.
 */
@Service
public class AuditFederationService {

    private static final Logger log = LoggerFactory.getLogger(AuditFederationService.class);
    /** Cap per owning service — keep high enough that older role/doc rows stay visible. */
    private static final int PER_SERVICE_LIMIT = 2000;

    private final AuditLogRepository peopleRepository;
    private final RestClient.Builder restClientBuilder;
    private final String serviceKey;
    private final String internalKey;
    private final List<RemoteAuditSource> remotes;

    public AuditFederationService(
            AuditLogRepository peopleRepository,
            RestClient.Builder restClientBuilder,
            @Value("${platform.security.service-key}") String serviceKey,
            @Value("${platform.security.internal-key}") String internalKey,
            @Value("${platform.leave-base-url:http://127.0.0.1:8086}") String leaveBaseUrl,
            @Value("${platform.time-work-base-url:http://127.0.0.1:8087}") String timeWorkBaseUrl,
            @Value("${platform.payroll-base-url:http://127.0.0.1:8082}") String payrollBaseUrl,
            @Value("${platform.files-base-url:http://127.0.0.1:8084}") String filesBaseUrl,
            @Value("${platform.notification-base-url:http://127.0.0.1:8083}") String notificationBaseUrl,
            @Value("${platform.report-base-url:http://127.0.0.1:8085}") String reportBaseUrl) {
        this.peopleRepository = peopleRepository;
        this.restClientBuilder = restClientBuilder;
        this.serviceKey = serviceKey;
        this.internalKey = internalKey;
        this.remotes = List.of(
                new RemoteAuditSource("leave", leaveBaseUrl),
                new RemoteAuditSource("time-work", timeWorkBaseUrl),
                new RemoteAuditSource("payroll", payrollBaseUrl),
                new RemoteAuditSource("files", filesBaseUrl),
                new RemoteAuditSource("notification", notificationBaseUrl),
                new RemoteAuditSource("report", reportBaseUrl)
        );
    }

    public List<AggregatedAuditRow> collectAll() {
        List<AggregatedAuditRow> merged = new ArrayList<>();
        for (AuditLog row : peopleRepository.findAllByOrderByAtDesc(PageRequest.of(0, PER_SERVICE_LIMIT))) {
            merged.add(fromPeople(row));
        }
        for (RemoteAuditSource remote : remotes) {
            merged.addAll(fetchRemote(remote));
        }
        merged.sort(Comparator
                .comparing(AggregatedAuditRow::getAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AggregatedAuditRow::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return merged;
    }

    private List<AggregatedAuditRow> fetchRemote(RemoteAuditSource remote) {
        try {
            RestClient client = restClientBuilder
                    .baseUrl(remote.baseUrl().replaceAll("/$", ""))
                    .defaultHeader(PlatformHeaders.SERVICE_KEY, serviceKey)
                    .defaultHeader(InternalCalls.HEADER, internalKey)
                    .build();
            List<Map<String, Object>> body = client.get()
                    .uri(uri -> uri.path("/api/internal/audit")
                            .queryParam("limit", PER_SERVICE_LIMIT)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (body == null || body.isEmpty()) {
                return List.of();
            }
            List<AggregatedAuditRow> rows = new ArrayList<>(body.size());
            for (Map<String, Object> m : body) {
                rows.add(fromMap(remote.name(), m));
            }
            return rows;
        } catch (Exception e) {
            log.warn("Audit federation skipped for {}: {}", remote.name(), e.getMessage());
            return List.of();
        }
    }

    private static AggregatedAuditRow fromPeople(AuditLog a) {
        AggregatedAuditRow row = new AggregatedAuditRow();
        row.setService("people");
        row.setId("people:" + a.getId());
        row.setAt(a.getAt());
        row.setActorEmployeeId(a.getActorEmployeeId());
        row.setAction(a.getAction());
        row.setEntity(a.getEntity());
        row.setEntityId(a.getEntityId());
        row.setBeforeJson(a.getBeforeJson());
        row.setAfterJson(a.getAfterJson());
        row.setIp(a.getIp());
        return row;
    }

    private static AggregatedAuditRow fromMap(String service, Map<String, Object> m) {
        AggregatedAuditRow row = new AggregatedAuditRow();
        String svc = str(m.get("service"));
        if (svc.isBlank()) {
            svc = service;
        }
        row.setService(svc);
        Object rawId = m.get("id");
        row.setId(svc + ":" + (rawId == null ? "0" : String.valueOf(rawId)));
        row.setAt(parseInstant(m.get("at")));
        row.setActorEmployeeId(asLong(m.get("actorEmployeeId")));
        row.setAction(str(m.get("action")));
        row.setEntity(str(m.get("entity")));
        row.setEntityId(str(m.get("entityId")));
        row.setBeforeJson(jsonish(m.get("beforeJson")));
        row.setAfterJson(jsonish(m.get("afterJson")));
        row.setIp(str(m.get("ip")));
        return row;
    }

    private static Instant parseInstant(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Instant i) {
            return i;
        }
        try {
            return Instant.parse(String.valueOf(raw));
        } catch (Exception e) {
            return null;
        }
    }

    private static Long asLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }

    private static String jsonish(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            return s;
        }
        return String.valueOf(raw);
    }

    private record RemoteAuditSource(String name, String baseUrl) {}
}
