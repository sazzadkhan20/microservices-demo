"use client";

import { useMemo } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { PageHeader } from "@/components/ui/PageHeader";
import { StatCard } from "@/components/ui/StatCard";
import { StatusPill } from "@/components/ui/StatusPill";
import { api, apiFetch, apiFetchPage } from "@/lib/api/client";
import { useDirectory, useHolidaysForYear } from "@/lib/api/directory";
import { datesBetween, oneLine } from "@/lib/admin";
import { fmtDate, fmtDateTime, fmtRange, humanize, isoDate, todayIso } from "@/lib/format";
import {
  auditRowSchema,
  correctionRowSchema,
  leaveAppRowSchema,
  teamAttendanceListSchema,
} from "@/lib/schemas/admin";

const dash = "—";

export default function AdminDashboardPage() {
  const { can, me } = useAuth();
  const today = todayIso();
  const year = Number(today.slice(0, 4));
  const dir = useDirectory(can("employees", "view"));

  const attendance = useQuery({
    queryKey: ["dash-attendance", today],
    queryFn: ({ signal }) =>
      apiFetch(api.hrms("attendance/team"), { query: { date: today }, schema: teamAttendanceListSchema, signal }),
    enabled: can("attendance", "view"),
  });
  const pendingLeave = useQuery({
    queryKey: ["dash-pending-leave"],
    queryFn: ({ signal }) =>
      apiFetchPage(api.hrms("leave-applications"), leaveAppRowSchema, { page: 1, size: 1, filters: { status: "PENDING_APPROVAL" } }, { signal }),
    enabled: can("leave", "view"),
  });
  const leaveApps = useQuery({
    queryKey: ["leave-calendar-apps"],
    queryFn: ({ signal }) => apiFetchPage(api.hrms("leave-applications"), leaveAppRowSchema, { page: 1, size: 500 }, { signal }),
    enabled: can("leave", "view"),
  });
  const corrections = useQuery({
    queryKey: ["dash-corrections"],
    queryFn: ({ signal }) =>
      apiFetch(api.hrms("attendance/corrections"), { query: { status: "PENDING" }, schema: z.array(correctionRowSchema), signal }),
    enabled: can("attendance", "correct"),
  });
  const holidays = useHolidaysForYear(year);
  const nextHolidays = useHolidaysForYear(year + 1);
  const audit = useQuery({
    queryKey: ["dash-audit"],
    queryFn: ({ signal }) => apiFetchPage(api.hrms("audit"), auditRowSchema, { page: 1, size: 6 }, { signal }),
    enabled: can("settings", "manage"),
  });

  const att = useMemo(() => {
    const rows = attendance.data ?? [];
    const by = (codes: string[]) => rows.filter((r) => codes.includes(String(r.statusCode ?? "").toUpperCase())).length;
    return { total: rows.length, present: by(["P"]), late: by(["L"]), half: by(["HD"]), absent: by(["A", "NC"]) };
  }, [attendance.data]);

  const headcount = useMemo(() => {
    const emps = dir.employees;
    const byDept = new Map<string, number>();
    const byStatus = new Map<string, number>();
    for (const e of emps) {
      const d = e.departmentName || "No department";
      byDept.set(d, (byDept.get(d) ?? 0) + 1);
      const s = e.status || "Unknown";
      byStatus.set(s, (byStatus.get(s) ?? 0) + 1);
    }
    return {
      total: emps.length,
      noRole: emps.filter((e) => (e.roles ?? []).length === 0 && e.status !== "Terminated").length,
      depts: [...byDept.entries()].sort((a, b) => b[1] - a[1]),
      statuses: [...byStatus.entries()].sort((a, b) => b[1] - a[1]),
    };
  }, [dir.employees]);

  const upcomingHolidays = useMemo(() => {
    const all = [...(holidays.data?.content ?? []), ...(nextHolidays.data?.content ?? [])];
    const seen = new Set<string>();
    return all
      .filter((h) => (isoDate(h.endDate) || isoDate(h.startDate)) >= today)
      .sort((a, b) => (isoDate(a.startDate) < isoDate(b.startDate) ? -1 : 1))
      .filter((h) => {
        const k = `${h.name}|${isoDate(h.startDate)}`;
        if (seen.has(k)) return false;
        seen.add(k);
        return true;
      })
      .slice(0, 5);
  }, [holidays.data, nextHolidays.data, today]);

  const awayThisWeek = useMemo(() => {
    const end = new Date();
    end.setDate(end.getDate() + 6);
    const to = `${end.getFullYear()}-${String(end.getMonth() + 1).padStart(2, "0")}-${String(end.getDate()).padStart(2, "0")}`;
    return (leaveApps.data?.content ?? [])
      .filter((a) => String(a.status).toUpperCase() === "APPROVED")
      .filter((a) => datesBetween(a.startDate, a.endDate, today, to).length > 0)
      .sort((a, b) => (isoDate(a.startDate) < isoDate(b.startDate) ? -1 : 1));
  }, [leaveApps.data, today]);

  const maxDept = Math.max(1, ...headcount.depts.map(([, n]) => n));
  const val = (ok: boolean, v: number | string | undefined, loading: boolean) => (loading ? dash : ok ? (v ?? dash) : dash);

  return (
    <div className="stack">
      <PageHeader
        title="Admin dashboard"
        subtitle={`${fmtDate(today)} · key numbers from people, attendance and leave. Figures are limited to what your role can see.`}
      />

      <div className="grid g4">
        <StatCard
          label="People"
          value={val(can("employees", "view"), headcount.total, dir.isLoading)}
          sub={can("employees", "view") ? `${headcount.statuses.find(([s]) => s === "Active")?.[1] ?? 0} active` : "Needs employees access"}
          tone="acc"
        />
        <StatCard
          label="Present today"
          value={val(can("attendance", "view"), att.present + att.late, attendance.isLoading)}
          sub={can("attendance", "view") ? `${att.late} late · of ${att.total}` : "Needs attendance access"}
          tone="good"
        />
        <StatCard
          label="Absent today"
          value={val(can("attendance", "view"), att.absent, attendance.isLoading)}
          sub={can("attendance", "view") ? `${att.half} half day` : undefined}
          tone={att.absent ? "bad" : ""}
        />
        <StatCard
          label="Leave awaiting approval"
          value={val(can("leave", "view"), pendingLeave.data?.totalElements, pendingLeave.isLoading)}
          sub={
            can("attendance", "correct")
              ? `${corrections.data?.length ?? 0} attendance correction${(corrections.data?.length ?? 0) === 1 ? "" : "s"}`
              : undefined
          }
          tone={pendingLeave.data?.totalElements ? "warn" : ""}
        />
      </div>

      <div className="grid g2">
        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <h2>Headcount by department</h2>
            {headcount.noRole ? <StatusPill tone="warn">{headcount.noRole} without a role</StatusPill> : null}
          </div>
          {!can("employees", "view") ? (
            <p className="muted">You need employee access to see headcount.</p>
          ) : headcount.depts.length === 0 ? (
            <p className="muted">{dir.isLoading ? "Loading…" : "No employees yet."}</p>
          ) : (
            <div className="stack" style={{ gap: 8 }}>
              {headcount.depts.map(([name, n]) => (
                <div key={name} className="stack" style={{ gap: 3 }}>
                  <div className="row between small">
                    <span>{name}</span>
                    <span className="mono">{n}</span>
                  </div>
                  <div style={{ background: "var(--sunk)", borderRadius: 4, height: 8 }}>
                    <div style={{ width: `${(n / maxDept) * 100}%`, background: "var(--accent)", height: "100%", borderRadius: 4 }} />
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="card stack">
          <h2 style={{ marginBottom: 0 }}>Attendance today</h2>
          {!can("attendance", "view") ? (
            <p className="muted">You need attendance access to see today’s numbers.</p>
          ) : att.total === 0 ? (
            <p className="muted">{attendance.isLoading ? "Loading…" : "No attendance data for today."}</p>
          ) : (
            <>
              <div
                style={{ display: "flex", height: 14, borderRadius: 7, overflow: "hidden", background: "var(--sunk)" }}
                role="img"
                aria-label="Attendance split"
              >
                {[
                  [att.present, "var(--good)"],
                  [att.late, "var(--warn)"],
                  [att.half, "var(--info)"],
                  [att.absent, "var(--bad)"],
                ].map(([n, color], i) => (
                  <div key={i} style={{ width: `${(Number(n) / att.total) * 100}%`, background: String(color) }} />
                ))}
              </div>
              <div className="row small">
                <StatusPill tone="good">Present {att.present}</StatusPill>
                <StatusPill tone="warn">Late {att.late}</StatusPill>
                <StatusPill tone="info">Half day {att.half}</StatusPill>
                <StatusPill tone="bad">Absent {att.absent}</StatusPill>
              </div>
              <Link href="/team/attendance" className="linkbtn">
                Open team attendance →
              </Link>
            </>
          )}
        </section>
      </div>

      <div className="grid g2">
        <section className="card stack">
          <h2 style={{ marginBottom: 0 }}>Upcoming holidays</h2>
          {upcomingHolidays.length === 0 ? (
            <p className="muted">{holidays.isLoading ? "Loading…" : "No upcoming holidays on the calendar."}</p>
          ) : (
            <div className="stack" style={{ gap: 6 }}>
              {upcomingHolidays.map((h) => (
                <div className="row between" key={String(h.id)}>
                  <span>{h.name}</span>
                  <span className="mono small muted">{fmtRange(h.startDate, h.endDate)}</span>
                </div>
              ))}
            </div>
          )}
          <Link href="/settings/holidays" className="linkbtn">
            Holiday calendars →
          </Link>
        </section>

        <section className="card stack">
          <h2 style={{ marginBottom: 0 }}>Away in the next 7 days</h2>
          {!can("leave", "view") ? (
            <p className="muted">You need leave access to see who is away.</p>
          ) : awayThisWeek.length === 0 ? (
            <p className="muted">{leaveApps.isLoading ? "Loading…" : "Nobody has approved leave this week."}</p>
          ) : (
            <div className="stack" style={{ gap: 6 }}>
              {awayThisWeek.slice(0, 6).map((a) => (
                <div className="row between" key={String(a.id)}>
                  <span>
                    {dir.nameOf(a.employeeId)} <span className="muted small">· {a.leaveTypeCode}</span>
                  </span>
                  <span className="mono small muted">{fmtRange(a.startDate, a.endDate)}</span>
                </div>
              ))}
            </div>
          )}
          <Link href="/leave/calendar" className="linkbtn">
            Leave calendar →
          </Link>
        </section>
      </div>

      {can("settings", "manage") ? (
        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <h2>Recent activity</h2>
            <Link href="/settings/audit" className="linkbtn">
              Full audit log →
            </Link>
          </div>
          <div className="tbl-wrap">
            <table>
              <thead>
                <tr>
                  <th>When</th>
                  <th>Who</th>
                  <th>Action</th>
                  <th>Detail</th>
                </tr>
              </thead>
              <tbody>
                {(audit.data?.content ?? []).length === 0 ? (
                  <tr>
                    <td colSpan={4} className="empty">
                      {audit.isLoading ? "Loading…" : "No activity recorded yet"}
                    </td>
                  </tr>
                ) : (
                  (audit.data?.content ?? []).map((r) => (
                    <tr key={String(r.id)}>
                      <td className="mono small">{fmtDateTime(r.at)}</td>
                      <td>{dir.nameOf(r.actorEmployeeId)}</td>
                      <td>
                        <StatusPill tone="info">{humanize(r.action)}</StatusPill>
                      </td>
                      <td className="small muted">
                        {r.entity}
                        {r.entityId ? ` #${r.entityId}` : ""} · {oneLine(r.afterJson, 60)}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}

      <section className="card stack">
        <h2 style={{ marginBottom: 0 }}>Quick links</h2>
        <div className="row">
          {[
            ["/employees", "Employees", ["employees", "view"]],
            ["/approvals", "Approvals", ["leave", "approve"]],
            ["/payroll", "Payroll", ["payroll", "view"]],
            ["/reports", "Reports", ["reports", "view"]],
            ["/settings/org", "Organisation", ["org", "manage"]],
            ["/settings/roles", "Roles & permissions", ["roles", "view"]],
          ]
            .filter(([, , perm]) => can((perm as string[])[0], (perm as string[])[1]))
            .map(([href, label]) => (
              <Link key={String(href)} href={String(href)} className="btn">
                {String(label)}
              </Link>
            ))}
        </div>
        <p className="muted small">Signed in as {me.name}.</p>
      </section>
    </div>
  );
}
