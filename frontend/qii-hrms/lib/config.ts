import "server-only";

/** Server-only runtime configuration. Never import from client components. */

function str(name: string, fallback?: string): string {
  const v = process.env[name];
  if (v && v.trim()) return v.trim();
  return fallback ?? "";
}

function bool(name: string, fallback = false): boolean {
  const v = process.env[name];
  if (v == null || !v.trim()) return fallback;
  return ["1", "true", "yes", "on"].includes(v.trim().toLowerCase());
}

const isProd = process.env.NODE_ENV === "production";

/**
 * Shared secret for the gateway account-picker endpoints (token mint + account list).
 * Server-side only; sent as X-Demo-Auth-Key. Must equal gateway DEMO_AUTH_KEY from root `.env`.
 * No hardcoded fallback — misconfiguration fails closed.
 */
function resolveDemoAuthKey(): string {
  return str("DEMO_AUTH_KEY");
}

export const config = {
  gatewayUrl: str("GATEWAY_URL", "http://127.0.0.1:8090").replace(/\/+$/, ""),
  cookieName: str("COOKIE_NAME", "qii_session"),
  cookieSecure: process.env.COOKIE_SECURE
    ? process.env.COOKIE_SECURE.toLowerCase() === "true"
    : isProd,
  demoAuthKey: resolveDemoAuthKey(),
  demoAuthEnabled: bool("DEMO_AUTH_ENABLED", false),
  gatewayTimeoutMs: Number(str("GATEWAY_TIMEOUT_MS", "30000")) || 30000,
  /** Fallback session lifetime when the token response has no expiresInSeconds. */
  defaultSessionSeconds: 60 * 60 * 8,

  // --- Google OIDC (BFF-only secrets) ---
  oidcEnabled: bool("OIDC_ENABLED", false),
  googleClientId: str("GOOGLE_CLIENT_ID"),
  googleClientSecret: str("GOOGLE_CLIENT_SECRET"),
  oidcRedirectUri: str("OIDC_REDIRECT_URI", "http://localhost:3000/api/auth/oidc/callback"),
  oidcBffKey: str("OIDC_BFF_KEY"),
  /** Optional Google hosted-domain hint (e.g. qiicreative.com.au). Empty for Gmail test logins. */
  googleHd: str("GOOGLE_HD"),
} as const;
