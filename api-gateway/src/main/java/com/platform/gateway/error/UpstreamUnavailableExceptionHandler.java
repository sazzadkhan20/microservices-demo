package com.platform.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.channel.AbortedException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Maps upstream connectivity failures to HTTP 503 with a stable JSON body.
 * Without this, Spring Cloud Gateway surfaces {@link ConnectException} as a raw 500.
 */
@Component
@Order(-2)
public class UpstreamUnavailableExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UpstreamUnavailableExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public UpstreamUnavailableExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!isUpstreamUnavailable(ex)) {
            return Mono.error(ex);
        }
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        String path = exchange.getRequest().getURI().getPath();
        log.warn("Upstream unavailable for {} {}: {}",
                exchange.getRequest().getMethod(), path, rootMessage(ex));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "upstream_unavailable");
        body.put("message", "A required backend service is not reachable");
        body.put("path", path);
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"error\":\"upstream_unavailable\",\"status\":503}".getBytes(StandardCharsets.UTF_8);
        }

        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    static boolean isUpstreamUnavailable(Throwable ex) {
        Throwable t = ex;
        while (t != null) {
            if (t instanceof ConnectException
                    || t instanceof UnknownHostException
                    || t instanceof NoRouteToHostException
                    || t instanceof TimeoutException
                    || t instanceof AbortedException) {
                return true;
            }
            String name = t.getClass().getName();
            // Netty's AnnotatedConnectException is package-private; match by name.
            if (name.endsWith("AnnotatedConnectException") || name.endsWith("ConnectTimeoutException")) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && msg.toLowerCase().contains("connection refused")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
