"use client";

import { useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { DataTable } from "@/components/ui/DataTable";
import { FormField } from "@/components/ui/FormField";
import { PageHeader } from "@/components/ui/PageHeader";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch, listParams } from "@/lib/api/client";
import { byId, rowsOf, type Rec } from "@/lib/api/lookups";
import { fmtDate, fmtNumber, str, todayIso } from "@/lib/format";

const rowSchema = z.object({ workDate: z.any().optional() }).passthrough();

const formSchema = z.object({
  workDate: z.string().min(1, "Pick a date"),
  taskId: z.string().min(1, "Choose a task"),
  minutes: z.coerce.number("Enter minutes").int("Whole minutes only").min(5, "At least 5 minutes").max(960, "At most 16 hours"),
  note: z.string(),
});

export default function WorkLogPage() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({ workDate: todayIso(), taskId: "", minutes: "60", note: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");
  const canLog = can("attendance", "checkin");

  const tasks = useQuery({
    queryKey: ["lookup", "tasks"],
    queryFn: async ({ signal }) =>
      rowsOf<Rec>(await apiFetch(api.hrms("tasks"), { query: listParams({ page: 1, size: 500 }), signal })),
    staleTime: 60_000,
  });
  const taskName = useMemo(
    () => byId((tasks.data ?? []) as { id: number }[], (t) => String((t as unknown as Rec).title ?? `Task ${t.id}`)),
    [tasks.data],
  );

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.hrms("work-logs"), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Work log saved");
      setErr("");
      setForm((f) => ({ ...f, note: "" }));
      void qc.invalidateQueries({ queryKey: ["work-logs"] });
    },
    onError: (e: Error) => setErr(e.message),
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
    setErrors({});
    save.mutate({
      workDate: parsed.data.workDate,
      taskId: Number(parsed.data.taskId),
      minutes: parsed.data.minutes,
      note: parsed.data.note.trim() || undefined,
    });
  }

  return (
    <div className="stack">
      <PageHeader title="Work log" subtitle="Record the time you spent on each task." />

      {canLog ? (
        <section className="card stack">
          <h2>Add entry</h2>
          <form className="stack" onSubmit={submit} noValidate>
            <div className="form-grid">
              <FormField label="Date" required error={errors.workDate}>
                <input
                  type="date"
                  max={todayIso()}
                  value={form.workDate}
                  onChange={(e) => setForm({ ...form, workDate: e.target.value })}
                />
              </FormField>
              <FormField label="Task" required error={errors.taskId}>
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
              <FormField label="Minutes" required error={errors.minutes} hint="e.g. 90 = 1h 30m">
                <input
                  type="number"
                  min={5}
                  step={5}
                  value={form.minutes}
                  onChange={(e) => setForm({ ...form, minutes: e.target.value })}
                />
              </FormField>
              <FormField label="Note">
                <input type="text" value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} />
              </FormField>
            </div>
            {err ? <div className="notice bad">{err}</div> : null}
            <div>
              <button type="submit" className="btn primary" disabled={save.isPending}>
                {save.isPending ? "Saving…" : "Save entry"}
              </button>
            </div>
          </form>
        </section>
      ) : null}

      <section className="card stack">
        <h2>My entries</h2>
        <DataTable<Rec>
          queryKey="work-logs"
          path={api.hrms("work-logs")}
          itemSchema={rowSchema as never}
          search={false}
          defaultSort="workDate,desc"
          emptyText="No work logged yet"
          rowKey={(r, i) => String(r.id ?? i)}
          columns={[
            { key: "workDate", header: "Date", sortable: true, render: (r) => fmtDate(r.workDate) },
            { key: "taskId", header: "Task", render: (r) => taskName.get(String(r.taskId)) ?? `Task ${r.taskId}` },
            { key: "minutes", header: "Time", className: "n", render: (r) => `${fmtNumber(Number(r.minutes) / 60, 2)} h` },
            { key: "note", header: "Note", render: (r) => str(r.note) },
          ]}
        />
      </section>
    </div>
  );
}
