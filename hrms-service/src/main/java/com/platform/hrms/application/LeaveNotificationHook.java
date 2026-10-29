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

    public LeaveNotificationHook(
            @Value("${platform.notification.base-url:http://127.0.0.1:8084}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
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
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/$", "") + "/api/notifications"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header("X-Tenant-ID", com.platform.common.tenant.TenantContext.getTenantId() == null
                            ? "" : com.platform.common.tenant.TenantContext.getTenantId())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
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
