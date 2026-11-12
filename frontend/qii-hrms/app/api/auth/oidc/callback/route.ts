import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { config } from "@/lib/config";
import {
  exchangeGoogleCode,
  OIDC_COOKIE,
  oidcConfigured,
  safeNextPath,
  verifyGoogleIdToken,
} from "@/lib/auth/oidc";
import { gatewayJson, GatewayError, setSessionCookie } from "@/lib/api/server";
import { meSchema } from "@/lib/schemas/me";

export const dynamic = "force-dynamic";

const tokenSchema = z.object({
  accessToken: z.string().min(1).optional(),
  token: z.string().min(1).optional(),
  expiresIn: z.union([z.number(), z.string()]).optional(),
  expiresInSeconds: z.union([z.number(), z.string()]).optional(),
}).transform((t) => ({
  accessToken: t.accessToken || t.token || "",
  expiresInSeconds:
    Number(t.expiresInSeconds ?? t.expiresIn ?? config.defaultSessionSeconds) ||
    config.defaultSessionSeconds,
}));

function clearOidcCookies(res: NextResponse) {
  for (const name of Object.values(OIDC_COOKIE)) {
    res.cookies.set(name, "", {
      httpOnly: true,
      secure: config.cookieSecure,
      sameSite: "lax",
      path: "/",
      maxAge: 0,
    });
  }
}

function loginErrorRedirect(req: NextRequest, code: string, detail?: string) {
  const url = new URL("/login", req.nextUrl.origin);
  url.searchParams.set("error", code);
  if (detail) url.searchParams.set("detail", detail.slice(0, 180));
  const res = NextResponse.redirect(url);
  clearOidcCookies(res);
  return res;
}

/**
 * Google OIDC callback: verify state → exchange code (PKCE) → verify id_token (nonce) →
 * gateway mints session → httpOnly cookie → redirect.
 */
export async function GET(req: NextRequest) {
  if (!oidcConfigured()) {
    return loginErrorRedirect(req, "oidc_misconfigured");
  }

  const err = req.nextUrl.searchParams.get("error");
  if (err) {
    const desc = req.nextUrl.searchParams.get("error_description") || err;
    return loginErrorRedirect(req, "google_denied", desc);
  }

  const code = req.nextUrl.searchParams.get("code");
  const state = req.nextUrl.searchParams.get("state");
  const expectedState = req.cookies.get(OIDC_COOKIE.state)?.value;
  const verifier = req.cookies.get(OIDC_COOKIE.verifier)?.value;
  const nonce = req.cookies.get(OIDC_COOKIE.nonce)?.value;
  const next = safeNextPath(req.cookies.get(OIDC_COOKIE.next)?.value);

  if (!code || !state || !expectedState || !verifier || !nonce) {
    return loginErrorRedirect(req, "oidc_state");
  }
  if (state !== expectedState) {
    return loginErrorRedirect(req, "oidc_state");
  }

  try {
    const { idToken } = await exchangeGoogleCode(code, verifier);
    // BFF verifies first (nonce); gateway re-verifies signature/aud/iss before minting.
    await verifyGoogleIdToken(idToken, nonce);

    const tokenRaw = await gatewayJson("/api/auth/oidc/token", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Oidc-Bff-Key": config.oidcBffKey,
      },
      body: JSON.stringify({ idToken, nonce }),
    });
    const token = tokenSchema.parse(tokenRaw);
    if (!token.accessToken) {
      return loginErrorRedirect(req, "oidc_token");
    }

    const meRaw = await gatewayJson("/hrms/api/me", { token: token.accessToken });
    const me = meSchema.safeParse(meRaw);
    if (!me.success) {
      return loginErrorRedirect(req, "oidc_me");
    }

    const destination =
      !me.data.roles || me.data.roles.length === 0 ? "/access-pending" : next;
    const res = NextResponse.redirect(new URL(destination, req.nextUrl.origin));
    clearOidcCookies(res);
    setSessionCookie(res, token.accessToken, token.expiresInSeconds);
    return res;
  } catch (err) {
    const e = err as GatewayError | Error;
    const message = e.message || "OIDC login failed";
    const lower = message.toLowerCase();
    if (lower.includes("no employee record")) {
      return loginErrorRedirect(req, "no_employee", message);
    }
    if (lower.includes("not active") || lower.includes("inactive")) {
      return loginErrorRedirect(req, "inactive", message);
    }
    if (lower.includes("company google") || lower.includes("work email")) {
      return loginErrorRedirect(req, "domain", message);
    }
    if (e instanceof GatewayError && e.status === 403) {
      return loginErrorRedirect(req, "forbidden", message);
    }
    return loginErrorRedirect(req, "oidc_failed", message);
  }
}
