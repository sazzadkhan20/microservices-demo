package com.platform.payroll.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.InternalCalls;
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
import java.util.List;
import java.util.Map;

/**
 * Calls HRMS /api/payroll-facts/* with service-key + internal-key (system S2S).
 * Fail-closed: never invents zero-absence facts when HRMS is unreachable.
 */
@Component
public class HrmsPayrollFactsClient {

    private static final Logger log = LoggerFactory.getLogger(HrmsPayrollFactsClient.class);

    private final String hrmsBaseUrl;
    private final String serviceKey;
    private final String internalKey;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public HrmsPayrollFactsClient(
            @Value("${payroll.hrms-base-url:http://127.0.0.1:8081}") String hrmsBaseUrl,
            @Value("${platform.security.service-key:local-service-key-change-me}") String serviceKey,
            @Value("${platform.security.internal-key:" + InternalCalls.DEFAULT_DEV_KEY + "}") String internalKey,
            ObjectMapper mapper) {
        this.hrmsBaseUrl = hrmsBaseUrl == null ? "" : hrmsBaseUrl.replaceAll("/$", "");
        this.serviceKey = serviceKey;
        this.internalKey = internalKey;
        this.mapper = mapper;
    }

    /**
     * Fetch payroll facts from HRMS for the given employee + period.
     * @throws IllegalStateException when HRMS is unavailable or returns an error (fail-closed).
     */
    public Map<String, Object> fetchFacts(Long employeeId, String periodLabel) {
        if (hrmsBaseUrl.isBlank() || employeeId == null || periodLabel == null) {
            throw new IllegalStateException("HRMS payroll-facts unavailable: missing base URL or args");
        }
        try {
            String url = hrmsBaseUrl + "/api/payroll-facts/employee/" + employeeId
                    + "?period=" + encode(periodLabel.substring(0, 7));
            HttpResponse<String> resp = http.send(buildGet(url), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new IllegalStateException(
                        "HRMS payroll-facts HTTP " + resp.statusCode() + " for emp=" + employeeId);
            }
            Map<String, Object> result = mapper.readValue(resp.body(), new TypeReference<>() {});
            normalizeBd(result, "baseSalary", "hourlyRate", "absentDays", "unpaidLeaveDays",
                    "paidLeaveDays", "hoursWorked");
            normalizeLeaveBalances(result);
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "HRMS payroll-facts fetch failed emp=" + employeeId + ": " + e.getMessage(), e);
        }
    }

    /** Active employee ids for month run (FR-PAY-01). Fail-closed when HRMS unreachable. */
    public List<Long> listActiveEmployeeIds() {
        if (hrmsBaseUrl.isBlank()) {
            throw new IllegalStateException("HRMS base URL not configured");
        }
        try {
            String url = hrmsBaseUrl + "/api/payroll-facts/active-employees";
            HttpResponse<String> resp = http.send(buildGet(url), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new IllegalStateException("HRMS active-employees HTTP " + resp.statusCode());
            }
            Map<String, Object> body = mapper.readValue(resp.body(), new TypeReference<>() {});
            Object ids = body.get("employeeIds");
            if (!(ids instanceof List<?> list)) {
                return List.of();
            }
            List<Long> out = new java.util.ArrayList<>();
            for (Object row : list) {
                if (row != null) {
                    out.add(Long.valueOf(String.valueOf(row)));
                }
            }
            return out;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("HRMS active-employees failed: " + e.getMessage(), e);
        }
    }

    /**
     * Apply leave cover on finalize (reverse=false) or reopen (reverse=true).
     * Fail-closed: throws when HRMS rejects so finalize cannot leave balances out of sync.
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
                    .header(InternalCalls.HEADER, internalKey)
                    .header(PlatformHeaders.TENANT_ID, tenant())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new IllegalStateException(
                        "apply-leave-cover HTTP " + resp.statusCode() + " emp=" + employeeId);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "apply-leave-cover failed emp=" + employeeId + ": " + e.getMessage(), e);
        }
    }

    private HttpRequest buildGet(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header(PlatformHeaders.SERVICE_KEY, serviceKey)
                .header(InternalCalls.HEADER, internalKey)
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
