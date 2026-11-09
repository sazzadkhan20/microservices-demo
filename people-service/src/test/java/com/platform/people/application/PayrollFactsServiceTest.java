package com.platform.people.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayrollFactsServiceTest {

    @Test
    void daysEmployedCoversInclusiveOverlap() {
        LocalDate periodStart = LocalDate.of(2026, 3, 1);
        LocalDate periodEnd = LocalDate.of(2026, 3, 31);
        assertEquals(31, PayrollFactsService.daysEmployedInPeriod(
                LocalDate.of(2025, 1, 1), null, periodStart, periodEnd));
        assertEquals(22, PayrollFactsService.daysEmployedInPeriod(
                LocalDate.of(2026, 3, 10), null, periodStart, periodEnd));
        assertEquals(15, PayrollFactsService.daysEmployedInPeriod(
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 3, 15), periodStart, periodEnd));
        assertEquals(6, PayrollFactsService.daysEmployedInPeriod(
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 15), periodStart, periodEnd));
    }

    @Test
    void daysEmployedIsZeroWhenMissingOrOutsidePeriod() {
        LocalDate periodStart = LocalDate.of(2026, 3, 1);
        LocalDate periodEnd = LocalDate.of(2026, 3, 31);
        assertEquals(0, PayrollFactsService.daysEmployedInPeriod(null, null, periodStart, periodEnd));
        assertEquals(0, PayrollFactsService.daysEmployedInPeriod(
                LocalDate.of(2026, 4, 1), null, periodStart, periodEnd));
        assertEquals(0, PayrollFactsService.daysEmployedInPeriod(
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 2, 28), periodStart, periodEnd));
    }
}
