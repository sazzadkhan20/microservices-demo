"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { ApprovalChain } from "@/components/leave/ApprovalChain";
import { useAuth } from "@/components/auth/AuthProvider";
import { ConfirmModal } from "@/components/ui/ConfirmModal";
import { DataTable } from "@/components/ui/DataTable";
import { FormField } from "@/components/ui/FormField";
import { PageHeader } from "@/components/ui/PageHeader";
import { StatusPill } from "@/components/ui/StatusPill";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { useLeaveTypes } from "@/lib/api/lookups";
import { daysBetween, fmtNumber, fmtRange, str, todayIso } from "@/lib/format";
import { leaveRowSchema, leavePreviewSchema, type LeavePreview, type LeaveRow } from "@/lib/schemas/leave";

type HalfDayPart = "FULL" | "FIRST_HALF" | "SECOND_HALF";

const applySchema = z
  .object({
    leaveTypeCode: z.string().min(1, "Choose a leave type"),
    startDate: z.string().min(1, "Start date is required"),
    endDate: z.string().min(1, "End date is required"),
    reason: z.string().trim().min(3, "Please give a short reason"),
    halfDayPart: z.enum(["FULL", "FIRST_HALF", "SECOND_HALF"]),
    attachmentRef: z.string().optional(),
  })
  .superRefine((v, ctx) => {
    if (v.startDate && v.endDate && v.endDate < v.startDate) {
      ctx.addIssue({ code: "custom", path: ["endDate"], message: "End date is before the start date" });
    }
    if (v.halfDayPart !== "FULL" && v.startDate !== v.endDate) {
      ctx.addIssue({ code: "custom", path: ["endDate"], message: "Half day applies to a single date" });
    }
  });

const STATUSES = [
  "PENDING_APPROVAL",
  "PENDING_RELIEF",
  "APPROVED",
  "REJECTED",
  "WITHDRAWN",
  "PENDING_CANCEL",
];

function warningList(warnings: LeavePreview["warnings"]): string[] {
  if (!warnings) return [];
  if (Array.isArray(warnings)) return warnings.map(String);
  return Object.entries(warnings).map(([k, v]) => (v == null || v === "" ? k : `${k}: ${String(v)}`));
}

export default function MyLeavePage() {
  const { me, can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({
    leaveTypeCode: "",
    startDate: todayIso(),
    endDate: todayIso(),
    reason: "",
    halfDayPart: "FULL" as HalfDayPart,
    attachmentRef: "",
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");
  const [status, setStatus] = useState("");
  const [withdrawId, setWithdrawId] = useState<string | number | null>(null);
  /** When true, withdraw undoes a PENDING_CANCEL request (leave stays approved). */
  const [withdrawCancelRequest, setWithdrawCancelRequest] = useState(false);
  const [cancelId, setCancelId] = useState<string | number | null>(null);

  // Preview state
  const [preview, setPreview] = useState<LeavePreview | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState("");
  const abortRef = useRef<AbortController | null>(null);
  /** Suppress auto-preview after a successful submit so the new PENDING leave is not reported as an "overlap" error. */
  const suppressPreviewUntilRef = useRef(0);

  const types = useLeaveTypes();
  const typeList = types.data ?? [];
  const effectiveType = form.leaveTypeCode || typeList[0]?.code || "";
  const selectedType = typeList.find((t) => t.code === effectiveType) ?? typeList[0];

  const attachmentMode = String(selectedType?.attachmentMode ?? "NEVER").toUpperCase();
  const attachmentAfter = Number(selectedType?.attachmentAfterDays ?? 0);
  const halfAllowed = selectedType?.halfDayAllowed !== false;

  function attachmentRequiredFor(days: number) {
    if (attachmentMode === "ALWAYS") return true;
    if (attachmentMode === "AFTER_DAYS" || attachmentMode === "AFTER_N") {
      return days > attachmentAfter;
    }
    return false;
  }

  function addCalendarDays(iso: string, days: number): string {
    const [y, m, d] = iso.split("-").map(Number);
    const dt = new Date(Date.UTC(y, m - 1, d + days, 12));
    return `${dt.getUTCFullYear()}-${String(dt.getUTCMonth() + 1).padStart(2, "0")}-${String(dt.getUTCDate()).padStart(2, "0")}`;
  }

  // ── Debounced auto-preview ────────────────────────────────────────────────
  useEffect(() => {
    if (!effectiveType || !form.startDate || !form.endDate) {
      setPreview(null);
      setPreviewError("");
      return;
    }
    // After submit success we briefly suppress preview — otherwise clearing reason/attachment
    // re-runs preview against the just-created PENDING request and shows a false overlap error.
    if (Date.now() < suppressPreviewUntilRef.current) {
      setPreview(null);
      setPreviewError("");
      setPreviewLoading(false);
      return;
    }
    abortRef.current?.abort();
    const ac = new AbortController();
    abortRef.current = ac;

    setPreviewLoading(true);
    const tid = setTimeout(() => {
      if (Date.now() < suppressPreviewUntilRef.current) {
        setPreviewLoading(false);
        return;
      }
      const isHalf = form.halfDayPart !== "FULL";
      apiFetch(api.hrms("leave/preview"), {
        method: "POST",
        body: {
          leaveTypeCode: effectiveType,
          startDate: form.startDate,
          endDate: isHalf ? form.startDate : form.endDate,
          halfDay: isHalf || undefined,
          halfDayPart: isHalf ? form.halfDayPart : undefined,
          attachmentRef: form.attachmentRef || undefined,
          reason: form.reason || "preview",
        },
        signal: ac.signal,
        schema: leavePreviewSchema,
      })
        .then((r) => {
          setPreview(r);
          setPreviewError("");
        })
        .catch((e: Error) => {
          if (e.name === "AbortError") return;
          setPreviewError(e.message);
          setPreview(null);
        })
        .finally(() => {
          if (!ac.signal.aborted) setPreviewLoading(false);
        });
    }, 420);

    return () => {
      clearTimeout(tid);
      ac.abort();
      setPreviewLoading(false);
    };
  }, [effectiveType, form.startDate, form.endDate, form.halfDayPart, form.attachmentRef, form.reason]);

  // ── Mutations ─────────────────────────────────────────────────────────────
  const apply = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.hrms("leave/request"), { method: "POST", body }),
    onSuccess: (_data, variables) => {
      toast.show("Leave request submitted");
      abortRef.current?.abort();
      suppressPreviewUntilRef.current = Date.now() + 2500;
      setFormError("");
      setPreview(null);
      setPreviewError("");
      setPreviewLoading(false);
      // Move the form off the just-submitted dates so a later preview does not immediately flag overlap.
      const submittedEnd = String(variables.endDate ?? variables.startDate ?? todayIso()).slice(0, 10);
      const nextStart = addCalendarDays(submittedEnd, 1);
      setForm((f) => ({
        ...f,
        reason: "",
        halfDayPart: "FULL",
        attachmentRef: "",
        startDate: nextStart,
        endDate: nextStart,
      }));
      void qc.invalidateQueries({ queryKey: ["my-leave"] });
      void qc.invalidateQueries({ queryKey: ["my-balances"] });
      void qc.invalidateQueries({ queryKey: ["lookup", "leave-types"] });
      void qc.invalidateQueries({ queryKey: ["approvals-pending-count"] });
    },
    onError: (e: Error) => setFormError(e.message),
  });

  const withdraw = useMutation({
    mutationFn: (id: string | number) =>
      apiFetch(api.hrms(`leave-applications/${id}/withdraw`), { method: "POST" }),
    onSuccess: () => {
      toast.show(withdrawCancelRequest ? "Cancellation withdrawn — leave stays approved" : "Request withdrawn");
      setWithdrawId(null);
      setWithdrawCancelRequest(false);
      void qc.invalidateQueries({ queryKey: ["my-leave"] });
      void qc.invalidateQueries({ queryKey: ["my-balances"] });
      void qc.invalidateQueries({ queryKey: ["approvals-pending"] });
      void qc.invalidateQueries({ queryKey: ["approvals-pending-count"] });
    },
    onError: (e: Error) => toast.error(e),
  });

  const cancelMut = useMutation({
    mutationFn: (id: string | number) =>
      apiFetch(api.hrms(`leave-applications/${id}/cancel`), { method: "POST" }),
    onSuccess: () => {
      toast.show("Cancellation requested — waiting on approval chain");
      setCancelId(null);
      void qc.invalidateQueries({ queryKey: ["my-leave"] });
      void qc.invalidateQueries({ queryKey: ["my-balances"] });
      void qc.invalidateQueries({ queryKey: ["approvals-pending"] });
      void qc.invalidateQueries({ queryKey: ["approvals-pending-count"] });
    },
    onError: (e: Error) => toast.error(e),
  });

  // ── Helpers ───────────────────────────────────────────────────────────────
  function setHalfDay(part: HalfDayPart) {
    if (part !== "FULL" && !halfAllowed) {
      setFormError("Half-day leave is not allowed for this category");
      return;
    }
    setForm((f) => ({
      ...f,
      halfDayPart: part,
      endDate: part !== "FULL" ? f.startDate : f.endDate,
    }));
  }

  const previewDays = Number(preview?.workingDaysCounted ?? (form.halfDayPart !== "FULL" ? 0.5 : 0));
  const needAttachment = attachmentRequiredFor(previewDays || 0);
  const missingAttachment = needAttachment && !form.attachmentRef.trim();
  const blockSubmit = !!previewError || previewLoading || missingAttachment || typeList.length === 0;

  function submit(e: FormEvent) {
    e.preventDefault();
    setFormError("");
    if (apply.isPending) return;
    if (blockSubmit) {
      setFormError(previewError || (missingAttachment ? "Attachment is required for this category" : "Fix preview errors before submitting"));
      return;
    }
    const isHalf = form.halfDayPart !== "FULL";
    const parsed = applySchema.safeParse({
      ...form,
      leaveTypeCode: effectiveType,
      endDate: isHalf ? form.startDate : form.endDate,
    });
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) {
        const k = String(i.path[0] ?? "form");
        if (!next[k]) next[k] = i.message;
      }
      setErrors(next);
      return;
    }
    if (needAttachment && !parsed.data.attachmentRef?.trim()) {
      setErrors({ attachmentRef: "Attachment is required for this leave category" });
      return;
    }
    setErrors({});
    // Stop in-flight preview so it cannot race the create and paint a false overlap error.
    abortRef.current?.abort();
    setPreviewError("");
    apply.mutate({
      leaveTypeCode: parsed.data.leaveTypeCode,
      startDate: parsed.data.startDate,
      endDate: parsed.data.endDate,
      reason: parsed.data.reason,
      halfDay: isHalf || undefined,
      halfDayPart: isHalf ? parsed.data.halfDayPart : undefined,
      attachmentRef: parsed.data.attachmentRef || undefined,
    });
  }

  const span =
    form.halfDayPart !== "FULL"
      ? 0.5
      : form.startDate && form.endDate
        ? daysBetween(form.startDate, form.endDate)
        : 0;

  const isPending = (s?: string | null) => String(s ?? "").toUpperCase().includes("PENDING");

  return (
    <div className="stack">
      <PageHeader
        title="My leave"
        subtitle="Apply using your current-period leave categories. Balance is shown per category."
      />

      <div className="grid g2 leave-apply">
        {/* ── Apply form ── */}
        <section className="card stack">
          <h2>Apply for leave</h2>
          {!can("leave", "request") ? (
            <p className="muted">You cannot request leave with your current roles.</p>
          ) : (
            <>
              {typeList.length > 0 ? (
                <div className="stack" style={{ gap: 6 }}>
                  <div className="small muted">Your current categories & available balance</div>
                  <div className="tbl-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>Category</th>
                          <th className="n">Available</th>
                          <th className="n">Pending</th>
                          <th>Period</th>
                        </tr>
                      </thead>
                      <tbody>
                        {typeList.map((t) => (
                          <tr
                            key={t.code}
                            style={t.code === effectiveType ? { background: "var(--accent-soft)" } : undefined}
                            onClick={() => setForm((f) => ({ ...f, leaveTypeCode: t.code }))}
                          >
                            <td>
                              <b>{t.name}</b>
                              <div className="mono small muted">{t.code}</div>
                            </td>
                            <td className="n">
                              <b>{fmtNumber(t.availableDays ?? t.balanceDays)}</b>
                            </td>
                            <td className="n">{fmtNumber(t.pendingDays ?? 0)}</td>
                            <td className="small muted">
                              {t.periodStart || t.periodEnd
                                ? `${str(t.periodStart, "…")} → ${str(t.periodEnd, "…")}`
                                : "Open"}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              ) : (
                <p className="muted">No leave categories apply to you in the current period.</p>
              )}

              <form className="stack" onSubmit={submit} noValidate>
                <FormField label="Leave type" required error={errors.leaveTypeCode}>
                  <select
                    value={effectiveType}
                    onChange={(e) => setForm({ ...form, leaveTypeCode: e.target.value, halfDayPart: "FULL" })}
                  >
                    {typeList.map((t) => (
                      <option key={t.code} value={t.code}>
                        {t.name} — {fmtNumber(t.availableDays ?? t.balanceDays)} available
                      </option>
                    ))}
                    {typeList.length === 0 ? <option value="">No leave types</option> : null}
                  </select>
                </FormField>

                <FormField label="Day type" hint={!halfAllowed ? "Half day not allowed for this category" : undefined}>
                  <select
                    value={form.halfDayPart}
                    disabled={!halfAllowed}
                    onChange={(e) => setHalfDay(e.target.value as HalfDayPart)}
                  >
                    <option value="FULL">Full day</option>
                    <option value="FIRST_HALF">First half</option>
                    <option value="SECOND_HALF">Second half</option>
                  </select>
                </FormField>

                <div className="row stack-sm" style={{ alignItems: "flex-start" }}>
                  <div style={{ flex: 1 }}>
                    <FormField label="Start" required error={errors.startDate}>
                      <input
                        type="date"
                        value={form.startDate}
                        onChange={(e) =>
                          setForm((f) => ({
                            ...f,
                            startDate: e.target.value,
                            endDate:
                              f.halfDayPart !== "FULL"
                                ? e.target.value
                                : f.endDate < e.target.value
                                  ? e.target.value
                                  : f.endDate,
                          }))
                        }
                      />
                    </FormField>
                  </div>
                  <div style={{ flex: 1 }}>
                    <FormField label="End" required error={errors.endDate}>
                      <input
                        type="date"
                        min={form.startDate}
                        value={form.halfDayPart !== "FULL" ? form.startDate : form.endDate}
                        disabled={form.halfDayPart !== "FULL" ? false : false}
                        readOnly={form.halfDayPart !== "FULL"}
                        onChange={(e) =>
                          form.halfDayPart === "FULL" && setForm({ ...form, endDate: e.target.value })
                        }
                      />
                    </FormField>
                  </div>
                </div>

                <FormField label="Reason" required error={errors.reason}>
                  <textarea
                    value={form.reason}
                    onChange={(e) => setForm({ ...form, reason: e.target.value })}
                  />
                </FormField>

                <FormField
                  label="Attachment reference"
                  required={needAttachment}
                  error={errors.attachmentRef}
                  hint={
                    attachmentMode === "ALWAYS"
                      ? "Required for this category"
                      : attachmentMode === "AFTER_DAYS" || attachmentMode === "AFTER_N"
                        ? `Required when leave exceeds ${attachmentAfter} day(s)`
                        : "Optional"
                  }
                >
                  <input
                    type="text"
                    value={form.attachmentRef}
                    placeholder="e.g. doc-123 or https://…"
                    onChange={(e) => setForm({ ...form, attachmentRef: e.target.value })}
                  />
                </FormField>

                {span > 0 ? (
                  <p className="note">
                    {form.halfDayPart !== "FULL"
                      ? `Half day (${form.halfDayPart === "FIRST_HALF" ? "first half" : "second half"})`
                      : `${span} calendar day${span === 1 ? "" : "s"}`}{" "}
                    requested. Working days exclude your weekly days off and location holidays.
                  </p>
                ) : null}

                {formError ? (
                  <p className="err" role="alert">
                    {formError}
                  </p>
                ) : null}

                <div>
                  <button
                    type="submit"
                    className="btn primary"
                    disabled={apply.isPending || blockSubmit}
                    title={previewError || (missingAttachment ? "Attachment required" : undefined)}
                  >
                    {apply.isPending ? "Submitting…" : "Submit request"}
                  </button>
                </div>
              </form>

              {/* ── Preview panel ── */}
              {(previewLoading || preview || previewError) && effectiveType ? (
                <div
                  className="notice"
                  style={{
                    borderLeft: "3px solid var(--accent)",
                    paddingLeft: 12,
                    marginTop: 4,
                  }}
                >
                  <b style={{ display: "block", marginBottom: 4 }}>
                    {previewLoading ? "Calculating preview…" : "Request preview"}
                  </b>
                  {previewError ? <p className="err small">{previewError}</p> : null}
                  {preview && !previewLoading ? (
                    <div className="stack" style={{ gap: 6 }}>
                      {preview.workingDaysCounted != null ? (
                        <div className="row between small">
                          <span className="muted">Working days</span>
                          <b>{fmtNumber(preview.workingDaysCounted)}</b>
                        </div>
                      ) : null}
                      {preview.available != null ? (
                        <div className="row between small">
                          <span className="muted">Available balance</span>
                          <b>{fmtNumber(preview.available)}</b>
                        </div>
                      ) : null}
                      {preview.pendingDays != null ? (
                        <div className="row between small">
                          <span className="muted">Pending reserved</span>
                          <b>{fmtNumber(preview.pendingDays)}</b>
                        </div>
                      ) : null}
                      {preview.balanceAfter != null ? (
                        <div className="row between small">
                          <span className="muted">Balance after</span>
                          <b style={{ color: Number(preview.balanceAfter) < 0 ? "var(--danger)" : undefined }}>
                            {fmtNumber(preview.balanceAfter)}
                          </b>
                        </div>
                      ) : null}
                      {preview.approvers && preview.approvers.length > 0 ? (
                        <div className="small">
                          <span className="muted">Approvers: </span>
                          {preview.approvers
                            .map((a) => str(a.expectedName) || str(a.name) || str(a.role) || "?")
                            .join(" → ")}
                        </div>
                      ) : null}
                      {preview.noticeRequired != null || selectedType?.noticeDays ? (
                        <div className="row between small">
                          <span className="muted">Notice</span>
                          <span>
                            {fmtNumber(preview.noticeGiven ?? 0)} given /{" "}
                            {fmtNumber(preview.noticeRequired ?? selectedType?.noticeDays ?? 0)} required
                          </span>
                        </div>
                      ) : null}
                      {preview.maxPerRequest != null || selectedType?.maxPerRequest ? (
                        <div className="row between small">
                          <span className="muted">Max per request</span>
                          <span>{fmtNumber(preview.maxPerRequest ?? selectedType?.maxPerRequest ?? 0)}</span>
                        </div>
                      ) : null}
                      {preview.teamClashAlert ? (
                        <span className="pill warn small">Team leave limit may be reached — manager will be notified</span>
                      ) : null}
                      {warningList(preview.warnings).length > 0 ? (
                        <div className="stack" style={{ gap: 2 }}>
                          {warningList(preview.warnings).map((w, i) => (
                            <span key={i} className="pill warn small">
                              {w}
                            </span>
                          ))}
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              ) : null}
            </>
          )}
        </section>

        {/* ── My requests ── */}
        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <h2>My requests</h2>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              style={{ width: 190 }}
              aria-label="Status filter"
            >
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
              {
                key: "workingDaysCounted",
                header: "Days",
                className: "n",
                render: (r) => fmtNumber(r.workingDaysCounted),
              },
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
              {
                key: "chain",
                header: "Approval chain",
                render: (r) => <ApprovalChain steps={r.approvals} />,
              },
              {
                key: "actions",
                header: "",
                render: (r) => {
                  const st = String(r.status ?? "").toUpperCase();
                  if (st === "PENDING_CANCEL" && can("leave", "request")) {
                    return (
                      <button
                        type="button"
                        className="btn sm"
                        disabled={withdraw.isPending}
                        onClick={() => {
                          setWithdrawCancelRequest(true);
                          setWithdrawId(r.id);
                        }}
                        title="Withdraw the cancellation request; leave stays approved"
                      >
                        Withdraw cancel
                      </button>
                    );
                  }
                  if (isPending(r.status) && st !== "PENDING_CANCEL" && can("leave", "request")) {
                    return (
                      <button
                        type="button"
                        className="btn sm"
                        disabled={withdraw.isPending}
                        onClick={() => {
                          setWithdrawCancelRequest(false);
                          setWithdrawId(r.id);
                        }}
                      >
                        Withdraw
                      </button>
                    );
                  }
                  if (st === "APPROVED" && can("leave", "request")) {
                    return (
                      <button
                        type="button"
                        className="btn sm danger"
                        disabled={cancelMut.isPending}
                        onClick={() => setCancelId(r.id)}
                      >
                        Cancel
                      </button>
                    );
                  }
                  return null;
                },
              },
            ]}
          />
        </section>
      </div>

      {/* ── Withdraw confirm (apply pending OR cancel pending) ── */}
      <ConfirmModal
        open={withdrawId !== null}
        title={withdrawCancelRequest ? "Withdraw cancellation request?" : "Withdraw leave request?"}
        confirmLabel="Withdraw"
        danger
        busy={withdraw.isPending}
        onCancel={() => {
          setWithdrawId(null);
          setWithdrawCancelRequest(false);
        }}
        onConfirm={() => withdrawId !== null && withdraw.mutate(withdrawId)}
      >
        {withdrawCancelRequest
          ? "The cancellation request will be withdrawn. Your leave stays approved and the balance is unchanged."
          : "The request will be withdrawn and any held balance released."}
      </ConfirmModal>

      {/* ── Cancel approved leave confirm ── */}
      <ConfirmModal
        open={cancelId !== null}
        title="Cancel approved leave?"
        confirmLabel="Request cancellation"
        danger
        busy={cancelMut.isPending}
        onCancel={() => setCancelId(null)}
        onConfirm={() => cancelId !== null && cancelMut.mutate(cancelId)}
      >
        A cancellation request will be sent to your approver. Your leave balance will be restored if approved.
      </ConfirmModal>

    </div>
  );
}
