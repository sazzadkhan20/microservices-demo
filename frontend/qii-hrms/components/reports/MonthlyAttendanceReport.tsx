"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { z } from "zod";
import { FormField } from "@/components/ui/FormField";
import { StatusPill } from "@/components/ui/StatusPill";
import { useToast } from "@/components/ui/Toast";
import { api } from "@/lib/api/client";
import { MONTH_NAMES, fieldErrors, parseCsv, postForBlob, saveBlob, type BlobResult, type FieldErrors } from "@/lib/admin";
import { humanize } from "@/lib/format";

const requestSchema = z.object({
  year: z
    .string()
    .regex(/^\d{4}$/, "Enter a 4-digit year")
    .refine((v) => Number(v) >= 2000 && Number(v) <= 2100, "Year must be 2000–2100"),
  month: z.string().regex(/^\d+$/).refine((v) => Number(v) >= 1 && Number(v) <= 12, "Pick a month"),
  employeeId: z.string(),
  locationId: z.string().optional(),
  departmentId: z.string().optional(),
  teamId: z.string().optional(),
});

type Result = BlobResult & { header: string[]; rows: string[][] };

const PAGE_SIZES = [10, 20, 50, 100];

/** FR-RP-01 — monthly attendance register (person × day matrix). */
export function MonthlyAttendanceReport({
  employees,
  locations,
  departments,
  teams,
  fixedEmployeeId,
  canExport = true,
  sharedFilters,
  hideOrgFilters = false,
  hideToolbar = false,
  period,
  runKey = 0,
  onPendingChange,
  onComplete,
}: {
  employees?: { id: string; name: string }[];
  locations?: { id: string; name: string }[];
  departments?: { id: string; name: string }[];
  teams?: { id: string; name: string }[];
  fixedEmployeeId?: string;
  canExport?: boolean;
  sharedFilters?: {
    locationId?: string;
    departmentId?: string;
    teamId?: string;
    employeeId?: string;
  };
  hideOrgFilters?: boolean;
  /** When true, month/year/run live on the parent page. */
  hideToolbar?: boolean;
  period?: { month: string; year: string };
  /** Increment to trigger a run from the parent. */
  runKey?: number;
  onPendingChange?: (pending: boolean) => void;
  onComplete?: () => void;
}) {
  const toast = useToast();
  const now = new Date();
  const [form, setForm] = useState({
    year: period?.year ?? String(now.getFullYear()),
    month: period?.month ?? String(now.getMonth() + 1),
    employeeId: fixedEmployeeId ?? sharedFilters?.employeeId ?? "",
    locationId: sharedFilters?.locationId ?? "",
    departmentId: sharedFilters?.departmentId ?? "",
    teamId: sharedFilters?.teamId ?? "",
  });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [result, setResult] = useState<(Result & { label: string }) | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  useEffect(() => {
    setForm((f) => ({
      ...f,
      ...(period ? { month: period.month, year: period.year } : {}),
      locationId: sharedFilters?.locationId ?? f.locationId,
      departmentId: sharedFilters?.departmentId ?? f.departmentId,
      teamId: sharedFilters?.teamId ?? f.teamId,
      employeeId: fixedEmployeeId ?? sharedFilters?.employeeId ?? f.employeeId,
    }));
    setResult(null);
  }, [sharedFilters, fixedEmployeeId, period?.month, period?.year]);

  const run = useMutation({
    mutationFn: async (payload?: typeof form) => {
      const v = requestSchema.parse(payload ?? form);
      const blob = await postForBlob(
        api.reports("attendance/monthly"),
        {
          year: Number(v.year),
          month: Number(v.month),
          ...(v.employeeId ? { employeeId: Number(v.employeeId) } : {}),
          ...(v.locationId ? { locationId: Number(v.locationId) } : {}),
          ...(v.departmentId ? { departmentId: Number(v.departmentId) } : {}),
          ...(v.teamId ? { teamId: Number(v.teamId) } : {}),
        },
        `attendance-${v.year}-${v.month.padStart(2, "0")}.csv`,
      );
      const parsed = parseCsv(await blob.blob.text());
      return {
        ...blob,
        header: parsed[0] ?? [],
        rows: parsed.slice(1),
        label: `${MONTH_NAMES[Number(v.month) - 1]} ${v.year}`,
      };
    },
    onSuccess: (r) => {
      setResult(r);
      setPage(1);
      onComplete?.();
    },
    onError: (e) => toast.error(e),
  });

  useEffect(() => {
    onPendingChange?.(run.isPending);
  }, [run.isPending, onPendingChange]);

  const lastRunKey = useRef(0);
  useEffect(() => {
    if (!runKey || runKey === lastRunKey.current) return;
    lastRunKey.current = runKey;
    const next = {
      ...form,
      ...(period ? { month: period.month, year: period.year } : {}),
      locationId: sharedFilters?.locationId ?? form.locationId,
      departmentId: sharedFilters?.departmentId ?? form.departmentId,
      teamId: sharedFilters?.teamId ?? form.teamId,
      employeeId: fixedEmployeeId ?? sharedFilters?.employeeId ?? form.employeeId,
    };
    setForm(next);
    const parsed = requestSchema.safeParse(next);
    if (!parsed.success) {
      setErrors(fieldErrors(parsed));
      return;
    }
    setErrors({});
    run.mutate(next);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run only when parent bumps runKey
  }, [runKey]);

  function submit() {
    const parsed = requestSchema.safeParse(form);
    if (!parsed.success) return setErrors(fieldErrors(parsed));
    setErrors({});
    run.mutate(form);
  }

  function shiftMonth(by: number) {
    const d = new Date(Number(form.year), Number(form.month) - 1 + by, 1);
    setForm({ ...form, year: String(d.getFullYear()), month: String(d.getMonth() + 1) });
    setResult(null);
  }

  const statusSummary = useMemo(() => {
    if (!result?.header?.length) return [];
    const keys = ["P", "L", "A", "M", "HD", "OL", "WO", "HO", "NS", "NE"];
    const idx = Object.fromEntries(keys.map((k) => [k, result.header.indexOf(k)]));
    return keys
      .map((k) => {
        const i = idx[k];
        if (i < 0) return null;
        const n = result.rows.reduce((s, r) => s + (Number(r[i]) || 0), 0);
        return n > 0 ? ([k, n] as [string, number]) : null;
      })
      .filter(Boolean) as [string, number][];
  }, [result]);

  const totalPages = Math.max(1, Math.ceil((result?.rows.length ?? 0) / pageSize));
  const pageSafe = Math.min(page, totalPages);
  const pagedRows = useMemo(() => {
    if (!result?.rows?.length) return [];
    const from = (pageSafe - 1) * pageSize;
    return result.rows.slice(from, from + pageSize);
  }, [result, pageSafe, pageSize]);

  const showOrg = !hideOrgFilters;

  return (
    <div className="stack">
      {!hideToolbar ? (
        <div className="filters">
          <FormField label="Month" error={errors.month}>
            <select value={form.month} onChange={(e) => setForm({ ...form, month: e.target.value })}>
              {MONTH_NAMES.map((m, i) => (
                <option key={m} value={i + 1}>
                  {m}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Year" error={errors.year}>
            <input type="number" value={form.year} onChange={(e) => setForm({ ...form, year: e.target.value })} style={{ width: 100 }} />
          </FormField>
          {showOrg && locations?.length ? (
            <FormField label="Location">
              <select value={form.locationId} onChange={(e) => setForm({ ...form, locationId: e.target.value })}>
                <option value="">All locations</option>
                {locations.map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.name}
                  </option>
                ))}
              </select>
            </FormField>
          ) : null}
          {showOrg && departments?.length ? (
            <FormField label="Department">
              <select value={form.departmentId} onChange={(e) => setForm({ ...form, departmentId: e.target.value })}>
                <option value="">All departments</option>
                {departments.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            </FormField>
          ) : null}
          {showOrg && teams?.length ? (
            <FormField label="Team">
              <select value={form.teamId} onChange={(e) => setForm({ ...form, teamId: e.target.value })}>
                <option value="">All teams</option>
                {teams.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </FormField>
          ) : null}
          {showOrg && employees && !fixedEmployeeId ? (
            <FormField label="Person">
              <select value={form.employeeId} onChange={(e) => setForm({ ...form, employeeId: e.target.value })}>
                <option value="">All people in scope</option>
                {employees.map((e) => (
                  <option key={e.id} value={e.id}>
                    {e.name}
                  </option>
                ))}
              </select>
            </FormField>
          ) : null}
          <div className="field">
            <span>&nbsp;</span>
            <div className="row">
              <button type="button" className="btn" aria-label="Previous month" onClick={() => shiftMonth(-1)}>
                ←
              </button>
              <button type="button" className="btn" aria-label="Next month" onClick={() => shiftMonth(1)}>
                →
              </button>
              <button type="button" className="btn primary" disabled={run.isPending} onClick={submit}>
                {run.isPending ? "Generating…" : "Run report"}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {result ? (
        <div className="stack">
          <div className="row between">
            <div className="row">
              <b>FR-RP-01 · {result.label}</b>
              <span className="muted small">
                {result.rows.length} person{result.rows.length === 1 ? "" : "s"} · {result.filename}
              </span>
            </div>
            {canExport ? (
              <button type="button" className="btn" onClick={() => saveBlob(result)}>
                Download CSV
              </button>
            ) : null}
          </div>
          {statusSummary.length ? (
            <div className="row" style={{ flexWrap: "wrap", gap: 6 }}>
              {statusSummary.map(([k, n]) => (
                <StatusPill
                  key={k}
                  tone={k === "P" || k === "W" ? "good" : k === "L" || k === "M" || k === "HD" ? "warn" : k === "A" ? "bad" : ""}
                >
                  {k} · {n}
                </StatusPill>
              ))}
            </div>
          ) : null}
          {result.rows.length === 0 ? (
            <div className="note">No people matched these filters for this month.</div>
          ) : (
            <>
              <div className="row between" style={{ flexWrap: "wrap", gap: 8 }}>
                <span className="muted small">
                  Showing {(pageSafe - 1) * pageSize + 1}–{Math.min(pageSafe * pageSize, result.rows.length)} of{" "}
                  {result.rows.length}
                </span>
                <div className="row" style={{ gap: 8, alignItems: "center" }}>
                  <span className="muted small">Rows</span>
                  <select
                    aria-label="Page size"
                    value={pageSize}
                    onChange={(e) => {
                      setPageSize(Number(e.target.value));
                      setPage(1);
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
              <div className="tbl-wrap" style={{ maxHeight: 480, overflow: "auto" }}>
                <table>
                  <thead>
                    <tr>
                      {result.header.map((h) => (
                        <th key={h}>{/^D\d+$/.test(h) ? h.slice(1) : humanize(h)}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {pagedRows.map((r, i) => (
                      <tr key={(pageSafe - 1) * pageSize + i}>
                        {r.map((c, j) => (
                          <td key={j} className="mono small">
                            {c || <span className="muted">—</span>}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="row between" style={{ flexWrap: "wrap", gap: 8 }}>
                <span className="muted small">
                  Page {pageSafe} of {totalPages}
                </span>
                <div className="row" style={{ gap: 8 }}>
                  <button
                    type="button"
                    className="btn sm"
                    disabled={pageSafe <= 1}
                    onClick={() => setPage((p) => Math.max(1, p - 1))}
                  >
                    Previous
                  </button>
                  <button
                    type="button"
                    className="btn sm"
                    disabled={pageSafe >= totalPages}
                    onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  >
                    Next
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      ) : hideToolbar ? (
        <div className="note small">Set filters and month above, then Run report to preview the attendance register.</div>
      ) : (
        <div className="note small">Choose month and run the report to preview the day register, then download CSV.</div>
      )}
    </div>
  );
}
