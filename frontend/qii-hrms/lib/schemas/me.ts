import { z } from "zod";

/** GET /hrms/api/me */
export const meSchema = z.object({
  employeeId: z.coerce.string(),
  name: z.string(),
  email: z.string().nullable().optional(),
  tenantId: z.string().nullable().optional(),
  roles: z.array(z.string()).default([]),
  permissions: z.array(z.string()).default([]),
  /** feature:action → OWN | TEAM | ALL. Absent on older payloads. */
  scopes: z.record(z.string(), z.string()).optional(),
  department: z.string().nullable().optional(),
  position: z.string().nullable().optional(),
  phoneNumber: z.string().nullable().optional(),
  joinDate: z.string().nullable().optional(),
  status: z.string().nullable().optional(),
});

export type Me = z.infer<typeof meSchema>;
