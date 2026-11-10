"use client";

import { Suspense, useEffect, useMemo, useState, type FormEvent } from "react";
import { useSearchParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/components/auth/AuthProvider";
import { PageHeader } from "@/components/ui/PageHeader";
import { Modal } from "@/components/ui/Modal";
import { FormField } from "@/components/ui/FormField";
import { ErrorNote } from "@/components/ui/QueryState";
import { StatusPill } from "@/components/ui/StatusPill";
import { Tabs } from "@/components/ui/Tabs";
import { useToast } from "@/components/ui/Toast";
import { PayslipView, periodLabel } from "@/components/payroll/PayslipView";
import { api, apiFetch, downloadFile } from "@/lib/api/client";
import { rowsOf, useLeaveTypeCatalogue, type Rec } from "@/lib/api/lookups";
import { currentPeriod, fmtDateTime, fmtMoney, fmtNumber, str } from "@/lib/format";

type Run = Rec & {
  period: string;
  exists: boolean;
  status: string | null;
  revision?: number;
  totals?: Rec[];
  warnings?: string[];
  finalizeBlockers?: string[];
  reopenBlockers?: string[];
  leaveToTake?: Rec[];
  operations?: Rec[];
};

const IN_FLIGHT = new Set(["FINALIZING", "REOPENING"]);

function previousPeriod(p: string): string {
  const [y, m] = p.split("-").map(Number);
  const d = new Date(Date.UTC(y, m - 2, 1));
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}`;
}

export default function PayrollPage() {
  return (
    <Suspense fallback={<div className="empty">Loading…</div>}>
      <PayrollInner />
    </Suspense>
  );
}

function PayrollInner() {
  const { can } = useAuth();
  const params = useSearchParams();
  const canRun = can("payroll", "run");
  const canView = can("payroll", "view");
  const canExport = can("payroll", "export");

  const tabs = useMemo(() => {
    const t: { id: string; label: string }[] = [];
    if (canRun) t.push({ id: "run", label: "Payroll run" });
    if (canView) t.push({ id: "mypay", label: "My pay" });
    if (canRun) t.push({ id: "rules", label: "Rules" });
    return t;
  }, [canRun, canView]);

  const [tab, setTab] = useState(params.get("tab") ?? (canRun ? "run" : "mypay"));
  useEffect(() => {
    if (tabs.length > 0 && !tabs.some((t) => t.id === tab)) setTab(tabs[0].id);
  }, [tabs, tab]);

  return (
    <div className="stack">
      <PageHeader
        title="Payroll"
        subtitle="Monthly pay from salary, schedule, attendance and leave. Gross pay and attendance-based deductions only."
      />
      {tabs.length > 1 ? <Tabs tabs={tabs} value={tab} onChange={setTab} /> : null}
      {tab === "run" && canRun ? <RunPanel canExport={canExport} /> : null}
      {tab === "mypay" && canView ? <MyPayPanel initialPeriod={params.get("period")} /> : null}
      {tab === "rules" && canRun ? <RulesPanel /> : null}
    </div>
  );
}

// ───────────────────────────────────────────── Payroll run (admin)

function RunPanel({ canExport }: { canExport: boolean }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [period, setPeriod] = useState(previousPeriod(currentPeriod()));
  const [q, setQ] = useState("");
  const [exceptionsOnly, setExceptionsOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<number | null>(null);
  const [finalizeOpen, setFinalizeOpen] = useState(false);
  const [reopenOpen, setReopenOpen] = useState(false);

  const run = useQuery({
    queryKey: ["payroll-run", period],
    queryFn: ({ signal }) => apiFetch<Run>(api.payroll(`runs/${period}`), { signal }),
    enabled: /^\d{4}-\d{2}$/.test(period),
    refetchInterval: (query) => (IN_FLIGHT.has(String(query.state.data?.status ?? "")) ? 2000 : false),
  });
  const r = run.data;

  const lines = useQuery({
    queryKey: ["payroll-lines", period, q, exceptionsOnly, page, r?.revision, r?.status],
    queryFn: ({ signal }) =>
      apiFetch<Rec>(api.payroll(`runs/${period}/lines`), {
        query: { q: q || undefined, exceptionsOnly, page, size: 50 },
        signal,
      }),
    enabled: !!r?.exists,
  });
  const rows = rowsOf<Rec>(lines.data);
  const totalPages = Number((lines.data as Rec | undefined)?.totalPages ?? 1);

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ["payroll-run", period] });
    void qc.invalidateQueries({ queryKey: ["payroll-lines", period] });
    void qc.invalidateQueries({ queryKey: ["payroll-runs"] });
  };

  const calculate = useMutation({
    mutationFn: () => apiFetch<Run>(api.payroll(`runs/${period}/calculate`), { method: "POST" }),
    onSuccess: (data) => {
      qc.setQueryData(["payroll-run", period], data);
      toast.show(`${periodLabel(period)} calculated (revision ${str(data.revision)})`);
      refresh();
    },
    onError: (e: Error) => toast.error(e),
  });

  const returnToDraft = useMutation({
    mutationFn: () => apiFetch(api.payroll(`runs/${period}/return-to-draft`), { method: "POST" }),
    onSuccess: () => {
      toast.show("Returned to draft — nothing was deducted from leave");
      refresh();
    },
    onError: (e: Error) => toast.error(e),
  });

  const status = r?.status ?? null;
  const isDraft = status === "DRAFT";

  return (
    <div className="stack">
      <section className="card stack">
        <div className="row between" style={{ flexWrap: "wrap", gap: 12 }}>
          <div className="filters" style={{ margin: 0 }}>
            <label className="field">
              <span>Month</span>
              <input
                type="month"
                value={period}
                max={currentPeriod()}
                onChange={(e) => {
                  setPeriod(e.target.value);
                  setPage(0);
                  setSelected(null);
                }}
              />
            </label>
            <div>
              <div className="label">Status</div>
              {r?.exists ? <StatusPill status={status} /> : <span className="muted">Not calculated</span>}
              {r?.exists ? <span className="muted small"> · revision {str(r.revision)}</span> : null}
            </div>
            {r?.calculatedAt ? (
              <div>
                <div className="label">Calculated</div>
                <span className="small">{fmtDateTime(r.calculatedAt)}</span>
              </div>
            ) : null}
            {r?.finalizedAt ? (
              <div>
                <div className="label">Finalised</div>
                <span className="small">{fmtDateTime(r.finalizedAt)}</span>
              </div>
            ) : null}
          </div>
          <div className="row" style={{ gap: 8, flexWrap: "wrap" }}>
            {canExport && r?.exists ? (
              <button
                type="button"
                className="btn"
                onClick={() =>
                  downloadFile(api.payroll(`runs/${period}/export.csv`), undefined, `payroll-${period}.csv`).catch(
                    (e: Error) => toast.error(e),
                  )
                }
              >
                Export CSV
              </button>
            ) : null}
            {!r?.exists || isDraft ? (
              <button
                type="button"
                className="btn"
                disabled={calculate.isPending || r?.canCalculate === false}
                onClick={() => calculate.mutate()}
              >
                {calculate.isPending ? "Calculating…" : r?.exists ? "Recalculate" : "Calculate"}
              </button>
            ) : null}
            {isDraft ? (
              <button
                type="button"
                className="btn primary"
                disabled={!r?.canFinalize}
                title={(r?.finalizeBlockers ?? []).join("\n") || "Finalise this month"}
                onClick={() => setFinalizeOpen(true)}
              >
                Finalise month
              </button>
            ) : null}
            {status === "FINALIZED" ? (
              <button
                type="button"
                className="btn danger"
                disabled={!r?.canReopen}
                title={(r?.reopenBlockers ?? []).join("\n") || "Reopen this month"}
                onClick={() => setReopenOpen(true)}
              >
                Reopen month
              </button>
            ) : null}
            {status === "ACTION_REQUIRED" ? (
              <button type="button" className="btn primary" disabled={returnToDraft.isPending} onClick={() => returnToDraft.mutate()}>
                Return to draft
              </button>
            ) : null}
          </div>
        </div>

        <ErrorNote error={run.error} />
        {calculate.isError ? <div className="notice bad">{calculate.error.message}</div> : null}

        {(r?.warnings ?? []).length > 0 ? (
          <div className="notice warn">
            {(r?.warnings ?? []).map((w, i) => (
              <div key={i}>{w}</div>
            ))}
          </div>
        ) : null}
        {isDraft && (r?.finalizeBlockers ?? []).length > 0 ? (
          <div className="notice">
            <b>Before finalising:</b>
            <ul style={{ margin: "4px 0 0", paddingLeft: 18 }}>
              {(r?.finalizeBlockers ?? []).map((b, i) => (
                <li key={i} className="small">
                  {b}
                </li>
              ))}
            </ul>
          </div>
        ) : null}
        {status && IN_FLIGHT.has(status) ? (
          <div className="notice">
            {status === "FINALIZING" ? "Finalising" : "Reopening"} — waiting for Leave to confirm the balance
            changes. This page updates automatically.
          </div>
        ) : null}

        {r?.exists ? <Totals totals={r.totals ?? []} /> : null}
        {r?.exists && (r.leaveToTake ?? []).length > 0 ? (
          <p className="small muted">
            {status === "FINALIZED" ? "Taken from leave balances: " : "Finalising will take from leave balances: "}
            {(r.leaveToTake ?? []).map((l) => `${fmtNumber(l.days)} ${str(l.category)} (${str(l.employees)} people)`).join(", ")}
          </p>
        ) : null}
        {!r?.exists && !run.isLoading ? (
          <p className="muted">
            No payroll for {periodLabel(period)} yet. Calculate it to create the live draft for everyone employed this month.
          </p>
        ) : null}
      </section>

      {r?.exists ? (
        <section className="card stack">
          <div className="filters" style={{ margin: 0 }}>
            <label className="field">
              <span>Search</span>
              <input
                type="search"
                placeholder="Name or code"
                value={q}
                onChange={(e) => {
                  setQ(e.target.value);
                  setPage(0);
                }}
              />
            </label>
            <label className="checks" style={{ alignSelf: "flex-end" }}>
              <input
                type="checkbox"
                checked={exceptionsOnly}
                onChange={(e) => {
                  setExceptionsOnly(e.target.checked);
                  setPage(0);
                }}
              />{" "}
              Needs attention only ({str(r.exceptionCount, "0")})
            </label>
          </div>
          <ErrorNote error={lines.error} />
          <div className="tbl-wrap">
            <table>
              <thead>
                <tr>
                  <th>Employee</th>
                  <th>Pay</th>
                  <th className="n">Days / hours</th>
                  <th className="n">Absent</th>
                  <th className="n">Covered</th>
                  <th className="n">Unpaid</th>
                  <th className="n">Base pay</th>
                  <th className="n">Deduction</th>
                  <th className="n">Adjustment</th>
                  <th className="n">Net pay</th>
                  <th>Notes</th>
                </tr>
              </thead>
              <tbody>
                {rows.length === 0 ? (
                  <tr>
                    <td colSpan={11} className="empty">
                      {lines.isLoading ? "Loading…" : "No rows"}
                    </td>
                  </tr>
                ) : (
                  rows.map((l) => {
                    const cur = str(l.currency, "") || undefined;
                    const hourly = String(l.payType) === "HOURLY";
                    const notes = Array.isArray(l.notes) ? (l.notes as string[]) : [];
                    return (
                      <tr key={String(l.employeeId)} style={{ cursor: "pointer" }} onClick={() => setSelected(Number(l.employeeId))}>
                        <td>
                          <b>{str(l.employeeName)}</b>
                          <div className="muted small mono">{str(l.employeeCode, "")}</div>
                        </td>
                        <td className="small">
                          {l.payType ? (hourly ? "Hourly" : "Monthly") : "—"} <span className="muted">{str(l.currency, "")}</span>
                        </td>
                        <td className="n small">
                          {hourly ? `${fmtNumber(l.hoursWorked)} h` : `${fmtNumber(l.daysEmployed)} / ${fmtNumber(l.workingDays)}`}
                        </td>
                        <td className="n">{hourly ? "—" : fmtNumber(l.absentDays)}</td>
                        <td className="n">{hourly ? "—" : fmtNumber(l.coveredDays)}</td>
                        <td className="n">{hourly ? "—" : fmtNumber(l.unpaidLeaveDays)}</td>
                        <td className="n">{fmtMoney(l.basePay, cur)}</td>
                        <td className="n">{Number(l.deduction) > 0 ? `− ${fmtMoney(l.deduction)}` : "—"}</td>
                        <td className="n">{Number(l.adjustment) !== 0 ? fmtMoney(l.adjustment) : "—"}</td>
                        <td className="n">
                          <b>{fmtMoney(l.netPay, cur)}</b>
                        </td>
                        <td className="small" style={{ maxWidth: 280 }}>
                          {l.exception ? <span className="pill bad">Needs attention</span> : null}{" "}
                          <span className="muted">{notes.slice(0, 2).join(" · ")}</span>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
          {totalPages > 1 ? (
            <div className="row" style={{ gap: 8, justifyContent: "flex-end" }}>
              <button type="button" className="btn sm" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
                Previous
              </button>
              <span className="small muted">
                Page {page + 1} of {totalPages}
              </span>
              <button type="button" className="btn sm" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
                Next
              </button>
            </div>
          ) : null}
        </section>
      ) : null}

      {selected != null && r ? (
        <LineModal period={period} employeeId={selected} editable={isDraft} onClose={() => setSelected(null)} onChanged={refresh} />
      ) : null}
      {finalizeOpen && r ? (
        <FinalizeModal run={r} onClose={() => setFinalizeOpen(false)} onDone={refresh} />
      ) : null}
      {reopenOpen && r ? <ReopenModal run={r} onClose={() => setReopenOpen(false)} onDone={refresh} /> : null}
    </div>
  );
}

function Totals({ totals }: { totals: Rec[] }) {
  if (totals.length === 0) return null;
  return (
    <div className="grid g3">
      {totals.map((t) => {
        const cur = str(t.currency, "");
        return (
          <div key={cur} className="stat">
            <span className="s">
              {cur} · {str(t.employees)} people
            </span>
            <span className="v">{fmtMoney(t.netPay, cur)}</span>
            <span className="small muted">
              Base {fmtMoney(t.basePay)} · Deductions {fmtMoney(t.deduction)} · Adjustments {fmtMoney(t.adjustment)}
            </span>
          </div>
        );
      })}
    </div>
  );
}

function LineModal({
  period,
  employeeId,
  editable,
  onClose,
  onChanged,
}: {
  period: string;
  employeeId: number;
  editable: boolean;
  onClose: () => void;
  onChanged: () => void;
}) {
  const toast = useToast();
  const qc = useQueryClient();
  const line = useQuery({
    queryKey: ["payroll-line", period, employeeId],
    queryFn: ({ signal }) => apiFetch<Rec>(api.payroll(`runs/${period}/lines/${employeeId}`), { signal }),
  });
  const slip = line.data;
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [err, setErr] = useState("");
  useEffect(() => {
    if (slip) {
      setAmount(Number(slip.adjustment ?? 0) !== 0 ? String(slip.adjustment) : "");
      setNote(str(slip.adjustmentNote, ""));
    }
  }, [slip]);

  const after = (data: Rec) => {
    qc.setQueryData(["payroll-line", period, employeeId], data);
    setErr("");
    onChanged();
  };
  const save = useMutation({
    mutationFn: () =>
      apiFetch<Rec>(api.payroll(`runs/${period}/adjustments/${employeeId}`), {
        method: "PUT",
        body: { amount: Number(amount), note },
      }),
    onSuccess: (d) => {
      toast.show("Adjustment saved");
      after(d);
    },
    onError: (e: Error) => setErr(e.message),
  });
  const remove = useMutation({
    mutationFn: () => apiFetch<Rec>(api.payroll(`runs/${period}/adjustments/${employeeId}`), { method: "DELETE" }),
    onSuccess: (d) => {
      toast.show("Adjustment removed");
      after(d);
    },
    onError: (e: Error) => setErr(e.message),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const n = Number(amount);
    if (!amount || Number.isNaN(n) || n === 0) return setErr("Enter a non-zero amount (negative for a recovery)");
    if (!note.trim()) return setErr("A note is required — it is shown on the payslip");
    save.mutate();
  }

  const calc = (slip?.calc ?? {}) as Rec;
  return (
    <Modal open onClose={onClose} wide title={slip ? `${str(slip.employeeName)} · ${periodLabel(period)}` : "Payslip"}>
      <ErrorNote error={line.error} />
      {slip ? (
        <div className="stack">
          <PayslipView slip={{ ...slip, period }} />
          {editable ? (
            <form className="card stack" onSubmit={submit} noValidate>
              <h3 style={{ margin: 0 }}>Manual adjustment (one per person per month)</h3>
              <div className="form-grid">
                <FormField label="Amount" hint="Positive = bonus, negative = recovery (e.g. −1000)">
                  <input type="number" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
                </FormField>
                <FormField label="Note (shown on the payslip)">
                  <input type="text" maxLength={512} value={note} onChange={(e) => setNote(e.target.value)} />
                </FormField>
              </div>
              {err ? <div className="notice bad">{err}</div> : null}
              <div className="row" style={{ gap: 8 }}>
                <button type="submit" className="btn primary" disabled={save.isPending}>
                  {save.isPending ? "Saving…" : "Save adjustment"}
                </button>
                {Number(slip.adjustment ?? 0) !== 0 ? (
                  <button type="button" className="btn" disabled={remove.isPending} onClick={() => remove.mutate()}>
                    Remove
                  </button>
                ) : null}
              </div>
            </form>
          ) : null}
          <details>
            <summary className="small muted">Calculation details</summary>
            <div className="kv" style={{ marginTop: 8 }}>
              <div>
                <div className="label">Employment in month</div>
                {str(calc.employmentFrom)} → {str(calc.employmentTo)}
              </div>
              <div>
                <div className="label">Rate basis / absence rule</div>
                {str(calc.perDayRateBasis)} · {str(calc.absenceRule)}
              </div>
              <div>
                <div className="label">Divisor</div>
                {fmtNumber(calc.divisor)}
              </div>
              <div>
                <div className="label">Leave available for cover</div>
                {Array.isArray(calc.coverBalancesBefore) && calc.coverBalancesBefore.length > 0
                  ? (calc.coverBalancesBefore as Rec[]).map((b) => `${str(b.category)} ${fmtNumber(b.available)}`).join(", ")
                  : "—"}
              </div>
              <div>
                <div className="label">Salary records used</div>
                {Array.isArray(calc.salaries)
                  ? (calc.salaries as Rec[]).map((s) => `${str(s.effectiveDate)}: ${str(s.payType)} ${fmtMoney(s.amount, str(s.currency, ""))}`).join(" · ")
                  : "—"}
              </div>
            </div>
          </details>
        </div>
      ) : (
        <p className="muted">Loading…</p>
      )}
    </Modal>
  );
}

function useOperationPoll(onFinished: (op: Rec) => void) {
  const [operationId, setOperationId] = useState<string | null>(null);
  const op = useQuery({
    queryKey: ["payroll-operation", operationId],
    queryFn: ({ signal }) => apiFetch<Rec>(api.payroll(`operations/${operationId}`), { signal }),
    enabled: !!operationId,
    refetchInterval: (query) => (query.state.data?.status === "PENDING" || !query.state.data ? 1500 : false),
  });
  useEffect(() => {
    if (op.data && op.data.status !== "PENDING") {
      onFinished(op.data);
      setOperationId(null);
    }
  }, [op.data, onFinished]);
  return { start: setOperationId, polling: !!operationId };
}

/** FR-PAY-12 — second confirmation: review totals and leave to take, then type the month. */
function FinalizeModal({ run, onClose, onDone }: { run: Run; onClose: () => void; onDone: () => void }) {
  const toast = useToast();
  const [typed, setTyped] = useState("");
  const [err, setErr] = useState("");
  const poll = useOperationPoll((op) => {
    if (op.status === "SUCCEEDED") toast.show(`${periodLabel(run.period)} finalised — employees notified`);
    else setErr(`Finalise ${str(op.status)}: ${str(op.error, "see the run warnings")}`);
    onDone();
    if (op.status === "SUCCEEDED") onClose();
  });
  const finalize = useMutation({
    mutationFn: () =>
      apiFetch<Rec>(api.payroll(`runs/${run.period}/finalize`), {
        method: "POST",
        body: { confirm: true, revision: run.revision },
      }),
    onSuccess: (op) => {
      if (op.status === "PENDING") {
        poll.start(String(op.operationId));
      } else if (op.status === "SUCCEEDED") {
        toast.show(`${periodLabel(run.period)} finalised — employees notified`);
        onDone();
        onClose();
      } else {
        setErr(str(op.error, "Finalise did not complete"));
        onDone();
      }
    },
    onError: (e: Error) => setErr(e.message),
  });
  const busy = finalize.isPending || poll.polling;
  return (
    <Modal open onClose={busy ? () => {} : onClose} title={`Finalise ${periodLabel(run.period)}?`}>
      <div className="stack">
        <p className="small">
          This locks every payslip of the month (revision {str(run.revision)}), takes covered absences from leave balances
          and notifies each employee that their payslip is ready. It can only be undone by reopening the month.
        </p>
        <Totals totals={run.totals ?? []} />
        {(run.leaveToTake ?? []).length > 0 ? (
          <div className="notice warn">
            Leave to take:{" "}
            {(run.leaveToTake ?? []).map((l) => `${fmtNumber(l.days)} day(s) ${str(l.category)} from ${str(l.employees)} people`).join("; ")}
          </div>
        ) : (
          <p className="small muted">No leave balances change.</p>
        )}
        <FormField label={`Type ${run.period} to confirm`}>
          <input type="text" value={typed} onChange={(e) => setTyped(e.target.value)} autoFocus />
        </FormField>
        {err ? <div className="notice bad">{err}</div> : null}
        <div className="modal-foot">
          <button type="button" className="btn" disabled={busy} onClick={onClose}>
            Cancel
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={typed.trim() !== run.period || busy}
            onClick={() => {
              setErr("");
              finalize.mutate();
            }}
          >
            {busy ? "Finalising…" : "Finalise month"}
          </button>
        </div>
      </div>
    </Modal>
  );
}

/** FR-PAY-14 — reopen with reason; leave adjustments are reversed before the run returns to draft. */
function ReopenModal({ run, onClose, onDone }: { run: Run; onClose: () => void; onDone: () => void }) {
  const toast = useToast();
  const [typed, setTyped] = useState("");
  const [reason, setReason] = useState("");
  const [err, setErr] = useState("");
  const poll = useOperationPoll((op) => {
    if (op.status === "SUCCEEDED") toast.show(`${periodLabel(run.period)} reopened — leave adjustments returned`);
    else setErr(`Reopen ${str(op.status)}: ${str(op.error)}`);
    onDone();
    if (op.status === "SUCCEEDED") onClose();
  });
  const reopen = useMutation({
    mutationFn: () =>
      apiFetch<Rec>(api.payroll(`runs/${run.period}/reopen`), { method: "POST", body: { confirm: true, reason } }),
    onSuccess: (op) => {
      if (op.status === "PENDING") poll.start(String(op.operationId));
      else {
        toast.show(`${periodLabel(run.period)} reopened`);
        onDone();
        onClose();
      }
    },
    onError: (e: Error) => setErr(e.message),
  });
  const busy = reopen.isPending || poll.polling;
  return (
    <Modal open onClose={busy ? () => {} : onClose} title={`Reopen ${periodLabel(run.period)}?`}>
      <div className="stack">
        <p className="small">
          The finalised payslips are kept as a revision, the leave taken for covered absences is returned, and the month goes
          back to draft for recalculation. Employees keep seeing the old payslip until it is finalised again.
        </p>
        <FormField label="Reason (audited)" required>
          <input type="text" maxLength={512} value={reason} onChange={(e) => setReason(e.target.value)} />
        </FormField>
        <FormField label={`Type ${run.period} to confirm`}>
          <input type="text" value={typed} onChange={(e) => setTyped(e.target.value)} />
        </FormField>
        {err ? <div className="notice bad">{err}</div> : null}
        <div className="modal-foot">
          <button type="button" className="btn" disabled={busy} onClick={onClose}>
            Cancel
          </button>
          <button
            type="button"
            className="btn danger"
            disabled={typed.trim() !== run.period || !reason.trim() || busy}
            onClick={() => {
              setErr("");
              reopen.mutate();
            }}
          >
            {busy ? "Reopening…" : "Reopen month"}
          </button>
        </div>
      </div>
    </Modal>
  );
}

// ───────────────────────────────────────────── My pay (FR-SAL-03)

function MyPayPanel({ initialPeriod }: { initialPeriod: string | null }) {
  const { can } = useAuth();
  const [open, setOpen] = useState<string | null>(initialPeriod);
  const estimate = useQuery({
    queryKey: ["payroll-me-estimate"],
    queryFn: ({ signal }) => apiFetch<Rec>(api.payroll("me/estimate"), { signal }),
  });
  const slips = useQuery({
    queryKey: ["payroll-me-payslips"],
    queryFn: ({ signal }) => apiFetch<Rec[]>(api.payroll("me/payslips"), { signal }),
  });
  const salary = useQuery({
    queryKey: ["payroll-me-salary"],
    queryFn: ({ signal }) => apiFetch<Rec>(api.payroll("me/salary"), { signal }),
    enabled: can("salary", "view"),
  });
  const detail = useQuery({
    queryKey: ["payroll-me-payslip", open],
    queryFn: ({ signal }) => apiFetch<Rec>(api.payroll(`me/payslips/${open}`), { signal }),
    enabled: !!open,
  });
  const est = (estimate.data?.estimate ?? null) as Rec | null;
  const cur = (salary.data?.current ?? null) as Rec | null;
  const list = rowsOf<Rec>(slips.data);

  return (
    <div className="stack">
      <section className="card stack">
        <h2>Current pay</h2>
        {cur ? (
          <div className="kv">
            <div>
              <div className="label">Pay type</div>
              {String(cur.payType) === "HOURLY" ? "Hourly" : "Monthly"}
            </div>
            <div>
              <div className="label">{String(cur.payType) === "HOURLY" ? "Hourly rate" : "Monthly gross"}</div>
              <b>{fmtMoney(cur.amount, str(cur.currency, ""))}</b>
            </div>
            <div>
              <div className="label">Effective from</div>
              {str(cur.effectiveDate)}
            </div>
          </div>
        ) : (
          <p className="muted">{salary.isLoading ? "Loading…" : "No salary record yet."}</p>
        )}
      </section>

      <section className="card stack">
        <div className="row between">
          <h2 style={{ margin: 0 }}>This month so far</h2>
          <span className="small muted">Live estimate · {str(estimate.data?.asOf)}</span>
        </div>
        <ErrorNote error={estimate.error} />
        {est ? <PayslipView slip={{ ...est, period: estimate.data?.period }} estimate /> : null}
        {!est && !estimate.isLoading && !estimate.error ? <p className="muted">No estimate available.</p> : null}
      </section>

      <section className="card stack">
        <h2>My payslips</h2>
        <ErrorNote error={slips.error} />
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th>Month</th>
                <th className="n">Base pay</th>
                <th className="n">Deduction</th>
                <th className="n">Adjustment</th>
                <th className="n">Net pay</th>
                <th>Finalised</th>
              </tr>
            </thead>
            <tbody>
              {list.length === 0 ? (
                <tr>
                  <td colSpan={6} className="empty">
                    {slips.isLoading ? "Loading…" : "No finalised payslips yet"}
                  </td>
                </tr>
              ) : (
                list.map((p) => (
                  <tr key={String(p.period)} style={{ cursor: "pointer" }} onClick={() => setOpen(String(p.period))}>
                    <td>{periodLabel(p.period)}</td>
                    <td className="n">{fmtMoney(p.basePay, str(p.currency, ""))}</td>
                    <td className="n">{fmtMoney(p.deduction)}</td>
                    <td className="n">{Number(p.adjustment) !== 0 ? fmtMoney(p.adjustment) : "—"}</td>
                    <td className="n">
                      <b>{fmtMoney(p.netPay, str(p.currency, ""))}</b>
                    </td>
                    <td className="small">{fmtDateTime(p.finalizedAt)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {open ? (
        <Modal
          open
          wide
          onClose={() => setOpen(null)}
          title={`Payslip · ${periodLabel(open)}`}
          footer={
            <button type="button" className="btn" onClick={() => window.print()}>
              Print
            </button>
          }
        >
          <ErrorNote error={detail.error} />
          {detail.data ? <PayslipView slip={detail.data} /> : !detail.error ? <p className="muted">Loading…</p> : null}
        </Modal>
      ) : null}
    </div>
  );
}

// ───────────────────────────────────────────── Rules (§1.9)

function RulesPanel() {
  const qc = useQueryClient();
  const toast = useToast();
  const settings = useQuery({
    queryKey: ["payroll-settings"],
    queryFn: ({ signal }) => apiFetch<Rec>(api.payroll("settings"), { signal }),
  });
  const leaveTypes = useLeaveTypeCatalogue();
  const [form, setForm] = useState<{ perDayRateBasis: string; absenceRule: string; cover: string[]; notify: boolean } | null>(null);
  const [err, setErr] = useState("");
  useEffect(() => {
    if (settings.data && !form) {
      setForm({
        perDayRateBasis: str(settings.data.perDayRateBasis, "WORKING_DAYS"),
        absenceRule: str(settings.data.absenceRule, "BALANCE_FIRST"),
        cover: Array.isArray(settings.data.absenceCoverOrder) ? (settings.data.absenceCoverOrder as string[]) : [],
        notify: settings.data.notifyPayslipReady !== false,
      });
    }
  }, [settings.data, form]);

  const save = useMutation({
    mutationFn: (body: Rec) => apiFetch<Rec>(api.payroll("settings"), { method: "PUT", body }),
    onSuccess: (d) => {
      qc.setQueryData(["payroll-settings"], d);
      toast.show("Payroll rules saved — drafts use them on their next calculation");
      setErr("");
    },
    onError: (e: Error) => setErr(e.message),
  });

  const families = useMemo(() => {
    const set = new Map<string, string>();
    for (const t of leaveTypes.data ?? []) {
      if (t.paid === false) continue;
      const fam = String(t.code).toUpperCase().replace(/_\d{4}$/, "");
      if (!set.has(fam)) set.set(fam, t.name.replace(/\s*\(\d{4}\)$/, ""));
    }
    return [...set.entries()];
  }, [leaveTypes.data]);

  if (!form) {
    return (
      <section className="card">
        <ErrorNote error={settings.error} />
        <p className="muted">Loading…</p>
      </section>
    );
  }

  const move = (i: number, d: number) => {
    const next = [...form.cover];
    const j = i + d;
    if (j < 0 || j >= next.length) return;
    [next[i], next[j]] = [next[j], next[i]];
    setForm({ ...form, cover: next });
  };
  const fixed = Array.isArray(settings.data?.fixedRules) ? (settings.data!.fixedRules as Rec[]) : [];
  const defaults = (settings.data?.defaults ?? {}) as Rec;

  return (
    <section className="card stack">
      <h2>Payroll rules</h2>
      <p className="muted small">
        Defaults follow the SRS: no extra deductions — only absences beyond leave balance and unpaid leave reduce pay.
        Changes are audited and apply to draft months on their next calculation; finalised months never change.
      </p>
      <form
        className="stack"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate({
            perDayRateBasis: form.perDayRateBasis,
            absenceRule: form.absenceRule,
            absenceCoverOrder: form.cover,
            notifyPayslipReady: form.notify,
          });
        }}
      >
        <div className="form-grid">
          <FormField label="Per-day rate (BR-15)" hint="Monthly gross ÷ working days in the month from the person's schedule, or ÷ 30">
            <select value={form.perDayRateBasis} onChange={(e) => setForm({ ...form, perDayRateBasis: e.target.value })}>
              <option value="WORKING_DAYS">Salary ÷ working days in month (default)</option>
              <option value="CALENDAR_30">Salary ÷ 30</option>
            </select>
          </FormField>
          <FormField label="Absence rule (FR-PAY-04)" hint="Balance first covers absences from leave before any deduction">
            <select value={form.absenceRule} onChange={(e) => setForm({ ...form, absenceRule: e.target.value })}>
              <option value="BALANCE_FIRST">Balance first (default)</option>
              <option value="DIRECT">Direct — deduct every absent day</option>
            </select>
          </FormField>
        </div>

        <div className="stack" style={{ gap: 6, opacity: form.absenceRule === "DIRECT" ? 0.5 : 1 }}>
          <div className="label">Leave used to cover absences, in order (BR-16; never Sick by default)</div>
          {form.cover.length === 0 ? <p className="small muted">No cover categories — every absence is deducted.</p> : null}
          {form.cover.map((c, i) => (
            <div key={c} className="row" style={{ gap: 8 }}>
              <span className="pill">{i + 1}</span>
              <b style={{ minWidth: 160 }}>{families.find(([f]) => f === c)?.[1] ?? c}</b>
              <span className="mono small muted">{c}</span>
              <button type="button" className="btn sm" onClick={() => move(i, -1)} disabled={i === 0}>
                ↑
              </button>
              <button type="button" className="btn sm" onClick={() => move(i, 1)} disabled={i === form.cover.length - 1}>
                ↓
              </button>
              <button type="button" className="btn sm" onClick={() => setForm({ ...form, cover: form.cover.filter((x) => x !== c) })}>
                Remove
              </button>
            </div>
          ))}
          <div>
            <select
              value=""
              onChange={(e) => {
                if (e.target.value) setForm({ ...form, cover: [...form.cover, e.target.value] });
              }}
            >
              <option value="">+ Add a paid leave category…</option>
              {families
                .filter(([f]) => !form.cover.includes(f))
                .map(([f, name]) => (
                  <option key={f} value={f}>
                    {name} ({f})
                  </option>
                ))}
            </select>
          </div>
        </div>

        <label className="checks">
          <input type="checkbox" checked={form.notify} onChange={(e) => setForm({ ...form, notify: e.target.checked })} /> Notify each
          employee when their payslip is ready (FR-NT-11)
        </label>

        {err ? <div className="notice bad">{err}</div> : null}
        <div className="row" style={{ gap: 8 }}>
          <button type="submit" className="btn primary" disabled={save.isPending}>
            {save.isPending ? "Saving…" : "Save rules"}
          </button>
          <button
            type="button"
            className="btn"
            onClick={() =>
              setForm({
                perDayRateBasis: str(defaults.perDayRateBasis, "WORKING_DAYS"),
                absenceRule: str(defaults.absenceRule, "BALANCE_FIRST"),
                cover: Array.isArray(defaults.absenceCoverOrder) ? (defaults.absenceCoverOrder as string[]) : ["CASUAL", "ANNUAL"],
                notify: true,
              })
            }
          >
            Reset to SRS defaults
          </button>
        </div>
      </form>

      <div>
        <div className="label" style={{ marginBottom: 6 }}>
          Fixed rules
        </div>
        <ul style={{ margin: 0, paddingLeft: 18 }}>
          {fixed.map((f) => (
            <li key={str(f.id)} className="small">
              <span className="mono muted">{str(f.id)}</span> {str(f.rule)}
            </li>
          ))}
        </ul>
      </div>
      {settings.data?.updatedAt ? <p className="small muted">Last changed {fmtDateTime(settings.data.updatedAt)}</p> : null}
    </section>
  );
}
