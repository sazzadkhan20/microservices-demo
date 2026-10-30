package com.platform.hrms.api.rest;

import com.platform.hrms.domain.TenantSettings;
import com.platform.hrms.extension.engine.LeaveCalcEngine;
import com.platform.hrms.infrastructure.persistence.TenantSettingsRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BR — Read-only business rules documentation API.
 * GET /api/business-rules (settings:view)
 * Returns a map of BR-01..BR-22 with default/value from active config
 * (tenant_settings + leave_policy + attendanceRules + workLogRules + payroll keys).
 * Config-driven: no hard-coded values — all pulled from the settings bag.
 */
@RestController
@RequestMapping("/api/business-rules")
@Tag(name = "Business Rules", description = "Read-only view of active config-driven business rules")
public class BusinessRulesController {

    private final TenantSettingsRepository settingsRepository;
    private final LeaveCalcEngine leaveCalcEngine;

    public BusinessRulesController(TenantSettingsRepository settingsRepository,
                                   LeaveCalcEngine leaveCalcEngine) {
        this.settingsRepository = settingsRepository;
        this.leaveCalcEngine = leaveCalcEngine;
    }

    @GetMapping
    @RequiresPermission(feature = "settings", action = "manage")
    @Operation(summary = "BR-01..22 Active business rules from tenant config (read-only documentation)")
    public Map<String, Object> get() {
        Map<String, Object> settings = settingsRepository.findAll().stream()
                .findFirst().map(TenantSettings::getSettings).orElse(Map.of());

        Map<String, Object> att = nested(settings, "attendanceRules");
        Map<String, Object> wl = nested(settings, "workLogRules");
        Map<String, Object> payroll = nested(settings, "payroll");
        Map<String, Object> leavePolicy = leaveCalcEngine.currentPolicyView();

        Map<String, Object> rules = new LinkedHashMap<>();

        // Leave rules
        rules.put("BR-01", rule("BR-01", "Annual leave entitlement days",
                leavePolicy.get("annualDays"), 21));
        rules.put("BR-02", rule("BR-02", "Leave notice period mode (FIXED|ROLE|UNION)",
                leavePolicy.get("noticeMode"), "FIXED"));
        rules.put("BR-03", rule("BR-03", "Default notice days",
                leavePolicy.get("defaultNoticeDays"), 3));
        rules.put("BR-04", rule("BR-04", "Coverage check required before approving leave",
                leavePolicy.get("requireCoverageCheck"), false));
        rules.put("BR-05", rule("BR-05", "Coverage mode (SHIFT|TEAM|NONE)",
                leavePolicy.get("coverageMode"), "NONE"));
        rules.put("BR-06", rule("BR-06", "Require relief assignment when coverage fails",
                leavePolicy.get("requireReliefOnCoverageFail"), false));
        rules.put("BR-07", rule("BR-07", "Leave approval chain (MANAGER|HR|MANAGER_HR|AUTO)",
                leavePolicy.get("approvalChain"), "MANAGER"));

        // Attendance rules
        rules.put("BR-08", rule("BR-08", "Late-night check-out attribution window (minutes after midnight)",
                att.get("lateNightCheckoutMinutes"), 360));
        rules.put("BR-09", rule("BR-09", "Grace period after shift start before marking Late (minutes)",
                att.get("graceMinutes"), 15));
        rules.put("BR-10", rule("BR-10", "Cutoff time for auto missing-checkout (hours local)",
                att.get("checkoutCutoffHour"), 23));
        rules.put("BR-11", rule("BR-11", "Count open punch as zero hours when missing checkout",
                att.get("zeroHoursWhenOpenMissing"), true));
        rules.put("BR-12", rule("BR-12", "Half-day threshold (minutes worked)",
                att.get("halfDayMinutes"), 240));

        // Work log rules
        rules.put("BR-13", rule("BR-13", "Work log required for worked days",
                wl.get("required"), true));
        rules.put("BR-14", rule("BR-14", "Flag work log when logged > worked + grace (minutes)",
                wl.get("flagGraceMinutes"), 60));
        rules.put("BR-15", rule("BR-15", "Missing work-log notification enabled",
                wl.get("notifyMissing"), true));

        // Payroll rules
        rules.put("BR-16", rule("BR-16", "Payroll currency",
                payroll.get("currency"), "USD"));
        rules.put("BR-17", rule("BR-17", "Payroll cycle (MONTHLY|BI_WEEKLY|WEEKLY)",
                payroll.get("cycle"), "MONTHLY"));
        rules.put("BR-18", rule("BR-18", "Payslip auto-publish enabled",
                payroll.get("autoPublishPayslip"), false));

        // Probation / contract rules
        rules.put("BR-19", rule("BR-19", "Probation reminder days before end",
                settings.get("probationReminderDays"), 14));
        rules.put("BR-20", rule("BR-20", "Contract expiry reminder days before end",
                settings.get("contractExpiryReminderDays"), 30));

        // Checklist rules
        rules.put("BR-21", rule("BR-21", "Default checklist assignment triggers (ONBOARDING|OFFBOARDING)",
                settings.get("checklistAutoAssignOn"), "ONBOARDING,OFFBOARDING"));

        // Notification rules
        rules.put("BR-22", rule("BR-22", "Idle leave approval alert after N working days",
                att.get("idleApprovalWorkingDays"), 2));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rules", rules);
        result.put("source", "tenant_settings + leave_policy (config-driven, read-only)");
        return result;
    }

    private static Map<String, Object> rule(String id, String description, Object value, Object defaultValue) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("description", description);
        m.put("value", value != null ? value : defaultValue);
        m.put("default", defaultValue);
        m.put("overridden", value != null);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> settings, String key) {
        Object raw = settings.get(key);
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return Map.of();
    }
}
