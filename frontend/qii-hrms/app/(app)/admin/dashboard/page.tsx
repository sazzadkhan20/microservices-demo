"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { DonutChart, HBarList, ProgressBar } from "@/components/dashboard/MiniCharts";
import { useAuth } from "@/components/auth/AuthProvider";
import { PageHeader } from "@/components/ui/PageHeader";
import { StatCard } from "@/components/ui/StatCard";
import { StatusPill } from "@/components/ui/StatusPill";
import { ErrorNote } from "@/components/ui/QueryState";
import { api, apiFetch } from "@/lib/api/client";
import { useLocations, useTeams } from "@/lib/api/lookups";
import { addDaysIso, saveBlob } from "@/lib/admin";
import { fmtDate, fmtHours, fmtNumber, humanize, todayIso } from "@/lib/format";

const num = z.union([z.number(), z.string()]).nullable().optional();
const adminDashSchema = z
  .object({
    date: z.string().optional(),
    headcountTotal: num,
    headcountActive: num,
    headcountByLocation: z
      .array(z.object({ label: z.string(), count: z.union([z.number(), z.string()]) }))
      .optional(),
    headcountByType: z
      .array(z.object({ label: z.string(), count: z.union([z.number(), z.string()]) }))
      .optional(),
    endingIn30Days: z.array(z.record(z.string(), z.unknown())).optional(),
    endingIn30DaysCount: num,
    endingBreakdown: z.record(z.string(), z.unknown()).optional(),
  })
  .passthrough();

const attendanceTodaySchema = z
  .object({
    date: z.string().optional(),
    headcountInScope: num,
    expectedWorking: num,
    checkedIn: num,
    onLeave: num,
    late: num,
    absent: num,
    halfDay: num,
    missingCheckout: num,
    byStatus: z.record(z.string(), z.unknown()).optional(),
  })
  .passthrough();

const pendingStatsSchema = z
  .object({
    pending: num,
    waitingTwoPlusWorkingDays: num,
    idleWorkingDays: num,
  })
  .passthrough();

const workHoursRowSchema = z
  .object({
    employeeId: z.union([z.number(), z.string()]).optional(),
    employeeName: z.string().optional(),
    employeeCode: z.string().optional(),
    workPattern: z.string().optional(),
    expectedHours: num,
    workedHours: num,
    loggedHours: num,
    gapHours: num,
    workedPercent: num,
    daysPresent: num,
    leaveDays: num,
    absences: num,
    lateArrivals: num,
    missingWorkLogDays: num,
    missingCheckouts: num,
  })
  .passthrough();

const workHoursSchema = z
  .object({
    from: z.string().optional(),
    to: z.string().optional(),
    rows: z.array(workHoursRowSchema).default([]),
    totals: z.record(z.string(), z.unknown()).optional(),
    page: num,
    size: num,
    totalElements: num,
    totalPages: num,
  })
  .passthrough();

type Period = "day" | "week" | "month";
type SortKey = "name" | "worked" | "gap" | "absences" | "leave" | "missing";

function n(v: unknown): number {
  if (v == null || v === "") return 0;
  const x = Number(v);
  return Number.isFinite(x) ? x : 0;
}

/** Trailing windows ending on `anchor` (default today) — never includes future days past today. */
function periodRange(p: Period, anchor: string, today: string): { from: string; to: string } {
  const end = anchor > today ? today : anchor;
  if (p === "day") return { from: end, to: end };
  if (p === "week") return { from: addDaysIso(end, -6), to: end };
  // Trailing 30 calendar days ending today/anchor (today inclusive → −29).
  return { from: addDaysIso(end, -29), to: end };
}

function shiftAnchor(period: Period, anchor: string, dir: -1 | 1, today: string): string {
  let next: string;
  if (period === "day") next = addDaysIso(anchor, dir);
  else if (period === "week") next = addDaysIso(anchor, dir * 7);
  else next = addDaysIso(anchor, dir * 30);
  return next > today ? today : next;
}

function csvEscape(v: unknown): string {
  const s = v == null ? "" : String(v);
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

function useDebounced(value: string, ms = 300): string {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

const TYPE_COLORS = ["var(--accent)", "var(--good)", "var(--warn)", "var(--info)", "#8b7ec8", "#c48b6a"];
const ATT_COLORS = {
  present: "var(--good)",
  late: "var(--warn)",
  halfDay: "var(--info)",
  onLeave: "#8b7ec8",
  absent: "var(--bad)",
  missingCheckout: "#c48b6a",
};

const STALE = 45_000;

export default function AdminDashboardPage() {
  const router = useRouter();
  const { can, me } = useAuth();
  const today = todayIso();

  const [whPeriod, setWhPeriod] = useState<Period>("week");
  const [whAnchor, setWhAnchor] = useState(today);
  const [whLocationId, setWhLocationId] = useState("");
  const [whTeamId, setWhTeamId] = useState("");
  const [whSort, setWhSort] = useState<SortKey>("name");
  const [whSearch, setWhSearch] = useState("");
  const [whPage, setWhPage] = useState(1);
  const [whSize, setWhSize] = useState(20);
  const debouncedSearch = useDebounced(whSearch, 300);

  const whRange = useMemo(() => periodRange(whPeriod, whAnchor, today), [whPeriod, whAnchor, today]);
  const locations = useLocations(can("attendance", "view") || can("employees", "view"));
  const teams = useTeams(can("attendance", "view") || can("employees", "view"));

  const canPeople = can("employees", "view");
  const canAtt = can("attendance", "view");
  const canLeave = can("leave", "view");

  // Reset page when filters change
  const filterSig = `${whRange.from}|${whRange.to}|${whLocationId}|${whTeamId}|${whSort}|${debouncedSearch}|${whSize}`;
  const [lastFilterSig, setLastFilterSig] = useState(filterSig);
  if (lastFilterSig !== filterSig) {
    setLastFilterSig(filterSig);
    if (whPage !== 1) setWhPage(1);
  }

  const adminDash = useQuery({
    queryKey: ["dash-admin", today],
    queryFn: ({ signal }) =>
      apiFetch(api.hrms("dashboard/admin"), { query: { date: today }, schema: adminDashSchema, signal }),
    enabled: canPeople,
    staleTime: STALE,
  });

  const attToday = useQuery({
    queryKey: ["dash-attendance-today", today],
    queryFn: ({ signal }) =>
      apiFetch(api.hrms("dashboard/attendance-today"), {
        query: { date: today },
        schema: attendanceTodaySchema,
        signal,
      }),
    enabled: canAtt,
    staleTime: STALE,
  });

  const pendingStats = useQuery({
    queryKey: ["dash-pending-stats"],
    queryFn: ({ signal }) =>
      apiFetch(api.hrms("approvals/dashboard-stats"), { schema: pendingStatsSchema, signal }),
    enabled: canLeave,
    staleTime: STALE,
  });

  const workHours = useQuery({
    queryKey: [
      "dash-work-hours",
      whRange.from,
      whRange.to,
      whLocationId,
      whTeamId,
      whSort,
      debouncedSearch,
      whPage,
      whSize,
    ],
    queryFn: ({ signal }) =>
      apiFetch(api.hrms("dashboard/work-hours"), {
        query: {
          from: whRange.from,
          to: whRange.to,
          locationId: whLocationId || undefined,
          teamId: whTeamId || undefined,
          sort: whSort,
          q: debouncedSearch || undefined,
          page: whPage,
          size: whSize,
        },
        schema: workHoursSchema,
        signal,
      }),
    enabled: canAtt,
    staleTime: 20_000,
    placeholderData: keepPreviousData,
  });

  const byLocation = useMemo(
    () =>
      (adminDash.data?.headcountByLocation ?? []).map((r) => ({
        label: r.label,
        value: n(r.count),
      })),
    [adminDash.data],
  );
  const byType = useMemo(() => {
    const rows = adminDash.data?.headcountByType ?? [];
    return rows.map((r, i) => ({
      label: humanize(String(r.label)),
      value: n(r.count),
      color: TYPE_COLORS[i % TYPE_COLORS.length],
    }));
  }, [adminDash.data]);

  const attSlices = useMemo(() => {
    const d = attToday.data;
    if (!d) return [] as { label: string; value: number; color: string }[];
    return [
      { label: "Present", value: n(d.byStatus?.present), color: ATT_COLORS.present },
      { label: "Late", value: n(d.late), color: ATT_COLORS.late },
      { label: "Half day", value: n(d.halfDay), color: ATT_COLORS.halfDay },
      { label: "On leave", value: n(d.onLeave), color: ATT_COLORS.onLeave },
      { label: "Absent", value: n(d.absent), color: ATT_COLORS.absent },
      { label: "Missing out", value: n(d.missingCheckout), color: ATT_COLORS.missingCheckout },
    ].filter((s) => s.value > 0);
  }, [attToday.data]);

  const ending = adminDash.data?.endingIn30Days ?? [];
  const totals = workHours.data?.totals ?? {};
  const rows = workHours.data?.rows ?? [];
  const totalElements = n(workHours.data?.totalElements);
  const totalPages = Math.max(1, n(workHours.data?.totalPages) || 1);
  const pageFrom = totalElements === 0 ? 0 : (whPage - 1) * whSize + 1;
  const pageTo = Math.min(whPage * whSize, totalElements);

  const [exporting, setExporting] = useState(false);
  const exportCsv = async () => {
    setExporting(true);
    try {
      const all = await apiFetch(api.hrms("dashboard/work-hours"), {
        query: {
          from: whRange.from,
          to: whRange.to,
          locationId: whLocationId || undefined,
          teamId: whTeamId || undefined,
          sort: whSort,
          q: debouncedSearch || undefined,
          page: 1,
          size: Math.min(500, Math.max(totalElements || whSize, whSize)),
        },
        schema: workHoursSchema,
      });
      const header = [
        "Employee",
        "Code",
        "Pattern",
        "Expected",
        "Worked",
        "Logged",
        "Gap",
        "Days present",
        "Leave days",
        "Absences",
        "Late",
        "Missing logs",
        "Missing check-outs",
      ];
      const lines = [header.join(",")];
      for (const r of all.rows ?? []) {
        lines.push(
          [
            r.employeeName,
            r.employeeCode,
            r.workPattern,
            r.expectedHours,
            r.workedHours,
            r.loggedHours,
            r.gapHours,
            r.daysPresent,
            r.leaveDays,
            r.absences,
            r.lateArrivals,
            r.missingWorkLogDays,
            r.missingCheckouts,
          ]
            .map(csvEscape)
            .join(","),
        );
      }
      saveBlob({
        blob: new Blob([lines.join("\n")], { type: "text/csv;charset=utf-8" }),
        filename: `work-hours_${whRange.from}_${whRange.to}.csv`,
      });
    } finally {
      setExporting(false);
    }
  };

  const dash = "—";
  const val = (ok: boolean, v: unknown, loading: boolean) =>
    loading ? dash : ok ? fmtNumber(n(v), 0) : dash;

  const cardsLoading = (canPeople && adminDash.isLoading) || (canAtt && attToday.isLoading) || (canLeave && pendingStats.isLoading);

  return (
    <div className="stack" style={{ gap: 16 }}>
      <PageHeader
        title="Admin dashboard"
        subtitle={`${fmtDate(today)} · scoped to your team. Cards refresh independently of the work-hours table.`}
      />

      <ErrorNote error={adminDash.error || attToday.error || pendingStats.error || workHours.error} />

      <div className="grid g4">
        <StatCard
          label="Headcount"
          value={val(canPeople, adminDash.data?.headcountTotal, adminDash.isLoading)}
          sub={
            canPeople
              ? `${fmtNumber(n(adminDash.data?.headcountActive), 0)} active`
              : "Needs employees access"
          }
          tone="acc"
        />
        <StatCard
          label="Checked in / expected"
          value={
            canAtt && !attToday.isLoading
              ? `${fmtNumber(n(attToday.data?.checkedIn), 0)} / ${fmtNumber(n(attToday.data?.expectedWorking), 0)}`
              : dash
          }
          sub={canAtt ? `${fmtNumber(n(attToday.data?.late), 0)} late today` : "Needs attendance access"}
          tone="good"
        />
        <StatCard
          label="On leave today"
          value={val(canAtt, attToday.data?.onLeave, attToday.isLoading)}
          sub={canAtt ? `${fmtNumber(n(attToday.data?.absent), 0)} absent` : undefined}
          tone={n(attToday.data?.onLeave) ? "info" : ""}
        />
        <StatCard
          label="Pending approvals"
          value={val(canLeave, pendingStats.data?.pending, pendingStats.isLoading)}
          sub={
            canLeave
              ? `${fmtNumber(n(pendingStats.data?.waitingTwoPlusWorkingDays), 0)} waited 2+ working days`
              : "Needs leave access"
          }
          tone={n(pendingStats.data?.pending) ? "warn" : ""}
          href={
            can("leave", "approve") ? (
              <Link href="/approvals" className="linkbtn">
                Open queue →
              </Link>
            ) : undefined
          }
        />
      </div>

      <div className="grid g4">
        <StatCard label="Late today" value={val(canAtt, attToday.data?.late, attToday.isLoading)} tone={n(attToday.data?.late) ? "warn" : "good"} />
        <StatCard
          label="Contracts ≤30d"
          value={val(canPeople, (adminDash.data?.endingBreakdown as { contract?: unknown } | undefined)?.contract, adminDash.isLoading)}
        />
        <StatCard
          label="Probation ≤30d"
          value={val(canPeople, (adminDash.data?.endingBreakdown as { probation?: unknown } | undefined)?.probation, adminDash.isLoading)}
        />
        <StatCard
          label="Internships ≤30d"
          value={val(canPeople, (adminDash.data?.endingBreakdown as { internship?: unknown } | undefined)?.internship, adminDash.isLoading)}
        />
      </div>

      <div className="grid g3">
        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <h2>By location</h2>
            <StatusPill tone="info">{fmtNumber(n(adminDash.data?.headcountTotal), 0)}</StatusPill>
          </div>
          {!canPeople ? (
            <p className="muted small">Needs employees access</p>
          ) : adminDash.isLoading ? (
            <p className="muted">Loading…</p>
          ) : (
            <HBarList items={byLocation.slice(0, 8)} />
          )}
        </section>

        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <h2>By employment type</h2>
          </div>
          {!canPeople ? (
            <p className="muted small">Needs employees access</p>
          ) : adminDash.isLoading ? (
            <p className="muted">Loading…</p>
          ) : byType.length === 0 ? (
            <p className="muted">No people in scope.</p>
          ) : (
            <div className="row" style={{ alignItems: "center", gap: 16 }}>
              <DonutChart
                size={128}
                thickness={18}
                slices={byType}
                center={
                  <div>
                    <div className="mono" style={{ fontSize: 18, fontWeight: 600 }}>
                      {fmtNumber(n(adminDash.data?.headcountTotal), 0)}
                    </div>
                    <div className="muted small">total</div>
                  </div>
                }
              />
              <div className="stack" style={{ gap: 5, flex: 1, minWidth: 0 }}>
                {byType.map((s) => (
                  <div key={s.label} className="row between small">
                    <span className="row" style={{ gap: 6, minWidth: 0 }}>
                      <span style={{ width: 8, height: 8, borderRadius: 2, background: s.color, flexShrink: 0 }} />
                      <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{s.label}</span>
                    </span>
                    <span className="mono">{s.value}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </section>

        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <h2>Attendance today</h2>
            <Link href="/team/attendance" className="linkbtn">
              Open →
            </Link>
          </div>
          {!canAtt ? (
            <p className="muted small">Needs attendance access</p>
          ) : attToday.isLoading ? (
            <p className="muted">Loading…</p>
          ) : attSlices.length === 0 ? (
            <p className="muted">No attendance in scope today.</p>
          ) : (
            <div className="row" style={{ alignItems: "center", gap: 16 }}>
              <DonutChart
                size={128}
                thickness={18}
                slices={attSlices}
                center={
                  <div>
                    <div className="mono" style={{ fontSize: 18, fontWeight: 600 }}>
                      {fmtNumber(n(attToday.data?.checkedIn), 0)}
                    </div>
                    <div className="muted small">in</div>
                  </div>
                }
              />
              <div className="stack" style={{ gap: 5, flex: 1, minWidth: 0 }}>
                {attSlices.map((s) => (
                  <div key={s.label} className="row between small">
                    <span className="row" style={{ gap: 6 }}>
                      <span style={{ width: 8, height: 8, borderRadius: 2, background: s.color }} />
                      {s.label}
                    </span>
                    <span className="mono">{s.value}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </section>
      </div>

      <section className="card stack">
        <div className="card-head" style={{ marginBottom: 0 }}>
          <h2>Ending in 30 days</h2>
          <StatusPill tone={ending.length ? "warn" : "good"}>{ending.length}</StatusPill>
        </div>
        {!canPeople ? (
          <p className="muted small">Needs employees access</p>
        ) : ending.length === 0 ? (
          <p className="muted">{adminDash.isLoading || cardsLoading ? "Loading…" : "Nothing ending soon in your scope."}</p>
        ) : (
          <div className="tbl-wrap">
            <table>
              <thead>
                <tr>
                  <th>Person</th>
                  <th>Kind</th>
                  <th>Ends</th>
                  <th className="n">Days left</th>
                </tr>
              </thead>
              <tbody>
                {ending.slice(0, 6).map((e, i) => (
                  <tr key={`${e.employeeId}-${e.kind}-${i}`}>
                    <td>
                      <Link href={`/employees/${e.employeeId}`} className="linkbtn">
                        {String(e.employeeName ?? `#${e.employeeId}`)}
                      </Link>
                    </td>
                    <td>
                      <StatusPill tone="warn">{String(e.label ?? e.kind)}</StatusPill>
                    </td>
                    <td className="mono small">{fmtDate(e.endDate)}</td>
                    <td className="n mono small">{fmtNumber(n(e.daysLeft), 0)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {canAtt ? (
        <section className="card stack" style={{ gap: 14 }}>
          <div className="card-head" style={{ marginBottom: 0 }}>
            <div>
              <h2 style={{ margin: 0 }}>Work hours</h2>
              <p className="muted small" style={{ margin: "4px 0 0" }}>
                {whPeriod === "day"
                  ? "Today"
                  : whPeriod === "week"
                    ? "Last 7 days"
                    : "Last 30 days"}
                {" · "}
                {fmtDate(whRange.from)}
                {whRange.from !== whRange.to ? ` – ${fmtDate(whRange.to)}` : ""}
                {workHours.isFetching ? " · updating…" : ""}
              </p>
            </div>
            <div className="row" style={{ gap: 6 }}>
              {(["day", "week", "month"] as Period[]).map((p) => (
                <button
                  key={p}
                  type="button"
                  className={`btn sm${whPeriod === p ? " primary" : ""}`}
                  onClick={() => {
                    setWhPeriod(p);
                    setWhAnchor(today);
                    setWhPage(1);
                  }}
                >
                  {p.charAt(0).toUpperCase() + p.slice(1)}
                </button>
              ))}
              <button
                type="button"
                className="btn sm"
                onClick={() => setWhAnchor(shiftAnchor(whPeriod, whAnchor, -1, today))}
                aria-label="Previous"
              >
                ←
              </button>
              <button type="button" className="btn sm" onClick={() => setWhAnchor(today)} disabled={whAnchor === today}>
                Today
              </button>
              <button
                type="button"
                className="btn sm"
                onClick={() => setWhAnchor(shiftAnchor(whPeriod, whAnchor, 1, today))}
                aria-label="Next"
                disabled={whAnchor >= today}
              >
                →
              </button>
              <button type="button" className="btn sm" onClick={() => void exportCsv()} disabled={exporting || totalElements === 0}>
                {exporting ? "Exporting…" : "Export CSV"}
              </button>
            </div>
          </div>

          <div className="row" style={{ flexWrap: "wrap", gap: 8 }}>
            <input
              type="search"
              placeholder="Search employee name or code…"
              value={whSearch}
              onChange={(e) => setWhSearch(e.target.value)}
              aria-label="Search employees"
              style={{ flex: "1 1 220px", minWidth: 180, maxWidth: 320 }}
            />
            <select aria-label="Location" value={whLocationId} onChange={(e) => setWhLocationId(e.target.value)} style={{ width: 150 }}>
              <option value="">All locations</option>
              {(locations.data ?? []).map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </select>
            <select aria-label="Team" value={whTeamId} onChange={(e) => setWhTeamId(e.target.value)} style={{ width: 150 }}>
              <option value="">All teams</option>
              {(teams.data ?? []).map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
            <select aria-label="Sort" value={whSort} onChange={(e) => setWhSort(e.target.value as SortKey)} style={{ width: 170 }}>
              <option value="name">Sort: name</option>
              <option value="worked">Sort: hours worked</option>
              <option value="gap">Sort: gap</option>
              <option value="absences">Sort: absences</option>
              <option value="leave">Sort: leave</option>
              <option value="missing">Sort: missing logs</option>
            </select>
            <select aria-label="Page size" value={whSize} onChange={(e) => setWhSize(Number(e.target.value))} style={{ width: 110 }}>
              {[10, 20, 50].map((s) => (
                <option key={s} value={s}>
                  {s} / page
                </option>
              ))}
            </select>
          </div>

          <div className="grid g4">
            <StatCard
              label="Worked vs expected"
              value={`${fmtHours(totals.workedHours)} / ${fmtHours(totals.expectedHours)}`}
              sub={`Gap ${fmtHours(totals.gapHours)} · filtered set`}
              tone={n(totals.gapHours) < 0 ? "warn" : "good"}
            />
            <StatCard label="Logged share" value={`${fmtNumber(n(totals.loggedSharePercent))}%`} sub={`${fmtHours(totals.loggedHours)} logged`} />
            <StatCard
              label="Leave / absences"
              value={`${fmtNumber(n(totals.leaveDays), 0)} / ${fmtNumber(n(totals.absences), 0)}`}
              sub={`${fmtNumber(n(totals.lateArrivals), 0)} late`}
            />
            <StatCard
              label="Missing logs / outs"
              value={`${fmtNumber(n(totals.missingWorkLogDays), 0)} / ${fmtNumber(n(totals.missingCheckouts), 0)}`}
              tone={n(totals.missingWorkLogDays) || n(totals.missingCheckouts) ? "warn" : ""}
            />
          </div>

          {workHours.isLoading && !workHours.data ? (
            <p className="muted">Loading work hours…</p>
          ) : rows.length === 0 ? (
            <p className="muted">
              {debouncedSearch
                ? `No employees match “${debouncedSearch}” for this period.`
                : "No work-hours data for this period in your scope."}
            </p>
          ) : (
            <>
              <div className="tbl-wrap" style={{ opacity: workHours.isFetching ? 0.72 : 1, transition: "opacity .15s" }}>
                <table>
                  <thead>
                    <tr>
                      <th>Employee</th>
                      <th>Pattern</th>
                      <th className="n">Expected</th>
                      <th>Worked</th>
                      <th className="n">Logged</th>
                      <th className="n">Gap</th>
                      <th className="n">Present</th>
                      <th className="n">Leave</th>
                      <th className="n">Absent</th>
                      <th className="n">Late</th>
                      <th className="n">No log</th>
                      <th className="n">Missing checkout</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((r, i) => {
                      const pct = n(r.workedPercent);
                      const tone = pct >= 95 ? "good" : pct >= 80 ? "warn" : "bad";
                      const detailHref =
                        r.employeeId != null && r.employeeId !== ""
                          ? `/employees/${r.employeeId}/attendance?from=${whRange.from}&to=${whRange.to}`
                          : undefined;
                      return (
                        <tr
                          key={String(r.employeeId ?? i)}
                          style={detailHref ? { cursor: "pointer" } : undefined}
                          onClick={detailHref ? () => router.push(detailHref) : undefined}
                        >
                          <td>
                            {detailHref ? (
                              <Link href={detailHref} className="linkbtn" onClick={(e) => e.stopPropagation()}>
                                <b>{r.employeeName ?? `#${r.employeeId}`}</b>
                              </Link>
                            ) : (
                              <b>{r.employeeName ?? `#${r.employeeId}`}</b>
                            )}
                            {r.employeeCode ? <div className="muted small mono">{r.employeeCode}</div> : null}
                          </td>
                          <td className="small">{humanize(String(r.workPattern ?? "—"))}</td>
                          <td className="n mono small">{fmtHours(r.expectedHours)}</td>
                          <td>
                            <div className="row" style={{ gap: 8, flexWrap: "nowrap" }}>
                              <span className="mono small">{fmtHours(r.workedHours)}</span>
                              <ProgressBar pct={pct} tone={tone} />
                              <span className="muted small mono">{fmtNumber(pct)}%</span>
                            </div>
                          </td>
                          <td className="n mono small">{fmtHours(r.loggedHours)}</td>
                          <td className="n mono small" style={{ color: n(r.gapHours) < 0 ? "var(--warn)" : undefined }}>
                            {fmtHours(r.gapHours)}
                          </td>
                          <td className="n mono small">{fmtNumber(n(r.daysPresent), 0)}</td>
                          <td className="n mono small">{fmtNumber(n(r.leaveDays), 0)}</td>
                          <td className="n mono small">{fmtNumber(n(r.absences), 0)}</td>
                          <td className="n mono small">{fmtNumber(n(r.lateArrivals), 0)}</td>
                          <td className="n mono small">{fmtNumber(n(r.missingWorkLogDays), 0)}</td>
                          <td
                            className="n mono small"
                            style={{ color: n(r.missingCheckouts) > 0 ? "var(--warn)" : undefined }}
                          >
                            {fmtNumber(n(r.missingCheckouts), 0)}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              <div className="row between" style={{ flexWrap: "wrap", gap: 8 }}>
                <span className="muted small">
                  Showing {pageFrom}–{pageTo} of {fmtNumber(totalElements, 0)}
                  {debouncedSearch ? ` · “${debouncedSearch}”` : ""}
                </span>
                <div className="row" style={{ gap: 6 }}>
                  <button type="button" className="btn sm" disabled={whPage <= 1} onClick={() => setWhPage((p) => Math.max(1, p - 1))}>
                    Previous
                  </button>
                  <span className="mono small" style={{ alignSelf: "center", minWidth: 72, textAlign: "center" }}>
                    {whPage} / {totalPages}
                  </span>
                  <button
                    type="button"
                    className="btn sm"
                    disabled={whPage >= totalPages}
                    onClick={() => setWhPage((p) => p + 1)}
                  >
                    Next
                  </button>
                </div>
              </div>
            </>
          )}
        </section>
      ) : null}

      <p className="muted small">Signed in as {me.name}. Totals follow your filters and search, not only the current page.</p>
    </div>
  );
}
