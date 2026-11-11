package com.platform.leave.client;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.InternalCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Leave → Time&amp;Work S2S (guide §3 — schedules/holidays owned by Time&amp;Work).
 * Internal endpoints require {@link InternalCalls#HEADER} (SEC-06 / TW-01).
 */
@Component
public class TimeWorkClient {

    private static final Logger log = LoggerFactory.getLogger(TimeWorkClient.class);

    private final RestClient client;
    private final String internalKey;

    public TimeWorkClient(
            RestClient.Builder builder,
            @Value("${platform.time-work-base-url:http://127.0.0.1:8087}") String baseUrl,
            @Value("${platform.security.service-key}") String serviceKey,
            @Value("${platform.security.internal-key}") String internalKey) {
        this.internalKey = internalKey;
        this.client = builder
                .baseUrl(baseUrl.replaceAll("/$", ""))
                .defaultHeader(PlatformHeaders.SERVICE_KEY, serviceKey)
                .build();
    }

    public Map<String, Object> resolveSchedule(Long employeeId) {
        if (employeeId == null) {
            return Map.of();
        }
        try {
            Map<String, Object> body = client.get()
                    .uri("/api/internal/schedules/resolve/{id}", employeeId)
                    .header(InternalCalls.HEADER, internalKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? Map.of() : body;
        } catch (Exception e) {
            log.warn("Time&Work schedule resolve failed for {}: {}", employeeId, e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> workingDayCodes(Long employeeId) {
        Object raw = resolveSchedule(employeeId).get("workingDays");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public String scheduleMode(Long employeeId) {
        Object mode = resolveSchedule(employeeId).get("mode");
        return mode == null ? "" : String.valueOf(mode);
    }

    public Set<LocalDate> holidaysInRange(Long locationId, LocalDate from, LocalDate to) {
        if (locationId == null || from == null || to == null) {
            return Set.of();
        }
        try {
            List<String> dates = client.get()
                    .uri(uri -> uri.path("/api/internal/holidays/in-range")
                            .queryParam("locationId", locationId)
                            .queryParam("from", from)
                            .queryParam("to", to)
                            .build())
                    .header(InternalCalls.HEADER, internalKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (dates == null) {
                return Set.of();
            }
            return dates.stream().map(LocalDate::parse).collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Time&Work holiday range failed: {}", e.getMessage());
            return Set.of();
        }
    }

    public boolean isHoliday(Long locationId, LocalDate day) {
        if (locationId == null || day == null) {
            return false;
        }
        return holidaysInRange(locationId, day, day).contains(day);
    }
}
