package com.platform.leave.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.messaging.PlatformEvent;
import com.platform.leave.application.LeaveCoverService;
import com.platform.leave.domain.LeaveOperation;
import com.platform.leave.infrastructure.persistence.LeaveOperationRepository;
import com.platform.messaging.outbox.InboxGuard;
import com.platform.messaging.outbox.OutboxRelay;
import com.platform.messaging.outbox.OutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Guide §6 on the Leave side: business rejections are recorded, temporary failures are retried. */
class LeaveCoverCommandListenerTest {

    private final InboxGuard inbox = mock(InboxGuard.class);
    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final LeaveCoverService cover = mock(LeaveCoverService.class);
    private final LeaveOperationRepository operations = mock(LeaveOperationRepository.class);
    private LeaveCoverCommandListener listener;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        listener = new LeaveCoverCommandListener(new ObjectMapper(), inbox, outbox, mock(ObjectProvider.class), cover,
                operations, mock(LeaveCoverCommandHandler.class));
        when(inbox.find(anyString())).thenReturn(Optional.empty());
        when(inbox.processOnce(anyString(), anyString(), anyString(), any(), any()))
                .thenAnswer(inv -> Optional.ofNullable(((Supplier<Map<String, Object>>) inv.getArgument(4)).get()));
        when(operations.findByOperationKey(anyString())).thenReturn(Optional.empty());
    }

    private static Map<String, Object> finalizeCommand(Object lines) {
        return Map.of("eventId", "ev-1", "eventType", "payroll.leave_cover.command", "correlationId", "c",
                "aggregateVersion", 7, "payload", Map.of("operationId", "op-1", "operationKey", "FINALIZE:2026-10:r7",
                        "action", "FINALIZE", "period", "2026-10", "revision", 7, "lines", lines));
    }

    private static List<Map<String, Object>> oneCover() {
        return List.of(Map.of("employeeId", 7, "covers", List.of(Map.of("category", "CASUAL", "days", 1.5))));
    }

    private Map<String, Object> publishedResult() {
        ArgumentCaptor<PlatformEvent> event = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(outbox).enqueue(event.capture(), eq("leave.cover.result"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.getValue().payload();
        return payload;
    }

    @Test
    void appliedCoverIsRecordedAndAnswered() {
        when(cover.applyFinalize(eq("FINALIZE:2026-10:r7"), eq("2026-10"), anyList())).thenReturn(List.of());
        listener.handleInTransaction(finalizeCommand(oneCover()));
        ArgumentCaptor<LeaveOperation> saved = ArgumentCaptor.forClass(LeaveOperation.class);
        verify(operations).save(saved.capture());
        assertEquals(LeaveOperation.STATUS_SUCCEEDED, saved.getValue().getStatus());
        assertEquals(true, publishedResult().get("success"));
    }

    @Test
    void businessRejectionIsRecordedAndAnswered() {
        when(cover.applyFinalize(anyString(), anyString(), anyList()))
                .thenReturn(List.of("Employee 7: CASUAL has 0 day(s) left but payroll covered 1.5 — recalculate 2026-10"));
        listener.handleInTransaction(finalizeCommand(oneCover()));
        Map<String, Object> result = publishedResult();
        assertEquals(false, result.get("success"));
        assertEquals("FAILED", result.get("status"));
        assertTrue(String.valueOf(result.get("errorMessage")).contains("CASUAL has 0"));
    }

    @Test
    void temporaryFailureIsThrownSoTheWholeDeliveryRollsBackAndIsRetried() {
        when(cover.applyFinalize(anyString(), anyString(), anyList()))
                .thenThrow(new ObjectOptimisticLockingFailureException("LeaveBalance", 1L));
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> listener.handleInTransaction(finalizeCommand(oneCover())));
        verify(operations, never()).save(any());
        verify(outbox, never()).enqueue(any(), anyString());
    }

    @Test
    void malformedCommandIsAPermanentRejection() {
        listener.handleInTransaction(finalizeCommand("not-a-list"));
        verify(cover, never()).applyFinalize(anyString(), anyString(), anyList());
        Map<String, Object> result = publishedResult();
        assertEquals(false, result.get("success"));
        assertTrue(String.valueOf(result.get("errorMessage")).startsWith("Invalid payroll leave-cover command"));
    }

    @Test
    void commandArrivingAfterAPayrollFenceReplaysCancelledAndTouchesNoBalance() {
        LeaveOperation fenced = new LeaveOperation();
        fenced.setStatus(LeaveOperation.STATUS_CANCELLED);
        fenced.setResultJson(Map.of("success", false, "status", "CANCELLED", "errorMessage", "Cancelled by payroll"));
        when(operations.findByOperationKey("FINALIZE:2026-10:r7")).thenReturn(Optional.of(fenced));
        listener.handleInTransaction(finalizeCommand(oneCover()));
        verify(cover, never()).applyFinalize(anyString(), anyString(), anyList());
        verify(operations, never()).save(any());
        Map<String, Object> result = publishedResult();
        assertEquals(false, result.get("success"));
        assertEquals("CANCELLED", result.get("status"));
        assertEquals(true, result.get("idempotentReplay"));
    }

    @Test
    void reopenWithoutTheKeyToReverseIsRejected() {
        assertTrue(LeaveCoverCommandListener.validate("REOPEN:2026-10:r7:a1", "REOPEN", "2026-10", Map.of())
                .contains("reverseOfKey"));
        assertEquals(null, LeaveCoverCommandListener.validate("REOPEN:2026-10:r7:a1", "REOPEN", "2026-10",
                Map.of("reverseOfKey", "FINALIZE:2026-10:r7")));
    }
}
