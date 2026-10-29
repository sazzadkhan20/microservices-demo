"use client";

import { useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { FormField } from "@/components/ui/FormField";
import { ConfirmModal } from "@/components/ui/ConfirmModal";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { rowsOf, type Role } from "@/lib/api/lookups";
import { humanize } from "@/lib/format";

const SCOPES = ["OWN", "TEAM", "ALL"] as const;
const OVERVIEW = "__overview__";

const newRoleSchema = z.object({
  code: z
    .string()
    .trim()
    .min(2, "Code is required")
    .regex(/^[A-Za-z0-9_]+$/, "Letters, numbers and underscores only"),
  name: z.string().trim().min(2, "Name is required"),
  description: z.string(),
});

const scopeTone = (s?: string | null) => (s === "ALL" ? "good" : s === "TEAM" ? "info" : s === "OWN" ? "" : "");

export default function RolesPage() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canManage = can("roles", "manage");

  const [selected, setSelected] = useState(OVERVIEW);
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [draftFor, setDraftFor] = useState<string | null>(null);
  const [modal, setModal] = useState<"new" | "copy" | null>(null);
  const [form, setForm] = useState({ code: "", name: "", description: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");
  const [confirmDel, setConfirmDel] = useState(false);

  const rolesQ = useQuery({
    queryKey: ["roles"],
    queryFn: ({ signal }) => apiFetch(api.hrms("roles"), { signal }),
  });
  const roles = useMemo(() => rowsOf<Role>(rolesQ.data), [rolesQ.data]);
  const role = roles.find((r) => String(r.id) === selected);

  // The set of known permissions = union across all roles (backend rejects unknown pairs).
  const universe = useMemo(() => {
    const features = new Map<string, Set<string>>();
    for (const r of roles) {
      for (const p of r.permissions ?? []) {
        if (!features.has(p.feature)) features.set(p.feature, new Set());
        features.get(p.feature)!.add(p.action);
      }
    }
    const actionOrder = ["view", "create", "edit", "delete", "approve", "manage", "checkin", "correct", "request", "export", "run", "read"];
    const allActions = [...new Set([...features.values()].flatMap((s) => [...s]))].sort(
      (a, b) => (actionOrder.indexOf(a) + 100) % 100 - ((actionOrder.indexOf(b) + 100) % 100) || a.localeCompare(b),
    );
    return { features: [...features.keys()].sort(), actions: allActions, map: features };
  }, [roles]);

  // Reset the draft when switching role (derive during render, no effect).
  if (role && draftFor !== selected) {
    const d: Record<string, string> = {};
    for (const p of role.permissions ?? []) d[`${p.feature}:${p.action}`] = (p.scope || "OWN").toUpperCase();
    setDraft(d);
    setDraftFor(selected);
  }

  const dirty = useMemo(() => {
    if (!role) return false;
    const orig: Record<string, string> = {};
    for (const p of role.permissions ?? []) orig[`${p.feature}:${p.action}`] = (p.scope || "OWN").toUpperCase();
    const keys = new Set([...Object.keys(orig), ...Object.keys(draft)]);
    for (const k of keys) if ((orig[k] ?? "") !== (draft[k] ?? "")) return true;
    return false;
  }, [role, draft]);

  function invalidate() {
    void qc.invalidateQueries({ queryKey: ["roles"] });
    void qc.invalidateQueries({ queryKey: ["lookup", "roles"] });
  }

  const savePerms = useMutation({
    mutationFn: () => {
      const matrix = Object.entries(draft)
        .filter(([, scope]) => scope)
        .map(([key, scope]) => {
          const [feature, action] = key.split(":");
          return { feature, action, scope };
        });
      return apiFetch(api.hrms(`roles/${role!.id}/permissions`), { method: "PUT", body: matrix });
    },
    onSuccess: () => {
      toast.show("Permissions saved");
      setDraftFor(null);
      invalidate();
    },
    onError: (e: Error) => toast.error(e),
  });

  const create = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      modal === "copy"
        ? apiFetch<Role>(api.hrms(`roles/${role!.id}/copy`), { method: "POST", body })
        : apiFetch<Role>(api.hrms("roles"), { method: "POST", body }),
    onSuccess: (r) => {
      toast.show(modal === "copy" ? "Role copied" : "Role created");
      setModal(null);
      setSelected(String(r.id));
      setDraftFor(null);
      invalidate();
    },
    onError: (e: Error) => setErr(e.message),
  });

  const remove = useMutation({
    mutationFn: () => apiFetch(api.hrms(`roles/${role!.id}`), { method: "DELETE" }),
    onSuccess: () => {
      toast.show("Role deleted");
      setConfirmDel(false);
      setSelected(OVERVIEW);
      invalidate();
    },
    onError: (e: Error) => toast.error(e),
  });

  function openModal(kind: "new" | "copy") {
    setForm({ code: "", name: "", description: "" });
    setErrors({});
    setErr("");
    setModal(kind);
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = newRoleSchema.safeParse(form);
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setErrors(next);
    }
    setErrors({});
    setErr("");
    create.mutate({
      code: parsed.data.code.toUpperCase(),
      name: parsed.data.name,
      description: parsed.data.description || undefined,
    });
  }

  const readOnly = !canManage || role?.code === "SUPERADMIN";

  return (
    <div className="stack">
      <PageHeader
        title="Roles & permissions"
        subtitle="Which features each role can use, and whether that applies to the person's own data, their team, or everyone."
        actions={
          canManage ? (
            <button type="button" className="btn primary" onClick={() => openModal("new")}>
              + New role
            </button>
          ) : null
        }
      />
      <ErrorNote error={rolesQ.error} />

      <div className="tabs" role="tablist">
        <button type="button" className={selected === OVERVIEW ? "on" : undefined} onClick={() => setSelected(OVERVIEW)}>
          Overview
        </button>
        {roles.map((r) => (
          <button
            key={r.id}
            type="button"
            className={selected === String(r.id) ? "on" : undefined}
            onClick={() => setSelected(String(r.id))}
          >
            {r.name || r.code}
          </button>
        ))}
      </div>

      {rolesQ.isLoading ? <p className="muted">Loading roles…</p> : null}

      {selected === OVERVIEW && roles.length > 0 ? (
        <section className="card stack">
          <h2>All roles</h2>
          <div className="tbl-wrap">
            <table className="matrix">
              <thead>
                <tr>
                  <th>Permission</th>
                  {roles.map((r) => (
                    <th key={r.id}>{r.name || r.code}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {universe.features.flatMap((f) =>
                  [...(universe.map.get(f) ?? [])].map((a) => (
                    <tr key={`${f}:${a}`}>
                      <td>
                        <b>{humanize(f)}</b> <span className="muted">· {a}</span>
                      </td>
                      {roles.map((r) => {
                        const p = (r.permissions ?? []).find((x) => x.feature === f && x.action === a);
                        const tone = scopeTone(p?.scope);
                        return (
                          <td key={r.id}>
                            {p ? <span className={`pill${tone ? ` ${tone}` : ""}`}>{p.scope || "OWN"}</span> : <span className="muted">—</span>}
                          </td>
                        );
                      })}
                    </tr>
                  )),
                )}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}

      {role ? (
        <section className="card stack">
          <div className="card-head" style={{ marginBottom: 0 }}>
            <div>
              <h2>{role.name || role.code}</h2>
              <p className="muted small">
                <span className="mono">{role.code}</span>
                {role.description ? ` · ${role.description}` : ""}
              </p>
            </div>
            <div className="row">
              {role.systemRole ? <span className="pill info">System role</span> : null}
              {canManage ? (
                <button type="button" className="btn" onClick={() => openModal("copy")}>
                  Copy role
                </button>
              ) : null}
              {canManage && !role.systemRole ? (
                <button
                  type="button"
                  className="btn danger"
                  disabled={remove.isPending}
                  onClick={() => setConfirmDel(true)}
                >
                  Delete
                </button>
              ) : null}
            </div>
          </div>
          {role.code === "SUPERADMIN" ? (
            <p className="note">The Super Admin role is read-only so the tenant can never lock itself out.</p>
          ) : null}
          <div className="tbl-wrap">
            <table className="matrix">
              <thead>
                <tr>
                  <th>Feature</th>
                  {universe.actions.map((a) => (
                    <th key={a}>{a}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {universe.features.map((f) => (
                  <tr key={f}>
                    <td>
                      <b>{humanize(f)}</b>
                    </td>
                    {universe.actions.map((a) => {
                      const exists = universe.map.get(f)?.has(a);
                      if (!exists) return <td key={a} className="muted">·</td>;
                      const key = `${f}:${a}`;
                      return (
                        <td key={a}>
                          <select
                            aria-label={`${f} ${a}`}
                            value={draft[key] ?? ""}
                            disabled={readOnly}
                            onChange={(e) => setDraft({ ...draft, [key]: e.target.value })}
                          >
                            <option value="">—</option>
                            {SCOPES.map((s) => (
                              <option key={s} value={s}>
                                {s}
                              </option>
                            ))}
                          </select>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!readOnly ? (
            <div className="row">
              <button type="button" className="btn primary" disabled={!dirty || savePerms.isPending} onClick={() => savePerms.mutate()}>
                {savePerms.isPending ? "Saving…" : "Save permissions"}
              </button>
              {dirty ? (
                <button type="button" className="btn" onClick={() => setDraftFor(null)}>
                  Discard changes
                </button>
              ) : null}
              <span className="muted small">OWN = own records · TEAM = own team · ALL = everyone</span>
            </div>
          ) : null}
        </section>
      ) : null}

      <ConfirmModal
        open={confirmDel && !!role}
        title={`Delete role “${role?.name || role?.code || ""}”?`}
        confirmLabel="Delete"
        danger
        busy={remove.isPending}
        onCancel={() => setConfirmDel(false)}
        onConfirm={() => remove.mutate()}
      >
        The role must have no employees assigned.
      </ConfirmModal>

      <Modal
        open={modal !== null}
        onClose={() => setModal(null)}
        title={modal === "copy" ? `Copy “${role?.name ?? role?.code}”` : "New role"}
        subtitle={modal === "copy" ? "Creates a new role with the same permissions." : "Starts with no permissions."}
      >
        <form className="stack" onSubmit={submit} noValidate>
          <div className="form-grid">
            <FormField label="Code" required error={errors.code} hint="Upper-case identifier, e.g. TEAM_LEAD">
              <input type="text" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
            </FormField>
            <FormField label="Name" required error={errors.name}>
              <input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </FormField>
            <FormField label="Description" full>
              <input type="text" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </FormField>
          </div>
          {err ? <div className="notice bad">{err}</div> : null}
          <div className="modal-foot">
            <button type="button" className="btn" onClick={() => setModal(null)}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={create.isPending}>
              {create.isPending ? "Saving…" : "Create"}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
