"use client";

import { useState } from "react";
import { useAuth } from "@/components/auth/AuthProvider";
import { DataTable, type Column } from "@/components/ui/DataTable";
import { PageHeader } from "@/components/ui/PageHeader";
import { api } from "@/lib/api/client";
import { useDirectory, useTaskDirectory } from "@/lib/api/directory";
import { fmtDate, fmtNumber, isoDate } from "@/lib/format";
import { workLogRowSchema, type WorkLogRow } from "@/lib/schemas/admin";

export default function TeamWorkLogsPage() {
  const { can } = useAuth();
  const dir = useDirectory(can("employees", "view"));
  const tasks = useTaskDirectory();
  const [employee, setEmployee] = useState("");
  const [task, setTask] = useState("");
  const [date, setDate] = useState("");

  const filters: Record<string, string> = {};
  if (employee) filters.employeeId = employee;
  if (task) filters.taskId = task;
  if (date) filters.workDate = date;

  const columns: Column<WorkLogRow>[] = [
    { key: "workDate", header: "Date", className: "mono", render: (r) => fmtDate(isoDate(r.workDate)) },
    { key: "employeeId", header: "Employee", render: (r) => dir.nameOf(r.employeeId) },
    { key: "taskId", header: "Task", render: (r) => tasks.titleOf(r.taskId) },
    {
      key: "minutes",
      header: "Time",
      className: "n",
      render: (r) => (r.minutes != null ? `${fmtNumber(r.minutes / 60, 2)} h` : "—"),
    },
    { key: "note", header: "Note", render: (r) => r.note || <span className="muted">—</span> },
  ];

  return (
    <div className="stack">
      <PageHeader title="Team work logs" subtitle="Time logged against tasks by the people in your scope." />
      <DataTable<WorkLogRow>
        queryKey="team-work-logs"
        path={api.hrms("work-logs/team")}
        itemSchema={workLogRowSchema}
        columns={columns}
        filters={filters}
        emptyText="No work logs match"
        rowKey={(r) => String(r.id)}
        toolbar={
          <>
            {dir.employees.length ? (
              <select aria-label="Employee" value={employee} onChange={(e) => setEmployee(e.target.value)} style={{ width: 190 }}>
                <option value="">Everyone</option>
                {dir.employees.map((e) => (
                  <option key={String(e.id)} value={String(e.id)}>
                    {e.fullName}
                  </option>
                ))}
              </select>
            ) : null}
            <select aria-label="Task" value={task} onChange={(e) => setTask(e.target.value)} style={{ width: 200 }}>
              <option value="">All tasks</option>
              {tasks.tasks.map((t) => (
                <option key={String(t.id)} value={String(t.id)}>
                  {t.title}
                </option>
              ))}
            </select>
            <input type="date" aria-label="Date" value={date} onChange={(e) => setDate(e.target.value)} style={{ width: 150 }} />
            {date ? (
              <button type="button" className="btn sm" onClick={() => setDate("")}>
                Clear date
              </button>
            ) : null}
          </>
        }
      />
      <div className="note small">
        The work-log API returns all visible logs and does not yet apply your team scope server-side.
      </div>
    </div>
  );
}
