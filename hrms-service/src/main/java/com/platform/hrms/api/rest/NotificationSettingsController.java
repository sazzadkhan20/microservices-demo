package com.platform.hrms.api.rest;

import com.platform.hrms.domain.TenantSettings;
import com.platform.hrms.infrastructure.persistence.TenantSettingsRepository;
import com.platform.hrms.security.RequiresPermission;
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
 */
@RestController
@RequestMapping("/api/notification-settings")
@Tag(name = "Notification Settings", description = "Toggle in-app notification events per tenant")
public class NotificationSettingsController {

    /** Canonical set of event keys — config-driven; extend here to add new event types. */
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

    private static final String SETTINGS_KEY = "notificationEvents";

    private final TenantSettingsRepository settingsRepository;

    public NotificationSettingsController(TenantSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @GetMapping
    @RequiresPermission(feature = "settings", action = "view")
    @Operation(summary = "FR-NT Get notification event enable/disable switches (all default true)")
    public Map<String, Object> get() {
        Map<String, Object> stored = storedEvents();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : KNOWN_EVENTS) {
            Object val = stored.get(key);
            // default true when absent
            result.put(key, val instanceof Boolean b ? b : true);
        }
        // preserve any extra keys persisted (forward-compat)
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
        Map<String, Object> events = new LinkedHashMap<>(storedEventsFrom(settings));
        // Only accept boolean values
        patch.forEach((k, v) -> {
            if (v instanceof Boolean b) {
                events.put(k, b);
            } else if (v != null) {
                events.put(k, Boolean.parseBoolean(String.valueOf(v)));
            }
        });
        settings.put(SETTINGS_KEY, events);
        row.setSettings(settings);
        row.setUpdatedAt(Instant.now());
        settingsRepository.save(row);
        // Return full normalised map (defaults filled)
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : KNOWN_EVENTS) {
            Object val = events.get(key);
            result.put(key, val instanceof Boolean b ? b : true);
        }
        events.forEach((k, v) -> result.putIfAbsent(k, v));
        return result;
    }

    private Map<String, Object> storedEvents() {
        Map<String, Object> settings = settingsRepository.findAll().stream().findFirst()
                .map(TenantSettings::getSettings).orElse(Map.of());
        return storedEventsFrom(settings);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> storedEventsFrom(Map<String, Object> settings) {
        Object raw = settings.get(SETTINGS_KEY);
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return new LinkedHashMap<>();
    }
}
