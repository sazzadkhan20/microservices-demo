"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/components/auth/AuthProvider";
import { EmptyState } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { StatusPill } from "@/components/ui/StatusPill";
import { api, apiFetch, apiFetchPage } from "@/lib/api/client";
import { useDirectory, useHolidaysForYear } from "@/lib/api/directory";
import { useLocations, useTeams } from "@/lib/api/lookups";
import { MONTH_NAMES, datesBetween, dowOf, monthCells, ymd, DOW_LABEL, DOW } from "@/lib/admin";
import { fmtNumber, fmtRange, isoDate, todayIso } from "@/lib/format";
import { leaveAppRowSchema, leavePolicySchema, type LeaveAppRow } from "@/lib/schemas/admin";

const LIVE = new Set(["APPROVED", "PENDING_APPROVAL", "PENDING", "PENDING_RELIEF"]);
const firstName = (n: string) => n.split(" ")[0];

export default function LeaveCalendarPage() {
  const { can } = useAuth();
  const today = todayIso();
  const [year, setYear] = useState(Number(today.slice(0, 4)));
  const [month, setMonth] = useState(Number(today.slice(5, 7)));
  const [team, setTeam] = useState("");
  const [location, setLocation] = useState("");
  const [includePending, setIncludePending] = useState(true);

  const dir = useDirectory(can("employees", "view"));
  const teams = useTeams();
  const locations = useLocations();
  const holidays = useHolidaysForYear(year);
  const policy = useQuery({
    queryKey: ["leave-policy"],
    queryFn: ({ signal }) => apiFetch(api.hrms("leave-policy"), { schema: leavePolicySchema, signal }),
    staleTime: 5 * 60_000,
  });
  const apps = useQuery({
    queryKey: ["leave-calendar-apps"],
    queryFn: ({ signal }) => apiFetchPage(api.hrms("leave-applications"), leaveAppRowSchema, { page: 1, size: 500 }, { signal }),
  });

  const from = ymd(year, month, 1);
  const to = ymd(year, month, new Date(year, month, 0).getDate());
  const workingDays = policy.data?.workingDays;

  const visible = useMemo(() => {
    return (apps.data?.content ?? [])
      .filter((a) => {
        const st = String(a.status ?? "").toUpperCase();
        return includePending ? LIVE.has(st) : st === "APPROVED";
      })
      .filter((a) => {
        const s = isoDate(a.startDate);
        const e = isoDate(a.endDate) || s;
        return s <= to && e >= from;
      })
      .filter((a) => {
        if (!team) return true;
        return String(dir.byId.get(String(a.employeeId))?.teamId ?? "") === team;
      });
  }, [apps.data, includePending, from, to, team, dir.byId]);

  const byDay = useMemo(() => {
    const m = new Map<string, LeaveAppRow[]>();
    for (const a of visible) {
      for (const d of datesBetween(a.startDate, a.endDate, from, to)) {
        const arr = m.get(d) ?? [];
        arr.push(a);
        m.set(d, arr);
      }
    }
    return m;
  }, [visible, from, to]);

  const holByDay = useMemo(() => {
    const m = new Map<string, string[]>();
    for (const h of holidays.data?.content ?? []) {
      if (location && String(h.locationId) !== location) continue;
      for (const d of datesBetween(h.startDate, h.endDate, from, to)) {
        m.set(d, [...(m.get(d) ?? []), h.name]);
      }
    }
    return m;
  }, [holidays.data, location, from, to]);

  function shift(by: number) {
    const d = new Date(year, month - 1 + by, 1);
    setYear(d.getFullYear());
    setMonth(d.getMonth() + 1);
  }

  const cells = monthCells(year, month);
  const nameFor = (a: LeaveAppRow) => dir.nameOf(a.employeeId);
  const sorted = [...visible].sort((a, b) => (isoDate(a.startDate) < isoDate(b.startDate) ? -1 : 1));

  return (
    <div className="stack">
      <PageHeader
        title="Leave calendar"
        subtitle="Who is away and which days are holidays. Days outside the working week are shaded."
        actions={
          <>
            <button type="button" className="btn" aria-label="Previous month" onClick={() => shift(-1)}>
              ←
            </button>
            <b style={{ minWidth: 140, textAlign: "center" }}>
              {MONTH_NAMES[month - 1]} {year}
            </b>
            <button type="button" className="btn" aria-label="Next month" onClick={() => shift(1)}>
              →
            </button>
            <button
              type="button"
              className="btn"
              onClick={() => {
                setYear(Number(today.slice(0, 4)));
                setMonth(Number(today.slice(5, 7)));
              }}
            >
              Today
            </button>
          </>
        }
      />

      <div className="filters">
        <div className="field">
          <span>Team</span>
          <select value={team} onChange={(e) => setTeam(e.target.value)} disabled={!dir.employees.length}>
            <option value="">All teams</option>
            {(teams.data ?? []).map((t) => (
              <option key={String(t.id)} value={String(t.id)}>
                {t.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <span>Holidays for</span>
          <select value={location} onChange={(e) => setLocation(e.target.value)}>
            <option value="">All locations</option>
            {(locations.data ?? []).map((l) => (
              <option key={String(l.id)} value={String(l.id)}>
                {l.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <span>&nbsp;</span>
          <div className="checks">
            <label>
              <input type="checkbox" checked={includePending} onChange={(e) => setIncludePending(e.target.checked)} /> Include pending requests
            </label>
          </div>
        </div>
      </div>

      <ErrorNote error={apps.error} />

      {!apps.isLoading && !apps.error && (apps.data?.content ?? []).length === 0 ? (
        <EmptyState title="No leave recorded yet">Approved and pending leave requests will show here once people apply.</EmptyState>
      ) : null}

      <div className="card">
        <div className="cal" style={{ marginBottom: 4 }}>
          {DOW.map((d) => (
            <div className="h" key={d}>
              {DOW_LABEL[d]}
            </div>
          ))}
        </div>
        <div className="cal">
          {cells.map((iso, i) => {
            if (!iso) return <div key={i} className="d off" style={{ opacity: 0.4 }} />;
            const leave = byDay.get(iso) ?? [];
            const hols = holByDay.get(iso) ?? [];
            const off = workingDays ? !workingDays.includes(dowOf(iso)) : false;
            return (
              <div key={iso} className={`d${off ? " off" : ""}${iso === today ? " today" : ""}`}>
                <span className="dn">{Number(iso.slice(8))}</span>
                {hols.map((h) => (
                  <span className="tag hol" key={h} title={h}>
                    {h}
                  </span>
                ))}
                {leave.slice(0, 3).map((a) => {
                  const pending = String(a.status).toUpperCase() !== "APPROVED";
                  return (
                    <span
                      className="tag"
                      key={String(a.id)}
                      title={`${nameFor(a)} · ${a.leaveTypeCode} · ${a.status}`}
                      style={pending ? { background: "var(--info-soft)", color: "var(--info)" } : undefined}
                    >
                      {firstName(nameFor(a))}
                    </span>
                  );
                })}
                {leave.length > 3 ? <span className="small muted">+{leave.length - 3} more</span> : null}
              </div>
            );
          })}
        </div>
        <div className="row small muted" style={{ marginTop: 12 }}>
          <span className="tag" style={{ background: "var(--accent-soft)", color: "var(--accent)", padding: "0 6px", borderRadius: 4 }}>
            Approved
          </span>
          <span className="tag" style={{ background: "var(--info-soft)", color: "var(--info)", padding: "0 6px", borderRadius: 4 }}>
            Pending
          </span>
          <span className="tag" style={{ background: "var(--warn-soft)", color: "var(--warn)", padding: "0 6px", borderRadius: 4 }}>
            Holiday
          </span>
        </div>
      </div>

      <section className="card stack">
        <h2 style={{ marginBottom: 0 }}>
          Leave in {MONTH_NAMES[month - 1]} <span className="muted small">({sorted.length})</span>
        </h2>
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th>Employee</th>
                <th>Category</th>
                <th>Dates</th>
                <th className="n">Days</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {sorted.length === 0 ? (
                <tr>
                  <td colSpan={5} className="empty">
                    {apps.isLoading ? "Loading…" : "Nobody is on leave this month"}
                  </td>
                </tr>
              ) : (
                sorted.map((a) => (
                  <tr key={String(a.id)}>
                    <td>{nameFor(a)}</td>
                    <td>{a.leaveTypeCode}</td>
                    <td className="mono">{fmtRange(a.startDate, a.endDate)}</td>
                    <td className="n">{fmtNumber(a.workingDaysCounted)}</td>
                    <td>
                      <StatusPill status={a.status} />
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}