package com.platform.hrms.api.rest;

import com.platform.hrms.domain.TenantSettings;
import com.platform.hrms.infrastructure.persistence.TenantSettingsRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Tenant Settings")
public class SettingsController {

    private final TenantSettingsRepository repository;

    public SettingsController(TenantSettingsRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @RequiresPermission(feature = "settings", action = "manage")
    @Operation(summary = "Get tenant settings JSON (config bag)")
    public Map<String, Object> get() {
        return repository.findAll().stream().findFirst()
                .map(TenantSettings::getSettings)
                .orElse(Map.of());
    }

    @PutMapping
    @RequiresPermission(feature = "settings", action = "manage")
    @Operation(summary = "Merge tenant settings (config-driven switches)")
    public Map<String, Object> put(@RequestBody Map<String, Object> patch) {
        TenantSettings row = repository.findAll().stream().findFirst().orElseGet(TenantSettings::new);
        Map<String, Object> merged = new HashMap<>(row.getSettings() == null ? Map.of() : row.getSettings());
        merged.putAll(patch);
        row.setSettings(merged);
        row.setUpdatedAt(Instant.now());
        repository.save(row);
        return merged;
    }

    @GetMapping("/notifications")
    @Operation(summary = "Notification preference bag (stored in tenant_settings.notifications)")
    public Map<String, Object> getNotifications() {
        Map<String, Object> settings = repository.findAll().stream().findFirst()
                .map(TenantSettings::getSettings).orElse(Map.of());
        Object n = settings.get("notifications");
        if (n instanceof Map<?, ?> m) {
            Map<String, Object> out = new HashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return Map.of();
    }

    @PutMapping("/notifications")
    @Operation(summary = "Replace notification preference bag")
    public Map<String, Object> putNotifications(@RequestBody Map<String, Object> body) {
        TenantSettings row = repository.findAll().stream().findFirst().orElseGet(TenantSettings::new);
        Map<String, Object> merged = new HashMap<>(row.getSettings() == null ? Map.of() : row.getSettings());
        merged.put("notifications", body);
        row.setSettings(merged);
        row.setUpdatedAt(Instant.now());
        repository.save(row);
        return body;
    }
}
