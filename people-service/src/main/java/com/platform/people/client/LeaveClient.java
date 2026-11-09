package com.platform.people.client;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.InternalCalls;
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
    private final String internalKey;

    public LeaveClient(
            RestClient.Builder builder,
            @Value("${platform.leave-base-url:http://127.0.0.1:8086}") String baseUrl,
            @Value("${platform.security.service-key}") String serviceKey,
            @Value("${platform.security.internal-key}") String internalKey) {
        this.internalKey = internalKey;
        this.client = builder
                .baseUrl(baseUrl.replaceAll("/$", ""))
                .defaultHeader(PlatformHeaders.SERVICE_KEY, serviceKey)
                .build();
    }

    public boolean hasPendingApprovals(Long leavingEmployeeId) {
        try {
            Map<?, ?> body = client.get()
                    .uri("/api/internal/approvals/pending-for/{id}", leavingEmployeeId)
                    .header(InternalCalls.HEADER, internalKey)
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
                    .header(InternalCalls.HEADER, internalKey)
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
                    .header(InternalCalls.HEADER, internalKey)
                    .retrieve()
                    .body(Map.class);
            return body == null ? Map.of() : (Map<String, Object>) body;
        } catch (Exception e) {
            log.warn("Leave facts fetch failed: {}", e.getMessage());
            // PAY-01: fail closed — caller must not invent zeros
            throw new IllegalStateException("Leave service unavailable for payroll facts", e);
        }
    }
}
