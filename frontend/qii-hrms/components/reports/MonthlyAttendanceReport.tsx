"use client";

import { useMemo, useState } from "react";
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
});

type Result = BlobResult & { header: string[]; rows: string[][] };

const PREVIEW_ROWS = 100;

/** Run the reports-service monthly attendance report (POST → CSV), preview it and download. */
export function MonthlyAttendanceReport({
  employees,
  fixedEmployeeId,
  canExport = true,
}: {
  /** When given, the user can pick an employee (or all). */
  employees?: { id: string; name: string }[];
  /** Locks the report to one employee (used for "my monthly report"). */
  fixedEmployeeId?: string;
  canExport?: boolean;
}) {
  const toast = useToast();
  const now = new Date();
  const [form, setForm] = useState({
    year: String(now.getFullYear()),
    month: String(now.getMonth() + 1),
    employeeId: fixedEmployeeId ?? "",
  });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [result, setResult] = useState<(Result & { label: string }) | null>(null);

  const run = useMutation({
    mutationFn: async () => {
      const v = requestSchema.parse(form);
      const blob = await postForBlob(
        api.reports("attendance/monthly"),
        { year: Number(v.year), month: Number(v.month), ...(v.employeeId ? { employeeId: Number(v.employeeId) } : {}) },
        `attendance-${v.year}-${v.month.padStart(2, "0")}.csv`,
      );
      const parsed = parseCsv(await blob.blob.text());
      return { ...blob, header: parsed[0] ?? [], rows: parsed.slice(1), label: `${MONTH_NAMES[Number(v.month) - 1]} ${v.year}` };
    },
    onSuccess: (r) => setResult(r),
    onError: (e) => toast.error(e),
  });

  function submit() {
    const parsed = requestSchema.safeParse(form);
    if (!parsed.success) return setErrors(fieldErrors(parsed));
    setErrors({});
    run.mutate();
  }

  const statusIdx = result ? result.header.findIndex((h) => h.toLowerCase() === "status") : -1;
  const summary = useMemo(() => {
    if (!result || statusIdx < 0) return [];
    const c = new Map<string, number>();
    for (const r of result.rows) c.set(r[statusIdx] ?? "", (c.get(r[statusIdx] ?? "") ?? 0) + 1);
    return [...c.entries()].sort((a, b) => b[1] - a[1]);
  }, [result, statusIdx]);

  function shiftMonth(by: number) {
    const d = new Date(Number(form.year), Number(form.month) - 1 + by, 1);
    setForm({ ...form, year: String(d.getFullYear()), month: String(d.getMonth() + 1) });
    setResult(null);
  }

  return (
    <div className="stack">
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
        {employees && !fixedEmployeeId ? (
          <FormField label="Employee">
            <select value={form.employeeId} onChange={(e) => setForm({ ...form, employeeId: e.target.value })}>
              <option value="">All employees</option>
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

      {result ? (
        <div className="stack">
          <div className="row between">
            <div className="row">
              <b>{result.label}</b>
              <span className="muted small">
                {result.rows.length} row{result.rows.length === 1 ? "" : "s"} · {result.filename}
              </span>
            </div>
            {canExport ? (
              <button type="button" className="btn" onClick={() => saveBlob(result)}>
                Download CSV
              </button>
            ) : null}
          </div>
          {summary.length ? (
            <div className="row">
              {summary.map(([k, n]) => (
                <StatusPill key={k} tone={/present/i.test(k) ? "good" : /late|half/i.test(k) ? "warn" : /absent/i.test(k) ? "bad" : ""}>
                  {humanize(k)} · {n}
                </StatusPill>
              ))}
            </div>
          ) : null}
          {result.rows.length === 0 ? (
            <div className="note">The report ran but contains no rows for this period.</div>
          ) : (
            <div className="tbl-wrap" style={{ maxHeight: 420, overflow: "auto" }}>
              <table>
                <thead>
                  <tr>
                    {result.header.map((h) => (
                      <th key={h}>{humanize(h.replace(/([a-z])([A-Z])/g, "$1_$2"))}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {result.rows.slice(0, PREVIEW_ROWS).map((r, i) => (
                    <tr key={i}>
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
          )}
          {result.rows.length > PREVIEW_ROWS ? (
            <p className="muted small">
              Showing the first {PREVIEW_ROWS} of {result.rows.length} rows — download the CSV for the full report.
            </p>
          ) : null}
        </div>
      ) : (
        <div className="note small">Choose a period and run the report to preview it here, then download the CSV.</div>
      )}
    </div>
  );
}
