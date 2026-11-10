"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { FormField } from "@/components/ui/FormField";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch, DEFAULT_PAGE_SIZE, listParams } from "@/lib/api/client";
import { rowsOf, type Rec } from "@/lib/api/lookups";
import { pageResponseSchema } from "@/lib/schemas/page";
import { addDaysIso } from "@/lib/admin";
import { fmtDate, fmtNumber, str, todayIso } from "@/lib/format";

type Rules = {
  editDaysBack?: number;
  stepMinutes?: number;
  minMinutes?: number;
  maxMinutesPerDay?: number;
  overPunchGraceMinutes?: number;
  workLogRule?: string;
  workLogMinPercent?: number;
};

const entrySchema = z.object({
  id: z.union([z.number(), z.string()]),
  workDate: z.string().optional().nullable(),
  taskId: z.union([z.number(), z.string()]).optional().nullable(),
  taskTitle: z.string().optional().nullable(),
  minutes: z.union([z.number(), z.string()]).optional().nullable(),
  note: z.string().optional().nullable(),
  flagReason: z.string().optional().nullable(),
  flagged: z.boolean().optional().nullable(),
  editable: z.boolean().optional().nullable(),
}).passthrough();

export default function WorkLogPage() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canLog = can("attendance", "checkin");

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  const rulesQ = useQuery({
    queryKey: ["work-log-rules"],
    queryFn: ({ signal }) => apiFetch<Rules>(api.hrms("work-logs/rules"), { signal }),
  });
  const rules = rulesQ.data;
  const step = rules?.stepMinutes ?? 15;
  const minM = rules?.minMinutes ?? 15;
  const maxDay = rules?.maxMinutesPerDay ?? 960;
  const back = rules?.editDaysBack ?? 7;
  const minDate = addDaysIso(todayIso(), -back);

  const formSchema = useMemo(
    () =>
      z.object({
        workDate: z.string().min(1, "Pick a date"),
        taskId: z.string().min(1, "Choose a task"),
        minutes: z.coerce
          .number("Enter minutes")
          .int("Whole minutes only")
          .min(minM, `At least ${minM} minutes (0.25 h)`)
          .max(maxDay, `At most ${maxDay / 60} hours`)
          .refine((n) => n % step === 0, `Use ${step}-minute steps`),
        note: z.string().trim().min(1, "Description is required"),
      }),
    [minM, maxDay, step],
  );

  const [form, setForm] = useState({ workDate: todayIso(), taskId: "", minutes: String(step * 4), note: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  // FR-WL-01 — only OPEN / IN_PROGRESS tasks assigned to the employee (no DONE fallback).
  const tasks = useQuery({
    queryKey: ["lookup", "tasks-assigned-open"],
    queryFn: async ({ signal }) => {
      const rows = rowsOf<Rec>(await apiFetch(api.hrms("tasks/assigned"), { signal }));
      return rows.filter((t) => {
        const st = String(t.status ?? "").toUpperCase();
        return st === "OPEN" || st === "IN_PROGRESS";
      });
    },
    staleTime: 30_000,
    enabled: canLog,
  });

  useEffect(() => {
    setPage(1);
  }, [size]);

  const logs = useQuery({
    queryKey: ["work-logs", page, size],
    queryFn: async ({ signal }) => {
      const raw = await apiFetch(api.hrms("work-logs"), {
        query: listParams({ page, size, sort: "workDate,desc" }),
        signal,
      });
      const parsed = pageResponseSchema(entrySchema).safeParse(raw);
      if (parsed.success) {
        return {
          content: parsed.data.content as Rec[],
          page: parsed.data.page,
          size: parsed.data.size,
          totalElements: parsed.data.totalElements,
          totalPages: Math.max(parsed.data.totalPages, 1),
        };
      }
      const content = rowsOf<Rec>(raw);
      return { content, page: 1, size: content.length, totalElements: content.length, totalPages: 1 };
    },
  });

  const rows = logs.data?.content ?? [];
  const totalPages = Math.max(logs.data?.totalPages ?? 1, 1);
  const totalElements = logs.data?.totalElements ?? rows.length;

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.hrms("work-logs"), { method: "POST", body }),
    onSuccess: (row) => {
      toast.show(row && (row as Rec).flagged ? "Saved (flagged: over punched hours)" : "Work log saved");
      setErr("");
      setForm((f) => ({ ...f, note: "" }));
      void qc.invalidateQueries({ queryKey: ["work-logs"] });
    },
    onError: (e: Error) => setErr(e.message),
  });

  const del = useMutation({
    mutationFn: (id: string | number) => apiFetch(api.hrms(`work-logs/${id}`), { method: "DELETE" }),
    onSuccess: () => {
      toast.show("Entry deleted");
      void qc.invalidateQueries({ queryKey: ["work-logs"] });
    },
    onError: (e: Error) => toast.error(e),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = formSchema.safeParse(form);
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      setErrors(next);
      return;
    }
    if (parsed.data.workDate < minDate || parsed.data.workDate > todayIso()) {
      setErrors({ workDate: `Only today and the last ${back} days are editable` });
      return;
    }
    setErrors({});
    save.mutate({
      workDate: parsed.data.workDate,
      taskId: Number(parsed.data.taskId),
      minutes: parsed.data.minutes,
      description: parsed.data.note.trim(),
      note: parsed.data.note.trim(),
    });
  }

  return (
    <div className="stack">
      <PageHeader
        title="Work log"
        subtitle={`Log time on your assigned open tasks. Editable for today and the last ${back} days · max ${maxDay / 60} h/day.`}
      />

      {canLog ? (
        <section className="card stack">
          <h2>Add entry</h2>
          <form className="stack" onSubmit={submit} noValidate>
            <div className="form-grid">
              <FormField label="Date" required error={errors.workDate}>
                <input
                  type="date"
                  min={minDate}
                  max={todayIso()}
                  value={form.workDate}
                  onChange={(e) => setForm({ ...form, workDate: e.target.value })}
                />
              </FormField>
              <FormField label="Task" required error={errors.taskId} hint="Open / in-progress tasks assigned to you">
                <select value={form.taskId} onChange={(e) => setForm({ ...form, taskId: e.target.value })}>
                  <option value="">Select a task…</option>
                  {(tasks.data ?? []).map((t) => (
                    <option key={String(t.id)} value={String(t.id)}>
                      {str(t.title, `Task ${t.id}`)}
                      {t.clientName ? ` — ${String(t.clientName)}` : ""}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField label="Minutes" required error={errors.minutes} hint={`${step}-minute steps, min ${minM}`}>
                <input
                  type="number"
                  min={minM}
                  step={step}
                  value={form.minutes}
                  onChange={(e) => setForm({ ...form, minutes: e.target.value })}
                />
              </FormField>
              <FormField label="Description" required error={errors.note}>
                <input type="text" value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} />
              </FormField>
            </div>
            {err ? <div className="notice bad">{err}</div> : null}
            <div>
              <button type="submit" className="btn primary" disabled={save.isPending || !(tasks.data ?? []).length}>
                {save.isPending ? "Saving…" : "Save entry"}
              </button>
            </div>
            {canLog && (tasks.data ?? []).length === 0 && !tasks.isLoading ? (
              <p className="small muted">No open tasks are assigned to you — ask your manager to assign one.</p>
            ) : null}
          </form>
        </section>
      ) : null}

      <section className="card stack">
        <h2>My entries</h2>
        <ErrorNote error={logs.error} />
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Task</th>
                <th className="n">Time</th>
                <th>Description</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={5} className="empty">
                    {logs.isLoading ? "Loading…" : "No entries yet"}
                  </td>
                </tr>
              ) : (
                rows.map((r) => {
                  const editable = r.editable !== false;
                  return (
                    <tr key={String(r.id)}>
                      <td className="mono">{fmtDate(r.workDate)}</td>
                      <td>{str(r.taskTitle, `Task ${r.taskId}`)}</td>
                      <td className="n">
                        {fmtNumber(Number(r.minutes) / 60, 2)} h
                        {r.flagged ? <span className="pill warn" style={{ marginLeft: 6 }}>Flagged</span> : null}
                      </td>
                      <td>
                        {str(r.note, "")}
                        {r.flagReason ? <div className="small muted">{String(r.flagReason)}</div> : null}
                      </td>
                      <td>
                        {canLog && editable ? (
                          <button type="button" className="btn sm danger" disabled={del.isPending} onClick={() => del.mutate(r.id as number)}>
                            Delete
                          </button>
                        ) : (
                          <span className="muted small">Read-only</span>
                        )}
                      </td>
                    </tr>
                  );
                })
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
            <button type="button" className="btn sm" disabled={page <= 1 || logs.isFetching} onClick={() => setPage((p) => p - 1)}>
              Previous
            </button>
            <button
              type="button"
              className="btn sm"
              disabled={page >= totalPages || logs.isFetching}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}
