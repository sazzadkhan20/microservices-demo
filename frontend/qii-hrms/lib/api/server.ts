import "server-only";

import { cookies } from "next/headers";
import { NextResponse, type NextRequest } from "next/server";
import { config } from "@/lib/config";
import { meSchema, type Me } from "@/lib/schemas/me";

/* -------------------------------------------------------------------------- */
/* Session cookie                                                             */
/* -------------------------------------------------------------------------- */

export function sessionCookieOptions(maxAgeSeconds?: number) {
  return {
    httpOnly: true,
    secure: config.cookieSecure,
    sameSite: "lax" as const,
    path: "/",
    maxAge: maxAgeSeconds && maxAgeSeconds > 0 ? maxAgeSeconds : config.defaultSessionSeconds,
  };
}

export function setSessionCookie(res: NextResponse, token: string, maxAgeSeconds?: number) {
  res.cookies.set(config.cookieName, token, sessionCookieOptions(maxAgeSeconds));
}

export function clearSessionCookie(res: NextResponse) {
  res.cookies.set(config.cookieName, "", { ...sessionCookieOptions(0), maxAge: 0 });
}

/** Read the access token from the httpOnly cookie (server components / actions). */
export async function readSessionToken(): Promise<string | null> {
  const store = await cookies();
  return store.get(config.cookieName)?.value || null;
}

export function readSessionTokenFromRequest(req: NextRequest): string | null {
  return req.cookies.get(config.cookieName)?.value || null;
}

/* -------------------------------------------------------------------------- */
/* Gateway fetch                                                              */
/* -------------------------------------------------------------------------- */

export class GatewayError extends Error {
  constructor(
    public status: number,
    message: string,
    public body?: unknown,
  ) {
    super(message);
    this.name = "GatewayError";
  }
}

/**
 * Headers for the gateway's account-picker endpoints (/api/registry/token, /hrms/api/auth/accounts).
 * The shared secret never reaches the browser; it is attached here, server-side only.
 */
export function demoAuthHeaders(): Record<string, string> {
  if (!config.demoAuthKey) {
    throw new GatewayError(503, "Server misconfigured: set DEMO_AUTH_KEY (or GATEWAY_SERVICE_KEY)");
  }
  return { "X-Demo-Auth-Key": config.demoAuthKey };
}

export function gatewayUrl(path: string, search?: string | URLSearchParams): string {
  const p = path.startsWith("/") ? path : `/${path}`;
  const qs = typeof search === "string" ? search.replace(/^\?/, "") : (search?.toString() ?? "");
  return `${config.gatewayUrl}${p}${qs ? `?${qs}` : ""}`;
}

export type GatewayFetchInit = Omit<RequestInit, "signal"> & {
  /** Bearer token; omitted for public endpoints. */
  token?: string | null;
  search?: string | URLSearchParams;
  /** Override the header-phase timeout (ms). */
  timeoutMs?: number;
};

/**
 * Low-level fetch to the API Gateway. Returns the raw Response (body not consumed).
 * The timeout only covers time-to-headers so large downloads are not cut off.
 */
export async function gatewayFetch(path: string, init: GatewayFetchInit = {}): Promise<Response> {
  const { token, search, timeoutMs, headers: initHeaders, ...rest } = init;
  const headers = new Headers(initHeaders);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (!headers.has("Accept")) headers.set("Accept", "application/json");

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs ?? config.gatewayTimeoutMs);
  try {
    return await fetch(gatewayUrl(path, search), {
      ...rest,
      headers,
      cache: "no-store",
      redirect: "manual",
      signal: controller.signal,
    });
  } catch (err) {
    const aborted = err instanceof Error && err.name === "AbortError";
    throw new GatewayError(
      aborted ? 504 : 502,
      aborted ? "Gateway timed out" : "Gateway unreachable",
    );
  } finally {
    clearTimeout(timer);
  }
}

/** Fetch + parse JSON, throwing GatewayError on non-2xx. */
export async function gatewayJson<T = unknown>(
  path: string,
  init: GatewayFetchInit = {},
): Promise<T> {
  const res = await gatewayFetch(path, init);
  const text = await res.text();
  let body: unknown = undefined;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }
  if (!res.ok) throw new GatewayError(res.status, extractMessage(body) ?? res.statusText, body);
  return body as T;
}

export function extractMessage(body: unknown): string | undefined {
  if (!body) return undefined;
  if (typeof body === "string") return body.slice(0, 300);
  if (typeof body === "object") {
    const b = body as Record<string, unknown>;
    for (const k of ["message", "detail", "error", "title"]) {
      const v = b[k];
      if (typeof v === "string" && v) return v;
    }
  }
  return undefined;
}

/* -------------------------------------------------------------------------- */
/* Identity helpers (used by layouts / pages)                                 */
/* -------------------------------------------------------------------------- */

export type MeResult =
  | { status: "ok"; me: Me }
  | { status: "unauthenticated" }
  | { status: "error"; message: string };

/** Fetch /hrms/api/me for the current cookie. Never throws. */
export async function fetchMe(token: string | null): Promise<MeResult> {
  if (!token) return { status: "unauthenticated" };
  try {
    const raw = await gatewayJson("/hrms/api/me", { token });
    const parsed = meSchema.safeParse(raw);
    if (!parsed.success) return { status: "error", message: "Unexpected /me response shape" };
    return { status: "ok", me: parsed.data };
  } catch (err) {
    if (err instanceof GatewayError) {
      if (err.status === 401) return { status: "unauthenticated" };
      return { status: "error", message: err.message };
    }
    return { status: "error", message: "Unexpected error" };
  }
}

export async function getMe(): Promise<MeResult> {
  return fetchMe(await readSessionToken());
}

/* -------------------------------------------------------------------------- */
/* Generic BFF proxy                                                          */
/* -------------------------------------------------------------------------- */

const FORWARD_REQUEST_HEADERS = [
  "accept",
  "accept-language",
  "content-type",
  "range",
  "if-none-match",
  "if-modified-since",
  "x-request-id",
];

const SKIP_RESPONSE_HEADERS = new Set([
  "connection",
  "keep-alive",
  "transfer-encoding",
  "upgrade",
  "set-cookie",
  "content-encoding",
  "content-length",
  "cache-control",
  "expires",
  "pragma",
]);

export function jsonError(status: number, message: string) {
  return NextResponse.json({ status, message }, { status });
}

/** Reject cross-origin state-changing requests (defence in depth on top of SameSite=Lax). */
function isCrossOriginMutation(req: NextRequest): boolean {
  if (["GET", "HEAD", "OPTIONS"].includes(req.method)) return false;
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

export type ProxyTarget = {
  /**
   * Gateway path prefix the BFF segments are appended to, e.g.
   *  hrms          -> /hrms/api
   *  payroll       -> /payroll/api/payroll
   *  files         -> /files/api/files
   *  notifications -> /notifications/api/notifications
   *  reports       -> /reports/api/reports
   */
  upstreamPrefix: string;
};

function safeSegments(segments: string[] | undefined): string[] | null {
  const out: string[] = [];
  for (const s of segments ?? []) {
    if (!s || s === "." || s === ".." || /[\\/]/.test(s) || s.includes("\0")) return null;
    out.push(encodeURIComponent(s));
  }
  return out;
}

/**
 * Proxy a browser request to the Gateway, attaching Authorization from the httpOnly cookie.
 * On upstream 401 the session cookie is cleared so the client can redirect to /login.
 */
export async function proxyToGateway(
  req: NextRequest,
  segments: string[] | undefined,
  target: ProxyTarget,
): Promise<Response> {
  const token = readSessionTokenFromRequest(req);
  if (!token) return jsonError(401, "Not authenticated");
  if (isCrossOriginMutation(req)) return jsonError(403, "Cross-origin request blocked");

  const segs = safeSegments(segments);
  if (!segs) return jsonError(400, "Invalid path");

  const upstreamPath = [target.upstreamPrefix.replace(/\/+$/, ""), ...segs].join("/");

  const headers = new Headers();
  for (const name of FORWARD_REQUEST_HEADERS) {
    const v = req.headers.get(name);
    if (v) headers.set(name, v);
  }

  const hasBody = !["GET", "HEAD"].includes(req.method) && req.body !== null;

  let upstream: Response;
  try {
    upstream = await gatewayFetch(upstreamPath, {
      method: req.method,
      headers,
      token,
      search: req.nextUrl.search,
      body: hasBody ? req.body : undefined,
      timeoutMs: hasBody ? Math.max(config.gatewayTimeoutMs, 120_000) : undefined,
      // Required by undici when streaming a request body.
      ...(hasBody ? ({ duplex: "half" } as object) : {}),
    });
  } catch (err) {
    const e = err as GatewayError;
    return jsonError(e.status ?? 502, e.message ?? "Gateway error");
  }

  const resHeaders = new Headers();
  upstream.headers.forEach((value, key) => {
    if (!SKIP_RESPONSE_HEADERS.has(key.toLowerCase())) resHeaders.set(key, value);
  });
  if (!upstream.headers.get("content-encoding")) {
    const len = upstream.headers.get("content-length");
    if (len) resHeaders.set("content-length", len);
  }
  resHeaders.set("Cache-Control", "private, no-store");

  const noBody = upstream.status === 204 || upstream.status === 304 || req.method === "HEAD";
  const res = new NextResponse(noBody ? null : upstream.body, {
    status: upstream.status,
    headers: resHeaders,
  });
  if (upstream.status === 401) clearSessionCookie(res);
  return res;
}
