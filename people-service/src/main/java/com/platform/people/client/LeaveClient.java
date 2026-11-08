package com.platform.people.client;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class LeaveClient {

    private static final Logger log = LoggerFactory.getLogger(LeaveClient.class);

    private final RestClient client;

    public LeaveClient(
            RestClient.Builder builder,
            @Value("${platform.leave-base-url:http://127.0.0.1:8086}") String baseUrl,
            @Value("${platform.security.service-key}") String serviceKey) {
        this.client = builder
                .baseUrl(baseUrl.replaceAll("/$", ""))
                .defaultHeader(PlatformHeaders.SERVICE_KEY, serviceKey)
                .build();
    }

    public boolean hasPendingApprovals(Long leavingEmployeeId) {
        try {
            Map<?, ?> body = client.get()
                    .uri("/api/internal/approvals/pending-for/{id}", leavingEmployeeId)
                    .header(PlatformHeaders.TENANT_ID, tenant())
                    .retrieve()
                    .body(Map.class);
            return body != null && Boolean.TRUE.equals(body.get("pending"));
        } catch (Exception e) {
            log.warn("Leave pending-approvals check failed: {}", e.getMessage());
            throw new IllegalStateException("Leave service unavailable for offboarding check", e);
        }
    }

    public void reassignPendingApprovals(Long leavingId, Long newApproverId) {
        try {
            client.post()
                    .uri("/api/internal/approvals/reassign")
                    .header(PlatformHeaders.TENANT_ID, tenant())
                    .body(Map.of("leavingId", leavingId, "newApproverId", newApproverId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Leave reassign approvals failed: {}", e.getMessage());
            throw new IllegalStateException("Leave service unavailable for approval reassignment", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> leaveFacts(Long employeeId, String period) {
        try {
            Map<?, ?> body = client.get()
                    .uri(uri -> uri.path("/api/internal/leave-facts/{id}")
                            .queryParam("period", period)
                            .build(employeeId))
                    .header(PlatformHeaders.TENANT_ID, tenant())
                    .retrieve()
                    .body(Map.class);
            return body == null ? Map.of() : (Map<String, Object>) body;
        } catch (Exception e) {
            log.warn("Leave facts fetch failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private static String tenant() {
        String t = TenantContext.getTenantId();
        return t == null ? "" : t;
    }
}
