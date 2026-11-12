package com.platform.people.api.rest;

import com.platform.common.security.InternalCalls;
import com.platform.people.application.AuditService;
import com.platform.people.application.UserAccountService;
import com.platform.people.domain.Employee;
import com.platform.people.domain.UserAccount;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import com.platform.people.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service-to-service auth checks (gateway login token mint). Not exposed through the gateway
 * ({@code /internal/**} is blocked there); callers hit People directly with service + internal keys.
 * Login writes {@code users.last_login_*} and a {@code SIGN_IN} row in {@code people_audit_log}.
 */
@RestController
@RequestMapping("/api/internal")
@Hidden
public class InternalAuthController {

    private final EmployeeRepository employeeRepository;
    private final UserAccountService userAccountService;
    private final AuditService auditService;
    private final String internalKey;

    public InternalAuthController(
            EmployeeRepository employeeRepository,
            UserAccountService userAccountService,
            AuditService auditService,
            @Value("${platform.security.internal-key}") String internalKey) {
        this.employeeRepository = employeeRepository;
        this.userAccountService = userAccountService;
        this.auditService = auditService;
        this.internalKey = internalKey;
    }

    public record LoginBody(String ip, String location, String userAgent) {
    }

    @GetMapping("/employees/{id}/auth-check")
    @RequiresPermission(feature = "employees", action = "view", allowInternal = true)
    public Map<String, Object> authCheck(@PathVariable Long id, HttpServletRequest request) {
        requireInternal(request);
        Employee emp = requireEmployee(id);
        // Ensure login row exists even when only checking (demo account list / future OIDC).
        userAccountService.ensureForEmployee(emp);
        return authBody(emp, null);
    }

    /**
     * Gateway calls this when minting a session: validates active employee, ensures {@code users}
     * row, records last_login_* fields, returns auth payload.
     */
    @PostMapping("/employees/{id}/login")
    @RequiresPermission(feature = "employees", action = "view", allowInternal = true)
    public Map<String, Object> recordLogin(
            @PathVariable Long id,
            @RequestBody(required = false) LoginBody body,
            HttpServletRequest request) {
        requireInternal(request);
        Employee emp = requireEmployee(id);
        boolean active = isLoginAllowed(emp);
        if (!active) {
            Map<String, Object> out = authBody(emp, null);
            out.put("active", false);
            return out;
        }
        String ip = body != null && body.ip() != null && !body.ip().isBlank()
                ? body.ip()
                : clientIp(request);
        String ua = body != null && body.userAgent() != null && !body.userAgent().isBlank()
                ? body.userAgent()
                : request.getHeader("User-Agent");
        String location = body != null ? body.location() : null;
        UserAccount account = userAccountService.recordLogin(emp, ip, location, ua);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("employeeId", emp.getId());
        after.put("email", emp.getEmail());
        after.put("fullName", emp.getFullName());
        after.put("ip", ip);
        if (location != null && !location.isBlank()) {
            after.put("location", location);
        }
        if (ua != null && !ua.isBlank()) {
            after.put("userAgent", ua.length() > 240 ? ua.substring(0, 240) : ua);
        }
        if (account.getLastLoginAt() != null) {
            after.put("lastLoginAt", account.getLastLoginAt().toString());
        }
        auditService.record(emp.getId(), "SIGN_IN", "UserAccount",
                String.valueOf(account.getId()), null, after, ip);
        return authBody(emp, account);
    }

    private void requireInternal(HttpServletRequest request) {
        if (!InternalCalls.isTrusted(request.getHeader(InternalCalls.HEADER), internalKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal key required");
        }
    }

    private Employee requireEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    private static boolean isLoginAllowed(Employee emp) {
        return emp.isActive()
                && (emp.getStatus() == null || "Active".equalsIgnoreCase(emp.getStatus().trim())
                || "On leave".equalsIgnoreCase(emp.getStatus().trim()));
    }

    private static Map<String, Object> authBody(Employee emp, UserAccount account) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeId", emp.getId());
        out.put("active", isLoginAllowed(emp));
        out.put("email", emp.getEmail());
        out.put("fullName", emp.getFullName());
        if (account != null) {
            out.put("userId", account.getId());
            out.put("googleSub", account.getGoogleSub());
            out.put("lastLoginAt", account.getLastLoginAt() == null ? null : account.getLastLoginAt().toString());
        }
        return out;
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
