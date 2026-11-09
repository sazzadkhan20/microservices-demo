import { z } from "zod";

/** Row returned by GET /hrms/api/auth/accounts */
export const accountSchema = z.object({
  employeeId: z.coerce.string(),
  name: z.string(),
  email: z.string().nullable().optional(),
  role: z.string().nullable().optional(),
  department: z.string().nullable().optional(),
});

export const accountListSchema = z.array(accountSchema);

export type Account = z.infer<typeof accountSchema>;

export const loginRequestSchema = z.object({
  employeeId: z.coerce.string().min(1),
});

/** Gateway POST /api/auth/token response. */
export const tokenResponseSchema = z.object({
  accessToken: z.string().min(1),
  token: z.string().optional(),
  tokenType: z.string().optional(),
  employeeId: z.union([z.number(), z.string()]).nullable().optional(),
  expiresInSeconds: z.number().optional(),
});
