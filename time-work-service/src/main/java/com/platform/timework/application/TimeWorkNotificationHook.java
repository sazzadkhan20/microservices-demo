package com.platform.timework.application;

import com.platform.timework.domain.TenantSettings;
import com.platform.timework.infrastructure.persistence.TenantSettingsRepository;
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
 * Best-effort push to notification-service for Time&amp;Work events (FR-WL-06).
 * Empty {@code platform.notification.base-url} → log-only (never fails the caller).
 */
@Component
public class TimeWorkNotificationHook {

    private static final Logger log = LoggerFactory.getLogger(TimeWorkNotificationHook.class);

    private final String baseUrl;
    private final String serviceKey;
    private final String internalKey;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public TimeWorkNotificationHook(
            @Value("${platform.notification.base-url:http://127.0.0.1:8083}") String baseUrl,
            @Value("${platform.security.service-key}") String serviceKey,
            @Value("${platform.security.internal-key}") String internalKey,
            TenantSettingsRepository tenantSettingsRepository) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.serviceKey = serviceKey;
        this.internalKey = internalKey;
        this.tenantSettingsRepository = tenantSettingsRepository;
    }

    /** True when missing-work-log event is enabled (SRS + UI keys; opt-out). */
    public boolean isMissingWorkLogEnabled() {
        Boolean a = readEventFlag("MISSING_WORKLOG");
        Boolean b = readEventFlag("worklog_missing");
        if (a != null) return a;
        if (b != null) return b;
        return true;
    }

    private Boolean readEventFlag(String eventKey) {
        try {
            TenantSettings settings = tenantSettingsRepository.findAll().stream().findFirst().orElse(null);
            if (settings == null || settings.getSettings() == null) return null;
            Object raw = settings.getSettings().get("notificationEvents");
            if (raw instanceof Map<?, ?> m && m.containsKey(eventKey)) {
                Object val = m.get(eventKey);
                if (val instanceof Boolean b) return b;
                if (val != null) return Boolean.parseBoolean(String.valueOf(val));
            }
        } catch (Exception e) {
            log.warn("readEventFlag failed for {}: {}", eventKey, e.getMessage());
        }
        return null;
    }

    /** FR-WL-06 — missing work log for a worked day (dedupe once per employee+date). */
    public boolean notifyMissingWorkLog(Long employeeId, String workDate) {
        if (!isMissingWorkLogEnabled()) return false;
        return push(employeeId,
                "Missing work log",
                "No work log for " + workDate + ". Please add entries for that day.",
                "WORKLOG",
                "worklog_missing-" + employeeId + "-" + workDate,
                "/work-log");
    }

    /** Manager copy — same event switch, separate dedupe key (once per employee+date). */
    public boolean notifyManagerMissingWorkLog(Long managerId, Long employeeId, String employeeName, String workDate) {
        if (!isMissingWorkLogEnabled() || managerId == null) return false;
        String name = employeeName == null || employeeName.isBlank() ? ("Employee #" + employeeId) : employeeName;
        return push(managerId,
                "Team member missing work log",
                name + " has no work log for " + workDate,
                "WORKLOG",
                "worklog_missing_mgr-" + employeeId + "-" + workDate,
                "/team/work-logs");
    }

    /** @return true when notification-service accepted (2xx) or dedupe hit; false on skip/failure. */
    public boolean push(Long employeeId, String title, String bodyText, String category, String dedupeKey, String linkUrl) {
        String safeLink = sanitizeLink(linkUrl != null ? linkUrl : defaultLinkFor(category));
        String body = "{\"employeeId\":" + employeeId
                + ",\"title\":\"" + escape(title) + "\""
                + ",\"body\":\"" + escape(bodyText) + "\""
                + ",\"category\":\"" + escape(category) + "\""
                + ",\"dedupeKey\":\"" + escape(dedupeKey) + "\""
                + (safeLink == null ? "" : ",\"linkUrl\":\"" + escape(safeLink) + "\"")
                + "}";
        if (baseUrl.isBlank()) {
            log.warn("TimeWork notify skipped — platform.notification.base-url is empty (emp={} key={})",
                    employeeId, dedupeKey);
            return false;
        }
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/$", "") + "/api/notifications"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header(com.platform.common.constants.PlatformHeaders.SERVICE_KEY, serviceKey)
                    .header(com.platform.common.security.InternalCalls.HEADER, internalKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (employeeId != null) {
                b.header(com.platform.common.constants.PlatformHeaders.EMPLOYEE_ID, String.valueOf(employeeId));
            }
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                log.warn("TimeWork notification HTTP {} for key={}: {}", resp.statusCode(), dedupeKey, resp.body());
                return false;
            }
            log.info("TimeWork notification ok emp={} key={} status={}", employeeId, dedupeKey, resp.statusCode());
            return true;
        } catch (Exception e) {
            log.warn("TimeWork notification failed (non-fatal) key={}: {}", dedupeKey, e.getMessage());
            return false;
        }
    }

    /** Opt-out model: enabled unless explicitly false. Accepts both SRS and UI keys. */
    public boolean isEnabled(String eventKey) {
        try {
            TenantSettings settings = tenantSettingsRepository.findAll().stream().findFirst().orElse(null);
            if (settings == null || settings.getSettings() == null) return true;
            Object raw = settings.getSettings().get("notificationEvents");
            if (raw instanceof Map<?, ?> m) {
                Object val = m.get(eventKey);
                if (val instanceof Boolean b) return b;
                if (val != null) return Boolean.parseBoolean(String.valueOf(val));
            }
        } catch (Exception e) {
            log.warn("isEnabled check failed for {}: {}", eventKey, e.getMessage());
        }
        return true;
    }

    static String defaultLinkFor(String category) {
        if (category == null) return "/notifications";
        return switch (category.trim().toUpperCase()) {
            case "WORKLOG" -> "/work-log";
            case "ATTENDANCE" -> "/attendance";
            default -> "/notifications";
        };
    }

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
