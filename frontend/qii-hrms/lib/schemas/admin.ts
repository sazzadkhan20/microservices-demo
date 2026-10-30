import { z } from "zod";

/**
 * Row shapes for the settings / team / admin pages. They mirror what the live hrms-service
 * returns (verified against the running gateway). Dates are `z.any()` because LocalDate arrives
 * as [y,m,d], Instant as epoch seconds and hand-built maps as ISO strings — normalise with
 * `toDate` / `isoDate` from lib/format.
 */

const id = z.union([z.number(), z.string()]);
const maybe = <T extends z.ZodType>(t: T) => t.nullable().optional();

export const teamRowSchema = z.object({
  id,
  code: maybe(z.string()),
  name: z.string(),
  departmentId: maybe(id),
  leadEmployeeId: maybe(id),
  maxOnLeave: maybe(z.number()),
  clashAction: maybe(z.string()),
});
export type TeamRow = z.infer<typeof teamRowSchema>;

export const locationRowSchema = z.object({
  id,
  code: maybe(z.string()),
  name: z.string(),
  country: maybe(z.string()),
  timeZone: maybe(z.string()),
  defaultCurrency: maybe(z.string()),
});
export type LocationRow = z.infer<typeof locationRowSchema>;

export const departmentSchema = z.object({ id, code: maybe(z.string()), name: z.string() });
export type DepartmentRow = z.infer<typeof departmentSchema>;
export const departmentListSchema = z.array(departmentSchema);

export const orgTreeSchema = z.array(
  z.object({
    id,
    code: maybe(z.string()),
    name: z.string(),
    teams: z
      .array(
        z.object({
          id,
          code: maybe(z.string()),
          name: z.string(),
          leadEmployeeId: maybe(id),
          maxOnLeave: maybe(z.number()),
          clashAction: maybe(z.string()),
        }),
      )
      .default([]),
  }),
);
export type OrgTree = z.infer<typeof orgTreeSchema>;

export const holidayRowSchema = z.object({
  id,
  locationId: maybe(id),
  name: z.string(),
  startDate: z.any(),
  endDate: z.any(),
  holidayType: maybe(z.string()),
  optionalFlag: z.boolean().optional(),
});
export type HolidayRow = z.infer<typeof holidayRowSchema>;

export const scheduleRowSchema = z.object({
  id,
  scopeType: z.string(),
  scopeId: id,
  mode: maybe(z.string()),
  /** jsonb column mapped as String: arrives as JSON text, tolerate a real array too. */
  workingDays: z.any(),
  startTime: maybe(z.string()),
  endTime: maybe(z.string()),
  graceMinutes: maybe(z.number()),
  halfDayHours: maybe(z.union([z.number(), z.string()])),
  checkoutCutoff: maybe(z.string()),
  weeklyHours: maybe(z.union([z.number(), z.string()])),
});
export type ScheduleRow = z.infer<typeof scheduleRowSchema>;

export const leaveCategoryRowSchema = z.object({
  id,
  code: z.string(),
  name: z.string(),
  quotaDays: maybe(z.union([z.number(), z.string()])),
  periodStart: z.any().optional(),
  periodEnd: z.any().optional(),
  paid: z.boolean().optional(),
  halfDayAllowed: z.boolean().optional(),
  attachmentMode: maybe(z.string()),
  attachmentAfterDays: maybe(z.number()),
  noticeDays: maybe(z.number()),
  maxPerRequest: maybe(z.number()),
  negativeCap: maybe(z.union([z.number(), z.string()])),
  approvalChain: z.any().optional(),
  active: z.boolean().optional(),
});
export type LeaveCategoryRow = z.infer<typeof leaveCategoryRowSchema>;

export const leavePolicySchema = z
  .object({
    annualDays: maybe(z.number()),
    workWeekDays: maybe(z.number()),
    workingDays: z.array(z.string()).optional(),
    noticeMode: maybe(z.string()),
    defaultNoticeDays: maybe(z.number()),
    unionNoticeDays: maybe(z.number()),
    nonUnionNoticeDays: maybe(z.number()),
    requireCoverageCheck: z.boolean().optional(),
    coverageMode: maybe(z.string()),
    requireReliefOnCoverageFail: z.boolean().optional(),
    defaultLeaveTypeCode: maybe(z.string()),
    approvalChain: z.array(z.object({ step: z.number().optional(), role: z.string() })).optional(),
    leaveTypes: z
      .array(
        z.object({
          code: z.string(),
          name: z.string(),
          paid: z.boolean().optional(),
          requiresBalance: z.boolean().optional(),
          allowWhenZeroBalance: z.boolean().optional(),
          allowWhenCoverageFail: z.boolean().optional(),
          deductBalance: z.boolean().optional(),
        }),
      )
      .optional(),
    coverageRules: z.array(z.record(z.string(), z.any())).optional(),
    source: maybe(z.string()),
  })
  .passthrough();
export type LeavePolicy = z.infer<typeof leavePolicySchema>;

export const permissionSchema = z.object({
  feature: z.string(),
  action: z.string(),
  scope: maybe(z.string()),
  key: maybe(z.string()),
});

export const roleSchema = z.object({
  id,
  code: z.string(),
  name: maybe(z.string()),
  description: maybe(z.string()),
  systemRole: z.boolean().optional(),
  active: z.boolean().optional(),
  protectFromOffboard: z.boolean().optional(),
  immutablePermissions: z.boolean().optional(),
  grantsWildcard: z.boolean().optional(),
  minHolders: z.number().optional(),
  assignableBy: maybe(z.string()),
  holderCount: z.number().optional(),
  permissions: z.array(permissionSchema).default([]),
});
export type RoleView = z.infer<typeof roleSchema>;
export const roleListSchema = z.array(roleSchema);

/** Slim employee row used for names / membership lookups. */
export const directoryEmployeeSchema = z.object({
  id,
  employeeCode: maybe(z.string()),
  fullName: maybe(z.string()),
  email: maybe(z.string()),
  jobTitle: maybe(z.string()),
  status: maybe(z.string()),
  departmentId: maybe(id),
  departmentName: maybe(z.string()),
  teamId: maybe(id),
  locationId: maybe(id),
  managerId: maybe(id),
  roles: z.array(z.string()).optional(),
});
export type DirectoryEmployee = z.infer<typeof directoryEmployeeSchema>;

export const auditRowSchema = z.object({
  id,
  at: z.any(),
  actorEmployeeId: maybe(id),
  action: maybe(z.string()),
  entity: maybe(z.string()),
  entityId: maybe(z.string()),
  beforeJson: z.any().optional(),
  afterJson: z.any().optional(),
  ip: maybe(z.string()),
});
export type AuditRow = z.infer<typeof auditRowSchema>;

export const taskRowSchema = z
  .object({
    id,
    title: z.string(),
    clientName: maybe(z.string()),
    status: maybe(z.string()),
    priority: maybe(z.string()),
    dueDate: z.any().optional(),
    createdBy: maybe(id),
    externalRef: maybe(z.string()),
    createdAt: z.any().optional(),
    assigneeIds: z.array(id).optional(),
    loggedMinutes: maybe(z.number()),
    loggedHours: maybe(z.number()),
    overdue: z.boolean().optional(),
  })
  .passthrough();
export type TaskRow = z.infer<typeof taskRowSchema>;

export const workLogRowSchema = z
  .object({
    id: maybe(id),
    employeeId: maybe(id),
    employeeName: maybe(z.string()),
    workDate: z.any().optional(),
    taskId: maybe(id),
    taskTitle: maybe(z.string()),
    minutes: maybe(z.number()),
    note: maybe(z.string()),
    flagged: z.boolean().optional(),
    punchedMinutes: maybe(z.number()),
    punchedHours: maybe(z.number()),
    dayLoggedMinutes: maybe(z.number()),
    missingLog: z.boolean().optional(),
    createdAt: z.any().optional(),
  })
  .passthrough();
export type WorkLogRow = z.infer<typeof workLogRowSchema>;

/** GET /attendance/team row (unpaged array). */
export const teamAttendanceRowSchema = z
  .object({
    employeeId: maybe(id),
    employeeCode: maybe(z.string()),
    employeeName: maybe(z.string()),
    name: maybe(z.string()),
    departmentId: maybe(id),
    date: maybe(z.string()),
    checkIn: z.any().optional(),
    checkOut: z.any().optional(),
    status: maybe(z.string()),
    statusCode: maybe(z.string()),
    statusAlias: maybe(z.string()),
    locationLabel: maybe(z.string()),
    totalHours: maybe(z.number()),
    workedMinutes: maybe(z.number()),
    workingHours: maybe(z.string()),
    loggedMinutes: maybe(z.number()),
    loggedHours: maybe(z.number()),
    expectedHours: maybe(z.union([z.number(), z.string()])),
    note: maybe(z.string()),
  })
  .passthrough();
export type TeamAttendanceRow = z.infer<typeof teamAttendanceRowSchema>;
export const teamAttendanceListSchema = z.array(teamAttendanceRowSchema);

/** LeaveApplicationResponse (subset). */
export const leaveAppRowSchema = z
  .object({
    id,
    employeeId: maybe(id),
    leaveTypeCode: maybe(z.string()),
    startDate: z.any(),
    endDate: z.any(),
    workingDaysCounted: maybe(z.union([z.number(), z.string()])),
    status: maybe(z.string()),
    reason: maybe(z.string()),
  })
  .passthrough();
export type LeaveAppRow = z.infer<typeof leaveAppRowSchema>;

export const correctionRowSchema = z
  .object({ id, employeeId: maybe(id), status: maybe(z.string()) })
  .passthrough();

export const leaveBalanceRowSchema = z.object({
  id: maybe(id),
  employeeId: maybe(id),
  leaveTypeCode: z.string(),
  year: z.number().optional(),
  balanceDays: maybe(z.union([z.number(), z.string()])),
});
