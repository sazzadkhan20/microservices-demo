package com.platform.messaging.config;

import com.platform.common.constants.PlatformHeaders;
import com.platform.messaging.correlation.CorrelationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Propagates / generates X-Correlation-ID for every HTTP request (guide observability).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(PlatformHeaders.CORRELATION_ID);
        String correlationId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString()
                : incoming.trim();
        try {
            CorrelationContext.set(correlationId);
            response.setHeader(PlatformHeaders.CORRELATION_ID, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            CorrelationContext.clear();
        }
    }
}
