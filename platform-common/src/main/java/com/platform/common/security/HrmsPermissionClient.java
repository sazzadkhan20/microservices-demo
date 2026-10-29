package com.platform.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constants.PlatformHeaders;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RBAC lives in the HRMS tenant database. Services without that DB (notifications, reports)
 * resolve an employee's permission keys via HRMS {@code GET /api/me} using the already
 * authenticated tenant + employee identity (plus the service key for traceability).
 *
 * <p>Fail-closed: any transport / 5xx failure throws {@link PermissionLookupException};
 * 4xx (unknown employee, etc.) means "no permissions".
 * A short TTL cache avoids one HRMS round-trip per request.
 */
public final class HrmsPermissionClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_CACHE_ENTRIES = 5_000;

    private final String baseUrl;
    private final String serviceKey;
    private final long cacheTtlMillis;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public HrmsPermissionClient(String baseUrl, String serviceKey, Duration cacheTtl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("HRMS base URL is required for permission checks");
        }
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
        this.serviceKey = serviceKey == null ? "" : serviceKey;
        this.cacheTtlMillis = cacheTtl == null ? 0 : Math.max(0, cacheTtl.toMillis());
    }

    /** Permission keys such as {@code reports:view}; may contain {@code *:*}. */
    public Set<String> permissions(String tenantId, Long employeeId) {
        if (tenantId == null || tenantId.isBlank() || employeeId == null) {
            return Set.of();
        }
        String key = tenantId + "|" + employeeId;
        long now = System.currentTimeMillis();
        CacheEntry hit = cache.get(key);
        if (hit != null && hit.expiresAt > now) {
            return hit.keys;
        }
        Set<String> fresh = fetch(tenantId, employeeId);
        if (cacheTtlMillis > 0) {
            if (cache.size() > MAX_CACHE_ENTRIES) {
                cache.clear();
            }
            cache.put(key, new CacheEntry(fresh, now + cacheTtlMillis));
        }
        return fresh;
    }

    public boolean has(String tenantId, Long employeeId, String feature, String action) {
        return grants(permissions(tenantId, employeeId), feature, action);
    }

    /** Wildcards ({@code *:*}, {@code feature:*}) and the view/read alias are honoured. */
    public static boolean grants(Set<String> keys, String feature, String action) {
        if (keys == null || keys.isEmpty()) {
            return false;
        }
        String f = feature.toLowerCase(Locale.ROOT);
        String a = action.toLowerCase(Locale.ROOT);
        if (keys.contains("*:*") || keys.contains(f + ":*") || keys.contains(f + ":" + a)) {
            return true;
        }
        if ("view".equals(a)) {
            return keys.contains(f + ":read");
        }
        if ("read".equals(a)) {
            return keys.contains(f + ":view");
        }
        return false;
    }

    private Set<String> fetch(String tenantId, Long employeeId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/me"))
                .timeout(Duration.ofSeconds(5))
                .header(PlatformHeaders.TENANT_ID, tenantId)
                .header(PlatformHeaders.EMPLOYEE_ID, String.valueOf(employeeId))
                .header(PlatformHeaders.SERVICE_KEY, serviceKey)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 500) {
                throw new PermissionLookupException("HRMS permission lookup failed: HTTP " + status);
            }
            if (status >= 400) {
                return Set.of();
            }
            JsonNode perms = MAPPER.readTree(response.body()).path("permissions");
            Set<String> out = new HashSet<>();
            if (perms.isArray()) {
                perms.forEach(n -> out.add(n.asText("").toLowerCase(Locale.ROOT)));
            }
            return Set.copyOf(out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PermissionLookupException("HRMS permission lookup interrupted", e);
        } catch (IOException e) {
            throw new PermissionLookupException("HRMS permission lookup failed: " + e.getMessage(), e);
        }
    }

    private record CacheEntry(Set<String> keys, long expiresAt) {
    }
}
