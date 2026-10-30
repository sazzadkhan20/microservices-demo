"use client";

import { useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/components/auth/AuthProvider";
import { EmptyState } from "@/components/ui/EmptyState";
import { FormField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { AttCode } from "@/components/ui/StatusPill";
import { StatCard } from "@/components/ui/StatCard";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { useDepartments } from "@/lib/api/lookups";
import { localRangeToIso } from "@/lib/attendance/punch";
import { addDaysIso } from "@/lib/admin";
import { fmtDate, fmtNumber, fmtTime, todayIso } from "@/lib/format";
import { teamAttendanceListSchema, type TeamAttendanceRow } from "@/lib/schemas/admin";

const GROUPS: { id: string; label: string; codes: string[] }[] = [
  { id: "P", label: "Present", codes: ["P", "W"] },
  { id: "L", label: "Late", codes: ["L"] },
  { id: "HD", label: "Half day", codes: ["HD"] },
  { id: "A", label: "Absent", codes: ["A", "NC"] },
  { id: "OL", label: "On leave", codes: ["OL"] },
];

const codeOf = (r: TeamAttendanceRow) => String(r.statusCode ?? "").toUpperCase();

type EditTarget = {
  employeeId: string | number;
  name: string;
  workDate: string;
  checkInLocal: string;
  checkOutLocal: string;
  reason: string;
};

export default function TeamAttendancePage() {
  const { can, me } = useAuth();
  const canEdit = can("attendance", "correct");
  const qc = useQueryClient();
  const toast = useToast();
  const [date, setDate] = useState(todayIso());
  const [status, setStatus] = useState("");
  const [q, setQ] = useState("");
  const [edit, setEdit] = useState<EditTarget | null>(null);
  const [editErr, setEditErr] = useState("");
  const depts = useDepartments();

  const team = useQuery({
    queryKey: ["team-attendance", date],
    queryFn: ({ signal }) =>
      apiFetch(api.hrms("attendance/team"), { query: { date }, schema: teamAttendanceListSchema, signal }),
  });
  const rows = useMemo(() => team.data ?? [], [team.data]);
  const deptName = useMemo(() => new Map((depts.data ?? []).map((d) => [String(d.id), d.name])), [depts.data]);

  const counts = useMemo(() => {
    const c: Record<string, number> = {};
    for (const g of GROUPS) c[g.id] = rows.filter((r) => g.codes.includes(codeOf(r))).length;
    c.other = rows.length - GROUPS.reduce((n, g) => n + c[g.id], 0);
    return c;
  }, [rows]);

  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const group = GROUPS.find((g) => g.id === status);
    return rows
      .filter((r) => !group || group.codes.includes(codeOf(r)))
      .filter(
        (r) =>
          !needle ||
          `${r.employeeName ?? r.name ?? ""} ${r.employeeCode ?? ""}`.toLowerCase().includes(needle),
      )
      .sort((a, b) => String(a.employeeName ?? a.name ?? "").localeCompare(String(b.employeeName ?? b.name ?? "")));
  }, [rows, q, status]);

  const isToday = date === todayIso();

  const saveEdit = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiFetch(api.hrms("attendance/correct"), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Attendance updated");
      setEdit(null);
      setEditErr("");
      void qc.invalidateQueries({ queryKey: ["team-attendance", date] });
    },
    onError: (e: Error) => setEditErr(e.message || "Save failed"),
  });

  function toLocalHm(iso: unknown, fallback: string) {
    if (!iso) return fallback;
    const d = new Date(String(iso));
    if (Number.isNaN(d.getTime())) return fallback;
    const pad = (n: number) => String(n).padStart(2, "0");
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  function openEdit(r: TeamAttendanceRow) {
    if (String(r.employeeId) === String(me.employeeId)) {
      toast.error(new Error("You cannot edit your own attendance"));
      return;
    }
    setEdit({
      employeeId: r.employeeId!,
      name: String(r.employeeName ?? r.name ?? `#${r.employeeId}`),
      workDate: date,
      checkInLocal: toLocalHm(r.checkIn, "09:00"),
      checkOutLocal: toLocalHm(r.checkOut, "18:00"),
      reason: "",
    });
    setEditErr("");
  }

  function submitEdit(e: FormEvent) {
    e.preventDefault();
    if (!edit) return;
    if (edit.reason.trim().length < 3) {
      setEditErr("Reason is required (min 3 characters)");
      return;
    }
    const times = localRangeToIso(edit.workDate, edit.checkInLocal, edit.checkOutLocal);
    saveEdit.mutate({
      employeeId: Number(edit.employeeId),
      workDate: edit.workDate,
      checkInAt: times.requestedCheckIn,
      checkOutAt: times.requestedCheckOut,
      reason: edit.reason.trim(),
    });
  }

  return (
    <div className="stack">
      <PageHeader
        title="Team attendance"
        subtitle="Who is in, late or absent on a day — worked and logged hours for your scope."
        actions={
          <>
            <button type="button" className="btn" aria-label="Previous day" onClick={() => setDate(addDaysIso(date, -1))}>
              ←
            </button>
            <input type="date" aria-label="Date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} style={{ width: 160 }} />
            <button type="button" className="btn" aria-label="Next day" onClick={() => setDate(addDaysIso(date, 1))}>
              →
            </button>
            <button type="button" className="btn" disabled={isToday} onClick={() => setDate(todayIso())}>
              Today
            </button>
            <button type="button" className="btn" onClick={() => void team.refetch()}>
              {team.isFetching ? "Refreshing…" : "Refresh"}
            </button>
          </>
        }
      />

      <div className="grid g4">
        <StatCard label="Present" value={team.data ? counts.P : "—"} tone="good" sub={fmtDate(date)} />
        <StatCard label="Late" value={team.data ? counts.L : "—"} tone="warn" />
        <StatCard label="Half day" value={team.data ? counts.HD : "—"} tone="info" />
        <StatCard label="Absent" value={team.data ? counts.A : "—"} tone="bad" sub={counts.other ? `+${counts.other} other` : undefined} />
      </div>

      <ErrorNote error={team.error} />

      <div className="filters">
        <div className="field wide">
          <span>Search</span>
          <input type="search" placeholder="Name or employee code" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <div className="field">
          <span>Status</span>
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All ({rows.length})</option>
            {GROUPS.map((g) => (
              <option key={g.id} value={g.id}>
                {g.label} ({counts[g.id]})
              </option>
            ))}
          </select>
        </div>
      </div>

      {!team.isLoading && !team.error && rows.length === 0 ? (
        <EmptyState title="No one in your scope">
          The attendance API returned no active people for this date. Managers see their direct reports and team; HR sees everyone.
        </EmptyState>
      ) : (
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th>Employee</th>
                <th>Department</th>
                <th>Status</th>
                <th>Check in</th>
                <th>Check out</th>
                <th className="n">Worked</th>
                <th className="n">Logged</th>
                <th>Location</th>
                {canEdit ? <th /> : null}
              </tr>
            </thead>
            <tbody>
              {shown.length === 0 ? (
                <tr>
                  <td colSpan={canEdit ? 9 : 8} className="empty">
                    {team.isLoading ? "Loading…" : "No one matches these filters"}
                  </td>
                </tr>
              ) : (
                shown.map((r, i) => (
                  <tr key={String(r.employeeId ?? i)}>
                    <td>
                      <b>{r.employeeName ?? r.name ?? `#${r.employeeId}`}</b>
                      <br />
                      <span className="small muted mono">{r.employeeCode}</span>
                    </td>
                    <td>{r.departmentId != null ? (deptName.get(String(r.departmentId)) ?? "—") : "—"}</td>
                    <td>
                      <AttCode code={r.statusCode} label={r.status} />
                    </td>
                    <td className="mono">{fmtTime(r.checkIn)}</td>
                    <td className="mono">{r.checkOut ? fmtTime(r.checkOut) : r.checkIn ? <span className="muted">Still in</span> : "—"}</td>
                    <td className="n">{r.totalHours != null && r.checkIn ? fmtNumber(r.totalHours) : "—"}</td>
                    <td className="n">
                      {(r as TeamAttendanceRow & { loggedHours?: number }).loggedHours != null
                        ? fmtNumber((r as TeamAttendanceRow & { loggedHours?: number }).loggedHours)
                        : (r as { loggedMinutes?: number }).loggedMinutes != null
                          ? fmtNumber(Number((r as { loggedMinutes?: number }).loggedMinutes) / 60)
                          : "—"}
                    </td>
                    <td className="small muted">{r.locationLabel ?? "—"}</td>
                    {canEdit ? (
                      <td>
                        {String(r.employeeId) !== String(me.employeeId) ? (
                          <button type="button" className="btn sm" onClick={() => openEdit(r)}>
                            Edit
                          </button>
                        ) : (
                          <span className="muted small">Self</span>
                        )}
                      </td>
                    ) : null}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        open={!!edit}
        onClose={() => setEdit(null)}
        title={edit ? `Edit attendance — ${edit.name}` : ""}
        subtitle="HR edit with a reason. Original times stay on correction requests; this overwrites the day record."
        footer={
          <>
            <button type="button" className="btn" onClick={() => setEdit(null)}>
              Cancel
            </button>
            <button type="button" className="btn primary" disabled={saveEdit.isPending} onClick={submitEdit}>
              {saveEdit.isPending ? "Saving…" : "Save"}
            </button>
          </>
        }
      >
        {edit ? (
          <form className="stack" onSubmit={submitEdit}>
            <FormField label="Date">
              <input type="date" value={edit.workDate} readOnly />
            </FormField>
            <div className="row">
              <FormField label="Check in">
                <input
                  type="time"
                  value={edit.checkInLocal}
                  onChange={(e) => setEdit({ ...edit, checkInLocal: e.target.value })}
                />
              </FormField>
              <FormField label="Check out" hint="Overnight OK if earlier than check-in">
                <input
                  type="time"
                  value={edit.checkOutLocal}
                  onChange={(e) => setEdit({ ...edit, checkOutLocal: e.target.value })}
                />
              </FormField>
            </div>
            <FormField label="Reason" required error={editErr || undefined}>
              <textarea
                value={edit.reason}
                onChange={(e) => setEdit({ ...edit, reason: e.target.value })}
                placeholder="Why this change?"
              />
            </FormField>
          </form>
        ) : null}
      </Modal>
    </div>
  );
}
