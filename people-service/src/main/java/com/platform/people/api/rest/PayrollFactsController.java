package com.platform.people.api.rest;

import com.platform.people.application.PayrollFactsService;
import com.platform.people.domain.TenantSettings;
import com.platform.people.infrastructure.persistence.TenantSettingsRepository;
import com.platform.people.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * FR-SAL-01..03, FR-PAY-01..05 — payroll facts REST API consumed by payroll-service.
 * Authenticated via service-key (passed by payroll-service); employee scope guarded by leave:view.
 */
@RestController
@RequestMapping("/api/payroll-facts")
@Tag(name = "Payroll Facts", description = "HRMS payroll facts for payroll-service (service-key auth)")
public class PayrollFactsController {

    private final PayrollFactsService payrollFactsService;
    private final TenantSettingsRepository tenantSettingsRepository;

    public PayrollFactsController(
            PayrollFactsService payrollFactsService,
            TenantSettingsRepository tenantSettingsRepository) {
        this.payrollFactsService = payrollFactsService;
        this.tenantSettingsRepository = tenantSettingsRepository;
    }

    /**
     * S2S gate for payroll-service: is a notification event enabled for this tenant?
     * Recognizes {@code PAYSLIP_READY} and legacy FE key {@code payroll}. Defaults true.
     */
    @GetMapping("/notification-event")
    @RequiresPermission(feature = "settings", action = "view", allowInternal = true)
    @Operation(summary = "Check tenant notificationEvents[key] for payroll S2S — defaults true")
    public Map<String, Object> notificationEvent(@RequestParam String key) {
        String requested = key == null ? "" : key.trim();
        boolean enabled = isNotificationEventEnabled(requested);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", requested);
        out.put("enabled", enabled);
        return out;
    }

    @SuppressWarnings("unchecked")
    private boolean isNotificationEventEnabled(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        try {
            TenantSettings settings = tenantSettingsRepository.findAll().stream().findFirst().orElse(null);
            if (settings == null || settings.getSettings() == null) {
                return true;
            }
            Object raw = settings.getSettings().get("notificationEvents");
            if (!(raw instanceof Map<?, ?> m)) {
                return true;
            }
            // Accept both canonical and FE alias keys for payslip.
            String[] aliases = aliasesFor(key);
            for (String alias : aliases) {
                Object val = m.get(alias);
                if (val == null) {
                    // case-insensitive scan
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (e.getKey() != null
                                && alias.equalsIgnoreCase(String.valueOf(e.getKey()))) {
                            val = e.getValue();
                            break;
                        }
                    }
                }
                if (val instanceof Boolean b) {
                    return b;
                }
                if (val != null) {
                    return Boolean.parseBoolean(String.valueOf(val));
                }
            }
        } catch (Exception ignored) {
            return true;
        }
        return true;
    }

    private static String[] aliasesFor(String key) {
        String k = key.toUpperCase(Locale.ROOT);
        if ("PAYSLIP_READY".equals(k) || "PAYROLL".equals(k)) {
            return new String[]{"PAYSLIP_READY", "payroll", "PAYROLL"};
        }
        return new String[]{key, k, key.toLowerCase(Locale.ROOT)};
    }

    @GetMapping("/employee/{id}")
    @RequiresPermission(feature = "leave", action = "view", allowInternal = true)
    @Operation(summary = "Compute payroll facts for employee in period YYYY-MM — leave:view or internal key")
    public Map<String, Object> facts(
            @PathVariable Long id,
            @RequestParam String period) {
        return payrollFactsService.computeFacts(id, period);
    }

    @GetMapping("/active-employees")
    @RequiresPermission(feature = "employees", action = "view", allowInternal = true)
    @Operation(summary = "Active employee ids for payroll month run — employees:view or internal key")
    public Map<String, Object> activeEmployees() {
        return payrollFactsService.listActiveEmployeeIds();
    }

    /**
     * Leave cover is owned by leave-service via RabbitMQ ({@code payroll.leave-cover.command}).
     * This endpoint is retired to prevent People from mutating Leave balances.
     */
    @PostMapping("/apply-leave-cover")
    @RequiresPermission(feature = "leave", action = "manage", allowInternal = true)
    @Operation(summary = "Retired — use RabbitMQ leave-cover command (leave-service)")
    public ResponseEntity<Map<String, Object>> applyLeaveCover(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "status", "GONE",
                "message", "Leave cover is handled by leave-service over RabbitMQ; People no longer mutates leave balances."
        ));
    }
}
