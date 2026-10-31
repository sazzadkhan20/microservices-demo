package com.platform.report.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Monthly report must show tenant-local punch times (Asia/Dhaka), not UTC wall-clock from Instant.
 */
class AttendanceReportServiceTimeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void convertsUtcInstantToAsiaDhaka() throws Exception {
        ObjectNode row = mapper.createObjectNode();
        // 04:05 UTC == 10:05 Asia/Dhaka
        row.put("checkIn", "2026-10-30T04:05:00Z");
        row.put("timeZone", "Asia/Dhaka");
        assertEquals("10:05", invokeLocalTime(row, "checkInLocal", "checkIn", "checkInAt"));
    }

    @Test
    void prefersCheckInLocalWhenPresent() throws Exception {
        ObjectNode row = mapper.createObjectNode();
        row.put("checkIn", "2026-10-30T04:05:00Z");
        row.put("checkInLocal", "10:05:00");
        row.put("timeZone", "Asia/Dhaka");
        assertEquals("10:05", invokeLocalTime(row, "checkInLocal", "checkIn", "checkInAt"));
    }

    @Test
    void defaultsZoneToAsiaDhaka() throws Exception {
        ObjectNode row = mapper.createObjectNode();
        row.put("checkOut", "2026-10-30T12:10:00Z");
        assertEquals("18:10", invokeLocalTime(row, "checkOutLocal", "checkOut", "checkOutAt"));
    }

    private String invokeLocalTime(ObjectNode row, String local, String... fields) throws Exception {
        Method m = AttendanceReportService.class.getDeclaredMethod(
                "localTimeOf",
                com.fasterxml.jackson.databind.JsonNode.class,
                ZoneId.class,
                String.class,
                String[].class);
        m.setAccessible(true);
        ZoneId zone;
        Method zoneOf = AttendanceReportService.class.getDeclaredMethod(
                "zoneOf", com.fasterxml.jackson.databind.JsonNode.class);
        zoneOf.setAccessible(true);
        zone = (ZoneId) zoneOf.invoke(null, row);
        return (String) m.invoke(null, row, zone, local, fields);
    }
}
