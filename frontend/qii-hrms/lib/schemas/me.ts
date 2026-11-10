import { z } from "zod";

/** GET /hrms/api/me */
export const meSchema = z.object({
  employeeId: z.coerce.string(),
  name: z.string(),
  email: z.string().nullable().optional(),
  roles: z.array(z.string()).default([]),
  permissions: z.array(z.string()).default([]),
  /** feature:action → OWN | TEAM | ALL. Absent on older payloads. */
  scopes: z.record(z.string(), z.string()).optional(),
  department: z.string().nullable().optional(),
  position: z.string().nullable().optional(),
  phoneNumber: z.string().nullable().optional(),
  joinDate: z.string().nullable().optional(),
  status: z.string().nullable().optional(),
  /** Employee profile photo file id (MinIO-backed via file-service). */
  photoFileId: z.union([z.number(), z.string()]).nullable().optional(),
  photoObjectKey: z.string().nullable().optional(),
  /** Org location — drives attendance day / late / cut-off and UI clock (DR-01). */
  locationId: z.union([z.number(), z.string()]).nullable().optional(),
  locationName: z.string().nullable().optional(),
  locationTimeZone: z.string().nullable().optional(),
});

export type Me = z.infer<typeof meSchema>;
