package com.platform.gateway.api;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.InternalCalls;
import com.platform.common.security.JwtSupport;
import com.platform.common.security.SecretMatcher;
import com.platform.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo account-picker session issuance.
 *
 * <p>The gateway is the only component that signs and verifies session JWTs. Issuance is gated by
 * {@code X-Demo-Auth-Key} (held only by the Next.js BFF) and is disabled unless
 * {@code platform.demo-auth.enabled=true}; production must use OIDC instead (guide §4).
 * The employee must exist and be active in People before a token is issued; each mint records
 * login audit on People {@code users} (google_sub reserved for OIDC).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthTokenController {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenController.class);
    private static final Duration PEOPLE_TIMEOUT = Duration.ofSeconds(5);

    private final JwtSupport jwtSupport;
    private final GatewayProperties properties;
    private final WebClient people;

    public AuthTokenController(JwtSupport jwtSupport, GatewayProperties properties, WebClient.Builder webClientBuilder) {
        this.jwtSupport = jwtSupport;
        this.properties = properties;
        this.people = webClientBuilder
                .baseUrl(properties.getPeople().getBaseUrl().replaceAll("/+$", ""))
                .build();
    }

    public record TokenRequest(Long employeeId, String subject) {
    }

    @PostMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> token(
            @RequestHeader(value = PlatformHeaders.DEMO_AUTH_KEY, required = false) String demoKey,
            @RequestBody(required = false) TokenRequest request,
            ServerWebExchange exchange) {
        GatewayProperties.DemoAuth demo = properties.getDemoAuth();
        if (!demo.isEnabled()) {
            return Mono.just(error(HttpStatus.NOT_FOUND, "Not found"));
        }
        if (!SecretMatcher.matches(demoKey, demo.getKey())) {
            log.warn("Demo-auth token request rejected (missing/invalid {})", PlatformHeaders.DEMO_AUTH_KEY);
            return Mono.just(error(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        }
        if (request == null || request.employeeId() == null) {
            return Mono.just(error(HttpStatus.BAD_REQUEST, "employeeId is required"));
        }
        Long employeeId = request.employeeId();
        ServerHttpRequest http = exchange.getRequest();
        Map<String, String> loginBody = new LinkedHashMap<>();
        loginBody.put("ip", clientIp(http));
        String ua = http.getHeaders().getFirst("User-Agent");
        if (ua != null) {
            loginBody.put("userAgent", ua);
        }
        return recordLogin(employeeId, loginBody)
                .map(active -> {
                    if (!active) {
                        return error(HttpStatus.UNAUTHORIZED, "Employee not found or inactive: " + employeeId);
                    }
                    long ttl = Math.max(300L, properties.getSession().getTokenTtlSeconds());
                    String subject = request.subject() == null || request.subject().isBlank()
                            ? "employee-" + employeeId
                            : request.subject();
                    String jwt = jwtSupport.issueToken(subject, employeeId, ttl);
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("accessToken", jwt);
                    body.put("tokenType", "Bearer");
                    body.put("expiresInSeconds", ttl);
                    body.put("employeeId", employeeId);
                    return ResponseEntity.ok(body);
                });
    }

    /** Fail closed: People unreachable / non-2xx means no session. Records login on success. */
    private Mono<Boolean> recordLogin(Long employeeId, Map<String, String> loginBody) {
        return people.post()
                .uri("/api/internal/employees/{id}/login", employeeId)
                .header(PlatformHeaders.SERVICE_KEY, properties.getSecurity().getServiceKey())
                .header(InternalCalls.HEADER, properties.getSecurity().getInternalKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(loginBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(PEOPLE_TIMEOUT)
                .map(body -> Boolean.TRUE.equals(body.get("active")))
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode().value() >= 500) {
                        log.warn("People login record failed for employee {}: HTTP {}", employeeId, e.getStatusCode());
                    }
                    return Mono.just(false);
                })
                .onErrorResume(e -> {
                    log.warn("People login unavailable for employee {}: {}", employeeId, e.getMessage());
                    return Mono.just(false);
                });
    }

    private static String clientIp(ServerHttpRequest request) {
        List<String> xff = request.getHeaders().get("X-Forwarded-For");
        if (xff != null && !xff.isEmpty() && xff.get(0) != null && !xff.get(0).isBlank()) {
            return xff.get(0).split(",")[0].trim();
        }
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return null;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
