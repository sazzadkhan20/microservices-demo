package com.platform.report.security;

import com.platform.common.security.AuthContext;
import com.platform.report.infrastructure.HrmsGatewayClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class RbacInterceptor implements HandlerInterceptor {

    private final HrmsGatewayClient hrms;

    public RbacInterceptor(HrmsGatewayClient hrms) {
        this.hrms = hrms;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
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
        Long employeeId = AuthContext.getEmployeeId();
        if (employeeId == null) {
            return deny(response, HttpStatus.UNAUTHORIZED, "Missing employee identity");
        }
        if (!hrms.hasPermission(employeeId, need.feature(), need.action())) {
            return deny(response, HttpStatus.FORBIDDEN,
                    "Forbidden: requires " + need.feature() + ":" + need.action());
        }
        return true;
    }

    private static boolean deny(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        return false;
    }
}
