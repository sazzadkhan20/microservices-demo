package com.platform.timework.security;

import com.platform.common.security.AuthContext;
import com.platform.common.security.InternalCalls;
import com.platform.timework.client.people.PeopleAuthzClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RbacInterceptor implements HandlerInterceptor {

    private final PeopleAuthzClient rbacService;
    private final String internalKey;

    public RbacInterceptor(
            PeopleAuthzClient rbacService,
            @Value("${platform.security.internal-key}") String internalKey) {
        this.rbacService = rbacService;
        this.internalKey = internalKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        RequiresPermission need = hm.getMethodAnnotation(RequiresPermission.class);
        if (need == null) {
            need = hm.getBeanType().getAnnotation(RequiresPermission.class);
        }
        if (need == null) {
            return true;
        }
        if (need.allowInternal()
                && InternalCalls.isTrusted(request.getHeader(InternalCalls.HEADER), internalKey)) {
            return true;
        }
        Long employeeId = AuthContext.getEmployeeId();
        if (employeeId == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Missing employee identity for permission check\"}");
            return false;
        }
        if (!rbacService.hasPermission(employeeId, need.feature(), need.action())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Forbidden: requires " + need.feature() + ":" + need.action() + "\"}");
            return false;
        }
        return true;
    }
}
