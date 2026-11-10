"use client";

import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { FormField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { StatusPill } from "@/components/ui/StatusPill";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch, DEFAULT_PAGE_SIZE, listParams } from "@/lib/api/client";
import { useDirectory } from "@/lib/api/directory";
import { fieldErrors, type FieldErrors } from "@/lib/admin";
import { fmtDate, fmtNumber, humanize, isoDate, todayIso } from "@/lib/format";
import { taskRowSchema, type TaskRow } from "@/lib/schemas/admin";
import { pageResponseSchema } from "@/lib/schemas/page";

const STATUSES = ["OPEN", "IN_PROGRESS", "DONE"];
const PRIORITIES = ["LOW", "MEDIUM", "HIGH"];

const taskForm = z.object({
  title: z.string().trim().min(1, "Title is required").max(256),
  clientName: z.string().trim().max(128),
  priority: z.string(),
  status: z.string().min(1),
  dueDate: z.string(),
  externalRef: z.string().trim().max(64),
  assigneeIds: z.array(z.string()).min(1, "Pick at least one assignee"),
});

type TaskView = TaskRow & {
  loggedMinutes?: number;
  loggedHours?: number;
  overdue?: boolean;
  assigneeIds?: Array<string | number>;
  priority?: string | null;
  createdBy?: string | number | null;
};

type Candidate = {
  id: string | number;
  fullName?: string | null;
  employeeCode?: string | null;
};

export default function TasksPage() {
  const { can } = useAuth();
  const canCreate = can("tasks", "create");
  const canEdit = can("tasks", "edit");
  const canView = can("tasks", "view") || canCreate;
  const dir = useDirectory(can("employees", "view") || canView);

  const [status, setStatus] = useState("");
  const [qDraft, setQDraft] = useState("");
  const [q, setQ] = useState("");
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [open, setOpen] = useState(false);
  const [edit, setEdit] = useState<TaskView | null>(null);

  // Debounced search — no Search button.
  useEffect(() => {
    const t = window.setTimeout(() => setQ(qDraft.trim()), 300);
    return () => window.clearTimeout(t);
  }, [qDraft]);

  useEffect(() => {
    setPage(1);
  }, [status, q, size]);

  const tasks = useQuery({
    queryKey: ["tasks-admin", status, q, page, size],
    queryFn: async ({ signal }) => {
      const raw = await apiFetch(api.hrms("tasks"), {
        query: listParams({
          page,
          size,
          q: q || undefined,
          filters: status ? { status } : undefined,
        }),
        signal,
      });
      const parsed = pageResponseSchema(taskRowSchema.passthrough()).safeParse(raw);
      if (parsed.success) {
        return {
          content: parsed.data.content as TaskView[],
          page: parsed.data.page,
          size: parsed.data.size,
          totalElements: parsed.data.totalElements,
          totalPages: Math.max(parsed.data.totalPages, 1),
        };
      }
      // Fallback if envelope drifts
      const content = Array.isArray((raw as { content?: unknown })?.content)
        ? ((raw as { content: TaskView[] }).content ?? [])
        : [];
      return { content, page: 1, size: content.length, totalElements: content.length, totalPages: 1 };
    },
    enabled: canView,
  });

  const rows = tasks.data?.content ?? [];
  const totalPages = Math.max(tasks.data?.totalPages ?? 1, 1);
  const totalElements = tasks.data?.totalElements ?? rows.length;

  return (
    <div className="stack">
      <PageHeader
        title="Tasks"
        subtitle="Work items people log time against. Assignees are limited to your reporting team. Logged hours and overdue flags update from work logs."
        actions={
          canCreate ? (
            <button type="button" className="btn primary" onClick={() => setOpen(true)}>
              New task
            </button>
          ) : null
        }
      />
      <ErrorNote error={tasks.error} />
      <div className="filters" style={{ display: "flex", flexWrap: "wrap", gap: 12, alignItems: "end" }}>
        <div className="field">
          <span>Search</span>
          <input
            type="search"
            placeholder="Title, client, or external id…"
            value={qDraft}
            onChange={(e) => setQDraft(e.target.value)}
            style={{ width: 260 }}
          />
        </div>
        <div className="field">
          <span>Status</span>
          <select value={status} onChange={(e) => setStatus(e.target.value)} style={{ width: 160 }}>
            <option value="">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {humanize(s)}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="tbl-wrap">
        <table>
          <thead>
            <tr>
              <th>Task</th>
              <th>Client / project</th>
              <th>Status</th>
              <th>Assignees</th>
              <th className="n">Logged</th>
              <th>Due</th>
              <th>External id</th>
              {canEdit ? <th /> : null}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={canEdit ? 8 : 7} className="empty">
                  {tasks.isLoading ? "Loading…" : "No tasks match this view"}
                </td>
              </tr>
            ) : (
              rows.map((t) => {
                const assignees = (t.assigneeIds ?? []).map((id) => dir.nameOf(id)).filter(Boolean);
                const loggedH = t.loggedHours ?? (t.loggedMinutes ?? 0) / 60;
                return (
                  <tr key={String(t.id)} className={t.overdue ? "warn-row" : undefined}>
                    <td>
                      <b>{t.title}</b>
                      {t.priority ? <div className="small muted">{humanize(t.priority)}</div> : null}
                    </td>
                    <td>{t.clientName || <span className="muted">—</span>}</td>
                    <td>
                      <StatusPill status={t.status} />
                      {t.overdue ? (
                        <span className="pill bad" style={{ marginLeft: 6 }}>
                          Overdue
                        </span>
                      ) : null}
                    </td>
                    <td className="small">
                      {assignees.length ? assignees.join(", ") : <span className="muted">—</span>}
                    </td>
                    <td className="n mono">{fmtNumber(loggedH, 2)} h</td>
                    <td className="mono">{t.dueDate ? fmtDate(isoDate(t.dueDate)) : "—"}</td>
                    <td className="mono small">{t.externalRef || <span className="muted">—</span>}</td>
                    {canEdit ? (
                      <td>
                        <button type="button" className="btn sm" onClick={() => setEdit(t)}>
                          Edit
                        </button>
                      </td>
                    ) : null}
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
          <button type="button" className="btn sm" disabled={page <= 1 || tasks.isFetching} onClick={() => setPage((p) => p - 1)}>
            Previous
          </button>
          <button
            type="button"
            className="btn sm"
            disabled={page >= totalPages || tasks.isFetching}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </button>
        </div>
      </div>

      {open ? <TaskModal mode="create" onClose={() => setOpen(false)} /> : null}
      {edit ? <TaskModal mode="edit" initial={edit} onClose={() => setEdit(null)} /> : null}
    </div>
  );
}

function TaskModal({
  mode,
  initial,
  onClose,
}: {
  mode: "create" | "edit";
  initial?: TaskView;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const candidates = useQuery({
    queryKey: ["task-assignee-candidates"],
    queryFn: ({ signal }) => apiFetch<Candidate[]>(api.hrms("tasks/assignee-candidates"), { signal }),
  });
  const people = useMemo(() => candidates.data ?? [], [candidates.data]);

  const [form, setForm] = useState({
    title: initial?.title ?? "",
    clientName: initial?.clientName ?? "",
    priority: initial?.priority ?? "MEDIUM",
    status: initial?.status ?? "OPEN",
    dueDate: initial?.dueDate ? isoDate(initial.dueDate) : "",
    externalRef: initial?.externalRef ?? "",
    assigneeIds: (initial?.assigneeIds ?? []).map(String),
  });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [assigneeFilter, setAssigneeFilter] = useState("");

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      mode === "create"
        ? apiFetch(api.hrms("tasks"), { method: "POST", body })
        : apiFetch(api.hrms(`tasks/${initial!.id}`), { method: "PUT", body }),
    onSuccess: () => {
      toast.show(mode === "create" ? "Task created" : "Task updated");
      void qc.invalidateQueries({ queryKey: ["tasks-admin"] });
      void qc.invalidateQueries({ queryKey: ["directory", "tasks"] });
      onClose();
    },
    onError: (e) => toast.error(e),
  });

  const filteredPeople = useMemo(() => {
    const needle = assigneeFilter.trim().toLowerCase();
    if (!needle) return people;
    return people.filter((e) =>
      `${e.fullName ?? ""} ${e.employeeCode ?? ""}`.toLowerCase().includes(needle),
    );
  }, [people, assigneeFilter]);

  function toggleAssignee(id: string) {
    setForm((f) => ({
      ...f,
      assigneeIds: f.assigneeIds.includes(id) ? f.assigneeIds.filter((x) => x !== id) : [...f.assigneeIds, id],
    }));
  }

  function submit() {
    const parsed = taskForm.safeParse(form);
    if (!parsed.success) return setErrors(fieldErrors(parsed));
    setErrors({});
    const v = parsed.data;
    save.mutate({
      title: v.title,
      clientName: v.clientName || undefined,
      priority: v.priority || undefined,
      status: v.status,
      dueDate: v.dueDate || undefined,
      externalRef: v.externalRef || undefined,
      assigneeIds: v.assigneeIds.map(Number),
    });
  }

  return (
    <Modal
      open
      title={mode === "create" ? "New task" : "Edit task"}
      subtitle="Assignees are limited to people under you in the reporting line (plus yourself)."
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="btn primary" disabled={save.isPending} onClick={submit}>
            {save.isPending ? "Saving…" : mode === "create" ? "Create task" : "Save"}
          </button>
        </>
      }
    >
      <div className="form-grid">
        <FormField label="Title" required full error={errors.title}>
          <input type="text" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
        </FormField>
        <FormField label="Client / project" full error={errors.clientName}>
          <input type="text" value={form.clientName} onChange={(e) => setForm({ ...form, clientName: e.target.value })} />
        </FormField>
        <FormField label="Priority">
          <select value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value })}>
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {humanize(p)}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label="Status">
          <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {humanize(s)}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label="Due date" error={errors.dueDate}>
          <input type="date" min={todayIso()} value={form.dueDate} onChange={(e) => setForm({ ...form, dueDate: e.target.value })} />
        </FormField>
        <FormField
          label="External id"
          hint="Stable project-module id (FR-TSK-04). Changing it never drops work logs (logs use task id)."
          error={errors.externalRef}
        >
          <input
            type="text"
            className="mono"
            value={form.externalRef}
            onChange={(e) => setForm({ ...form, externalRef: e.target.value })}
            placeholder="e.g. PM-123"
          />
        </FormField>
        <FormField label="Assignees" required full error={errors.assigneeIds}>
          <input
            type="search"
            placeholder="Filter people…"
            value={assigneeFilter}
            onChange={(e) => setAssigneeFilter(e.target.value)}
            style={{ marginBottom: 8 }}
          />
          {candidates.isLoading ? <div className="small muted">Loading your team…</div> : null}
          {candidates.error ? <div className="notice bad">{(candidates.error as Error).message}</div> : null}
          {!candidates.isLoading && people.length === 0 ? (
            <div className="notice">No assignable people in your reporting team.</div>
          ) : null}
          <div className="stack" style={{ maxHeight: 180, overflow: "auto", gap: 6 }}>
            {filteredPeople.map((e) => {
              const id = String(e.id);
              return (
                <label key={id} className="row" style={{ gap: 8 }}>
                  <input type="checkbox" checked={form.assigneeIds.includes(id)} onChange={() => toggleAssignee(id)} />
                  <span>
                    {e.fullName} {e.employeeCode ? <span className="muted small">({e.employeeCode})</span> : null}
                  </span>
                </label>
              );
            })}
          </div>
        </FormField>
      </div>
    </Modal>
  );
}
