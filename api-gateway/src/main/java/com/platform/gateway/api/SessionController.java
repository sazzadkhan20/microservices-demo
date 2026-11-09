package com.platform.gateway.api;

import com.platform.common.security.JwtSupport;
import com.platform.common.security.SecretMatcher;
import com.platform.gateway.session.SessionRevocationStore;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Session revoke endpoints (guide §4 — logout + administrative offboarding revocation).
 */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final JwtSupport jwtSupport;
    private final SessionRevocationStore revocationStore;
    private final String adminKey;

    public SessionController(
            JwtSupport jwtSupport,
            SessionRevocationStore revocationStore,
            @Value("${PLATFORM_ADMIN_KEY:}") String adminKey) {
        this.jwtSupport = jwtSupport;
        this.revocationStore = revocationStore;
        this.adminKey = adminKey == null ? "" : adminKey;
    }

    /** Logout: revoke the caller's current JWT jti until natural expiry. */
    @PostMapping("/revoke")
    public Mono<ResponseEntity<Map<String, Object>>> revokeSelf(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "message", "Bearer token required")));
        }
        try {
            Claims claims = jwtSupport.parseAndValidate(auth.substring("Bearer ".length()).trim());
            String jti = jwtSupport.requireJti(claims);
            Duration ttl = remainingTtl(claims);
            return revocationStore.revokeJti(jti, ttl)
                    .thenReturn(ResponseEntity.ok(Map.of("ok", true, "jti", jti)));
        } catch (Exception e) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "message", "Invalid token")));
        }
    }

    /**
     * Offboarding / admin revoke: invalidate all sessions for an employee until TTL.
     * Requires {@code X-Platform-Admin-Key}.
     */
    @PostMapping("/revoke-employee/{employeeId}")
    public Mono<ResponseEntity<Map<String, Object>>> revokeEmployee(
            @PathVariable Long employeeId,
            @RequestHeader(value = "X-Platform-Admin-Key", required = false) String providedAdminKey,
            @RequestParam(defaultValue = "604800") long ttlSeconds) {
        if (adminKey.isBlank() || !SecretMatcher.matches(providedAdminKey, adminKey)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "message", "Admin key required")));
        }
        Duration ttl = Duration.ofSeconds(Math.max(60, ttlSeconds));
        return revocationStore.revokeEmployee(employeeId, ttl)
                .thenReturn(ResponseEntity.ok(Map.of(
                        "ok", true,
                        "employeeId", employeeId,
                        "ttlSeconds", ttl.toSeconds())));
    }

    private static Duration remainingTtl(Claims claims) {
        if (claims.getExpiration() == null) {
            return Duration.ofHours(12);
        }
        long seconds = claims.getExpiration().toInstant().getEpochSecond() - Instant.now().getEpochSecond();
        return Duration.ofSeconds(Math.max(60, seconds));
    }
}
