"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { MonthlyAttendanceReport } from "@/components/reports/MonthlyAttendanceReport";
import { EmptyState } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote, Loading } from "@/components/ui/QueryState";
import { StatusPill } from "@/components/ui/StatusPill";
import { api, apiFetch } from "@/lib/api/client";
import { useDirectory } from "@/lib/api/directory";
import { fmtDateTime, humanize } from "@/lib/format";

const exportsSchema = z.array(z.record(z.string(), z.any()));

/** Reports that reports-service actually serves. Add an entry when a new endpoint ships. */
const CATALOG = [
  {
    id: "attendance-monthly",
    name: "Monthly attendance",
    description: "Per-day attendance status with check-in and check-out for a month, for everyone or one person.",
    format: "CSV",
    endpoint: "POST /reports/api/reports/attendance/monthly",
  },
];

export default function ReportsPage() {
  const { can, me } = useAuth();
  const dir = useDirectory(can("employees", "view"));
  const exportsQ = useQuery({
    queryKey: ["report-exports"],
    queryFn: ({ signal }) => apiFetch(api.reports("exports"), { schema: exportsSchema, signal }),
  });
  const past = exportsQ.data ?? [];
  const cols = Array.from(new Set(past.flatMap((r) => Object.keys(r)))).slice(0, 6);

  return (
    <div className="stack">
      <PageHeader
        title="Reports"
        subtitle="Run a report, preview it, and download it. Everyone can also open their own monthly report."
        actions={
          <Link href="/reports/monthly" className="btn">
            My monthly report
          </Link>
        }
      />

      <section className="card stack">
        <div className="card-head" style={{ marginBottom: 0 }}>
          <h2>Available reports</h2>
          <span className="muted small">
            {CATALOG.length} report{CATALOG.length === 1 ? "" : "s"}
          </span>
        </div>
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th>Report</th>
                <th>What it covers</th>
                <th>Format</th>
              </tr>
            </thead>
            <tbody>
              {CATALOG.map((r) => (
                <tr key={r.id}>
                  <td>
                    <b>{r.name}</b>
                    <br />
                    <span className="mono small muted">{r.endpoint}</span>
                  </td>
                  <td className="small">{r.description}</td>
                  <td>
                    <StatusPill tone="acc">{r.format}</StatusPill>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="note small">
          Payroll, leave-balance and headcount reports are not served by the reports service yet, so they are not listed here.
        </div>
      </section>

      <section className="card stack">
        <h2 style={{ marginBottom: 0 }}>Monthly attendance</h2>
        {can("reports", "export") || can("reports", "view") ? (
          <MonthlyAttendanceReport
            canExport={can("reports", "export")}
            employees={dir.employees.map((e) => ({ id: String(e.id), name: e.fullName ?? `#${e.id}` }))}
            fixedEmployeeId={dir.employees.length === 0 && !can("employees", "view") ? String(me.employeeId) : undefined}
          />
        ) : (
          <EmptyState title="No access">You need the reports permission to run this report.</EmptyState>
        )}
      </section>

      <section className="card stack">
        <h2 style={{ marginBottom: 0 }}>Past exports</h2>
        <ErrorNote error={exportsQ.error} />
        {exportsQ.isLoading ? <Loading /> : null}
        {!exportsQ.isLoading && !exportsQ.error && past.length === 0 ? (
          <div className="note">
            No archived exports. The reports service returns the list of saved export files from{" "}
            <span className="mono">GET /reports/api/reports/exports</span>, and it is empty because reports are generated on
            demand and are not archived yet.
          </div>
        ) : null}
        {past.length > 0 ? (
          <div className="tbl-wrap">
            <table>
              <thead>
                <tr>
                  {cols.map((c) => (
                    <th key={c}>{humanize(c.replace(/([a-z])([A-Z])/g, "$1_$2"))}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {past.map((r, i) => (
                  <tr key={i}>
                    {cols.map((c) => (
                      <td key={c} className="small">
                        {/(at|date|time)$/i.test(c) ? fmtDateTime(r[c]) : String(r[c] ?? "—")}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>
    </div>
  );
}
