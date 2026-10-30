"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/components/auth/AuthProvider";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { AttCode, StatusPill } from "@/components/ui/StatusPill";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { rowsOf, type Rec } from "@/lib/api/lookups";
import { buildPunchBody, fmtHoursMinutes } from "@/lib/attendance/punch";
import { fmtDate, fmtDateTime, fmtMoney, fmtNumber, fmtRange, fmtTime, pick, str } from "@/lib/format";
import { resolveNotificationHref } from "@/lib/schemas/hrms";

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
}

export default function HomePage() {
  const { me, can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const router = useRouter();
  const canApproveLeave = can("leave", "approve");
  const canCorrect = can("attendance", "correct");
  const canViewPay = can("payroll", "view");

  const today = useQuery({
    queryKey: ["attendance-today"],
    queryFn: ({ signal }) => apiFetch<Rec>(api.hrms("attendance/today"), { signal }),
    enabled: can("attendance", "view"),
    refetchInterval: (q) => {
      const d = q.state.data as Rec | undefined;
      return d?.hoursLive || (d?.checkIn && !d?.checkOut && String(d?.statusCode).toUpperCase() !== "M")
        ? 30_000
        : false;
    },
  });
  const balances = useQuery({
    queryKey: ["my-balances", me.employeeId],
    queryFn: ({ signal }) => apiFetch(api.hrms(`leave-balances/employee/${me.employeeId}`), { signal }),
    enabled: can("leave", "view"),
  });
  const myLeave = useQuery({
    queryKey: ["my-leave", "home"],
    queryFn: ({ signal }) => apiFetch(api.hrms("leave/my"), { query: { page: 1, size: 5 }, signal }),
    enabled: can("leave", "view"),
  });
  const pendingLeave = useQuery({
    queryKey: ["approvals-pending-count"],
    queryFn: ({ signal }) => apiFetch(api.hrms("approvals/pending"), { signal }),
    enabled: canApproveLeave,
  });
  const pendingCorr = useQuery({
    queryKey: ["corrections-pending"],
    queryFn: ({ signal }) => apiFetch(api.hrms("attendance/corrections"), { query: { status: "PENDING" }, signal }),
    enabled: canCorrect,
  });
  const notes = useQuery({
    queryKey: ["notifications", "home"],
    queryFn: ({ signal }) => apiFetch(api.notifications(""), { query: { page: 0, size: 5 }, signal }),
    retry: false,
  });
  const myCurrent = useQuery({
    queryKey: ["payroll-me-current"],
    queryFn: ({ signal }) => apiFetch<Rec>(api.payroll("me/current"), { signal }),
    enabled: canViewPay,
  });
  const unread = useQuery({
    queryKey: ["notifications-unread"],
    queryFn: ({ signal }) => apiFetch<{ unreadCount?: number }>(api.notifications("unread-count"), { signal }),
    retry: false,
  });

  const [attActionError, setAttActionError] = useState("");
  const refreshToday = () => {
    void qc.invalidateQueries({ queryKey: ["attendance-today"] });
    void qc.invalidateQueries({ queryKey: ["my-attendance"] });
  };
  const checkIn = useMutation({
    mutationFn: async () => {
      const body = await buildPunchBody();
      return apiFetch(api.hrms("attendance/check-in"), { method: "POST", body });
    },
    onSuccess: () => {
      setAttActionError("");
      toast.show("Checked in");
      refreshToday();
    },
    onError: (e: Error) => setAttActionError(e.message || "Check-in failed"),
  });
  const checkOut = useMutation({
    mutationFn: async () => {
      const body = await buildPunchBody();
      return apiFetch(api.hrms("attendance/check-out"), { method: "POST", body });
    },
    onSuccess: () => {
      setAttActionError("");
      toast.show("Checked out");
      refreshToday();
    },
    onError: (e: Error) => setAttActionError(e.message || "Check-out failed"),
  });

  const t = today.data;
  const checkInAt = t ? pick(t, "checkIn", "checkInAt") : null;
  const checkOutAt = t ? pick(t, "checkOut", "checkOutAt") : null;
  const lateNight = Boolean(t?.lateNightCheckoutAvailable);
  const canCheck = can("attendance", "checkin");

  const bal = rowsOf<Rec>(balances.data);
  const leaveRows = rowsOf<Rec>(myLeave.data);
  const noteRows = rowsOf<Rec>(notes.data);
  const leaveCount = Array.isArray(pendingLeave.data) ? pendingLeave.data.length : 0;
  const corrCount = Array.isArray(pendingCorr.data) ? pendingCorr.data.length : 0;
  const approvalCount = leaveCount + corrCount;
  const unreadCount = Number(unread.data?.unreadCount ?? 0);

  return (
    <div className="stack">
      <PageHeader
        title={`${greeting()}, ${me.name.split(" ")[0]}`}
        subtitle={`${me.position || "Team member"}${me.department ? ` · ${me.department}` : ""}`}
        actions={
          can("leave", "request") ? (
            <Link href="/leave" className="btn primary">
              Request leave
            </Link>
          ) : null
        }
      />

      <div className="grid g3">
        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <h2>Attendance today</h2>
            {t ? <AttCode code={t.statusCode} label={t.status} /> : null}
          </div>
          <ErrorNote error={today.error} />
          {today.isLoading ? <p className="muted">Loading…</p> : null}
          {t ? (
            <>
              <div className="check-in">
                <div className="stat">
                  <span className="s">Checked in</span>
                  <span className="v">{fmtTime(checkInAt)}</span>
                </div>
                <div className="stat">
                  <span className="s">Checked out</span>
                  <span className="v">{fmtTime(checkOutAt)}</span>
                </div>
                <div className="stat">
                  <span className="s">Worked</span>
                  <span className="v">{checkInAt ? str(t.workingHours, "0h") : "—"}</span>
                </div>
              </div>
              <div className="row" style={{ gap: 16, flexWrap: "wrap" }}>
                <span className="small muted">
                  Expected {fmtNumber((t as Rec).expectedHours ?? 0)}h
                </span>
                <span className="small muted">
                  Logged {fmtHoursMinutes((t as Rec).loggedMinutes)}
                </span>
                {(t as Rec).week ? (
                  <span className="small muted">
                    Week {fmtHoursMinutes(((t as Rec).week as Rec).workedMinutes)} /{" "}
                    {fmtNumber(((t as Rec).week as Rec).expectedHours ?? 0)}h
                  </span>
                ) : null}
              </div>
              <p className="muted small">
                {fmtDate(t.date)} · {str(t.timeZone)}
                {t.locationLabel ? ` · ${String(t.locationLabel)}` : ""}
                {t.deviceType ? ` · ${String(t.deviceType)}` : ""}
              </p>
            </>
          ) : null}
          <div className="row">
            {canCheck && !checkInAt && !lateNight ? (
              <button
                type="button"
                className="btn primary"
                disabled={checkIn.isPending}
                onClick={() => {
                  setAttActionError("");
                  checkIn.mutate();
                }}
              >
                Check in
              </button>
            ) : null}
            {canCheck && ((checkInAt && !checkOutAt) || lateNight) ? (
              <button
                type="button"
                className="btn primary"
                disabled={checkOut.isPending}
                onClick={() => {
                  setAttActionError("");
                  checkOut.mutate();
                }}
              >
                {lateNight ? "Check out (yesterday)" : "Check out"}
              </button>
            ) : null}
            <Link href="/attendance" className="btn sm">
              History
            </Link>
          </div>
          {attActionError ? <p className="err" role="alert">{attActionError}</p> : null}
        </section>

        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <h2>Leave balance</h2>
            <Link href="/leave" className="btn sm">
              Open
            </Link>
          </div>
          <ErrorNote error={balances.error} />
          {balances.isLoading ? <p className="muted">Loading…</p> : null}
          {!balances.isLoading && bal.length === 0 && !balances.error ? <p className="muted">No balances yet.</p> : null}
          <div className="stack" style={{ gap: 8 }}>
            {bal.slice(0, 5).map((b) => (
              <div key={String(b.id)} className="row between">
                <span>{str(b.leaveTypeCode)}</span>
                <span className="num">
                  <b>{fmtNumber(b.balanceDays)}</b> <span className="muted small">days</span>
                </span>
              </div>
            ))}
          </div>
        </section>

        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <h2>Pending approvals</h2>
            {canApproveLeave || canCorrect ? (
              <Link href="/approvals" className="btn sm">
                Review
              </Link>
            ) : null}
          </div>
          {canApproveLeave || canCorrect ? (
            <>
              <div className="stat">
                <span className="v">{approvalCount}</span>
                <span className="s">
                  {leaveCount} leave · {corrCount} attendance correction{corrCount === 1 ? "" : "s"}
                </span>
              </div>
              <ErrorNote error={pendingLeave.error || pendingCorr.error} />
            </>
          ) : (
            <p className="muted">Nothing waiting on you. Approvals appear here for managers and HR.</p>
          )}
        </section>
      </div>

      {canViewPay ? (
        <div className="grid g3">
          <section className="card stack" style={{ gridColumn: "span 2" }}>
            <div className="card-head" style={{ marginBottom: 0 }}>
              <h2>My pay</h2>
              <Link href="/payroll" className="btn sm">
                Payslips
              </Link>
            </div>
            <ErrorNote error={myCurrent.error} />
            {myCurrent.isLoading ? <p className="muted">Loading…</p> : null}
            {myCurrent.data ? (
              <>
                <div className="row" style={{ gap: 24 }}>
                  <div className="stat">
                    <span className="s">Current pay</span>
                    <span className="v">{fmtMoney((myCurrent.data as Rec).currentPay)}</span>
                  </div>
                  <div className="stat">
                    <span className="s">MTD estimate</span>
                    <span className="v">{fmtMoney((myCurrent.data as Rec).mtdEstimate)}</span>
                  </div>
                </div>
                {rowsOf<Rec>((myCurrent.data as Rec).payslips).length > 0 ? (
                  <div className="stack" style={{ gap: 6 }}>
                    {rowsOf<Rec>((myCurrent.data as Rec).payslips)
                      .slice(0, 3)
                      .map((p, i) => (
                        <div key={String(p.id ?? i)} className="row between">
                          <span className="mono small">{str(p.periodLabel)}</span>
                          <span className="row" style={{ gap: 8 }}>
                            <b>{fmtMoney(p.netAmount)}</b>
                            <StatusPill status={p.status} />
                          </span>
                        </div>
                      ))}
                  </div>
                ) : (
                  <p className="muted small">No finalized payslips yet.</p>
                )}
              </>
            ) : null}
          </section>
        </div>
      ) : null}

      <div className="grid g2">
        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <h2>
              Notifications {unreadCount > 0 ? <span className="pill acc">{unreadCount} new</span> : null}
            </h2>
            <Link href="/notifications" className="btn sm">
              All
            </Link>
          </div>
          {notes.isLoading ? <p className="muted">Loading…</p> : null}
          {noteRows.length === 0 && !notes.isLoading ? <p className="muted">You&apos;re all caught up.</p> : null}
          <div className="stack" style={{ gap: 8 }}>
            {noteRows.map((n, i) => {
              const href = resolveNotificationHref({
                linkUrl: str(n.linkUrl, "") || null,
                category: str(n.category, "") || null,
              });
              const open = async () => {
                if (!n.readFlag && n.id != null) {
                  try {
                    await apiFetch(api.notifications(`${n.id}/read`), { method: "POST" });
                    void qc.invalidateQueries({ queryKey: ["notifications"] });
                    void qc.invalidateQueries({ queryKey: ["notifications-unread"] });
                  } catch {
                    /* navigate anyway */
                  }
                }
                if (href) router.push(href);
                else router.push("/notifications");
              };
              return (
                <button
                  key={String(n.id ?? i)}
                  type="button"
                  className="note"
                  onClick={() => void open()}
                  style={{
                    textAlign: "left",
                    width: "100%",
                    cursor: "pointer",
                    border: "none",
                    background: "transparent",
                    padding: 0,
                  }}
                >
                  <div className="row between">
                    <b style={{ color: "var(--fg)" }}>{str(n.title, "Notification")}</b>
                    <span className="small">{fmtDateTime(n.createdAt)}</span>
                  </div>
                  <div>{str(n.body, "")}</div>
                </button>
              );
            })}
          </div>
        </section>

        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <h2>My recent leave</h2>
            <Link href="/leave" className="btn sm">
              All
            </Link>
          </div>
          {myLeave.isLoading ? <p className="muted">Loading…</p> : null}
          {leaveRows.length === 0 && !myLeave.isLoading ? <p className="muted">No leave requests yet.</p> : null}
          <div className="stack" style={{ gap: 8 }}>
            {leaveRows.map((r) => (
              <div key={String(r.id)} className="row between">
                <span>
                  <b>{str(r.leaveTypeCode)}</b> <span className="muted small">{fmtRange(r.startDate, r.endDate)}</span>
                </span>
                <StatusPill status={r.status} />
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}
