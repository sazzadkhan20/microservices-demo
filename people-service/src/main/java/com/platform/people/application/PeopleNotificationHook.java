package com.platform.people.application;

import com.platform.people.domain.TenantSettings;
import com.platform.people.infrastructure.persistence.TenantSettingsRepository;
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
 * Best-effort push to notification-service for People lifecycle events (FR-NT).
 * Empty base URL → log-only. Deduped once per event key.
 */
@Component
public class PeopleNotificationHook {

    private static final Logger log = LoggerFactory.getLogger(PeopleNotificationHook.class);

    private final String baseUrl;
    private final String serviceKey;
    private final String internalKey;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public PeopleNotificationHook(
            @Value("${platform.notification-base-url:http://127.0.0.1:8083}") String baseUrl,
            @Value("${platform.security.service-key}") String serviceKey,
            @Value("${platform.security.internal-key}") String internalKey,
            TenantSettingsRepository tenantSettingsRepository) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.serviceKey = serviceKey;
        this.internalKey = internalKey;
        this.tenantSettingsRepository = tenantSettingsRepository;
    }

    public boolean isProbationEndEnabled() {
        return isEventEnabled("PROBATION_END", "contract_end");
    }

    public boolean isDocExpiryEnabled() {
        return isEventEnabled("DOC_EXPIRY", "doc_expiry");
    }

    public boolean isChecklistDueEnabled() {
        return isEventEnabled("CHECKLIST_DUE", "checklist_due");
    }

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

    public boolean notifyContractEnding(
            Long recipientId, Long employeeId, String employeeName, String kind, String endDate) {
        if (!isProbationEndEnabled() || recipientId == null) return false;
        String name = employeeName == null || employeeName.isBlank() ? ("Employee #" + employeeId) : employeeName;
        return push(recipientId,
                kind + " ending soon",
                name + "'s " + kind.toLowerCase() + " ends on " + endDate,
                "PEOPLE",
                "contract-end-" + kind.toLowerCase() + "-" + employeeId + "-" + endDate + "-" + recipientId,
                "/employees/" + employeeId);
    }

    public boolean notifyDocExpiring(Long recipientId, Long employeeId, String employeeName, String docName, String expiresAt) {
        if (!isDocExpiryEnabled() || recipientId == null) return false;
        String name = employeeName == null || employeeName.isBlank() ? ("Employee #" + employeeId) : employeeName;
        String label = docName == null || docName.isBlank() ? "Document" : docName;
        return push(recipientId,
                "Document expiring soon",
                name + ": " + label + " expires on " + expiresAt,
                "PEOPLE",
                "doc-expiry-" + employeeId + "-" + sanitizeKeyPart(expiresAt)
                        + "-" + sanitizeKeyPart(label) + "-" + recipientId,
                "/employees/" + employeeId);
    }

    public boolean notifyChecklistDue(
            Long ownerId,
            Long employeeId,
            String checklistId,
            int itemIndex,
            String checklistName,
            String itemLabel,
            String dueDate,
            boolean overdue) {
        if (!isChecklistDueEnabled() || ownerId == null) return false;
        String title = overdue ? "Checklist item overdue" : "Checklist item due soon";
        String body = (checklistName == null ? "Checklist" : checklistName)
                + ": " + (itemLabel == null ? "item" : itemLabel)
                + (overdue ? " was due " : " is due ") + dueDate
                + (employeeId == null ? "" : " (employee #" + employeeId + ")");
        // Stable once-per-event key: checklist + item index + due date + owner (not free-text label).
        String key = "checklist-due-"
                + sanitizeKeyPart(checklistId == null ? "0" : checklistId)
                + "-i" + itemIndex
                + "-" + sanitizeKeyPart(dueDate)
                + "-o" + ownerId;
        return push(ownerId, title, body, "CHECKLIST", key, "/checklists");
    }

    static String sanitizeKeyPart(String raw) {
        if (raw == null || raw.isBlank()) return "x";
        String s = raw.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    public boolean push(Long employeeId, String title, String bodyText, String category, String dedupeKey, String linkUrl) {
        String safeLink = sanitizeLink(linkUrl);
        String body = "{\"employeeId\":" + employeeId
                + ",\"title\":\"" + escape(title) + "\""
                + ",\"body\":\"" + escape(bodyText) + "\""
                + ",\"category\":\"" + escape(category) + "\""
                + ",\"dedupeKey\":\"" + escape(dedupeKey) + "\""
                + (safeLink == null ? "" : ",\"linkUrl\":\"" + escape(safeLink) + "\"")
                + "}";
        if (baseUrl.isBlank()) {
            log.info("People notification (log-only): emp={} key={}", employeeId, dedupeKey);
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
                log.warn("People notification HTTP {} key={}: {}", resp.statusCode(), dedupeKey, resp.body());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("People notification failed key={}: {}", dedupeKey, e.getMessage());
            return false;
        }
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
