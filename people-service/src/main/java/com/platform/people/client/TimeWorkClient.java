package com.platform.people.client;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.InternalCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

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

    public void syncEmployeeWorkPattern(Long employeeId, String workPattern, Long locationId) {
        if (employeeId == null) {
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("employeeId", employeeId);
            body.put("workPattern", workPattern);
            body.put("locationId", locationId);
            client.post()
                    .uri("/api/internal/work-schedules/sync-from-pattern")
                    .header(InternalCalls.HEADER, internalKey)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Time&Work schedule sync failed for employee {}: {}", employeeId, e.getMessage());
        }
    }

    /**
     * Ensure a LOCATION base work schedule exists (Mon–Fri 09:00–17:00 defaults).
     * Idempotent — existing location hours are never overwritten.
     */
    public void ensureLocationDefaultSchedule(Long locationId) {
        if (locationId == null) {
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("locationId", locationId);
            client.post()
                    .uri("/api/internal/work-schedules/ensure-location-default")
                    .header(InternalCalls.HEADER, internalKey)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Time&Work location default schedule ensure failed for {}: {}", locationId, e.getMessage());
        }
    }

    /**
     * Resolved schedule mode for the employee (EMPLOYEE → TEAM → LOCATION).
     * Empty string when Time&amp;Work is unreachable or no schedule exists.
     */
    @SuppressWarnings("unchecked")
    public String resolveScheduleMode(Long employeeId) {
        if (employeeId == null) {
            return "";
        }
        try {
            Map<String, Object> body = client.get()
                    .uri("/api/internal/schedules/resolve/{id}", employeeId)
                    .header(InternalCalls.HEADER, internalKey)
                    .retrieve()
                    .body(Map.class);
            if (body == null || body.get("mode") == null) {
                return "";
            }
            return String.valueOf(body.get("mode")).trim().toUpperCase();
        } catch (Exception e) {
            log.warn("Time&Work schedule resolve failed for {}: {}", employeeId, e.getMessage());
            return "";
        }
    }
}
