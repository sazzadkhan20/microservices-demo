package com.platform.payroll.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constants.PlatformHeaders;
import com.platform.common.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

/**
 * Pulls attendance facts from HRMS for payroll (service-key trusted call).
 */
@Component
public class HrmsAttendanceClient {

    private static final Logger log = LoggerFactory.getLogger(HrmsAttendanceClient.class);

    private final String hrmsBaseUrl;
    private final String serviceKey;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public HrmsAttendanceClient(
            @Value("${payroll.hrms-base-url:http://127.0.0.1:8081}") String hrmsBaseUrl,
            @Value("${platform.security.service-key:local-service-key-change-me}") String serviceKey,
            ObjectMapper mapper) {
        this.hrmsBaseUrl = hrmsBaseUrl == null ? "" : hrmsBaseUrl.replaceAll("/$", "");
        this.serviceKey = serviceKey;
        this.mapper = mapper;
    }

    public record AttendanceFacts(BigDecimal regularHours, BigDecimal absenceDays, int rows) {}

    public AttendanceFacts factsForPeriod(Long employeeId, String periodLabel) {
        if (hrmsBaseUrl.isBlank() || employeeId == null || periodLabel == null || periodLabel.length() < 7) {
            return new AttendanceFacts(BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }
        YearMonth ym;
        try {
            ym = YearMonth.parse(periodLabel.substring(0, 7));
        } catch (Exception e) {
            return new AttendanceFacts(BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        try {
            String url = hrmsBaseUrl + "/api/attendance/employee/" + employeeId
                    + "?page=1&size=100&sort=workDate,asc";
            String tenant = TenantContext.getTenantId() == null ? "" : TenantContext.getTenantId();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header(PlatformHeaders.SERVICE_KEY, serviceKey)
                    .header(PlatformHeaders.TENANT_ID, tenant)
                    .header(PlatformHeaders.EMPLOYEE_ID, String.valueOf(employeeId))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                log.warn("HRMS attendance fetch HTTP {} for emp={}", resp.statusCode(), employeeId);
                return new AttendanceFacts(BigDecimal.ZERO, BigDecimal.ZERO, 0);
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode content = root.path("content");
            if (!content.isArray()) {
                return new AttendanceFacts(BigDecimal.ZERO, BigDecimal.ZERO, 0);
            }
            BigDecimal minutes = BigDecimal.ZERO;
            int absent = 0;
            int rows = 0;
            for (JsonNode row : content) {
                LocalDate d = parseDate(row);
                if (d == null || d.isBefore(from) || d.isAfter(to)) {
                    continue;
                }
                rows++;
                String status = text(row, "statusCode", text(row, "status", "")).toUpperCase(Locale.ROOT);
                if (status.contains("ABSENT") || "AB".equals(status) || status.contains("NO_SHOW")) {
                    absent++;
                }
                Number logged = number(row, "workedMinutes");
                if (logged == null) {
                    logged = number(row, "loggedMinutes");
                }
                if (logged != null && logged.doubleValue() > 0) {
                    minutes = minutes.add(BigDecimal.valueOf(logged.doubleValue()));
                }
            }
            BigDecimal hours = minutes.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            return new AttendanceFacts(hours, BigDecimal.valueOf(absent), rows);
        } catch (Exception e) {
            log.warn("HRMS attendance facts failed: {}", e.getMessage());
            return new AttendanceFacts(BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }
    }

    private static LocalDate parseDate(JsonNode row) {
        JsonNode n = row.get("workDate");
        if (n == null) n = row.get("date");
        if (n == null) return null;
        if (n.isArray() && n.size() >= 3) {
            return LocalDate.of(n.get(0).asInt(), n.get(1).asInt(), n.get(2).asInt());
        }
        String s = n.asText(null);
        if (s == null || s.isBlank()) return null;
        if (s.length() >= 10) s = s.substring(0, 10);
        return LocalDate.parse(s);
    }

    private static String text(JsonNode row, String a, String fallback) {
        if (row == null || !row.has(a) || row.get(a).isNull()) return fallback;
        return row.get(a).asText(fallback);
    }

    private static Number number(JsonNode row, String field) {
        if (row == null || !row.has(field) || row.get(field).isNull()) return null;
        JsonNode n = row.get(field);
        if (n.isNumber()) return n.numberValue();
        try {
            return Double.parseDouble(n.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
