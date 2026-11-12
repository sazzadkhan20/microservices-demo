package com.platform.report.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constants.PlatformHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Service-to-service calls into People / Leave / Time&amp;Work (gateway-stamped service key).
 */
@Component
public class HrmsGatewayClient {

    private final String peopleBaseUrl;
    private final String leaveBaseUrl;
    private final String timeWorkBaseUrl;
    private final String serviceKey;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public HrmsGatewayClient(
            @Value("${report.hrms-base-url:http://127.0.0.1:8081}") String peopleBaseUrl,
            @Value("${report.leave-base-url:http://127.0.0.1:8086}") String leaveBaseUrl,
            @Value("${report.time-work-base-url:http://127.0.0.1:8087}") String timeWorkBaseUrl,
            @Value("${platform.security.service-key}") String serviceKey,
            ObjectMapper mapper) {
        this.peopleBaseUrl = trim(peopleBaseUrl);
        this.leaveBaseUrl = leaveBaseUrl == null || leaveBaseUrl.isBlank() ? this.peopleBaseUrl : trim(leaveBaseUrl);
        this.timeWorkBaseUrl = timeWorkBaseUrl == null || timeWorkBaseUrl.isBlank() ? this.peopleBaseUrl : trim(timeWorkBaseUrl);
        this.serviceKey = serviceKey;
        this.mapper = mapper;
    }

    private static String trim(String u) {
        return u == null ? "" : u.replaceAll("/$", "");
    }

    public JsonNode me(Long employeeId) {
        return getJson(peopleBaseUrl, "/api/me", employeeId, Map.of());
    }

    public Set<String> permissionsFor(Long employeeId) {
        JsonNode me = me(employeeId);
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
        String feat = feature.toLowerCase(Locale.ROOT);
        if ("view".equalsIgnoreCase(action) && perms.contains(feat + ":read")) {
            return true;
        }
        if ("read".equalsIgnoreCase(action) && perms.contains(feat + ":view")) {
            return true;
        }
        // Export implies view for reports (same as FE route gate).
        if ("view".equalsIgnoreCase(action) && perms.contains(feat + ":export")) {
            return true;
        }
        return false;
    }

    /** reports:view scope from /me scopes map — OWN | TEAM | ALL (default OWN). */
    public String reportsViewScope(Long employeeId) {
        JsonNode me = me(employeeId);
        if (me == null || !me.has("scopes") || !me.get("scopes").isObject()) {
            return "OWN";
        }
        JsonNode n = me.get("scopes").get("reports:view");
        if (n == null) {
            n = me.get("scopes").get("reports:export");
        }
        String s = n == null ? "" : n.asText("").trim().toUpperCase(Locale.ROOT);
        if (s.isBlank()) {
            return hasPermission(employeeId, "reports", "export") ? "ALL" : "OWN";
        }
        return s;
    }

    /** Employees visible to the actor (people-service already applies employees:view scope). */
    public List<JsonNode> listEmployees(Long actorEmployeeId) {
        return pageAll(peopleBaseUrl, "/api/employees", actorEmployeeId, Map.of("sort", "fullName,asc"));
    }

    public List<JsonNode> leaveTypesCatalogue(Long actorEmployeeId) {
        JsonNode n = getJson(leaveBaseUrl, "/api/leave-types/catalogue", actorEmployeeId, Map.of());
        if (n == null) {
            return List.of();
        }
        if (n.isArray()) {
            List<JsonNode> out = new ArrayList<>();
            n.forEach(out::add);
            return out;
        }
        if (n.has("content") && n.get("content").isArray()) {
            List<JsonNode> out = new ArrayList<>();
            n.get("content").forEach(out::add);
            return out;
        }
        return List.of();
    }

    public List<JsonNode> leaveBalances(Long actorEmployeeId, int year) {
        // Leave returns a flat array (scoped). pageAll also accepts top-level arrays.
        return pageAll(leaveBaseUrl, "/api/leave-balances", actorEmployeeId,
                Map.of("year", String.valueOf(year)));
    }

    public List<JsonNode> leaveApplications(Long actorEmployeeId, LocalDate from, LocalDate to) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("size", "100");
        q.put("sort", "startDate,desc");
        if (from != null) {
            q.put("from", from.toString());
        }
        if (to != null) {
            q.put("to", to.toString());
        }
        return pageAll(leaveBaseUrl, "/api/leave-applications", actorEmployeeId, q);
    }

    public List<JsonNode> attendanceForEmployee(Long actorEmployeeId, Long targetEmployeeId,
                                               LocalDate from, LocalDate to) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("sort", "workDate,asc");
        if (from != null) {
            q.put("from", from.toString());
        }
        if (to != null) {
            q.put("to", to.toString());
        }
        return pageAll(timeWorkBaseUrl, "/api/attendance/employee/" + targetEmployeeId, actorEmployeeId, q);
    }

    /**
     * Calendar day-book for one employee (same classification as admin Work hours / detail page).
     */
    public JsonNode employeeAttendanceDayBook(Long actorEmployeeId, Long targetEmployeeId,
                                              LocalDate from, LocalDate to) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("from", from.toString());
        q.put("to", to.toString());
        return getJson(timeWorkBaseUrl, "/api/dashboard/employee/" + targetEmployeeId + "/attendance",
                actorEmployeeId, q);
    }

    /** Dashboard work-hours summary rows for a period (already attendance-scoped). */
    public JsonNode workHours(Long actorEmployeeId, LocalDate from, LocalDate to,
                              Long locationId, Long teamId, String q) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("from", from.toString());
        query.put("to", to.toString());
        query.put("page", "1");
        query.put("size", "100");
        query.put("sort", "name");
        if (locationId != null) {
            query.put("locationId", String.valueOf(locationId));
        }
        if (teamId != null) {
            query.put("teamId", String.valueOf(teamId));
        }
        if (q != null && !q.isBlank()) {
            query.put("q", q);
        }
        // Fetch all pages
        List<JsonNode> rows = new ArrayList<>();
        JsonNode totals = null;
        int page = 1;
        int totalPages = 1;
        while (page <= 50) {
            query.put("page", String.valueOf(page));
            JsonNode body = getJson(timeWorkBaseUrl, "/api/dashboard/work-hours", actorEmployeeId, query);
            if (body == null) {
                break;
            }
            if (totals == null && body.has("totals")) {
                totals = body.get("totals");
            }
            if (body.has("rows") && body.get("rows").isArray()) {
                body.get("rows").forEach(rows::add);
            }
            totalPages = Math.max(1, body.path("totalPages").asInt(1));
            if (page >= totalPages) {
                break;
            }
            page++;
        }
        com.fasterxml.jackson.databind.node.ObjectNode out = mapper.createObjectNode();
        out.set("rows", mapper.valueToTree(rows));
        if (totals != null) {
            out.set("totals", totals);
        }
        return out;
    }

    private List<JsonNode> pageAll(String baseUrl, String path, Long actorId, Map<String, String> baseQuery) {
        List<JsonNode> rows = new ArrayList<>();
        int page = 1;
        final int size = 100;
        while (page <= 50) {
            Map<String, String> query = new LinkedHashMap<>(baseQuery);
            query.putIfAbsent("page", String.valueOf(page));
            query.putIfAbsent("size", String.valueOf(size));
            query.put("page", String.valueOf(page));
            query.put("size", String.valueOf(size));
            JsonNode pageJson = getJson(baseUrl, path, actorId, query);
            if (pageJson == null) {
                break;
            }
            JsonNode content = pageJson.has("content") ? pageJson.get("content") : pageJson;
            if (content != null && content.isArray()) {
                int before = rows.size();
                content.forEach(rows::add);
                if (rows.size() == before) {
                    break;
                }
            } else {
                break;
            }
            int totalPages = pageJson.path("totalPages").asInt(page);
            if (!pageJson.has("totalPages") || page >= totalPages) {
                break;
            }
            page++;
        }
        return rows;
    }

    private JsonNode getJson(String baseUrl, String path, Long actorEmployeeId, Map<String, String> query) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            StringBuilder url = new StringBuilder(baseUrl).append(path);
            if (query != null && !query.isEmpty()) {
                url.append('?');
                boolean first = true;
                for (Map.Entry<String, String> e : query.entrySet()) {
                    if (e.getValue() == null || e.getValue().isBlank()) {
                        continue;
                    }
                    if (!first) {
                        url.append('&');
                    }
                    first = false;
                    url.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                            .append('=')
                            .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
                }
            }
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(20))
                    .header(PlatformHeaders.SERVICE_KEY, serviceKey)
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
