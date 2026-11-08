package com.platform.people.api.rest;

import com.platform.people.domain.TenantSettings;
import com.platform.people.infrastructure.persistence.TenantSettingsRepository;
import com.platform.people.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * People-owned business rules only (employee / reporting / payroll docs).
 * Attendance &amp; work-log rules live in Time&amp;Work; leave rules in Leave — fetch those via their APIs.
 */
@RestController
@RequestMapping("/api/business-rules")
@Tag(name = "Business Rules", description = "Read-only view of People-owned config-driven business rules")
public class BusinessRulesController {

    private final TenantSettingsRepository settingsRepository;

    public BusinessRulesController(TenantSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @GetMapping
    @RequiresPermission(feature = "settings", action = "manage")
    @Operation(summary = "Active People-owned business rules from tenant_settings")
    public Map<String, Object> get() {
        Map<String, Object> settings = settingsRepository.findAll().stream()
                .findFirst().map(TenantSettings::getSettings).orElse(Map.of());

        Map<String, Object> employee = nested(settings, "employeeRules");
        Map<String, Object> reporting = nested(settings, "reportingLine");
        Map<String, Object> payroll = nested(settings, "payroll");

        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("BR-P01", rule("BR-P01", "Require manager on employee create",
                employee.get("requireManagerOnCreate"), true));
        rules.put("BR-P02", rule("BR-P02", "Reporting line requires effective date",
                reporting.get("requireEffectiveDate"), true));
        rules.put("BR-P03", rule("BR-P03", "Payroll pay day of month",
                payroll.get("payDayOfMonth"), 25));
        rules.put("note", "Attendance/work-log rules → time-work-service; leave rules → leave-service");
        return rules;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> root, String key) {
        Object v = root.get(key);
        if (v instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static Map<String, Object> rule(String code, String title, Object value, Object defaultValue) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("title", title);
        m.put("value", value == null ? defaultValue : value);
        m.put("defaultValue", defaultValue);
        return m;
    }
}
