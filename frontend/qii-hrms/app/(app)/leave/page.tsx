"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { ApprovalChain } from "@/components/leave/ApprovalChain";
import { useAuth } from "@/components/auth/AuthProvider";
import { ConfirmModal } from "@/components/ui/ConfirmModal";
import { DataTable } from "@/components/ui/DataTable";
import { FormField } from "@/components/ui/FormField";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { StatusPill } from "@/components/ui/StatusPill";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { rowsOf, useLeaveTypes, type Rec } from "@/lib/api/lookups";
import { daysBetween, fmtNumber, fmtRange, str, todayIso } from "@/lib/format";
import { leaveRowSchema, type LeaveRow } from "@/lib/schemas/leave";

const applySchema = z
  .object({
    leaveTypeCode: z.string().min(1, "Choose a leave type"),
    startDate: z.string().min(1, "Start date is required"),
    endDate: z.string().min(1, "End date is required"),
    reason: z.string().trim().min(3, "Please give a short reason"),
    halfDay: z.boolean(),
  })
  .superRefine((v, ctx) => {
    if (v.startDate && v.endDate && v.endDate < v.startDate) {
      ctx.addIssue({ code: "custom", path: ["endDate"], message: "End date is before the start date" });
    }
    if (v.halfDay && v.startDate !== v.endDate) {
      ctx.addIssue({ code: "custom", path: ["halfDay"], message: "Half day applies to a single date" });
    }
  });

const STATUSES = ["PENDING_APPROVAL", "PENDING_RELIEF", "APPROVED", "REJECTED", "WITHDRAWN"];

export default function MyLeavePage() {
  const { me, can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({ leaveTypeCode: "", startDate: todayIso(), endDate: todayIso(), reason: "", halfDay: false });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");
  const [status, setStatus] = useState("");
  const [withdrawId, setWithdrawId] = useState<string | number | null>(null);

  const types = useLeaveTypes();
  const balances = useQuery({
    queryKey: ["my-balances", me.employeeId],
    queryFn: ({ signal }) => apiFetch(api.hrms(`leave-balances/employee/${me.employeeId}`), { signal }),
  });
  const bal = rowsOf<Rec>(balances.data);
  const balanceOf = (code: string) => bal.find((b) => b.leaveTypeCode === code)?.balanceDays;

  const typeList = types.data ?? [];
  const effectiveType = form.leaveTypeCode || typeList[0]?.code || "";

  const apply = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.hrms("leave/request"), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Leave request submitted");
      setFormError("");
      setForm((f) => ({ ...f, reason: "", halfDay: false }));
      void qc.invalidateQueries({ queryKey: ["my-leave"] });
      void qc.invalidateQueries({ queryKey: ["my-balances"] });
      void qc.invalidateQueries({ queryKey: ["approvals-pending-count"] });
    },
    onError: (e: Error) => setFormError(e.message),
  });

  const withdraw = useMutation({
    mutationFn: (id: string | number) => apiFetch(api.hrms(`leave-applications/${id}/withdraw`), { method: "POST" }),
    onSuccess: () => {
      toast.show("Request withdrawn");
      setWithdrawId(null);
      void qc.invalidateQueries({ queryKey: ["my-leave"] });
      void qc.invalidateQueries({ queryKey: ["my-balances"] });
    },
    onError: (e: Error) => toast.error(e),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    setFormError("");
    const parsed = applySchema.safeParse({ ...form, leaveTypeCode: effectiveType });
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) {
        const k = String(i.path[0] ?? "form");
        if (!next[k]) next[k] = i.message;
      }
      setErrors(next);
      return;
    }
    setErrors({});
    apply.mutate({
      leaveTypeCode: parsed.data.leaveTypeCode,
      startDate: parsed.data.startDate,
      endDate: parsed.data.endDate,
      reason: parsed.data.reason,
      halfDay: parsed.data.halfDay || undefined,
    });
  }

  const span = form.startDate && form.endDate ? daysBetween(form.startDate, form.endDate) : 0;
  const isPending = (s?: string | null) => String(s ?? "").toUpperCase().includes("PENDING");

  return (
    <div className="stack">
      <PageHeader title="My leave" subtitle="Your balances, new requests and where each request is in the approval chain." />

      <div className="grid g4">
        {bal.length === 0 ? (
          <div className="card muted">{balances.isLoading ? "Loading balances…" : "No balances recorded yet."}</div>
        ) : null}
        {bal.map((b) => (
          <div key={String(b.id)} className="card">
            <div className="stat">
              <span className="s">
                {str(b.leaveTypeCode)} · {str(b.year)}
              </span>
              <span className="v">{fmtNumber(b.balanceDays)}</span>
              <span className="s">days remaining</span>
            </div>
          </div>
        ))}
      </div>
      <ErrorNote error={balances.error} />

      <div className="grid g2" style={{ gridTemplateColumns: "minmax(0,1fr) minmax(0,1.7fr)", alignItems: "start" }}>
        <section className="card stack">
          <h2>Apply for leave</h2>
          {!can("leave", "request") ? (
            <p className="muted">You cannot request leave with your current roles.</p>
          ) : (
            <form className="stack" onSubmit={submit} noValidate>
              <FormField label="Leave type" required error={errors.leaveTypeCode}>
                <select value={effectiveType} onChange={(e) => setForm({ ...form, leaveTypeCode: e.target.value })}>
                  {typeList.map((t) => {
                    const b = balanceOf(t.code);
                    return (
                      <option key={t.code} value={t.code}>
                        {t.name}
                        {b !== undefined ? ` — ${fmtNumber(b)} left` : ""}
                      </option>
                    );
                  })}
                  {typeList.length === 0 ? <option value="">No leave types configured</option> : null}
                </select>
              </FormField>
              <div className="row" style={{ alignItems: "flex-start" }}>
                <div style={{ flex: 1 }}>
                  <FormField label="Start" required error={errors.startDate}>
                    <input
                      type="date"
                      value={form.startDate}
                      onChange={(e) =>
                        setForm({
                          ...form,
                          startDate: e.target.value,
                          endDate: form.endDate < e.target.value ? e.target.value : form.endDate,
                        })
                      }
                    />
                  </FormField>
                </div>
                <div style={{ flex: 1 }}>
                  <FormField label="End" required error={errors.endDate}>
                    <input
                      type="date"
                      min={form.startDate}
                      value={form.endDate}
                      onChange={(e) => setForm({ ...form, endDate: e.target.value })}
                    />
                  </FormField>
                </div>
              </div>
              <div className="checks">
                <label>
                  <input
                    type="checkbox"
                    checked={form.halfDay}
                    onChange={(e) => setForm({ ...form, halfDay: e.target.checked })}
                  />
                  Half day
                </label>
                {errors.halfDay ? <span className="err">{errors.halfDay}</span> : null}
              </div>
              <FormField label="Reason" required error={errors.reason}>
                <textarea value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} />
              </FormField>
              {span > 0 ? (
                <p className="note">
                  {form.halfDay ? "Half day" : `${span} calendar day${span === 1 ? "" : "s"}`} requested. Working days are
                  counted by the server using your work schedule and holidays.
                </p>
              ) : null}
              {formError ? <div className="notice bad">{formError}</div> : null}
              <div>
                <button type="submit" className="btn primary" disabled={apply.isPending || typeList.length === 0}>
                  {apply.isPending ? "Submitting…" : "Submit request"}
                </button>
              </div>
            </form>
          )}
        </section>

        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <h2>My requests</h2>
            <select value={status} onChange={(e) => setStatus(e.target.value)} style={{ width: 190 }} aria-label="Status filter">
              <option value="">All statuses</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s.replace(/_/g, " ").toLowerCase()}
                </option>
              ))}
            </select>
          </div>
          <DataTable<LeaveRow>
            queryKey="my-leave"
            path={api.hrms("leave/my")}
            itemSchema={leaveRowSchema as never}
            filters={{ status }}
            search={false}
            pageSizes={[10, 20, 50]}
            initialSize={10}
            emptyText="No requests yet"
            rowKey={(r) => String(r.id)}
            columns={[
              {
                key: "leaveTypeCode",
                header: "Type",
                render: (r) => (
                  <div>
                    <b>{str(r.leaveTypeCode)}</b>
                    <div className="muted small">{str(r.reason, "")}</div>
                  </div>
                ),
              },
              { key: "dates", header: "Dates", render: (r) => fmtRange(r.startDate, r.endDate) },
              { key: "workingDaysCounted", header: "Days", className: "n", render: (r) => fmtNumber(r.workingDaysCounted) },
              {
                key: "status",
                header: "Status",
                render: (r) => (
                  <div className="stack" style={{ gap: 4 }}>
                    <StatusPill status={r.status} />
                    {r.rejectReason ? <span className="small muted">{r.rejectReason}</span> : null}
                  </div>
                ),
              },
              { key: "chain", header: "Approval chain", render: (r) => <ApprovalChain steps={r.approvals} /> },
              {
                key: "actions",
                header: "",
                render: (r) =>
                  isPending(r.status) && can("leave", "request") ? (
                    <button
                      type="button"
                      className="btn sm"
                      disabled={withdraw.isPending}
                      onClick={() => setWithdrawId(r.id)}
                    >
                      Withdraw
                    </button>
                  ) : null,
              },
            ]}
          />
        </section>
      </div>

      <ConfirmModal
        open={withdrawId !== null}
        title="Withdraw leave request?"
        confirmLabel="Withdraw"
        danger
        busy={withdraw.isPending}
        onCancel={() => setWithdrawId(null)}
        onConfirm={() => withdrawId !== null && withdraw.mutate(withdrawId)}
      >
        The request will be cancelled and any held balance released.
      </ConfirmModal>
    </div>
  );
}
