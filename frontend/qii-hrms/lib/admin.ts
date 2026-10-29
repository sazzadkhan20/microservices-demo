import type { z } from "zod";
import { ApiError, buildQuery, type QueryParams } from "@/lib/api/client";
import { isoDate } from "@/lib/format";

/* -------------------------------------------------------------------------- */
/* Form helpers                                                               */
/* -------------------------------------------------------------------------- */

export type FieldErrors = Record<string, string>;

/** Flatten a failed zod result into { fieldName: firstMessage }. */
export function fieldErrors(result: z.ZodSafeParseResult<unknown>): FieldErrors {
  const out: FieldErrors = {};
  if (result.success) return out;
  for (const issue of result.error.issues) {
    const key = String(issue.path[0] ?? "_");
    if (!out[key]) out[key] = issue.message;
  }
  return out;
}

/** "" -> undefined, otherwise trimmed. */
export const blank = (s: string): string | undefined => (s.trim() === "" ? undefined : s.trim());

/* -------------------------------------------------------------------------- */
/* jsonb-as-string columns                                                    */
/* -------------------------------------------------------------------------- */

/** work_schedule.working_days / leave_category.approval_chain arrive as JSON text (or an array). */
export function parseJsonList<T = unknown>(v: unknown): T[] {
  if (Array.isArray(v)) return v as T[];
  if (typeof v === "string" && v.trim()) {
    try {
      const p = JSON.parse(v);
      return Array.isArray(p) ? (p as T[]) : [];
    } catch {
      return [];
    }
  }
  return [];
}

export const DOW = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"] as const;
export const DOW_LABEL: Record<string, string> = {
  SUN: "Sun",
  MON: "Mon",
  TUE: "Tue",
  WED: "Wed",
  THU: "Thu",
  FRI: "Fri",
  SAT: "Sat",
};

export type ChainStep = { step: number; role: string };

export function parseChain(v: unknown): ChainStep[] {
  return parseJsonList<{ step?: number; role?: string }>(v)
    .filter((s) => s && typeof s.role === "string")
    .map((s, i) => ({ step: s.step ?? i + 1, role: String(s.role) }));
}

/* -------------------------------------------------------------------------- */
/* Blob / CSV                                                                 */
/* -------------------------------------------------------------------------- */

export type BlobResult = { blob: Blob; filename: string };

/** POST JSON through the BFF and return the binary body (cookie auth, same-origin). */
export async function postForBlob(
  path: string,
  body: unknown,
  fallbackName = "download",
  query?: QueryParams,
): Promise<BlobResult> {
  let res: Response;
  try {
    res = await fetch(`${path}${buildQuery(query)}`, {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json", Accept: "*/*" },
      body: JSON.stringify(body ?? {}),
    });
  } catch {
    throw new ApiError(0, "Network error — could not reach the server");
  }
  if (!res.ok) {
    let msg = res.statusText || "Request failed";
    try {
      const j = (await res.json()) as Record<string, unknown>;
      for (const k of ["message", "detail", "error", "title"]) {
        if (typeof j[k] === "string" && j[k]) {
          msg = j[k] as string;
          break;
        }
      }
    } catch {
      /* body was not JSON */
    }
    if (res.status === 401 && typeof window !== "undefined") window.location.assign("/login");
    throw new ApiError(res.status, msg);
  }
  const cd = res.headers.get("content-disposition") ?? "";
  const m = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cd);
  return { blob: await res.blob(), filename: m ? decodeURIComponent(m[1]) : fallbackName };
}

export function saveBlob({ blob, filename }: BlobResult) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

/** Minimal RFC-4180 CSV parser (quoted fields, escaped quotes, CRLF). */
export function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = "";
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"') {
        if (text[i + 1] === '"') {
          cell += '"';
          i++;
        } else quoted = false;
      } else cell += c;
    } else if (c === '"') quoted = true;
    else if (c === ",") {
      row.push(cell);
      cell = "";
    } else if (c === "\n" || c === "\r") {
      if (c === "\r" && text[i + 1] === "\n") i++;
      row.push(cell);
      cell = "";
      if (row.some((x) => x !== "")) rows.push(row);
      row = [];
    } else cell += c;
  }
  row.push(cell);
  if (row.some((x) => x !== "")) rows.push(row);
  return rows;
}

/* -------------------------------------------------------------------------- */
/* Calendar helpers (string based — no timezone drift)                        */
/* -------------------------------------------------------------------------- */

export const pad2 = (n: number) => String(n).padStart(2, "0");
export const ymd = (y: number, m: number, d: number) => `${y}-${pad2(m)}-${pad2(d)}`;

/** Month grid cells (Sunday first). `null` pads the leading / trailing blanks. */
export function monthCells(year: number, month: number): (string | null)[] {
  const first = new Date(year, month - 1, 1).getDay();
  const days = new Date(year, month, 0).getDate();
  const cells: (string | null)[] = Array.from({ length: first }, () => null);
  for (let d = 1; d <= days; d++) cells.push(ymd(year, month, d));
  while (cells.length % 7 !== 0) cells.push(null);
  return cells;
}

export function dowOf(iso: string): (typeof DOW)[number] {
  const [y, m, d] = iso.split("-").map(Number);
  return DOW[new Date(y, m - 1, d).getDay()];
}

/** Every ISO date from start..end inclusive, clamped to [from,to]. */
export function datesBetween(start: unknown, end: unknown, from: string, to: string): string[] {
  const a = isoDate(start);
  const b = isoDate(end) || a;
  if (!a) return [];
  const out: string[] = [];
  const [ay, am, ad] = a.split("-").map(Number);
  const cur = new Date(ay, am - 1, ad);
  for (let guard = 0; guard < 400; guard++) {
    const s = ymd(cur.getFullYear(), cur.getMonth() + 1, cur.getDate());
    if (s > b) break;
    if (s >= from && s <= to) out.push(s);
    cur.setDate(cur.getDate() + 1);
  }
  return out;
}

export function addDaysIso(iso: string, days: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  const dt = new Date(y, m - 1, d + days);
  return ymd(dt.getFullYear(), dt.getMonth() + 1, dt.getDate());
}

export const MONTH_NAMES = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

/** Pretty-print a jsonb-as-string value (audit before/after). */
export function prettyJson(v: unknown): string {
  if (v === null || v === undefined || v === "") return "";
  let cur: unknown = v;
  for (let i = 0; i < 2 && typeof cur === "string"; i++) {
    try {
      cur = JSON.parse(cur as string);
    } catch {
      break;
    }
  }
  return typeof cur === "string" ? cur : JSON.stringify(cur, null, 2);
}

export function oneLine(v: unknown, max = 80): string {
  const s = prettyJson(v).replace(/\s+/g, " ").trim();
  return s.length > max ? `${s.slice(0, max - 1)}…` : s;
}
