package com.platform.hrms.api.rest;

import com.platform.common.security.InternalCalls;
import com.platform.hrms.domain.Employee;
import com.platform.hrms.infrastructure.persistence.EmployeeRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service-to-service auth checks (registry token mint). Not exposed through the gateway
 * ({@code /internal/**} is blocked there); callers hit HRMS directly with service + internal keys.
 */
@RestController
@RequestMapping("/api/internal")
@Hidden
public class InternalAuthController {

    private final EmployeeRepository employeeRepository;
    private final String internalKey;

    public InternalAuthController(
            EmployeeRepository employeeRepository,
            @Value("${platform.security.internal-key:" + InternalCalls.DEFAULT_DEV_KEY + "}") String internalKey) {
        this.employeeRepository = employeeRepository;
        this.internalKey = internalKey;
    }

    @GetMapping("/employees/{id}/auth-check")
    @RequiresPermission(feature = "employees", action = "view", allowInternal = true)
    public Map<String, Object> authCheck(@PathVariable Long id, HttpServletRequest request) {
        if (!InternalCalls.isTrusted(request.getHeader(InternalCalls.HEADER), internalKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal key required");
        }
        Employee emp = employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        boolean active = emp.isActive()
                && (emp.getStatus() == null || "Active".equalsIgnoreCase(emp.getStatus().trim()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeId", emp.getId());
        out.put("active", active);
        out.put("email", emp.getEmail());
        out.put("fullName", emp.getFullName());
        return out;
    }
}
