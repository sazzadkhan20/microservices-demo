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
import { fmtDate, fmtDateTime, fmtNumber, fmtRange, str } from "@/lib/format";
import type { LeaveRow } from "@/lib/schemas/leave";

type Decision = { kind: "leave" | "corr"; id: string | number; action: "approve" | "reject"; who: string };

export default function ApprovalsPage() {
  const { me, can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canLeave = can("leave", "approve");
  const canCorr = can("attendance", "correct");
  const [corrStatus, setCorrStatus] = useState("PENDING");
  const [decision, setDecision] = useState<Decision | null>(null);
  const [note, setNote] = useState("");

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

  const leaveRows = rowsOf<LeaveRow & Rec>(pending.data);
  const corrRows = rowsOf<Rec>(corrections.data);

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
        subtitle="Leave requests and attendance corrections waiting on a decision."
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
            <span className="pill acc">{leaveRows.length}</span>
          </div>
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
                  leaveRows.map((r) => {
                    const who = names.get(String(r.employeeId)) ?? `Employee #${r.employeeId}`;
                    return (
                      <tr key={String(r.id)}>
                        <td>
                          <b>{who}</b>
                          <div className="small muted">{str(r.reason, "")}</div>
                        </td>
                        <td>{str(r.leaveTypeCode)}</td>
                        <td>{fmtRange(r.startDate, r.endDate)}</td>
                        <td className="n">{fmtNumber(r.workingDaysCounted)}</td>
                        <td>
                          <ApprovalChain steps={r.approvals} />
                        </td>
                        <td>
                          <div className="stack" style={{ gap: 4 }}>
                            <StatusPill status={r.status} />
                            {r.coverageOk === false ? <span className="pill warn">Coverage clash</span> : null}
                            {r.needsRelief ? <span className="pill info">Needs relief</span> : null}
                          </div>
                        </td>
                        <td>
                          <div className="row" style={{ flexWrap: "nowrap", justifyContent: "flex-end" }}>
                            <button
                              type="button"
                              className="btn sm primary"
                              disabled={decide.isPending}
                              onClick={() => open({ kind: "leave", id: r.id, action: "approve", who })}
                            >
                              Approve
                            </button>
                            <button
                              type="button"
                              className="btn sm danger"
                              disabled={decide.isPending}
                              onClick={() => open({ kind: "leave", id: r.id, action: "reject", who })}
                            >
                              Reject
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
        </section>
      ) : null}

      {canCorr ? (
        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <h2>Attendance corrections</h2>
            <select value={corrStatus} onChange={(e) => setCorrStatus(e.target.value)} style={{ width: 140 }} aria-label="Status">
              <option value="PENDING">Pending</option>
              <option value="ALL">All</option>
            </select>
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
                  corrRows.map((r) => {
                    const who = str(r.employeeName, `Employee #${r.employeeId}`);
                    const isPending = String(r.status).toUpperCase() === "PENDING";
                    return (
                      <tr key={String(r.id)}>
                        <td>
                          <b>{who}</b>
                        </td>
                        <td>{fmtDate(r.workDate)}</td>
                        <td className="mono small">
                          {fmtDateTime(r.requestedCheckIn)} → {fmtDateTime(r.requestedCheckOut)}
                        </td>
                        <td>
                          {str(r.reason, "")}
                          {r.reviewNote ? <div className="small muted">Note: {String(r.reviewNote)}</div> : null}
                        </td>
                        <td>
                          <StatusPill status={r.status} />
                        </td>
                        <td>
                          {isPending ? (
                            <div className="row" style={{ flexWrap: "nowrap", justifyContent: "flex-end" }}>
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
                          ) : null}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}

      <Modal
        open={!!decision}
        onClose={() => setDecision(null)}
        title={decision ? `${decision.action === "approve" ? "Approve" : "Reject"} — ${decision.who}` : ""}
        subtitle={decision?.kind === "leave" ? "Leave request" : "Attendance correction"}
        footer={
          <>
            <button type="button" className="btn" onClick={() => setDecision(null)}>
              Cancel
            </button>
            <button
              type="button"
              className={`btn ${decision?.action === "reject" ? "danger" : "primary"}`}
              disabled={decide.isPending || (decision?.action === "reject" && note.trim().length < 3)}
              onClick={() => decision && decide.mutate({ ...decision, note: note.trim() })}
            >
              {decide.isPending ? "Saving…" : decision?.action === "approve" ? "Confirm approval" : "Confirm rejection"}
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
