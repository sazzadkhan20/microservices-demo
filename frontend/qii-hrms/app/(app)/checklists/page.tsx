"use client";

import { useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { FormField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { StatusPill } from "@/components/ui/StatusPill";
import { Tabs } from "@/components/ui/Tabs";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { rowsOf, useEmployeesLookup, type Rec } from "@/lib/api/lookups";
import { fmtDate, fmtDateTime, str } from "@/lib/format";

/** HR / manager-of-subject / subject employee / named person (requires ownerEmployeeId). */
const OWNER_TYPES = ["HR", "MANAGER", "EMPLOYEE", "NAMED"] as const;

type TemplateItem = {
  title: string;
  required: boolean;
  ownerType: (typeof OWNER_TYPES)[number];
  ownerEmployeeId: string;
  dueOffsetDays: number;
};

const templateItemSchema = z
  .object({
    title: z.string().trim().min(1),
    required: z.boolean(),
    ownerType: z.enum(OWNER_TYPES),
    ownerEmployeeId: z.string().optional(),
    dueOffsetDays: z.number().int(),
  })
  .superRefine((item, ctx) => {
    if (item.ownerType === "NAMED" && !String(item.ownerEmployeeId ?? "").trim()) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: "Select the owner employee",
        path: ["ownerEmployeeId"],
      });
    }
  });

const templateSchema = z.object({
  name: z.string().trim().min(2, "Name is required"),
  type: z.enum(["ONBOARDING", "OFFBOARDING", "CUSTOM"]),
  items: z
    .array(templateItemSchema)
    .transform((a) => a.filter((i) => i.title.trim()))
    .refine((a) => a.length > 0, "Add at least one item"),
});

const assignSchema = z
  .object({
    employeeId: z.string().min(1, "Select an employee"),
    type: z.enum(["ONBOARDING", "OFFBOARDING", "CUSTOM"]),
    templateId: z.string().optional(),
    startOrExitDate: z.string().min(1, "Base date is required for due dates"),
  })
  .superRefine((v, ctx) => {
    if (v.type === "CUSTOM" && !String(v.templateId ?? "").trim()) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: "Select a custom template",
        path: ["templateId"],
      });
    }
  });

const blankItem = (): TemplateItem => ({
  title: "",
  required: true,
  ownerType: "HR",
  ownerEmployeeId: "",
  dueOffsetDays: 0,
});

function ownerLabel(t: string | undefined | null) {
  switch (String(t ?? "").toUpperCase()) {
    case "HR":
      return "HR";
    case "MANAGER":
      return "Subject's manager";
    case "EMPLOYEE":
      return "Subject employee";
    case "NAMED":
      return "Specific employee";
    default:
      return str(t, "—");
  }
}

function offsetHint(n: number, type: string) {
  const t = String(type ?? "").toUpperCase();
  const base = t === "OFFBOARDING" ? "exit date" : t === "CUSTOM" ? "base date" : "start date";
  if (n === 0) return `Due on the ${base}`;
  if (n > 0) return `Due ${n} day${n === 1 ? "" : "s"} after the ${base}`;
  const d = Math.abs(n);
  return `Due ${d} day${d === 1 ? "" : "s"} before the ${base}`;
}

export default function ChecklistsPage() {
  const { me, can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canManage = can("checklists", "create");
  const canCreate = can("checklists", "create");
  const selfId = me.employeeId != null ? String(me.employeeId) : "";

  const [tab, setTab] = useState("assigned");
  /** Default: My items (owner inbox). By-employee is optional for HR/managers. */
  const [selectedEmpId, setSelectedEmpId] = useState("");
  const [empQuery, setEmpQuery] = useState("");
  const [typeFilter, setTypeFilter] = useState<"ALL" | "ONBOARDING" | "OFFBOARDING" | "CUSTOM">("ALL");
  const [ownerInbox, setOwnerInbox] = useState(true);
  const holdsHrRole = useMemo(
    () => (me.roles ?? []).some((r) => String(r).trim().toUpperCase() === "HR"),
    [me.roles],
  );

  const tabs = useMemo(() => {
    const t: { id: string; label: string }[] = [{ id: "assigned", label: "Active checklists" }];
    if (canManage) t.push({ id: "templates", label: "Templates" });
    return t;
  }, [canManage]);

  const needEmpLookup = canManage || can("employees", "view") || can("checklists", "edit") || canCreate;
  const employees = useEmployeesLookup(needEmpLookup || canManage);
  const empName = useMemo(() => {
    const m = new Map<string, string>();
    for (const e of employees.data ?? []) {
      m.set(String(e.id), e.fullName ?? `#${e.id}`);
    }
    if (selfId && me.name) m.set(selfId, me.name);
    return m;
  }, [employees.data, selfId, me.name]);

  const empOptions = useMemo(() => {
    const all = employees.data ?? [];
    const q = empQuery.trim().toLowerCase();
    const filtered = !q
      ? all
      : all.filter((e) => {
          const name = String(e.fullName ?? "").toLowerCase();
          const code = String(e.employeeCode ?? "").toLowerCase();
          return name.includes(q) || code.includes(q) || String(e.id).includes(q);
        });
    return filtered.slice(0, 80);
  }, [employees.data, empQuery]);

  const templatesQ = useQuery({
    queryKey: ["checklists", "templates"],
    queryFn: ({ signal }) =>
      apiFetch(api.hrms("checklists"), { query: { templatesOnly: true }, signal }),
    enabled: canManage,
  });
  const templates = rowsOf<Rec>(templatesQ.data);

  const assignedEnabled = ownerInbox || !!selectedEmpId;
  const assignedQ = useQuery({
    queryKey: ["checklists", "assigned", ownerInbox ? "inbox" : selectedEmpId],
    queryFn: ({ signal }) =>
      apiFetch(
        api.hrms("checklists"),
        ownerInbox ? { signal } : { query: { employeeId: selectedEmpId }, signal },
      ),
    enabled: assignedEnabled,
  });
  const assigned = useMemo(() => {
    const rows = rowsOf<Rec>(assignedQ.data).filter((r) => r.employeeId != null);
    if (typeFilter === "ALL") return rows;
    return rows.filter((r) => String(r.type).toUpperCase() === typeFilter);
  }, [assignedQ.data, typeFilter]);

  const selectedEmpLabel =
    (selectedEmpId && empName.get(selectedEmpId)) ||
    (selectedEmpId ? `#${selectedEmpId}` : "");

  const invalidateChecklists = () => {
    void qc.invalidateQueries({ queryKey: ["checklists"] });
  };

  const [tplOpen, setTplOpen] = useState(false);
  const [editingTpl, setEditingTpl] = useState<Rec | null>(null);
  const [name, setName] = useState("");
  const [type, setType] = useState<"ONBOARDING" | "OFFBOARDING" | "CUSTOM">("ONBOARDING");
  const [items, setItems] = useState<TemplateItem[]>([blankItem()]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  const [assignOpen, setAssignOpen] = useState(false);
  const [assignForm, setAssignForm] = useState({
    employeeId: "",
    type: "ONBOARDING" as "ONBOARDING" | "OFFBOARDING" | "CUSTOM",
    templateId: "",
    startOrExitDate: "",
  });
  const [assignErrors, setAssignErrors] = useState<Record<string, string>>({});
  const [assignErr, setAssignErr] = useState("");

  function openNewTemplate() {
    setEditingTpl(null);
    setName("");
    setType("ONBOARDING");
    setItems([blankItem()]);
    setErrors({});
    setErr("");
    setTplOpen(true);
  }

  function openEditTemplate(t: Rec) {
    setEditingTpl(t);
    setName(str(t.name, ""));
    const ty = String(t.type ?? "ONBOARDING").toUpperCase();
    setType(ty === "OFFBOARDING" || ty === "CUSTOM" ? ty : "ONBOARDING");
    const its = ((t.items as Rec[]) ?? []).map((i) => {
      const ot = String(i.ownerType ?? "HR").toUpperCase();
      const ownerType = (OWNER_TYPES.includes(ot as (typeof OWNER_TYPES)[number])
        ? ot
        : "HR") as (typeof OWNER_TYPES)[number];
      return {
        title: str(i.title, ""),
        required: Boolean(i.required),
        ownerType,
        ownerEmployeeId: i.ownerEmployeeId != null ? String(i.ownerEmployeeId) : "",
        dueOffsetDays: Number(i.dueOffsetDays ?? 0),
      };
    });
    setItems(its.length ? its : [blankItem()]);
    setErrors({});
    setErr("");
    setTplOpen(true);
  }

  const toggle = useMutation({
    mutationFn: ({ checklistId, itemIndex, completed }: { checklistId: string; itemIndex: number; completed: boolean }) =>
      apiFetch(api.hrms(`checklists/${checklistId}/items/${itemIndex}`), { method: "PUT", body: { completed } }),
    onSuccess: () => {
      toast.show("Checklist updated");
      invalidateChecklists();
    },
    onError: (e: Error) => toast.error(e),
  });

  const saveTemplate = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      editingTpl
        ? apiFetch(api.hrms(`checklists/templates/${editingTpl.id}`), { method: "PUT", body })
        : apiFetch(api.hrms("checklists/templates"), { method: "POST", body }),
    onSuccess: () => {
      toast.show(editingTpl ? "Template saved" : "Template created");
      setTplOpen(false);
      invalidateChecklists();
    },
    onError: (e: Error) => setErr(e.message),
  });

  const assign = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.hrms("checklists/assign"), { method: "POST", body }),
    onSuccess: (_data, vars) => {
      toast.show("Checklist assigned");
      setAssignOpen(false);
      const empId = vars?.employeeId != null ? String(vars.employeeId) : assignForm.employeeId;
      setOwnerInbox(false);
      if (empId) setSelectedEmpId(empId);
      setAssignForm({ employeeId: "", type: "ONBOARDING", templateId: "", startOrExitDate: "" });
      setTab("assigned");
      invalidateChecklists();
    },
    onError: (e: Error) => setAssignErr(e.message),
  });

  function submitTemplate(e: FormEvent) {
    e.preventDefault();
    const parsed = templateSchema.safeParse({ name, type, items });
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) {
        const key = i.path.length > 1 ? `${i.path[0]}.${i.path[1]}` : String(i.path[0]);
        next[key] ||= i.message;
        if (i.path[0] === "items" && i.path.includes("ownerEmployeeId")) {
          next.ownerEmployeeId = i.message;
        }
      }
      return setErrors(next);
    }
    setErrors({});
    setErr("");
    saveTemplate.mutate({
      name: parsed.data.name,
      type: parsed.data.type,
      items: parsed.data.items.map((it) => {
        const row: Record<string, unknown> = {
          title: it.title,
          required: it.required,
          ownerType: it.ownerType,
          dueOffsetDays: it.dueOffsetDays,
        };
        if (it.ownerType === "NAMED" && it.ownerEmployeeId) {
          row.ownerEmployeeId = Number(it.ownerEmployeeId);
        }
        return row;
      }),
    });
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
      startOrExitDate: parsed.data.startOrExitDate,
    };
    if (parsed.data.templateId) body.templateId = parsed.data.templateId;
    assign.mutate(body);
  }

  function canTickItem(item: Rec): boolean {
    if (can("checklists", "create")) return true;
    const ownerType = String(item.ownerType ?? "").toUpperCase();
    // HR-owned steps are a role pool — any dedicated HR holder can tick.
    if (ownerType === "HR" && holdsHrRole) return true;
    const ownerId = item.ownerEmployeeId != null ? String(item.ownerEmployeeId) : "";
    return !!me.employeeId && ownerId === String(me.employeeId);
  }

  function itemOwnerLabel(item: Rec): string {
    if (item.ownerDisplay) return str(item.ownerDisplay);
    const ot = String(item.ownerType ?? "").toUpperCase();
    const name =
      str(item.ownerName) ||
      (item.ownerEmployeeId != null
        ? empName.get(String(item.ownerEmployeeId)) || `#${item.ownerEmployeeId}`
        : "");
    if (ot === "HR") return name ? `HR · ${name}` : "HR";
    return name || ownerLabel(ot);
  }

  function isMyItem(item: Rec): boolean {
    const ot = String(item.ownerType ?? "").toUpperCase();
    if (ot === "HR" && holdsHrRole) return true;
    return (
      me.employeeId != null &&
      item.ownerEmployeeId != null &&
      String(item.ownerEmployeeId) === String(me.employeeId)
    );
  }

  const filteredTemplates = templates.filter(
    (t) => String(t.type ?? "").toUpperCase() === assignForm.type,
  );

  return (
    <div className="stack">
      <PageHeader
        title="Checklists"
        subtitle="Each step has an owner type and a due date from start/exit. HR steps go to the HR role pool (not the person who assigned). My items shows what you own."
        actions={
          canManage ? (
            <div className="row">
              {canCreate ? (
                <button
                  type="button"
                  className="btn"
                  onClick={() => {
                    setAssignErr("");
                    setAssignErrors({});
                    setAssignOpen(true);
                  }}
                >
                  Assign
                </button>
              ) : null}
              {canCreate ? (
                <button type="button" className="btn primary" onClick={openNewTemplate}>
                  + New template
                </button>
              ) : null}
            </div>
          ) : null
        }
      />
      <ErrorNote error={tab === "templates" ? templatesQ.error : assignedQ.error} />

      {tabs.length > 1 ? <Tabs tabs={tabs} value={tab} onChange={setTab} /> : null}

      {tab === "templates" && canManage ? (
        <div className="stack">
          {templatesQ.isLoading ? <p className="muted">Loading templates…</p> : null}
          {templates.length === 0 && !templatesQ.isLoading ? (
            <div className="card empty">No templates yet. Create an onboarding or offboarding template.</div>
          ) : null}
          <div className="grid g2" style={{ alignItems: "start" }}>
            {templates.map((t) => {
              const its = (t.items as Rec[]) ?? [];
              return (
                <section key={String(t.id)} className="card stack">
                  <div className="card-head" style={{ marginBottom: 0 }}>
                    <div>
                      <h2 style={{ margin: 0 }}>{str(t.name)}</h2>
                      <p className="muted small" style={{ margin: "4px 0 0" }}>
                        {its.length} step{its.length === 1 ? "" : "s"} · due offsets from{" "}
                        {String(t.type).toUpperCase() === "OFFBOARDING" ? "exit" : "start"} date
                      </p>
                    </div>
                    <div className="row" style={{ gap: 8 }}>
                      <StatusPill status={t.type} />
                      {can("checklists", "edit") ? (
                        <button type="button" className="btn sm" onClick={() => openEditTemplate(t)}>
                          Edit
                        </button>
                      ) : null}
                    </div>
                  </div>
                  <ul className="stack" style={{ listStyle: "none", padding: 0, margin: 0, gap: 10 }}>
                    {its.map((item, idx) => {
                      const named =
                        item.ownerEmployeeId != null
                          ? empName.get(String(item.ownerEmployeeId)) || `#${item.ownerEmployeeId}`
                          : null;
                      return (
                        <li key={idx} className="row between" style={{ alignItems: "flex-start", gap: 12 }}>
                          <div style={{ flex: 1 }}>
                            <div>
                              <b>{str(item.title)}</b>
                              {item.required ? (
                                <span className="pill warn" style={{ marginLeft: 8 }}>
                                  required
                                </span>
                              ) : null}
                            </div>
                            <div className="muted small" style={{ marginTop: 2 }}>
                              Owner: {ownerLabel(str(item.ownerType))}
                              {named ? ` · ${named}` : ""}
                              {" · "}
                              {offsetHint(Number(item.dueOffsetDays ?? 0), str(t.type))}
                            </div>
                          </div>
                        </li>
                      );
                    })}
                  </ul>
                </section>
              );
            })}
          </div>
        </div>
      ) : null}

      {tab === "assigned" ? (
        <div className="stack">
          <section className="card stack" style={{ gap: 12 }}>
            <div className="row between" style={{ alignItems: "flex-end", flexWrap: "wrap", gap: 12 }}>
              <div style={{ flex: "1 1 280px", minWidth: 220 }}>
                <div className="label">View</div>
                <div className="row" style={{ gap: 6, marginBottom: 8 }} role="group" aria-label="Checklist view mode">
                  <button
                    type="button"
                    className={ownerInbox ? "btn sm primary" : "btn sm"}
                    onClick={() => {
                      setOwnerInbox(true);
                      setSelectedEmpId("");
                    }}
                  >
                    My items
                  </button>
                  {canManage || needEmpLookup ? (
                    <button
                      type="button"
                      className={!ownerInbox ? "btn sm primary" : "btn sm"}
                      onClick={() => setOwnerInbox(false)}
                    >
                      By employee
                    </button>
                  ) : null}
                </div>
                {ownerInbox ? (
                  <p className="muted small" style={{ margin: 0 }}>
                    Items you own (including HR-pool steps if you hold the HR role), plus checklists where you are the hire/exit employee.
                  </p>
                ) : (
                  <div className="stack" style={{ gap: 8 }}>
                    <input
                      type="search"
                      placeholder="Search name or code…"
                      value={empQuery}
                      onChange={(e) => setEmpQuery(e.target.value)}
                      aria-label="Search employees"
                    />
                    <select
                      value={selectedEmpId}
                      onChange={(e) => setSelectedEmpId(e.target.value)}
                      aria-label="Select employee"
                    >
                      <option value="">Select an employee…</option>
                      {selectedEmpId && !empOptions.some((e) => String(e.id) === selectedEmpId) ? (
                        <option value={selectedEmpId}>{selectedEmpLabel}</option>
                      ) : null}
                      {empOptions.map((emp) => (
                        <option key={emp.id} value={emp.id}>
                          {emp.fullName ?? `#${emp.id}`}
                          {emp.employeeCode ? ` · ${emp.employeeCode}` : ""}
                        </option>
                      ))}
                    </select>
                  </div>
                )}
              </div>
              <div className="row" style={{ gap: 6 }} role="group" aria-label="Checklist type filter">
                {(
                  [
                    ["ALL", "All"],
                    ["ONBOARDING", "Onboarding"],
                    ["OFFBOARDING", "Offboarding"],
                    ["CUSTOM", "Custom"],
                  ] as const
                ).map(([id, label]) => (
                  <button
                    key={id}
                    type="button"
                    className={typeFilter === id ? "btn sm primary" : "btn sm"}
                    onClick={() => setTypeFilter(id)}
                    disabled={!assignedEnabled}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>
            {ownerInbox ? (
              <p className="muted small" style={{ margin: 0 }}>
                Showing your owned / subject checklists
                {typeFilter !== "ALL" ? ` · ${typeFilter.toLowerCase()}` : ""}.
              </p>
            ) : selectedEmpId ? (
              <p className="muted small" style={{ margin: 0 }}>
                Showing checklists for <b>{selectedEmpLabel}</b>
                {typeFilter !== "ALL" ? ` · ${typeFilter.toLowerCase()}` : ""}.
              </p>
            ) : (
              <p className="muted small" style={{ margin: 0 }}>
                Choose an employee, or switch to My items to see what you own.
              </p>
            )}
          </section>

          {!assignedEnabled ? (
            <div className="card empty">Select an employee above to view their checklists.</div>
          ) : null}
          {assignedEnabled && assignedQ.isLoading ? <p className="muted">Loading checklists…</p> : null}
          {assignedEnabled && !assignedQ.isLoading && assigned.length === 0 ? (
            <div className="card empty">
              {ownerInbox
                ? "Nothing assigned to you yet."
                : canManage
                  ? "No checklists for this employee yet. Use Assign, or attach onboarding on hire / offboarding on exit."
                  : "No checklists for this employee."}
            </div>
          ) : null}

          <div className="grid g2" style={{ alignItems: "start" }}>
            {assigned.map((c) => {
              const its = (c.items as Rec[]) ?? [];
              const done = Number(c.completedItems ?? its.filter((i) => i.completed).length);
              const total = Number(c.totalItems ?? its.length);
              const pct = total ? Math.round((done / total) * 100) : 0;
              const subject =
                str(c.employeeName) ||
                empName.get(String(c.employeeId)) ||
                (c.employeeId != null ? `#${c.employeeId}` : "");
              const baseLabel =
                String(c.type).toUpperCase() === "OFFBOARDING"
                  ? "exit"
                  : String(c.type).toUpperCase() === "CUSTOM"
                    ? "base"
                    : "start";
              return (
                <section key={String(c.id)} className="card stack">
                  <div className="card-head" style={{ marginBottom: 0 }}>
                    <div>
                      <h2 style={{ margin: 0 }}>{str(c.name)}</h2>
                      <p className="muted small" style={{ margin: "4px 0 0" }}>
                        {subject}
                        {c.employeeCode ? ` · ${str(c.employeeCode)}` : ""}
                        {c.baseDate ? ` · ${baseLabel} ${fmtDate(c.baseDate)}` : ""}
                      </p>
                      <p className="muted small" style={{ margin: "2px 0 0" }}>
                        {done} of {total} done
                      </p>
                    </div>
                    <div className="row" style={{ gap: 6 }}>
                      <StatusPill status={c.type} />
                      <StatusPill status={c.status} />
                    </div>
                  </div>
                  <div style={{ height: 6, background: "var(--sunk)", borderRadius: 4, overflow: "hidden" }}>
                    <div style={{ width: `${pct}%`, height: "100%", background: "var(--accent)" }} />
                  </div>
                  <ul className="stack" style={{ listStyle: "none", padding: 0, margin: 0, gap: 10 }}>
                    {its.map((item, idx) => {
                      const tickable = canTickItem(item);
                      const owner = itemOwnerLabel(item);
                      const mine = isMyItem(item);
                      const ot = String(item.ownerType ?? "").toUpperCase();
                      return (
                        <li key={idx}>
                          <label
                            className="row"
                            style={{
                              flexWrap: "nowrap",
                              alignItems: "flex-start",
                              opacity: tickable || item.completed ? 1 : 0.85,
                              outline: mine && !item.completed ? "1px solid var(--border)" : undefined,
                              borderRadius: 6,
                              padding: mine ? "4px 6px" : undefined,
                            }}
                          >
                            <input
                              type="checkbox"
                              checked={Boolean(item.completed)}
                              disabled={toggle.isPending || !tickable}
                              onChange={(e) =>
                                toggle.mutate({
                                  checklistId: String(c.id),
                                  itemIndex: idx,
                                  completed: e.target.checked,
                                })
                              }
                              style={{ marginTop: 4 }}
                              title={tickable ? undefined : "Only the owner or HR can tick this item"}
                            />
                            <span style={{ flex: 1 }}>
                              <span
                                style={
                                  item.completed
                                    ? { textDecoration: "line-through", color: "var(--muted)" }
                                    : undefined
                                }
                              >
                                {str(item.title)}
                                {item.required ? (
                                  <span className="pill warn" style={{ marginLeft: 8 }}>
                                    required
                                  </span>
                                ) : null}
                                {mine ? (
                                  <span className="pill" style={{ marginLeft: 8 }}>
                                    yours
                                  </span>
                                ) : null}
                              </span>
                              <span className="muted small" style={{ display: "block", marginTop: 2 }}>
                                Owner: {owner}
                                {ot && ot !== "HR" && ot !== "NAMED" ? ` (${ownerLabel(ot)})` : ""}
                                {item.dueDate ? ` · due ${fmtDate(item.dueDate)}` : ""}
                                {item.dueOffsetDays != null && c.baseDate
                                  ? ` (${offsetHint(Number(item.dueOffsetDays), str(c.type))})`
                                  : ""}
                              </span>
                              {item.completed && (item.completedByName || item.completedBy || item.completedAt) ? (
                                <span className="muted small" style={{ display: "block", marginTop: 2 }}>
                                  Done
                                  {item.completedByName || item.completedBy
                                    ? ` by ${str(item.completedByName || item.completedBy)}`
                                    : ""}
                                  {item.completedAt ? ` · ${fmtDateTime(item.completedAt)}` : ""}
                                </span>
                              ) : null}
                            </span>
                          </label>
                        </li>
                      );
                    })}
                  </ul>
                </section>
              );
            })}
          </div>
        </div>
      ) : null}

      <Modal
        open={tplOpen}
        onClose={() => setTplOpen(false)}
        title={editingTpl ? "Edit checklist template" : "New checklist template"}
        wide
      >
        <form className="stack" onSubmit={submitTemplate} noValidate>
          <div className="form-grid">
            <FormField label="Name" required error={errors.name} full>
              <input type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. New Hire Onboarding" />
            </FormField>
            <FormField label="Type" required error={errors.type}>
              <select value={type} onChange={(e) => setType(e.target.value as typeof type)}>
                <option value="ONBOARDING">Onboarding (offsets from start date)</option>
                <option value="OFFBOARDING">Offboarding (offsets from exit date)</option>
                <option value="CUSTOM">Custom</option>
              </select>
            </FormField>
          </div>
          <div className="stack" style={{ gap: 10 }}>
            <span className="label">Items</span>
            <p className="muted small" style={{ margin: 0 }}>
              Due offset is days from the {type === "OFFBOARDING" ? "exit" : "start"} date (0 = same day,
              negative = before). Owner is who must tick the item when the checklist is assigned.
            </p>
            {items.map((it, i) => (
              <div key={i} className="card stack" style={{ padding: 12, gap: 8 }}>
                <div className="row" style={{ flexWrap: "nowrap", gap: 8 }}>
                  <input
                    type="text"
                    placeholder={`Step ${i + 1}`}
                    value={it.title}
                    style={{ flex: 1 }}
                    onChange={(e) => setItems(items.map((x, j) => (j === i ? { ...x, title: e.target.value } : x)))}
                  />
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
                <div className="form-grid">
                  <FormField label="Owner type" hint="Who owns this step when assigned">
                    <select
                      value={it.ownerType}
                      onChange={(e) => {
                        const ownerType = e.target.value as TemplateItem["ownerType"];
                        setItems(
                          items.map((x, j) =>
                            j === i
                              ? {
                                  ...x,
                                  ownerType,
                                  ownerEmployeeId: ownerType === "NAMED" ? x.ownerEmployeeId : "",
                                }
                              : x,
                          ),
                        );
                      }}
                    >
                      {OWNER_TYPES.map((o) => (
                        <option key={o} value={o}>
                          {ownerLabel(o)}
                        </option>
                      ))}
                    </select>
                  </FormField>
                  {it.ownerType === "NAMED" ? (
                    <FormField
                      label="Owner employee"
                      required
                      error={errors.ownerEmployeeId}
                      hint="Pick the person who owns this step"
                    >
                      <select
                        value={it.ownerEmployeeId}
                        onChange={(e) =>
                          setItems(
                            items.map((x, j) => (j === i ? { ...x, ownerEmployeeId: e.target.value } : x)),
                          )
                        }
                      >
                        <option value="">Select employee…</option>
                        {(employees.data ?? []).map((emp) => (
                          <option key={emp.id} value={emp.id}>
                            {emp.fullName ?? `#${emp.id}`}
                            {emp.employeeCode ? ` · ${emp.employeeCode}` : ""}
                          </option>
                        ))}
                      </select>
                    </FormField>
                  ) : (
                    <FormField
                      label="Owner"
                      hint={
                        it.ownerType === "EMPLOYEE"
                          ? "Resolved to the hire / exit employee on assign"
                          : it.ownerType === "MANAGER"
                            ? "Resolved to that employee's manager on assign"
                            : "Resolved to the primary HR role holder (not Admin/SuperAdmin who assigns)"
                      }
                    >
                      <input type="text" disabled value={ownerLabel(it.ownerType)} />
                    </FormField>
                  )}
                  <FormField
                    label="Due offset (days)"
                    hint={offsetHint(it.dueOffsetDays, type)}
                    full
                  >
                    <input
                      type="number"
                      value={it.dueOffsetDays}
                      onChange={(e) =>
                        setItems(
                          items.map((x, j) =>
                            j === i ? { ...x, dueOffsetDays: Number(e.target.value || 0) } : x,
                          ),
                        )
                      }
                    />
                  </FormField>
                </div>
                <label className="row small" style={{ gap: 6 }}>
                  <input
                    type="checkbox"
                    checked={it.required}
                    onChange={(e) =>
                      setItems(items.map((x, j) => (j === i ? { ...x, required: e.target.checked } : x)))
                    }
                  />
                  Required
                </label>
              </div>
            ))}
            {errors.items ? <span className="err">{errors.items}</span> : null}
            <div>
              <button type="button" className="btn sm" onClick={() => setItems([...items, blankItem()])}>
                + Add item
              </button>
            </div>
          </div>
          {err ? <div className="notice bad">{err}</div> : null}
          <div className="modal-foot">
            <button type="button" className="btn" onClick={() => setTplOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={saveTemplate.isPending}>
              {saveTemplate.isPending ? "Saving…" : editingTpl ? "Save template" : "Create template"}
            </button>
          </div>
        </form>
      </Modal>

      <Modal open={assignOpen} onClose={() => setAssignOpen(false)} title="Assign checklist">
        <form className="stack" onSubmit={submitAssign} noValidate>
          <p className="small muted" style={{ margin: 0 }}>
            Creates a copy of the template for the employee. Each item gets a named owner and a due date =
            base date + the item&apos;s due offset (days).
          </p>
          <div className="form-grid">
            <FormField label="Employee" required error={assignErrors.employeeId}>
              <select
                value={assignForm.employeeId}
                onChange={(e) => setAssignForm({ ...assignForm, employeeId: e.target.value })}
              >
                <option value="">Select…</option>
                {(employees.data ?? []).map((emp) => (
                  <option key={emp.id} value={emp.id}>
                    {emp.fullName ?? `#${emp.id}`}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Type" required error={assignErrors.type}>
              <select
                value={assignForm.type}
                onChange={(e) =>
                  setAssignForm({
                    ...assignForm,
                    type: e.target.value as "ONBOARDING" | "OFFBOARDING" | "CUSTOM",
                    templateId: "",
                  })
                }
              >
                <option value="ONBOARDING">Onboarding</option>
                <option value="OFFBOARDING">Offboarding</option>
                <option value="CUSTOM">Custom</option>
              </select>
            </FormField>
            <FormField
              label="Template"
              required={assignForm.type === "CUSTOM"}
              error={assignErrors.templateId}
              hint={
                assignForm.type === "CUSTOM"
                  ? "Pick a custom template"
                  : "Leave blank to use the default for the type"
              }
            >
              <select
                value={assignForm.templateId}
                onChange={(e) => setAssignForm({ ...assignForm, templateId: e.target.value })}
              >
                <option value="">
                  {assignForm.type === "CUSTOM" ? "Select template…" : "Default"}
                </option>
                {filteredTemplates.map((t) => (
                  <option key={String(t.id)} value={String(t.id)}>
                    {str(t.name)}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField
              label={
                assignForm.type === "OFFBOARDING"
                  ? "Exit date"
                  : assignForm.type === "CUSTOM"
                    ? "Base date"
                    : "Start date"
              }
              required
              error={assignErrors.startOrExitDate}
              hint="Base date for due offsets (required)"
            >
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
