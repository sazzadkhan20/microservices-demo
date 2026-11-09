package com.platform.gateway.filter;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.correlation.CorrelationIds;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Propagates / generates X-Correlation-ID across the public edge (OIDC-ready, auth-agnostic). */
@Component
public class CorrelationIdGatewayFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(PlatformHeaders.CORRELATION_ID);
        String correlationId = CorrelationIds.sanitizeOrCreate(incoming);
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(PlatformHeaders.CORRELATION_ID, correlationId)
                .build();
        exchange.getResponse().getHeaders().set(PlatformHeaders.CORRELATION_ID, correlationId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
