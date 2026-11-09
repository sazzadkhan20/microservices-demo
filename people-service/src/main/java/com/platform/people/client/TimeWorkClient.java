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

    @SuppressWarnings("unchecked")
    public Map<String, Object> attendanceFacts(Long employeeId, String period) {
        try {
            Map<?, ?> body = client.get()
                    .uri(uri -> uri.path("/api/internal/attendance-facts/{id}")
                            .queryParam("period", period)
                            .build(employeeId))
                    .header(InternalCalls.HEADER, internalKey)
                    .retrieve()
                    .body(Map.class);
            return body == null ? Map.of() : (Map<String, Object>) body;
        } catch (Exception e) {
            log.warn("Attendance facts fetch failed: {}", e.getMessage());
            throw new IllegalStateException("Time&Work service unavailable for payroll facts", e);
        }
    }
}
