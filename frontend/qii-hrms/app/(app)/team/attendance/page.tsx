"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { EmptyState } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { AttCode } from "@/components/ui/StatusPill";
import { StatCard } from "@/components/ui/StatCard";
import { api, apiFetch } from "@/lib/api/client";
import { useDepartments } from "@/lib/api/lookups";
import { addDaysIso } from "@/lib/admin";
import { fmtDate, fmtNumber, fmtTime, todayIso } from "@/lib/format";
import { teamAttendanceListSchema, type TeamAttendanceRow } from "@/lib/schemas/admin";

const GROUPS: { id: string; label: string; codes: string[] }[] = [
  { id: "P", label: "Present", codes: ["P"] },
  { id: "L", label: "Late", codes: ["L"] },
  { id: "HD", label: "Half day", codes: ["HD"] },
  { id: "A", label: "Absent", codes: ["A", "NC"] },
];

const codeOf = (r: TeamAttendanceRow) => String(r.statusCode ?? "").toUpperCase();

export default function TeamAttendancePage() {
  const [date, setDate] = useState(todayIso());
  const [status, setStatus] = useState("");
  const [q, setQ] = useState("");
  const depts = useDepartments();

  const team = useQuery({
    queryKey: ["team-attendance", date],
    queryFn: ({ signal }) =>
      apiFetch(api.hrms("attendance/team"), { query: { date }, schema: teamAttendanceListSchema, signal }),
  });
  const rows = useMemo(() => team.data ?? [], [team.data]);
  const deptName = useMemo(() => new Map((depts.data ?? []).map((d) => [String(d.id), d.name])), [depts.data]);

  const counts = useMemo(() => {
    const c: Record<string, number> = {};
    for (const g of GROUPS) c[g.id] = rows.filter((r) => g.codes.includes(codeOf(r))).length;
    c.other = rows.length - GROUPS.reduce((n, g) => n + c[g.id], 0);
    return c;
  }, [rows]);

  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const group = GROUPS.find((g) => g.id === status);
    return rows
      .filter((r) => !group || group.codes.includes(codeOf(r)))
      .filter(
        (r) =>
          !needle ||
          `${r.employeeName ?? r.name ?? ""} ${r.employeeCode ?? ""}`.toLowerCase().includes(needle),
      )
      .sort((a, b) => String(a.employeeName ?? a.name ?? "").localeCompare(String(b.employeeName ?? b.name ?? "")));
  }, [rows, q, status]);

  const isToday = date === todayIso();

  return (
    <div className="stack">
      <PageHeader
        title="Team attendance"
        subtitle="Who is in, late or absent on a day, for the people in your scope."
        actions={
          <>
            <button type="button" className="btn" aria-label="Previous day" onClick={() => setDate(addDaysIso(date, -1))}>
              ←
            </button>
            <input type="date" aria-label="Date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} style={{ width: 160 }} />
            <button type="button" className="btn" aria-label="Next day" onClick={() => setDate(addDaysIso(date, 1))}>
              →
            </button>
            <button type="button" className="btn" disabled={isToday} onClick={() => setDate(todayIso())}>
              Today
            </button>
            <button type="button" className="btn" onClick={() => void team.refetch()}>
              {team.isFetching ? "Refreshing…" : "Refresh"}
            </button>
          </>
        }
      />

      <div className="grid g4">
        <StatCard label="Present" value={team.data ? counts.P : "—"} tone="good" sub={fmtDate(date)} />
        <StatCard label="Late" value={team.data ? counts.L : "—"} tone="warn" />
        <StatCard label="Half day" value={team.data ? counts.HD : "—"} tone="info" />
        <StatCard label="Absent" value={team.data ? counts.A : "—"} tone="bad" sub={counts.other ? `+${counts.other} other` : undefined} />
      </div>

      <ErrorNote error={team.error} />

      <div className="filters">
        <div className="field wide">
          <span>Search</span>
          <input type="search" placeholder="Name or employee code" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <div className="field">
          <span>Status</span>
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All ({rows.length})</option>
            {GROUPS.map((g) => (
              <option key={g.id} value={g.id}>
                {g.label} ({counts[g.id]})
              </option>
            ))}
          </select>
        </div>
      </div>

      {!team.isLoading && !team.error && rows.length === 0 ? (
        <EmptyState title="No one in your scope">
          The attendance API returned no active people for this date. Managers see their direct reports and team; HR sees everyone.
        </EmptyState>
      ) : (
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th>Employee</th>
                <th>Department</th>
                <th>Status</th>
                <th>Check in</th>
                <th>Check out</th>
                <th className="n">Hours</th>
                <th>Location</th>
              </tr>
            </thead>
            <tbody>
              {shown.length === 0 ? (
                <tr>
                  <td colSpan={7} className="empty">
                    {team.isLoading ? "Loading…" : "No one matches these filters"}
                  </td>
                </tr>
              ) : (
                shown.map((r, i) => (
                  <tr key={String(r.employeeId ?? i)}>
                    <td>
                      <b>{r.employeeName ?? r.name ?? `#${r.employeeId}`}</b>
                      <br />
                      <span className="small muted mono">{r.employeeCode}</span>
                    </td>
                    <td>{r.departmentId != null ? (deptName.get(String(r.departmentId)) ?? "—") : "—"}</td>
                    <td>
                      <AttCode code={r.statusCode} label={r.status} />
                    </td>
                    <td className="mono">{fmtTime(r.checkIn)}</td>
                    <td className="mono">{r.checkOut ? fmtTime(r.checkOut) : r.checkIn ? <span className="muted">Still in</span> : "—"}</td>
                    <td className="n">{r.totalHours != null && r.checkIn ? fmtNumber(r.totalHours) : "—"}</td>
                    <td className="small muted">{r.locationLabel ?? "—"}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
