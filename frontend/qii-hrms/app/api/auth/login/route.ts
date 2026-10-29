import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { config } from "@/lib/config";
import { demoAuthHeaders, gatewayJson, GatewayError, jsonError, setSessionCookie } from "@/lib/api/server";
import { meSchema } from "@/lib/schemas/me";

export const dynamic = "force-dynamic";

const bodySchema = z.object({
  employeeId: z.union([z.string(), z.number()]).transform((v) => String(v)),
});

const tokenSchema = z.object({
  accessToken: z.string().min(1).optional(),
  token: z.string().min(1).optional(),
  expiresIn: z.union([z.number(), z.string()]).optional(),
  expiresInSeconds: z.union([z.number(), z.string()]).optional(),
}).transform((t) => ({
  accessToken: t.accessToken || t.token || "",
  expiresInSeconds: Number(t.expiresInSeconds ?? t.expiresIn ?? config.defaultSessionSeconds) || config.defaultSessionSeconds,
}));

/** Account-picker login → Registry JWT → httpOnly cookie. */
export async function POST(req: NextRequest) {
  let json: unknown;
  try {
    json = await req.json();
  } catch {
    return jsonError(400, "Invalid JSON body");
  }
  const parsed = bodySchema.safeParse(json);
  if (!parsed.success) return jsonError(400, "employeeId is required");

  try {
    const tokenRaw = await gatewayJson("/api/registry/token", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...demoAuthHeaders() },
      body: JSON.stringify({
        tenantId: config.tenantId,
        employeeId: Number(parsed.data.employeeId),
      }),
    });
    const token = tokenSchema.parse(tokenRaw);
    if (!token.accessToken) return jsonError(502, "Token response missing accessToken");

    const meRaw = await gatewayJson("/hrms/api/me", { token: token.accessToken });
    const me = meSchema.safeParse(meRaw);
    if (!me.success) return jsonError(502, "Unexpected /me response");

    const res = NextResponse.json({ me: me.data });
    setSessionCookie(res, token.accessToken, token.expiresInSeconds);
    return res;
  } catch (err) {
    const e = err as GatewayError;
    return jsonError(e.status ?? 502, e.message ?? "Login failed");
  }
}
