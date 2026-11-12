"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApprovalChain } from "@/components/leave/ApprovalChain";
import { useAuth } from "@/components/auth/AuthProvider";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { StatusPill } from "@/components/ui/StatusPill";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { byId, rowsOf, useEmployeesLookup, type Rec } from "@/lib/api/lookups";
import { fmtDate, fmtDateTime, fmtLeaveDays, fmtNumber, fmtRange, fmtTime, str } from "@/lib/format";
import type { LeaveRow } from "@/lib/schemas/leave";

const PAGE_SIZES = [10, 20, 50, 100] as const;

type Decision = {
  kind: "leave" | "corr";
  id: string | number;
  action: "approve" | "reject";
  who: string;
  /** Leave cancellation chain (PENDING_CANCEL) — avoid scanning leaveRows (may contain nulls). */
  isCancel?: boolean;
  /** Admin acting after idle threshold — requires a note. */
  onBehalf?: boolean;
};

function useClientPager<T>(rows: T[], pageSize: number, page: number, setPage: (n: number | ((p: number) => number)) => void, resetKey: string) {
  const [lastKey, setLastKey] = useState(resetKey);
  if (lastKey !== resetKey) {
    setLastKey(resetKey);
    if (page !== 1) setPage(1);
  }
  const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
  const pageSafe = Math.min(page, totalPages);
  const paged = useMemo(() => {
    const from = (pageSafe - 1) * pageSize;
    return rows.slice(from, from + pageSize);
  }, [rows, pageSafe, pageSize]);
  return { paged, pageSafe, totalPages, total: rows.length };
}

export default function ApprovalsPage() {
  const { me, can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canLeave = can("leave", "approve");
  const canCorr = can("attendance", "correct");
  const [corrStatus, setCorrStatus] = useState("PENDING");
  const [decision, setDecision] = useState<Decision | null>(null);
  const [note, setNote] = useState("");
  const [leavePage, setLeavePage] = useState(1);
  const [leavePageSize, setLeavePageSize] = useState(20);
  const [corrPage, setCorrPage] = useState(1);
  const [corrPageSize, setCorrPageSize] = useState(20);

  const employees = useEmployeesLookup(canLeave);
  const names = useMemo(() => byId(employees.data, (e) => e.fullName || `#${e.id}`), [employees.data]);

  const pending = useQuery({
    queryKey: ["approvals-pending"],
    queryFn: ({ signal }) => apiFetch(api.hrms("approvals/pending"), { signal }),
    enabled: canLeave,
  });
  const corrections = useQuery({
    queryKey: ["corrections-pending", corrStatus],
    queryFn: ({ signal }) => apiFetch(api.hrms("attendance/corrections"), { query: { status: corrStatus }, signal }),
    enabled: canCorr,
  });

  const leaveRows = rowsOf<LeaveRow & Rec>(pending.data).filter(
    (r): r is LeaveRow & Rec => r != null && r.id != null && r.id !== "",
  );
  const corrRows = rowsOf<Rec>(corrections.data).filter((r): r is Rec => r != null && r.id != null);

  const leavePager = useClientPager(
    leaveRows,
    leavePageSize,
    leavePage,
    setLeavePage,
    `leave|${leaveRows.length}|${leavePageSize}`,
  );
  const corrPager = useClientPager(
    corrRows,
    corrPageSize,
    corrPage,
    setCorrPage,
    `corr|${corrStatus}|${corrRows.length}|${corrPageSize}`,
  );

  function refresh() {
    void qc.invalidateQueries({ queryKey: ["approvals-pending"] });
    void qc.invalidateQueries({ queryKey: ["approvals-pending-count"] });
    void qc.invalidateQueries({ queryKey: ["corrections-pending"] });
    void qc.invalidateQueries({ queryKey: ["my-balances"] });
  }

  const decide = useMutation({
    mutationFn: (d: Decision & { note: string }) =>
      d.kind === "leave"
        ? apiFetch(api.hrms(`leave-applications/${d.id}/${d.action}`), {
            method: "POST",
            body: { approverEmployeeId: Number(me.employeeId), comment: d.note || undefined },
          })
        : apiFetch(api.hrms(`attendance/corrections/${d.id}/${d.action}`), {
            method: "POST",
            body: { note: d.note || undefined },
          }),
    onSuccess: (_r, d) => {
      toast.show(`${d.action === "approve" ? "Approved" : "Rejected"} — ${d.who}`);
      setDecision(null);
      setNote("");
      refresh();
    },
    onError: (e: Error) => toast.error(e),
  });

  function open(d: Decision) {
    setNote("");
    setDecision(d);
  }

  if (!canLeave && !canCorr) {
    return (
      <div className="stack">
        <PageHeader title="Approvals" />
        <div className="empty card">You do not have approval permissions.</div>
      </div>
    );
  }

  return (
    <div className="stack">
      <PageHeader
        title="Approvals"
        subtitle="Only the current chain step you can decide — or idle on-behalf for admins after 2 working days."
        actions={
          <button type="button" className="btn" onClick={refresh}>
            Refresh
          </button>
        }
      />

      {canLeave ? (
        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <h2>Leave waiting for you</h2>
            <div className="row" style={{ gap: 8, alignItems: "center" }}>
              <span className="pill acc">{leaveRows.length}</span>
              <label className="row small muted" style={{ gap: 6 }}>
                Rows
                <select
                  aria-label="Leave rows per page"
                  value={leavePageSize}
                  onChange={(e) => {
                    setLeavePageSize(Number(e.target.value));
                    setLeavePage(1);
                  }}
                  style={{ width: 72 }}
                >
                  {PAGE_SIZES.map((n) => (
                    <option key={n} value={n}>
                      {n}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          </div>
          <p className="small muted" style={{ margin: 0 }}>
            Shows items at your current chain step. On-behalf rows appear only when a step has been idle
            (≥ 2 working days) and you may act as admin — the audit log records who acted.
          </p>
          <ErrorNote error={pending.error} />
          <div className="tbl-wrap">
            <table>
              <thead>
                <tr>
                  <th>Employee</th>
                  <th>Type</th>
                  <th>Dates</th>
                  <th className="n">Days</th>
                  <th>Chain</th>
                  <th>Checks</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {leaveRows.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="empty">
                      {pending.isLoading ? "Loading…" : "Nothing pending — you're all caught up."}
                    </td>
                  </tr>
                ) : (
                  leavePager.paged.map((r) => {
                    const who = names.get(String(r.employeeId)) ?? `Employee #${r.employeeId}`;
                    const isCancel = String(r.status ?? "").toUpperCase() === "PENDING_CANCEL";
                    const onBehalf = String(r.decisionAuthority ?? "").toUpperCase() === "ON_BEHALF";
                    return (
                      <tr key={String(r.id)}>
                        <td>
                          <b>{who}</b>
                          <div className="small muted">{str(r.reason, "")}</div>
                          {isCancel ? (
                            <div className="small" style={{ marginTop: 4 }}>
                              <span className="pill warn">Cancellation request</span>
                            </div>
                          ) : null}
                          {onBehalf ? (
                            <div className="small" style={{ marginTop: 4 }}>
                              <span className="pill info" title="Step idle ≥ 2 working days — you may act on behalf">
                                On behalf (idle)
                              </span>
                            </div>
                          ) : null}
                        </td>
                        <td>{str(r.leaveTypeCode)}</td>
                        <td>{fmtRange(r.startDate, r.endDate)}</td>
                        <td className="n">{fmtLeaveDays(r.workingDaysCounted)}</td>
                        <td>
                          <ApprovalChain steps={r.approvals} />
                        </td>
                        <td>
                          <div className="stack" style={{ gap: 4 }}>
                            <StatusPill status={r.status} />
                            {r.coverageOk === false ? <span className="pill warn">Coverage clash</span> : null}
                            {r.needsRelief ? <span className="pill info">Needs relief</span> : null}
                            {r.teamClashAlert ? (
                              <span className="pill warn" title="Team leave limit may be exceeded">
                                ⚑ Team leave limit
                              </span>
                            ) : null}
                            {Array.isArray(r.warnings) && r.warnings.length > 0 ? (
                              <span
                                className="pill warn"
                                title={(r.warnings as string[]).join(" · ")}
                              >
                                ⚠ {(r.warnings as string[]).length === 1
                                  ? (r.warnings as string[])[0]
                                  : `${(r.warnings as string[]).length} warnings`}
                              </span>
                            ) : null}
                          </div>
                        </td>
                        <td>
                          <div className="row approve-actions">
                            <button
                              type="button"
                              className="btn sm primary"
                              disabled={decide.isPending}
                              onClick={() =>
                                open({ kind: "leave", id: r.id, action: "approve", who, isCancel, onBehalf })
                              }
                            >
                              {onBehalf ? "Approve on behalf" : isCancel ? "Approve cancel" : "Approve"}
                            </button>
                            <button
                              type="button"
                              className="btn sm danger"
                              disabled={decide.isPending}
                              onClick={() =>
                                open({ kind: "leave", id: r.id, action: "reject", who, isCancel, onBehalf })
                              }
                            >
                              {onBehalf ? "Reject on behalf" : isCancel ? "Deny cancel" : "Reject"}
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
          {leaveRows.length > 0 ? (
            <div className="row between">
              <span className="muted small">
                Showing {(leavePager.pageSafe - 1) * leavePageSize + 1}–
                {Math.min(leavePager.pageSafe * leavePageSize, leavePager.total)} of {leavePager.total}
                {pending.isFetching ? " · refreshing" : ""}
              </span>
              <div className="row">
                <button
                  type="button"
                  className="btn sm"
                  disabled={leavePager.pageSafe <= 1}
                  onClick={() => setLeavePage((p) => Math.max(1, p - 1))}
                >
                  Prev
                </button>
                <span className="muted small">
                  Page {leavePager.pageSafe}/{leavePager.totalPages}
                </span>
                <button
                  type="button"
                  className="btn sm"
                  disabled={leavePager.pageSafe >= leavePager.totalPages}
                  onClick={() => setLeavePage((p) => Math.min(leavePager.totalPages, p + 1))}
                >
                  Next
                </button>
              </div>
            </div>
          ) : null}
        </section>
      ) : null}

      {canCorr ? (
        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <h2>Attendance corrections</h2>
            <div className="row" style={{ gap: 8, alignItems: "center" }}>
              <select
                value={corrStatus}
                onChange={(e) => {
                  setCorrStatus(e.target.value);
                  setCorrPage(1);
                }}
                style={{ width: 140 }}
                aria-label="Status"
              >
                <option value="PENDING">Pending</option>
                <option value="ALL">All</option>
              </select>
              <label className="row small muted" style={{ gap: 6 }}>
                Rows
                <select
                  aria-label="Correction rows per page"
                  value={corrPageSize}
                  onChange={(e) => {
                    setCorrPageSize(Number(e.target.value));
                    setCorrPage(1);
                  }}
                  style={{ width: 72 }}
                >
                  {PAGE_SIZES.map((n) => (
                    <option key={n} value={n}>
                      {n}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          </div>
          <ErrorNote error={corrections.error} />
          <div className="tbl-wrap">
            <table>
              <thead>
                <tr>
                  <th>Employee</th>
                  <th>Date</th>
                  <th>Requested</th>
                  <th>Reason</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {corrRows.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="empty">
                      {corrections.isLoading ? "Loading…" : "No correction requests"}
                    </td>
                  </tr>
                ) : (
                  corrPager.paged.map((r) => {
                    const who = str(r.employeeName, `Employee #${r.employeeId}`);
                    const isPending = String(r.status).toUpperCase() === "PENDING";
                    const isOwn = String(r.employeeId) === String(me.employeeId);
                    return (
                      <tr key={String(r.id)}>
                        <td>
                          <b>{who}</b>
                        </td>
                        <td>{fmtDate(r.workDate)}</td>
                        <td className="mono small">
                          <div>
                            {fmtDateTime(r.requestedCheckIn)} → {fmtDateTime(r.requestedCheckOut)}
                          </div>
                          {r.originalCheckIn || r.originalCheckOut ? (
                            <div className="muted">
                              Original: {fmtTime(r.originalCheckIn)} – {fmtTime(r.originalCheckOut)}
                              {r.originalStatusCode ? ` (${str(r.originalStatusCode)})` : ""}
                            </div>
                          ) : null}
                        </td>
                        <td>
                          {str(r.reason, "")}
                          {r.reviewNote ? <div className="small muted">Note: {String(r.reviewNote)}</div> : null}
                        </td>
                        <td>
                          <StatusPill status={r.status} />
                        </td>
                        <td>
                          {isPending && !isOwn ? (
                            <div className="row approve-actions">
                              <button
                                type="button"
                                className="btn sm primary"
                                disabled={decide.isPending}
                                onClick={() => open({ kind: "corr", id: r.id as number, action: "approve", who })}
                              >
                                Approve
                              </button>
                              <button
                                type="button"
                                className="btn sm danger"
                                disabled={decide.isPending}
                                onClick={() => open({ kind: "corr", id: r.id as number, action: "reject", who })}
                              >
                                Reject
                              </button>
                            </div>
                          ) : isPending && isOwn ? (
                            <span className="small muted">Your request</span>
                          ) : null}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
          {corrRows.length > 0 ? (
            <div className="row between">
              <span className="muted small">
                Showing {(corrPager.pageSafe - 1) * corrPageSize + 1}–
                {Math.min(corrPager.pageSafe * corrPageSize, corrPager.total)} of {corrPager.total}
                {corrections.isFetching ? " · refreshing" : ""}
              </span>
              <div className="row">
                <button
                  type="button"
                  className="btn sm"
                  disabled={corrPager.pageSafe <= 1}
                  onClick={() => setCorrPage((p) => Math.max(1, p - 1))}
                >
                  Prev
                </button>
                <span className="muted small">
                  Page {corrPager.pageSafe}/{corrPager.totalPages}
                </span>
                <button
                  type="button"
                  className="btn sm"
                  disabled={corrPager.pageSafe >= corrPager.totalPages}
                  onClick={() => setCorrPage((p) => Math.min(corrPager.totalPages, p + 1))}
                >
                  Next
                </button>
              </div>
            </div>
          ) : null}
        </section>
      ) : null}

      <Modal
        open={!!decision}
        onClose={() => setDecision(null)}
        title={
          decision
            ? `${decision.onBehalf ? "On behalf · " : ""}${decision.action === "approve" ? "Approve" : "Reject"} — ${decision.who}`
            : ""
        }
        subtitle={
          decision?.kind === "leave"
            ? decision.onBehalf
              ? "Acting on the expected approver's behalf after idle threshold — note is required and logged."
              : decision.isCancel
                ? "Leave cancellation request"
                : "Leave request at your chain step"
            : "Attendance correction"
        }
        footer={
          <>
            <button type="button" className="btn" onClick={() => setDecision(null)}>
              Cancel
            </button>
            <button
              type="button"
              className={`btn ${decision?.action === "reject" ? "danger" : "primary"}`}
              disabled={
                decide.isPending ||
                ((decision?.action === "reject" || decision?.onBehalf) && note.trim().length < 3)
              }
              onClick={() => decision && decide.mutate({ ...decision, note: note.trim() })}
            >
              {decide.isPending
                ? "Saving…"
                : decision?.action === "approve"
                  ? decision.onBehalf
                    ? "Confirm on-behalf approval"
                    : decision.isCancel
                      ? "Confirm cancel approval"
                      : "Confirm approval"
                  : decision?.isCancel
                    ? "Deny cancellation"
                    : "Confirm rejection"}
            </button>
          </>
        }
      >
        <label className="field">
          <span>{decision?.action === "reject" ? "Reason (required)" : "Comment (optional)"}</span>
          <textarea value={note} onChange={(e) => setNote(e.target.value)} autoFocus />
        </label>
      </Modal>
    </div>
  );
}
