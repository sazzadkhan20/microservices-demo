package com.platform.people.client;

import com.platform.common.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * SEC-08 — ask the gateway to revoke all sessions for an offboarded / deactivated employee.
 * TTL defaults to ≥ platform JWT TTL (7d).
 */
@Component
public class SessionRevokeClient {

    private static final Logger log = LoggerFactory.getLogger(SessionRevokeClient.class);

    private final RestClient client;
    private final String adminKey;
    private final boolean enabled;

    public SessionRevokeClient(
            RestClient.Builder builder,
            @Value("${platform.gateway-base-url:http://127.0.0.1:8090}") String gatewayBaseUrl,
            @Value("${PLATFORM_ADMIN_KEY:}") String adminKey,
            @Value("${platform.session-revoke.enabled:true}") boolean enabled) {
        this.adminKey = adminKey == null ? "" : adminKey;
        this.enabled = enabled;
        this.client = builder.baseUrl(gatewayBaseUrl.replaceAll("/$", "")).build();
    }

    public void revokeEmployee(Long employeeId) {
        if (!enabled || employeeId == null) {
            return;
        }
        if (adminKey.isBlank()) {
            log.warn("Session revoke skipped — PLATFORM_ADMIN_KEY not set");
            return;
        }
        String tenant = TenantContext.getTenantId();
        if (tenant == null || tenant.isBlank()) {
            log.warn("Session revoke skipped — no tenant context for employee {}", employeeId);
            return;
        }
        try {
            client.post()
                    .uri(uri -> uri.path("/api/session/revoke-employee/{id}")
                            .queryParam("tenantId", tenant)
                            .queryParam("ttlSeconds", 604800)
                            .build(employeeId))
                    .header("X-Platform-Admin-Key", adminKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // Fail closed on offboarding path: surface so the status change can be retried
            log.error("Session revoke failed for employee {}: {}", employeeId, e.getMessage());
            throw new IllegalStateException("Failed to revoke sessions for employee " + employeeId, e);
        }
    }
}
