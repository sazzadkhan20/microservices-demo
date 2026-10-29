package com.platform.gateway.filter;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.JwtSupport;
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

/**
 * Trust boundary: strip spoofed tenant/employee headers, verify JWT, inject trusted identity.
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

        if (isPublic(path)) {
            ServerHttpRequest.Builder publicBuilder = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(PlatformHeaders.TENANT_ID);
                        headers.remove(PlatformHeaders.EMPLOYEE_ID);
                    });
            // Account picker: bind tenant from query so HRMS can open the tenant DataSource
            if (path.contains("/api/auth/accounts")) {
                String tenantId = exchange.getRequest().getQueryParams().getFirst("tenantId");
                if (tenantId != null && !tenantId.isBlank()) {
                    publicBuilder.header(PlatformHeaders.TENANT_ID, tenantId.trim());
                }
            }
            return chain.filter(exchange.mutate().request(publicBuilder.build()).build());
        }

        // Platform control-plane: admin key is validated by tenant-registry (not JWT)
        String adminKey = exchange.getRequest().getHeaders().getFirst("X-Platform-Admin-Key");
        if (adminKey != null && !adminKey.isBlank() && isRegistryControlPlane(path)) {
            ServerHttpRequest scrubbed = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(PlatformHeaders.TENANT_ID);
                        headers.remove(PlatformHeaders.EMPLOYEE_ID);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(scrubbed).build());
        }

        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            String token = auth.substring("Bearer ".length()).trim();
            Claims claims = jwtSupport.parseAndValidate(token);
            String tenantId = jwtSupport.requireTenantId(claims);
            Long employeeId = jwtSupport.employeeIdOrNull(claims);

            ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(PlatformHeaders.TENANT_ID);
                        headers.remove(PlatformHeaders.EMPLOYEE_ID);
                    })
                    .header(PlatformHeaders.TENANT_ID, tenantId);
            if (employeeId != null) {
                builder.header(PlatformHeaders.EMPLOYEE_ID, String.valueOf(employeeId));
            }
            ServerHttpRequest request = builder.build();

            log.debug("Injected tenant={} employee={} path={}", tenantId, employeeId, path);
            return chain.filter(exchange.mutate().request(request).build());
        } catch (Exception ex) {
            log.warn("JWT rejected for path {}: {}", path, ex.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isPublic(String path) {
        // Match pre-rewrite (/hrms/...) and post-rewrite (/api/...) account-picker paths
        if (path != null && path.contains("/api/auth/accounts")) {
            return true;
        }
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
