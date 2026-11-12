package com.platform.payroll.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.InternalCalls;
import com.platform.messaging.correlation.CorrelationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only, bounded batch lookups of payroll facts from their owners (docs/PAYROLL_SPEC.md §2.1):
 * People (employment), Time &amp; Work (schedule + attendance), Leave (approved leave + cover balances).
 * One call per provider per run; explicit deadlines (guide §7); fail closed — a provider outage stops
 * the calculation instead of inventing zero absences.
 */
@Component
public class FactsClient {

    private final String peopleBaseUrl;
    private final String timeWorkBaseUrl;
    private final String leaveBaseUrl;
    private final String serviceKey;
    private final String internalKey;
    private final ObjectMapper mapper;
    private final Duration requestTimeout;
    private final HttpClient http;

    public FactsClient(
            @Value("${payroll.people-base-url:http://127.0.0.1:8081}") String peopleBaseUrl,
            @Value("${payroll.time-work-base-url:http://127.0.0.1:8087}") String timeWorkBaseUrl,
            @Value("${payroll.leave-base-url:http://127.0.0.1:8086}") String leaveBaseUrl,
            @Value("${platform.security.service-key}") String serviceKey,
            @Value("${platform.security.internal-key}") String internalKey,
            @Value("${payroll.facts-timeout-seconds:15}") long timeoutSeconds,
            ObjectMapper mapper) {
        this.peopleBaseUrl = trim(peopleBaseUrl);
        this.timeWorkBaseUrl = trim(timeWorkBaseUrl);
        this.leaveBaseUrl = trim(leaveBaseUrl);
        this.serviceKey = serviceKey;
        this.internalKey = internalKey;
        this.mapper = mapper;
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    /** Employees employed for any part of the month (FR-PAY-01); optionally restricted to ids. */
    public List<Map<String, Object>> employees(String period, Collection<Long> employeeIds) {
        Map<String, Object> body = get("People", peopleBaseUrl + "/api/internal/payroll/employees?period="
                + enc(period) + ids(employeeIds));
        Object rows = body.get("employees");
        if (!(rows instanceof List<?> list)) {
            throw new FactsUnavailableException("People returned no employee list");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> out = (List<Map<String, Object>>) list;
        return out;
    }

    /** Per-employee schedule mode + per-day attendance facts for the month. */
    public Map<String, Map<String, Object>> attendance(String period, Collection<Long> employeeIds) {
        return byEmployee("Time & Work", timeWorkBaseUrl + "/api/internal/payroll/attendance?period="
                + enc(period) + ids(employeeIds));
    }

    /** Per-employee approved leave in the month + usable cover balances in cover order. */
    public Map<String, Map<String, Object>> leave(String period, List<String> coverOrder, Collection<Long> employeeIds) {
        String order = coverOrder == null ? "" : String.join(",", coverOrder);
        return byEmployee("Leave", leaveBaseUrl + "/api/internal/payroll/leave?period=" + enc(period)
                + "&coverOrder=" + enc(order) + ids(employeeIds));
    }

    /** Ledger totals Leave recorded for a payroll operation key (reconciliation, guide §6). */
    public Map<String, Object> coverLedger(String operationKey) {
        return get("Leave", leaveBaseUrl + "/api/internal/payroll/cover-ledger?operationKey=" + enc(operationKey));
    }

    /** Every cover row Leave holds for a month, across all finalise keys (period reconciliation). */
    public Map<String, Object> coverLedgerForPeriod(String period) {
        return get("Leave", leaveBaseUrl + "/api/internal/payroll/cover-ledger?period=" + enc(period));
    }

    /** What Leave recorded for an operation key: {@code {known, status, success, result}}. */
    public Map<String, Object> leaveOperation(String operationKey) {
        return get("Leave", leaveBaseUrl + "/api/internal/payroll/operation?operationKey=" + enc(operationKey));
    }

    /**
     * Fences an operation key in Leave before a manual resolve: Leave records CANCELLED if it never
     * processed the key (a late command then changes nothing) and returns the outcome it holds.
     */
    public Map<String, Object> fenceLeaveOperation(String operationKey, String reason) {
        String body;
        try {
            body = mapper.writeValueAsString(Map.of("reason", reason == null ? "" : reason));
        } catch (Exception e) {
            throw new FactsUnavailableException("Cannot encode fence request");
        }
        HttpRequest req = base(leaveBaseUrl + "/api/internal/payroll/operation/fence?operationKey=" + enc(operationKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send("Leave", req);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> byEmployee(String provider, String url) {
        Map<String, Object> body = get(provider, url);
        Object rows = body.get("employees");
        if (!(rows instanceof Map<?, ?> m)) {
            throw new FactsUnavailableException(provider + " returned no per-employee facts");
        }
        return (Map<String, Map<String, Object>>) m;
    }

    private HttpRequest.Builder base(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .header(PlatformHeaders.SERVICE_KEY, serviceKey)
                .header(InternalCalls.HEADER, internalKey)
                .header(PlatformHeaders.CORRELATION_ID, CorrelationContext.getOrCreate());
    }

    private Map<String, Object> get(String provider, String url) {
        return send(provider, base(url).GET().build());
    }

    private Map<String, Object> send(String provider, HttpRequest req) {
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new FactsUnavailableException(provider + " payroll facts returned HTTP " + resp.statusCode());
            }
            return mapper.readValue(resp.body(), new TypeReference<>() {});
        } catch (FactsUnavailableException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FactsUnavailableException(provider + " payroll facts interrupted");
        } catch (Exception e) {
            throw new FactsUnavailableException(provider + " payroll facts unavailable: " + e.getMessage());
        }
    }

    private static String ids(Collection<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return "";
        }
        return "&employeeIds=" + employeeIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String trim(String url) {
        return url == null ? "" : url.replaceAll("/$", "");
    }

    /** A fact provider could not answer; the run is not recalculated (HTTP 503). */
    public static class FactsUnavailableException extends RuntimeException {
        public FactsUnavailableException(String message) {
            super(message);
        }
    }
}
