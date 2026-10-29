package com.platform.notification.infrastructure.multitenancy;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.AuthContext;
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
 * Binds Gateway-injected X-Tenant-ID and X-Employee-ID into request-scoped context.
 *
 * <p><b>CRITICAL:</b> always clears both contexts in finally — prevents cross-tenant leaks.
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
            String empHeader = request.getHeader(PlatformHeaders.EMPLOYEE_ID);
            if (empHeader != null && !empHeader.isBlank()) {
                AuthContext.setEmployeeId(Long.parseLong(empHeader.trim()));
            }
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
            TenantContext.clear();
        }
    }
}
