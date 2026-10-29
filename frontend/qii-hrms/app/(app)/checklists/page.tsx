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
import { rowsOf, type Rec } from "@/lib/api/lookups";
import { str } from "@/lib/format";

type Item = { title: string; required: boolean };

const templateSchema = z.object({
  name: z.string().trim().min(2, "Name is required"),
  type: z.string().trim().min(1, "Type is required"),
  items: z
    .array(z.object({ title: z.string().trim(), required: z.boolean() }))
    .transform((a) => a.filter((i) => i.title))
    .refine((a) => a.length > 0, "Add at least one item"),
});

export default function ChecklistsPage() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canManage = can("org", "manage");

  const list = useQuery({
    queryKey: ["checklists"],
    queryFn: ({ signal }) => apiFetch(api.hrms("checklists"), { signal }),
  });
  const rows = rowsOf<Rec>(list.data);

  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [type, setType] = useState("ONBOARDING");
  const [items, setItems] = useState<Item[]>([{ title: "", required: true }]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

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

  return (
    <div className="stack">
      <PageHeader
        title="Checklists"
        subtitle="Onboarding and offboarding steps. Tick items off as they are completed."
        actions={
          canManage ? (
            <button type="button" className="btn primary" onClick={() => { setErr(""); setOpen(true); }}>
              + New checklist
            </button>
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
                      <span style={item.completed ? { textDecoration: "line-through", color: "var(--muted)" } : undefined}>
                        {str(item.title)}
                        {item.required ? <span className="pill warn" style={{ marginLeft: 8 }}>required</span> : null}
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
    </div>
  );
}
