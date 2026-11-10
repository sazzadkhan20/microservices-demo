"use client";

import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { api, apiFetch, DEFAULT_PAGE_SIZE } from "@/lib/api/client";
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

type FilterOption = { id: number | string; fullName?: string; employeeCode?: string; title?: string };

type PageEnvelope = {
  content: Row[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

function parsePage(raw: unknown): PageEnvelope {
  if (raw && typeof raw === "object" && Array.isArray((raw as { content?: unknown }).content)) {
    const p = raw as Record<string, unknown>;
    const content = (p.content as Row[]).filter(Boolean);
    const size = Number(p.size) > 0 ? Number(p.size) : DEFAULT_PAGE_SIZE;
    const totalElements = Number(p.totalElements ?? content.length);
    const totalPages = Math.max(1, Number(p.totalPages) || Math.ceil(totalElements / size) || 1);
    const page = Math.max(1, Number(p.page) || 1);
    return { content, page, size, totalElements, totalPages };
  }
  const content = Array.isArray(raw) ? (raw as Row[]) : [];
  return { content, page: 1, size: content.length || DEFAULT_PAGE_SIZE, totalElements: content.length, totalPages: 1 };
}

export default function TeamWorkLogsPage() {
  const [employee, setEmployee] = useState("");
  const [task, setTask] = useState("");
  const [from, setFrom] = useState(todayIso());
  const [to, setTo] = useState(todayIso());
  const [missingOnly, setMissingOnly] = useState(false);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  useEffect(() => {
    setPage(1);
  }, [from, to, missingOnly, employee, task, size]);

  // Employees in work-log scope + tasks assigned/logged in range (not only manager-created).
  // Prefer flat /team-options; fall back to /team?optionsOnly=true if needed.
  const options = useQuery({
    queryKey: ["team-work-log-options", from, to],
    queryFn: async ({ signal }) => {
      const parse = (raw: { employees?: FilterOption[]; tasks?: FilterOption[] } | null | undefined) => ({
        employees: Array.isArray(raw?.employees) ? raw.employees : [],
        tasks: Array.isArray(raw?.tasks) ? raw.tasks : [],
      });
      try {
        return parse(
          await apiFetch<{ employees?: FilterOption[]; tasks?: FilterOption[] }>(
            api.hrms("work-logs/team-options"),
            { query: { from, to }, signal },
          ),
        );
      } catch {
        return parse(
          await apiFetch<{ employees?: FilterOption[]; tasks?: FilterOption[] }>(
            api.hrms("work-logs/team"),
            { query: { from, to, optionsOnly: "true" }, signal },
          ),
        );
      }
    },
  });

  const employees = options.data?.employees ?? [];
  const tasks = options.data?.tasks ?? [];

  const taskTitleOf = useMemo(() => {
    const m = new Map<string, string>();
    for (const t of tasks) m.set(String(t.id), String(t.title ?? `Task #${t.id}`));
    return (id: unknown) => (id == null || id === "" ? "—" : m.get(String(id)) ?? `Task #${id}`);
  }, [tasks]);

  // Drop stale filter values when options refresh for a new range.
  useEffect(() => {
    if (employee && employees.length && !employees.some((e) => String(e.id) === employee)) {
      setEmployee("");
    }
    if (task && tasks.length && !tasks.some((t) => String(t.id) === task)) {
      setTask("");
    }
  }, [employees, tasks, employee, task]);

  const q = useQuery({
    queryKey: ["team-work-logs", from, to, missingOnly, employee, task, page, size],
    queryFn: async ({ signal }) => {
      const raw = await apiFetch(api.hrms("work-logs/team"), {
        query: {
          from,
          to,
          missingOnly: missingOnly ? "true" : "false",
          employeeId: employee || undefined,
          taskId: task || undefined,
          page,
          size,
          sort: "workDate,desc",
        },
        signal,
      });
      return parsePage(raw);
    },
  });

  const rows = q.data?.content ?? [];
  const totalPages = Math.max(q.data?.totalPages ?? 1, 1);
  const totalElements = q.data?.totalElements ?? rows.length;

  return (
    <div className="stack">
      <PageHeader
        title="Team work logs"
        subtitle="Logged time next to punched hours for people in your attendance / work-log scope."
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
        <div className="field">
          <span>Employee</span>
          <select value={employee} onChange={(e) => setEmployee(e.target.value)} style={{ width: 190 }}>
            <option value="">Everyone</option>
            {employees.map((e) => (
              <option key={String(e.id)} value={String(e.id)}>
                {e.fullName ?? `#${e.id}`}
                {e.employeeCode ? ` (${e.employeeCode})` : ""}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <span>Task</span>
          <select value={task} onChange={(e) => setTask(e.target.value)} style={{ width: 220 }} disabled={missingOnly}>
            <option value="">All tasks</option>
            {tasks.map((t) => (
              <option key={String(t.id)} value={String(t.id)}>
                {t.title ?? `Task #${t.id}`}
              </option>
            ))}
          </select>
        </div>
        <label className="row" style={{ gap: 8, alignItems: "center" }}>
          <input
            type="checkbox"
            checked={missingOnly}
            onChange={(e) => {
              setMissingOnly(e.target.checked);
              if (e.target.checked) setTask("");
            }}
          />
          <span className="small">Days with no log</span>
        </label>
        <button type="button" className="btn" onClick={() => void q.refetch()}>
          {q.isFetching ? "Refreshing…" : "Refresh"}
        </button>
      </div>
      <ErrorNote error={q.error ?? options.error} />
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
                    <b>{r.employeeName ?? `#${r.employeeId}`}</b>
                    {r.missingLog ? <span className="pill warn" style={{ marginLeft: 6 }}>No log</span> : null}
                  </td>
                  <td>{r.taskTitle ?? (r.taskId != null ? taskTitleOf(r.taskId) : "—")}</td>
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
      <div className="row" style={{ justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 8 }}>
        <span className="small muted">
          {totalElements} total · page {Math.min(page, totalPages)}/{totalPages}
        </span>
        <div className="row" style={{ gap: 8, alignItems: "center" }}>
          <label className="small muted row" style={{ gap: 6, alignItems: "center" }}>
            Per page
            <select
              value={size}
              onChange={(e) => setSize(Number(e.target.value))}
              style={{ width: 72 }}
              aria-label="Rows per page"
            >
              {[10, 20, 50, 100].map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
          </label>
          <button type="button" className="btn sm" disabled={page <= 1 || q.isFetching} onClick={() => setPage((p) => p - 1)}>
            Previous
          </button>
          <button
            type="button"
            className="btn sm"
            disabled={page >= totalPages || q.isFetching}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </button>
        </div>
      </div>
    </div>
  );
}
