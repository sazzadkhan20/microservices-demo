package com.platform.payroll.security;

import com.platform.common.security.AuthContext;
import com.platform.payroll.infrastructure.grpc.HrmsEmployeeClient;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Enforces {@link RequiresPermission}. Identity comes from the gateway-injected employee id
 * (bound to {@link AuthContext} by TenantFilter, which only trusts it with a valid service key);
 * permissions come from HRMS. The resolved {@link PermissionSet} is stored on the request so the
 * controller can apply data scoping (own vs. all employees) without a second lookup.
 */
@Component
public class RbacInterceptor implements HandlerInterceptor {

    public static final String PERMISSIONS_ATTR = RbacInterceptor.class.getName() + ".permissions";

    private static final Logger log = LoggerFactory.getLogger(RbacInterceptor.class);

    private final HrmsEmployeeClient hrms;

    public RbacInterceptor(HrmsEmployeeClient hrms) {
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
            return deny(response, HttpStatus.UNAUTHORIZED, "Missing employee identity for permission check");
        }
        PermissionSet permissions;
        try {
            permissions = new PermissionSet(hrms.getPermissions(employeeId));
        } catch (StatusRuntimeException ex) {
            // Fail closed: if HRMS cannot confirm permissions, nothing is granted.
            log.warn("Permission lookup failed for employee {}: {}", employeeId, ex.getStatus());
            return deny(response, HttpStatus.SERVICE_UNAVAILABLE, "Permission check unavailable");
        }
        if (!permissions.has(need.feature(), need.action())) {
            return deny(response, HttpStatus.FORBIDDEN,
                    "Forbidden: requires " + need.feature() + ":" + need.action());
        }
        request.setAttribute(PERMISSIONS_ATTR, permissions);
        return true;
    }

    private static boolean deny(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        return false;
    }
}
