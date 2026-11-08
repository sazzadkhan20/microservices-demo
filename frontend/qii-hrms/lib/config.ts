import "server-only";

/** Server-only runtime configuration. Never import from client components. */

function str(name: string, fallback?: string): string {
  const v = process.env[name];
  if (v && v.trim()) return v.trim();
  return fallback ?? "";
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
  tenantId: str("TENANT_ID", "qii_creative"),
  gatewayUrl: str("GATEWAY_URL", "http://127.0.0.1:8090").replace(/\/+$/, ""),
  cookieName: str("COOKIE_NAME", "qii_session"),
  cookieSecure: process.env.COOKIE_SECURE
    ? process.env.COOKIE_SECURE.toLowerCase() === "true"
    : isProd,
  demoAuthKey: resolveDemoAuthKey(),
  gatewayTimeoutMs: Number(str("GATEWAY_TIMEOUT_MS", "30000")) || 30000,
  /** Fallback session lifetime when the token response has no expiresInSeconds. */
  defaultSessionSeconds: 60 * 60 * 8,
} as const;
