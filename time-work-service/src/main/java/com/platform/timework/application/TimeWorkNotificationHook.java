package com.platform.timework.application;

import com.platform.timework.domain.TenantSettings;
import com.platform.timework.infrastructure.persistence.TenantSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.timework.client.people.EmployeeView;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Best-effort push to notification-service for Time&amp;Work events (FR-WL-06).
 * Empty {@code platform.notification.base-url} → log-only (never fails the caller).
 * Event switches: People notification-settings (source of truth), then local timework_settings.
 */
@Component
public class TimeWorkNotificationHook {

    private static final Logger log = LoggerFactory.getLogger(TimeWorkNotificationHook.class);
    private static final long SETTINGS_CACHE_MS = 30_000L;

    private final String baseUrl;
    private final String peopleBaseUrl;
    private final String serviceKey;
    private final String internalKey;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<CachedEvents> peopleEventsCache = new AtomicReference<>();

    public TimeWorkNotificationHook(
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

    /** True when missing-work-log event is enabled (SRS + UI keys; opt-out). */
    public boolean isMissingWorkLogEnabled() {
        return isEventEnabled("MISSING_WORKLOG", "worklog_missing");
    }

    public boolean isCorrectionEnabled() {
        return isEventEnabled("ATT_CORRECTION", "correction");
    }

    public boolean isNoCheckinEnabled() {
        return isEventEnabled("NO_CHECKIN", "missing_checkin");
    }

    public boolean isMissingCheckoutEnabled() {
        return isEventEnabled("MISSING_CHECKOUT", "missing_checkout");
    }

    /** Explicit false on any alias disables; otherwise default on. */
    public boolean isEventEnabled(String... aliases) {
        Boolean explicit = null;
        for (String key : aliases) {
            Boolean v = readEventFlag(key);
            if (v == null) continue;
            if (!v) return false;
            explicit = true;
        }
        return explicit == null || explicit;
    }

    private Boolean readEventFlag(String eventKey) {
        try {
            Map<String, Object> people = loadPeopleEvents();
            if (people.containsKey(eventKey)) {
                Object val = people.get(eventKey);
                if (val instanceof Boolean b) return b;
                if (val != null) return Boolean.parseBoolean(String.valueOf(val));
            }
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

    private record CachedEvents(long atMs, Map<String, Object> events) {}

    /** Correction submitted → manager. */
    public boolean notifyCorrectionSubmitted(
            Long managerId, Long employeeId, String employeeName, Long correctionId, String workDate) {
        if (!isCorrectionEnabled() || managerId == null) return false;
        String name = employeeName == null || employeeName.isBlank() ? ("Employee #" + employeeId) : employeeName;
        return push(managerId,
                "Attendance correction requested",
                name + " requested a correction for " + workDate + " (#" + correctionId + ")",
                "ATTENDANCE",
                "att-correction-submitted-" + correctionId + "-" + managerId,
                "/attendance/corrections");
    }

    /** Correction decided → employee. */
    public boolean notifyCorrectionDecided(Long employeeId, Long correctionId, String status, String workDate) {
        if (!isCorrectionEnabled() || employeeId == null) return false;
        return push(employeeId,
                "Attendance correction " + status,
                "Your correction #" + correctionId + " for " + workDate + " is " + status,
                "ATTENDANCE",
                "att-correction-decided-" + correctionId + "-" + status,
                "/attendance");
    }

    /** No check-in by start + 1 hour. */
    public boolean notifyNoCheckin(Long employeeId, String workDate) {
        if (!isNoCheckinEnabled() || employeeId == null) return false;
        return push(employeeId,
                "Missing check-in",
                "No check-in recorded for " + workDate + " (past start time + 1 hour).",
                "ATTENDANCE",
                "no-checkin-" + employeeId + "-" + workDate,
                "/attendance");
    }

    /** Missing check-out at cut-off. */
    public boolean notifyMissingCheckout(Long employeeId, String workDate) {
        if (!isMissingCheckoutEnabled() || employeeId == null) return false;
        return push(employeeId,
                "Missing check-out",
                "Check-out was not recorded for " + workDate + " by the cut-off.",
                "ATTENDANCE",
                "missing-checkout-" + employeeId + "-" + workDate,
                "/attendance");
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

    /**
     * Ongoing holiday range shortened — notify only employees at that holiday's location.
     * Opt-out via {@code holiday_changed} (default on).
     *
     * @return number of successful pushes
     */
    public int notifyHolidayShortened(
            Long locationId,
            List<EmployeeView> atLocation,
            Long holidayId,
            String holidayName,
            LocalDate previousEnd,
            LocalDate newEnd) {
        if (locationId == null || atLocation == null || atLocation.isEmpty()) {
            return 0;
        }
        if (!isHolidayChangedEnabled()) {
            return 0;
        }
        String label = holidayName == null || holidayName.isBlank() ? "Holiday" : holidayName.trim();
        String body = label + " now ends on " + newEnd
                + " (was " + previousEnd + "). Later days are working days again.";
        int sent = 0;
        for (EmployeeView e : atLocation) {
            if (e == null || e.getId() == null || !e.isActive()) {
                continue;
            }
            if (!locationId.equals(e.getLocationId())) {
                continue; // hard location scope
            }
            boolean ok = push(
                    e.getId(),
                    "Holiday dates updated",
                    body,
                    "HOLIDAY",
                    "holiday_shorten-" + holidayId + "-" + newEnd + "-" + e.getId(),
                    "/leave/calendar");
            if (ok) {
                sent++;
            }
        }
        return sent;
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

    /** Opt-out: holiday range change notices (default on). */
    public boolean isHolidayChangedEnabled() {
        Boolean a = readEventFlag("holiday_changed");
        Boolean b = readEventFlag("HOLIDAY_CHANGED");
        if (a != null) return a;
        if (b != null) return b;
        return true;
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
            case "HOLIDAY" -> "/leave/calendar";
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
