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

    public LeaveClient(
            RestClient.Builder builder,
            @Value("${platform.leave-base-url:http://127.0.0.1:8086}") String baseUrl,
            @Value("${platform.security.service-key}") String serviceKey) {
        this.client = builder
                .baseUrl(baseUrl.replaceAll("/$", ""))
                .defaultHeader(PlatformHeaders.SERVICE_KEY, serviceKey)
                .build();
    }

    public LeaveImpact leaveImpact(Long employeeId, LocalDate day) {
        if (employeeId == null || day == null) {
            return LeaveImpact.NONE;
        }
        try {
            List<Map<String, Object>> rows = client.get()
                    .uri(uri -> uri.path("/api/internal/approved-leave/{id}")
                            .queryParam("from", day.toString())
                            .queryParam("to", day.toString())
                            .build(employeeId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
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
                if (start.equals(end)
                        && counted.compareTo(BigDecimal.ONE) < 0
                        && counted.compareTo(BigDecimal.ZERO) > 0) {
                    return LeaveImpact.HALF;
                }
                return LeaveImpact.FULL;
            }
            return LeaveImpact.NONE;
        } catch (Exception e) {
            log.warn("Leave impact lookup failed emp={} day={}: {}", employeeId, day, e.getMessage());
            return LeaveImpact.NONE;
        }
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
