package com.platform.timework.client;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaveClientImpactTest {

    private static Map<String, Object> row(String start, String end, String part, String counted) {
        return Map.of("startDate", start, "endDate", end, "halfDayPart", part, "workingDaysCounted", new BigDecimal(counted));
    }

    @Test
    void sameRuleForEveryDayOfARange() {
        List<Map<String, Object>> rows = List.of(
                row("2026-11-02", "2026-11-04", "FULL", "3"),
                row("2026-11-09", "2026-11-09", "AM", "0.5"),
                row("2026-11-10", "2026-11-10", "FULL", "0.5"));
        assertEquals(LeaveClient.LeaveImpact.NONE, LeaveClient.impactFor(rows, LocalDate.of(2026, 11, 1)));
        assertEquals(LeaveClient.LeaveImpact.FULL, LeaveClient.impactFor(rows, LocalDate.of(2026, 11, 3)));
        assertEquals(LeaveClient.LeaveImpact.HALF, LeaveClient.impactFor(rows, LocalDate.of(2026, 11, 9)));
        assertEquals(LeaveClient.LeaveImpact.HALF, LeaveClient.impactFor(rows, LocalDate.of(2026, 11, 10)));
        assertEquals(LeaveClient.LeaveImpact.NONE, LeaveClient.impactFor(List.of(), LocalDate.of(2026, 11, 10)));
    }
}
