package com.platform.hrms.api.rest;

import com.platform.hrms.domain.TenantSettings;
import com.platform.hrms.infrastructure.persistence.TenantSettingsRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Onboarding/offboarding checklists — templates from tenant_settings.checklists,
 * with built-in defaults when unset (prototype parity).
 */
@RestController
@RequestMapping("/api/checklists")
@Tag(name = "Checklists")
public class ChecklistController {

    private final TenantSettingsRepository settingsRepository;

    public ChecklistController(TenantSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @GetMapping
    @RequiresPermission(feature = "org", action = "view")
    @Operation(summary = "List onboarding/offboarding checklists")
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> list() {
        Map<String, Object> settings = settingsRepository.findAll().stream().findFirst()
                .map(TenantSettings::getSettings).orElse(Map.of());
        Object raw = settings.get("checklists");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    m.forEach((k, v) -> row.put(String.valueOf(k), v));
                    out.add(row);
                }
            }
            return out;
        }
        return defaultTemplates();
    }

    private static List<Map<String, Object>> defaultTemplates() {
        return List.of(
                checklist("1", "New Hire Onboarding", "ONBOARDING", "OPEN",
                        List.of(
                                item("Laptop / workstation ready", true, false),
                                item("Email & SSO account created", true, false),
                                item("HRMS profile + role assigned", true, false),
                                item("Team introduction scheduled", false, false),
                                item("Policy acknowledgment signed", true, false)
                        )),
                checklist("2", "Employee Offboarding", "OFFBOARDING", "OPEN",
                        List.of(
                                item("Asset return checklist", true, false),
                                item("Access revoked (email/SSO/VPN)", true, false),
                                item("Final payroll / leave settlement", true, false),
                                item("Exit interview completed", false, false)
                        ))
        );
    }

    private static Map<String, Object> checklist(String id, String name, String type, String status,
                                                 List<Map<String, Object>> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("type", type);
        m.put("status", status);
        m.put("items", items);
        m.put("totalItems", items.size());
        m.put("completedItems", items.stream().filter(i -> Boolean.TRUE.equals(i.get("completed"))).count());
        return m;
    }

    private static Map<String, Object> item(String title, boolean required, boolean completed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("required", required);
        m.put("completed", completed);
        return m;
    }
}
