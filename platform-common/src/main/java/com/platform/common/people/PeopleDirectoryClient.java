package com.platform.common.people;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.InternalCalls;
import com.platform.common.tenant.TenantContext;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * S2S client for People-owned employee + RBAC directory (guide §3).
 * Leave / Time&amp;Work must not keep local employee/app_* tables.
 */
public final class PeopleDirectoryClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST = new TypeReference<>() {};

    private final String baseUrl;
    private final String serviceKey;
    private final String internalKey;
    private final long cacheTtlMillis;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String, CacheEntry> empCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> authzCache = new ConcurrentHashMap<>();

    public PeopleDirectoryClient(String baseUrl, String serviceKey, String internalKey, Duration cacheTtl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("People base URL is required");
        }
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
        this.serviceKey = serviceKey == null ? "" : serviceKey;
        this.internalKey = internalKey == null ? "" : internalKey;
        this.cacheTtlMillis = cacheTtl == null ? 0 : Math.max(0, cacheTtl.toMillis());
    }

    public Optional<Map<String, Object>> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        String tenant = tenant();
        String key = tenant + "|e|" + id;
        Optional<Map<String, Object>> cached = cachedMap(empCache, key);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<Map<String, Object>> fresh = getMap("/api/internal/employees/" + id);
        fresh.ifPresent(m -> putCache(empCache, key, m));
        return fresh;
    }

    public List<Map<String, Object>> findAll() {
        return getList("/api/internal/employees");
    }

    public List<Map<String, Object>> findByTeamIdAndActiveTrue(Long teamId) {
        if (teamId == null) {
            return List.of();
        }
        return getList("/api/internal/employees?teamId=" + teamId + "&active=true");
    }

    public List<Map<String, Object>> findByManagerId(Long managerId) {
        if (managerId == null) {
            return List.of();
        }
        return getList("/api/internal/employees?managerId=" + managerId);
    }

    public Map<String, Object> authz(Long employeeId) {
        if (employeeId == null) {
            return Map.of();
        }
        String tenant = tenant();
        String key = tenant + "|a|" + employeeId;
        Optional<Map<String, Object>> cached = cachedMap(authzCache, key);
        if (cached.isPresent()) {
            return cached.get();
        }
        Map<String, Object> fresh = getMap("/api/internal/employees/" + employeeId + "/authz").orElse(Map.of());
        if (!fresh.isEmpty()) {
            putCache(authzCache, key, fresh);
        }
        return fresh;
    }

    /** Drop cached People reads after {@code people.employee.upserted} (no local employee SoT). */
    public void invalidateEmployee(Long employeeId) {
        String tenant = tenant();
        if (employeeId != null) {
            empCache.remove(tenant + "|e|" + employeeId);
            authzCache.remove(tenant + "|a|" + employeeId);
        }
    }

    public void invalidateAll() {
        empCache.clear();
        authzCache.clear();
    }

    private Optional<Map<String, Object>> getMap(String path) {
        try {
            HttpResponse<String> response = send(path);
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("People directory HTTP " + response.statusCode() + " for " + path);
            }
            return Optional.ofNullable(MAPPER.readValue(response.body(), MAP));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("People directory interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("People directory failed: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> getList(String path) {
        try {
            HttpResponse<String> response = send(path);
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("People directory HTTP " + response.statusCode() + " for " + path);
            }
            List<Map<String, Object>> list = MAPPER.readValue(response.body(), LIST);
            return list == null ? List.of() : list;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("People directory interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("People directory failed: " + e.getMessage(), e);
        }
    }

    private HttpResponse<String> send(String path) throws Exception {
        String tenant = tenant();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(8))
                .header(PlatformHeaders.TENANT_ID, tenant)
                .header(PlatformHeaders.SERVICE_KEY, serviceKey)
                .header(InternalCalls.HEADER, internalKey)
                .header("Accept", "application/json")
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String tenant() {
        String t = TenantContext.getTenantId();
        return t == null ? "" : t;
    }

    private Optional<Map<String, Object>> cachedMap(Map<String, CacheEntry> cache, String key) {
        if (cacheTtlMillis <= 0) {
            return Optional.empty();
        }
        CacheEntry hit = cache.get(key);
        if (hit != null && hit.expiresAt > System.currentTimeMillis()) {
            return Optional.of(hit.value);
        }
        return Optional.empty();
    }

    private void putCache(Map<String, CacheEntry> cache, String key, Map<String, Object> value) {
        if (cacheTtlMillis <= 0) {
            return;
        }
        if (cache.size() > 5_000) {
            cache.clear();
        }
        cache.put(key, new CacheEntry(value, System.currentTimeMillis() + cacheTtlMillis));
    }

    private record CacheEntry(Map<String, Object> value, long expiresAt) {
    }
}
