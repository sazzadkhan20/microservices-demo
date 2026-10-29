import { z } from "zod";

const id = z.union([z.number(), z.string()]);
const maybe = <T extends z.ZodType>(t: T) => t.nullable().optional();

/** GET /hrms/api/employees(/{id}) — EmployeeResponse. Dates arrive as [y,m,d] arrays or strings. */
export const employeeSchema = z
  .object({
    id,
    employeeCode: maybe(z.string()),
    fullName: maybe(z.string()),
    preferredName: maybe(z.string()),
    email: maybe(z.string()),
    jobTitle: maybe(z.string()),
    departmentId: maybe(id),
    departmentName: maybe(z.string()),
    designationId: maybe(id),
    designationName: maybe(z.string()),
    managerId: maybe(id),
    managerName: maybe(z.string()),
    teamId: maybe(id),
    teamName: maybe(z.string()),
    locationId: maybe(id),
    locationName: maybe(z.string()),
    locationCode: maybe(z.string()),
    employmentType: maybe(z.string()),
    startDate: z.any().optional(),
    probationEnd: z.any().optional(),
    contractEnd: z.any().optional(),
    payCurrency: maybe(z.string()),
    workPattern: maybe(z.string()),
    status: maybe(z.string()),
    phone: maybe(z.string()),
    personalEmail: maybe(z.string()),
    baseSalary: maybe(z.union([z.number(), z.string()])),
    hourlyRate: maybe(z.union([z.number(), z.string()])),
    active: z.boolean().optional(),
    roles: z.array(z.string()).optional(),
  })
  .passthrough();

export type Employee = z.infer<typeof employeeSchema>;

export const EMPLOYMENT_TYPES = ["FULL_TIME", "PART_TIME", "CONTRACT", "CASUAL", "INTERN"] as const;
export const WORK_PATTERNS = ["FIXED", "SHIFT", "FLEXIBLE"] as const;
export const EMPLOYEE_STATUSES = ["Active", "On leave", "Inactive", "Resigned", "Terminated"] as const;
