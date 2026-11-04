"use client";

import { useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { DataTable } from "@/components/ui/DataTable";
import { FormField } from "@/components/ui/FormField";
import { ConfirmModal } from "@/components/ui/ConfirmModal";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { StatusPill } from "@/components/ui/StatusPill";
import { Tabs } from "@/components/ui/Tabs";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch, downloadFile } from "@/lib/api/client";
import { byId, rowsOf, useEmployeesLookup, type Rec } from "@/lib/api/lookups";
import { currentPeriod, fmtDateTime, fmtMoney, fmtNumber, str } from "@/lib/format";

const rowSchema = z.object({ id: z.union([z.number(), z.string()]) }).passthrough();

const calcSchema = z.object({
  employeeId: z.string().min(1, "Choose an employee"),
  periodLabel: z.string().regex(/^\d{4}-(0[1-9]|1[0-2])$/, "Use YYYY-MM"),
  useHrmsAttendance: z.boolean(),
  absenceDays: z.coerce.number("Enter days").min(0, "Must be ≥ 0").optional(),
  adjustmentAmount: z.string().optional(),
  adjustmentNote: z.string().optional(),
  regularHours: z.coerce.number("Enter hours").min(0, "Must be ≥ 0").max(744, "Too many hours").optional(),
  overtimeHours: z.coerce.number("Enter hours").min(0, "Must be ≥ 0").optional(),
  weekendHours: z.coerce.number("Enter hours").min(0, "Must be ≥ 0").optional(),
});

const rulesSchema = z.object({
  overtimeMultiplier: z.coerce.number("Enter a number").positive("Must be > 0"),
  weekendMultiplier: z.coerce.number("Enter a number").positive("Must be > 0"),
});

export default function PayrollPage() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canRun = can("payroll", "run");
  const canViewPay = can("payroll", "view");

  const tabs = useMemo(() => {
    const t: { id: string; label: string }[] = [{ id: "payslips", label: "Payslips" }];
    if (!canRun && canViewPay) t.push({ id: "mypay", label: "My pay" });
    if (canRun) t.push({ id: "rules", label: "Rules" });
    return t;
  }, [canRun, canViewPay]);

  const [tab, setTab] = useState("payslips");
  const [period, setPeriod] = useState(currentPeriod());
  const [empFilter, setEmpFilter] = useState("");
  const [open, setOpen] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [detail, setDetail] = useState<Rec | null>(null);
  const [confirm, setConfirm] = useState<{ kind: "finalize" | "reopen"; row: Rec } | null>(null);
  const [runMonthConfirm, setRunMonthConfirm] = useState(false);

  const payrollConfig = useQuery({
    queryKey: ["payroll-config"],
    queryFn: ({ signal }) => apiFetch<Rec>(api.payroll("config"), { signal }),
    enabled: canViewPay || canRun,
  });
  const periodPolicy = (payrollConfig.data?.periodPolicy ?? null) as Rec | null;
  const finalizeHint = useMemo(() => {
    const mode = String(periodPolicy?.periodEndMode ?? "MONTH_END");
    if (/DAY_OF_MONTH/i.test(mode)) {
      const day = Number(periodPolicy?.periodEndDay ?? 25);
      return `Finalize on/after day ${day} of the period month (tenant config).`;
    }
    return "Finalize after the calendar month ends (tenant config).";
  }, [periodPolicy]);

  function canFinalizeRow(r: Rec): boolean {
    const d = (r.detailsJson ?? r.details) as Rec | undefined;
    if (typeof d?.canFinalize === "boolean") return d.canFinalize;
    // Client-side fallback from period policy + periodLabel
    const label = String(r.periodLabel ?? "");
    if (!/^\d{4}-\d{2}/.test(label)) return false;
    const [y, m] = label.split("-").map(Number);
    const today = new Date();
    const mode = String(periodPolicy?.periodEndMode ?? "MONTH_END");
    if (/DAY_OF_MONTH/i.test(mode)) {
      const day = Math.min(Number(periodPolicy?.periodEndDay ?? 25) || 25, 28);
      const close = new Date(y, m - 1, day);
      const start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
      return start.getTime() >= close.getTime();
    }
    const monthEnd = new Date(y, m, 0); // last day of period month
    const start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    return start.getTime() > monthEnd.getTime();
  }
  const [form, setForm] = useState({
    employeeId: "",
    periodLabel: currentPeriod(),
    useHrmsAttendance: true,
    absenceDays: "0",
    adjustmentAmount: "",
    adjustmentNote: "",
    regularHours: "160",
    overtimeHours: "0",
    weekendHours: "0",
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  const employees = useEmployeesLookup();
  const empName = useMemo(() => byId(employees.data, (e) => e.fullName || `#${e.id}`), [employees.data]);

  const refresh = () => void qc.invalidateQueries({ queryKey: ["payslips"] });

  // Warnings for previous-month not finalized
  const warnings = useQuery({
    queryKey: ["payroll-warnings", period],
    queryFn: ({ signal }) => apiFetch(api.payroll("warnings"), { query: { period }, signal }),
    enabled: canRun && !!period,
  });

  // Payslip summary for currency totals
  const payslipSummary = useQuery({
    queryKey: ["payslips-summary", period, empFilter],
    queryFn: ({ signal }) =>
      apiFetch(api.payroll("payslips"), {
        query: { size: "500", page: "1", ...(period ? { "filter.periodLabel": period } : {}), ...(empFilter ? { "filter.employeeId": empFilter } : {}) },
        signal,
      }),
    enabled: tab === "payslips" && !!period,
  });

  const currencyTotals = useMemo(() => {
    const rows = rowsOf<Rec>(payslipSummary.data);
    if (rows.length === 0) return [] as [string, number][];
    const map = new Map<string, number>();
    for (const r of rows) {
      const dj = (r.detailsJson ?? r.details) as Rec | undefined;
      const currency = String(dj?.currency ?? "");
      const net = Number(r.netAmount ?? dj?.net ?? 0);
      map.set(currency, (map.get(currency) ?? 0) + net);
    }
    return [...map.entries()] as [string, number][];
  }, [payslipSummary.data]);

  const calc = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.payroll("calculate"), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Payslip calculated (draft)");
      setOpen(false);
      setErr("");
      refresh();
    },
    onError: (e: Error) => setErr(e.message),
  });

  const runMonth = useMutation({
    mutationFn: (p: string) => apiFetch(api.payroll("calculate-month"), { method: "POST", query: { period: p } }),
    onSuccess: () => {
      toast.show("Month calculation queued for all employees");
      setRunMonthConfirm(false);
      refresh();
    },
    onError: (e: Error) => {
      toast.error(e);
      setRunMonthConfirm(false);
    },
  });

  const finalize = useMutation({
    mutationFn: (id: string | number) =>
      apiFetch(api.payroll(`payslips/${id}/finalize`), { method: "POST", body: { confirm: true } }),
    onSuccess: () => {
      toast.show("Payslip finalized");
      setConfirm(null);
      setDetail(null);
      refresh();
    },
    onError: (e: Error) => toast.error(e),
  });

  const reopen = useMutation({
    mutationFn: (id: string | number) => apiFetch(api.payroll(`payslips/${id}/reopen`), { method: "POST" }),
    onSuccess: () => {
      toast.show("Payslip reopened");
      setConfirm(null);
      setDetail(null);
      refresh();
    },
    onError: (e: Error) => toast.error(e),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = calcSchema.safeParse(form);
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      setErrors(next);
      return;
    }
    setErrors({});
    const { employeeId, periodLabel, useHrmsAttendance, absenceDays, adjustmentAmount, adjustmentNote, regularHours, overtimeHours, weekendHours } =
      parsed.data;
    const body: Record<string, unknown> = {
      employeeId: Number(employeeId),
      periodLabel,
      useHrmsAttendance,
    };
    if ((absenceDays ?? 0) > 0) body.absenceDays = absenceDays;
    const adjAmt = adjustmentAmount ? Number(adjustmentAmount) : 0;
    if (!Number.isNaN(adjAmt) && adjAmt !== 0) {
      body.adjustmentAmount = adjAmt;
      if (adjustmentNote) body.adjustmentNote = adjustmentNote;
    }
    if (!useHrmsAttendance) {
      body.regularHours = regularHours ?? 0;
      body.overtimeHours = overtimeHours ?? 0;
      body.weekendHours = weekendHours ?? 0;
    } else if (showAdvanced) {
      if ((overtimeHours ?? 0) > 0) body.overtimeHours = overtimeHours;
      if ((weekendHours ?? 0) > 0) body.weekendHours = weekendHours;
    }
    calc.mutate(body);
  }

  function openCalc() {
    setErr("");
    setErrors({});
    setShowAdvanced(false);
    setForm((f) => ({
      ...f,
      periodLabel: period || currentPeriod(),
      useHrmsAttendance: true,
      absenceDays: "0",
      adjustmentAmount: "",
      adjustmentNote: "",
      regularHours: "160",
      overtimeHours: "0",
      weekendHours: "0",
    }));
    setOpen(true);
  }

  const nameOf = (r: Rec) => {
    const d = r.details as Rec | undefined;
    return empName.get(String(r.employeeId)) ?? (d?.employeeName as string | undefined) ?? `#${r.employeeId}`;
  };
  const isFinal = (r: Rec) => /final/i.test(String(r.status));

  // Detail drawer — payslip details JSON (FR-PAY-15)
  const dj = (detail?.detailsJson ?? detail?.details) as Rec | undefined;
  const djDeductions = useMemo(() => {
    const raw = dj?.deductions;
    if (Array.isArray(raw) && raw.length > 0) return raw as Rec[];
    // Legacy payslips: synthesize lines from totals so the UI is never blank when money moved
    const lines: Rec[] = [];
    const absence = Number(dj?.absenceDeduction ?? 0);
    const late = Number(dj?.lateDeduction ?? 0);
    const total = Number(dj?.deduction ?? detail?.deductionAmount ?? 0);
    if (absence > 0) lines.push({ label: "unpaid_absence", type: "ABSENCE", amount: absence });
    if (late > 0) lines.push({ label: "late_penalty", type: "LATE", amount: late, lateCount: dj?.lateCount });
    if (lines.length === 0 && total > 0) lines.push({ label: "deductions", type: "TOTAL", amount: total });
    return lines;
  }, [dj, detail]);
  const djNotes = Array.isArray(dj?.notes) ? (dj.notes as unknown[]) : [];
  const djConsumed = dj?.consumedByType && typeof dj.consumedByType === "object" ? (dj.consumedByType as Record<string, unknown>) : null;

  const warningRows = useMemo(() => {
    if (!warnings.data) return [] as string[];
    if (Array.isArray(warnings.data)) {
      return (warnings.data as Rec[]).map((w) => str(w.message ?? w.detail ?? w));
    }
    const wd = warnings.data as Rec;
    if (wd.message) return [str(wd.message)];
    // Backend shape: { previousPeriod, unfinalizedCount, unfinalized: [{payslipId,employeeId,status}] }
    const count = Number(wd.unfinalizedCount ?? 0);
    if (count > 0) {
      const prev = str(wd.previousPeriod ?? "previous period");
      return [
        `${count} payslip(s) from ${prev} are still not finalized. Finalize them before closing the current run.`,
      ];
    }
    return [] as string[];
  }, [warnings.data]);

  return (
    <div className="stack">
      <PageHeader
        title="Payroll"
        subtitle={`Draft payslips use schedule, attendance and leave. ${finalizeHint}`}
        actions={
          canRun && tab === "payslips" ? (
            <div className="row" style={{ gap: 8, flexWrap: "wrap" }}>
              <button
                type="button"
                className="btn"
                disabled={!period}
                onClick={() => {
                  if (!period) return;
                  downloadFile(api.payroll("export.csv"), { period }).catch((e: Error) => toast.error(e));
                }}
              >
                Export CSV
              </button>
              <button
                type="button"
                className="btn"
                disabled={!period || runMonth.isPending}
                onClick={() => setRunMonthConfirm(true)}
              >
                {runMonth.isPending ? "Running…" : "Run month"}
              </button>
              <button type="button" className="btn primary" onClick={openCalc}>
                + Calculate payslip
              </button>
            </div>
          ) : null
        }
      />

      <Tabs tabs={tabs} value={tab} onChange={setTab} />

      {tab === "rules" ? <RulesPanel canRun={canRun} /> : null}
      {tab === "mypay" ? <MyPayPanel /> : null}

      {tab === "payslips" ? (
        <div className="card stack">
          {warningRows.length > 0 ? (
            <div className="notice warn">
              {warningRows.map((w, i) => (
                <div key={i}>{w}</div>
              ))}
            </div>
          ) : null}
          <div className="filters">
            <label className="field">
              <span>Period</span>
              <input type="month" value={period} onChange={(e) => setPeriod(e.target.value)} />
            </label>
            <label className="field">
              <span>Employee</span>
              <select value={empFilter} onChange={(e) => setEmpFilter(e.target.value)}>
                <option value="">Everyone</option>
                {(employees.data ?? []).map((e) => (
                  <option key={e.id} value={e.id}>
                    {e.fullName}
                  </option>
                ))}
              </select>
            </label>
            {period || empFilter ? (
              <button
                type="button"
                className="btn"
                onClick={() => {
                  setPeriod("");
                  setEmpFilter("");
                }}
              >
                All periods
              </button>
            ) : null}
          </div>
          <DataTable<Rec>
            queryKey="payslips"
            path={api.payroll("payslips")}
            itemSchema={rowSchema as never}
            search={false}
            filters={{ periodLabel: period, employeeId: empFilter }}
            emptyText="No payslips for this selection"
            rowKey={(r) => String(r.id)}
            onRowClick={(r) => setDetail(r)}
            columns={[
              { key: "employeeId", header: "Employee", render: (r) => <b>{nameOf(r)}</b> },
              { key: "periodLabel", header: "Period", className: "mono" },
              {
                key: "basePay",
                header: "Base / Net",
                className: "n",
                render: (r) => {
                  const d = (r.detailsJson ?? r.details) as Rec | undefined;
                  return (
                    <span>
                      {fmtMoney(d?.basePay ?? r.grossAmount)}{" "}
                      <span className="muted">/ {fmtMoney(d?.net ?? r.netAmount)}</span>
                    </span>
                  );
                },
              },
              { key: "deductionAmount", header: "Deductions", className: "n", render: (r) => fmtMoney(r.deductionAmount) },
              { key: "netAmount", header: "Net", className: "n", render: (r) => <b>{fmtMoney(r.netAmount)}</b> },
              { key: "status", header: "Status", render: (r) => <StatusPill status={r.status} /> },
              {
                key: "actions",
                header: "",
                render: (r) =>
                  canRun ? (
                    <div className="row" style={{ flexWrap: "nowrap", justifyContent: "flex-end" }} onClick={(e) => e.stopPropagation()}>
                      {isFinal(r) ? (
                        <button
                          type="button"
                          className="btn sm"
                          disabled={reopen.isPending}
                          onClick={() => setConfirm({ kind: "reopen", row: r })}
                        >
                          Reopen
                        </button>
                      ) : (
                        <button
                          type="button"
                          className="btn sm primary"
                          disabled={finalize.isPending || !canFinalizeRow(r)}
                          title={!canFinalizeRow(r) ? finalizeHint : "Finalize payslip"}
                          onClick={() => setConfirm({ kind: "finalize", row: r })}
                        >
                          Finalize
                        </button>
                      )}
                    </div>
                  ) : null,
              },
            ]}
          />
          {currencyTotals.length > 1 ? (
            <div className="row" style={{ gap: 16, justifyContent: "flex-end", borderTop: "1px solid var(--border)", paddingTop: 12 }}>
              <span className="small muted">Period totals (net):</span>
              {currencyTotals.map(([cur, total]) => (
                <span key={cur} className="num">
                  <b>{fmtMoney(total, cur || undefined)}</b>
                </span>
              ))}
            </div>
          ) : null}
        </div>
      ) : null}

      {/* Calculate payslip modal */}
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Calculate payslip"
        subtitle="Idempotent per employee and period — recalculating updates the draft."
      >
        <form className="stack" onSubmit={submit} noValidate>
          <div className="form-grid">
            <FormField label="Employee" required error={errors.employeeId}>
              <select value={form.employeeId} onChange={(e) => setForm({ ...form, employeeId: e.target.value })}>
                <option value="">Select…</option>
                {(employees.data ?? []).map((e) => (
                  <option key={e.id} value={e.id}>
                    {e.fullName} {e.employeeCode ? `(${e.employeeCode})` : ""}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Period" required error={errors.periodLabel}>
              <input type="month" value={form.periodLabel} onChange={(e) => setForm({ ...form, periodLabel: e.target.value })} />
            </FormField>

            <div className="checks full">
              <label>
                <input
                  type="checkbox"
                  checked={form.useHrmsAttendance}
                  onChange={(e) => setForm({ ...form, useHrmsAttendance: e.target.checked })}
                />{" "}
                Use HRMS attendance (schedule / clock-in data)
              </label>
            </div>

            {form.useHrmsAttendance ? (
              <>
                <FormField
                  label="Absence days (override)"
                  error={errors.absenceDays}
                  hint="Leave blank or 0 to derive from HRMS attendance."
                >
                  <input
                    type="number"
                    min={0}
                    step="0.5"
                    value={form.absenceDays}
                    onChange={(e) => setForm({ ...form, absenceDays: e.target.value })}
                  />
                </FormField>

                <div className="full">
                  <button
                    type="button"
                    className="btn sm"
                    onClick={() => setShowAdvanced((v) => !v)}
                    style={{ marginBottom: 4 }}
                  >
                    {showAdvanced ? "Hide" : "Show"} advanced hour overrides
                  </button>
                  {showAdvanced ? (
                    <div className="form-grid" style={{ marginTop: 8 }}>
                      <FormField label="Overtime hours (override)" error={errors.overtimeHours} hint="0 = use attendance data.">
                        <input
                          type="number"
                          min={0}
                          step="0.25"
                          value={form.overtimeHours}
                          onChange={(e) => setForm({ ...form, overtimeHours: e.target.value })}
                        />
                      </FormField>
                      <FormField label="Weekend hours (override)" error={errors.weekendHours}>
                        <input
                          type="number"
                          min={0}
                          step="0.25"
                          value={form.weekendHours}
                          onChange={(e) => setForm({ ...form, weekendHours: e.target.value })}
                        />
                      </FormField>
                    </div>
                  ) : null}
                </div>
              </>
            ) : (
              <>
                <FormField label="Regular hours" required error={errors.regularHours}>
                  <input
                    type="number"
                    min={0}
                    step="0.25"
                    value={form.regularHours}
                    onChange={(e) => setForm({ ...form, regularHours: e.target.value })}
                  />
                </FormField>
                <FormField label="Overtime hours" error={errors.overtimeHours}>
                  <input
                    type="number"
                    min={0}
                    step="0.25"
                    value={form.overtimeHours}
                    onChange={(e) => setForm({ ...form, overtimeHours: e.target.value })}
                  />
                </FormField>
                <FormField label="Weekend hours" error={errors.weekendHours}>
                  <input
                    type="number"
                    min={0}
                    step="0.25"
                    value={form.weekendHours}
                    onChange={(e) => setForm({ ...form, weekendHours: e.target.value })}
                  />
                </FormField>
                <FormField label="Absence days" error={errors.absenceDays} hint="Covered via the configured leave cover order.">
                  <input
                    type="number"
                    min={0}
                    step="0.5"
                    value={form.absenceDays}
                    onChange={(e) => setForm({ ...form, absenceDays: e.target.value })}
                  />
                </FormField>
              </>
            )}

            {/* Adjustment (FR-PAY-08) */}
            <FormField label="Adjustment amount" error={errors.adjustmentAmount} hint="Positive = bonus, negative = deduction.">
              <input
                type="number"
                step="0.01"
                value={form.adjustmentAmount}
                placeholder="0"
                onChange={(e) => setForm({ ...form, adjustmentAmount: e.target.value })}
              />
            </FormField>
            {form.adjustmentAmount && form.adjustmentAmount !== "0" ? (
              <FormField label="Adjustment note" error={errors.adjustmentNote}>
                <input
                  type="text"
                  value={form.adjustmentNote}
                  placeholder="Reason for adjustment…"
                  onChange={(e) => setForm({ ...form, adjustmentNote: e.target.value })}
                />
              </FormField>
            ) : null}
          </div>
          {err ? <div className="notice bad">{err}</div> : null}
          <div className="modal-foot">
            <button type="button" className="btn" onClick={() => setOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={calc.isPending}>
              {calc.isPending ? "Calculating…" : "Calculate"}
            </button>
          </div>
        </form>
      </Modal>

      {/* Detail drawer (FR-PAY-15) */}
      <Modal
        open={!!detail}
        onClose={() => setDetail(null)}
        title={detail ? `${nameOf(detail)} · ${str(detail.periodLabel)}` : ""}
        subtitle={detail ? `Created ${fmtDateTime(detail.createdAt)}` : undefined}
        footer={
          detail && canRun ? (
            isFinal(detail) ? (
              <button type="button" className="btn" disabled={reopen.isPending} onClick={() => reopen.mutate(detail.id as number)}>
                Reopen
              </button>
            ) : (
              <button
                type="button"
                className="btn primary"
                disabled={finalize.isPending || !canFinalizeRow(detail)}
                title={!canFinalizeRow(detail) ? finalizeHint : "Finalize payslip"}
                onClick={() => setConfirm({ kind: "finalize", row: detail })}
              >
                Finalize
              </button>
            )
          ) : undefined
        }
      >
        {detail ? (
          <div className="stack">
            {/* Top stats */}
            <div className="grid g3">
              <div className="stat">
                <span className="s">Base pay</span>
                <span className="v">{fmtMoney(dj?.basePay ?? detail.grossAmount, String(dj?.currency ?? ""))}</span>
              </div>
              <div className="stat">
                <span className="s">Deduction</span>
                <span className="v">{fmtMoney(dj?.deduction ?? detail.deductionAmount)}</span>
              </div>
              <div className="stat">
                <span className="s">Net pay</span>
                <span className="v">
                  <b>{fmtMoney(dj?.net ?? detail.netAmount, String(dj?.currency ?? ""))}</b>
                </span>
              </div>
            </div>

            {/* KV breakdown */}
            <div className="kv">
              {dj?.perDayRate !== undefined ? (
                <div>
                  <div className="label">Per-day rate</div>
                  {fmtMoney(dj.perDayRate)}
                </div>
              ) : null}
              {dj?.hoursWorked !== undefined ? (
                <div>
                  <div className="label">Hours worked</div>
                  {fmtNumber(dj.hoursWorked)}
                </div>
              ) : null}
              {(dj?.workingDays ?? detail.workingDays) !== undefined ? (
                <div>
                  <div className="label">Working days</div>
                  {fmtNumber(dj?.workingDays ?? detail.workingDays)}
                </div>
              ) : null}
              {dj?.daysEmployed !== undefined ? (
                <div>
                  <div className="label">Days employed</div>
                  {fmtNumber(dj.daysEmployed)}
                </div>
              ) : null}
              {(dj?.absentDays ?? detail.absentDays) !== undefined ? (
                <div>
                  <div className="label">Absent days</div>
                  {fmtNumber(dj?.absentDays ?? detail.absentDays)}
                </div>
              ) : null}
              {dj?.lateCount !== undefined ? (
                <div>
                  <div className="label">Late occurrences</div>
                  {fmtNumber(dj.lateCount, 0)}
                </div>
              ) : null}
              {(dj?.coveredDays !== undefined || djConsumed) ? (
                <div>
                  <div className="label">Covered by leave</div>
                  {fmtNumber(
                    dj?.coveredDays ??
                      Object.values(djConsumed ?? {}).reduce((a, v) => a + Number(v || 0), 0),
                  )}
                </div>
              ) : null}
              {(dj?.uncoveredDays ?? dj?.uncoveredAbsentDays) !== undefined ? (
                <div>
                  <div className="label">Uncovered days</div>
                  {fmtNumber(dj?.uncoveredDays ?? dj?.uncoveredAbsentDays)}
                </div>
              ) : null}
              {dj?.unpaidLeaveDays !== undefined ? (
                <div>
                  <div className="label">Unpaid leave days</div>
                  {fmtNumber(dj.unpaidLeaveDays)}
                </div>
              ) : null}
              {((dj?.adjustment ?? dj?.adjustmentAmount) !== undefined &&
                Number(dj?.adjustment ?? dj?.adjustmentAmount) !== 0) ? (
                <div>
                  <div className="label">Adjustment</div>
                  {fmtMoney(dj?.adjustment ?? dj?.adjustmentAmount)}
                </div>
              ) : null}
              {dj?.payMode ? (
                <div>
                  <div className="label">Pay mode</div>
                  {str(dj.payMode)}
                </div>
              ) : null}
              {dj?.periodCloseDate ? (
                <div>
                  <div className="label">Period closes</div>
                  {str(dj.periodCloseDate)}
                </div>
              ) : null}
              {dj?.missingCheckoutCount !== undefined && Number(dj.missingCheckoutCount) > 0 ? (
                <div>
                  <div className="label">Missing checkouts</div>
                  {fmtNumber(dj.missingCheckoutCount, 0)}
                </div>
              ) : null}
              <div>
                <div className="label">Status</div>
                <StatusPill status={detail.status} />
              </div>
            </div>

            {/* Leave consumed by type */}
            {djConsumed && Object.keys(djConsumed).length > 0 ? (
              <div>
                <div className="label" style={{ marginBottom: 6 }}>Leave cover used</div>
                <div className="row" style={{ flexWrap: "wrap", gap: 8 }}>
                  {Object.entries(djConsumed).map(([k, v]) => (
                    <span key={k} className="pill">
                      {k}: {fmtNumber(v)}d
                    </span>
                  ))}
                </div>
              </div>
            ) : null}

            {/* Deductions breakdown */}
            {djDeductions.length > 0 ? (
              <div className="tbl-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Deduction</th>
                      <th className="n">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {djDeductions.map((d, i) => (
                      <tr key={i}>
                        <td>
                          {str(d.label ?? d.name ?? d.key ?? d.type)}
                          {d.lateCount != null ? (
                            <span className="muted small"> · {fmtNumber(d.lateCount, 0)}×</span>
                          ) : null}
                          {d.days != null ? (
                            <span className="muted small"> · {fmtNumber(d.days)}d</span>
                          ) : null}
                        </td>
                        <td className="n">{fmtMoney(d.amount ?? d.value)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="muted small">
                No money deductions. Absences may be fully covered by leave balance
                {dj?.absentDays != null ? ` (absent ${fmtNumber(dj.absentDays)}d)` : ""}.
              </p>
            )}

            {/* Notes */}
            {djNotes.length > 0 ? (
              <div>
                <div className="label" style={{ marginBottom: 4 }}>Notes</div>
                <ul style={{ margin: 0, paddingLeft: 18 }}>
                  {djNotes.map((n, i) => (
                    <li key={i} className="small">{str(n)}</li>
                  ))}
                </ul>
              </div>
            ) : null}
          </div>
        ) : null}
      </Modal>

      {/* Finalize / reopen confirm */}
      <ConfirmModal
        open={!!confirm}
        title={confirm?.kind === "reopen" ? "Reopen payslip?" : "Finalize payslip?"}
        confirmLabel={confirm?.kind === "reopen" ? "Reopen" : "Finalize"}
        busy={finalize.isPending || reopen.isPending}
        onCancel={() => setConfirm(null)}
        onConfirm={() => {
          if (!confirm) return;
          const id = confirm.row.id as number;
          if (confirm.kind === "reopen") reopen.mutate(id);
          else finalize.mutate(id);
        }}
      >
        {confirm?.kind === "reopen"
          ? "Reopen this finalized payslip for recalculation?"
          : `Finalize ${confirm ? nameOf(confirm.row) : ""} · ${confirm ? String(confirm.row.periodLabel ?? "") : ""}? ${finalizeHint} Leave balances will be adjusted for absence cover and this payslip will be locked.`}
      </ConfirmModal>

      {/* Run month confirm */}
      <ConfirmModal
        open={runMonthConfirm}
        title="Run payroll for entire month?"
        confirmLabel="Run month"
        busy={runMonth.isPending}
        onCancel={() => setRunMonthConfirm(false)}
        onConfirm={() => {
          if (period) runMonth.mutate(period);
        }}
      >
        {`Calculate (or recalculate) draft payslips for all employees in ${period}. Existing drafts will be updated; finalized payslips are not affected.`}
      </ConfirmModal>
    </div>
  );
}

// ── My Pay panel (FR-SAL-03 self-view) ─────────────────────────────────────
function MyPayPanel() {
  const myCurrent = useQuery({
    queryKey: ["payroll-me-current"],
    queryFn: ({ signal }) => apiFetch<Rec>(api.payroll("me/current"), { signal }),
  });

  const data = myCurrent.data;
  const payslips = rowsOf<Rec>(data?.payslips);

  return (
    <section className="card stack">
      <h2>My pay</h2>
      <ErrorNote error={myCurrent.error} />
      {myCurrent.isLoading ? <p className="muted">Loading…</p> : null}
      {data ? (
        <>
          <div className="grid g3">
            <div className="stat">
              <span className="s">Current pay</span>
              <span className="v">{fmtMoney(data.currentPay)}</span>
            </div>
            <div className="stat">
              <span className="s">MTD estimate</span>
              <span className="v">
                {fmtMoney(
                  (data.mtdEstimate as Rec | undefined)?.netAmount ?? data.mtdEstimate,
                )}
              </span>
            </div>
          </div>
          {payslips.length > 0 ? (
            <div className="tbl-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Period</th>
                    <th className="n">Net</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {payslips.map((p, i) => (
                    <tr key={String(p.id ?? i)}>
                      <td className="mono">{str(p.periodLabel)}</td>
                      <td className="n">{fmtMoney(p.netAmount)}</td>
                      <td>
                        <StatusPill status={p.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="muted">No finalized payslips yet.</p>
          )}
        </>
      ) : null}
    </section>
  );
}

// ── Rules panel ─────────────────────────────────────────────────────────────
function RulesPanel({ canRun }: { canRun: boolean }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({
    overtimeMultiplier: "1.5",
    weekendMultiplier: "2",
    periodEndMode: "MONTH_END",
    periodEndDay: "31",
    latePenaltyPerOccurrence: "200",
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");
  const [loaded, setLoaded] = useState(false);

  const config = useQuery({
    queryKey: ["payroll-config"],
    queryFn: ({ signal }) => apiFetch<Rec>(api.payroll("config"), { signal }),
  });

  if (config.data && !loaded) {
    const rules = Array.isArray(config.data.deductionRules) ? (config.data.deductionRules as Rec[]) : [];
    const cfg = (key: string, fallback: string) => {
      const hit = rules.find((r) => String(r.key) === key);
      return hit?.value != null ? String(hit.value) : fallback;
    };
    const pp = (config.data.periodPolicy ?? {}) as Rec;
    setForm({
      overtimeMultiplier: String(config.data.overtimeMultiplier ?? "1.5"),
      weekendMultiplier: String(config.data.weekendMultiplier ?? "2"),
      periodEndMode: String(pp.periodEndMode ?? cfg("periodEndMode", "MONTH_END")),
      periodEndDay: String(pp.periodEndDay ?? cfg("periodEndDay", "31")),
      latePenaltyPerOccurrence: cfg("latePenaltyPerOccurrence", "0"),
    });
    setLoaded(true);
  }

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.payroll("config"), { method: "PUT", body }),
    onSuccess: () => {
      toast.show("Payroll rules saved");
      setErr("");
      void qc.invalidateQueries({ queryKey: ["payroll-config"] });
    },
    onError: (e: Error) => setErr(e.message),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = rulesSchema.safeParse({
      overtimeMultiplier: form.overtimeMultiplier,
      weekendMultiplier: form.weekendMultiplier,
    });
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setErrors(next);
    }
    setErrors({});
    const existing = Array.isArray(config.data?.deductionRules) ? ([...(config.data!.deductionRules as Rec[])] as Rec[]) : [];
    const upsert = (key: string, value: string | number) => {
      const i = existing.findIndex((r) => String(r.type).toUpperCase() === "CONFIG" && String(r.key) === key);
      const row = { type: "CONFIG", key, value };
      if (i >= 0) existing[i] = row;
      else existing.push(row);
    };
    upsert("periodEndMode", form.periodEndMode);
    upsert("periodEndDay", Number(form.periodEndDay) || 31);
    upsert("latePenaltyPerOccurrence", Number(form.latePenaltyPerOccurrence) || 0);
    save.mutate({
      overtimeMultiplier: parsed.data.overtimeMultiplier,
      weekendMultiplier: parsed.data.weekendMultiplier,
      deductionRules: existing,
    });
  }

  const ruleRows = Array.isArray(config.data?.deductionRules) ? (config.data!.deductionRules as Rec[]) : [];

  return (
    <section className="card stack">
      <h2>Payroll rules</h2>
      <p className="muted small">
        Tenant-pack config drives absence cover, late penalties, and when payslips may be finalized. Same engine for every business.
      </p>
      <ErrorNote error={config.error} />
      {!canRun ? <p className="muted">You need payroll:run to change these rules.</p> : null}
      <form className="stack" onSubmit={submit} noValidate>
        <div className="form-grid">
          <FormField label="Overtime multiplier" required error={errors.overtimeMultiplier}>
            <input
              type="number"
              min={0}
              step="0.01"
              value={form.overtimeMultiplier}
              disabled={!canRun || config.isLoading}
              onChange={(e) => setForm({ ...form, overtimeMultiplier: e.target.value })}
            />
          </FormField>
          <FormField label="Weekend multiplier" required error={errors.weekendMultiplier}>
            <input
              type="number"
              min={0}
              step="0.01"
              value={form.weekendMultiplier}
              disabled={!canRun || config.isLoading}
              onChange={(e) => setForm({ ...form, weekendMultiplier: e.target.value })}
            />
          </FormField>
          <FormField label="Period end mode" hint="MONTH_END (Qii) or DAY_OF_MONTH (e.g. day 20/25).">
            <select
              value={form.periodEndMode}
              disabled={!canRun || config.isLoading}
              onChange={(e) => setForm({ ...form, periodEndMode: e.target.value })}
            >
              <option value="MONTH_END">MONTH_END</option>
              <option value="DAY_OF_MONTH">DAY_OF_MONTH</option>
            </select>
          </FormField>
          <FormField label="Period end day" hint="Used when mode is DAY_OF_MONTH (1–28).">
            <input
              type="number"
              min={1}
              max={28}
              value={form.periodEndDay}
              disabled={!canRun || config.isLoading || form.periodEndMode === "MONTH_END"}
              onChange={(e) => setForm({ ...form, periodEndDay: e.target.value })}
            />
          </FormField>
          <FormField label="Late penalty / occurrence" hint="Money deducted per L/LATE attendance day. 0 = track only.">
            <input
              type="number"
              min={0}
              step="0.01"
              value={form.latePenaltyPerOccurrence}
              disabled={!canRun || config.isLoading}
              onChange={(e) => setForm({ ...form, latePenaltyPerOccurrence: e.target.value })}
            />
          </FormField>
        </div>
        {ruleRows.length > 0 ? (
          <div className="tbl-wrap">
            <table>
              <thead>
                <tr>
                  <th>Rule</th>
                  <th>Type</th>
                  <th>Value</th>
                </tr>
              </thead>
              <tbody>
                {ruleRows.map((r, i) => (
                  <tr key={i}>
                    <td className="mono">{str(r.key ?? r.label ?? "—")}</td>
                    <td>{str(r.type)}</td>
                    <td className="mono">{typeof r.value === "object" ? JSON.stringify(r.value) : str(r.value ?? r.amount ?? r.percent)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
        {err ? <div className="notice bad">{err}</div> : null}
        <div>
          <button type="submit" className="btn primary" disabled={!canRun || save.isPending || config.isLoading}>
            {save.isPending ? "Saving…" : "Save rules"}
          </button>
        </div>
      </form>
    </section>
  );
}
