"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { FormField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { ErrorNote } from "@/components/ui/QueryState";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import type { Rec } from "@/lib/api/lookups";
import { fmtDate, fmtDateTime, fmtMoney, humanize, str, todayIso } from "@/lib/format";

const schema = z.object({
  effectiveDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Choose a date"),
  amount: z.coerce.number("Enter an amount").positive("Must be greater than 0"),
  payType: z.enum(["MONTHLY", "HOURLY"]),
  currency: z.enum(["BDT", "AUD"], { message: "Choose BDT or AUD" }),
  reason: z.string().trim().min(1, "A reason is required").max(512, "Max 512 characters"),
});

export function salaryQueryKey(employeeId: string | number) {
  return ["salary", String(employeeId)];
}

/** Current pay from Payroll (the salary owner). */
export function useSalary(employeeId: string | number, enabled = true) {
  return useQuery({
    queryKey: salaryQueryKey(employeeId),
    queryFn: ({ signal }) => apiFetch<Rec>(api.payroll(`salary/employees/${employeeId}`), { signal }),
    enabled,
  });
}

/** FR-SAL-01/02 — salary history and recording a change (pay type, amount, currency, effective date, reason). */
export function SalaryPanel({
  employeeId,
  defaultCurrency,
  canEdit,
}: {
  employeeId: string;
  defaultCurrency?: string | null;
  canEdit: boolean;
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const salary = useSalary(employeeId);
  const current = (salary.data?.current ?? null) as Rec | null;
  const history = Array.isArray(salary.data?.history) ? (salary.data!.history as Rec[]) : [];
  const upcoming = Array.isArray(salary.data?.upcoming) ? (salary.data!.upcoming as Rec[]) : [];

  const blank = () => ({
    effectiveDate: todayIso(),
    amount: "",
    payType: String(current?.payType ?? "MONTHLY"),
    currency: String(current?.currency ?? (defaultCurrency === "AUD" ? "AUD" : "BDT")),
    reason: "",
  });
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(blank);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  const save = useMutation({
    mutationFn: (body: Rec) => apiFetch(api.payroll(`salary/employees/${employeeId}`), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Salary change recorded");
      setOpen(false);
      void qc.invalidateQueries({ queryKey: salaryQueryKey(employeeId) });
    },
    onError: (e: Error) => setErr(e.message),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = schema.safeParse(form);
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setErrors(next);
    }
    setErrors({});
    setErr("");
    save.mutate(parsed.data);
  }

  return (
    <div className="stack">
      <div className="card stack">
        <div className="card-head" style={{ marginBottom: 0 }}>
          <h2>Current pay</h2>
          {canEdit ? (
            <button
              type="button"
              className="btn primary"
              onClick={() => {
                setForm(blank());
                setErrors({});
                setErr("");
                setOpen(true);
              }}
            >
              Record salary change
            </button>
          ) : null}
        </div>
        <ErrorNote error={salary.error} />
        {current ? (
          <div className="kv">
            <div>
              <div className="label">Pay type</div>
              {humanize(current.payType)}
            </div>
            <div>
              <div className="label">{String(current.payType) === "HOURLY" ? "Hourly rate" : "Monthly gross"}</div>
              <b>{fmtMoney(current.amount, str(current.currency, ""))}</b>
            </div>
            <div>
              <div className="label">Effective from</div>
              {fmtDate(current.effectiveDate)}
            </div>
          </div>
        ) : (
          <p className="muted">{salary.isLoading ? "Loading…" : "No salary recorded — payroll flags this person until one is added."}</p>
        )}
        {upcoming.length > 0 ? (
          <div className="notice">
            Upcoming: {upcoming.map((u) => `${fmtMoney(u.amount, str(u.currency, ""))} from ${fmtDate(u.effectiveDate)}`).join("; ")}
          </div>
        ) : null}
      </div>

      <div className="card stack">
        <h2>Salary history</h2>
        <p className="muted small">Every change is kept and audited. Mid-month changes are paid per day from their effective date.</p>
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th>Effective</th>
                <th>Type</th>
                <th className="n">Amount</th>
                <th>Reason</th>
                <th>Recorded</th>
              </tr>
            </thead>
            <tbody>
              {history.length === 0 ? (
                <tr>
                  <td colSpan={5} className="empty">
                    {salary.isLoading ? "Loading…" : "No salary changes recorded"}
                  </td>
                </tr>
              ) : (
                history.map((r) => (
                  <tr key={String(r.id)}>
                    <td className="mono">{fmtDate(r.effectiveDate)}</td>
                    <td>{humanize(r.payType)}</td>
                    <td className="n">{fmtMoney(r.amount, str(r.currency, ""))}</td>
                    <td className="muted">{str(r.reason)}</td>
                    <td className="small muted">{fmtDateTime(r.createdAt)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      <Modal open={open} onClose={() => setOpen(false)} title="Record salary change">
        <form className="stack" onSubmit={submit} noValidate>
          <div className="form-grid">
            <FormField label="Pay type" required error={errors.payType}>
              <select value={form.payType} onChange={(e) => setForm({ ...form, payType: e.target.value })}>
                <option value="MONTHLY">Monthly</option>
                <option value="HOURLY">Hourly</option>
              </select>
            </FormField>
            <FormField label={form.payType === "HOURLY" ? "Hourly rate" : "Monthly gross"} required error={errors.amount}>
              <input type="number" min={0} step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />
            </FormField>
            <FormField label="Currency" required error={errors.currency}>
              <select value={form.currency} onChange={(e) => setForm({ ...form, currency: e.target.value })}>
                <option value="BDT">BDT</option>
                <option value="AUD">AUD</option>
              </select>
            </FormField>
            <FormField label="Effective date" required error={errors.effectiveDate}>
              <input type="date" value={form.effectiveDate} onChange={(e) => setForm({ ...form, effectiveDate: e.target.value })} />
            </FormField>
            <FormField label="Reason" required full error={errors.reason}>
              <input type="text" maxLength={512} value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} />
            </FormField>
          </div>
          {err ? <div className="notice bad">{err}</div> : null}
          <div className="modal-foot">
            <button type="button" className="btn" onClick={() => setOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={save.isPending}>
              {save.isPending ? "Saving…" : "Save"}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
