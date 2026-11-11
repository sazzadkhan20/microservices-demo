package com.platform.leave.application;

import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.domain.LeaveCategory;
import com.platform.leave.infrastructure.persistence.LeaveCategoryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class LeaveCategoryProRataTest {

    private final LeaveCategoryService service = new LeaveCategoryService(mock(LeaveCategoryRepository.class));

    @Test
    void fullQuotaWhenProRataOff() {
        LeaveCategory c = category(true, false, "2026-01-01", "2026-12-31", "20");
        EmployeeView e = employee(LocalDate.of(2026, 11, 10));
        assertEquals(0, new BigDecimal("20").compareTo(service.effectiveQuota(c, e)));
    }

    @Test
    void fullQuotaWhenJoinedOnOrBeforePeriodStart() {
        LeaveCategory c = category(true, true, "2026-01-01", "2026-12-31", "20");
        EmployeeView e = employee(LocalDate.of(2025, 6, 1));
        assertEquals(0, new BigDecimal("20").compareTo(service.effectiveQuota(c, e)));
    }

    @Test
    void proRataForNov10_2026Joiner() {
        LeaveCategory c = category(true, true, "2026-01-01", "2026-12-31", "20");
        EmployeeView e = employee(LocalDate.of(2026, 11, 10));
        // 52/365 * 20 = 2.849… → nearest 0.5 = 3
        assertEquals(0, new BigDecimal("3").compareTo(service.effectiveQuota(c, e)));
    }

    @Test
    void proRataForNov1_2026Joiner() {
        LeaveCategory c = category(true, true, "2026-01-01", "2026-12-31", "20");
        EmployeeView e = employee(LocalDate.of(2026, 11, 1));
        // 61/365 * 20 = 3.342… → nearest 0.5 = 3.5
        assertEquals(0, new BigDecimal("3.5").compareTo(service.effectiveQuota(c, e)));
    }

    @Test
    void zeroWhenJoinedAfterPeriodEnd() {
        LeaveCategory c = category(true, true, "2026-01-01", "2026-12-31", "20");
        EmployeeView e = employee(LocalDate.of(2027, 1, 5));
        assertEquals(0, BigDecimal.ZERO.compareTo(service.effectiveQuota(c, e)));
    }

    private static LeaveCategory category(
            boolean active, boolean proRata, String start, String end, String quota) {
        LeaveCategory c = new LeaveCategory();
        c.setActive(active);
        c.setProRata(proRata);
        c.setPeriodStart(LocalDate.parse(start));
        c.setPeriodEnd(LocalDate.parse(end));
        c.setQuotaDays(new BigDecimal(quota));
        c.setCode("ANNUAL");
        return c;
    }

    private static EmployeeView employee(LocalDate startDate) {
        EmployeeView e = new EmployeeView();
        e.setId(99L);
        e.setActive(true);
        e.setStartDate(startDate);
        return e;
    }
}
