import type { z } from "zod";
import { pageResponseSchema, type PageResponse } from "@/lib/schemas/page";

/**
 * Browser-side API helpers. Only ever talks to same-origin Next `/api/*` (the BFF);
 * the access token lives in an httpOnly cookie and is never visible here.
 *
 * BFF path map (client path -> gateway path):
 *   /api/hrms/<x>          -> /hrms/api/<x>
 *   /api/payroll/<x>       -> /payroll/api/payroll/<x>
 *   /api/files/<x>         -> /files/api/files/<x>
 *   /api/notifications/<x> -> /notifications/api/notifications/<x>
 *   /api/reports/<x>       -> /reports/api/reports/<x>
 */

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public body?: unknown,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export type QueryValue = string | number | boolean | null | undefined;
export type QueryParams = Record<string, QueryValue | QueryValue[]>;

export function buildQuery(params?: QueryParams): string {
  if (!params) return "";
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    const values = Array.isArray(v) ? v : [v];
    for (const item of values) {
      if (item === undefined || item === null || item === "") continue;
      sp.append(k, String(item));
    }
  }
  const s = sp.toString();
  return s ? `?${s}` : "";
}

/* ---- list/table query contract: page,size,sort,q,filter.* ---- */

export type ListQuery = {
  page?: number; // 0-based
  size?: number;
  /** "field,asc" | "field,desc" */
  sort?: string;
  q?: string;
  /** Sent as filter.<key>=<value> */
  filters?: Record<string, QueryValue | QueryValue[]>;
};

export const DEFAULT_PAGE_SIZE = 20;

/** Always emits page, size, sort (when set), q (when set) and filter.* params. */
export function listParams(query: ListQuery = {}): QueryParams {
  const params: QueryParams = {
    // Backend PageQuery is 1-based
    page: query.page ?? 1,
    size: query.size ?? DEFAULT_PAGE_SIZE,
    sort: query.sort,
    q: query.q?.trim() || undefined,
  };
  for (const [k, v] of Object.entries(query.filters ?? {})) {
    params[`filter.${k}`] = v;
  }
  return params;
}

/* ---- core fetch ---- */

export type ApiFetchOptions<S extends z.ZodType | undefined = undefined> = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  query?: QueryParams;
  /** JSON-serialised unless FormData/Blob/string. */
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  schema?: S;
};

function redirectToLogin() {
  if (typeof window === "undefined") return;
  if (window.location.pathname.startsWith("/login")) return;
  const next = encodeURIComponent(window.location.pathname + window.location.search);
  window.location.assign(`/login?next=${next}`);
}

async function parseBody(res: Response): Promise<unknown> {
  if (res.status === 204) return undefined;
  const text = await res.text();
  if (!text) return undefined;
  const ct = res.headers.get("content-type") ?? "";
  if (ct.includes("json")) {
    try {
      return JSON.parse(text);
    } catch {
      return text;
    }
  }
  return text;
}

function messageFrom(body: unknown, fallback: string): string {
  if (body && typeof body === "object") {
    const b = body as Record<string, unknown>;
    for (const k of ["message", "detail", "error", "title"]) {
      if (typeof b[k] === "string" && b[k]) return b[k] as string;
    }
  }
  if (typeof body === "string" && body) return body.slice(0, 300);
  return fallback;
}

export async function apiFetch<T = unknown, S extends z.ZodType | undefined = undefined>(
  path: string,
  opts: ApiFetchOptions<S> = {},
): Promise<S extends z.ZodType ? z.infer<S> : T> {
  const { method = "GET", query, body, headers, signal, schema } = opts;
  const init: RequestInit = {
    method,
    credentials: "same-origin",
    signal,
    headers: { Accept: "application/json", ...headers },
  };
  if (body !== undefined && method !== "GET") {
    if (body instanceof FormData || body instanceof Blob || typeof body === "string") {
      init.body = body;
    } else {
      init.body = JSON.stringify(body);
      (init.headers as Record<string, string>)["Content-Type"] = "application/json";
    }
  }

  let res: Response;
  try {
    res = await fetch(`${path}${buildQuery(query)}`, init);
  } catch (err) {
    if (err instanceof DOMException && err.name === "AbortError") throw err;
    throw new ApiError(0, "Network error — could not reach the server");
  }

  const data = await parseBody(res);
  if (!res.ok) {
    if (res.status === 401 && !path.startsWith("/api/auth/")) redirectToLogin();
    throw new ApiError(res.status, messageFrom(data, res.statusText || "Request failed"), data);
  }
  if (schema) {
    const parsed = (schema as z.ZodType).safeParse(data);
    if (!parsed.success) {
      throw new ApiError(502, "Response did not match the expected shape", parsed.error.issues);
    }
    return parsed.data as never;
  }
  return data as never;
}

/** GET a list endpoint and validate the PageResponse envelope. */
export async function apiFetchPage<S extends z.ZodType>(
  path: string,
  itemSchema: S,
  query: ListQuery = {},
  extra?: { signal?: AbortSignal; query?: QueryParams },
): Promise<PageResponse<z.infer<S>>> {
  return apiFetch(path, {
    query: { ...extra?.query, ...listParams(query) },
    signal: extra?.signal,
    schema: pageResponseSchema(itemSchema),
  }) as Promise<PageResponse<z.infer<S>>>;
}

/** Service-scoped helpers so call sites never hand-build BFF prefixes. */
export const api = {
  hrms: (p: string) => `/api/hrms/${p.replace(/^\/+/, "")}`,
  payroll: (p: string) => `/api/payroll/${p.replace(/^\/+/, "")}`,
  files: (p: string) => `/api/files/${p.replace(/^\/+/, "")}`,
  notifications: (p = "") => `/api/notifications/${p.replace(/^\/+/, "")}`.replace(/\/$/, ""),
  reports: (p: string) => `/api/reports/${p.replace(/^\/+/, "")}`,
};

/** Trigger an in-app download through the BFF (cookie auth, no token exposure). */
export async function downloadFile(path: string, query?: QueryParams, fallbackName = "download") {
  const res = await fetch(`${path}${buildQuery(query)}`, { credentials: "same-origin" });
  if (!res.ok) {
    let msg = res.statusText;
    try {
      msg = messageFrom(await res.json(), msg);
    } catch {}
    if (res.status === 401) redirectToLogin();
    throw new ApiError(res.status, msg || "Download failed");
  }
  const cd = res.headers.get("content-disposition") ?? "";
  const m = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cd);
  const name = m ? decodeURIComponent(m[1]) : fallbackName;
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
