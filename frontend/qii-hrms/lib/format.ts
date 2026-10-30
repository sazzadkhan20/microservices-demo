/**
 * Formatting helpers shared by pages. All tolerate null/undefined/odd input.
 *
 * NOTE: the backend serialises dates inconsistently — JPA entities/DTO records emit
 *   LocalDate  -> [2026, 4, 14]
 *   Instant    -> 1793265569.422352000 (epoch seconds, decimal)
 * while hand-built maps emit ISO strings. `toDate` normalises all of them.
 */

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
    return new Date(y, m - 1, d, h, mi, Math.floor(s));
  }
  if (typeof v === "number") {
    return new Date(v < 1e11 ? v * 1000 : v);
  }
  const s = String(v);
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return new Date(`${s}T00:00:00`);
  if (/^\d+(\.\d+)?$/.test(s)) {
    const n = Number(s);
    return new Date(n < 1e11 ? n * 1000 : n);
  }
  const d = new Date(s);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** yyyy-MM-dd (local) for <input type="date"> and request bodies; "" when unknown. */
export function isoDate(v: unknown): string {
  if (typeof v === "string" && /^\d{4}-\d{2}-\d{2}/.test(v)) return v.slice(0, 10);
  const d = toDate(v);
  if (!d) return "";
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export function fmtDate(v: unknown): string {
  const d = toDate(v);
  if (!d) return v ? String(v) : "—";
  // IR-UI-05 — e.g. "Fri 30 Oct 2026"
  const weekday = d.toLocaleDateString("en-GB", { weekday: "short" });
  const rest = d.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
  return `${weekday} ${rest}`;
}

export function fmtDateShort(v: unknown): string {
  const d = toDate(v);
  if (!d) return v ? String(v) : "—";
  return d.toLocaleDateString(undefined, { day: "2-digit", month: "short" });
}

export function fmtTime(v: unknown): string {
  const d = toDate(v);
  if (!d) return "—";
  return d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
}

export function fmtDateTime(v: unknown): string {
  const d = toDate(v);
  if (!d) return v ? String(v) : "—";
  return d.toLocaleString(undefined, { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
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

export function fmtMoney(v: unknown, currency?: string | null): string {
  if (v === null || v === undefined || v === "") return "—";
  // Payroll me/current nests MTD as `{ netAmount, grossAmount, ... }`
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

export function todayIso(): string {
  return isoDate(new Date());
}

export function currentPeriod(): string {
  return todayIso().slice(0, 7);
}

/** Inclusive calendar-day count between two yyyy-MM-dd strings. */
export function daysBetween(start: string, end: string): number {
  const a = new Date(`${start}T00:00:00`).getTime();
  const b = new Date(`${end}T00:00:00`).getTime();
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
