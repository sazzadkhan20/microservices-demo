package com.platform.payroll.application;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.InternalCalls;
import com.platform.common.tenant.TenantContext;
import com.platform.payroll.domain.PayrollConfigEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Best-effort payslip notifications → notification-service.
 * <p>Config-driven (no tenant if-else):
 * <ul>
 *   <li>{@code payroll_config} CONFIG {@code notifyPayslipFinalized} (default true)</li>
 *   <li>HRMS {@code notificationEvents.PAYSLIP_READY} / {@code payroll} (default true)</li>
 *   <li>optional CONFIG {@code payslipNotificationLink} (relative path, default {@code /payroll})</li>
 * </ul>
 * Always targets <strong>only</strong> the payslip's employeeId — never broadcasts.
 */
@Component
public class PayrollNotificationHook {

    private static final Logger log = LoggerFactory.getLogger(PayrollNotificationHook.class);

    private final String notificationBaseUrl;
    private final String hrmsBaseUrl;
    private final String serviceKey;
    private final String internalKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public PayrollNotificationHook(
            @Value("${platform.notification.base-url:http://127.0.0.1:8083}") String notificationBaseUrl,
            @Value("${payroll.hrms-base-url:http://127.0.0.1:8081}") String hrmsBaseUrl,
            @Value("${platform.security.service-key}") String serviceKey,
            @Value("${platform.security.internal-key}") String internalKey) {
        this.notificationBaseUrl = notificationBaseUrl == null ? "" : notificationBaseUrl.trim();
        this.hrmsBaseUrl = hrmsBaseUrl == null ? "" : hrmsBaseUrl.replaceAll("/$", "");
        this.serviceKey = serviceKey;
        this.internalKey = internalKey;
    }

    /**
     * Notify the employee that their payslip for {@code periodLabel} is finalized.
     * Idempotent via dedupeKey {@code payslip-final-{id}}.
     */
    public void notifyPayslipFinalized(
            PayrollConfigEntity config,
            Long employeeId,
            Long payslipId,
            String periodLabel,
            BigDecimal netAmount,
            String currency) {
        if (employeeId == null || payslipId == null) {
            return;
        }
        if (!isPackNotifyEnabled(config)) {
            log.debug("Payslip finalize notify skipped (pack notifyPayslipFinalized=false) emp={} slip={}",
                    employeeId, payslipId);
            return;
        }
        if (!isTenantEventEnabled()) {
            log.debug("Payslip finalize notify skipped (HRMS PAYSLIP_READY off) emp={} slip={}",
                    employeeId, payslipId);
            return;
        }

        String period = periodLabel == null ? "" : periodLabel;
        String cur = currency == null || currency.isBlank() ? "" : currency.trim();
        String net = netAmount == null ? "" : netAmount.toPlainString();
        String title = "Payslip ready";
        String body = "Your payslip for " + period + " has been finalized"
                + (net.isBlank() ? "." : " — net " + (cur.isBlank() ? "" : cur + " ") + net + ".");
        String link = configStr(config, "payslipNotificationLink", "/payroll");
        String dedupe = "payslip-final-" + payslipId;

        push(employeeId, title, body, "PAYROLL", dedupe, link);
    }

    private boolean isPackNotifyEnabled(PayrollConfigEntity config) {
        String v = configStr(config, "notifyPayslipFinalized", "true");
        return Boolean.parseBoolean(v);
    }

    /**
     * Asks HRMS whether PAYSLIP_READY (or legacy {@code payroll}) is enabled.
     * Fail-open: if HRMS unreachable, still send (pack flag already passed).
     */
    private boolean isTenantEventEnabled() {
        if (hrmsBaseUrl.isBlank()) {
            return true;
        }
        try {
            String url = hrmsBaseUrl + "/api/payroll-facts/notification-event?key=PAYSLIP_READY";
            HttpResponse<String> resp = http.send(buildGet(url), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                log.warn("notification-event check HTTP {} — defaulting enabled", resp.statusCode());
                return true;
            }
            String body = resp.body() == null ? "" : resp.body();
            // {"key":"PAYSLIP_READY","enabled":true}
            if (body.contains("\"enabled\":false") || body.contains("\"enabled\": false")) {
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("notification-event check failed (default enabled): {}", e.getMessage());
            return true;
        }
    }

    private void push(Long employeeId, String title, String bodyText, String category,
                      String dedupeKey, String linkUrl) {
        String safeLink = sanitizeLink(linkUrl);
        String body = "{\"employeeId\":" + employeeId
                + ",\"title\":\"" + escape(title) + "\""
                + ",\"body\":\"" + escape(bodyText) + "\""
                + ",\"category\":\"" + escape(category) + "\""
                + ",\"dedupeKey\":\"" + escape(dedupeKey) + "\""
                + (safeLink == null ? "" : ",\"linkUrl\":\"" + escape(safeLink) + "\"")
                + "}";
        if (notificationBaseUrl.isBlank()) {
            log.info("Payslip notification (log-only): emp={} slipKey={} link={}", employeeId, dedupeKey, safeLink);
            return;
        }
        try {
            String tenant = TenantContext.getTenantId() == null ? "" : TenantContext.getTenantId();
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(notificationBaseUrl.replaceAll("/$", "") + "/api/notifications"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header(PlatformHeaders.SERVICE_KEY, serviceKey)
                    .header(InternalCalls.HEADER, internalKey)
                    .header(PlatformHeaders.TENANT_ID, tenant)
                    .header(PlatformHeaders.EMPLOYEE_ID, String.valueOf(employeeId))
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                log.warn("Payslip notification HTTP {}: {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("Payslip notification failed (non-fatal): {}", e.getMessage());
        }
    }

    private HttpRequest buildGet(String url) {
        String tenant = TenantContext.getTenantId() == null ? "" : TenantContext.getTenantId();
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(3))
                .header(PlatformHeaders.SERVICE_KEY, serviceKey)
                .header(InternalCalls.HEADER, internalKey)
                .header(PlatformHeaders.TENANT_ID, tenant)
                .GET()
                .build();
    }

    private static String configStr(PayrollConfigEntity config, String key, String def) {
        if (config == null || config.getDeductionRules() == null) return def;
        for (Map<String, Object> rule : config.getDeductionRules()) {
            if (!"CONFIG".equalsIgnoreCase(String.valueOf(rule.getOrDefault("type", "")))) continue;
            if (!key.equals(String.valueOf(rule.get("key")))) continue;
            Object v = rule.get("value");
            if (v != null) return String.valueOf(v).trim();
        }
        return def;
    }

    static String sanitizeLink(String link) {
        if (link == null || link.isBlank()) return "/payroll";
        String t = link.trim();
        if (!t.startsWith("/") || t.startsWith("//") || t.contains("://")) return "/payroll";
        if (t.length() > 512) t = t.substring(0, 512);
        return t;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
