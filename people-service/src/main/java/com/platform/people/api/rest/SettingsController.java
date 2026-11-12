package com.platform.people.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.people.application.AuditService;
import com.platform.people.domain.TenantSettings;
import com.platform.people.infrastructure.persistence.TenantSettingsRepository;
import com.platform.people.security.RequiresPermission;
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
    private final AuditService auditService;

    public SettingsController(TenantSettingsRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
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
        Map<String, Object> before = new HashMap<>(row.getSettings() == null ? Map.of() : row.getSettings());
        Map<String, Object> merged = new HashMap<>(before);
        merged.putAll(patch);
        row.setSettings(merged);
        row.setUpdatedAt(Instant.now());
        repository.save(row);
        auditService.record(AuthContext.getEmployeeId(), "UPDATE", "TenantSettings",
                row.getId() == null ? "tenant" : String.valueOf(row.getId()), before, merged);
        return merged;
    }

    @GetMapping("/notifications")
    @RequiresPermission(feature = "settings", action = "manage")
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
    @RequiresPermission(feature = "settings", action = "manage")
    @Operation(summary = "Replace notification preference bag")
    public Map<String, Object> putNotifications(@RequestBody Map<String, Object> body) {
        TenantSettings row = repository.findAll().stream().findFirst().orElseGet(TenantSettings::new);
        Map<String, Object> merged = new HashMap<>(row.getSettings() == null ? Map.of() : row.getSettings());
        Object beforeNotif = merged.get("notifications");
        merged.put("notifications", body);
        row.setSettings(merged);
        row.setUpdatedAt(Instant.now());
        repository.save(row);
        auditService.record(AuthContext.getEmployeeId(), "UPDATE", "NotificationSettings",
                row.getId() == null ? "tenant" : String.valueOf(row.getId()),
                beforeNotif == null ? Map.of() : beforeNotif,
                body);
        return body;
    }
}
