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
import { api, apiFetch } from "@/lib/api/client";
import { byId, useEmployeesLookup, type Rec } from "@/lib/api/lookups";
import { currentPeriod, fmtDateTime, fmtMoney, fmtNumber, str } from "@/lib/format";

const rowSchema = z.object({ id: z.union([z.number(), z.string()]) }).passthrough();

const TABS = [
  { id: "payslips", label: "Payslips" },
  { id: "rules", label: "Rules" },
];

const calcSchema = z.object({
  employeeId: z.string().min(1, "Choose an employee"),
  periodLabel: z.string().regex(/^\d{4}-(0[1-9]|1[0-2])$/, "Use YYYY-MM"),
  regularHours: z.coerce.number("Enter hours").min(0, "Must be ≥ 0").max(744, "Too many hours"),
  overtimeHours: z.coerce.number("Enter hours").min(0, "Must be ≥ 0"),
  weekendHours: z.coerce.number("Enter hours").min(0, "Must be ≥ 0"),
  absenceDays: z.coerce.number("Enter days").min(0, "Must be ≥ 0"),
  useHrmsAttendance: z.boolean(),
});

const rulesSchema = z.object({
  overtimeMultiplier: z.coerce.number("Enter a number").positive("Must be > 0"),
  weekendMultiplier: z.coerce.number("Enter a number").positive("Must be > 0"),
});

export default function PayrollPage() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  // Must match the backend: calculate / finalize / reopen require payroll:run.
  const canRun = can("payroll", "run");

  const [tab, setTab] = useState("payslips");
  const [period, setPeriod] = useState(currentPeriod());
  const [empFilter, setEmpFilter] = useState("");
  const [open, setOpen] = useState(false);
  const [detail, setDetail] = useState<Rec | null>(null);
  const [confirm, setConfirm] = useState<{ kind: "finalize" | "reopen"; row: Rec } | null>(null);
  const [form, setForm] = useState({
    employeeId: "",
    periodLabel: currentPeriod(),
    regularHours: "160",
    overtimeHours: "0",
    weekendHours: "0",
    absenceDays: "0",
    useHrmsAttendance: true,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  const employees = useEmployeesLookup();
  const empName = useMemo(() => byId(employees.data, (e) => e.fullName || `#${e.id}`), [employees.data]);

  const refresh = () => void qc.invalidateQueries({ queryKey: ["payslips"] });

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
  const finalize = useMutation({
    mutationFn: (id: string | number) => apiFetch(api.payroll(`payslips/${id}/finalize`), { method: "POST" }),
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
    const body: Record<string, unknown> = {
      employeeId: Number(parsed.data.employeeId),
      periodLabel: parsed.data.periodLabel,
      useHrmsAttendance: parsed.data.useHrmsAttendance,
      overtimeHours: parsed.data.overtimeHours,
      weekendHours: parsed.data.weekendHours,
      absenceDays: parsed.data.absenceDays,
    };
    if (parsed.data.useHrmsAttendance) {
      body.regularHours = 0;
    } else {
      body.regularHours = parsed.data.regularHours;
    }
    calc.mutate(body);
  }

  const nameOf = (r: Rec) => {
    const d = r.details as Rec | undefined;
    return empName.get(String(r.employeeId)) ?? (d?.employeeName as string | undefined) ?? `#${r.employeeId}`;
  };
  const isFinal = (r: Rec) => /final/i.test(String(r.status));

  const detailRules = (detail?.details as Rec | undefined)?.deductions;

  return (
    <div className="stack">
      <PageHeader
        title="Payroll"
        subtitle="Draft payslips are calculated from hours and the tenant payroll config; finalize to lock them."
        actions={
          canRun && tab === "payslips" ? (
            <button
              type="button"
              className="btn primary"
              onClick={() => {
                setErr("");
                setErrors({});
                setForm((f) => ({
                  ...f,
                  periodLabel: period || currentPeriod(),
                  useHrmsAttendance: true,
                  regularHours: "160",
                  overtimeHours: "0",
                  weekendHours: "0",
                  absenceDays: "0",
                }));
                setOpen(true);
              }}
            >
              + Calculate payslip
            </button>
          ) : null
        }
      />

      <Tabs tabs={TABS} value={tab} onChange={setTab} />

      {tab === "rules" ? <RulesPanel canRun={canRun} /> : null}

      {tab === "payslips" ? (
        <div className="card stack">
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
              { key: "hours", header: "Hours (reg / OT / wknd)", className: "n", render: (r) => `${fmtNumber(r.regularHours)} / ${fmtNumber(r.overtimeHours)} / ${fmtNumber(r.weekendHours)}` },
              { key: "grossAmount", header: "Gross", className: "n", render: (r) => fmtMoney(r.grossAmount) },
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
                          disabled={finalize.isPending}
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
        </div>
      ) : null}

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
                Use HRMS attendance hours
              </label>
            </div>
            {!form.useHrmsAttendance ? (
              <>
                <FormField label="Regular hours" error={errors.regularHours}>
                  <input type="number" min={0} step="0.25" value={form.regularHours} onChange={(e) => setForm({ ...form, regularHours: e.target.value })} />
                </FormField>
                <FormField label="Overtime hours" error={errors.overtimeHours}>
                  <input type="number" min={0} step="0.25" value={form.overtimeHours} onChange={(e) => setForm({ ...form, overtimeHours: e.target.value })} />
                </FormField>
                <FormField label="Weekend hours" error={errors.weekendHours}>
                  <input type="number" min={0} step="0.25" value={form.weekendHours} onChange={(e) => setForm({ ...form, weekendHours: e.target.value })} />
                </FormField>
                <FormField label="Absence days" error={errors.absenceDays} hint="Covered via the configured leave cover order.">
                  <input type="number" min={0} step="0.5" value={form.absenceDays} onChange={(e) => setForm({ ...form, absenceDays: e.target.value })} />
                </FormField>
              </>
            ) : (
              <>
                <FormField label="Overtime hours (override)" error={errors.overtimeHours} hint="Optional — leave 0 to use attendance only for regular hours.">
                  <input type="number" min={0} step="0.25" value={form.overtimeHours} onChange={(e) => setForm({ ...form, overtimeHours: e.target.value })} />
                </FormField>
                <FormField label="Weekend hours (override)" error={errors.weekendHours}>
                  <input type="number" min={0} step="0.25" value={form.weekendHours} onChange={(e) => setForm({ ...form, weekendHours: e.target.value })} />
                </FormField>
              </>
            )}
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
              <button type="button" className="btn primary" disabled={finalize.isPending} onClick={() => finalize.mutate(detail.id as number)}>
                Finalize
              </button>
            )
          ) : undefined
        }
      >
        {detail ? (
          <div className="stack">
            <div className="grid g3">
              <div className="stat">
                <span className="s">Gross</span>
                <span className="v">{fmtMoney(detail.grossAmount)}</span>
              </div>
              <div className="stat">
                <span className="s">Deductions</span>
                <span className="v">{fmtMoney(detail.deductionAmount)}</span>
              </div>
              <div className="stat">
                <span className="s">Net</span>
                <span className="v">{fmtMoney(detail.netAmount)}</span>
              </div>
            </div>
            <div className="kv">
              <div>
                <div className="label">Regular hours</div>
                {fmtNumber(detail.regularHours)}
              </div>
              <div>
                <div className="label">Overtime hours</div>
                {fmtNumber(detail.overtimeHours)}
              </div>
              <div>
                <div className="label">Weekend hours</div>
                {fmtNumber(detail.weekendHours)}
              </div>
              <div>
                <div className="label">Status</div>
                <StatusPill status={detail.status} />
              </div>
            </div>
            {Array.isArray(detailRules) && detailRules.length > 0 ? (
              <div className="tbl-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Deduction</th>
                      <th className="n">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(detailRules as Rec[]).map((d, i) => (
                      <tr key={i}>
                        <td>{str(d.label ?? d.name ?? d.key ?? d.type)}</td>
                        <td className="n">{fmtMoney(d.amount ?? d.value)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="muted small">No deductions applied.</p>
            )}
          </div>
        ) : null}
      </Modal>

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
          : `Finalize ${confirm ? nameOf(confirm.row) : ""} · ${confirm ? String(confirm.row.periodLabel ?? "") : ""}? This locks the payslip.`}
      </ConfirmModal>
    </div>
  );
}

function RulesPanel({ canRun }: { canRun: boolean }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({ overtimeMultiplier: "1.5", weekendMultiplier: "2" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");
  const [loaded, setLoaded] = useState(false);

  const config = useQuery({
    queryKey: ["payroll-config"],
    queryFn: ({ signal }) => apiFetch<Rec>(api.payroll("config"), { signal }),
  });

  if (config.data && !loaded) {
    setForm({
      overtimeMultiplier: String(config.data.overtimeMultiplier ?? "1.5"),
      weekendMultiplier: String(config.data.weekendMultiplier ?? "2"),
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
    const parsed = rulesSchema.safeParse(form);
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setErrors(next);
    }
    setErrors({});
    save.mutate({
      overtimeMultiplier: parsed.data.overtimeMultiplier,
      weekendMultiplier: parsed.data.weekendMultiplier,
    });
  }

  return (
    <section className="card stack">
      <h2>Payroll rules</h2>
      <p className="muted small">Tenant overtime and weekend multipliers used when calculating payslips.</p>
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
        </div>
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
