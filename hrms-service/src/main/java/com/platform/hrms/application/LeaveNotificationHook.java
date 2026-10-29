package com.platform.hrms.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Best-effort push to notification-service on leave decisions.
 * Config: platform.notification.base-url (empty = log-only, never fails leave txn).
 */
@Component
public class LeaveNotificationHook {

    private static final Logger log = LoggerFactory.getLogger(LeaveNotificationHook.class);

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private final String serviceKey;

    public LeaveNotificationHook(
            @Value("${platform.notification.base-url:http://127.0.0.1:8083}") String baseUrl,
            @Value("${platform.security.service-key:local-service-key-change-me}") String serviceKey) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.serviceKey = serviceKey;
    }

    public void notifyLeaveDecision(Long employeeId, Long applicationId, String status, String title) {
        String body = "{\"employeeId\":" + employeeId
                + ",\"title\":\"" + escape(title) + "\""
                + ",\"body\":\"Leave application #" + applicationId + " is now " + escape(status) + "\""
                + ",\"category\":\"LEAVE\""
                + ",\"dedupeKey\":\"leave-" + applicationId + "-" + escape(status) + "\"}";
        if (baseUrl.isBlank()) {
            log.info("Leave notification (log-only): emp={} app={} status={}", employeeId, applicationId, status);
            return;
        }
        try {
            String tenant = com.platform.common.tenant.TenantContext.getTenantId() == null
                    ? "" : com.platform.common.tenant.TenantContext.getTenantId();
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/$", "") + "/api/notifications"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    // Trusted service-to-service: service key + recipient identity (controller binds to AuthContext)
                    .header(com.platform.common.constants.PlatformHeaders.SERVICE_KEY, serviceKey)
                    .header(com.platform.common.constants.PlatformHeaders.TENANT_ID, tenant)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (employeeId != null) {
                b.header(com.platform.common.constants.PlatformHeaders.EMPLOYEE_ID, String.valueOf(employeeId));
            }
            HttpRequest req = b.build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                log.warn("Leave notification HTTP {}: {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("Leave notification failed (non-fatal): {}", e.getMessage());
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
