"use client";

import { useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { DataTable } from "@/components/ui/DataTable";
import { FormField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { AttCode, StatusPill } from "@/components/ui/StatusPill";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch, listParams } from "@/lib/api/client";
import { rowsOf, type Rec } from "@/lib/api/lookups";
import { buildPunchBody, fmtHoursMinutes, localRangeToIso } from "@/lib/attendance/punch";
import { fmtDate, fmtDateTime, fmtNumber, fmtTime, isoDate, pick, str, todayIso } from "@/lib/format";

const rowSchema = z.object({ workDate: z.any().optional() }).passthrough();
type Row = Rec;

const correctionSchema = z
  .object({
    workDate: z.string().min(1, "Pick the date to correct"),
    checkInLocal: z.string().min(1, "Check-in time is required"),
    checkOutLocal: z.string().min(1, "Check-out time is required"),
    reason: z.string().trim().min(3, "Please give a reason"),
  })
  .superRefine((v, ctx) => {
    if (v.workDate && v.workDate > todayIso()) {
      ctx.addIssue({ code: "custom", path: ["workDate"], message: "Cannot correct a future date" });
    }
    // Overnight OK — same clock time next day is resolved in localRangeToIso.
  });

const pad = (n: number) => String(n).padStart(2, "0");
const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

export default function MyAttendancePage() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ workDate: todayIso(), checkInLocal: "10:00", checkOutLocal: "18:00", reason: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");
  const [attActionError, setAttActionError] = useState("");
  const now = new Date();
  const [month, setMonth] = useState({ y: now.getFullYear(), m: now.getMonth() });

  const canCheck = can("attendance", "checkin");

  const today = useQuery({
    queryKey: ["attendance-today"],
    queryFn: ({ signal }) => apiFetch<Rec>(api.hrms("attendance/today"), { signal }),
    refetchInterval: (q) => {
      const d = q.state.data as Rec | undefined;
      return d?.hoursLive || (d?.checkIn && !d?.checkOut && String(d?.statusCode).toUpperCase() !== "M")
        ? 30_000
        : false;
    },
  });
  const mine = useQuery({
    queryKey: ["my-corrections"],
    queryFn: ({ signal }) => apiFetch(api.hrms("attendance/corrections/my"), { signal }),
    enabled: canCheck,
  });

  const monthRows = useQuery({
    queryKey: ["my-attendance-month"],
    queryFn: async ({ signal }) =>
      rowsOf<Rec>(await apiFetch(api.hrms("attendance/my"), { query: listParams({ page: 1, size: 500, sort: "workDate,desc" }), signal })),
  });
  const byDate = useMemo(() => {
    const m = new Map<string, Rec>();
    for (const r of monthRows.data ?? []) m.set(isoDate(r.workDate), r);
    return m;
  }, [monthRows.data]);
  const cells = useMemo(() => {
    const first = new Date(month.y, month.m, 1);
    const lead = (first.getDay() + 6) % 7; // Monday-first
    const days = new Date(month.y, month.m + 1, 0).getDate();
    return [...Array.from({ length: lead }, () => 0), ...Array.from({ length: days }, (_, i) => i + 1)];
  }, [month]);
  const monthLabel = new Date(month.y, month.m, 1).toLocaleDateString(undefined, { month: "long", year: "numeric" });
  function shiftMonth(delta: number) {
    const d = new Date(month.y, month.m + delta, 1);
    setMonth({ y: d.getFullYear(), m: d.getMonth() });
  }
  function correctDay(iso: string) {
    setForm((f) => ({ ...f, workDate: iso }));
    setErrors({});
    setErr("");
    setOpen(true);
  }

  const t = today.data;
  const inAt = t ? pick(t, "checkIn", "checkInAt") : null;
  const outAt = t ? pick(t, "checkOut", "checkOutAt") : null;
  const lateNight = Boolean(t?.lateNightCheckoutAvailable);

  function refresh() {
    void qc.invalidateQueries({ queryKey: ["attendance-today"] });
    void qc.invalidateQueries({ queryKey: ["my-attendance"] });
    void qc.invalidateQueries({ queryKey: ["my-attendance-month"] });
  }
  const checkIn = useMutation({
    mutationFn: async () => {
      const body = await buildPunchBody();
      return apiFetch(api.hrms("attendance/check-in"), { method: "POST", body });
    },
    onSuccess: () => {
      setAttActionError("");
      toast.show("Checked in");
      refresh();
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
      refresh();
    },
    onError: (e: Error) => setAttActionError(e.message || "Check-out failed"),
  });

  const request = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.hrms("attendance/corrections"), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Correction requested");
      setOpen(false);
      setErr("");
      setForm((f) => ({ ...f, reason: "" }));
      void qc.invalidateQueries({ queryKey: ["my-corrections"] });
    },
    onError: (e: Error) => setErr(e.message),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = correctionSchema.safeParse(form);
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
    const times = localRangeToIso(parsed.data.workDate, parsed.data.checkInLocal, parsed.data.checkOutLocal);
    request.mutate({
      workDate: parsed.data.workDate,
      ...times,
      reason: parsed.data.reason,
    });
  }

  const myRows = rowsOf<Rec>(mine.data);

  return (
    <div className="stack">
      <PageHeader
        title="My attendance"
        subtitle="Today's status, your history and correction requests."
        actions={
          canCheck ? (
            <button
              type="button"
              className="btn"
              onClick={() => {
                setErrors({});
                setErr("");
                setOpen(true);
              }}
            >
              Request correction
            </button>
          ) : null
        }
      />

      <section className="card stack">
        <div className="card-head" style={{ marginBottom: 0 }}>
          <h2>Today</h2>
          {t ? <AttCode code={t.statusCode} label={t.status} /> : null}
        </div>
        <ErrorNote error={today.error} />
        <div className="check-in">
          <div className="stat">
            <span className="s">Checked in</span>
            <span className="big-time">{fmtTime(inAt)}</span>
          </div>
          <div className="stat">
            <span className="s">Checked out</span>
            <span className="big-time">{fmtTime(outAt)}</span>
          </div>
          <div className="stat">
            <span className="s">Worked</span>
            <span className="big-time">{inAt ? str(t?.workingHours, "—") : "—"}</span>
          </div>
          <div className="grow" />
          <div className="row">
            {canCheck && !inAt && !lateNight ? (
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
            {canCheck && ((inAt && !outAt) || lateNight) ? (
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
          </div>
          {attActionError ? <p className="err" role="alert">{attActionError}</p> : null}
        </div>
        <p className="muted small" style={{ margin: 0 }}>
          {t?.locationLabel ? String(t.locationLabel) : "Location not shared"}
          {t?.deviceType ? ` · ${String(t.deviceType)}` : ""}
          {t?.timeZone ? ` · ${String(t.timeZone)}` : ""}
        </p>
      </section>

      <div className="grid g3">
        <div className="card stack" style={{ gap: 4 }}>
          <span className="muted small">Today</span>
          <b>
            {fmtHoursMinutes(t?.workedMinutes)} / {fmtNumber(t?.expectedHours ?? 0)}h expected
          </b>
          <span className="small muted">Logged {fmtHoursMinutes(t?.loggedMinutes)}</span>
        </div>
        <div className="card stack" style={{ gap: 4 }}>
          <span className="muted small">This week</span>
          <b>
            {fmtHoursMinutes((t?.week as Rec | undefined)?.workedMinutes)} /{" "}
            {fmtNumber((t?.week as Rec | undefined)?.expectedHours ?? 0)}h
          </b>
          <span className="small muted">
            Logged {fmtHoursMinutes((t?.week as Rec | undefined)?.loggedMinutes)}
          </span>
        </div>
        <div className="card stack" style={{ gap: 4 }}>
          <span className="muted small">This month</span>
          <b>
            {fmtHoursMinutes((t?.month as Rec | undefined)?.workedMinutes)} /{" "}
            {fmtNumber((t?.month as Rec | undefined)?.expectedHours ?? 0)}h
          </b>
          <span className="small muted">
            Logged {fmtHoursMinutes((t?.month as Rec | undefined)?.loggedMinutes)}
          </span>
        </div>
      </div>

      <section className="card stack">
        <div className="card-head" style={{ marginBottom: 0 }}>
          <h2>{monthLabel}</h2>
          <div className="row">
            <button type="button" className="btn sm" onClick={() => shiftMonth(-1)} aria-label="Previous month">
              ←
            </button>
            <button type="button" className="btn sm" onClick={() => setMonth({ y: now.getFullYear(), m: now.getMonth() })}>
              This month
            </button>
            <button type="button" className="btn sm" onClick={() => shiftMonth(1)} aria-label="Next month">
              →
            </button>
          </div>
        </div>
        <ErrorNote error={monthRows.error} />
        <div className="cal">
          {WEEKDAYS.map((w) => (
            <div key={w} className="h">
              {w}
            </div>
          ))}
          {cells.map((day, i) => {
            if (!day) return <div key={`b${i}`} />;
            const iso = `${month.y}-${pad(month.m + 1)}-${pad(day)}`;
            const rec = byDate.get(iso);
            const weekend = (i % 7) >= 5;
            const future = iso > todayIso();
            const canFix = canCheck && !future;
            return (
              <div
                key={iso}
                className={`d${weekend ? " off" : ""}${iso === todayIso() ? " today" : ""}`}
                style={canFix ? { cursor: "pointer" } : undefined}
                title={canFix ? "Request a correction for this day" : undefined}
                onClick={canFix ? () => correctDay(iso) : undefined}
              >
                <span className="dn">{day}</span>
                {rec ? (
                  <>
                    <span className={`code c-${String(rec.statusCode ?? "").toUpperCase()}`} style={{ alignSelf: "flex-start" }}>{str(rec.statusCode, "·")}</span>
                    <span className="small muted">
                      {fmtTime(pick(rec, "checkIn", "checkInAt"))}
                      {pick(rec, "checkOut", "checkOutAt") ? `–${fmtTime(pick(rec, "checkOut", "checkOutAt"))}` : ""}
                    </span>
                  </>
                ) : null}
              </div>
            );
          })}
        </div>
        <p className="note">Click a past day to request a correction for it.</p>
      </section>

      <section className="card stack">
        <h2>History</h2>
        <DataTable<Row>
          queryKey="my-attendance"
          path={api.hrms("attendance/my")}
          itemSchema={rowSchema as never}
          search={false}
          defaultSort="workDate,desc"
          emptyText="No attendance recorded yet"
          rowKey={(r, i) => String(r.id ?? i)}
          columns={[
            { key: "workDate", header: "Date", sortable: true, render: (r) => fmtDate(r.workDate) },
            { key: "checkIn", header: "Check in", render: (r) => fmtTime(pick(r, "checkIn", "checkInAt")) },
            { key: "checkOut", header: "Check out", render: (r) => fmtTime(pick(r, "checkOut", "checkOutAt")) },
            { key: "statusCode", header: "Status", render: (r) => <AttCode code={r.statusCode} label={r.status} /> },
            { key: "workingHours", header: "Worked", className: "n", render: (r) => str(r.workingHours) },
            { key: "locationLabel", header: "Location", render: (r) => str(r.locationLabel) },
          ]}
        />
      </section>

      {canCheck ? (
        <section className="card stack">
          <h2>My correction requests</h2>
          <ErrorNote error={mine.error} />
          <div className="tbl-wrap">
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Requested</th>
                  <th>Reason</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {myRows.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="empty">
                      {mine.isLoading ? "Loading…" : "None yet"}
                    </td>
                  </tr>
                ) : (
                  myRows.map((r) => (
                    <tr key={String(r.id)}>
                      <td>{fmtDate(r.workDate)}</td>
                      <td className="mono small">
                        {fmtTime(r.requestedCheckIn)} – {fmtTime(r.requestedCheckOut)}
                      </td>
                      <td>
                        {str(r.reason, "")}
                        {r.reviewNote ? <div className="small muted">Review: {String(r.reviewNote)}</div> : null}
                      </td>
                      <td>
                        <StatusPill status={r.status} />
                        <div className="small muted">{r.updatedAt ? fmtDateTime(r.updatedAt) : ""}</div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Request attendance correction"
        subtitle="Your manager or HR will review it. Approved times replace the recorded ones."
      >
        <form className="stack" onSubmit={submit} noValidate>
          <div className="form-grid">
            <FormField label="Work date" required error={errors.workDate}>
              <input
                type="date"
                max={todayIso()}
                value={form.workDate}
                onChange={(e) => setForm({ ...form, workDate: e.target.value })}
              />
            </FormField>
            <div />
            <FormField label="Check in" required error={errors.checkInLocal}>
              <input type="time" value={form.checkInLocal} onChange={(e) => setForm({ ...form, checkInLocal: e.target.value })} />
            </FormField>
            <FormField label="Check out" required error={errors.checkOutLocal}>
              <input
                type="time"
                value={form.checkOutLocal}
                onChange={(e) => setForm({ ...form, checkOutLocal: e.target.value })}
              />
            </FormField>
            <FormField label="Reason" required error={errors.reason} full>
              <textarea value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} />
            </FormField>
          </div>
          {err ? <p className="err" role="alert">{err}</p> : null}
          <div className="modal-foot">
            <button type="button" className="btn" onClick={() => setOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={request.isPending}>
              {request.isPending ? "Submitting…" : "Submit request"}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
