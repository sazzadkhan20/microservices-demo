package com.platform.payroll.infrastructure.multitenancy;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads gateway-injected {@code X-Tenant-ID} and binds it to {@link TenantContext}.
 *
 * <p>Swagger/actuator paths are allowed without tenant (so you can open Swagger UI).
 * Business APIs require the header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String tenantId = request.getHeader(PlatformHeaders.TENANT_ID);
        if (tenantId == null || tenantId.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Missing X-Tenant-ID (gateway must inject it)\"}");
            return;
        }
        try {
            TenantContext.setTenantId(tenantId.trim());
            filterChain.doFilter(request, response);
        } finally {
            // Prevent ThreadLocal leakage across pooled threads
            TenantContext.clear();
        }
    }
}
