package com.platform.report.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constants.PlatformHeaders;
import com.platform.common.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Service-to-service calls into HRMS (same shared service key the gateway stamps).
 */
@Component
public class HrmsGatewayClient {

    private final String hrmsBaseUrl;
    private final String serviceKey;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public HrmsGatewayClient(
            @Value("${report.hrms-base-url:http://127.0.0.1:8081}") String hrmsBaseUrl,
            @Value("${platform.security.service-key:local-service-key-change-me}") String serviceKey,
            ObjectMapper mapper) {
        this.hrmsBaseUrl = hrmsBaseUrl == null ? "" : hrmsBaseUrl.replaceAll("/$", "");
        this.serviceKey = serviceKey;
        this.mapper = mapper;
    }

    public Set<String> permissionsFor(Long employeeId) {
        JsonNode me = getJson("/api/me", employeeId, Map.of());
        Set<String> out = new HashSet<>();
        if (me != null && me.has("permissions") && me.get("permissions").isArray()) {
            me.get("permissions").forEach(n -> out.add(n.asText("").toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    public boolean hasPermission(Long employeeId, String feature, String action) {
        Set<String> perms = permissionsFor(employeeId);
        if (perms.contains("*:*") || perms.contains(feature.toLowerCase(Locale.ROOT) + ":*")) {
            return true;
        }
        String need = feature.toLowerCase(Locale.ROOT) + ":" + action.toLowerCase(Locale.ROOT);
        if (perms.contains(need)) {
            return true;
        }
        // view ↔ read alias used across the platform
        if ("view".equalsIgnoreCase(action) && perms.contains(feature.toLowerCase(Locale.ROOT) + ":read")) {
            return true;
        }
        if ("read".equalsIgnoreCase(action) && perms.contains(feature.toLowerCase(Locale.ROOT) + ":view")) {
            return true;
        }
        // Do not treat reports:view as reports:export — export is a distinct grant.
        return false;
    }

    /**
     * Page of attendance rows for an employee.
     * {@code actorEmployeeId} is used for HRMS auth/scope; {@code targetEmployeeId} is the data subject.
     */
    public List<JsonNode> attendanceForEmployee(Long actorEmployeeId, Long targetEmployeeId, int page, int size) {
        JsonNode pageJson = getJson(
                "/api/attendance/employee/" + targetEmployeeId,
                actorEmployeeId,
                Map.of("page", String.valueOf(page), "size", String.valueOf(size), "sort", "workDate,asc"));
        List<JsonNode> rows = new ArrayList<>();
        if (pageJson != null && pageJson.has("content") && pageJson.get("content").isArray()) {
            pageJson.get("content").forEach(rows::add);
        }
        return rows;
    }

    private JsonNode getJson(String path, Long actorEmployeeId, Map<String, String> query) {
        if (hrmsBaseUrl.isBlank()) {
            return null;
        }
        try {
            StringBuilder url = new StringBuilder(hrmsBaseUrl).append(path);
            if (!query.isEmpty()) {
                url.append('?');
                boolean first = true;
                for (Map.Entry<String, String> e : query.entrySet()) {
                    if (!first) url.append('&');
                    first = false;
                    url.append(e.getKey()).append('=').append(e.getValue());
                }
            }
            String tenant = TenantContext.getTenantId() == null ? "" : TenantContext.getTenantId();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(8))
                    .header(PlatformHeaders.SERVICE_KEY, serviceKey)
                    .header(PlatformHeaders.TENANT_ID, tenant)
                    .header(PlatformHeaders.EMPLOYEE_ID, String.valueOf(actorEmployeeId))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                return null;
            }
            return mapper.readTree(resp.body());
        } catch (Exception e) {
            return null;
        }
    }
}
