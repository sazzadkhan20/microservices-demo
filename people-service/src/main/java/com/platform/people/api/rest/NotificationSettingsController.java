package com.platform.people.api.rest;

import com.platform.people.domain.TenantSettings;
import com.platform.people.infrastructure.persistence.TenantSettingsRepository;
import com.platform.people.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-NT — In-app notification event switches (email excluded per user decision).
 * Reads/writes tenant_settings.settings.notificationEvents map of eventKey → boolean.
 * All known event keys default to true when absent.
 * <p>Accepts both canonical BE keys and FE snake_case aliases; GET returns both so
 * either UI catalogue works without missing toggles.
 */
@RestController
@RequestMapping("/api/notification-settings")
@Tag(name = "Notification Settings", description = "Toggle in-app notification events per tenant")
public class NotificationSettingsController {

    /** Canonical BE keys. */
    static final List<String> KNOWN_EVENTS = List.of(
            "LEAVE_SUBMITTED",
            "LEAVE_DECISION",
            "LEAVE_IDLE",
            "TEAM_CLASH",
            "ATT_CORRECTION",
            "NO_CHECKIN",
            "MISSING_CHECKOUT",
            "MISSING_WORKLOG",
            "PROBATION_END",
            "DOC_EXPIRY",
            "CHECKLIST_DUE",
            "PAYSLIP_READY"
    );

    /** FE catalogue keys → canonical. */
    private static final Map<String, String> ALIAS_TO_CANONICAL = Map.ofEntries(
            Map.entry("leave_submitted", "LEAVE_SUBMITTED"),
            Map.entry("leave_decided", "LEAVE_DECISION"),
            Map.entry("approval_waiting", "LEAVE_IDLE"),
            Map.entry("team_clash", "TEAM_CLASH"),
            Map.entry("correction", "ATT_CORRECTION"),
            Map.entry("missing_checkin", "NO_CHECKIN"),
            Map.entry("missing_checkout", "MISSING_CHECKOUT"),
            Map.entry("worklog_missing", "MISSING_WORKLOG"),
            Map.entry("contract_end", "PROBATION_END"),
            Map.entry("doc_expiry", "DOC_EXPIRY"),
            Map.entry("checklist_due", "CHECKLIST_DUE"),
            Map.entry("PAYSLIP_READY", "PAYSLIP_READY"),
            Map.entry("payroll", "PAYSLIP_READY")
    );

    private static final Map<String, String> CANONICAL_TO_FE = Map.ofEntries(
            Map.entry("LEAVE_SUBMITTED", "leave_submitted"),
            Map.entry("LEAVE_DECISION", "leave_decided"),
            Map.entry("LEAVE_IDLE", "approval_waiting"),
            Map.entry("TEAM_CLASH", "team_clash"),
            Map.entry("ATT_CORRECTION", "correction"),
            Map.entry("NO_CHECKIN", "missing_checkin"),
            Map.entry("MISSING_CHECKOUT", "missing_checkout"),
            Map.entry("MISSING_WORKLOG", "worklog_missing"),
            Map.entry("PROBATION_END", "contract_end"),
            Map.entry("DOC_EXPIRY", "doc_expiry"),
            Map.entry("CHECKLIST_DUE", "checklist_due"),
            Map.entry("PAYSLIP_READY", "PAYSLIP_READY")
    );

    private static final String SETTINGS_KEY = "notificationEvents";

    private final TenantSettingsRepository settingsRepository;
    private final com.platform.people.application.AuditService auditService;

    public NotificationSettingsController(
            TenantSettingsRepository settingsRepository,
            com.platform.people.application.AuditService auditService) {
        this.settingsRepository = settingsRepository;
        this.auditService = auditService;
    }

    @GetMapping
    @RequiresPermission(feature = "settings", action = "manage", allowInternal = true)
    @Operation(summary = "FR-NT Get notification event enable/disable switches (all default true)")
    public Map<String, Object> get() {
        Map<String, Object> stored = storedEvents();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : KNOWN_EVENTS) {
            boolean on = resolveEnabled(stored, key);
            result.put(key, on);
            String fe = CANONICAL_TO_FE.get(key);
            if (fe != null) {
                result.put(fe, on);
            }
        }
        stored.forEach((k, v) -> result.putIfAbsent(k, v));
        return result;
    }

    @PutMapping
    @RequiresPermission(feature = "settings", action = "manage")
    @Operation(summary = "FR-NT Update notification event switches (eventKey → boolean map)")
    public Map<String, Object> put(@RequestBody Map<String, Object> patch) {
        TenantSettings row = settingsRepository.findAll().stream().findFirst()
                .orElseGet(TenantSettings::new);
        Map<String, Object> settings = new HashMap<>(row.getSettings() == null ? Map.of() : row.getSettings());
        Map<String, Object> beforeEvents = new LinkedHashMap<>(storedEventsFrom(settings));
        Map<String, Object> events = new LinkedHashMap<>(beforeEvents);
        patch.forEach((k, v) -> {
            if (k == null) return;
            Boolean b = null;
            if (v instanceof Boolean bb) b = bb;
            else if (v != null) b = Boolean.parseBoolean(String.valueOf(v));
            if (b == null) return;
            String canonical = ALIAS_TO_CANONICAL.getOrDefault(k, k);
            events.put(canonical, b);
            String fe = CANONICAL_TO_FE.get(canonical);
            if (fe != null) {
                events.put(fe, b);
            } else if (!canonical.equals(k)) {
                events.put(k, b);
            }
        });
        settings.put(SETTINGS_KEY, events);
        row.setSettings(settings);
        row.setUpdatedAt(Instant.now());
        settingsRepository.save(row);
        auditService.record(
                com.platform.common.security.AuthContext.getEmployeeId(),
                "UPDATE",
                "NotificationEventSettings",
                row.getId() == null ? "tenant" : String.valueOf(row.getId()),
                beforeEvents,
                events);
        return get();
    }

    private static boolean resolveEnabled(Map<String, Object> stored, String canonical) {
        Boolean explicit = null;
        for (String key : List.of(canonical, CANONICAL_TO_FE.getOrDefault(canonical, canonical))) {
            if (!stored.containsKey(key)) continue;
            Object val = stored.get(key);
            boolean on = val instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(val));
            if (!on) return false;
            explicit = true;
        }
        return explicit == null || explicit;
    }

    private Map<String, Object> storedEvents() {
        TenantSettings row = settingsRepository.findAll().stream().findFirst().orElse(null);
        if (row == null || row.getSettings() == null) return Map.of();
        return storedEventsFrom(row.getSettings());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> storedEventsFrom(Map<String, Object> settings) {
        Object raw = settings.get(SETTINGS_KEY);
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> {
                if (k != null) out.put(String.valueOf(k), v);
            });
            return out;
        }
        return Map.of();
    }
}
