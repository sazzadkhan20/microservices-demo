"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/components/auth/AuthProvider";
import { PayslipView, periodLabel } from "@/components/payroll/PayslipView";
import { PageHeader } from "@/components/ui/PageHeader";
import { Modal } from "@/components/ui/Modal";
import { ErrorNote } from "@/components/ui/QueryState";
import { AttCode, StatusPill } from "@/components/ui/StatusPill";
import { useToast } from "@/components/ui/Toast";
import { DAYS, parseDays } from "@/components/schedules/ScheduleEditorModal";
import { api, apiFetch } from "@/lib/api/client";
import { rowsOf, useLeaveTypes, type Rec } from "@/lib/api/lookups";
import { buildPunchBody, fmtHoursMinutes } from "@/lib/attendance/punch";
import { fmtDate, fmtDateTime, fmtHours, fmtLeaveDays, fmtMoney, fmtNumber, fmtRange, fmtTime, pick, str } from "@/lib/format";
import { resolveNotificationHref } from "@/lib/schemas/hrms";

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
}

function humanizePattern(v?: string | null) {
  const s = String(v ?? "").trim();
  if (!s) return "—";
  return s
    .toLowerCase()
    .split(/[_\s]+/)
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

export default function HomePage() {
  const { me, can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const router = useRouter();
  const canApproveLeave = can("leave", "approve");
  const canCorrect = can("attendance", "correct");
  // FR-SAL-03 — own payslips; salary:view aliases to payroll:view on BE/FE
  const canViewPay = can("payroll", "view") || can("salary", "view");
  const leaveEligible = me.leaveEligible !== false;
  const canViewAttendance = can("attendance", "view");
  const [openPayslip, setOpenPayslip] = useState<string | null>(null);

  const selfEmp = useQuery({
    queryKey: ["employee-self", me.employeeId],
    queryFn: ({ signal }) =>
      apiFetch<Rec>(api.hrms(`employees/${me.employeeId}`), { signal }),
    enabled: leaveEligible && can("leave", "request") && !!me.employeeId,
    staleTime: 60_000,
    retry: false,
  });
  const canRequestLeave =
    leaveEligible &&
    can("leave", "request") &&
    !(
      selfEmp.isSuccess &&
      (selfEmp.data?.managerId == null ||
        selfEmp.data?.managerId === "" ||
        Number(selfEmp.data.managerId) <= 0)
    );

  const today = useQuery({
    queryKey: ["attendance-today"],
    queryFn: ({ signal }) => apiFetch<Rec>(api.hrms("attendance/today"), { signal }),
    enabled: canViewAttendance,
    refetchInterval: (q) => {
      const d = q.state.data as Rec | undefined;
      return d?.hoursLive || (d?.checkIn && !d?.checkOut && String(d?.statusCode).toUpperCase() !== "M")
        ? 30_000
        : false;
    },
  });

  // Current-period eligible categories only (same gate as My leave apply form).
  const leaveTypes = useLeaveTypes(leaveEligible && can("leave", "view"));

  const balances = useQuery({
    queryKey: ["my-balances-current", me.employeeId],
    queryFn: ({ signal }) =>
      apiFetch(api.hrms(`leave-balances/employee/${me.employeeId}`), {
        query: { currentOnly: "true" },
        signal,
      }),
    enabled: leaveEligible && can("leave", "view"),
  });

  const scheduleQ = useQuery({
    queryKey: ["work-schedule-mine", me.employeeId],
    queryFn: ({ signal }) =>
      apiFetch<{
        source?: string;
        sourceId?: number | string;
        workPattern?: string;
        resolved?: Rec;
      }>(api.hrms("work-schedules/mine"), { signal }),
    enabled: !!me.employeeId && canViewAttendance,
    staleTime: 60_000,
    retry: false,
  });

  const myLeave = useQuery({
    queryKey: ["my-leave", "home"],
    queryFn: ({ signal }) => apiFetch(api.hrms("leave/my"), { query: { page: 1, size: 5 }, signal }),
    enabled: leaveEligible && can("leave", "view"),
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
  const myEstimate = useQuery({
    queryKey: ["payroll-me-estimate"],
    queryFn: ({ signal }) => apiFetch<Rec>(api.payroll("me/estimate"), { signal }),
    enabled: canViewPay,
  });
  const myPayslips = useQuery({
    queryKey: ["payroll-me-payslips"],
    queryFn: ({ signal }) => apiFetch<Rec[]>(api.payroll("me/payslips"), { signal }),
    enabled: canViewPay,
  });
  const payslipDetail = useQuery({
    queryKey: ["payroll-me-payslip", openPayslip],
    queryFn: ({ signal }) => apiFetch<Rec>(api.payroll(`me/payslips/${openPayslip}`), { signal }),
    enabled: canViewPay && !!openPayslip,
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
  const statusM = String(t?.statusCode ?? "").toUpperCase() === "M";
  // Prefer API punch gates — never revive Check out after cut-off / Missing.
  const canCheckInBtn =
    t?.canCheckIn != null ? Boolean(t.canCheckIn) : Boolean(!checkInAt && !lateNight && !statusM);
  const canCheckOutBtn =
    t?.canCheckOut != null
      ? Boolean(t.canCheckOut)
      : Boolean(!statusM && ((checkInAt && !checkOutAt) || lateNight));
  const canCheck = can("attendance", "checkin");

  const leaveBalanceRows = useMemo(() => {
    const today = new Date();
    today.setHours(12, 0, 0, 0);
    const inPeriod = (start?: string | null, end?: string | null) => {
      if (start) {
        const s = new Date(start);
        if (!Number.isNaN(s.getTime()) && s > today) return false;
      }
      if (end) {
        const e = new Date(end);
        if (!Number.isNaN(e.getTime()) && e < today) return false;
      }
      return true;
    };

    const balByCode = new Map(
      rowsOf<Rec>(balances.data).map((b) => [String(b.leaveTypeCode ?? "").toUpperCase(), b]),
    );

    // Prefer leave-types (already current-period + eligible). Enrich from currentOnly balances.
    const fromTypes = (leaveTypes.data ?? [])
      .filter((lt) => inPeriod(lt.periodStart, lt.periodEnd))
      .map((lt) => {
        const code = String(lt.code ?? "").toUpperCase();
        const b = balByCode.get(code);
        const available = Number(lt.availableDays ?? lt.balanceDays ?? b?.availableDays ?? b?.balanceDays ?? 0);
        const pending = Number(lt.pendingDays ?? b?.pendingDays ?? 0);
        const taken = Number(b?.takenDays ?? 0);
        const quota = Number(b?.quotaDays ?? 0);
        const total = quota > 0 ? quota : Math.max(available + pending + taken, available);
        return {
          key: code || String(lt.id ?? lt.name),
          label: lt.name || lt.code,
          available,
          pending,
          taken,
          total,
          period:
            lt.periodStart || lt.periodEnd
              ? `${str(lt.periodStart, "…")} → ${str(lt.periodEnd, "…")}`
              : null,
        };
      });

    if (fromTypes.length > 0) {
      // Keep leave-types catalogue order (sortOrder), not raw available ranking.
      return fromTypes.slice(0, 5);
    }

    // Fallback: currentOnly balances when leave-types is empty.
    return rowsOf<Rec>(balances.data)
      .map((b) => {
        const available = Number(b.availableDays ?? b.balanceDays ?? 0);
        const pending = Number(b.pendingDays ?? 0);
        const taken = Number(b.takenDays ?? 0);
        const quota = Number(b.quotaDays ?? 0);
        const total = quota > 0 ? quota : Math.max(available + pending + taken, available);
        return {
          key: String(b.id ?? b.leaveTypeCode),
          label: str(b.leaveTypeName, str(b.leaveTypeCode)),
          available,
          pending,
          taken,
          total,
          period: b.year != null ? `Year ${b.year}` : null,
        };
      })
      .slice(0, 5);
  }, [leaveTypes.data, balances.data]);

  const leaveRows = rowsOf<Rec>(myLeave.data);
  const noteRows = rowsOf<Rec>(notes.data);
  const leaveCount = Array.isArray(pendingLeave.data) ? pendingLeave.data.length : 0;
  const corrCount = Array.isArray(pendingCorr.data) ? pendingCorr.data.length : 0;
  const approvalCount = leaveCount + corrCount;
  const unreadCount = Number(unread.data?.unreadCount ?? 0);

  const resolved = scheduleQ.data?.resolved;
  const scheduleMode = String(resolved?.mode ?? me.scheduleMode ?? "").toUpperCase() || null;
  const scheduleHourly = scheduleMode === "HOURLY";
  const scheduleDays = parseDays(resolved?.workingDays);
  const scheduleSource = String(scheduleQ.data?.source ?? "").toUpperCase();

  return (
    <div className="stack">
      <PageHeader
        title={`${greeting()}, ${me.name.split(" ")[0]}`}
        subtitle={`${me.position || "Team member"}${me.department ? ` · ${me.department}` : ""}`}
        actions={
          canRequestLeave ? (
            <Link href="/leave" className="btn primary">
              Request leave
            </Link>
          ) : null
        }
      />

      <div className="row" style={{ gap: 8, flexWrap: "wrap" }}>
        {me.locationName ? <span className="pill info">{me.locationName}</span> : null}
        {me.locationTimeZone ? <span className="pill">{me.locationTimeZone}</span> : null}
        {me.workPattern ? <span className="pill">Pattern · {humanizePattern(me.workPattern)}</span> : null}
        {scheduleMode ? (
          <span className={`pill ${scheduleHourly ? "warn" : "info"}`}>
            Schedule · {scheduleHourly ? "Hourly" : "Fixed"}
          </span>
        ) : null}
        {!leaveEligible ? <span className="pill warn">Leave not available</span> : null}
      </div>

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
                <span className="small muted">Expected {fmtHours((t as Rec).expectedHours ?? 0)}</span>
                <span className="small muted">Logged {fmtHoursMinutes((t as Rec).loggedMinutes)}</span>
                {(t as Rec).week ? (
                  <span className="small muted">
                    Week {fmtHoursMinutes(((t as Rec).week as Rec).workedMinutes)} /{" "}
                    {fmtHours(((t as Rec).week as Rec).expectedHours ?? 0)}
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
            {canCheck && canCheckInBtn ? (
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
            {canCheck && canCheckOutBtn ? (
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
            <h2>My work schedule</h2>
            {can("attendance_settings", "view") || can("attendance_settings", "edit") ? (
              <Link href="/settings/schedules" className="btn sm">
                Details
              </Link>
            ) : null}
          </div>
          <ErrorNote error={scheduleQ.error} />
          {scheduleQ.isLoading ? <p className="muted">Loading…</p> : null}
          {!scheduleQ.isLoading && !resolved && !scheduleQ.error ? (
            <p className="muted">No schedule resolved yet — ask HR to set your location hours.</p>
          ) : null}
          {resolved || scheduleMode ? (
            <>
              <div className="row" style={{ gap: 10, flexWrap: "wrap" }}>
                <span className={`pill ${scheduleHourly ? "warn" : "info"}`}>
                  {scheduleHourly ? "Hourly" : "Fixed days"}
                </span>
                {scheduleSource ? <span className="pill">{humanizePattern(scheduleSource)} source</span> : null}
                {me.workPattern ? (
                  <span className="small muted">Work pattern {humanizePattern(me.workPattern)}</span>
                ) : null}
              </div>
              {scheduleHourly ? (
                <div className="check-in" style={{ marginTop: 4 }}>
                  <div className="stat">
                    <span className="s">Expected hours / week</span>
                    <span className="v">
                      {resolved?.weeklyHours != null ? `${fmtNumber(resolved.weeklyHours)} h` : "—"}
                    </span>
                  </div>
                  <div className="stat">
                    <span className="s">Grace</span>
                    <span className="v" style={{ fontSize: 16 }}>
                      {resolved?.graceMinutes != null ? `${resolved.graceMinutes} min` : "—"}
                    </span>
                  </div>
                </div>
              ) : (
                <>
                  <div className="row" style={{ gap: 4, flexWrap: "wrap" }}>
                    {DAYS.map((d) => (
                      <span
                        key={d}
                        className={`code ${scheduleDays.includes(d) ? "c-P" : "c-WO"}`}
                        style={{ minWidth: 34 }}
                        title={scheduleDays.includes(d) ? "Working day" : "Off"}
                      >
                        {d}
                      </span>
                    ))}
                  </div>
                  <div className="check-in" style={{ marginTop: 4 }}>
                    <div className="stat">
                      <span className="s">Office hours</span>
                      <span className="v mono" style={{ fontSize: 16 }}>
                        {str(resolved?.startTime, "—")}–{str(resolved?.endTime, "—")}
                      </span>
                    </div>
                    <div className="stat">
                      <span className="s">Half day</span>
                      <span className="v" style={{ fontSize: 16 }}>
                        {resolved?.halfDayHours != null ? `${fmtNumber(resolved.halfDayHours)} h` : "—"}
                      </span>
                    </div>
                    <div className="stat">
                      <span className="s">Grace</span>
                      <span className="v" style={{ fontSize: 16 }}>
                        {resolved?.graceMinutes != null ? `${resolved.graceMinutes} min` : "—"}
                      </span>
                    </div>
                    <div className="stat">
                      <span className="s">Cut-off</span>
                      <span className="v mono" style={{ fontSize: 16 }}>
                        {str(resolved?.checkoutCutoff, "—")}
                      </span>
                    </div>
                  </div>
                </>
              )}
            </>
          ) : null}
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

      {leaveEligible ? (
        <div className="grid g2">
          <section className="card stack">
            <div className="card-head" style={{ marginBottom: 0 }}>
              <h2>Leave balance</h2>
              <Link href="/leave" className="btn sm">
                Open
              </Link>
            </div>
            <p className="small muted" style={{ marginTop: -4 }}>
              Ongoing categories only (top {leaveBalanceRows.length || 5}). Past and future periods stay off this card.
            </p>
            <ErrorNote error={leaveTypes.error || balances.error} />
            {leaveTypes.isLoading || balances.isLoading ? <p className="muted">Loading…</p> : null}
            {!leaveTypes.isLoading && !balances.isLoading && leaveBalanceRows.length === 0 ? (
              <p className="muted">No leave categories apply to you right now.</p>
            ) : null}
            <div className="stack" style={{ gap: 12 }}>
              {leaveBalanceRows.map((b) => {
                const usedPct =
                  b.total > 0 ? Math.min(100, Math.round(((b.taken + b.pending) / b.total) * 100)) : 0;
                const availPct = b.total > 0 ? Math.min(100, Math.round((b.available / b.total) * 100)) : 0;
                return (
                  <div key={b.key} className="stack" style={{ gap: 6 }}>
                    <div className="row between" style={{ alignItems: "flex-start" }}>
                      <div className="stack" style={{ gap: 2 }}>
                        <span>{b.label}</span>
                        {b.period ? <span className="muted small">{b.period}</span> : null}
                      </div>
                      <div className="stack" style={{ gap: 2, textAlign: "right" }}>
                        <span className="num">
                          <b>{fmtLeaveDays(b.available)}</b>{" "}
                          <span className="muted small">available</span>
                        </span>
                        {b.pending > 0 ? (
                          <span className="muted small">{fmtLeaveDays(b.pending)} pending</span>
                        ) : null}
                      </div>
                    </div>
                    <div
                      className="leave-meter"
                      title={`${fmtLeaveDays(b.available)} available · ${fmtLeaveDays(b.taken)} taken${
                        b.pending > 0 ? ` · ${fmtLeaveDays(b.pending)} pending` : ""
                      }`}
                    >
                      <span className="leave-meter-avail" style={{ width: `${availPct}%` }} />
                      <span className="leave-meter-used" style={{ width: `${usedPct}%` }} />
                    </div>
                  </div>
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
                    <b>{str(r.leaveTypeCode)}</b>{" "}
                    <span className="muted small">{fmtRange(r.startDate, r.endDate)}</span>
                  </span>
                  <StatusPill status={r.status} />
                </div>
              ))}
            </div>
          </section>
        </div>
      ) : null}

      {canViewPay ? (
        <div className="grid g3">
          <section className="card stack" style={{ gridColumn: "span 2" }}>
            <div className="card-head" style={{ marginBottom: 0 }}>
              <h2>My pay</h2>
              <Link href="/payroll?tab=mypay" className="btn sm">
                Payslips
              </Link>
            </div>
            <ErrorNote error={myEstimate.error ?? myPayslips.error} />
            {myEstimate.isLoading ? <p className="muted">Loading…</p> : null}
            {(() => {
              const est = (myEstimate.data?.estimate ?? null) as Rec | null;
              const slips = rowsOf<Rec>(myPayslips.data);
              return (
                <>
                  <div className="row" style={{ gap: 24 }}>
                    <div className="stat">
                      <span className="s">This month so far (estimate)</span>
                      <span className="v">{est ? fmtMoney(est.netPay, str(est.currency, "")) : "—"}</span>
                    </div>
                    <div className="stat">
                      <span className="s">Last payslip</span>
                      <span className="v">{slips[0] ? fmtMoney(slips[0].netPay, str(slips[0].currency, "")) : "—"}</span>
                    </div>
                  </div>
                  {slips.length > 0 ? (
                    <div className="stack" style={{ gap: 6 }}>
                      {slips.slice(0, 3).map((p) => (
                        <button
                          key={String(p.period)}
                          type="button"
                          className="row between"
                          style={{
                            cursor: "pointer",
                            width: "100%",
                            textAlign: "left",
                            background: "transparent",
                            border: 0,
                            padding: "6px 0",
                            color: "inherit",
                            font: "inherit",
                          }}
                          onClick={() => setOpenPayslip(String(p.period))}
                        >
                          <span className="mono small">{periodLabel(p.period)}</span>
                          <span className="row" style={{ gap: 8 }}>
                            <b>{fmtMoney(p.netPay, str(p.currency, ""))}</b>
                            <StatusPill status={p.status} />
                          </span>
                        </button>
                      ))}
                    </div>
                  ) : (
                    <p className="muted small">No finalised payslips yet.</p>
                  )}
                </>
              );
            })()}
          </section>
        </div>
      ) : null}

      {openPayslip ? (
        <Modal
          open
          wide
          onClose={() => setOpenPayslip(null)}
          title={`Payslip · ${periodLabel(openPayslip)}`}
          footer={
            <button type="button" className="btn" onClick={() => window.print()}>
              Print
            </button>
          }
        >
          <ErrorNote error={payslipDetail.error} />
          {payslipDetail.data ? (
            <PayslipView slip={payslipDetail.data} />
          ) : !payslipDetail.error ? (
            <p className="muted">Loading…</p>
          ) : null}
        </Modal>
      ) : null}

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
    </div>
  );
}
