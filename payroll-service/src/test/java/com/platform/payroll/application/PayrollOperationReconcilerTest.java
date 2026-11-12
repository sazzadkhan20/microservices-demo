package com.platform.payroll.application;

import com.platform.payroll.domain.PayrollOperation;
import com.platform.payroll.infrastructure.persistence.PayrollOperationRepository;
import com.platform.payroll.infrastructure.persistence.PayrollRunRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Guide §6/§9 reconciler: backoff, single instance, alert de-duplication. */
class PayrollOperationReconcilerTest {

    private final PayrollOperationRepository operations = mock(PayrollOperationRepository.class);
    private final PayrollFinalizeService finalizeService = mock(PayrollFinalizeService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PayrollOperationReconciler reconciler = new PayrollOperationReconciler(operations,
            mock(PayrollRunRepository.class), finalizeService,
            new PayrollOperationPolicy(Duration.ofMinutes(5), Duration.ofMinutes(15)), jdbc, new SimpleMeterRegistry(),
            120, 1800, 60_000, 3_600_000, 24);

    private static PayrollOperation pending(String id, Duration age, Duration sinceSent, int attempts) {
        PayrollOperation o = new PayrollOperation();
        o.setOperationId(id);
        o.setOperationKey("FINALIZE:2026-09:r" + id);
        o.setStatus(PayrollOperation.PENDING);
        o.setAttempts(attempts);
        o.setLastSentAt(Instant.now().minus(sinceSent));
        ReflectionTestUtils.setField(o, "createdAt", Instant.now().minus(age));
        return o;
    }

    @Test
    void backoffDoublesFromTwoMinutesAndIsCapped() {
        assertEquals(Duration.ofSeconds(120), reconciler.backoff(1));
        assertEquals(Duration.ofSeconds(240), reconciler.backoff(2));
        assertEquals(Duration.ofSeconds(480), reconciler.backoff(3));
        assertEquals(Duration.ofSeconds(1800), reconciler.backoff(9));
    }

    @Test
    void onlyTheLeaseHolderReconciles() {
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(0);
        reconciler.reconcile();
        verify(operations, never()).findByStatusOrderByCreatedAtAsc(anyString());
        verify(finalizeService, never()).resend(any());
    }

    @Test
    void resendsOnlyOperationsWhoseBackoffElapsed() {
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        PayrollOperation due = pending("1", Duration.ofMinutes(3), Duration.ofMinutes(3), 1);
        PayrollOperation notDue = pending("2", Duration.ofMinutes(5), Duration.ofMinutes(3), 2); // needs 4 min
        when(operations.findByStatusOrderByCreatedAtAsc(PayrollOperation.PENDING)).thenReturn(List.of(due, notDue));
        reconciler.reconcile();
        verify(finalizeService, times(1)).resend(due);
        verify(finalizeService, never()).resend(eq(notDue));
    }

    @Test
    void alertsOncePerAlertWindow() {
        Instant now = Instant.now();
        assertTrue(reconciler.shouldAlert("op", now));
        assertFalse(reconciler.shouldAlert("op", now.plus(Duration.ofMinutes(5))));
        assertTrue(reconciler.shouldAlert("op", now.plus(Duration.ofMinutes(16))));
    }
}
