package com.platform.gateway.session;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.InternalCalls;
import com.platform.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guide §4 offboarding — a session is only honoured while its employee may still sign in.
 * Defence in depth on top of offboarding revocation: covers a lost/expired Redis revocation entry
 * or any status change that did not revoke sessions.
 *
 * <p>Results are cached per employee ({@code platform.session.active-check-ttl-seconds}, default 30 s
 * — the documented bounded delay). If People is unreachable, the last known answer is used for up to
 * 10 minutes; with no answer at all the request gets 503 (not 401, so the browser keeps its cookie).
 */
@Component
public class EmployeeSessionGuard {

    public enum Status { ACTIVE, INACTIVE, UNKNOWN }

    private static final Logger log = LoggerFactory.getLogger(EmployeeSessionGuard.class);
    private static final Duration STALE_MAX = Duration.ofMinutes(10);
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final WebClient people;
    private final GatewayProperties properties;
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();
    private final Map<Long, Mono<Status>> inflight = new ConcurrentHashMap<>();

    public EmployeeSessionGuard(GatewayProperties properties, WebClient.Builder builder) {
        this.properties = properties;
        this.people = builder.clone()
                .baseUrl(properties.getPeople().getBaseUrl().replaceAll("/+$", ""))
                .build();
    }

    public Mono<Status> status(Long employeeId) {
        if (!properties.getSession().isActiveCheckEnabled() || employeeId == null) {
            return Mono.just(Status.ACTIVE);
        }
        long now = System.currentTimeMillis();
        Entry hit = cache.get(employeeId);
        long ttl = Duration.ofSeconds(Math.max(1, properties.getSession().getActiveCheckTtlSeconds())).toMillis();
        if (hit != null && now - hit.at < ttl) {
            return Mono.just(hit.status);
        }
        return inflight.computeIfAbsent(employeeId, id -> fetch(id)
                .doOnNext(s -> {
                    if (cache.size() > 50_000) {
                        cache.clear();
                    }
                    cache.put(id, new Entry(s, System.currentTimeMillis()));
                })
                .onErrorResume(err -> {
                    Entry stale = cache.get(id);
                    if (stale != null && System.currentTimeMillis() - stale.at < STALE_MAX.toMillis()) {
                        log.warn("People session-status unavailable for {} — using last known value: {}", id, err.toString());
                        return Mono.just(stale.status);
                    }
                    log.error("People session-status unavailable for {} and nothing cached: {}", id, err.toString());
                    return Mono.just(Status.UNKNOWN);
                })
                .doFinally(sig -> inflight.remove(id))
                .cache());
    }

    /** Drop the cached answer (e.g. after an administrative revocation). */
    public void forget(Long employeeId) {
        if (employeeId != null) {
            cache.remove(employeeId);
        }
    }

    private Mono<Status> fetch(Long employeeId) {
        return people.get()
                .uri("/api/internal/employees/{id}/session-status", employeeId)
                .header(PlatformHeaders.SERVICE_KEY, properties.getSecurity().getServiceKey())
                .header(InternalCalls.HEADER, properties.getSecurity().getInternalKey())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(TIMEOUT)
                .map(body -> Boolean.TRUE.equals(body.get("active")) ? Status.ACTIVE : Status.INACTIVE)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.just(Status.INACTIVE));
    }

    private record Entry(Status status, long at) {
    }
}
