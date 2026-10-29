"use client";

import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/components/auth/AuthProvider";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { AttCode, StatusPill } from "@/components/ui/StatusPill";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { rowsOf, type Rec } from "@/lib/api/lookups";
import { fmtDate, fmtDateTime, fmtNumber, fmtRange, fmtTime, pick, str } from "@/lib/format";

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
}

export default function HomePage() {
  const { me, can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canApproveLeave = can("leave", "approve");
  const canCorrect = can("attendance", "correct");

  const today = useQuery({
    queryKey: ["attendance-today"],
    queryFn: ({ signal }) => apiFetch<Rec>(api.hrms("attendance/today"), { signal }),
    enabled: can("attendance", "view"),
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
  const unread = useQuery({
    queryKey: ["notifications-unread"],
    queryFn: ({ signal }) => apiFetch<{ unreadCount?: number }>(api.notifications("unread-count"), { signal }),
    retry: false,
  });

  const refreshToday = () => {
    void qc.invalidateQueries({ queryKey: ["attendance-today"] });
    void qc.invalidateQueries({ queryKey: ["my-attendance"] });
  };
  const checkIn = useMutation({
    mutationFn: () => apiFetch(api.hrms("attendance/check-in"), { method: "POST", body: {} }),
    onSuccess: () => {
      toast.show("Checked in");
      refreshToday();
    },
    onError: (e: Error) => toast.error(e),
  });
  const checkOut = useMutation({
    mutationFn: () => apiFetch(api.hrms("attendance/check-out"), { method: "POST", body: {} }),
    onSuccess: () => {
      toast.show("Checked out");
      refreshToday();
    },
    onError: (e: Error) => toast.error(e),
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
              <p className="muted small">
                {fmtDate(t.date)} · {str(t.timeZone)}
                {t.locationLabel ? ` · ${String(t.locationLabel)}` : ""}
              </p>
            </>
          ) : null}
          <div className="row">
            {canCheck && !checkInAt && !lateNight ? (
              <button type="button" className="btn primary" disabled={checkIn.isPending} onClick={() => checkIn.mutate()}>
                Check in
              </button>
            ) : null}
            {canCheck && ((checkInAt && !checkOutAt) || lateNight) ? (
              <button type="button" className="btn primary" disabled={checkOut.isPending} onClick={() => checkOut.mutate()}>
                {lateNight ? "Check out (yesterday)" : "Check out"}
              </button>
            ) : null}
            <Link href="/attendance" className="btn sm">
              History
            </Link>
          </div>
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
            {noteRows.map((n, i) => (
              <div key={String(n.id ?? i)} className="note">
                <div className="row between">
                  <b style={{ color: "var(--fg)" }}>{str(n.title, "Notification")}</b>
                  <span className="small">{fmtDateTime(n.createdAt)}</span>
                </div>
                <div>{str(n.body, "")}</div>
              </div>
            ))}
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
