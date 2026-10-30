import { z } from "zod";

const id = z.union([z.number(), z.string()]);

export const approvalStepSchema = z
  .object({
    stepOrder: z.number().optional(),
    approverRole: z.string().nullable().optional(),
    status: z.string().nullable().optional(),
    actorName: z.string().nullable().optional(),
    comment: z.string().nullable().optional(),
  })
  .passthrough();

/** LeaveApplicationResponse (dates may be [y,m,d] arrays). */
export const leaveRowSchema = z
  .object({
    id,
    employeeId: id.optional(),
    leaveTypeCode: z.string().nullable().optional(),
    startDate: z.any().optional(),
    endDate: z.any().optional(),
    workingDaysCounted: z.union([z.number(), z.string()]).nullable().optional(),
    status: z.string().nullable().optional(),
    reason: z.string().nullable().optional(),
    rejectReason: z.string().nullable().optional(),
    currentStep: z.number().optional(),
    approvals: z.array(approvalStepSchema).optional(),
    halfDayPart: z.string().nullable().optional(),
    attachmentRef: z.string().nullable().optional(),
    teamClashAlert: z.union([z.boolean(), z.string()]).nullable().optional(),
    warnings: z.array(z.string()).nullable().optional(),
  })
  .passthrough();

export type LeaveRow = z.infer<typeof leaveRowSchema>;

/** Shape returned by POST leave/preview */
export const leavePreviewSchema = z
  .object({
    workingDaysCounted: z.number().nullable().optional(),
    balanceAfter: z.number().nullable().optional(),
    available: z.number().nullable().optional(),
    pendingDays: z.number().nullable().optional(),
    approvers: z
      .array(
        z.object({ name: z.string().nullable().optional(), role: z.string().nullable().optional() }).passthrough(),
      )
      .nullable()
      .optional(),
    warnings: z.array(z.string()).nullable().optional(),
    teamClashAlert: z.union([z.boolean(), z.string()]).nullable().optional(),
  })
  .passthrough();

export type LeavePreview = z.infer<typeof leavePreviewSchema>;
