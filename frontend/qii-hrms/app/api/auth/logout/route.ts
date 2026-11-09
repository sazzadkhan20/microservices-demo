import { NextRequest, NextResponse } from "next/server";
import {
  clearSessionCookie,
  gatewayFetch,
  jsonError,
  readSessionTokenFromRequest,
} from "@/lib/api/server";

export const dynamic = "force-dynamic";

/** Reject cross-origin logout (SameSite=Lax + Origin check). */
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
 * Guide §4 — logout clears httpOnly cookie and revokes JWT jti server-side via gateway.
 */
export async function POST(req: NextRequest) {
  if (isCrossOriginMutation(req)) return jsonError(403, "Cross-origin request blocked");

  const token = readSessionTokenFromRequest(req);
  if (token) {
    try {
      await gatewayFetch("/api/session/revoke", {
        method: "POST",
        token,
        timeoutMs: 5_000,
      });
    } catch {
      // Cookie is still cleared; revocation is best-effort if gateway is down.
    }
  }

  const res = NextResponse.json({ ok: true });
  clearSessionCookie(res);
  return res;
}
