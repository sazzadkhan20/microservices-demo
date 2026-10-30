package com.platform.hrms.api.dto;

import com.platform.hrms.domain.LeaveApplication;
import com.platform.hrms.domain.LeaveApproval;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record LeaveApplicationResponse(
        Long id,
        Long employeeId,
        String leaveTypeCode,
        boolean paid,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal workingDaysCounted,
        String status,
        String reason,
        boolean unionMember,
        int noticeDaysGiven,
        int noticeDaysRequired,
        boolean coverageOk,
        boolean needsRelief,
        Long reliefEmployeeId,
        String storeId,
        String shiftPattern,
        Map<String, Object> coverageSnapshot,
        int currentStep,
        String rejectReason,
        List<ApprovalStepView> approvals,
        Instant createdAt,
        Instant updatedAt,
        String halfDayPart,
        String attachmentRef,
        boolean teamClashAlert,
        Map<String, Object> warnings
) {
    public record ApprovalStepView(
            int stepOrder,
            String approverRole,
            String status,
            Long approverEmployeeId,
            String actorName,
            String comment,
            Instant decidedAt,
            String resolver,
            Long expectedApproverId
    ) {
        public static ApprovalStepView from(LeaveApproval a) {
            return new ApprovalStepView(
                    a.getStepOrder(),
                    a.getApproverRole(),
                    a.getStatus(),
                    a.getApproverEmployeeId(),
                    a.getActorName(),
                    a.getCommentText(),
                    a.getDecidedAt(),
                    a.getResolver(),
                    a.getExpectedApproverId()
            );
        }
    }

    public static LeaveApplicationResponse from(LeaveApplication app, List<LeaveApproval> approvals) {
        return new LeaveApplicationResponse(
                app.getId(),
                app.getEmployeeId(),
                app.getLeaveTypeCode(),
                app.isPaid(),
                app.getStartDate(),
                app.getEndDate(),
                app.getWorkingDaysCounted(),
                app.getStatus(),
                app.getReason(),
                app.isUnionMember(),
                app.getNoticeDaysGiven(),
                app.getNoticeDaysRequired(),
                app.isCoverageOk(),
                app.isNeedsRelief(),
                app.getReliefEmployeeId(),
                app.getStoreId(),
                app.getShiftPattern(),
                app.getCoverageSnapshot(),
                app.getCurrentStep(),
                app.getRejectReason(),
                approvals.stream().map(ApprovalStepView::from).toList(),
                app.getCreatedAt(),
                app.getUpdatedAt(),
                app.getHalfDayPart(),
                app.getAttachmentRef(),
                app.isTeamClashAlert(),
                app.getWarnings()
        );
    }
}
