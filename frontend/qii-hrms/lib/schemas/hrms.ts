import { z } from "zod";

/**
 * Response shapes for the self-service pages. All use `.passthrough()` so new backend
 * fields never break parsing. Dates may arrive as ISO strings, [y,m,d] arrays or epoch
 * seconds, so they are typed `any` and normalised with `toDate` / `isoDate` in lib/format.
 */

const id = z.union([z.number(), z.string()]);
const maybe = <T extends z.ZodType>(t: T) => t.nullable().optional();

export const approvalStepSchema = z
  .object({
    stepOrder: z.number().optional(),
    approverRole: maybe(z.string()),
    status: maybe(z.string()),
    approverEmployeeId: maybe(id),
    actorName: maybe(z.string()),
    comment: maybe(z.string()),
    decidedAt: z.any().optional(),
  })
  .passthrough();

/** LeaveApplicationResponse */
export const leaveApplicationSchema = z
  .object({
    id,
    employeeId: maybe(id),
    leaveTypeCode: maybe(z.string()),
    paid: z.boolean().optional(),
    startDate: z.any().optional(),
    endDate: z.any().optional(),
    workingDaysCounted: maybe(z.union([z.number(), z.string()])),
    status: maybe(z.string()),
    reason: maybe(z.string()),
    needsRelief: z.boolean().optional(),
    reliefEmployeeId: maybe(id),
    currentStep: z.number().optional(),
    rejectReason: maybe(z.string()),
    approvals: z.array(approvalStepSchema).optional(),
    createdAt: z.any().optional(),
  })
  .passthrough();
export type LeaveApplication = z.infer<typeof leaveApplicationSchema>;

/** LeaveBalanceResponse */
export const leaveBalanceSchema = z
  .object({
    id: maybe(id),
    employeeId: maybe(id),
    leaveTypeCode: z.string(),
    year: z.number().optional(),
    balanceDays: maybe(z.union([z.number(), z.string()])),
  })
  .passthrough();
export type LeaveBalance = z.infer<typeof leaveBalanceSchema>;

/** LeaveTypeResponse */
export const leaveTypeSchema = z
  .object({
    id: maybe(id),
    code: z.string(),
    name: z.string(),
    paid: z.boolean().optional(),
    requiresBalance: z.boolean().optional(),
    allowWhenZeroBalance: z.boolean().optional(),
    deductBalance: z.boolean().optional(),
  })
  .passthrough();
export type LeaveTypeRow = z.infer<typeof leaveTypeSchema>;

/** Attendance record view map (AttendanceService.toView). */
export const attendanceRowSchema = z
  .object({
    id: maybe(id),
    employeeId: maybe(id),
    date: maybe(z.string()),
    workDate: maybe(z.string()),
    checkIn: z.any().optional(),
    checkOut: z.any().optional(),
    checkInAt: z.any().optional(),
    checkOutAt: z.any().optional(),
    status: maybe(z.string()),
    statusCode: maybe(z.string()),
    statusLabel: maybe(z.string()),
    location: maybe(z.string()),
    workingHours: maybe(z.string()),
    totalHours: maybe(z.union([z.number(), z.string()])),
    note: maybe(z.string()),
  })
  .passthrough();
export type AttendanceRow = z.infer<typeof attendanceRowSchema>;

/** Attendance correction view map. */
export const correctionSchema = z
  .object({
    id,
    employeeId: maybe(id),
    employeeName: maybe(z.string()),
    workDate: z.string().optional(),
    requestedCheckIn: z.any().optional(),
    requestedCheckOut: z.any().optional(),
    reason: maybe(z.string()),
    status: maybe(z.string()),
    reviewNote: maybe(z.string()),
    createdAt: z.any().optional(),
  })
  .passthrough();
export type Correction = z.infer<typeof correctionSchema>;

/** NotificationResponse */
export const notificationSchema = z
  .object({
    id,
    title: maybe(z.string()),
    body: maybe(z.string()),
    category: maybe(z.string()),
    readFlag: z.boolean().optional(),
    createdAt: z.any().optional(),
  })
  .passthrough();
export type NotificationRow = z.infer<typeof notificationSchema>;

/** Spring Data `Page` envelope returned by notification-service (0-based). */
export const springPageSchema = <T extends z.ZodType>(item: T) =>
  z
    .object({
      content: z.array(item),
      totalElements: z.number().optional(),
      totalPages: z.number().optional(),
      number: z.number().optional(),
    })
    .passthrough();

/** Payslip row (PayrollController.toRow). */
export const payslipSchema = z
  .object({
    id,
    employeeId: maybe(id),
    periodLabel: maybe(z.string()),
    grossAmount: maybe(z.union([z.number(), z.string()])),
    deductionAmount: maybe(z.union([z.number(), z.string()])),
    netAmount: maybe(z.union([z.number(), z.string()])),
    regularHours: maybe(z.union([z.number(), z.string()])),
    overtimeHours: maybe(z.union([z.number(), z.string()])),
    weekendHours: maybe(z.union([z.number(), z.string()])),
    details: z.any().optional(),
    status: maybe(z.string()),
    createdAt: z.any().optional(),
  })
  .passthrough();
export type Payslip = z.infer<typeof payslipSchema>;

/** Checklist (ChecklistController). */
export const checklistItemSchema = z
  .object({
    title: z.string().optional(),
    required: z.boolean().optional(),
    completed: z.boolean().optional(),
  })
  .passthrough();

export const checklistSchema = z
  .object({
    id,
    name: z.string().optional(),
    type: maybe(z.string()),
    status: maybe(z.string()),
    items: z.array(checklistItemSchema).optional(),
    totalItems: z.number().optional(),
    completedItems: z.number().optional(),
  })
  .passthrough();
export type Checklist = z.infer<typeof checklistSchema>;

/** Work log + task rows. */
export const workLogSchema = z
  .object({
    id: maybe(id),
    employeeId: maybe(id),
    workDate: z.any().optional(),
    taskId: maybe(id),
    minutes: maybe(z.union([z.number(), z.string()])),
    note: maybe(z.string()),
  })
  .passthrough();
export type WorkLogRow = z.infer<typeof workLogSchema>;

export const taskSchema = z
  .object({
    id,
    title: maybe(z.string()),
    clientName: maybe(z.string()),
    status: maybe(z.string()),
  })
  .passthrough();
export type TaskRow = z.infer<typeof taskSchema>;

/** Employee document metadata (custom_fields.documents[]). */
export const documentSchema = z
  .object({
    fileId: z.union([z.string(), z.number()]),
    type: maybe(z.string()),
    documentType: maybe(z.string()),
    name: maybe(z.string()),
    uploadedAt: z.any().optional(),
    uploadedBy: maybe(id),
  })
  .passthrough();
export type EmployeeDocument = z.infer<typeof documentSchema>;
