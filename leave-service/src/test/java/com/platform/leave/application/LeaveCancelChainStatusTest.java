package com.platform.leave.application;

import com.platform.leave.domain.LeaveApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR-LV-18 — cancel chain must share the same pending-decision gate as apply. */
class LeaveCancelChainStatusTest {

    @Test
    void pendingCancelIsAwaitingDecision() {
        assertTrue(LeaveApplicationService.isAwaitingApprovalDecision(LeaveApplication.STATUS_PENDING_CANCEL));
        assertTrue(LeaveApplicationService.isAwaitingApprovalDecision(LeaveApplication.STATUS_PENDING_APPROVAL));
        assertFalse(LeaveApplicationService.isAwaitingApprovalDecision(LeaveApplication.STATUS_APPROVED));
        assertFalse(LeaveApplicationService.isAwaitingApprovalDecision(LeaveApplication.STATUS_CANCELLED));
        assertFalse(LeaveApplicationService.isAwaitingApprovalDecision(LeaveApplication.STATUS_REJECTED));
    }
}
