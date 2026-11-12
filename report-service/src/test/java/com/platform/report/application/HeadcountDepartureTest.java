package com.platform.report.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadcountDepartureTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void prefersExitDateOverContractEnd() {
        ObjectNode e = mapper.createObjectNode();
        e.put("active", false);
        e.put("status", "Terminated");
        e.put("exitDate", "2026-11-10");
        e.put("contractEnd", "2028-07-06");
        assertEquals(LocalDate.of(2026, 11, 10), ReportCatalogService.departureDate(e));
    }

    @Test
    void resignedWithoutContractEndUsesExitDate() {
        ObjectNode e = mapper.createObjectNode();
        e.put("active", false);
        e.put("status", "Resigned");
        e.put("exitDate", "2026-10-15");
        assertEquals(LocalDate.of(2026, 10, 15), ReportCatalogService.departureDate(e));
    }

    @Test
    void activeEmployeeContractEndIsNotDeparture() {
        ObjectNode e = mapper.createObjectNode();
        e.put("active", true);
        e.put("status", "Active");
        e.put("contractEnd", "2027-01-01");
        assertNull(ReportCatalogService.departureDate(e));
    }

    @Test
    void inactiveWithoutExitFallsBackToContractEnd() {
        ObjectNode e = mapper.createObjectNode();
        e.put("active", false);
        e.put("status", "Resigned");
        e.put("contractEnd", "2026-09-30");
        assertEquals(LocalDate.of(2026, 9, 30), ReportCatalogService.departureDate(e));
    }

    @Test
    void wasEmployedAtUsesExitDateNotActiveFlag() {
        ObjectNode e = mapper.createObjectNode();
        e.put("active", false);
        e.put("status", "Resigned");
        e.put("startDate", "2023-05-01");
        e.put("exitDate", "2026-12-01"); // future exit — still employed in October
        assertTrue(ReportCatalogService.wasEmployedAt(e, LocalDate.of(2026, 10, 31)));
        assertFalse(ReportCatalogService.wasEmployedAt(e, LocalDate.of(2026, 12, 1)));
        assertFalse(ReportCatalogService.wasEmployedAt(e, LocalDate.of(2026, 12, 31)));
    }

    @Test
    void onLeaveCountsAsActive() {
        ObjectNode e = mapper.createObjectNode();
        e.put("status", "On leave");
        assertTrue(ReportScope.isActive(e));
    }
}
