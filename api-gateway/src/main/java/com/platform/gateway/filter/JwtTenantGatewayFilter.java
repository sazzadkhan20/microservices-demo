package com.platform.gateway.filter;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.JwtSupport;
import com.platform.common.security.SecretMatcher;
import com.platform.gateway.config.GatewayProperties;
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
 * Trust boundary:
 * <ul>
 *   <li>strips every client-supplied trust header (tenant, employee, service key, demo key);</li>
 *   <li>verifies the JWT (or the demo-auth key for the account-picker endpoints);</li>
 *   <li>injects trusted identity plus {@code X-Service-Key}, which downstream services require.</li>
 * </ul>
 * Control-plane {@code /internal/**} routes are never reachable through the gateway.
 */
@Component
public class JwtTenantGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtTenantGatewayFilter.class);

    private final JwtSupport jwtSupport;
    private final GatewayProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtTenantGatewayFilter(JwtSupport jwtSupport, GatewayProperties properties) {
        this.jwtSupport = jwtSupport;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String rawPath = exchange.getRequest().getURI().getRawPath();

        if (hasTraversal(rawPath, path)) {
            return reject(exchange, HttpStatus.BAD_REQUEST);
        }
        // Service-to-service endpoints (e.g. registry DB credentials) must never be proxied,
        // because the gateway stamps X-Service-Key on what it forwards.
        if (isInternalPath(path)) {
            return reject(exchange, HttpStatus.NOT_FOUND);
        }

        if (isDemoAuthPath(path)) {
            return handleDemoAuth(exchange, chain, path);
        }

        if (isPublic(path)) {
            // Docs / actuator only: no tenant, no service key (services skip their filter on these paths).
            ServerHttpRequest publicRequest = exchange.getRequest().mutate()
                    .headers(GatewayHeaders::stripTrustHeaders)
                    .build();
            return chain.filter(exchange.mutate().request(publicRequest).build());
        }

        // Platform control-plane: admin key is validated by tenant-registry (not JWT)
        String adminKey = exchange.getRequest().getHeaders().getFirst("X-Platform-Admin-Key");
        if (adminKey != null && !adminKey.isBlank() && isRegistryControlPlane(path)) {
            ServerHttpRequest scrubbed = exchange.getRequest().mutate()
                    .headers(GatewayHeaders::stripTrustHeaders)
                    .header(PlatformHeaders.SERVICE_KEY, properties.getSecurity().getServiceKey())
                    .build();
            return chain.filter(exchange.mutate().request(scrubbed).build());
        }

        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        try {
            String token = auth.substring("Bearer ".length()).trim();
            Claims claims = jwtSupport.parseAndValidate(token);
            String tenantId = jwtSupport.requireTenantId(claims);
            Long employeeId = jwtSupport.employeeIdOrNull(claims);

            ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
                    .headers(GatewayHeaders::stripTrustHeaders)
                    .header(PlatformHeaders.TENANT_ID, tenantId)
                    .header(PlatformHeaders.SERVICE_KEY, properties.getSecurity().getServiceKey());
            if (employeeId != null) {
                builder.header(PlatformHeaders.EMPLOYEE_ID, String.valueOf(employeeId));
            }
            ServerHttpRequest request = builder.build();

            log.debug("Injected tenant={} employee={} path={}", tenantId, employeeId, path);
            return chain.filter(exchange.mutate().request(request).build());
        } catch (Exception ex) {
            log.warn("JWT rejected for path {}: {}", path, ex.getMessage());
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Account picker (token mint + account list): intentionally pre-JWT for the demo, but gated by a
     * shared secret that only the Next.js BFF knows.
     */
    private Mono<Void> handleDemoAuth(ServerWebExchange exchange, GatewayFilterChain chain, String path) {
        GatewayProperties.DemoAuth demo = properties.getDemoAuth();
        if (!demo.isEnabled()) {
            return reject(exchange, HttpStatus.NOT_FOUND);
        }
        String provided = exchange.getRequest().getHeaders().getFirst(PlatformHeaders.DEMO_AUTH_KEY);
        if (!SecretMatcher.matches(provided, demo.getKey())) {
            log.warn("Demo-auth rejected for path {} (missing/invalid {})", path, PlatformHeaders.DEMO_AUTH_KEY);
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
                .headers(GatewayHeaders::stripTrustHeaders)
                .header(PlatformHeaders.SERVICE_KEY, properties.getSecurity().getServiceKey());
        // Account picker: bind tenant from query so HRMS can open the tenant DataSource
        if (isAccountsPath(path)) {
            String tenantId = exchange.getRequest().getQueryParams().getFirst("tenantId");
            if (tenantId != null && !tenantId.isBlank()) {
                builder.header(PlatformHeaders.TENANT_ID, tenantId.trim());
            }
        }
        return chain.filter(exchange.mutate().request(builder.build()).build());
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

    private boolean isInternalPath(String path) {
        return path != null && (path.contains("/internal/") || path.endsWith("/internal"));
    }

    private boolean isAccountsPath(String path) {
        return pathMatcher.match("/hrms/api/auth/accounts", path)
                || pathMatcher.match("/hrms/api/auth/accounts/**", path)
                || pathMatcher.match("/api/auth/accounts", path)
                || pathMatcher.match("/api/auth/accounts/**", path);
    }

    private boolean isTokenPath(String path) {
        return pathMatcher.match("/api/registry/token", path)
                || pathMatcher.match("/registry/token", path);
    }

    private boolean isDemoAuthPath(String path) {
        return isAccountsPath(path) || isTokenPath(path);
    }

    private boolean isPublic(String path) {
        for (String pattern : properties.getPublicPaths()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRegistryControlPlane(String path) {
        if (path == null) {
            return false;
        }
        return pathMatcher.match("/api/registry/provision", path)
                || pathMatcher.match("/api/registry/tenants", path)
                || pathMatcher.match("/api/registry/tenants/**", path)
                || pathMatcher.match("/registry/provision", path)
                || pathMatcher.match("/registry/tenants", path)
                || pathMatcher.match("/registry/tenants/**", path);
    }

    @Override
    public int getOrder() {
        // Run before route RewritePath so public-paths can use gateway prefixes (/hrms/**)
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
