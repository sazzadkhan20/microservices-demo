package com.platform.payroll.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constants.PlatformHeaders;
import com.platform.common.tenant.TenantContext;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calls HRMS /api/payroll-facts/employee/{id}?period=YYYY-MM to get structured payroll facts.
 * Also calls /api/payroll-facts/apply-leave-cover on finalize/reopen.
 */
@Component
public class HrmsPayrollFactsClient {

    private static final Logger log = LoggerFactory.getLogger(HrmsPayrollFactsClient.class);

    private final String hrmsBaseUrl;
    private final String serviceKey;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public HrmsPayrollFactsClient(
            @Value("${payroll.hrms-base-url:http://127.0.0.1:8081}") String hrmsBaseUrl,
            @Value("${platform.security.service-key:local-service-key-change-me}") String serviceKey,
            ObjectMapper mapper) {
        this.hrmsBaseUrl = hrmsBaseUrl == null ? "" : hrmsBaseUrl.replaceAll("/$", "");
        this.serviceKey = serviceKey;
        this.mapper = mapper;
    }

    /**
     * Fetch payroll facts from HRMS for the given employee + period.
     * Returns an empty map (with fallback zeros) when HRMS is unavailable.
     */
    public Map<String, Object> fetchFacts(Long employeeId, String periodLabel) {
        if (hrmsBaseUrl.isBlank() || employeeId == null || periodLabel == null) {
            return emptyFacts(employeeId, periodLabel);
        }
        try {
            String url = hrmsBaseUrl + "/api/payroll-facts/employee/" + employeeId
                    + "?period=" + encode(periodLabel.substring(0, 7));
            HttpResponse<String> resp = http.send(buildGet(url), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                log.warn("HRMS payroll-facts HTTP {} for emp={} period={}", resp.statusCode(), employeeId, periodLabel);
                return emptyFacts(employeeId, periodLabel);
            }
            Map<String, Object> result = mapper.readValue(resp.body(), new TypeReference<>() {});
            // Normalize BigDecimal fields that Jackson may parse as Double
            normalizeBd(result, "baseSalary", "hourlyRate", "absentDays", "unpaidLeaveDays",
                    "paidLeaveDays", "hoursWorked");
            normalizeLeaveBalances(result);
            return result;
        } catch (Exception e) {
            log.warn("HRMS payroll-facts fetch failed emp={} period={}: {}", employeeId, periodLabel, e.getMessage());
            return emptyFacts(employeeId, periodLabel);
        }
    }

    /** Active employee ids for month run (FR-PAY-01). Empty when HRMS unreachable. */
    public java.util.List<Long> listActiveEmployeeIds() {
        if (hrmsBaseUrl.isBlank()) {
            return java.util.List.of();
        }
        try {
            String url = hrmsBaseUrl + "/api/employees?page=1&size=500&filter.active=true";
            HttpResponse<String> resp = http.send(buildGet(url), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                log.warn("HRMS employees list HTTP {}", resp.statusCode());
                return java.util.List.of();
            }
            Map<String, Object> body = mapper.readValue(resp.body(), new TypeReference<>() {});
            Object content = body.get("content");
            if (!(content instanceof java.util.List<?> list)) {
                return java.util.List.of();
            }
            java.util.List<Long> ids = new java.util.ArrayList<>();
            for (Object row : list) {
                if (row instanceof Map<?, ?> m && m.get("id") != null) {
                    ids.add(Long.valueOf(String.valueOf(m.get("id"))));
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("HRMS employees list failed: {}", e.getMessage());
            return java.util.List.of();
        }
    }

    /**
     * Apply leave cover on finalize (reverse=false) or reopen (reverse=true).
     */
    public void applyLeaveCover(Long employeeId, String periodLabel,
                                Map<String, BigDecimal> consumedByType, boolean reverse) {
        if (hrmsBaseUrl.isBlank() || employeeId == null || consumedByType == null || consumedByType.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("employeeId", employeeId);
            body.put("periodLabel", periodLabel);
            body.put("consumedByType", consumedByType);
            body.put("reverse", reverse);
            String json = mapper.writeValueAsString(body);
            String url = hrmsBaseUrl + "/api/payroll-facts/apply-leave-cover";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header(PlatformHeaders.SERVICE_KEY, serviceKey)
                    .header(PlatformHeaders.TENANT_ID, tenant())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                log.warn("apply-leave-cover HTTP {} emp={} period={} reverse={}",
                        resp.statusCode(), employeeId, periodLabel, reverse);
            }
        } catch (Exception e) {
            log.warn("apply-leave-cover failed emp={} period={}: {}", employeeId, periodLabel, e.getMessage());
        }
    }

    // ---------------------- helpers ----------------------

    private HttpRequest buildGet(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header(PlatformHeaders.SERVICE_KEY, serviceKey)
                .header(PlatformHeaders.TENANT_ID, tenant())
                .GET()
                .build();
    }

    private String tenant() {
        String t = TenantContext.getTenantId();
        return t == null ? "" : t;
    }

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static Map<String, Object> emptyFacts(Long employeeId, String periodLabel) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", employeeId);
        m.put("periodLabel", periodLabel);
        m.put("workingDaysInMonth", 22);
        m.put("daysEmployed", 22);
        m.put("absentDays", BigDecimal.ZERO);
        m.put("unpaidLeaveDays", BigDecimal.ZERO);
        m.put("paidLeaveDays", BigDecimal.ZERO);
        m.put("hoursWorked", BigDecimal.ZERO);
        m.put("missingCheckoutCount", 0L);
        m.put("leaveBalances", Map.of());
        m.put("payType", "MONTHLY");
        m.put("currency", "BDT");
        return m;
    }

    private void normalizeBd(Map<String, Object> m, String... keys) {
        for (String key : keys) {
            Object v = m.get(key);
            if (v instanceof Number n && !(v instanceof BigDecimal)) {
                m.put(key, BigDecimal.valueOf(n.doubleValue()));
            } else if (v instanceof String s) {
                try { m.put(key, new BigDecimal(s)); } catch (Exception ignored) { /* leave as-is */ }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void normalizeLeaveBalances(Map<String, Object> m) {
        Object raw = m.get("leaveBalances");
        if (!(raw instanceof Map<?, ?> lbMap)) return;
        Map<String, BigDecimal> normalized = new LinkedHashMap<>();
        lbMap.forEach((k, v) -> {
            if (k != null && v != null) {
                try {
                    BigDecimal bd = v instanceof BigDecimal b ? b
                            : v instanceof Number n ? BigDecimal.valueOf(n.doubleValue())
                            : new BigDecimal(String.valueOf(v));
                    normalized.put(String.valueOf(k).toUpperCase(java.util.Locale.ROOT), bd);
                } catch (Exception ignored) { /* skip */ }
            }
        });
        m.put("leaveBalances", normalized);
    }
}
