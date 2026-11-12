import { NextRequest, NextResponse } from "next/server";
import { config } from "@/lib/config";
import {
  buildGoogleAuthorizeUrl,
  newOidcSecrets,
  OIDC_COOKIE,
  oidcConfigured,
  safeNextPath,
} from "@/lib/auth/oidc";

export const dynamic = "force-dynamic";

function oidcCookieOptions(maxAge = 600) {
  return {
    httpOnly: true,
    secure: config.cookieSecure,
    sameSite: "lax" as const,
    path: "/",
    maxAge,
  };
}

/**
 * Start Google OIDC Authorization Code + PKCE (guide §4 / SEC-01).
 * Stores state/nonce/verifier in httpOnly cookies, then redirects to Google.
 */
export async function GET(req: NextRequest) {
  if (!oidcConfigured()) {
    const login = new URL("/login", req.nextUrl.origin);
    login.searchParams.set("error", "oidc_misconfigured");
    return NextResponse.redirect(login);
  }

  const next = safeNextPath(req.nextUrl.searchParams.get("next"));
  const secrets = newOidcSecrets();
  const authorizeUrl = buildGoogleAuthorizeUrl({
    state: secrets.state,
    nonce: secrets.nonce,
    challenge: secrets.challenge,
  });

  const res = NextResponse.redirect(authorizeUrl);
  const opts = oidcCookieOptions();
  res.cookies.set(OIDC_COOKIE.state, secrets.state, opts);
  res.cookies.set(OIDC_COOKIE.verifier, secrets.verifier, opts);
  res.cookies.set(OIDC_COOKIE.nonce, secrets.nonce, opts);
  res.cookies.set(OIDC_COOKIE.next, next, opts);
  return res;
}
