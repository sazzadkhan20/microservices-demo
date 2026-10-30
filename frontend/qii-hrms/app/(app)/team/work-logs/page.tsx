"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/components/auth/AuthProvider";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { api, apiFetch } from "@/lib/api/client";
import { useDirectory, useTaskDirectory } from "@/lib/api/directory";
import { fmtDate, fmtNumber, fmtTime, todayIso } from "@/lib/format";

type Row = {
  id?: number | string;
  employeeId?: number | string;
  employeeName?: string;
  employeeCode?: string;
  workDate?: string;
  taskId?: number | string;
  taskTitle?: string;
  minutes?: number;
  punchedMinutes?: number;
  punchedHours?: number;
  dayLoggedMinutes?: number;
  checkIn?: string;
  checkOut?: string;
  note?: string;
  flagged?: boolean;
  missingLog?: boolean;
};

export default function TeamWorkLogsPage() {
  const { can } = useAuth();
  const dir = useDirectory(can("employees", "view"));
  const tasks = useTaskDirectory();
  const [employee, setEmployee] = useState("");
  const [task, setTask] = useState("");
  const [from, setFrom] = useState(todayIso());
  const [to, setTo] = useState(todayIso());
  const [missingOnly, setMissingOnly] = useState(false);

  const q = useQuery({
    queryKey: ["team-work-logs", from, to, missingOnly],
    queryFn: ({ signal }) =>
      apiFetch<Row[]>(api.hrms("work-logs/team"), {
        query: { from, to, missingOnly: missingOnly ? "true" : "false" },
        signal,
      }),
  });

  const rows = useMemo(() => {
    let list = Array.isArray(q.data) ? q.data : [];
    if (employee) list = list.filter((r) => String(r.employeeId) === employee);
    if (task) list = list.filter((r) => String(r.taskId) === task);
    return list;
  }, [q.data, employee, task]);

  return (
    <div className="stack">
      <PageHeader
        title="Team work logs"
        subtitle="Logged time next to punched hours for people in your attendance scope."
      />
      <div className="filters">
        <div className="field">
          <span>From</span>
          <input type="date" value={from} onChange={(e) => e.target.value && setFrom(e.target.value)} style={{ width: 150 }} />
        </div>
        <div className="field">
          <span>To</span>
          <input type="date" value={to} onChange={(e) => e.target.value && setTo(e.target.value)} style={{ width: 150 }} />
        </div>
        {dir.employees.length ? (
          <div className="field">
            <span>Employee</span>
            <select value={employee} onChange={(e) => setEmployee(e.target.value)} style={{ width: 190 }}>
              <option value="">Everyone</option>
              {dir.employees.map((e) => (
                <option key={String(e.id)} value={String(e.id)}>
                  {e.fullName}
                </option>
              ))}
            </select>
          </div>
        ) : null}
        <div className="field">
          <span>Task</span>
          <select value={task} onChange={(e) => setTask(e.target.value)} style={{ width: 200 }}>
            <option value="">All tasks</option>
            {tasks.tasks.map((t) => (
              <option key={String(t.id)} value={String(t.id)}>
                {t.title}
              </option>
            ))}
          </select>
        </div>
        <label className="row" style={{ gap: 8, alignItems: "center" }}>
          <input type="checkbox" checked={missingOnly} onChange={(e) => setMissingOnly(e.target.checked)} />
          <span className="small">Days with no log</span>
        </label>
        <button type="button" className="btn" onClick={() => void q.refetch()}>
          {q.isFetching ? "Refreshing…" : "Refresh"}
        </button>
      </div>
      <ErrorNote error={q.error} />
      <div className="tbl-wrap">
        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Employee</th>
              <th>Task</th>
              <th>In → Out</th>
              <th className="n">Punched</th>
              <th className="n">Logged</th>
              <th>Note</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={7} className="empty">
                  {q.isLoading ? "Loading…" : "No rows match"}
                </td>
              </tr>
            ) : (
              rows.map((r, i) => (
                <tr key={String(r.id ?? `${r.employeeId}-${r.workDate}-${r.taskId}-${i}`)}>
                  <td className="mono">{fmtDate(r.workDate)}</td>
                  <td>
                    <b>{r.employeeName ?? dir.nameOf(r.employeeId)}</b>
                    {r.missingLog ? <span className="pill warn" style={{ marginLeft: 6 }}>No log</span> : null}
                  </td>
                  <td>{r.taskTitle ?? (r.taskId != null ? tasks.titleOf(r.taskId) : "—")}</td>
                  <td className="mono small">
                    {r.checkIn ? fmtTime(r.checkIn) : "—"}
                    {" → "}
                    {r.checkOut ? fmtTime(r.checkOut) : "—"}
                  </td>
                  <td className="n">{r.punchedHours != null ? `${fmtNumber(r.punchedHours, 2)} h` : "—"}</td>
                  <td className="n">
                    {r.minutes != null
                      ? `${fmtNumber(r.minutes / 60, 2)} h`
                      : r.dayLoggedMinutes != null
                        ? `${fmtNumber(r.dayLoggedMinutes / 60, 2)} h`
                        : "—"}
                    {r.flagged ? <span className="pill warn" style={{ marginLeft: 6 }}>Flag</span> : null}
                  </td>
                  <td className="small">{r.note || "—"}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
