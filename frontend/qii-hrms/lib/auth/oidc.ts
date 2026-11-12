import "server-only";

import { createHash, randomBytes } from "crypto";
import { createRemoteJWKSet, jwtVerify } from "jose";
import { config } from "@/lib/config";

const GOOGLE_AUTH = "https://accounts.google.com/o/oauth2/v2/auth";
const GOOGLE_TOKEN = "https://oauth2.googleapis.com/token";
const GOOGLE_ISSUERS = ["https://accounts.google.com", "accounts.google.com"];
const JWKS = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));

export const OIDC_COOKIE = {
  state: "qii_oidc_state",
  verifier: "qii_oidc_verifier",
  nonce: "qii_oidc_nonce",
  next: "qii_oidc_next",
} as const;

export function oidcConfigured(): boolean {
  return Boolean(
    config.oidcEnabled &&
      config.googleClientId &&
      config.googleClientSecret &&
      config.oidcRedirectUri &&
      config.oidcBffKey,
  );
}

export function newOidcSecrets() {
  const state = base64Url(randomBytes(24));
  const nonce = base64Url(randomBytes(24));
  const verifier = base64Url(randomBytes(32));
  const challenge = base64Url(createHash("sha256").update(verifier).digest());
  return { state, nonce, verifier, challenge };
}

export function buildGoogleAuthorizeUrl(input: {
  state: string;
  nonce: string;
  challenge: string;
}): string {
  const params = new URLSearchParams({
    client_id: config.googleClientId,
    redirect_uri: config.oidcRedirectUri,
    response_type: "code",
    scope: "openid email profile",
    state: input.state,
    nonce: input.nonce,
    code_challenge: input.challenge,
    code_challenge_method: "S256",
    access_type: "online",
    prompt: "select_account",
  });
  // Workspace-hosted domain hint (production). Leave empty for Gmail test accounts.
  if (config.googleHd) {
    params.set("hd", config.googleHd);
  }
  return `${GOOGLE_AUTH}?${params.toString()}`;
}

export async function exchangeGoogleCode(code: string, verifier: string): Promise<{
  idToken: string;
}> {
  const body = new URLSearchParams({
    code,
    client_id: config.googleClientId,
    client_secret: config.googleClientSecret,
    redirect_uri: config.oidcRedirectUri,
    grant_type: "authorization_code",
    code_verifier: verifier,
  });
  const res = await fetch(GOOGLE_TOKEN, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
    body,
    cache: "no-store",
  });
  const json = (await res.json().catch(() => ({}))) as Record<string, unknown>;
  if (!res.ok) {
    const err = typeof json.error_description === "string"
      ? json.error_description
      : typeof json.error === "string"
        ? json.error
        : "Google token exchange failed";
    throw new Error(err);
  }
  const idToken = typeof json.id_token === "string" ? json.id_token : "";
  if (!idToken) throw new Error("Google response missing id_token");
  return { idToken };
}

export async function verifyGoogleIdToken(idToken: string, expectedNonce: string) {
  const { payload } = await jwtVerify(idToken, JWKS, {
    issuer: GOOGLE_ISSUERS,
    audience: config.googleClientId,
    algorithms: ["RS256"],
  });
  if (payload.nonce !== expectedNonce) {
    throw new Error("Invalid OIDC nonce");
  }
  const email = typeof payload.email === "string" ? payload.email : "";
  const sub = typeof payload.sub === "string" ? payload.sub : "";
  const emailVerified =
    payload.email_verified === true || payload.email_verified === "true";
  if (!sub || !email) throw new Error("Google id_token missing email/sub");
  if (!emailVerified) throw new Error("Google email is not verified");
  return {
    sub,
    email: email.toLowerCase(),
    emailVerified,
    hd: typeof payload.hd === "string" ? payload.hd : undefined,
  };
}

/** Same-origin relative path only. */
export function safeNextPath(raw: string | null | undefined): string {
  if (!raw) return "/";
  const trimmed = raw.trim();
  if (!trimmed.startsWith("/") || trimmed.startsWith("//") || trimmed.startsWith("/\\")) return "/";
  if (trimmed.includes("\\") || trimmed.includes("://") || trimmed.includes("@")) return "/";
  let decoded = trimmed;
  try {
    decoded = decodeURIComponent(trimmed);
  } catch {
    return "/";
  }
  if (!decoded.startsWith("/") || decoded.startsWith("//") || decoded.startsWith("/\\")) return "/";
  if (decoded.includes("\\") || decoded.includes("://") || decoded.includes("@")) return "/";
  return decoded;
}

function base64Url(buf: Buffer): string {
  return buf
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}
