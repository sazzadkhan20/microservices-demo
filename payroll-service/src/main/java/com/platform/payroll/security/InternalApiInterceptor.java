package com.platform.payroll.security;

import com.platform.common.security.InternalCalls;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

/** SEC-06 — require X-Internal-Key on /api/internal/**. */
public final class InternalApiInterceptor implements HandlerInterceptor {

    private final String internalKey;

    public InternalApiInterceptor(String internalKey) {
        this.internalKey = internalKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!InternalCalls.isTrusted(request.getHeader(InternalCalls.HEADER), internalKey)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "X-Internal-Key required");
            return false;
        }
        return true;
    }
}
