import type { NextConfig } from "next";

const isProd = process.env.NODE_ENV === "production";

/**
 * Browser security headers for every page and BFF route (guide §4 / §9).
 * - CSP: same-origin only; Next.js App Router needs inline bootstrap scripts ('unsafe-inline'),
 *   dev mode additionally needs 'unsafe-eval' + the HMR websocket. blob:/data: cover document and
 *   photo previews (URL.createObjectURL). Google OIDC is a top-level redirect (form-action).
 * - No framing (clickjacking), no MIME sniffing, strict referrer, minimal browser features.
 * - HSTS only in production builds (served behind HTTPS ingress).
 */
const csp = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${isProd ? "" : " 'unsafe-eval'"}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  `connect-src 'self'${isProd ? "" : " ws: wss:"}`,
  "media-src 'self' blob:",
  "frame-src 'self' blob:",
  "worker-src 'self' blob:",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self' https://accounts.google.com",
  "frame-ancestors 'none'",
  // No upgrade-insecure-requests: the same production build is served over plain HTTP in local compose;
  // HTTPS is enforced by the ingress + HSTS (browsers ignore HSTS on plain HTTP).
].join("; ");

const securityHeaders = [
  { key: "Content-Security-Policy", value: csp },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()" },
  { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
  { key: "X-DNS-Prefetch-Control", value: "off" },
  ...(isProd ? [{ key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" }] : []),
];

const nextConfig: NextConfig = {
  // Self-contained server bundle for the Dockerfile.
  output: "standalone",
  poweredByHeader: false,
  reactStrictMode: true,
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
