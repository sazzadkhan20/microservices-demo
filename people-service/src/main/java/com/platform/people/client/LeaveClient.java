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
        return countPendingApprovals(leavingEmployeeId) > 0;
    }

    public int countPendingApprovals(Long leavingEmployeeId) {
        try {
            Map<?, ?> body = client.get()
                    .uri("/api/internal/approvals/pending-for/{id}", leavingEmployeeId)
                    .header(InternalCalls.HEADER, internalKey)
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                return 0;
            }
            Object count = body.get("count");
            if (count instanceof Number n) {
                return Math.max(0, n.intValue());
            }
            return Boolean.TRUE.equals(body.get("pending")) ? 1 : 0;
        } catch (Exception e) {
            log.warn("Leave pending-approvals check failed: {}", e.getMessage());
            throw new IllegalStateException("Leave service unavailable for offboarding check", e);
        }
    }

    /** @return number of pending approval steps moved to {@code newApproverId} */
    public int reassignPendingApprovals(Long leavingId, Long newApproverId) {
        try {
            Map<?, ?> body = client.post()
                    .uri("/api/internal/approvals/reassign")
                    .header(InternalCalls.HEADER, internalKey)
                    .body(Map.of("leavingId", leavingId, "newApproverId", newApproverId))
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                return 0;
            }
            Object updated = body.get("updated");
            if (updated instanceof Number n) {
                return Math.max(0, n.intValue());
            }
            return 0;
        } catch (Exception e) {
            log.warn("Leave reassign approvals failed: {}", e.getMessage());
            throw new IllegalStateException("Leave service unavailable for approval reassignment", e);
        }
    }

    /**
     * Move pending leave-approval steps for one applicant from old manager → new manager
     * when a reporting-line change becomes effective.
     */
    public int reassignApprovalsForApplicant(Long applicantId, Long fromApproverId, Long toApproverId) {
        if (applicantId == null || fromApproverId == null || toApproverId == null
                || fromApproverId.equals(toApproverId)) {
            return 0;
        }
        try {
            Map<?, ?> body = client.post()
                    .uri("/api/internal/approvals/reassign-for-applicant")
                    .header(InternalCalls.HEADER, internalKey)
                    .body(Map.of(
                            "applicantId", applicantId,
                            "fromApproverId", fromApproverId,
                            "toApproverId", toApproverId))
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                return 0;
            }
            Object updated = body.get("updated");
            if (updated instanceof Number n) {
                return Math.max(0, n.intValue());
            }
            return 0;
        } catch (Exception e) {
            log.warn("Leave reassign-for-applicant failed: {}", e.getMessage());
            throw new IllegalStateException("Leave service unavailable for manager-change approval move", e);
        }
    }
}
