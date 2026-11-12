package com.platform.leave.application;

import com.platform.leave.api.dto.LeaveApplicationResponse;
import com.platform.leave.client.people.PeopleAuthzClient;
import com.platform.leave.client.people.PeopleEmployeeGateway;
import com.platform.leave.client.timework.TimeWorkScheduleGateway;
import com.platform.leave.domain.LeaveApplication;
import com.platform.leave.domain.LeaveApproval;
import com.platform.leave.extension.config.TenantConfigLoader;
import com.platform.leave.extension.engine.WorkingDaysCalculator;
import com.platform.leave.infrastructure.persistence.LeaveApplicationRepository;
import com.platform.leave.infrastructure.persistence.LeaveApprovalRepository;
import com.platform.leave.infrastructure.persistence.LeaveCategoryRepository;
import com.platform.leave.infrastructure.persistence.LeaveTypeRepository;
import com.platform.leave.infrastructure.persistence.StoreShiftCoverageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The leave list now loads approval steps in batches. The response must be identical to the previous
 * one-query-per-application mapping: same applications in the same order, same steps in step order,
 * applications without steps get an empty list.
 */
class LeaveListBatchApprovalsTest {

    private final LeaveApprovalRepository approvals = mock(LeaveApprovalRepository.class);
    private final List<LeaveApproval> store = new ArrayList<>();

    private LeaveApplicationService service() {
        return new LeaveApplicationService(
                mock(PeopleEmployeeGateway.class), mock(LeaveApplicationRepository.class), approvals,
                mock(LeaveTypeRepository.class), mock(LeaveCategoryRepository.class),
                mock(StoreShiftCoverageRepository.class), mock(TenantConfigLoader.class),
                mock(WorkingDaysCalculator.class), mock(LeaveBalanceService.class), mock(PeopleAuthzClient.class),
                mock(AuditService.class), mock(LeaveTeamClashService.class), mock(LeaveNotificationHook.class),
                mock(DataScopeService.class), mock(ApprovalAuthorityService.class),
                mock(TimeWorkScheduleGateway.class), mock(LeaveAppliesToService.class),
                mock(LeaveCategoryService.class), mock(LeaveApprovalChainBuilder.class));
    }

    private static LeaveApplication app(long id) {
        LeaveApplication a = new LeaveApplication();
        ReflectionTestUtils.setField(a, "id", id);
        a.setEmployeeId(id % 7);
        return a;
    }

    private void step(long id, long appId, int order) {
        LeaveApproval s = new LeaveApproval();
        ReflectionTestUtils.setField(s, "id", id);
        s.setApplicationId(appId);
        s.setStepOrder(order);
        s.setApproverRole("ROLE" + order);
        s.setStatus(LeaveApproval.STATUS_PENDING);
        store.add(s);
    }

    @SuppressWarnings("unchecked")
    private void wireRepository() {
        // Same semantics as the derived queries, evaluated over the in-memory store.
        when(approvals.findByApplicationIdOrderByStepOrderAsc(anyLong())).thenAnswer(inv -> {
            Long appId = inv.getArgument(0);
            return store.stream().filter(s -> s.getApplicationId().equals(appId))
                    .sorted(Comparator.comparingInt(LeaveApproval::getStepOrder)).toList();
        });
        when(approvals.findByApplicationIdInOrderByApplicationIdAscStepOrderAscIdAsc(anyCollection())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return store.stream().filter(s -> ids.contains(s.getApplicationId()))
                    .sorted(Comparator.comparing(LeaveApproval::getApplicationId)
                            .thenComparingInt(LeaveApproval::getStepOrder)
                            .thenComparing(LeaveApproval::getId))
                    .toList();
        });
    }

    @Test
    void batchedListMatchesPerApplicationMapping() {
        List<LeaveApplication> apps = new ArrayList<>();
        long stepId = 10_000;
        // Newest-first (descending ids) like findAllByOrderByCreatedAtDesc; 1,203 rows crosses 3 batches.
        for (long id = 1203; id >= 1; id--) {
            apps.add(app(id));
            if (id % 5 == 0) {
                continue; // some applications have no approval steps
            }
            int steps = (int) (id % 3) + 1;
            for (int order = steps; order >= 1; order--) { // inserted out of order on purpose
                step(stepId++, id, order);
            }
        }
        wireRepository();
        LeaveApplicationService svc = service();

        List<LeaveApplicationResponse> expected = apps.stream()
                .map(a -> LeaveApplicationResponse.from(a, approvals.findByApplicationIdOrderByStepOrderAsc(a.getId())))
                .toList();
        List<LeaveApplicationResponse> actual = svc.withApprovals(apps);

        assertEquals(expected, actual);
        assertTrue(actual.stream().filter(r -> r.id() % 5 == 0).allMatch(r -> r.approvals().isEmpty()));
        verify(approvals, times(3)).findByApplicationIdInOrderByApplicationIdAscStepOrderAscIdAsc(anyCollection());
    }

    @Test
    void emptyListDoesNotQuery() {
        assertEquals(List.of(), service().withApprovals(List.of()));
        verify(approvals, never()).findByApplicationIdInOrderByApplicationIdAscStepOrderAscIdAsc(anyCollection());
    }
}
