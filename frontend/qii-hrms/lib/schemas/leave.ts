import { z } from "zod";

const id = z.union([z.number(), z.string()]);

/** API sends a number, a numeric string, or null. */
const looseNum = z.union([z.number(), z.string()]).nullable().optional();

/** Leave warnings are either a string list or a map of messages. */
const warningList = z.preprocess((v) => {
  if (v == null) return v;
  if (Array.isArray(v)) return v.map((x) => (typeof x === "string" ? x : JSON.stringify(x)));
  if (typeof v === "object") {
    return Object.values(v as Record<string, unknown>).map((x) =>
      typeof x === "string" ? x : JSON.stringify(x),
    );
  }
  return [String(v)];
}, z.array(z.string()).nullable().optional());

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
    warnings: warningList,
  })
  .passthrough();

export type LeaveRow = z.infer<typeof leaveRowSchema>;

/** Shape returned by POST leave/preview */
export const leavePreviewSchema = z
  .object({
    workingDaysCounted: z.union([z.number(), z.string()]).nullable().optional(),
    daysCounted: z.union([z.number(), z.string()]).nullable().optional(),
    balanceAfter: z.union([z.number(), z.string()]).nullable().optional(),
    available: z.union([z.number(), z.string()]).nullable().optional(),
    pendingDays: z.union([z.number(), z.string()]).nullable().optional(),
    approvers: z
      .array(
        z
          .object({
            name: z.string().nullable().optional(),
            expectedName: z.string().nullable().optional(),
            role: z.string().nullable().optional(),
            step: z.union([z.number(), z.string()]).optional(),
          })
          .passthrough(),
      )
      .nullable()
      .optional(),
    approvalChain: z.array(z.unknown()).nullable().optional(),
    warnings: warningList,
    teamClashAlert: z.union([z.boolean(), z.string()]).nullable().optional(),
  })
  .passthrough();

export type LeavePreview = z.infer<typeof leavePreviewSchema>;
