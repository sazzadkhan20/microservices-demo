package com.platform.gateway.filter;

import com.platform.common.security.SecretMatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Guide §9 — the gateway's own operational endpoints stay private. {@code /actuator/health/**} is open
 * for probes; metrics / prometheus / everything else needs {@code Authorization: Bearer <PLATFORM_METRICS_KEY>}
 * or {@code X-Metrics-Key}. Without a configured key only loopback callers are served (local dev).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ActuatorAccessWebFilter implements WebFilter {

    private final String metricsKey;

    public ActuatorAccessWebFilter(@Value("${platform.security.metrics-key:${PLATFORM_METRICS_KEY:}}") String metricsKey) {
        this.metricsKey = metricsKey == null ? "" : metricsKey.trim();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path == null || !path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }
        if ("/actuator/health".equals(path) || path.startsWith("/actuator/health/") || allowed(exchange)) {
            return chain.filter(exchange);
        }
        exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"error\":\"Not found\"}".getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private boolean allowed(ServerWebExchange exchange) {
        if (!metricsKey.isEmpty()) {
            String header = exchange.getRequest().getHeaders().getFirst("X-Metrics-Key");
            String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
            String bearer = auth != null && auth.startsWith("Bearer ") ? auth.substring(7).trim() : null;
            return SecretMatcher.matches(header, metricsKey) || SecretMatcher.matches(bearer, metricsKey);
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null && remote.getAddress() != null && remote.getAddress().isLoopbackAddress();
    }
}
