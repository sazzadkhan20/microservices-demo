"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { FormField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { StatusPill } from "@/components/ui/StatusPill";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { rowsOf, useEmployeesLookup, type Rec } from "@/lib/api/lookups";
import { fmtDateTime, str } from "@/lib/format";

type Item = { title: string; required: boolean };

const templateSchema = z.object({
  name: z.string().trim().min(2, "Name is required"),
  type: z.string().trim().min(1, "Type is required"),
  items: z
    .array(z.object({ title: z.string().trim(), required: z.boolean() }))
    .transform((a) => a.filter((i) => i.title))
    .refine((a) => a.length > 0, "Add at least one item"),
});

const assignSchema = z.object({
  employeeId: z.string().min(1, "Select an employee"),
  type: z.string().min(1, "Select a type"),
  templateId: z.string(),
  startOrExitDate: z.string(),
});

export default function ChecklistsPage() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canManage = can("checklists", "create") || can("checklists", "edit");

  const list = useQuery({
    queryKey: ["checklists"],
    queryFn: ({ signal }) => apiFetch(api.hrms("checklists"), { signal }),
  });
  const rows = rowsOf<Rec>(list.data);

  const employees = useEmployeesLookup(canManage);

  // Template create state
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [type, setType] = useState("ONBOARDING");
  const [items, setItems] = useState<Item[]>([{ title: "", required: true }]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  // Assign state
  const [assignOpen, setAssignOpen] = useState(false);
  const [assignForm, setAssignForm] = useState({ employeeId: "", type: "ONBOARDING", templateId: "", startOrExitDate: "" });
  const [assignErrors, setAssignErrors] = useState<Record<string, string>>({});
  const [assignErr, setAssignErr] = useState("");

  const toggle = useMutation({
    mutationFn: ({ checklistId, itemIndex, completed }: { checklistId: string; itemIndex: number; completed: boolean }) =>
      apiFetch(api.hrms(`checklists/${checklistId}/items/${itemIndex}`), { method: "PUT", body: { completed } }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["checklists"] }),
    onError: (e: Error) => toast.error(e),
  });

  const create = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.hrms("checklists/templates"), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Checklist created");
      setOpen(false);
      setName("");
      setItems([{ title: "", required: true }]);
      void qc.invalidateQueries({ queryKey: ["checklists"] });
    },
    onError: (e: Error) => setErr(e.message),
  });

  const assign = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.hrms("checklists/assign"), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Checklist assigned");
      setAssignOpen(false);
      setAssignForm({ employeeId: "", type: "ONBOARDING", templateId: "", startOrExitDate: "" });
      void qc.invalidateQueries({ queryKey: ["checklists"] });
    },
    onError: (e: Error) => setAssignErr(e.message),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = templateSchema.safeParse({ name, type, items });
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setErrors(next);
    }
    setErrors({});
    setErr("");
    create.mutate({ name: parsed.data.name, type: parsed.data.type.toUpperCase(), items: parsed.data.items });
  }

  function submitAssign(e: FormEvent) {
    e.preventDefault();
    const parsed = assignSchema.safeParse(assignForm);
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setAssignErrors(next);
    }
    setAssignErrors({});
    setAssignErr("");
    const body: Record<string, unknown> = {
      employeeId: Number(parsed.data.employeeId),
      type: parsed.data.type,
    };
    if (parsed.data.templateId) body.templateId = Number(parsed.data.templateId);
    if (parsed.data.startOrExitDate) body.startOrExitDate = parsed.data.startOrExitDate;
    assign.mutate(body);
  }

  // Template rows for the assign dropdown
  const templates = rows.filter((r) => !r.employeeId); // templates have no employeeId

  return (
    <div className="stack">
      <PageHeader
        title="Checklists"
        subtitle="Onboarding and offboarding steps. Tick items off as they are completed."
        actions={
          canManage ? (
            <div className="row">
              <button type="button" className="btn" onClick={() => { setAssignErr(""); setAssignErrors({}); setAssignOpen(true); }}>
                Assign
              </button>
              <button type="button" className="btn primary" onClick={() => { setErr(""); setOpen(true); }}>
                + New checklist
              </button>
            </div>
          ) : null
        }
      />
      <ErrorNote error={list.error} />
      {list.isLoading ? <p className="muted">Loading…</p> : null}
      {!list.isLoading && rows.length === 0 ? <div className="card empty">No checklists yet.</div> : null}

      <div className="grid g2" style={{ alignItems: "start" }}>
        {rows.map((c) => {
          const its = (c.items as Rec[]) ?? [];
          const done = its.filter((i) => i.completed).length;
          const pct = its.length ? Math.round((done / its.length) * 100) : 0;
          return (
            <section key={String(c.id)} className="card stack">
              <div className="card-head" style={{ marginBottom: 0 }}>
                <div>
                  <h2>{str(c.name)}</h2>
                  <p className="muted small">
                    {done} of {its.length} done
                  </p>
                </div>
                <StatusPill status={c.type} />
              </div>
              <div style={{ height: 6, background: "var(--sunk)", borderRadius: 4, overflow: "hidden" }}>
                <div style={{ width: `${pct}%`, height: "100%", background: "var(--accent)" }} />
              </div>
              <ul className="stack" style={{ listStyle: "none", padding: 0, margin: 0, gap: 8 }}>
                {its.map((item, idx) => (
                  <li key={idx}>
                    <label className="row" style={{ flexWrap: "nowrap", alignItems: "flex-start" }}>
                      <input
                        type="checkbox"
                        checked={Boolean(item.completed)}
                        disabled={toggle.isPending}
                        onChange={(e) => toggle.mutate({ checklistId: String(c.id), itemIndex: idx, completed: e.target.checked })}
                        style={{ marginTop: 4 }}
                      />
                      <span style={{ flex: 1 }}>
                        <span style={item.completed ? { textDecoration: "line-through", color: "var(--muted)" } : undefined}>
                          {str(item.title)}
                          {item.required ? <span className="pill warn" style={{ marginLeft: 8 }}>required</span> : null}
                        </span>
                        {item.completed && (item.completedBy || item.completedAt) ? (
                          <span className="muted small" style={{ display: "block", marginTop: 2 }}>
                            {item.completedBy ? `by ${str(item.completedBy)}` : ""}
                            {item.completedAt ? ` · ${fmtDateTime(item.completedAt)}` : ""}
                          </span>
                        ) : null}
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
            </section>
          );
        })}
      </div>

      <Modal open={open} onClose={() => setOpen(false)} title="New checklist template">
        <form className="stack" onSubmit={submit} noValidate>
          <div className="form-grid">
            <FormField label="Name" required error={errors.name}>
              <input type="text" value={name} onChange={(e) => setName(e.target.value)} />
            </FormField>
            <FormField label="Type" required error={errors.type}>
              <select value={type} onChange={(e) => setType(e.target.value)}>
                <option value="ONBOARDING">Onboarding</option>
                <option value="OFFBOARDING">Offboarding</option>
                <option value="CUSTOM">Custom</option>
              </select>
            </FormField>
          </div>
          <div className="stack" style={{ gap: 8 }}>
            <span className="label">Items</span>
            {items.map((it, i) => (
              <div key={i} className="row" style={{ flexWrap: "nowrap" }}>
                <input
                  type="text"
                  placeholder={`Step ${i + 1}`}
                  value={it.title}
                  onChange={(e) => setItems(items.map((x, j) => (j === i ? { ...x, title: e.target.value } : x)))}
                />
                <label className="row small" style={{ flexWrap: "nowrap", gap: 4 }}>
                  <input
                    type="checkbox"
                    checked={it.required}
                    onChange={(e) => setItems(items.map((x, j) => (j === i ? { ...x, required: e.target.checked } : x)))}
                  />
                  Required
                </label>
                <button
                  type="button"
                  className="btn sm"
                  aria-label="Remove item"
                  disabled={items.length === 1}
                  onClick={() => setItems(items.filter((_, j) => j !== i))}
                >
                  ✕
                </button>
              </div>
            ))}
            {errors.items ? <span className="err">{errors.items}</span> : null}
            <div>
              <button type="button" className="btn sm" onClick={() => setItems([...items, { title: "", required: false }])}>
                + Add item
              </button>
            </div>
          </div>
          {err ? <div className="notice bad">{err}</div> : null}
          <div className="modal-foot">
            <button type="button" className="btn" onClick={() => setOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={create.isPending}>
              {create.isPending ? "Creating…" : "Create checklist"}
            </button>
          </div>
        </form>
      </Modal>

      <Modal open={assignOpen} onClose={() => setAssignOpen(false)} title="Assign checklist">
        <form className="stack" onSubmit={submitAssign} noValidate>
          <div className="form-grid">
            <FormField label="Employee" required error={assignErrors.employeeId}>
              <select value={assignForm.employeeId} onChange={(e) => setAssignForm({ ...assignForm, employeeId: e.target.value })}>
                <option value="">Select…</option>
                {(employees.data ?? []).map((emp) => (
                  <option key={emp.id} value={emp.id}>
                    {emp.fullName ?? `#${emp.id}`}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Type" required error={assignErrors.type}>
              <select value={assignForm.type} onChange={(e) => setAssignForm({ ...assignForm, type: e.target.value })}>
                <option value="ONBOARDING">Onboarding</option>
                <option value="OFFBOARDING">Offboarding</option>
              </select>
            </FormField>
            <FormField label="Template" hint="Optional — leave blank to use the default for the type">
              <select value={assignForm.templateId} onChange={(e) => setAssignForm({ ...assignForm, templateId: e.target.value })}>
                <option value="">Default</option>
                {templates.map((t) => (
                  <option key={String(t.id)} value={String(t.id)}>
                    {str(t.name)}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Start / exit date" hint="Optional">
              <input
                type="date"
                value={assignForm.startOrExitDate}
                onChange={(e) => setAssignForm({ ...assignForm, startOrExitDate: e.target.value })}
              />
            </FormField>
          </div>
          {assignErr ? <div className="notice bad">{assignErr}</div> : null}
          <div className="modal-foot">
            <button type="button" className="btn" onClick={() => setAssignOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={assign.isPending}>
              {assign.isPending ? "Assigning…" : "Assign checklist"}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
