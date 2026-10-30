package com.platform.hrms.application;

import com.platform.hrms.domain.TenantSettings;
import com.platform.hrms.infrastructure.persistence.TenantSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Best-effort push to notification-service on leave decisions (in-app only; email excluded).
 * Config: platform.notification.base-url (empty = log-only, never fails leave txn).
 * FR-NT: each push checks tenant_settings.notificationEvents[eventKey] before sending.
 * Each notification carries {@code linkUrl} so the portal can navigate on click.
 */
@Component
public class LeaveNotificationHook {

    private static final Logger log = LoggerFactory.getLogger(LeaveNotificationHook.class);

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private final String serviceKey;
    private final String internalKey;
    private final TenantSettingsRepository tenantSettingsRepository;

    public LeaveNotificationHook(
            @Value("${platform.notification.base-url:http://127.0.0.1:8083}") String baseUrl,
            @Value("${platform.security.service-key:local-service-key-change-me}") String serviceKey,
            @Value("${platform.security.internal-key:"
                    + com.platform.common.security.InternalCalls.DEFAULT_DEV_KEY + "}") String internalKey,
            TenantSettingsRepository tenantSettingsRepository) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.serviceKey = serviceKey;
        this.internalKey = internalKey;
        this.tenantSettingsRepository = tenantSettingsRepository;
    }

    public void notifyLeaveDecision(Long employeeId, Long applicationId, String status, String title) {
        if (!isEnabled("LEAVE_DECISION")) return;
        push(employeeId,
                title,
                "Leave application #" + applicationId + " is now " + status,
                "LEAVE",
                "leave-" + applicationId + "-" + status,
                "/leave");
    }

    /** FR-LV-14 — team clash ALERT: notify manager/team lead / first approver. */
    public void notifyTeamClash(Long employeeId, Long applicationId, String message) {
        if (!isEnabled("TEAM_CLASH")) return;
        push(employeeId,
                "Team Leave Clash Alert",
                message + " (application #" + applicationId + ")",
                "LEAVE",
                "team-clash-" + applicationId,
                "/leave/calendar");
    }

    /** FR-LV-16 — idle approval reminder. */
    public void notifyIdleApproval(Long approverEmployeeId, Long applicationId) {
        if (!isEnabled("LEAVE_IDLE")) return;
        push(approverEmployeeId,
                "Leave approval pending",
                "Leave application #" + applicationId + " has been waiting for your approval for 2+ working days.",
                "LEAVE",
                "leave-idle-" + applicationId + "-" + approverEmployeeId,
                "/approvals");
    }

    /** FR-WL-06 — missing work log for a worked day (dedupe once per employee+date). */
    public void notifyMissingWorkLog(Long employeeId, String workDate) {
        if (!isEnabled("MISSING_WORKLOG")) return;
        push(employeeId,
                "Missing work log",
                "No work log for " + workDate + ". Please add entries for that day.",
                "WORKLOG",
                "worklog_missing-" + employeeId + "-" + workDate,
                "/work-log");
    }

    /**
     * FR-NT — Read tenant_settings.notificationEvents[eventKey].
     * Defaults to true when not explicitly set (opt-out model).
     */
    public boolean isEnabled(String eventKey) {
        try {
            TenantSettings settings = tenantSettingsRepository.findAll().stream().findFirst().orElse(null);
            if (settings == null) return true;
            Object raw = settings.getSettings().get("notificationEvents");
            if (raw instanceof Map<?, ?> m) {
                Object val = m.get(eventKey);
                if (val instanceof Boolean b) return b;
                if (val != null) return Boolean.parseBoolean(String.valueOf(val));
            }
        } catch (Exception e) {
            log.warn("isEnabled check failed for {}: {}", eventKey, e.getMessage());
        }
        return true; // default enabled
    }

    /** Backward-compatible push without deep link (falls back to category default). */
    public void push(Long employeeId, String title, String bodyText, String category, String dedupeKey) {
        push(employeeId, title, bodyText, category, dedupeKey, defaultLinkFor(category));
    }

    public void push(Long employeeId, String title, String bodyText, String category, String dedupeKey, String linkUrl) {
        String safeLink = sanitizeLink(linkUrl != null ? linkUrl : defaultLinkFor(category));
        String body = "{\"employeeId\":" + employeeId
                + ",\"title\":\"" + escape(title) + "\""
                + ",\"body\":\"" + escape(bodyText) + "\""
                + ",\"category\":\"" + escape(category) + "\""
                + ",\"dedupeKey\":\"" + escape(dedupeKey) + "\""
                + (safeLink == null ? "" : ",\"linkUrl\":\"" + escape(safeLink) + "\"")
                + "}";
        if (baseUrl.isBlank()) {
            log.info("Notification (log-only): emp={} cat={} key={} link={}", employeeId, category, dedupeKey, safeLink);
            return;
        }
        try {
            String tenant = com.platform.common.tenant.TenantContext.getTenantId() == null
                    ? "" : com.platform.common.tenant.TenantContext.getTenantId();
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/$", "") + "/api/notifications"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header(com.platform.common.constants.PlatformHeaders.SERVICE_KEY, serviceKey)
                    .header(com.platform.common.security.InternalCalls.HEADER, internalKey)
                    .header(com.platform.common.constants.PlatformHeaders.TENANT_ID, tenant)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (employeeId != null) {
                b.header(com.platform.common.constants.PlatformHeaders.EMPLOYEE_ID, String.valueOf(employeeId));
            }
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                log.warn("Notification HTTP {}: {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("Notification failed (non-fatal): {}", e.getMessage());
        }
    }

    static String defaultLinkFor(String category) {
        if (category == null) return "/notifications";
        return switch (category.trim().toUpperCase()) {
            case "LEAVE" -> "/leave";
            case "WORKLOG" -> "/work-log";
            case "PAYROLL" -> "/payroll";
            case "ATTENDANCE" -> "/attendance";
            case "APPROVAL", "APPROVALS" -> "/approvals";
            case "CHECKLIST", "CHECKLISTS" -> "/checklists";
            default -> "/notifications";
        };
    }

    /** Only allow relative in-app paths (no protocol / host). */
    static String sanitizeLink(String link) {
        if (link == null || link.isBlank()) return null;
        String t = link.trim();
        if (!t.startsWith("/") || t.startsWith("//") || t.contains("://")) return null;
        if (t.length() > 512) t = t.substring(0, 512);
        return t;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
