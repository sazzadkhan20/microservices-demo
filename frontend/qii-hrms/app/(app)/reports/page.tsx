"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { MonthlyAttendanceReport } from "@/components/reports/MonthlyAttendanceReport";
import { EmptyState } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote, Loading, NoAccess } from "@/components/ui/QueryState";
import { StatusPill } from "@/components/ui/StatusPill";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch, downloadFile } from "@/lib/api/client";
import { useDirectory } from "@/lib/api/directory";
import { useDepartments, useLocations, useTeams } from "@/lib/api/lookups";
import { MONTH_NAMES } from "@/lib/admin";
import { fmtDateTime, humanize, todayIso } from "@/lib/format";

const exportsSchema = z.array(z.record(z.string(), z.any()));

const CATALOG = [
  { id: "rp01", code: "FR-RP-01", type: "ATTENDANCE_MONTHLY", name: "Monthly attendance register", blurb: "One row per person, one column per day, status totals." },
  { id: "rp02", code: "FR-RP-02", type: "LATE_ABSENCE", name: "Late & absence summary", blurb: "Working days, late, absent, missing checkout, leave, late %." },
  { id: "rp03", code: "FR-RP-03", type: "LEAVE_BALANCES", name: "Leave balances", blurb: "Quota, carried, adjusted, taken, pending, remaining." },
  { id: "rp04", code: "FR-RP-04", type: "LEAVE_REQUESTS", name: "Leave requests log", blurb: "Every request with approvers, decisions and times." },
  { id: "rp05", code: "FR-RP-05", type: "HEADCOUNT", name: "Headcount", blurb: "Month-end headcount, joiners and leavers (last 6 months)." },
  { id: "rp06", code: "FR-RP-06", type: "EMPLOYEE_MASTER", name: "Employee master list", blurb: "Employment fields for people in your report scope." },
  { id: "rp07", code: "FR-RP-07", type: "PAYROLL", name: "Payroll", blurb: "Payroll run CSV for a month (opens Payroll export)." },
];

const EXPORT_CATEGORY_LABELS: Record<string, string> = {
  ATTENDANCE_MONTHLY: "FR-RP-01 · Monthly attendance",
  LATE_ABSENCE: "FR-RP-02 · Late & absence",
  LEAVE_BALANCES: "FR-RP-03 · Leave balances",
  LEAVE_REQUESTS: "FR-RP-04 · Leave requests",
  HEADCOUNT: "FR-RP-05 · Headcount",
  EMPLOYEE_MASTER: "FR-RP-06 · Employee master",
  PAYROLL: "FR-RP-07 · Payroll",
};

const thisYear = new Date().getFullYear();
const PAGE_SIZES = [10, 20, 50];

export default function ReportsPage() {
  const { can, me } = useAuth();
  const toast = useToast();
  const allowed = can("reports", "view") || can("reports", "export");
  const canExport = can("reports", "export");
  const dir = useDirectory(can("employees", "view"));
  const locations = useLocations(allowed);
  const departments = useDepartments(allowed);
  const teams = useTeams(allowed);

  const today = todayIso();
  const now = new Date();
  const [locationId, setLocationId] = useState("");
  const [departmentId, setDepartmentId] = useState("");
  const [teamId, setTeamId] = useState("");
  const [employeeId, setEmployeeId] = useState("");
  const [attMonth, setAttMonth] = useState(String(now.getMonth() + 1));
  const [attYear, setAttYear] = useState(String(now.getFullYear()));
  const [runKey, setRunKey] = useState(0);
  const [attPending, setAttPending] = useState(false);
  const [lbYear, setLbYear] = useState(String(thisYear));
  const [rangeFrom, setRangeFrom] = useState(`${thisYear}-01-01`);
  const [rangeTo, setRangeTo] = useState(today);
  const [downloading, setDownloading] = useState<string | null>(null);

  const [exportCategory, setExportCategory] = useState("");
  const [exportPage, setExportPage] = useState(1);
  const [exportSize, setExportSize] = useState(10);

  const employees = useMemo(
    () =>
      [...(dir.employees ?? [])]
        .filter((e) => {
          if (locationId && String(e.locationId ?? "") !== locationId) return false;
          if (departmentId && String(e.departmentId ?? "") !== departmentId) return false;
          if (teamId && String(e.teamId ?? "") !== teamId) return false;
          return true;
        })
        .map((e) => ({ id: String(e.id), name: e.fullName ?? `#${e.id}` }))
        .sort((a, b) => a.name.localeCompare(b.name)),
    [dir.employees, locationId, departmentId, teamId],
  );

  const locOpts = useMemo(
    () => [...(locations.data ?? [])].map((l) => ({ id: String(l.id), name: String(l.name) })).sort((a, b) => a.name.localeCompare(b.name)),
    [locations.data],
  );
  const deptOpts = useMemo(
    () =>
      [...(departments.data ?? [])]
        .map((d) => ({ id: String(d.id), name: String(d.name) }))
        .sort((a, b) => a.name.localeCompare(b.name)),
    [departments.data],
  );
  const teamOpts = useMemo(
    () => [...(teams.data ?? [])].map((t) => ({ id: String(t.id), name: String(t.name) })).sort((a, b) => a.name.localeCompare(b.name)),
    [teams.data],
  );

  const orgQuery = useMemo(() => {
    const q: Record<string, string> = {};
    if (locationId) q.locationId = locationId;
    if (departmentId) q.departmentId = departmentId;
    if (teamId) q.teamId = teamId;
    if (employeeId) q.employeeId = employeeId;
    return q;
  }, [locationId, departmentId, teamId, employeeId]);

  const sharedFilters = useMemo(
    () => ({ locationId, departmentId, teamId, employeeId }),
    [locationId, departmentId, teamId, employeeId],
  );

  const exportsQ = useQuery({
    queryKey: ["report-exports"],
    queryFn: ({ signal }) => apiFetch(api.reports("exports"), { schema: exportsSchema, signal }),
    enabled: allowed,
  });
  const past = exportsQ.data ?? [];

  async function doDownload(key: string, path: string, query: Record<string, string>, name: string) {
    if (rangeFrom && rangeTo && rangeFrom > rangeTo && (key === "lr" || key === "la")) {
      toast.error("From date must be on or before To date");
      return;
    }
    setDownloading(key);
    try {
      await downloadFile(path, query, name);
      toast.show("Download started");
      void exportsQ.refetch();
    } catch (e) {
      toast.error(e);
    } finally {
      setDownloading(null);
    }
  }

  function shiftAttendanceMonth(by: number) {
    const d = new Date(Number(attYear), Number(attMonth) - 1 + by, 1);
    setAttYear(String(d.getFullYear()));
    setAttMonth(String(d.getMonth() + 1));
  }

  const exportCategories = useMemo(() => {
    const set = new Set<string>();
    for (const r of past) {
      const t = String(r.reportType ?? "").trim();
      if (t) set.add(t);
    }
    return [...set].sort((a, b) => a.localeCompare(b));
  }, [past]);

  const filteredPast = useMemo(() => {
    if (!exportCategory) return past;
    return past.filter((r) => String(r.reportType ?? "") === exportCategory);
  }, [past, exportCategory]);

  const exportTotalPages = Math.max(1, Math.ceil(filteredPast.length / exportSize));
  const exportPageSafe = Math.min(exportPage, exportTotalPages);
  const pagedPast = useMemo(() => {
    const from = (exportPageSafe - 1) * exportSize;
    return filteredPast.slice(from, from + exportSize);
  }, [filteredPast, exportPageSafe, exportSize]);

  if (!allowed) {
    return (
      <div className="stack">
        <PageHeader title="Reports" />
        <NoAccess what="reports" />
      </div>
    );
  }

  return (
    <div className="stack">
      <PageHeader
        title="Reports"
        subtitle="Scoped CSV exports for HR analytics. Filters apply across every download below."
        actions={
          <Link href="/reports/monthly" className="btn">
            My monthly report
          </Link>
        }
      />

      {/* 1. Filters + month/year + Run report (one row) */}
      <section className="card stack" style={{ gap: 12 }}>
        <div className="card-head" style={{ marginBottom: 0 }}>
          <h2 style={{ margin: 0 }}>Filters</h2>
          <span className="muted small">Applies to every report · month/year for FR-RP-01</span>
        </div>
        <div className="filters">
          <label className="field">
            <span>Location</span>
            <select value={locationId} onChange={(e) => setLocationId(e.target.value)} aria-label="Filter location">
              <option value="">All locations</option>
              {locOpts.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Department</span>
            <select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)} aria-label="Filter department">
              <option value="">All departments</option>
              {deptOpts.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Team</span>
            <select value={teamId} onChange={(e) => setTeamId(e.target.value)} aria-label="Filter team">
              <option value="">All teams</option>
              {teamOpts.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Person</span>
            <select value={employeeId} onChange={(e) => setEmployeeId(e.target.value)} aria-label="Filter person">
              <option value="">All people in scope</option>
              {employees.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Month</span>
            <select value={attMonth} onChange={(e) => setAttMonth(e.target.value)} aria-label="Attendance month">
              {MONTH_NAMES.map((m, i) => (
                <option key={m} value={i + 1}>
                  {m}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Year</span>
            <input
              type="number"
              value={attYear}
              onChange={(e) => setAttYear(e.target.value)}
              style={{ width: 100 }}
              aria-label="Attendance year"
            />
          </label>
          <div className="field">
            <span>&nbsp;</span>
            <div className="row" style={{ gap: 8 }}>
              <button type="button" className="btn" aria-label="Previous month" onClick={() => shiftAttendanceMonth(-1)}>
                ←
              </button>
              <button type="button" className="btn" aria-label="Next month" onClick={() => shiftAttendanceMonth(1)}>
                →
              </button>
              <button
                type="button"
                className="btn primary"
                disabled={attPending}
                onClick={() => setRunKey((k) => k + 1)}
              >
                {attPending ? "Generating…" : "Run report"}
              </button>
              {(locationId || departmentId || teamId || employeeId) ? (
                <button
                  type="button"
                  className="btn"
                  onClick={() => {
                    setLocationId("");
                    setDepartmentId("");
                    setTeamId("");
                    setEmployeeId("");
                  }}
                >
                  Clear
                </button>
              ) : null}
            </div>
          </div>
        </div>
      </section>

      {/* 2. Downloads */}
      <section className="card stack" style={{ gap: 16 }}>
        <div className="card-head" style={{ marginBottom: 0 }}>
          <h2 style={{ margin: 0 }}>CSV downloads</h2>
          <span className="muted small">Shared filters apply to every export</span>
        </div>

        <div className="stack" style={{ gap: 8 }}>
          <div>
            <b>FR-RP-01 · Monthly attendance register</b>
            <p className="muted small" style={{ margin: 0 }}>
              Use month/year above, then Run report to preview and download.
            </p>
          </div>
          <MonthlyAttendanceReport
            canExport={canExport}
            employees={employees}
            hideOrgFilters
            hideToolbar
            sharedFilters={sharedFilters}
            period={{ month: attMonth, year: attYear }}
            runKey={runKey}
            onPendingChange={setAttPending}
            onComplete={() => void exportsQ.refetch()}
            fixedEmployeeId={!can("employees", "view") && !canExport ? String(me.employeeId) : undefined}
          />
        </div>

        {canExport ? (
          <>
            <hr style={{ border: 0, borderTop: "1px solid var(--border, #e5e7eb)", margin: 0 }} />

            <ReportDownload
              title="FR-RP-02 · Late & absence summary"
              hint="Per person for a date range"
              busy={downloading === "la"}
              onDownload={() =>
                doDownload(
                  "la",
                  api.reports("late-absence.csv"),
                  { from: rangeFrom, to: rangeTo, ...orgQuery },
                  "late-absence.csv",
                )
              }
            >
              <DateRange from={rangeFrom} to={rangeTo} setFrom={setRangeFrom} setTo={setRangeTo} />
            </ReportDownload>

            <ReportDownload
              title="FR-RP-03 · Leave balances"
              hint="Year snapshot by leave type"
              busy={downloading === "lb"}
              onDownload={() =>
                doDownload(
                  "lb",
                  api.reports("leave-balances.csv"),
                  { year: lbYear, ...orgQuery },
                  `leave-balances-${lbYear}.csv`,
                )
              }
            >
              <select aria-label="Year" value={lbYear} onChange={(e) => setLbYear(e.target.value)} style={{ width: 100 }}>
                {[thisYear - 1, thisYear, thisYear + 1].map((y) => (
                  <option key={y}>{y}</option>
                ))}
              </select>
            </ReportDownload>

            <ReportDownload
              title="FR-RP-04 · Leave requests log"
              hint="Requests overlapping the range"
              busy={downloading === "lr"}
              onDownload={() =>
                doDownload(
                  "lr",
                  api.reports("leave-requests.csv"),
                  { from: rangeFrom, to: rangeTo, ...orgQuery },
                  "leave-requests.csv",
                )
              }
            >
              <DateRange from={rangeFrom} to={rangeTo} setFrom={setRangeFrom} setTo={setRangeTo} />
            </ReportDownload>

            <ReportDownload
              title="FR-RP-05 · Headcount"
              hint="Last 6 months joiners / leavers / active at month-end"
              busy={downloading === "hc"}
              onDownload={() => doDownload("hc", api.reports("headcount.csv"), { ...orgQuery }, "headcount.csv")}
            />

            <ReportDownload
              title="FR-RP-06 · Employee master list"
              hint="Employment fields for people in scope"
              busy={downloading === "em"}
              onDownload={() => doDownload("em", api.reports("employees.csv"), { ...orgQuery }, "employees.csv")}
            />
          </>
        ) : (
          <p className="muted small">CSV export requires the reports:export permission.</p>
        )}

        <div className="row between" style={{ flexWrap: "wrap", gap: 8, alignItems: "center" }}>
          <div>
            <b>FR-RP-07 · Payroll</b>
            <p className="muted small" style={{ margin: 0 }}>
              Export from Payroll runs (requires payroll:export)
            </p>
          </div>
          <Link href="/payroll" className="btn sm">
            Open payroll
          </Link>
        </div>
      </section>

      {/* 3. Report catalog */}
      <section className="card stack">
        <div className="card-head" style={{ marginBottom: 0 }}>
          <h2 style={{ margin: 0 }}>Report catalog</h2>
          <span className="muted small">{CATALOG.length} reports</span>
        </div>
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Report</th>
                <th>Content</th>
              </tr>
            </thead>
            <tbody>
              {CATALOG.map((r) => (
                <tr key={r.id}>
                  <td className="mono small">{r.code}</td>
                  <td>
                    <b>{r.name}</b>
                  </td>
                  <td className="small">{r.blurb}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* 4. Past exports — category filter + pagination */}
      <section className="card stack">
        <div className="card-head" style={{ marginBottom: 0 }}>
          <h2 style={{ margin: 0 }}>Past exports</h2>
          <span className="muted small">
            {filteredPast.length} of {past.length} archived
          </span>
        </div>

        <div className="row between" style={{ flexWrap: "wrap", gap: 8 }}>
          <div className="row" style={{ gap: 8, flexWrap: "wrap", alignItems: "center" }}>
            <select
              aria-label="Report category"
              value={exportCategory}
              onChange={(e) => {
                setExportCategory(e.target.value);
                setExportPage(1);
              }}
              style={{ width: 280 }}
            >
              <option value="">All categories</option>
              {exportCategories.map((c) => (
                <option key={c} value={c}>
                  {EXPORT_CATEGORY_LABELS[c] ?? humanize(c)}
                </option>
              ))}
            </select>
          </div>
          <div className="row" style={{ gap: 8, alignItems: "center" }}>
            <span className="muted small">Rows</span>
            <select
              aria-label="Page size"
              value={exportSize}
              onChange={(e) => {
                setExportSize(Number(e.target.value));
                setExportPage(1);
              }}
              style={{ width: 80 }}
            >
              {PAGE_SIZES.map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
          </div>
        </div>

        <ErrorNote error={exportsQ.error} />
        {exportsQ.isLoading ? <Loading /> : null}
        {!exportsQ.isLoading && !exportsQ.error && filteredPast.length === 0 ? (
          <EmptyState title="No archived exports yet">
            {exportCategory ? "No exports in this category." : "Run a report above to create an archive entry."}
          </EmptyState>
        ) : null}

        {pagedPast.length > 0 ? (
          <>
            <div className="tbl-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Category</th>
                    <th>Period</th>
                    <th>File</th>
                    <th>Status</th>
                    <th>Size</th>
                    <th>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {pagedPast.map((r, i) => {
                    const type = String(r.reportType ?? "");
                    return (
                      <tr key={String(r.id ?? i)}>
                        <td className="small">{EXPORT_CATEGORY_LABELS[type] ?? humanize(type) ?? "—"}</td>
                        <td className="mono small">{String(r.periodLabel ?? "—")}</td>
                        <td className="small">{String(r.fileName ?? "—")}</td>
                        <td>
                          <StatusPill status={String(r.status ?? "")} />
                        </td>
                        <td className="mono small">
                          {r.sizeBytes != null && r.sizeBytes !== "" ? `${r.sizeBytes} B` : "—"}
                        </td>
                        <td className="mono small">{fmtDateTime(r.createdAt)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="row between" style={{ flexWrap: "wrap", gap: 8 }}>
              <span className="muted small">
                Page {exportPageSafe} of {exportTotalPages}
              </span>
              <div className="row" style={{ gap: 8 }}>
                <button
                  type="button"
                  className="btn sm"
                  disabled={exportPageSafe <= 1}
                  onClick={() => setExportPage((p) => Math.max(1, p - 1))}
                >
                  Previous
                </button>
                <button
                  type="button"
                  className="btn sm"
                  disabled={exportPageSafe >= exportTotalPages}
                  onClick={() => setExportPage((p) => Math.min(exportTotalPages, p + 1))}
                >
                  Next
                </button>
              </div>
            </div>
          </>
        ) : null}
      </section>
    </div>
  );
}

function DateRange({
  from,
  to,
  setFrom,
  setTo,
}: {
  from: string;
  to: string;
  setFrom: (v: string) => void;
  setTo: (v: string) => void;
}) {
  return (
    <div className="row" style={{ gap: 8, flexWrap: "wrap" }}>
      <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} style={{ width: 140 }} aria-label="From" />
      <span className="muted">–</span>
      <input type="date" value={to} onChange={(e) => setTo(e.target.value)} style={{ width: 140 }} aria-label="To" />
    </div>
  );
}

function ReportDownload({
  title,
  hint,
  busy,
  onDownload,
  children,
}: {
  title: string;
  hint: string;
  busy: boolean;
  onDownload: () => void;
  children?: React.ReactNode;
}) {
  return (
    <div className="row between" style={{ flexWrap: "wrap", gap: 8, alignItems: "flex-end" }}>
      <div>
        <b>{title}</b>
        <p className="muted small" style={{ margin: 0 }}>
          {hint}
        </p>
      </div>
      <div className="row" style={{ gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        {children}
        <button type="button" className="btn sm primary" disabled={busy} onClick={onDownload}>
          {busy ? "Downloading…" : "Download CSV"}
        </button>
      </div>
    </div>
  );
}
