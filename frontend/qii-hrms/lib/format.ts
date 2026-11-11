/**
 * Formatting helpers shared by pages. All tolerate null/undefined/odd input.
 *
 * DR-01 — times are stored UTC and shown in the employee's location time zone.
 * AuthProvider calls setAppTimeZone(me.locationTimeZone) after login so fmt* /
 * todayIso use that zone system-wide (not the browser/DB clock).
 *
 * NOTE: the backend serialises dates inconsistently — JPA entities/DTO records emit
 *   LocalDate  -> [2026, 4, 14]
 *   Instant    -> 1793265569.422352000 (epoch seconds, decimal)
 * while hand-built maps emit ISO strings. `toDate` normalises all of them.
 */

let appTimeZone: string | null = null;

/** Called from AuthProvider when /me loads — IANA id e.g. Australia/Darwin. */
export function setAppTimeZone(tz: string | null | undefined): void {
  const t = tz?.trim();
  appTimeZone = t ? t : null;
}

export function getAppTimeZone(): string | undefined {
  return appTimeZone ?? undefined;
}

export function str(v: unknown, fallback = "—"): string {
  if (v === null || v === undefined || v === "") return fallback;
  return String(v);
}

/** Parse array / epoch-seconds / ISO string into a Date (or null). */
export function toDate(v: unknown): Date | null {
  if (v === null || v === undefined || v === "") return null;
  if (Array.isArray(v)) {
    const [y, m, d, h = 0, mi = 0, s = 0] = v.map(Number);
    if (!y || !m || !d) return null;
    // Treat date-only arrays as calendar dates (noon UTC) so TZ display cannot shift the day.
    if (h === 0 && mi === 0 && s === 0 && arguments.length) {
      /* keep */
    }
    return new Date(Date.UTC(y, m - 1, d, h, mi, Math.floor(s)));
  }
  if (typeof v === "number") {
    return new Date(v < 1e11 ? v * 1000 : v);
  }
  const s = String(v);
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) {
    const [y, m, d] = s.split("-").map(Number);
    return new Date(Date.UTC(y, m - 1, d, 12, 0, 0));
  }
  if (/^\d+(\.\d+)?$/.test(s)) {
    const n = Number(s);
    return new Date(n < 1e11 ? n * 1000 : n);
  }
  const d = new Date(s);
  return Number.isNaN(d.getTime()) ? null : d;
}

function resolveTz(timeZone?: string | null): string | undefined {
  const t = timeZone?.trim() || appTimeZone || undefined;
  return t || undefined;
}

/** yyyy-MM-dd in the viewer location zone (or explicit zone). */
export function isoDate(v: unknown, timeZone?: string | null): string {
  if (typeof v === "string" && /^\d{4}-\d{2}-\d{2}/.test(v)) return v.slice(0, 10);
  const d = toDate(v);
  if (!d) return "";
  const tz = resolveTz(timeZone);
  if (!tz) {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }
  return formatParts(d, tz, "ymd");
}

export function fmtDate(v: unknown, timeZone?: string | null): string {
  // Pure calendar dates — never shift via browser midnight.
  if (typeof v === "string" && /^\d{4}-\d{2}-\d{2}$/.test(v)) {
    const [y, m, d] = v.split("-").map(Number);
    const noon = new Date(Date.UTC(y, m - 1, d, 12));
    const weekday = noon.toLocaleDateString("en-GB", { weekday: "short", timeZone: "UTC" });
    const rest = noon.toLocaleDateString("en-GB", {
      day: "numeric",
      month: "short",
      year: "numeric",
      timeZone: "UTC",
    });
    return `${weekday} ${rest}`;
  }
  const d = toDate(v);
  if (!d) return v ? String(v) : "—";
  const tz = resolveTz(timeZone);
  const opts: Intl.DateTimeFormatOptions = {
    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric",
    ...(tz ? { timeZone: tz } : {}),
  };
  const weekday = d.toLocaleDateString("en-GB", { weekday: "short", ...(tz ? { timeZone: tz } : {}) });
  const rest = d.toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    ...(tz ? { timeZone: tz } : {}),
  });
  void opts;
  return `${weekday} ${rest}`;
}

export function fmtDateShort(v: unknown, timeZone?: string | null): string {
  if (typeof v === "string" && /^\d{4}-\d{2}-\d{2}$/.test(v)) {
    const [y, m, d] = v.split("-").map(Number);
    const noon = new Date(Date.UTC(y, m - 1, d, 12));
    return noon.toLocaleDateString(undefined, { day: "2-digit", month: "short", timeZone: "UTC" });
  }
  const d = toDate(v);
  if (!d) return v ? String(v) : "—";
  const tz = resolveTz(timeZone);
  return d.toLocaleDateString(undefined, {
    day: "2-digit",
    month: "short",
    ...(tz ? { timeZone: tz } : {}),
  });
}

export function fmtTime(v: unknown, timeZone?: string | null): string {
  const d = toDate(v);
  if (!d) return "—";
  const tz = resolveTz(timeZone);
  return d.toLocaleTimeString("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    ...(tz ? { timeZone: tz } : {}),
  });
}

export function fmtDateTime(v: unknown, timeZone?: string | null): string {
  const d = toDate(v);
  if (!d) return v ? String(v) : "—";
  const tz = resolveTz(timeZone);
  return d.toLocaleString("en-GB", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    ...(tz ? { timeZone: tz } : {}),
  });
}

export function fmtRange(start: unknown, end: unknown): string {
  const a = isoDate(start);
  const b = isoDate(end);
  if (!a) return "—";
  return !b || a === b ? fmtDateShort(a) : `${fmtDateShort(a)} – ${fmtDateShort(b)}`;
}

export function fmtNumber(v: unknown, digits = 2): string {
  if (v === null || v === undefined || v === "") return "—";
  const n = Number(v);
  if (Number.isNaN(n)) return String(v);
  return n.toLocaleString(undefined, { maximumFractionDigits: digits, minimumFractionDigits: 0 });
}

/**
 * Leave day amounts — nearest half-day step (same rule as leave-service LeaveDays.of).
 * Examples: 2.79 → 3, 2.3 → 2.5, 0.5 → 0.5. Whole days show without ".0".
 */
export function fmtLeaveDays(v: unknown): string {
  if (v === null || v === undefined || v === "") return "—";
  const n = Number(v);
  if (Number.isNaN(n)) return String(v);
  const stepped = Math.round(n * 2) / 2;
  return stepped.toLocaleString(undefined, {
    maximumFractionDigits: 1,
    minimumFractionDigits: 0,
  });
}

/** True when n is a half-day step (…, -1, -0.5, 0, 0.5, 1, …). */
export function isLeaveHalfDayStep(n: number): boolean {
  if (!Number.isFinite(n)) return false;
  return Math.abs(n * 2 - Math.round(n * 2)) < 1e-9;
}

export function fmtMoney(v: unknown, currency?: string | null): string {
  if (v === null || v === undefined || v === "") return "—";
  if (typeof v === "object" && v !== null) {
    const o = v as Record<string, unknown>;
    const nested = o.netAmount ?? o.net ?? o.amount ?? o.grossAmount;
    const cur = currency ?? (typeof o.currency === "string" ? o.currency : null);
    return fmtMoney(nested, cur);
  }
  const n = Number(v);
  if (Number.isNaN(n)) return String(v);
  const body = n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return currency ? `${currency} ${body}` : body;
}

/** IR-UI-05 — e.g. "8h 5m" from decimal hours or minutes. */
export function fmtHours(v: unknown, unit: "hours" | "minutes" = "hours"): string {
  if (v === null || v === undefined || v === "") return "—";
  const n = Number(v);
  if (Number.isNaN(n)) return String(v);
  const totalMinutes = unit === "minutes" ? Math.round(n) : Math.round(n * 60);
  const h = Math.floor(Math.abs(totalMinutes) / 60);
  const m = Math.abs(totalMinutes) % 60;
  const sign = totalMinutes < 0 ? "-" : "";
  if (m === 0) return `${sign}${h}h`;
  return `${sign}${h}h ${m}m`;
}

export function initials(name: string | null | undefined): string {
  if (!name) return "?";
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase();
}

/** Today's calendar date in the employee's location zone. */
export function todayIso(timeZone?: string | null): string {
  const tz = resolveTz(timeZone);
  if (!tz) return isoDate(new Date());
  return formatParts(new Date(), tz, "ymd");
}

export function currentPeriod(timeZone?: string | null): string {
  return todayIso(timeZone).slice(0, 7);
}

/** Inclusive calendar-day count between two yyyy-MM-dd strings. */
export function daysBetween(start: string, end: string): number {
  const a = new Date(`${start}T00:00:00Z`).getTime();
  const b = new Date(`${end}T00:00:00Z`).getTime();
  if (Number.isNaN(a) || Number.isNaN(b)) return 0;
  return Math.round((b - a) / 86_400_000) + 1;
}

export function humanize(v: unknown): string {
  if (!v) return "—";
  const s = String(v).replace(/_/g, " ").toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** Attendance rows expose either `checkIn`/`checkOut` (hrms) or legacy `checkInAt`/`checkOutAt`. */
export function pick(r: Record<string, unknown>, ...keys: string[]): unknown {
  for (const k of keys) if (r[k] !== undefined && r[k] !== null && r[k] !== "") return r[k];
  return null;
}

/**
 * Convert wall-clock YYYY-MM-DD + HH:mm in an IANA zone to a UTC Date.
 * Used for correction / HR edit so times match the employee's location, not the browser.
 */
export function zonedLocalToUtc(date: string, hm: string, timeZone: string): Date {
  const [y, mo, d] = date.split("-").map(Number);
  const [h, mi] = hm.split(":").map(Number);
  let utc = Date.UTC(y, mo - 1, d, h, mi, 0);
  for (let i = 0; i < 4; i++) {
    const parts = new Intl.DateTimeFormat("en-US", {
      timeZone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hourCycle: "h23",
    }).formatToParts(new Date(utc));
    const get = (t: string) => Number(parts.find((p) => p.type === t)?.value || 0);
    const asUtc = Date.UTC(get("year"), get("month") - 1, get("day"), get("hour"), get("minute"), get("second") || 0);
    const desired = Date.UTC(y, mo - 1, d, h, mi, 0);
    utc += desired - asUtc;
  }
  return new Date(utc);
}

function formatParts(d: Date, timeZone: string, mode: "ymd"): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(d);
  const get = (t: string) => parts.find((p) => p.type === t)?.value || "";
  if (mode === "ymd") return `${get("year")}-${get("month")}-${get("day")}`;
  return "";
}
