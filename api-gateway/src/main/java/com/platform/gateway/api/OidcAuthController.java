package com.platform.gateway.api;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.InternalCalls;
import com.platform.common.security.JwtSupport;
import com.platform.common.security.SecretMatcher;
import com.platform.gateway.config.GatewayProperties;
import com.platform.gateway.oidc.GoogleIdTokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
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
 * Google OIDC session issuance (guide §4 / SEC-01).
 *
 * <p>Only the Next.js BFF may call this endpoint ({@code X-Oidc-Bff-Key}). The gateway re-verifies
 * the Google ID token, maps identity through People {@code /api/internal/auth/oidc-login}, then
 * mints the platform session JWT. {@code employeeId} is never accepted as login proof.
 */
@RestController
@RequestMapping("/api/auth/oidc")
public class OidcAuthController {

    private static final Logger log = LoggerFactory.getLogger(OidcAuthController.class);
    private static final Duration PEOPLE_TIMEOUT = Duration.ofSeconds(8);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final JwtSupport jwtSupport;
    private final GatewayProperties properties;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final WebClient people;

    public OidcAuthController(
            JwtSupport jwtSupport,
            GatewayProperties properties,
            GoogleIdTokenVerifier googleIdTokenVerifier,
            WebClient.Builder webClientBuilder) {
        this.jwtSupport = jwtSupport;
        this.properties = properties;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.people = webClientBuilder
                .baseUrl(properties.getPeople().getBaseUrl().replaceAll("/+$", ""))
                .build();
    }

    public record OidcTokenRequest(String idToken, String nonce) {
    }

    @PostMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> token(
            @RequestHeader(value = PlatformHeaders.OIDC_BFF_KEY, required = false) String bffKey,
            @RequestBody(required = false) OidcTokenRequest request,
            ServerWebExchange exchange) {
        GatewayProperties.Oidc oidc = properties.getOidc();
        if (!oidc.isEnabled()) {
            return Mono.just(error(HttpStatus.NOT_FOUND, "Not found"));
        }
        if (!SecretMatcher.matches(bffKey, oidc.getBffKey())) {
            log.warn("OIDC token request rejected (missing/invalid {})", PlatformHeaders.OIDC_BFF_KEY);
            return Mono.just(error(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        }
        if (request == null || request.idToken() == null || request.idToken().isBlank()) {
            return Mono.just(error(HttpStatus.BAD_REQUEST, "idToken is required"));
        }

        final GoogleIdTokenVerifier.VerifiedGoogleIdentity identity;
        try {
            identity = googleIdTokenVerifier.verify(request.idToken());
        } catch (IllegalArgumentException ex) {
            return Mono.just(error(HttpStatus.UNAUTHORIZED, ex.getMessage()));
        } catch (IllegalStateException ex) {
            log.error("OIDC misconfigured: {}", ex.getMessage());
            return Mono.just(error(HttpStatus.SERVICE_UNAVAILABLE, "OIDC is not configured"));
        }

        if (!identity.emailVerified()) {
            return Mono.just(error(HttpStatus.UNAUTHORIZED,
                    "Google email is not verified. Use a verified work Google account."));
        }

        ServerHttpRequest http = exchange.getRequest();
        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("googleSub", identity.subject());
        loginBody.put("email", identity.email());
        loginBody.put("emailVerified", true);
        loginBody.put("ip", clientIp(http));
        String ua = http.getHeaders().getFirst("User-Agent");
        if (ua != null) {
            loginBody.put("userAgent", ua);
        }

        return people.post()
                .uri("/api/internal/auth/oidc-login")
                .header(PlatformHeaders.SERVICE_KEY, properties.getSecurity().getServiceKey())
                .header(InternalCalls.HEADER, properties.getSecurity().getInternalKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(loginBody)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .timeout(PEOPLE_TIMEOUT)
                .map(body -> mintSession(body, identity.email()))
                .onErrorResume(WebClientResponseException.class, e -> {
                    String msg = extractPeopleError(e);
                    HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
                    if (status == null) {
                        status = HttpStatus.UNAUTHORIZED;
                    }
                    if (e.getStatusCode().value() >= 500) {
                        log.warn("People OIDC login failed: HTTP {} — {}", e.getStatusCode(), msg);
                        return Mono.just(error(HttpStatus.BAD_GATEWAY, "Identity service unavailable"));
                    }
                    return Mono.just(error(status, msg));
                })
                .onErrorResume(e -> {
                    log.warn("People OIDC login unavailable: {}", e.getMessage());
                    return Mono.just(error(HttpStatus.BAD_GATEWAY, "Identity service unavailable"));
                });
    }

    private ResponseEntity<Map<String, Object>> mintSession(Map<String, Object> peopleBody, String email) {
        if (peopleBody == null || !Boolean.TRUE.equals(peopleBody.get("active"))) {
            return error(HttpStatus.UNAUTHORIZED, "Employee not found or inactive");
        }
        Object idRaw = peopleBody.get("employeeId");
        Long employeeId;
        try {
            employeeId = idRaw instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(idRaw));
        } catch (Exception ex) {
            return error(HttpStatus.BAD_GATEWAY, "Invalid identity response");
        }
        long ttl = Math.max(300L, properties.getSession().getTokenTtlSeconds());
        String subject = email != null && !email.isBlank() ? email : "employee-" + employeeId;
        String jwt = jwtSupport.issueToken(subject, employeeId, ttl);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", jwt);
        body.put("tokenType", "Bearer");
        body.put("expiresInSeconds", ttl);
        body.put("employeeId", employeeId);
        body.put("email", peopleBody.get("email"));
        body.put("fullName", peopleBody.get("fullName"));
        return ResponseEntity.ok(body);
    }

    private static String extractPeopleError(WebClientResponseException e) {
        try {
            Map<String, Object> body = e.getResponseBodyAs(MAP_TYPE);
            if (body != null) {
                for (String k : List.of("message", "error", "detail", "title")) {
                    Object v = body.get(k);
                    if (v instanceof String s && !s.isBlank()) {
                        return s;
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        String reason = e.getStatusText();
        return reason != null && !reason.isBlank() ? reason : "OIDC login failed";
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
