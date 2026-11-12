package com.platform.leave.application;

import com.platform.leave.domain.TenantSettings;
import com.platform.leave.infrastructure.persistence.TenantSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Best-effort push to notification-service on leave decisions (in-app only; email excluded).
 * Config: platform.notification.base-url (empty = log-only, never fails leave txn).
 * FR-NT: event switches come from People {@code /api/notification-settings} (source of truth),
 * with leave_settings as fallback. Defaults to enabled (opt-out).
 */
@Component
public class LeaveNotificationHook {

    private static final Logger log = LoggerFactory.getLogger(LeaveNotificationHook.class);
    private static final long SETTINGS_CACHE_MS = 30_000L;

    private final String baseUrl;
    private final String peopleBaseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String serviceKey;
    private final String internalKey;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final AtomicReference<CachedEvents> peopleEventsCache = new AtomicReference<>();

    public LeaveNotificationHook(
            @Value("${platform.notification.base-url:http://127.0.0.1:8083}") String baseUrl,
            @Value("${platform.people-base-url:${platform.hrms-base-url:http://127.0.0.1:8081}}") String peopleBaseUrl,
            @Value("${platform.security.service-key}") String serviceKey,
            @Value("${platform.security.internal-key}") String internalKey,
            TenantSettingsRepository tenantSettingsRepository) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.peopleBaseUrl = peopleBaseUrl == null ? "" : peopleBaseUrl.trim();
        this.serviceKey = serviceKey;
        this.internalKey = internalKey;
        this.tenantSettingsRepository = tenantSettingsRepository;
    }

    public void notifyLeaveDecision(Long employeeId, Long applicationId, String status, String title) {
        notifyLeaveDecision(employeeId, applicationId, status, title, "final");
    }

    /**
     * @param dedupeSuffix distinguishes mid-chain / manager copies (once per event+recipient path).
     */
    public void notifyLeaveDecision(
            Long employeeId, Long applicationId, String status, String title, String dedupeSuffix) {
        if (employeeId == null) return;
        if (!isEnabled("LEAVE_DECISION")) return;
        String suffix = dedupeSuffix == null || dedupeSuffix.isBlank() ? "final" : dedupeSuffix.trim();
        push(employeeId,
                title,
                "Leave application #" + applicationId + " is now " + status,
                "LEAVE",
                "leave-" + applicationId + "-" + status + "-" + suffix + "-" + employeeId,
                "/leave");
    }

    /** FR-NT-01 — notify the current-step approver that a leave was submitted. */
    public void notifyLeaveSubmitted(Long approverEmployeeId, Long applicationId, String applicantName) {
        if (approverEmployeeId == null) return;
        if (!isEnabled("LEAVE_SUBMITTED")) return;
        String who = applicantName == null || applicantName.isBlank() ? "An employee" : applicantName;
        push(approverEmployeeId,
                "Leave approval needed",
                who + " submitted leave application #" + applicationId,
                "LEAVE",
                "leave-submitted-" + applicationId + "-" + approverEmployeeId,
                "/approvals");
    }

    /** FR-LV-18 — notify current-step approver that an approved leave cancel was requested. */
    public void notifyLeaveCancelRequested(Long approverEmployeeId, Long applicationId, String applicantName) {
        if (approverEmployeeId == null) return;
        // Reuse LEAVE_SUBMITTED toggle so packs without a separate cancel flag still notify.
        if (!isEnabled("LEAVE_SUBMITTED") && !isEnabled("LEAVE_CANCEL_REQUEST")) return;
        String who = applicantName == null || applicantName.isBlank() ? "An employee" : applicantName;
        push(approverEmployeeId,
                "Leave cancellation approval needed",
                who + " requested cancellation of leave #" + applicationId,
                "LEAVE",
                "leave-cancel-" + applicationId + "-" + approverEmployeeId,
                "/approvals");
    }

    /** FR-LV-14 — team clash ALERT: notify manager / team lead / first approver (not the applicant). */
    public void notifyTeamClash(Long recipientEmployeeId, Long applicationId, String message) {
        if (recipientEmployeeId == null) return;
        if (!isEnabled("TEAM_CLASH")) return;
        push(recipientEmployeeId,
                "Team Leave Clash Alert",
                message + " (application #" + applicationId + ")",
                "LEAVE",
                "team-clash-" + applicationId + "-" + recipientEmployeeId,
                "/leave/calendar");
    }

    /** FR-LV-16 — idle approval reminder. */
    public void notifyIdleApproval(Long approverEmployeeId, Long applicationId) {
        if (approverEmployeeId == null) return;
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
     * FR-NT — People notification-settings first, then local leave_settings.
     * Accepts FE snake_case aliases; defaults to true (opt-out). Explicit false wins.
     */
    public boolean isEnabled(String eventKey) {
        try {
            Map<String, Object> people = loadPeopleEvents();
            Boolean fromPeople = flagFromMap(people, eventKey);
            if (fromPeople != null) return fromPeople;

            TenantSettings settings = tenantSettingsRepository.findAll().stream().findFirst().orElse(null);
            if (settings == null || settings.getSettings() == null) return true;
            Object raw = settings.getSettings().get("notificationEvents");
            if (!(raw instanceof Map<?, ?> m)) return true;
            Boolean fromLocal = flagFromMap(cast(m), eventKey);
            return fromLocal == null || fromLocal;
        } catch (Exception e) {
            log.warn("isEnabled check failed for {}: {}", eventKey, e.getMessage());
        }
        return true; // default enabled
    }

    private Boolean flagFromMap(Map<String, Object> m, String eventKey) {
        if (m == null || m.isEmpty()) return null;
        Boolean explicit = null;
        for (String key : eventKeyAliases(eventKey)) {
            if (!m.containsKey(key)) continue;
            Object val = m.get(key);
            boolean on = val instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(val));
            if (!on) return false;
            explicit = true;
        }
        return explicit;
    }

    private Map<String, Object> loadPeopleEvents() {
        CachedEvents cached = peopleEventsCache.get();
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.atMs < SETTINGS_CACHE_MS) {
            return cached.events;
        }
        if (peopleBaseUrl.isBlank()) {
            return Map.of();
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(peopleBaseUrl.replaceAll("/$", "") + "/api/notification-settings"))
                    .timeout(Duration.ofSeconds(2))
                    .header(com.platform.common.constants.PlatformHeaders.SERVICE_KEY, serviceKey)
                    .header(com.platform.common.security.InternalCalls.HEADER, internalKey)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300 || resp.body() == null || resp.body().isBlank()) {
                return cached != null ? cached.events : Map.of();
            }
            Map<String, Object> parsed = mapper.readValue(resp.body(), new TypeReference<>() {});
            peopleEventsCache.set(new CachedEvents(now, parsed));
            return parsed;
        } catch (Exception e) {
            log.debug("People notification-settings fetch failed: {}", e.getMessage());
            return cached != null ? cached.events : Map.of();
        }
    }

    private static Map<String, Object> cast(Map<?, ?> m) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        m.forEach((k, v) -> {
            if (k != null) out.put(String.valueOf(k), v);
        });
        return out;
    }

    private record CachedEvents(long atMs, Map<String, Object> events) {}

    /** Canonical BE key ↔ FE settings page keys. */
    static String[] eventKeyAliases(String eventKey) {
        if (eventKey == null) return new String[0];
        return switch (eventKey) {
            case "LEAVE_SUBMITTED", "leave_submitted" ->
                    new String[]{"LEAVE_SUBMITTED", "leave_submitted"};
            case "LEAVE_DECISION", "leave_decided" ->
                    new String[]{"LEAVE_DECISION", "leave_decided"};
            case "LEAVE_IDLE", "approval_waiting" ->
                    new String[]{"LEAVE_IDLE", "approval_waiting"};
            case "TEAM_CLASH", "team_clash" ->
                    new String[]{"TEAM_CLASH", "team_clash"};
            case "MISSING_WORKLOG", "worklog_missing" ->
                    new String[]{"MISSING_WORKLOG", "worklog_missing"};
            case "LEAVE_CANCEL_REQUEST" ->
                    new String[]{"LEAVE_CANCEL_REQUEST", "LEAVE_SUBMITTED", "leave_submitted"};
            default -> new String[]{eventKey};
        };
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
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/$", "") + "/api/notifications"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header(com.platform.common.constants.PlatformHeaders.SERVICE_KEY, serviceKey)
                    .header(com.platform.common.security.InternalCalls.HEADER, internalKey)
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
