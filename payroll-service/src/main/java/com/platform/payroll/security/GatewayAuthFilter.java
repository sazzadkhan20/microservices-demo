package com.platform.payroll.security;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.AuthContext;
import com.platform.common.security.SecretMatcher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Trust boundary behind the API gateway (single company, fixed DB connection).
 *
 * <p>Only requests carrying the gateway's {@code X-Service-Key} are accepted; the gateway strips
 * client copies and sets its own. The gateway-verified {@code X-Employee-ID} is bound to
 * {@link AuthContext} for the request and always cleared afterwards.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class GatewayAuthFilter extends OncePerRequestFilter {

    private final boolean requireGateway;
    private final String serviceKey;

    public GatewayAuthFilter(
            @Value("${platform.security.require-gateway:true}") boolean requireGateway,
            @Value("${platform.security.service-key}") String serviceKey) {
        this.requireGateway = requireGateway;
        this.serviceKey = serviceKey;
    }

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
        if (requireGateway
                && !SecretMatcher.matches(request.getHeader(PlatformHeaders.SERVICE_KEY), serviceKey)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized: request did not come through the gateway");
            return;
        }
        Long employeeId = null;
        String empHeader = request.getHeader(PlatformHeaders.EMPLOYEE_ID);
        if (empHeader != null && !empHeader.isBlank()) {
            try {
                employeeId = Long.parseLong(empHeader.trim());
            } catch (NumberFormatException ex) {
                reject(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid X-Employee-ID");
                return;
            }
        }
        try {
            if (employeeId != null) {
                AuthContext.setEmployeeId(employeeId);
            }
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
