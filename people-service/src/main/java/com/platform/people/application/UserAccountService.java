package com.platform.people.application;

import com.platform.people.domain.Employee;
import com.platform.people.domain.UserAccount;
import com.platform.people.infrastructure.persistence.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Login identity for employees. Ensures a {@code users} row exists for every employee,
 * records login audit fields, and keeps {@code google_sub} available for future OIDC.
 */
@Service
public class UserAccountService {

    private final UserAccountRepository repository;

    public UserAccountService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserAccount ensureForEmployee(Employee employee) {
        return repository.findByEmployeeId(employee.getId()).orElseGet(() -> {
            Instant now = Instant.now();
            UserAccount u = new UserAccount();
            u.setEmployeeId(employee.getId());
            u.setEmail(normalizeEmail(employee.getEmail()));
            u.setActive(employee.isActive());
            u.setCreatedAt(now);
            u.setUpdatedAt(now);
            return repository.save(u);
        });
    }

    @Transactional
    public UserAccount recordLogin(Employee employee, String ip, String location, String userAgent) {
        UserAccount u = ensureForEmployee(employee);
        Instant now = Instant.now();
        u.setEmail(normalizeEmail(employee.getEmail()));
        u.setActive(employee.isActive());
        u.setLastLoginAt(now);
        if (ip != null && !ip.isBlank()) {
            u.setLastLoginIp(trimTo(ip, 64));
        }
        if (location != null && !location.isBlank()) {
            u.setLastLoginLocation(trimTo(location, 256));
        }
        if (userAgent != null && !userAgent.isBlank()) {
            u.setLastUserAgent(trimTo(userAgent, 512));
        }
        u.setUpdatedAt(now);
        return repository.save(u);
    }

    @Transactional
    public void syncFromEmployee(Employee employee) {
        repository.findByEmployeeId(employee.getId()).ifPresent(u -> {
            u.setEmail(normalizeEmail(employee.getEmail()));
            u.setActive(employee.isActive());
            u.setUpdatedAt(Instant.now());
            repository.save(u);
        });
    }

    @Transactional
    public void deactivate(Long employeeId) {
        repository.findByEmployeeId(employeeId).ifPresent(u -> {
            u.setActive(false);
            u.setUpdatedAt(Instant.now());
            repository.save(u);
        });
    }

    /** Bind Google subject when OIDC lands; no-op safe if already set to same value. */
    @Transactional
    public UserAccount bindGoogleSub(Long employeeId, String googleSub) {
        if (googleSub == null || googleSub.isBlank()) {
            throw new IllegalArgumentException("google_sub is required");
        }
        String sub = googleSub.trim();
        Optional<UserAccount> bySub = repository.findByGoogleSub(sub);
        if (bySub.isPresent() && !bySub.get().getEmployeeId().equals(employeeId)) {
            throw new IllegalStateException("google_sub already linked to another user");
        }
        UserAccount u = repository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("User account not found for employee " + employeeId));
        u.setGoogleSub(sub);
        u.setUpdatedAt(Instant.now());
        return repository.save(u);
    }

    public Optional<UserAccount> findByEmployeeId(Long employeeId) {
        return repository.findByEmployeeId(employeeId);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimTo(String value, int max) {
        String v = value.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }
}
