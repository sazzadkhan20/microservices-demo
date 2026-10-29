"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { DataTable, type Column } from "@/components/ui/DataTable";
import { FormField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { StatusPill } from "@/components/ui/StatusPill";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { useDirectory } from "@/lib/api/directory";
import { fieldErrors, type FieldErrors } from "@/lib/admin";
import { fmtDate, humanize, isoDate } from "@/lib/format";
import { taskRowSchema, type TaskRow } from "@/lib/schemas/admin";

const STATUSES = ["OPEN", "IN_PROGRESS", "DONE", "CLOSED"];
const PRIORITIES = ["LOW", "MEDIUM", "HIGH"];

const taskForm = z.object({
  title: z.string().trim().min(1, "Title is required").max(256, "Max 256 characters"),
  description: z.string().trim().max(128, "Max 128 characters"),
  priority: z.string(),
  status: z.string().min(1),
  dueDate: z.string(),
});

/** The backend stores priority in externalRef as `PRIORITY:<LEVEL>`. */
function priorityOf(ref: string | null | undefined): string | null {
  const m = /^PRIORITY:(.+)$/i.exec(ref ?? "");
  return m ? m[1].toUpperCase() : null;
}

export default function TasksPage() {
  const { can } = useAuth();
  const canCreate = can("org", "manage");
  const dir = useDirectory(can("employees", "view"));
  const [status, setStatus] = useState("");
  const [open, setOpen] = useState(false);

  const columns: Column<TaskRow>[] = [
    { key: "title", header: "Task", render: (t) => <b>{t.title}</b> },
    { key: "clientName", header: "Client / description", sortable: true, render: (t) => t.clientName || <span className="muted">—</span> },
    { key: "status", header: "Status", sortable: true, render: (t) => <StatusPill status={t.status} /> },
    {
      key: "priority",
      header: "Priority",
      render: (t) => {
        const p = priorityOf(t.externalRef);
        return p ? <StatusPill tone={p === "HIGH" ? "bad" : p === "MEDIUM" ? "warn" : ""}>{humanize(p)}</StatusPill> : <span className="muted">—</span>;
      },
    },
    { key: "dueDate", header: "Due", className: "mono", render: (t) => (t.dueDate ? fmtDate(isoDate(t.dueDate)) : "—") },
    { key: "createdBy", header: "Created by", render: (t) => dir.nameOf(t.createdBy) },
  ];

  return (
    <div className="stack">
      <PageHeader
        title="Tasks"
        subtitle="Work items that time is logged against on the Work log page."
        actions={
          canCreate ? (
            <button type="button" className="btn primary" onClick={() => setOpen(true)}>
              New task
            </button>
          ) : null
        }
      />
      <DataTable<TaskRow>
        queryKey="tasks-admin"
        path={api.hrms("tasks")}
        itemSchema={taskRowSchema}
        columns={columns}
        filters={status ? { status } : {}}
        emptyText="No tasks yet"
        rowKey={(r) => String(r.id)}
        toolbar={
          <select aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value)} style={{ width: 160 }}>
            <option value="">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {humanize(s)}
              </option>
            ))}
          </select>
        }
      />
      <div className="note small">Tasks can be created here; editing, assigning and closing tasks is not offered by the backend yet.</div>
      {open ? <TaskModal onClose={() => setOpen(false)} /> : null}
    </div>
  );
}

function TaskModal({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({ title: "", description: "", priority: "MEDIUM", status: "OPEN", dueDate: "" });
  const [errors, setErrors] = useState<FieldErrors>({});
  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.hrms("tasks"), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Task created");
      void qc.invalidateQueries({ queryKey: ["tasks-admin"] });
      void qc.invalidateQueries({ queryKey: ["directory", "tasks"] });
      onClose();
    },
    onError: (e) => toast.error(e),
  });
  function submit() {
    const parsed = taskForm.safeParse(form);
    if (!parsed.success) return setErrors(fieldErrors(parsed));
    setErrors({});
    const v = parsed.data;
    save.mutate({
      title: v.title,
      description: v.description || undefined,
      priority: v.priority || undefined,
      status: v.status,
      dueDate: v.dueDate || undefined,
    });
  }
  return (
    <Modal
      open
      title="New task"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="btn primary" disabled={save.isPending} onClick={submit}>
            {save.isPending ? "Saving…" : "Create task"}
          </button>
        </>
      }
    >
      <div className="form-grid">
        <FormField label="Title" required full error={errors.title}>
          <input type="text" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
        </FormField>
        <FormField label="Client / description" full error={errors.description}>
          <input type="text" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
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
          <input type="date" value={form.dueDate} onChange={(e) => setForm({ ...form, dueDate: e.target.value })} />
        </FormField>
      </div>
    </Modal>
  );
}
