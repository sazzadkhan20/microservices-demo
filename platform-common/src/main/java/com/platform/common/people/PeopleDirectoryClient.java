package com.platform.common.people;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constants.PlatformHeaders;
import com.platform.common.http.DependencyUnavailableException;
import com.platform.common.security.InternalCalls;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * S2S client for People-owned employee + RBAC directory (guide §3).
 * Leave / Time&amp;Work must not keep local employee/app_* tables.
 *
 * <p>Resilience (guide §7 deadlines / bounded budgets):
 * <ul>
 *   <li>Every read (single employee, authz and the list lookups) is cached for {@code cacheTtl}
 *       and invalidated by {@code people.employee.upserted} events;</li>
 *   <li>concurrent misses for the same key share one HTTP call (no stampede on People);</li>
 *   <li>when People fails, the last good value is served for up to {@link #STALE_MAX_MILLIS}
 *       (bounded staleness for ordinary reads) instead of failing the caller;</li>
 *   <li>after {@link #BREAKER_THRESHOLD} consecutive failures the client fails fast for
 *       {@link #BREAKER_OPEN_MILLIS} so request threads are not parked on a dead dependency.</li>
 * </ul>
 */
public final class PeopleDirectoryClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST = new TypeReference<>() {};

    static final long STALE_MAX_MILLIS = Duration.ofMinutes(5).toMillis();
    static final int BREAKER_THRESHOLD = 5;
    static final long BREAKER_OPEN_MILLIS = Duration.ofSeconds(10).toMillis();
    private static final int MAX_ENTRIES = 5_000;
    private static final Object NOT_FOUND = new Object();

    private final String baseUrl;
    private final String serviceKey;
    private final String internalKey;
    private final long cacheTtlMillis;
    private final Duration requestTimeout;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Object>> inflight = new ConcurrentHashMap<>();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntil = new AtomicLong();

    public PeopleDirectoryClient(String baseUrl, String serviceKey, String internalKey, Duration cacheTtl) {
        this(baseUrl, serviceKey, internalKey, cacheTtl, Duration.ofSeconds(5));
    }

    public PeopleDirectoryClient(String baseUrl, String serviceKey, String internalKey, Duration cacheTtl,
                                 Duration requestTimeout) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("People base URL is required");
        }
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
        this.serviceKey = serviceKey == null ? "" : serviceKey;
        this.internalKey = internalKey == null ? "" : internalKey;
        this.cacheTtlMillis = cacheTtl == null ? 0 : Math.max(0, cacheTtl.toMillis());
        this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(5) : requestTimeout;
    }

    public Optional<Map<String, Object>> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        Object v = load("e|" + id, () -> getMap("/api/internal/employees/" + id).<Object>map(m -> m).orElse(NOT_FOUND));
        if (v == NOT_FOUND) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        return Optional.of(m);
    }

    public List<Map<String, Object>> findAll() {
        return list("l|all", "/api/internal/employees");
    }

    public List<Map<String, Object>> findByTeamIdAndActiveTrue(Long teamId) {
        if (teamId == null) {
            return List.of();
        }
        return list("l|team|" + teamId, "/api/internal/employees?teamId=" + teamId + "&active=true");
    }

    public List<Map<String, Object>> findByManagerId(Long managerId) {
        if (managerId == null) {
            return List.of();
        }
        return list("l|mgr|" + managerId, "/api/internal/employees?managerId=" + managerId);
    }

    public Map<String, Object> authz(Long employeeId) {
        if (employeeId == null) {
            return Map.of();
        }
        Object v = load("a|" + employeeId,
                () -> getMap("/api/internal/employees/" + employeeId + "/authz").<Object>map(m -> m).orElse(NOT_FOUND));
        if (v == NOT_FOUND) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        return m;
    }

    /** Drop cached People reads after {@code people.employee.upserted} (no local employee SoT). */
    public void invalidateEmployee(Long employeeId) {
        if (employeeId != null) {
            cache.remove("e|" + employeeId);
            cache.remove("a|" + employeeId);
        }
        // Team / manager / full lists may include this employee — drop them all (cheap to rebuild).
        cache.keySet().removeIf(k -> k.startsWith("l|"));
    }

    public void invalidateAll() {
        cache.clear();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(String key, String path) {
        List<Map<String, Object>> cached = (List<Map<String, Object>>) load(key, () -> List.copyOf(getList(path)));
        // Callers may decorate rows; never hand out the cached instances.
        List<Map<String, Object>> out = new ArrayList<>(cached.size());
        for (Map<String, Object> row : cached) {
            out.add(row == null ? null : new LinkedHashMap<>(row));
        }
        return out;
    }

    /** Fresh cache hit → value; miss → one shared fetch; failure → stale value (bounded) or the error. */
    private Object load(String key, Supplier<Object> fetch) {
        long now = System.currentTimeMillis();
        CacheEntry hit = cacheTtlMillis > 0 ? cache.get(key) : null;
        if (hit != null && hit.freshUntil > now) {
            return hit.value;
        }
        if (openUntil.get() > now) {
            if (hit != null && hit.storedAt + STALE_MAX_MILLIS > now) {
                return hit.value;
            }
            throw new DependencyUnavailableException("People directory unavailable (circuit open)");
        }
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> running = inflight.putIfAbsent(key, mine);
        if (running != null) {
            return join(running, hit, now);
        }
        try {
            Object value = fetch.get();
            consecutiveFailures.set(0);
            if (cacheTtlMillis > 0 && value != NOT_FOUND) {
                if (cache.size() > MAX_ENTRIES) {
                    cache.clear();
                }
                long t = System.currentTimeMillis();
                cache.put(key, new CacheEntry(value, t + cacheTtlMillis, t));
            }
            mine.complete(value);
            return value;
        } catch (RuntimeException e) {
            if (consecutiveFailures.incrementAndGet() >= BREAKER_THRESHOLD) {
                openUntil.set(System.currentTimeMillis() + BREAKER_OPEN_MILLIS);
            }
            mine.completeExceptionally(e);
            if (hit != null && hit.storedAt + STALE_MAX_MILLIS > now) {
                return hit.value;
            }
            throw e;
        } finally {
            inflight.remove(key, mine);
        }
    }

    private static Object join(CompletableFuture<Object> running, CacheEntry stale, long now) {
        try {
            return running.join();
        } catch (CompletionException e) {
            if (stale != null && stale.storedAt + STALE_MAX_MILLIS > now) {
                return stale.value;
            }
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new DependencyUnavailableException("People directory failed: " + cause.getMessage(), cause);
        }
    }

    private Optional<Map<String, Object>> getMap(String path) {
        try {
            HttpResponse<String> response = send(path);
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() >= 400) {
                throw new DependencyUnavailableException("People directory HTTP " + response.statusCode() + " for " + path);
            }
            return Optional.ofNullable(MAPPER.readValue(response.body(), MAP));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyUnavailableException("People directory interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new DependencyUnavailableException("People directory failed: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> getList(String path) {
        try {
            HttpResponse<String> response = send(path);
            if (response.statusCode() >= 400) {
                throw new DependencyUnavailableException("People directory HTTP " + response.statusCode() + " for " + path);
            }
            List<Map<String, Object>> list = MAPPER.readValue(response.body(), LIST);
            return list == null ? List.of() : list;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyUnavailableException("People directory interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new DependencyUnavailableException("People directory failed: " + e.getMessage(), e);
        }
    }

    private HttpResponse<String> send(String path) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header(PlatformHeaders.SERVICE_KEY, serviceKey)
                .header(InternalCalls.HEADER, internalKey)
                .header("Accept", "application/json")
                .GET();
        String cid = com.platform.common.correlation.CorrelationHeaders.current();
        if (cid != null) {
            request.header(com.platform.common.correlation.CorrelationHeaders.HEADER, cid);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private record CacheEntry(Object value, long freshUntil, long storedAt) {
    }
}
