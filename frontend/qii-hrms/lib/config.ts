import "server-only";

/** Server-only runtime configuration. Never import from client components. */

function str(name: string, fallback: string): string {
  const v = process.env[name];
  return v && v.trim() ? v.trim() : fallback;
}

const isProd = process.env.NODE_ENV === "production";

/**
 * Shared secret for the gateway's account-picker endpoints (token mint + account list).
 * Server-side only; sent as X-Demo-Auth-Key. Falls back to GATEWAY_SERVICE_KEY, then (non-production
 * only) to the local default that matches the gateway's default.
 */
const LOCAL_DEFAULT_DEMO_KEY = "local-service-key-change-me";
function resolveDemoAuthKey(): string {
  const configured = process.env.DEMO_AUTH_KEY?.trim() || process.env.GATEWAY_SERVICE_KEY?.trim();
  if (configured) return configured;
  return isProd ? "" : LOCAL_DEFAULT_DEMO_KEY;
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
