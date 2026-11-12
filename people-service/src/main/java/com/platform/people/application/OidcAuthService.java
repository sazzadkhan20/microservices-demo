package com.platform.people.application;

import com.platform.people.domain.Employee;
import com.platform.people.domain.UserAccount;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Google OIDC identity binding (FR-AUTH-01–04).
 *
 * <p>First sign-in matches verified Google email to an active employee work email, then binds
 * {@code users.google_sub}. Later sign-ins resolve by {@code google_sub}.
 */
@Service
public class OidcAuthService {

    public static final String NO_EMPLOYEE_MESSAGE =
            "This Google account has no employee record. Ask HR to add you first.";
    public static final String INACTIVE_MESSAGE =
            "This account is not active. Contact HR if you need access restored.";
    public static final String EMAIL_UNVERIFIED_MESSAGE =
            "Google email is not verified. Use a verified work Google account.";
    public static final String SUB_CONFLICT_MESSAGE =
            "This Google account is already linked to another employee.";
    public static final String EMPLOYEE_LINKED_MESSAGE =
            "This employee is already linked to a different Google account. Contact HR.";

    private final EmployeeRepository employeeRepository;
    private final UserAccountService userAccountService;
    private final AuditService auditService;
    private final EmployeeRulesService employeeRulesService;

    public OidcAuthService(
            EmployeeRepository employeeRepository,
            UserAccountService userAccountService,
            AuditService auditService,
            EmployeeRulesService employeeRulesService) {
        this.employeeRepository = employeeRepository;
        this.userAccountService = userAccountService;
        this.auditService = auditService;
        this.employeeRulesService = employeeRulesService;
    }

    public record OidcClaims(String googleSub, String email, boolean emailVerified) {
    }

    public record LoginMeta(String ip, String location, String userAgent) {
    }

    @Transactional
    public Map<String, Object> login(OidcClaims claims, LoginMeta meta) {
        if (claims == null || claims.googleSub() == null || claims.googleSub().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "google_sub is required");
        }
        if (claims.email() == null || claims.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        if (!claims.emailVerified()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, EMAIL_UNVERIFIED_MESSAGE);
        }

        String sub = claims.googleSub().trim();
        String email = claims.email().trim().toLowerCase(Locale.ROOT);

        // Work-email domain gate (FR-AUTH-01); aliases include temporary test domains when configured.
        try {
            employeeRulesService.assertWorkEmail(email, employeeRulesService.load());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }

        Employee employee = resolveEmployee(sub, email);
        if (!isLoginAllowed(employee)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, INACTIVE_MESSAGE);
        }

        UserAccount account = userAccountService.ensureForEmployee(employee);
        if (account.getGoogleSub() == null || account.getGoogleSub().isBlank()) {
            try {
                account = userAccountService.bindGoogleSub(employee.getId(), sub);
            } catch (IllegalStateException ex) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, SUB_CONFLICT_MESSAGE);
            }
        } else if (!account.getGoogleSub().equals(sub)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, EMPLOYEE_LINKED_MESSAGE);
        }

        String ip = meta != null ? meta.ip() : null;
        String location = meta != null ? meta.location() : null;
        String ua = meta != null ? meta.userAgent() : null;
        account = userAccountService.recordLogin(employee, ip, location, ua);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("employeeId", employee.getId());
        after.put("email", employee.getEmail());
        after.put("fullName", employee.getFullName());
        after.put("googleSub", sub);
        after.put("auth", "OIDC");
        if (ip != null && !ip.isBlank()) {
            after.put("ip", ip);
        }
        if (ua != null && !ua.isBlank()) {
            after.put("userAgent", ua.length() > 240 ? ua.substring(0, 240) : ua);
        }
        if (account.getLastLoginAt() != null) {
            after.put("lastLoginAt", account.getLastLoginAt().toString());
        }
        auditService.record(employee.getId(), "SIGN_IN", "UserAccount",
                String.valueOf(account.getId()), null, after, ip);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeId", employee.getId());
        out.put("active", true);
        out.put("email", employee.getEmail());
        out.put("fullName", employee.getFullName());
        out.put("userId", account.getId());
        out.put("googleSub", account.getGoogleSub());
        out.put("lastLoginAt", account.getLastLoginAt() == null ? null : account.getLastLoginAt().toString());
        return out;
    }

    private Employee resolveEmployee(String googleSub, String email) {
        Optional<UserAccount> bySub = userAccountService.findByGoogleSub(googleSub);
        if (bySub.isPresent()) {
            Long employeeId = bySub.get().getEmployeeId();
            return employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, NO_EMPLOYEE_MESSAGE));
        }

        Employee byEmail = employeeRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, NO_EMPLOYEE_MESSAGE));

        Optional<UserAccount> existing = userAccountService.findByEmployeeId(byEmail.getId());
        if (existing.isPresent()
                && existing.get().getGoogleSub() != null
                && !existing.get().getGoogleSub().isBlank()
                && !existing.get().getGoogleSub().equals(googleSub)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, EMPLOYEE_LINKED_MESSAGE);
        }
        return byEmail;
    }

    private static boolean isLoginAllowed(Employee emp) {
        return emp.isActive()
                && (emp.getStatus() == null || "Active".equalsIgnoreCase(emp.getStatus().trim())
                || "On leave".equalsIgnoreCase(emp.getStatus().trim()));
    }
}
