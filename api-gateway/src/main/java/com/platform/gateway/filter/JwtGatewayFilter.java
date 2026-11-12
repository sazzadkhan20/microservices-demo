package com.platform.gateway.filter;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.JwtSupport;
import com.platform.common.security.SecretMatcher;
import com.platform.gateway.config.GatewayProperties;
import com.platform.gateway.session.EmployeeSessionGuard;
import com.platform.gateway.session.SessionRevocationStore;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * Trust boundary for proxied routes (single company):
 * <ul>
 *   <li>strips every client-supplied trust header (employee, service key, demo key, admin key);</li>
 *   <li>verifies the session JWT (or the demo-auth key for the account-picker list);</li>
 *   <li>injects the trusted {@code X-Employee-ID} plus {@code X-Service-Key}, which services require.</li>
 * </ul>
 * {@code /internal/**} routes are never reachable through the gateway. Token minting and session
 * revocation are local gateway endpoints ({@code /api/auth/token}, {@code /api/session/**}).
 */
@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtGatewayFilter.class);

    private final JwtSupport jwtSupport;
    private final GatewayProperties properties;
    private final SessionRevocationStore revocationStore;
    private final EmployeeSessionGuard sessionGuard;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtGatewayFilter(
            JwtSupport jwtSupport,
            GatewayProperties properties,
            SessionRevocationStore revocationStore,
            EmployeeSessionGuard sessionGuard) {
        this.jwtSupport = jwtSupport;
        this.properties = properties;
        this.revocationStore = revocationStore;
        this.sessionGuard = sessionGuard;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String rawPath = exchange.getRequest().getURI().getRawPath();

        if (hasTraversal(rawPath, path)) {
            return reject(exchange, HttpStatus.BAD_REQUEST);
        }
        // Service-to-service endpoints must never be proxied: the gateway stamps X-Service-Key on what it forwards.
        if (isInternalPath(path)) {
            return reject(exchange, HttpStatus.NOT_FOUND);
        }
        // Guide §9: service actuator endpoints (metrics, env, heap dumps…) are never proxied.
        if (isProxiedActuatorPath(path)) {
            return reject(exchange, HttpStatus.NOT_FOUND);
        }

        if (isAccountsPath(path)) {
            return handleDemoAccounts(exchange, chain, path);
        }

        if (isPublic(path)) {
            // Docs / actuator only: no identity, no service key (services skip their filter on these paths).
            ServerHttpRequest publicRequest = exchange.getRequest().mutate()
                    .headers(GatewayHeaders::stripTrustHeaders)
                    .build();
            return chain.filter(exchange.mutate().request(publicRequest).build());
        }

        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        try {
            String token = auth.substring("Bearer ".length()).trim();
            Claims claims = jwtSupport.parseAndValidate(token);
            Long employeeId = jwtSupport.requireEmployeeId(claims);
            String jti = jwtSupport.requireJti(claims);
            Long issuedAt = claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant().getEpochSecond();

            return revocationStore.isRevoked(jti, employeeId, issuedAt)
                    .onErrorResume(err -> {
                        log.error("Session revocation store unavailable — denying request: {}", err.getMessage());
                        return Mono.just(true);
                    })
                    .flatMap(revoked -> {
                        if (Boolean.TRUE.equals(revoked)) {
                            log.warn("Revoked session jti={} emp={} path={}", jti, employeeId, path);
                            return reject(exchange, HttpStatus.UNAUTHORIZED);
                        }
                        return sessionGuard.status(employeeId).flatMap(status -> switch (status) {
                            case INACTIVE -> {
                                log.warn("Session refused for inactive employee={} path={}", employeeId, path);
                                yield reject(exchange, HttpStatus.UNAUTHORIZED);
                            }
                            case UNKNOWN -> reject(exchange, HttpStatus.SERVICE_UNAVAILABLE);
                            case ACTIVE -> forward(exchange, chain, employeeId, path);
                        });
                    });
        } catch (Exception ex) {
            log.warn("JWT rejected for path {}: {}", path, ex.getMessage());
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }
    }

    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain, Long employeeId, String path) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(GatewayHeaders::stripTrustHeaders)
                .header(PlatformHeaders.SERVICE_KEY, properties.getSecurity().getServiceKey())
                .header(PlatformHeaders.EMPLOYEE_ID, String.valueOf(employeeId))
                .build();
        log.debug("Injected employee={} path={}", employeeId, path);
        return chain.filter(exchange.mutate().request(request).build());
    }

    /**
     * Account-picker list: intentionally pre-login for the demo, gated by a shared secret only the
     * Next.js BFF holds. Disabled (404) unless {@code platform.demo-auth.enabled=true}.
     */
    private Mono<Void> handleDemoAccounts(ServerWebExchange exchange, GatewayFilterChain chain, String path) {
        GatewayProperties.DemoAuth demo = properties.getDemoAuth();
        if (!demo.isEnabled()) {
            return reject(exchange, HttpStatus.NOT_FOUND);
        }
        String provided = exchange.getRequest().getHeaders().getFirst(PlatformHeaders.DEMO_AUTH_KEY);
        if (!SecretMatcher.matches(provided, demo.getKey())) {
            log.warn("Demo-auth rejected for path {} (missing/invalid {})", path, PlatformHeaders.DEMO_AUTH_KEY);
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(GatewayHeaders::stripTrustHeaders)
                .header(PlatformHeaders.SERVICE_KEY, properties.getSecurity().getServiceKey())
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    private static boolean hasTraversal(String rawPath, String path) {
        if (rawPath == null || path == null) {
            return false;
        }
        String raw = rawPath.toLowerCase(Locale.ROOT);
        return path.contains("..") || path.contains("//") || path.indexOf('\0') >= 0
                || raw.contains("%2e") || raw.contains("%2f") || raw.contains("%5c") || raw.contains("%00");
    }

    /** {@code /<route>/actuator} or {@code /<route>/…/actuator/…} — gateway-own actuator is local, not proxied. */
    static boolean isProxiedActuatorPath(String path) {
        if (path == null) {
            return false;
        }
        String p = path.toLowerCase(Locale.ROOT);
        int idx = p.indexOf("/actuator");
        if (idx <= 0) {
            return false;
        }
        int end = idx + "/actuator".length();
        return end == p.length() || p.charAt(end) == '/';
    }

    private boolean isInternalPath(String path) {
        return path != null && (path.contains("/internal/") || path.endsWith("/internal"));
    }

    private boolean isAccountsPath(String path) {
        return pathMatcher.match("/hrms/api/auth/accounts", path)
                || pathMatcher.match("/hrms/api/auth/accounts/**", path)
                || pathMatcher.match("/people/api/auth/accounts", path)
                || pathMatcher.match("/people/api/auth/accounts/**", path);
    }

    private boolean isPublic(String path) {
        for (String pattern : properties.getPublicPaths()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        // Run before route RewritePath so public-paths can use gateway prefixes (/hrms/**)
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
