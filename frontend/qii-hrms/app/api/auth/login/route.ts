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

/** Reject cross-origin login (guide §4 CSRF control alongside SameSite=Lax). */
function isCrossOriginMutation(req: NextRequest): boolean {
  const origin = req.headers.get("origin");
  if (!origin) return false;
  try {
    const originHost = new URL(origin).host;
    const hosts = [req.headers.get("x-forwarded-host"), req.headers.get("host"), req.nextUrl.host]
      .filter(Boolean)
      .map((h) => String(h).split(",")[0].trim());
    return !hosts.includes(originHost);
  } catch {
    return true;
  }
}

/**
 * Account-picker login → Registry JWT (jti) → httpOnly cookie.
 * OIDC deferred; cookie is never exposed to browser JS. Session rotates on each login.
 */
export async function POST(req: NextRequest) {
  if (isCrossOriginMutation(req)) return jsonError(403, "Cross-origin request blocked");

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
    // Rotate session cookie on every successful login (guide §4).
    setSessionCookie(res, token.accessToken, token.expiresInSeconds);
    return res;
  } catch (err) {
    const e = err as GatewayError;
    return jsonError(e.status ?? 502, e.message ?? "Login failed");
  }
}
