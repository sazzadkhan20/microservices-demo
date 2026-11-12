"use client";

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { z } from "zod";
import { DonutChart, HBarList, ProgressBar, type Slice } from "@/components/dashboard/MiniCharts";
import { useAuth } from "@/components/auth/AuthProvider";
import { EmptyState } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { StatCard } from "@/components/ui/StatCard";
import { StatusPill } from "@/components/ui/StatusPill";
import { ErrorNote, Loading, NoAccess } from "@/components/ui/QueryState";
import { api, apiFetch } from "@/lib/api/client";
import { useLocations } from "@/lib/api/lookups";
import { addDaysIso } from "@/lib/admin";
import { fmtDate, fmtHours, fmtNumber, fmtTime, humanize, todayIso } from "@/lib/format";
import { useDebounced } from "@/lib/hooks";

const num = z.union([z.number(), z.string()]).nullable().optional();

const daySchema = z
  .object({
    date: z.string(),
    statusCode: z.string().optional(),
    statusLabel: z.string().optional(),
    checkInAt: z.any().optional(),
    checkOutAt: z.any().optional(),
    expectedHours: num,
    workedHours: num,
    loggedHours: num,
    missingWorkLog: z.boolean().optional(),
    leaveImpact: z.string().optional(),
    punchLocation: z.string().nullable().optional(),
  })
  .passthrough();

const detailSchema = z
  .object({
    from: z.string().optional(),
    to: z.string().optional(),
    employee: z
      .object({
        id: z.union([z.number(), z.string()]).optional(),
        fullName: z.string().optional(),
        employeeCode: z.string().optional(),
        workPattern: z.string().optional(),
        hourly: z.boolean().optional(),
        startDate: z.string().nullable().optional(),
        contractEnd: z.string().nullable().optional(),
        locationId: z.union([z.number(), z.string()]).nullable().optional(),
        locationTimeZone: z.string().nullable().optional(),
      })
      .passthrough()
      .optional(),
    summary: z.record(z.string(), z.unknown()).optional(),
    byStatus: z.record(z.string(), z.unknown()).optional(),
    days: z.array(daySchema).default([]),
    dayCount: num,
  })
  .passthrough();

const PAGE_SIZES = [10, 25, 50];

const STATUS_COLORS: Record<string, string> = {
  P: "var(--good)",
  W: "var(--good)",
  L: "var(--warn)",
  HD: "var(--info)",
  OL: "#8b7ec8",
  A: "var(--bad)",
  NC: "var(--bad)",
  M: "var(--warn)",
  WO: "var(--muted, #94a3b8)",
  HO: "#c48b6a",
  NS: "var(--sunk)",
  NE: "transparent",
};

function n(v: unknown): number {
  if (v == null || v === "") return 0;
  const x = Number(v);
  return Number.isFinite(x) ? x : 0;
}

/** Trailing 7 days ending today — matches dashboard Week filter. */
function trailingWeek(end: string): { from: string; to: string } {
  return { from: addDaysIso(end, -6), to: end };
}

export default function EmployeeAttendanceDetailPage() {
  const { can } = useAuth();
  const params = useParams<{ id: string }>();
  const search = useSearchParams();
  const employeeId = String(params?.id ?? "");
  const today = todayIso();
  const fallback = trailingWeek(today);
  const from = search.get("from")?.trim() || fallback.from;
  const to = search.get("to")?.trim() || fallback.to;
  const locations = useLocations(can("attendance", "view"));

  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(25);
  const dq = useDebounced(q, 250);

  const allowed = can("attendance", "view");

  const detail = useQuery({
    queryKey: ["employee-attendance-detail", employeeId, from, to],
    queryFn: ({ signal }) =>
      apiFetch(api.hrms(`dashboard/employee/${employeeId}/attendance`), {
        query: { from, to },
        schema: detailSchema,
        signal,
      }),
    enabled: allowed && !!employeeId && !!from && !!to,
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  });

  const summary = detail.data?.summary ?? {};
  const emp = detail.data?.employee;
  const days = detail.data?.days ?? [];
  const locationName = useMemo(() => {
    const id = emp?.locationId;
    if (id == null || id === "") return "";
    const hit = (locations.data ?? []).find((l) => String(l.id) === String(id));
    return hit?.name?.trim() || "";
  }, [emp?.locationId, locations.data]);

  const statusOptions = useMemo(() => {
    const codes = new Set<string>();
    for (const d of days) {
      const c = String(d.statusCode ?? "").trim();
      if (c) codes.add(c);
    }
    return [...codes].sort((a, b) => a.localeCompare(b));
  }, [days]);

  const filtered = useMemo(() => {
    const needle = dq.trim().toLowerCase();
    return days.filter((d) => {
      if (status && String(d.statusCode ?? "") !== status) return false;
      if (!needle) return true;
      const hay = `${d.date} ${d.statusCode ?? ""} ${d.statusLabel ?? ""} ${d.leaveImpact ?? ""}`.toLowerCase();
      return hay.includes(needle);
    });
  }, [days, dq, status]);

  const filterSig = `${dq}|${status}|${size}|${from}|${to}`;
  const [lastSig, setLastSig] = useState(filterSig);
  if (lastSig !== filterSig) {
    setLastSig(filterSig);
    if (page !== 1) setPage(1);
  }

  const totalElements = filtered.length;
  const totalPages = Math.max(1, Math.ceil(totalElements / size) || 1);
  const safePage = Math.min(page, totalPages);
  const pageFrom = totalElements === 0 ? 0 : (safePage - 1) * size + 1;
  const pageTo = Math.min(safePage * size, totalElements);
  const paged = filtered.slice((safePage - 1) * size, safePage * size);

  const donut: Slice[] = useMemo(() => {
    const by = detail.data?.byStatus ?? {};
    return Object.entries(by)
      .filter(([code]) => code !== "NE") // pre-join blanks stay out of the mix chart
      .map(([code, count]) => ({
        label: code === "NS" ? "No punch" : humanize(code),
        value: n(count),
        color: STATUS_COLORS[code] ?? "var(--accent)",
      }))
      .filter((s) => s.value > 0)
      .sort((a, b) => b.value - a.value);
  }, [detail.data?.byStatus]);

  const hourBars = useMemo(
    () => [
      { label: "Expected", value: Math.round(n(summary.expectedHours) * 10) / 10, color: "var(--muted, #94a3b8)" },
      { label: "Worked", value: Math.round(n(summary.workedHours) * 10) / 10, color: "var(--accent)" },
      { label: "Logged", value: Math.round(n(summary.loggedHours) * 10) / 10, color: "var(--info)" },
    ],
    [summary],
  );

  const workedPct = n(summary.workedPercent);
  const workedTone = workedPct >= 95 ? "good" : workedPct >= 80 ? "warn" : "bad";

  if (!allowed) {
    return (
      <div className="stack">
        <PageHeader title="Employee attendance" />
        <NoAccess what="attendance records" />
      </div>
    );
  }

  return (
    <div className="stack">
      <PageHeader
        title={emp?.fullName ? emp.fullName : "Employee attendance"}
        subtitle={
          emp
            ? [
                emp.employeeCode || null,
                locationName || null,
                emp.hourly ? "Hourly" : humanize(String(emp.workPattern ?? "")),
                from !== to ? `${fmtDate(from)} – ${fmtDate(to)}` : fmtDate(from),
              ]
                .filter(Boolean)
                .join(" · ")
            : `${fmtDate(from)}${from !== to ? ` – ${fmtDate(to)}` : ""}`
        }
        actions={
          <>
            <Link href={`/admin/dashboard`} className="btn">
              ← Dashboard
            </Link>
            {employeeId ? (
              <Link href={`/employees/${employeeId}`} className="btn">
                Profile
              </Link>
            ) : null}
          </>
        }
      />

      <ErrorNote error={detail.error} />

      {detail.isLoading && !detail.data ? <Loading /> : null}

      {!detail.isLoading && detail.data ? (
        <>
          <div className="grid g4">
            <StatCard
              label="Worked vs expected"
              value={`${fmtHours(summary.workedHours)} / ${fmtHours(summary.expectedHours)}`}
              sub={`Gap ${fmtHours(summary.gapHours)} · ${fmtNumber(workedPct)}%`}
              tone={n(summary.gapHours) < 0 ? "warn" : "good"}
            />
            <StatCard
              label="Present / leave / absent"
              value={`${fmtNumber(n(summary.daysPresent), 0)} / ${fmtNumber(n(summary.leaveDays), 0)} / ${fmtNumber(n(summary.absences), 0)}`}
              sub={`${fmtNumber(n(summary.lateArrivals), 0)} late`}
            />
            <StatCard
              label="Missing checkout"
              value={fmtNumber(n(summary.missingCheckouts), 0)}
              tone={n(summary.missingCheckouts) ? "warn" : ""}
              sub={`${fmtNumber(n(summary.missingWorkLogDays), 0)} days with no work log`}
            />
            <StatCard
              label="Logged hours"
              value={fmtHours(summary.loggedHours)}
              sub={
                <div className="row" style={{ gap: 8, marginTop: 6 }}>
                  <ProgressBar pct={workedPct} tone={workedTone} />
                  <span className="mono small">{fmtNumber(workedPct)}%</span>
                </div>
              }
            />
          </div>

          <div className="grid g2">
            <section className="card stack" style={{ gap: 12 }}>
              <h2 style={{ margin: 0 }}>Status mix</h2>
              <p className="muted small" style={{ margin: 0 }}>
                {emp?.hourly
                  ? "Hourly staff: expected accrues every calendar day — no holiday, week-off, or leave labels."
                  : "Every day in the period, same rules as the Work hours table."}
              </p>
              <div className="row" style={{ gap: 20, alignItems: "center", flexWrap: "wrap" }}>
                <DonutChart
                  slices={donut}
                  center={
                    <div>
                      <div className="mono" style={{ fontSize: 18, fontWeight: 700 }}>
                        {fmtNumber(n(detail.data.dayCount) || days.length, 0)}
                      </div>
                      <div className="muted small">days</div>
                    </div>
                  }
                />
                <div className="stack" style={{ gap: 6, flex: 1, minWidth: 160 }}>
                  {donut.map((s) => (
                    <div key={s.label} className="row between small">
                      <span className="row" style={{ gap: 8 }}>
                        <span
                          style={{
                            width: 10,
                            height: 10,
                            borderRadius: 2,
                            background: s.color,
                            display: "inline-block",
                          }}
                        />
                        {s.label}
                      </span>
                      <span className="mono">{fmtNumber(s.value, 0)}</span>
                    </div>
                  ))}
                  {donut.length === 0 ? <p className="muted small">No status data</p> : null}
                </div>
              </div>
            </section>

            <section className="card stack" style={{ gap: 12 }}>
              <h2 style={{ margin: 0 }}>Hours</h2>
              <p className="muted small" style={{ margin: 0 }}>
                Expected, punched work, and work-log hours for this period.
              </p>
              <HBarList items={hourBars} />
            </section>
          </div>

          <section className="card stack">
            <div className="card-head" style={{ marginBottom: 0 }}>
              <div>
                <h2 style={{ margin: 0 }}>Day by day</h2>
                <p className="muted small" style={{ margin: "4px 0 0" }}>
                  {fmtNumber(totalElements, 0)} day{totalElements === 1 ? "" : "s"}
                  {status || dq.trim() ? " matching filters" : " in period"}
                  {detail.isFetching ? " · updating…" : ""}
                </p>
              </div>
            </div>

            <div className="filters">
              <label className="field wide">
                <span>Search</span>
                <input
                  type="search"
                  placeholder="Search date, status…"
                  value={q}
                  onChange={(e) => setQ(e.target.value)}
                  aria-label="Search attendance days"
                />
              </label>
              <label className="field">
                <span>Status</span>
                <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Filter by status">
                  <option value="">All statuses</option>
                  {statusOptions.map((code) => (
                    <option key={code} value={code}>
                      {humanize(code)}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Per page</span>
                <select value={size} onChange={(e) => setSize(Number(e.target.value))} aria-label="Page size">
                  {PAGE_SIZES.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="row between" style={{ flexWrap: "wrap", gap: 8 }}>
              <p className="muted small" style={{ margin: 0 }}>
                {totalElements === 0
                  ? "No matching days"
                  : `Showing ${fmtNumber(pageFrom, 0)}–${fmtNumber(pageTo, 0)} of ${fmtNumber(totalElements, 0)}`}
              </p>
              <div className="row" style={{ gap: 6 }}>
                <button type="button" className="btn sm" disabled={safePage <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))}>
                  Previous
                </button>
                <span className="mono small" style={{ alignSelf: "center", minWidth: 72, textAlign: "center" }}>
                  {safePage} / {totalPages}
                </span>
                <button
                  type="button"
                  className="btn sm"
                  disabled={safePage >= totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </button>
              </div>
            </div>

            <div className="tbl-wrap" style={{ opacity: detail.isFetching ? 0.75 : 1, transition: "opacity .15s" }}>
              <table>
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Status</th>
                    <th>Location</th>
                    <th>Check-in</th>
                    <th>Check-out</th>
                    <th className="n">Expected</th>
                    <th className="n">Worked</th>
                    <th className="n">Logged</th>
                  </tr>
                </thead>
                <tbody>
                  {paged.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="empty">
                        {days.length === 0 ? "No attendance days in this period" : "No days match these filters"}
                      </td>
                    </tr>
                  ) : (
                    paged.map((d) => {
                      const beforeJoin = String(d.statusCode ?? "").toUpperCase() === "NE";
                      return (
                        <tr key={d.date} className={beforeJoin ? "muted" : undefined}>
                          <td className="mono">{fmtDate(d.date)}</td>
                          <td>
                            {beforeJoin ? (
                              <span className="muted small">—</span>
                            ) : (
                              <>
                                <StatusPill status={d.statusLabel || d.statusCode} />
                                {d.missingWorkLog ? <div className="muted small">No work log</div> : null}
                              </>
                            )}
                          </td>
                          <td className="small">
                            {beforeJoin ? "—" : d.punchLocation?.trim() || locationName || "—"}
                          </td>
                          <td className="mono small">
                            {beforeJoin ? "—" : d.checkInAt ? fmtTime(d.checkInAt) : "—"}
                          </td>
                          <td className="mono small">
                            {beforeJoin ? "—" : d.checkOutAt ? fmtTime(d.checkOutAt) : "—"}
                          </td>
                          <td className="n mono small">{beforeJoin ? "—" : fmtHours(d.expectedHours)}</td>
                          <td className="n mono small">{beforeJoin ? "—" : fmtHours(d.workedHours)}</td>
                          <td className="n mono small">{beforeJoin ? "—" : fmtHours(d.loggedHours)}</td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>

            {totalElements > size ? (
              <div className="row" style={{ justifyContent: "center", gap: 6 }}>
                <button type="button" className="btn sm" disabled={safePage <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))}>
                  Previous
                </button>
                <span className="mono small" style={{ alignSelf: "center" }}>
                  Page {safePage} of {totalPages}
                </span>
                <button
                  type="button"
                  className="btn sm"
                  disabled={safePage >= totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </button>
              </div>
            ) : null}
          </section>
        </>
      ) : null}

      {!detail.isLoading && !detail.error && detail.data && days.length === 0 ? (
        <EmptyState title="No days in range">Try another period from the admin dashboard.</EmptyState>
      ) : null}

      <p className="muted small">
        Period {from}
        {from !== to ? ` → ${to}` : ""}. Open from the dashboard Work hours table to keep the same window.
        {" · "}
        <Link href={`/employees/${employeeId}/attendance?from=${addDaysIso(from, -7)}&to=${addDaysIso(to, -7)}`}>
          previous 7 days
        </Link>
        {to < today ? (
          <>
            {" · "}
            <Link
              href={`/employees/${employeeId}/attendance?from=${addDaysIso(from, 7)}&to=${
                addDaysIso(to, 7) > today ? today : addDaysIso(to, 7)
              }`}
            >
              next 7 days
            </Link>
          </>
        ) : null}
      </p>
    </div>
  );
}
