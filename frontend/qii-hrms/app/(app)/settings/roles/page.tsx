"use client";

import Link from "next/link";
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
import { rowsOf, useEmployeesLookup, type Role } from "@/lib/api/lookups";
import { humanize } from "@/lib/format";

const OVERVIEW = "__overview__";
const MATRIX_ACTIONS = ["view", "create", "edit", "delete", "approve", "export"] as const;

type CatalogFeature = { key: string; name: string; scoped: boolean; actions: string[] };
type Catalog = { actions: string[]; features: CatalogFeature[] };

const newRoleSchema = z.object({
  code: z
    .string()
    .trim()
    .min(2, "Code is required")
    .regex(/^[A-Za-z0-9_]+$/, "Letters, numbers and underscores only"),
  name: z.string().trim().min(2, "Name is required"),
  description: z.string(),
});

const scopeTone = (s?: string | null) => (s === "ALL" || s === "YES" ? "good" : s === "TEAM" ? "info" : s === "OWN" ? "" : "");

/** Display value for pills: unscoped ALL → Yes (matches prototype). */
function displayScope(scope: string | null | undefined, scoped: boolean): string {
  if (!scope) return "—";
  const s = scope.toUpperCase();
  if (!scoped && (s === "ALL" || s === "YES")) return "Yes";
  return s === "OWN" ? "Own" : s === "TEAM" ? "Team" : s === "ALL" ? "All" : s;
}

function draftValueFor(scope: string | null | undefined, scoped: boolean): string {
  if (!scope) return "";
  const s = scope.toUpperCase();
  if (!scoped && (s === "ALL" || s === "YES")) return "YES";
  return s;
}

export default function RolesPage() {
  const { can, me } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canManage = can("roles", "manage");
  const canAssign = can("roles", "assign");

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
  const catalogQ = useQuery({
    queryKey: ["roles-catalog"],
    queryFn: ({ signal }) => apiFetch<Catalog>(api.hrms("roles/catalog"), { signal }),
  });
  const roles = useMemo(() => rowsOf<Role>(rolesQ.data), [rolesQ.data]);
  const catalog = catalogQ.data;
  const features = catalog?.features ?? [];
  const actions = useMemo(() => {
    const fromApi = catalog?.actions ?? [];
    const ordered = MATRIX_ACTIONS.filter((a) => fromApi.includes(a) || features.some((f) => f.actions.includes(a)));
    const extra = fromApi.filter((a) => !ordered.includes(a as (typeof MATRIX_ACTIONS)[number]));
    return [...ordered, ...extra];
  }, [catalog?.actions, features]);
  const role = roles.find((r) => String(r.id) === selected);
  const featureByKey = useMemo(() => new Map(features.map((f) => [f.key, f])), [features]);

  // Reset the draft when switching role or when catalog finishes loading.
  const draftKey = role ? `${selected}|${features.length}` : null;
  if (role && draftKey && draftFor !== draftKey) {
    const d: Record<string, string> = {};
    for (const p of role.permissions ?? []) {
      const scoped = featureByKey.get(p.feature)?.scoped ?? p.scoped !== false;
      d[`${p.feature}:${p.action}`] = draftValueFor(p.scope, scoped !== false);
    }
    setDraft(d);
    setDraftFor(draftKey);
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
    void qc.invalidateQueries({ queryKey: ["roles-catalog"] });
    void qc.invalidateQueries({ queryKey: ["lookup", "roles"] });
  }

  const savePerms = useMutation({
    mutationFn: () => {
      const matrix = Object.entries(draft)
        .filter(([, scope]) => scope)
        .map(([key, scope]) => {
          const [feature, action] = key.split(":");
          const scoped = featureByKey.get(feature)?.scoped !== false;
          const normalized = !scoped && scope === "YES" ? "ALL" : scope;
          return { feature, action, scope: normalized };
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
    if (kind === "copy") {
      if (!role) {
        toast.error("Select a role tab first, then click Copy role.");
        return;
      }
      const base = (role.code || "ROLE").toUpperCase().replace(/[^A-Z0-9_]/g, "_");
      setForm({
        code: `${base}_COPY`.slice(0, 48),
        name: `${role.name || role.code} (copy)`,
        description: role.description || "",
      });
    } else {
      setForm({ code: "", name: "", description: "" });
    }
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

  // Matrix edit locked by config flag immutablePermissions (any business can set this).
  const readOnly = !canManage || !!role?.immutablePermissions;

  return (
    <div className="stack">
      <PageHeader
        title="Roles & permissions"
        subtitle="Tick what each role can do and how far it reaches: own records, their team (direct and indirect reports) or everyone. Unscoped features use Yes."
        actions={
          canManage ? (
            <div className="row">
              {role ? (
                <button type="button" className="btn" onClick={() => openModal("copy")}>
                  Copy “{role.name || role.code}”
                </button>
              ) : null}
              <button type="button" className="btn primary" onClick={() => openModal("new")}>
                + New role
              </button>
            </div>
          ) : null
        }
      />
      <ErrorNote error={rolesQ.error || catalogQ.error} />

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
            {r.name || r.code}{" "}
            <span className="muted">{r.holderCount ?? 0}</span>
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
                {features.flatMap((f) =>
                  f.actions.map((a) => (
                    <tr key={`${f.key}:${a}`}>
                      <td>
                        <b>{f.name || humanize(f.key)}</b> <span className="muted">· {a}</span>
                      </td>
                      {roles.map((r) => {
                        const p = (r.permissions ?? []).find((x) => x.feature === f.key && x.action === a);
                        const tone = scopeTone(p?.scope);
                        return (
                          <td key={r.id}>
                            {p ? (
                              <span className={`pill${tone ? ` ${tone}` : ""}`}>
                                {displayScope(p.scope, f.scoped)}
                              </span>
                            ) : (
                              <span className="muted">—</span>
                            )}
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
          {role.immutablePermissions ? (
            <p className="note">
              This role’s permissions are locked by configuration (<span className="mono">immutablePermissions</span>)
              so the tenant cannot lock itself out.
            </p>
          ) : null}
          {role.protectFromOffboard || role.grantsWildcard || (role.minHolders ?? 0) > 0 ? (
            <p className="muted small">
              Policy:
              {role.protectFromOffboard ? " protectFromOffboard" : ""}
              {role.grantsWildcard ? " · grantsWildcard" : ""}
              {(role.minHolders ?? 0) > 0 ? ` · minHolders=${role.minHolders}` : ""}
              {role.assignableBy ? ` · assignableBy=${role.assignableBy}` : ""}
            </p>
          ) : null}
          <div className="tbl-wrap">
            <table className="matrix">
              <thead>
                <tr>
                  <th>Feature</th>
                  {actions.map((a) => (
                    <th key={a}>{a}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {features.map((f) => (
                  <tr key={f.key}>
                    <td>
                      <b>{f.name || humanize(f.key)}</b>
                    </td>
                    {actions.map((a) => {
                      if (!f.actions.includes(a)) return <td key={a} className="muted">·</td>;
                      const key = `${f.key}:${a}`;
                      const choices = f.scoped
                        ? [
                            ["", "—"],
                            ["OWN", "Own"],
                            ["TEAM", "Team"],
                            ["ALL", "All"],
                          ]
                        : [
                            ["", "—"],
                            ["YES", "Yes"],
                          ];
                      return (
                        <td key={a}>
                          {readOnly ? (
                            draft[key] ? (
                              <span className={`pill${scopeTone(draft[key]) ? ` ${scopeTone(draft[key])}` : ""}`}>
                                {displayScope(draft[key], f.scoped)}
                              </span>
                            ) : (
                              <span className="muted">—</span>
                            )
                          ) : (
                            <select
                              aria-label={`${f.name} ${a}`}
                              value={draft[key] ?? ""}
                              onChange={(e) => setDraft({ ...draft, [key]: e.target.value })}
                            >
                              {choices.map(([v, label]) => (
                                <option key={v || "none"} value={v}>
                                  {label}
                                </option>
                              ))}
                            </select>
                          )}
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
              <span className="muted small">Own / Team / All for scoped features · Yes for org-wide features</span>
            </div>
          ) : null}

          <RoleHoldersPanel role={role} canAssign={canAssign} meRoles={me.roles} mePerms={me.permissions} />
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
              {create.isPending ? "Saving…" : modal === "copy" ? "Create copy" : "Create role"}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

type Holder = { id: number | string; fullName?: string; employeeCode?: string; status?: string };

function canAssignRoleConfig(
  role: Role,
  actorRoles: string[] | undefined,
  actorPerms: string[] | undefined,
): boolean {
  const gate = (role.assignableBy ?? "").trim();
  if (!gate) return true;
  if ((actorPerms ?? []).some((p) => p.trim().toLowerCase() === "*:*")) return true;
  const allowed = new Set(
    gate
      .split(",")
      .map((s) => s.trim().toUpperCase())
      .filter(Boolean),
  );
  if (allowed.size === 0) return true;
  return (actorRoles ?? []).some((c) => allowed.has(String(c).toUpperCase()));
}

function RoleHoldersPanel({
  role,
  canAssign,
  meRoles,
  mePerms,
}: {
  role: Role;
  canAssign: boolean;
  meRoles?: string[];
  mePerms?: string[];
}) {
  const toast = useToast();
  const qc = useQueryClient();
  const [pick, setPick] = useState("");
  const assignable = canAssign && canAssignRoleConfig(role, meRoles, mePerms);
  const employees = useEmployeesLookup(canAssign);

  const holdersQ = useQuery({
    queryKey: ["role-holders", role.id],
    queryFn: ({ signal }) => apiFetch<Holder[]>(api.hrms(`roles/${role.id}/holders`), { signal }),
  });
  const holders = holdersQ.data ?? [];
  const holderIds = new Set(holders.map((h) => String(h.id)));

  const assign = useMutation({
    mutationFn: (employeeId: string) =>
      apiFetch(api.hrms(`roles/${role.id}/assign`), { method: "POST", body: { employeeId } }),
    onSuccess: () => {
      toast.show("Role assigned");
      setPick("");
      void qc.invalidateQueries({ queryKey: ["role-holders", role.id] });
      void qc.invalidateQueries({ queryKey: ["roles"] });
      void qc.invalidateQueries({ queryKey: ["lookup", "roles"] });
      void qc.invalidateQueries({ queryKey: ["employees"] });
    },
    onError: (e: Error) => toast.error(e),
  });

  const unassign = useMutation({
    mutationFn: (employeeId: string | number) =>
      apiFetch(api.hrms(`roles/${role.id}/unassign`), { method: "POST", body: { employeeId } }),
    onSuccess: () => {
      toast.show("Role removed");
      void qc.invalidateQueries({ queryKey: ["role-holders", role.id] });
      void qc.invalidateQueries({ queryKey: ["roles"] });
      void qc.invalidateQueries({ queryKey: ["lookup", "roles"] });
      void qc.invalidateQueries({ queryKey: ["employees"] });
    },
    onError: (e: Error) => toast.error(e),
  });

  const candidates = (employees.data ?? []).filter((e) => !holderIds.has(String(e.id)));
  const lockedMin = (role.minHolders ?? 0) > 0 && holders.length <= (role.minHolders ?? 0);

  return (
    <section className="stack" style={{ marginTop: 8 }}>
      <div className="card-head" style={{ marginBottom: 0 }}>
        <h2>People with this role</h2>
      </div>
      <ErrorNote error={holdersQ.error} />
      <div className="row">
        {holders.length === 0 ? <span className="muted">Nobody yet.</span> : null}
        {holders.map((h) => (
          <span key={h.id} className="pill acc">
            <Link href={`/employees/${h.id}`}>{h.fullName || h.employeeCode || h.id}</Link>
            {assignable && !lockedMin ? (
              <>
                {" "}
                <button
                  type="button"
                  className="linkbtn"
                  aria-label="Remove"
                  disabled={unassign.isPending}
                  onClick={() => unassign.mutate(h.id)}
                >
                  ✕
                </button>
              </>
            ) : null}
          </span>
        ))}
      </div>
      {assignable ? (
        <form
          className="row"
          onSubmit={(e) => {
            e.preventDefault();
            if (pick) assign.mutate(pick);
          }}
        >
          <select
            aria-label="Person"
            value={pick}
            onChange={(e) => setPick(e.target.value)}
            style={{ maxWidth: 240 }}
          >
            <option value="">Select person…</option>
            {candidates.map((e) => (
              <option key={e.id} value={String(e.id)}>
                {e.fullName || e.employeeCode || e.id}
              </option>
            ))}
          </select>
          <button type="submit" className="btn sm" disabled={!pick || assign.isPending}>
            {assign.isPending ? "Assigning…" : "Assign role"}
          </button>
        </form>
      ) : canAssign ? (
        <p className="muted small">
          Your roles cannot assign <span className="mono">{role.code}</span>
          {role.assignableBy ? ` (assignableBy=${role.assignableBy})` : ""}.
        </p>
      ) : null}
    </section>
  );
}
