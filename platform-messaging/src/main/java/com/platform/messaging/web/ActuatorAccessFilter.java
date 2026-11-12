package com.platform.messaging.web;

import com.platform.common.security.SecretMatcher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Guide §9 — keep operational endpoints private. Liveness / readiness ({@code /actuator/health/**})
 * stay open for the platform probes; every other actuator endpoint (metrics, prometheus, info…)
 * requires {@code Authorization: Bearer <PLATFORM_METRICS_KEY>} (or {@code X-Metrics-Key}).
 * When no key is configured, only loopback callers are served (local development).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ActuatorAccessFilter extends OncePerRequestFilter {

    public static final String METRICS_KEY_HEADER = "X-Metrics-Key";

    private final String metricsKey;

    public ActuatorAccessFilter(@Value("${platform.security.metrics-key:${PLATFORM_METRICS_KEY:}}") String metricsKey) {
        this.metricsKey = metricsKey == null ? "" : metricsKey.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isHealth(path) || allowed(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Not found\"}");
    }

    static boolean isHealth(String path) {
        return "/actuator/health".equals(path) || path.startsWith("/actuator/health/");
    }

    private boolean allowed(HttpServletRequest request) {
        if (!metricsKey.isEmpty()) {
            String header = request.getHeader(METRICS_KEY_HEADER);
            String auth = request.getHeader("Authorization");
            String bearer = auth != null && auth.startsWith("Bearer ") ? auth.substring(7).trim() : null;
            return SecretMatcher.matches(header, metricsKey) || SecretMatcher.matches(bearer, metricsKey);
        }
        return isLoopback(request.getRemoteAddr());
    }

    static boolean isLoopback(String addr) {
        if (addr == null || addr.isBlank()) {
            return false;
        }
        try {
            return InetAddress.getByName(addr).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
