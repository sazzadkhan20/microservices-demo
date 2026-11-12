package com.platform.timework.client;

import com.platform.common.constants.PlatformHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class LeaveClient {

    private static final Logger log = LoggerFactory.getLogger(LeaveClient.class);

    public enum LeaveImpact { NONE, HALF, FULL }

    private final RestClient client;
    private final String internalKey;

    public LeaveClient(
            RestClient.Builder builder,
            @Value("${platform.leave-base-url:http://127.0.0.1:8086}") String baseUrl,
            @Value("${platform.security.service-key}") String serviceKey,
            @Value("${platform.security.internal-key}") String internalKey) {
        org.springframework.http.client.SimpleClientHttpRequestFactory rf =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(3000);
        rf.setReadTimeout(10000);
        this.internalKey = internalKey;
        // Leave /api/internal/** requires the internal key (it was missing, so approved leave was never seen).
        this.client = builder
                .requestFactory(rf)
                .baseUrl(baseUrl.replaceAll("/$", ""))
                .defaultHeader(PlatformHeaders.SERVICE_KEY, serviceKey)
                .defaultHeader(com.platform.common.security.InternalCalls.HEADER, internalKey)
                .build();
    }

    /**
     * Memoised per HTTP request: Leave data cannot change during a Time&amp;Work request, and the
     * same employee/day is often evaluated twice (status + expected hours).
     */
    public LeaveImpact leaveImpact(Long employeeId, LocalDate day) {
        if (employeeId == null || day == null) {
            return LeaveImpact.NONE;
        }
        return com.platform.common.http.RequestMemo.get("leave-impact:" + employeeId + ":" + day,
                () -> fetchLeaveImpact(employeeId, day));
    }

    private LeaveImpact fetchLeaveImpact(Long employeeId, LocalDate day) {
        try {
            List<Map<String, Object>> rows = client.get()
                    .uri(uri -> uri.path("/api/internal/approved-leave/{id}")
                            .queryParam("from", day.toString())
                            .queryParam("to", day.toString())
                            .build(employeeId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return impactFor(rows, day);
        } catch (Exception e) {
            log.warn("Leave impact lookup failed emp={} day={}: {}", employeeId, day, e.getMessage());
            return LeaveImpact.NONE;
        }
    }

    /**
     * One call for a whole period (approved leave overlapping [from, to], same ordering as the per-day
     * lookup), evaluated day by day with exactly the {@link #leaveImpact} rule. On failure every day is
     * NONE, the same fallback the per-day lookup uses.
     */
    public Map<LocalDate, LeaveImpact> leaveImpacts(Long employeeId, LocalDate from, LocalDate to) {
        Map<LocalDate, LeaveImpact> out = new java.util.HashMap<>();
        if (employeeId == null || from == null || to == null || to.isBefore(from)) {
            return out;
        }
        List<Map<String, Object>> rows;
        try {
            rows = client.get()
                    .uri(uri -> uri.path("/api/internal/approved-leave/{id}")
                            .queryParam("from", from.toString())
                            .queryParam("to", to.toString())
                            .build(employeeId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Leave impact lookup failed emp={} {}..{}: {}", employeeId, from, to, e.getMessage());
            rows = List.of();
        }
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            LeaveImpact impact;
            try {
                impact = impactFor(rows, d);
            } catch (Exception e) {
                log.warn("Leave impact evaluation failed emp={} day={}: {}", employeeId, d, e.getMessage());
                impact = LeaveImpact.NONE;
            }
            out.put(d, impact);
        }
        return out;
    }

    static LeaveImpact impactFor(List<Map<String, Object>> rows, LocalDate day) {
        {
            if (rows == null || rows.isEmpty()) {
                return LeaveImpact.NONE;
            }
            for (Map<String, Object> a : rows) {
                LocalDate start = LocalDate.parse(String.valueOf(a.get("startDate")));
                LocalDate end = LocalDate.parse(String.valueOf(a.get("endDate")));
                if (day.isBefore(start) || day.isAfter(end)) {
                    continue;
                }
                BigDecimal counted = asDecimal(a.get("workingDaysCounted"));
                String part = a.get("halfDayPart") == null ? "FULL" : String.valueOf(a.get("halfDayPart"));
                boolean halfPart = !"FULL".equalsIgnoreCase(part);
                if (start.equals(end) && (halfPart
                        || (counted.compareTo(BigDecimal.ONE) < 0 && counted.compareTo(BigDecimal.ZERO) > 0))) {
                    return LeaveImpact.HALF;
                }
                return LeaveImpact.FULL;
            }
            return LeaveImpact.NONE;
        }
    }

    /**
     * Payroll batch: approved leave intersecting [from, to] for many employees in ONE call.
     * Fails closed (throws) — payroll must never treat "Leave unreachable" as "no leave".
     */
    public Map<Long, List<Map<String, Object>>> approvedLeaveBatch(java.util.Collection<Long> employeeIds,
                                                                  LocalDate from, LocalDate to) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Map.of();
        }
        String ids = employeeIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        Map<String, List<Map<String, Object>>> body = client.get()
                .uri(uri -> uri.path("/api/internal/payroll/approved-leave")
                        .queryParam("employeeIds", ids)
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .build())
                .header(com.platform.common.security.InternalCalls.HEADER, internalKey)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        Map<Long, List<Map<String, Object>>> out = new java.util.HashMap<>();
        if (body != null) {
            body.forEach((k, v) -> out.put(Long.valueOf(k), v));
        }
        return out;
    }

    private static BigDecimal asDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ONE;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(String.valueOf(v));
    }
}
