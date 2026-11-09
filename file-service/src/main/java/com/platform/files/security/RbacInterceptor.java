package com.platform.files.security;

import com.platform.common.people.PeopleDirectoryClient;
import com.platform.common.security.AuthContext;
import com.platform.common.security.HrmsPermissionClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class RbacInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RbacInterceptor.class);

    private final ObjectProvider<PeopleDirectoryClient> peopleDirectory;

    public RbacInterceptor(ObjectProvider<PeopleDirectoryClient> peopleDirectory) {
        this.peopleDirectory = peopleDirectory;
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
        if (!hasPermission(employeeId, need.feature(), need.action())) {
            return deny(response, HttpStatus.FORBIDDEN,
                    "Forbidden: requires " + need.feature() + ":" + need.action());
        }
        return true;
    }

    private boolean hasPermission(Long employeeId, String feature, String action) {
        PeopleDirectoryClient people = peopleDirectory.getIfAvailable();
        if (people == null) {
            return false;
        }
        try {
            var authz = people.authz(employeeId);
            Object wild = authz.get("grantsWildcard");
            if (wild instanceof Boolean b && b) {
                return true;
            }
            Object raw = authz.get("permissions");
            Set<String> keys = new HashSet<>();
            if (raw instanceof java.util.Collection<?> col) {
                for (Object o : col) {
                    if (o != null) {
                        keys.add(String.valueOf(o).toLowerCase(Locale.ROOT));
                    }
                }
            }
            return HrmsPermissionClient.grants(keys, feature, action);
        } catch (Exception e) {
            log.warn("Permission lookup failed for employee {}: {}", employeeId, e.getMessage());
            return false;
        }
    }

    private static boolean deny(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        return false;
    }
}
