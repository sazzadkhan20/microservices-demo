"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { DataTable } from "@/components/ui/DataTable";
import { FormField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { Tabs } from "@/components/ui/Tabs";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import {
  byId,
  rowsOf,
  useDepartments,
  useEmployeesLookup,
  type Loc,
  type Rec,
  type Team,
} from "@/lib/api/lookups";
import { str } from "@/lib/format";

const TABS = [
  { id: "departments", label: "Departments" },
  { id: "teams", label: "Teams" },
  { id: "locations", label: "Locations" },
];

const pass = <T extends z.ZodRawShape>(s: T) => z.object(s).passthrough();
const teamRow = pass({ id: z.union([z.number(), z.string()]) });
const locRow = pass({ id: z.union([z.number(), z.string()]) });

function collect(err: z.ZodError) {
  const next: Record<string, string> = {};
  for (const i of err.issues) next[String(i.path[0])] ||= i.message;
  return next;
}

export default function OrgSettingsPage() {
  const { can } = useAuth();
  const [tab, setTab] = useState("departments");
  const canManage = can("org", "manage");

  return (
    <div className="stack">
      <PageHeader title="Organization" subtitle="Departments, teams and locations used across employees, leave and attendance." />
      <Tabs tabs={TABS} value={tab} onChange={setTab} />
      {tab === "departments" ? <Departments canManage={canManage} /> : null}
      {tab === "teams" ? <Teams canManage={canManage} /> : null}
      {tab === "locations" ? <Locations canManage={canManage} /> : null}
    </div>
  );
}

/* ---------------------------- Departments + tree ---------------------------- */

function Departments({ canManage }: { canManage: boolean }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ code: "", name: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  const tree = useQuery({
    queryKey: ["org-tree"],
    queryFn: ({ signal }) => apiFetch(api.hrms("org/tree"), { signal }),
  });
  const nodes = rowsOf<Rec>(tree.data);

  const create = useMutation({
    mutationFn: (body: typeof form) => apiFetch(api.hrms("org/departments"), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Department created");
      setOpen(false);
      setForm({ code: "", name: "" });
      void qc.invalidateQueries({ queryKey: ["org-tree"] });
      void qc.invalidateQueries({ queryKey: ["lookup", "departments"] });
    },
    onError: (e: Error) => setErr(e.message),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = z
      .object({ code: z.string().trim().min(1, "Code is required"), name: z.string().trim().min(1, "Name is required") })
      .safeParse(form);
    if (!parsed.success) return setErrors(collect(parsed.error));
    setErrors({});
    setErr("");
    create.mutate({ code: parsed.data.code.toUpperCase(), name: parsed.data.name });
  }

  return (
    <section className="card stack">
      <div className="card-head" style={{ marginBottom: 0 }}>
        <h2>Org tree</h2>
        {canManage ? (
          <button type="button" className="btn primary" onClick={() => { setErr(""); setOpen(true); }}>
            + New department
          </button>
        ) : null}
      </div>
      <ErrorNote error={tree.error} />
      {tree.isLoading ? <p className="muted">Loading…</p> : null}
      <div className="tree">
        <ul>
          {nodes.map((d) => (
            <li key={String(d.id)}>
              <span className="node">
                <b>{str(d.name)}</b>
                <span className="muted small mono">{str(d.code)}</span>
              </span>
              {Array.isArray(d.teams) && d.teams.length > 0 ? (
                <ul>
                  {(d.teams as Rec[]).map((t) => (
                    <li key={String(t.id)}>
                      <span className="node">
                        {str(t.name)}
                        <span className="muted small">
                          max {str(t.maxOnLeave, "1")} on leave · {str(t.clashAction)}
                        </span>
                      </span>
                    </li>
                  ))}
                </ul>
              ) : null}
            </li>
          ))}
        </ul>
      </div>

      <Modal open={open} onClose={() => setOpen(false)} title="New department">
        <form className="stack" onSubmit={submit} noValidate>
          <div className="form-grid">
            <FormField label="Code" required error={errors.code}>
              <input type="text" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
            </FormField>
            <FormField label="Name" required error={errors.name}>
              <input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </FormField>
          </div>
          {err ? <div className="notice bad">{err}</div> : null}
          <div className="modal-foot">
            <button type="button" className="btn" onClick={() => setOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={create.isPending}>
              Create
            </button>
          </div>
        </form>
      </Modal>
    </section>
  );
}

/* ---------------------------------- Teams ---------------------------------- */

const teamSchema = z.object({
  code: z.string().trim().min(1, "Code is required"),
  name: z.string().trim().min(1, "Name is required"),
  departmentId: z.string(),
  leadEmployeeId: z.string(),
  maxOnLeave: z.coerce.number("Enter a number").int().min(0, "Must be ≥ 0").max(100),
  clashAction: z.enum(["ALERT", "BLOCK"]),
});

function Teams({ canManage }: { canManage: boolean }) {
  const qc = useQueryClient();
  const toast = useToast();
  const departments = useDepartments();
  const employees = useEmployeesLookup();
  const deptName = byId(departments.data, (d) => d.name);
  const empName = byId(employees.data, (e) => e.fullName || `#${e.id}`);

  const blank = { code: "", name: "", departmentId: "", leadEmployeeId: "", maxOnLeave: "1", clashAction: "ALERT" };
  const [editing, setEditing] = useState<Team | "new" | null>(null);
  const [form, setForm] = useState(blank);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  function openFor(t: Team | "new") {
    setErr("");
    setErrors({});
    setForm(
      t === "new"
        ? blank
        : {
            code: str(t.code, ""),
            name: str(t.name, ""),
            departmentId: t.departmentId != null ? String(t.departmentId) : "",
            leadEmployeeId: t.leadEmployeeId != null ? String(t.leadEmployeeId) : "",
            maxOnLeave: String(t.maxOnLeave ?? 1),
            clashAction: t.clashAction || "ALERT",
          },
    );
    setEditing(t);
  }

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      editing && editing !== "new"
        ? apiFetch(api.hrms(`teams/${editing.id}`), { method: "PUT", body })
        : apiFetch(api.hrms("teams"), { method: "POST", body }),
    onSuccess: () => {
      toast.show(editing === "new" ? "Team created" : "Team saved");
      setEditing(null);
      void qc.invalidateQueries({ queryKey: ["teams"] });
      void qc.invalidateQueries({ queryKey: ["lookup", "teams"] });
      void qc.invalidateQueries({ queryKey: ["org-tree"] });
    },
    onError: (e: Error) => setErr(e.message),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = teamSchema.safeParse(form);
    if (!parsed.success) return setErrors(collect(parsed.error));
    setErrors({});
    setErr("");
    save.mutate({
      code: parsed.data.code,
      name: parsed.data.name,
      departmentId: parsed.data.departmentId ? Number(parsed.data.departmentId) : undefined,
      leadEmployeeId: parsed.data.leadEmployeeId ? Number(parsed.data.leadEmployeeId) : undefined,
      maxOnLeave: parsed.data.maxOnLeave,
      clashAction: parsed.data.clashAction,
    });
  }

  return (
    <section className="card stack">
      <div className="card-head" style={{ marginBottom: 0 }}>
        <h2>Teams</h2>
        {canManage ? (
          <button type="button" className="btn primary" onClick={() => openFor("new")}>
            + New team
          </button>
        ) : null}
      </div>
      <DataTable<Team>
        queryKey="teams"
        path={api.hrms("teams")}
        itemSchema={teamRow as never}
        emptyText="No teams yet"
        rowKey={(r) => String(r.id)}
        onRowClick={canManage ? (r) => openFor(r) : undefined}
        columns={[
          { key: "code", header: "Code", sortable: true, className: "mono" },
          { key: "name", header: "Name", sortable: true, render: (r) => <b>{r.name}</b> },
          { key: "departmentId", header: "Department", render: (r) => deptName.get(String(r.departmentId)) ?? "—" },
          { key: "leadEmployeeId", header: "Lead", render: (r) => empName.get(String(r.leadEmployeeId)) ?? "—" },
          { key: "maxOnLeave", header: "Max on leave", className: "n" },
          { key: "clashAction", header: "Clash action", render: (r) => <span className={`pill ${r.clashAction === "BLOCK" ? "bad" : "warn"}`}>{r.clashAction}</span> },
        ]}
      />
      <Modal open={!!editing} onClose={() => setEditing(null)} title={editing === "new" ? "New team" : "Edit team"}>
        <form className="stack" onSubmit={submit} noValidate>
          <div className="form-grid">
            <FormField label="Code" required error={errors.code}>
              <input type="text" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
            </FormField>
            <FormField label="Name" required error={errors.name}>
              <input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </FormField>
            <FormField label="Department" error={errors.departmentId}>
              <select value={form.departmentId} onChange={(e) => setForm({ ...form, departmentId: e.target.value })}>
                <option value="">— None —</option>
                {(departments.data ?? []).map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Team lead" error={errors.leadEmployeeId}>
              <select value={form.leadEmployeeId} onChange={(e) => setForm({ ...form, leadEmployeeId: e.target.value })}>
                <option value="">— None —</option>
                {(employees.data ?? []).map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.fullName}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Max on leave at once" error={errors.maxOnLeave}>
              <input type="number" min={0} value={form.maxOnLeave} onChange={(e) => setForm({ ...form, maxOnLeave: e.target.value })} />
            </FormField>
            <FormField label="When exceeded" error={errors.clashAction}>
              <select value={form.clashAction} onChange={(e) => setForm({ ...form, clashAction: e.target.value })}>
                <option value="ALERT">Alert approver</option>
                <option value="BLOCK">Block request</option>
              </select>
            </FormField>
          </div>
          {err ? <div className="notice bad">{err}</div> : null}
          <div className="modal-foot">
            <button type="button" className="btn" onClick={() => setEditing(null)}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={save.isPending}>
              {save.isPending ? "Saving…" : "Save"}
            </button>
          </div>
        </form>
      </Modal>
    </section>
  );
}

/* -------------------------------- Locations -------------------------------- */

const locSchema = z.object({
  code: z.string().trim().min(1, "Code is required"),
  name: z.string().trim().min(1, "Name is required"),
  country: z.string(),
  timeZone: z.string().trim().min(1, "Time zone is required (e.g. Asia/Dhaka)"),
  defaultCurrency: z.string().trim().regex(/^[A-Za-z]{3}$/, "3-letter currency code"),
});

function Locations({ canManage }: { canManage: boolean }) {
  const qc = useQueryClient();
  const toast = useToast();
  const blank = { code: "", name: "", country: "", timeZone: "", defaultCurrency: "" };
  const [editing, setEditing] = useState<Loc | "new" | null>(null);
  const [form, setForm] = useState(blank);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  function openFor(l: Loc | "new") {
    setErr("");
    setErrors({});
    setForm(
      l === "new"
        ? blank
        : {
            code: str(l.code, ""),
            name: str(l.name, ""),
            country: str(l.country, ""),
            timeZone: str(l.timeZone, ""),
            defaultCurrency: str(l.defaultCurrency, ""),
          },
    );
    setEditing(l);
  }

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      editing && editing !== "new"
        ? apiFetch(api.hrms(`locations/${editing.id}`), { method: "PUT", body })
        : apiFetch(api.hrms("locations"), { method: "POST", body }),
    onSuccess: () => {
      toast.show(editing === "new" ? "Location created" : "Location saved");
      setEditing(null);
      void qc.invalidateQueries({ queryKey: ["locations"] });
      void qc.invalidateQueries({ queryKey: ["lookup", "locations"] });
    },
    onError: (e: Error) => setErr(e.message),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = locSchema.safeParse(form);
    if (!parsed.success) return setErrors(collect(parsed.error));
    setErrors({});
    setErr("");
    save.mutate({
      ...parsed.data,
      country: parsed.data.country || undefined,
      defaultCurrency: parsed.data.defaultCurrency.toUpperCase(),
    });
  }

  return (
    <section className="card stack">
      <div className="card-head" style={{ marginBottom: 0 }}>
        <h2>Locations</h2>
        {canManage ? (
          <button type="button" className="btn primary" onClick={() => openFor("new")}>
            + New location
          </button>
        ) : null}
      </div>
      <DataTable<Loc>
        queryKey="locations"
        path={api.hrms("locations")}
        itemSchema={locRow as never}
        emptyText="No locations yet"
        rowKey={(r) => String(r.id)}
        onRowClick={canManage ? (r) => openFor(r) : undefined}
        columns={[
          { key: "code", header: "Code", sortable: true, className: "mono" },
          { key: "name", header: "Name", sortable: true, render: (r) => <b>{r.name}</b> },
          { key: "country", header: "Country", render: (r) => str(r.country) },
          { key: "timeZone", header: "Time zone", className: "mono" },
          { key: "defaultCurrency", header: "Currency", className: "mono" },
        ]}
      />
      <Modal open={!!editing} onClose={() => setEditing(null)} title={editing === "new" ? "New location" : "Edit location"}>
        <form className="stack" onSubmit={submit} noValidate>
          <div className="form-grid">
            <FormField label="Code" required error={errors.code}>
              <input type="text" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
            </FormField>
            <FormField label="Name" required error={errors.name}>
              <input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </FormField>
            <FormField label="Country" error={errors.country}>
              <input type="text" value={form.country} onChange={(e) => setForm({ ...form, country: e.target.value })} />
            </FormField>
            <FormField label="Time zone" required error={errors.timeZone} hint="IANA name, e.g. Asia/Dhaka">
              <input type="text" value={form.timeZone} onChange={(e) => setForm({ ...form, timeZone: e.target.value })} />
            </FormField>
            <FormField label="Default currency" required error={errors.defaultCurrency}>
              <input
                type="text"
                maxLength={3}
                value={form.defaultCurrency}
                onChange={(e) => setForm({ ...form, defaultCurrency: e.target.value.toUpperCase() })}
              />
            </FormField>
          </div>
          {err ? <div className="notice bad">{err}</div> : null}
          <div className="modal-foot">
            <button type="button" className="btn" onClick={() => setEditing(null)}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={save.isPending}>
              {save.isPending ? "Saving…" : "Save"}
            </button>
          </div>
        </form>
      </Modal>
    </section>
  );
}
