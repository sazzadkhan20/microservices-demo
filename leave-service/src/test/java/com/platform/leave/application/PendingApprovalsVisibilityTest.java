package com.platform.leave.application;

import com.platform.leave.domain.LeaveApplication;
import com.platform.leave.domain.LeaveApproval;
import com.platform.leave.infrastructure.persistence.LeaveApprovalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Named / MoM approvers outside the applicant's TEAM tree must still see pending items
 * (notify already targets expectedApproverId — Approvals must match).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Pending approvals — chain authority overrides TEAM scope")
class PendingApprovalsVisibilityTest {

    @Mock LeaveApprovalRepository approvalRepository;
    @Mock DataScopeService dataScopeService;
    @Mock ApprovalAuthorityService approvalAuthorityService;
    @InjectMocks LeaveApplicationService service;

    @Test
    void namedApproverOutsideTeamScope_canSeeApplication() {
        LeaveApplication app = app(72L, 7L, 3, LeaveApplication.STATUS_PENDING_APPROVAL);
        LeaveApproval step = step(3, 5L);

        when(dataScopeService.canAccess(eq(5L), eq(7L), eq("leave"), eq("view"))).thenReturn(false);
        when(approvalRepository.findByApplicationIdAndStepOrderOrderByIdAsc(72L, 3))
                .thenReturn(List.of(step));
        when(approvalAuthorityService.authorityOf(eq(5L), eq(step), eq(app)))
                .thenReturn(ApprovalAuthorityService.Authority.STEP);

        assertTrue(service.canCallerSeeApplication(5L, app));
    }

    @Test
    void outsiderWithNoAuthority_cannotSeeApplication() {
        LeaveApplication app = app(72L, 7L, 3, LeaveApplication.STATUS_PENDING_APPROVAL);
        LeaveApproval step = step(3, 5L);

        when(dataScopeService.canAccess(eq(99L), eq(7L), eq("leave"), eq("view"))).thenReturn(false);
        when(approvalRepository.findByApplicationIdAndStepOrderOrderByIdAsc(72L, 3))
                .thenReturn(List.of(step));
        when(approvalAuthorityService.authorityOf(eq(99L), eq(step), eq(app)))
                .thenReturn(ApprovalAuthorityService.Authority.NONE);

        assertFalse(service.canCallerSeeApplication(99L, app));
    }

    @Test
    void teamScopeAlone_stillAllowsViewWithoutStepAuthority() {
        LeaveApplication app = app(72L, 7L, 3, LeaveApplication.STATUS_PENDING_APPROVAL);
        when(dataScopeService.canAccess(eq(10L), eq(7L), eq("leave"), eq("view"))).thenReturn(true);

        assertTrue(service.canCallerSeeApplication(10L, app));
    }

    @Test
    void teamVisibilityWouldExcludeApplicant_butStepAuthorityStillGrantsSee() {
        // Marcus (5) TEAM = {5}; Imran (7) not in set — same failure mode as production.
        DataScopeService.Visibility teamOnly =
                new DataScopeService.Visibility("TEAM", 5L, Set.of(5L));
        assertFalse(teamOnly.allows(7L));

        LeaveApplication app = app(72L, 7L, 3, LeaveApplication.STATUS_PENDING_APPROVAL);
        LeaveApproval step = step(3, 5L);
        when(dataScopeService.canAccess(eq(5L), eq(7L), eq("leave"), eq("view"))).thenReturn(false);
        when(approvalRepository.findByApplicationIdAndStepOrderOrderByIdAsc(72L, 3))
                .thenReturn(List.of(step));
        when(approvalAuthorityService.authorityOf(eq(5L), eq(step), eq(app)))
                .thenReturn(ApprovalAuthorityService.Authority.STEP);

        assertTrue(service.canCallerSeeApplication(5L, app),
                "Named/current-step approver must see app even when TEAM scope excludes applicant");
    }

    private static LeaveApplication app(Long id, Long employeeId, int currentStep, String status) {
        LeaveApplication a = new LeaveApplication();
        ReflectionTestUtils.setField(a, "id", id);
        a.setEmployeeId(employeeId);
        a.setCurrentStep(currentStep);
        a.setStatus(status);
        return a;
    }

    private static LeaveApproval step(int order, Long expectedApproverId) {
        LeaveApproval s = new LeaveApproval();
        ReflectionTestUtils.setField(s, "id", (long) order);
        s.setStepOrder(order);
        s.setStatus(LeaveApproval.STATUS_PENDING);
        s.setExpectedApproverId(expectedApproverId);
        return s;
    }
}
