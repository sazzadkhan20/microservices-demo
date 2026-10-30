"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { ConfirmModal } from "@/components/ui/ConfirmModal";
import { DataTable } from "@/components/ui/DataTable";
import { FormField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { Tabs } from "@/components/ui/Tabs";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { useEmployeesLookup, useLeaveTypes, type Rec } from "@/lib/api/lookups";
import { fmtNumber, str } from "@/lib/format";

const rowSchema = z.object({ id: z.union([z.number(), z.string()]) }).passthrough();

const TABS = [
  { id: "categories", label: "Leave categories" },
  { id: "balances", label: "Adjust balances" },
  { id: "opening", label: "Opening balances" },
  { id: "yearend", label: "Year end" },
  { id: "policy", label: "Policy" },
];

const ATTACH = ["NEVER", "ALWAYS", "AFTER_DAYS"];
const CARRY_MODES = ["NONE", "ALL", "CAP"] as const;
type CarryMode = (typeof CARRY_MODES)[number];

function chainRoles(v: unknown): string {
  try {
    const arr = typeof v === "string" ? JSON.parse(v) : v;
    if (Array.isArray(arr)) {
      return arr
        .slice()
        .sort((a, b) => Number(a.step ?? 0) - Number(b.step ?? 0))
        .map((s) => s.role)
        .join(", ");
    }
  } catch {
    /* fall through */
  }
  return "";
}

const catSchema = z.object({
  code: z.string().trim().min(2, "Code is required").regex(/^[A-Za-z0-9_]+$/, "Letters, numbers and underscores"),
  name: z.string().trim().min(2, "Name is required"),
  quotaDays: z.coerce.number("Enter a number").min(0, "Must be ≥ 0"),
  maxPerRequest: z.coerce.number("Enter a number").int().min(0, "Must be ≥ 0"),
  noticeDays: z.coerce.number("Enter a number").int().min(0, "Must be ≥ 0"),
  negativeCap: z.string().refine((v) => v === "" || !Number.isNaN(Number(v)), "Must be a number"),
  attachmentMode: z.string(),
  attachmentAfterDays: z.coerce.number("Enter a number").int().min(0),
  approvalChain: z.string(),
  paid: z.boolean(),
  halfDayAllowed: z.boolean(),
  active: z.boolean(),
  // period & carry
  periodStart: z.string().optional(),
  periodEnd: z.string().optional(),
  carryMode: z.enum(["NONE", "ALL", "CAP"]).default("NONE"),
  carryCapDays: z.coerce.number().min(0).default(0),
  carryExpiryDate: z.string().optional(),
  proRata: z.boolean().default(false),
});

export default function LeaveSetupPage() {
  const [tab, setTab] = useState("categories");
  return (
    <div className="stack">
      <PageHeader title="Leave setup" subtitle="Categories, quotas, approval chains and manual balance adjustments." />
      <Tabs tabs={TABS} value={tab} onChange={setTab} />
      {tab === "categories" ? <Categories /> : null}
      {tab === "balances" ? <Balances /> : null}
      {tab === "opening" ? <OpeningBalances /> : null}
      {tab === "yearend" ? <YearEnd /> : null}
      {tab === "policy" ? <Policy /> : null}
    </div>
  );
}

function Categories() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canManage = can("leave", "manage");
  const blank = {
    code: "",
    name: "",
    quotaDays: "10",
    maxPerRequest: "0",
    noticeDays: "0",
    negativeCap: "",
    attachmentMode: "NEVER",
    attachmentAfterDays: "0",
    approvalChain: "MANAGER",
    paid: true,
    halfDayAllowed: true,
    active: true,
    // period & carry
    periodStart: "",
    periodEnd: "",
    carryMode: "NONE" as CarryMode,
    carryCapDays: "0",
    carryExpiryDate: "",
    proRata: false,
  };
  const [editing, setEditing] = useState<Rec | "new" | null>(null);
  const [form, setForm] = useState(blank);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");
  const [renewTargetId, setRenewTargetId] = useState<string | number | null>(null);

  function openFor(r: Rec | "new") {
    setErr("");
    setErrors({});
    setForm(
      r === "new"
        ? blank
        : {
            code: str(r.code, ""),
            name: str(r.name, ""),
            quotaDays: String(r.quotaDays ?? 0),
            maxPerRequest: String(r.maxPerRequest ?? 0),
            noticeDays: String(r.noticeDays ?? 0),
            negativeCap: r.negativeCap == null ? "" : String(r.negativeCap),
            attachmentMode: str(r.attachmentMode, "NEVER"),
            attachmentAfterDays: String(r.attachmentAfterDays ?? 0),
            approvalChain: chainRoles(r.approvalChain),
            paid: Boolean(r.paid),
            halfDayAllowed: Boolean(r.halfDayAllowed),
            active: r.active !== false,
            periodStart: str(r.periodStart, ""),
            periodEnd: str(r.periodEnd, ""),
            carryMode: (str(r.carryMode, "NONE") as CarryMode) || "NONE",
            carryCapDays: String(r.carryCapDays ?? 0),
            carryExpiryDate: str(r.carryExpiryDate, ""),
            proRata: Boolean(r.proRata),
          },
    );
    setEditing(r);
  }

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      editing && editing !== "new"
        ? apiFetch(api.hrms(`leave-categories/${editing.id}`), { method: "PUT", body })
        : apiFetch(api.hrms("leave-categories"), { method: "POST", body }),
    onSuccess: () => {
      toast.show(editing === "new" ? "Category created — available as a leave type to request" : "Category saved");
      setEditing(null);
      void qc.invalidateQueries({ queryKey: ["leave-categories"] });
      // Apply forms use leave-types (kept in sync with categories on the server).
      void qc.invalidateQueries({ queryKey: ["leave-types"] });
      void qc.invalidateQueries({ queryKey: ["lookup", "leave-types"] });
    },
    onError: (e: Error) => setErr(e.message),
  });

  const renew = useMutation({
    mutationFn: (id: string | number) => {
      const nextYear = new Date().getFullYear() + 1;
      return apiFetch(api.hrms(`leave-categories/${id}/renew`), {
        method: "POST",
        body: {
          periodStart: `${nextYear}-01-01`,
          periodEnd: `${nextYear}-12-31`,
        },
      });
    },
    onSuccess: () => {
      toast.show("Category renewed for next year — new leave type is available to request");
      setRenewTargetId(null);
      void qc.invalidateQueries({ queryKey: ["leave-categories"] });
      void qc.invalidateQueries({ queryKey: ["leave-types"] });
      void qc.invalidateQueries({ queryKey: ["lookup", "leave-types"] });
    },
    onError: (e: Error) => {
      toast.error(e);
      setRenewTargetId(null);
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = catSchema.safeParse(form);
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setErrors(next);
    }
    setErrors({});
    setErr("");
    const v = parsed.data;
    const roles = v.approvalChain
      .split(/[,>→]/)
      .map((r) => r.trim().toUpperCase())
      .filter(Boolean);
    save.mutate({
      code: v.code.toUpperCase(),
      name: v.name,
      quotaDays: v.quotaDays,
      maxPerRequest: v.maxPerRequest,
      noticeDays: v.noticeDays,
      negativeCap: v.negativeCap === "" ? undefined : Number(v.negativeCap),
      attachmentMode: v.attachmentMode,
      attachmentAfterDays: v.attachmentAfterDays,
      approvalChain: JSON.stringify(roles.map((role, i) => ({ role, step: i + 1 }))),
      paid: v.paid,
      halfDayAllowed: v.halfDayAllowed,
      active: v.active,
      periodStart: v.periodStart || undefined,
      periodEnd: v.periodEnd || undefined,
      carryMode: v.carryMode,
      carryCapDays: v.carryMode === "CAP" ? v.carryCapDays : undefined,
      carryExpiryDate: v.carryExpiryDate || undefined,
      proRata: v.proRata,
    });
  }

  return (
    <section className="card stack">
      <div className="card-head" style={{ marginBottom: 0 }}>
        <h2>Leave categories</h2>
        {canManage ? (
          <button type="button" className="btn primary" onClick={() => openFor("new")}>
            + New category
          </button>
        ) : null}
      </div>
      <DataTable<Rec>
        queryKey="leave-categories"
        path={api.hrms("leave-categories")}
        itemSchema={rowSchema as never}
        emptyText="No categories"
        rowKey={(r) => String(r.id)}
        onRowClick={canManage ? (r) => openFor(r) : undefined}
        columns={[
          { key: "code", header: "Code", sortable: true, className: "mono" },
          { key: "name", header: "Name", sortable: true, render: (r) => <b>{str(r.name)}</b> },
          { key: "quotaDays", header: "Quota", className: "n", render: (r) => `${fmtNumber(r.quotaDays)} d` },
          { key: "maxPerRequest", header: "Max / request", className: "n", render: (r) => (Number(r.maxPerRequest) ? `${r.maxPerRequest} d` : "—") },
          { key: "noticeDays", header: "Notice", className: "n", render: (r) => (Number(r.noticeDays) ? `${r.noticeDays} d` : "—") },
          { key: "paid", header: "Paid", render: (r) => <span className={`pill ${r.paid ? "good" : "warn"}`}>{r.paid ? "Paid" : "Unpaid"}</span> },
          { key: "approvalChain", header: "Approval chain", render: (r) => chainRoles(r.approvalChain) || "Auto" },
          { key: "active", header: "State", render: (r) => <span className={`pill ${r.active ? "good" : ""}`}>{r.active ? "Active" : "Inactive"}</span> },
          {
            key: "renew_action",
            header: "",
            render: (r) =>
              canManage ? (
                <button
                  type="button"
                  className="btn sm"
                  disabled={renew.isPending}
                  onClick={(e) => {
                    e.stopPropagation();
                    setRenewTargetId(r.id as string | number);
                  }}
                >
                  Renew
                </button>
              ) : null,
          },
        ]}
      />
      <ConfirmModal
        open={renewTargetId !== null}
        title="Renew leave category?"
        confirmLabel="Renew"
        busy={renew.isPending}
        onCancel={() => setRenewTargetId(null)}
        onConfirm={() => renewTargetId !== null && renew.mutate(renewTargetId)}
      >
        This will set the period to next calendar year ({new Date().getFullYear() + 1}) for this category.
      </ConfirmModal>
      <Modal open={!!editing} onClose={() => setEditing(null)} wide title={editing === "new" ? "New leave category" : "Edit leave category"}>
        <form className="stack" onSubmit={submit} noValidate>
          <div className="form-grid">
            <FormField label="Code" required error={errors.code}>
              <input type="text" value={form.code} disabled={editing !== "new"} onChange={(e) => setForm({ ...form, code: e.target.value })} />
            </FormField>
            <FormField label="Name" required error={errors.name}>
              <input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </FormField>
            <FormField label="Yearly quota (days)" error={errors.quotaDays}>
              <input type="number" min={0} step="0.5" value={form.quotaDays} onChange={(e) => setForm({ ...form, quotaDays: e.target.value })} />
            </FormField>
            <FormField label="Max days per request" error={errors.maxPerRequest} hint="0 = no limit">
              <input type="number" min={0} value={form.maxPerRequest} onChange={(e) => setForm({ ...form, maxPerRequest: e.target.value })} />
            </FormField>
            <FormField label="Notice (days)" error={errors.noticeDays}>
              <input type="number" min={0} value={form.noticeDays} onChange={(e) => setForm({ ...form, noticeDays: e.target.value })} />
            </FormField>
            <FormField label="Negative balance cap" error={errors.negativeCap} hint="e.g. -2 allows going 2 days overdrawn">
              <input type="number" step="0.5" value={form.negativeCap} onChange={(e) => setForm({ ...form, negativeCap: e.target.value })} />
            </FormField>
            <FormField label="Attachment required">
              <select value={form.attachmentMode} onChange={(e) => setForm({ ...form, attachmentMode: e.target.value })}>
                {[...new Set([form.attachmentMode, ...ATTACH])].map((m) => (
                  <option key={m}>{m}</option>
                ))}
              </select>
            </FormField>
            <FormField label="…after (days)" error={errors.attachmentAfterDays}>
              <input type="number" min={0} value={form.attachmentAfterDays} onChange={(e) => setForm({ ...form, attachmentAfterDays: e.target.value })} />
            </FormField>
            <FormField label="Approval chain" full hint="Roles in order, comma-separated. Example: MANAGER, HR. Empty = auto-approved.">
              <input type="text" value={form.approvalChain} onChange={(e) => setForm({ ...form, approvalChain: e.target.value })} />
            </FormField>
            <FormField label="Period start" hint="Leave accrual period start date">
              <input type="date" value={form.periodStart} onChange={(e) => setForm({ ...form, periodStart: e.target.value })} />
            </FormField>
            <FormField label="Period end" hint="Leave accrual period end date">
              <input type="date" value={form.periodEnd} onChange={(e) => setForm({ ...form, periodEnd: e.target.value })} />
            </FormField>
            <FormField label="Carry mode" hint="How unused days carry over">
              <select
                value={form.carryMode}
                onChange={(e) => setForm({ ...form, carryMode: e.target.value as CarryMode })}
              >
                <option value="NONE">None — no carry-over</option>
                <option value="ALL">All — carry all unused days</option>
                <option value="CAP">Cap — carry up to a limit</option>
              </select>
            </FormField>
            {form.carryMode === "CAP" ? (
              <FormField label="Carry cap (days)" error={errors.carryCapDays}>
                <input
                  type="number"
                  min={0}
                  step="0.5"
                  value={form.carryCapDays}
                  onChange={(e) => setForm({ ...form, carryCapDays: e.target.value })}
                />
              </FormField>
            ) : null}
            <FormField label="Carry expiry date" hint="Date by which carried days must be used">
              <input
                type="date"
                value={form.carryExpiryDate}
                onChange={(e) => setForm({ ...form, carryExpiryDate: e.target.value })}
              />
            </FormField>
            <div className="checks full">
              <label>
                <input type="checkbox" checked={form.paid} onChange={(e) => setForm({ ...form, paid: e.target.checked })} /> Paid
              </label>
              <label>
                <input type="checkbox" checked={form.halfDayAllowed} onChange={(e) => setForm({ ...form, halfDayAllowed: e.target.checked })} /> Half days allowed
              </label>
              <label>
                <input type="checkbox" checked={form.proRata} onChange={(e) => setForm({ ...form, proRata: e.target.checked })} /> Pro-rata accrual
              </label>
              <label>
                <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Active
              </label>
            </div>
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

const adjustSchema = z.object({
  employeeId: z.string().min(1, "Choose an employee"),
  leaveTypeCode: z.string().min(1, "Choose a leave type"),
  deltaDays: z.coerce.number("Enter a number").refine((n) => n !== 0, "Cannot be zero"),
  reason: z.string().trim().min(3, "Please give a reason"),
});

function Balances() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canManage = can("leave", "manage");
  const employees = useEmployeesLookup();
  const types = useLeaveTypes();
  const [employeeId, setEmployeeId] = useState("");
  const [form, setForm] = useState({ leaveTypeCode: "", deltaDays: "1", reason: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  const balances = useQuery({
    queryKey: ["employee-balances", employeeId],
    queryFn: ({ signal }) => apiFetch<Rec[]>(api.hrms(`leave-balances/employee/${employeeId}`), { signal }),
    enabled: !!employeeId,
  });

  const adjust = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.hrms("leave-balances/adjust"), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Balance adjusted");
      setForm((f) => ({ ...f, reason: "" }));
      setErr("");
      void qc.invalidateQueries({ queryKey: ["employee-balances"] });
      void qc.invalidateQueries({ queryKey: ["my-balances"] });
    },
    onError: (e: Error) => setErr(e.message),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = adjustSchema.safeParse({ employeeId, ...form, leaveTypeCode: form.leaveTypeCode || types.data?.[0]?.code || "" });
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setErrors(next);
    }
    setErrors({});
    adjust.mutate({
      employeeId: Number(parsed.data.employeeId),
      leaveTypeCode: parsed.data.leaveTypeCode,
      deltaDays: parsed.data.deltaDays,
      reason: parsed.data.reason,
    });
  }

  return (
    <div className="grid g2" style={{ alignItems: "start" }}>
      <section className="card stack">
        <h2>Adjust a balance</h2>
        {!canManage ? <p className="muted">You need leave:manage to adjust balances.</p> : null}
        <form className="stack" onSubmit={submit} noValidate>
          <FormField label="Employee" required error={errors.employeeId}>
            <select value={employeeId} onChange={(e) => setEmployeeId(e.target.value)}>
              <option value="">Select…</option>
              {(employees.data ?? []).map((m) => (
                <option key={m.id} value={m.id}>
                  {m.fullName}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Leave type" required error={errors.leaveTypeCode}>
            <select value={form.leaveTypeCode || types.data?.[0]?.code || ""} onChange={(e) => setForm({ ...form, leaveTypeCode: e.target.value })}>
              {(types.data ?? []).map((t) => (
                <option key={t.code} value={t.code}>
                  {t.name}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Days to add (use a minus to deduct)" required error={errors.deltaDays}>
            <input type="number" step="0.5" value={form.deltaDays} onChange={(e) => setForm({ ...form, deltaDays: e.target.value })} />
          </FormField>
          <FormField label="Reason" required error={errors.reason}>
            <input type="text" value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} />
          </FormField>
          {err ? <div className="notice bad">{err}</div> : null}
          <div>
            <button type="submit" className="btn primary" disabled={!canManage || adjust.isPending}>
              {adjust.isPending ? "Saving…" : "Apply adjustment"}
            </button>
          </div>
        </form>
      </section>
      <section className="card stack">
        <h2>Current balances</h2>
        {!employeeId ? <p className="muted">Choose an employee to see their balances.</p> : null}
        <ErrorNote error={balances.error} />
        <div className="stack" style={{ gap: 8 }}>
          {(balances.data ?? []).map((b) => (
            <div key={String(b.id)} className="row between">
              <span>
                {str(b.leaveTypeCode)} <span className="muted small">{str(b.year)}</span>
              </span>
              <b className="num">{fmtNumber(b.balanceDays)} d</b>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

const openingSchema = z.object({
  employeeId: z.string().min(1, "Choose an employee"),
  leaveTypeCode: z.string().min(1, "Choose a leave type"),
  deltaDays: z.coerce.number("Enter a number").positive("Opening grant must be positive"),
  year: z.coerce.number("Enter a year").int().min(2000).max(2100),
  reason: z.string().trim().min(3, "Please give a reason"),
});

function OpeningBalances() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canManage = can("leave", "manage");
  const employees = useEmployeesLookup();
  const types = useLeaveTypes();
  const thisYear = new Date().getFullYear();
  const [employeeId, setEmployeeId] = useState("");
  const [form, setForm] = useState({
    leaveTypeCode: "",
    deltaDays: "10",
    year: String(thisYear),
    reason: "Opening balance",
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  const balances = useQuery({
    queryKey: ["employee-balances", employeeId, form.year],
    queryFn: ({ signal }) =>
      apiFetch<Rec[]>(api.hrms(`leave-balances/employee/${employeeId}`), {
        query: { year: form.year },
        signal,
      }),
    enabled: !!employeeId,
  });

  const adjust = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.hrms("leave-balances/adjust"), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Opening balance recorded");
      setErr("");
      void qc.invalidateQueries({ queryKey: ["employee-balances"] });
      void qc.invalidateQueries({ queryKey: ["my-balances"] });
    },
    onError: (e: Error) => setErr(e.message),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = openingSchema.safeParse({
      employeeId,
      ...form,
      leaveTypeCode: form.leaveTypeCode || types.data?.[0]?.code || "",
    });
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setErrors(next);
    }
    setErrors({});
    adjust.mutate({
      employeeId: Number(parsed.data.employeeId),
      leaveTypeCode: parsed.data.leaveTypeCode,
      deltaDays: parsed.data.deltaDays,
      year: parsed.data.year,
      reason: parsed.data.reason,
    });
  }

  return (
    <div className="grid g2" style={{ alignItems: "start" }}>
      <section className="card stack">
        <h2>Opening balances</h2>
        <p className="muted small">Grant starting leave for a year. Positive days only.</p>
        {!canManage ? <p className="muted">You need leave:manage to set opening balances.</p> : null}
        <form className="stack" onSubmit={submit} noValidate>
          <FormField label="Employee" required error={errors.employeeId}>
            <select value={employeeId} onChange={(e) => setEmployeeId(e.target.value)}>
              <option value="">Select…</option>
              {(employees.data ?? []).map((m) => (
                <option key={m.id} value={m.id}>
                  {m.fullName}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Leave type" required error={errors.leaveTypeCode}>
            <select
              value={form.leaveTypeCode || types.data?.[0]?.code || ""}
              onChange={(e) => setForm({ ...form, leaveTypeCode: e.target.value })}
            >
              {(types.data ?? []).map((t) => (
                <option key={t.code} value={t.code}>
                  {t.name}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Year" required error={errors.year}>
            <input type="number" min={2000} max={2100} value={form.year} onChange={(e) => setForm({ ...form, year: e.target.value })} />
          </FormField>
          <FormField label="Days to grant" required error={errors.deltaDays}>
            <input type="number" min={0.5} step="0.5" value={form.deltaDays} onChange={(e) => setForm({ ...form, deltaDays: e.target.value })} />
          </FormField>
          <FormField label="Reason" required error={errors.reason}>
            <input type="text" value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} />
          </FormField>
          {err ? <div className="notice bad">{err}</div> : null}
          <div>
            <button type="submit" className="btn primary" disabled={!canManage || adjust.isPending}>
              {adjust.isPending ? "Saving…" : "Record opening balance"}
            </button>
          </div>
        </form>
      </section>
      <section className="card stack">
        <h2>Balances for {form.year || "year"}</h2>
        {!employeeId ? <p className="muted">Choose an employee to see their balances.</p> : null}
        <ErrorNote error={balances.error} />
        <div className="stack" style={{ gap: 8 }}>
          {(balances.data ?? []).map((b) => (
            <div key={String(b.id)} className="row between">
              <span>
                {str(b.leaveTypeCode)} <span className="muted small">{str(b.year)}</span>
              </span>
              <b className="num">{fmtNumber(b.balanceDays)} d</b>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

const yearEndSchema = z.object({
  leaveTypeCode: z.string().min(1, "Choose a leave type"),
  fromYear: z.coerce.number("Enter a year").int().min(2000).max(2100),
  toYear: z.coerce.number("Enter a year").int().min(2000).max(2100),
  carryMaxDays: z.coerce.number("Enter a number").min(0, "Must be ≥ 0"),
});

function YearEnd() {
  const { can } = useAuth();
  const toast = useToast();
  const canManage = can("leave", "manage");
  const types = useLeaveTypes();
  const thisYear = new Date().getFullYear();
  const [form, setForm] = useState({
    leaveTypeCode: "",
    fromYear: String(thisYear - 1),
    toYear: String(thisYear),
    carryMaxDays: "5",
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");
  const [preview, setPreview] = useState<Rec[] | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);

  function bodyFrom(parsed: z.infer<typeof yearEndSchema>) {
    return {
      leaveTypeCode: parsed.leaveTypeCode,
      fromYear: parsed.fromYear,
      toYear: parsed.toYear,
      carryMaxDays: parsed.carryMaxDays,
    };
  }

  function parseForm() {
    const parsed = yearEndSchema.safeParse({
      ...form,
      leaveTypeCode: form.leaveTypeCode || types.data?.[0]?.code || "",
    });
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      setErrors(next);
      return null;
    }
    setErrors({});
    return parsed.data;
  }

  const runPreview = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiFetch<Rec[]>(api.hrms("leave-balances/year-end/preview"), { method: "POST", body }),
    onSuccess: (data) => {
      setPreview(Array.isArray(data) ? data : []);
      setErr("");
      toast.show("Preview ready");
    },
    onError: (e: Error) => setErr(e.message),
  });

  const confirm = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiFetch<Rec>(api.hrms("leave-balances/year-end/confirm"), { method: "POST", body }),
    onSuccess: (data) => {
      toast.show(`Year-end applied · ${str(data.employeesUpdated, "0")} employees updated`);
      setConfirmOpen(false);
      setErr("");
      const parsed = parseForm();
      if (parsed) runPreview.mutate(bodyFrom(parsed));
    },
    onError: (e: Error) => {
      setConfirmOpen(false);
      setErr(e.message);
    },
  });

  function onPreview(e: FormEvent) {
    e.preventDefault();
    const parsed = parseForm();
    if (!parsed) return;
    runPreview.mutate(bodyFrom(parsed));
  }

  return (
    <div className="stack">
      <section className="card stack">
        <h2>Year-end carry-over</h2>
        <p className="muted small">Preview how many days carry into the next year, then confirm to post ledger entries.</p>
        {!canManage ? <p className="muted">You need leave:manage to run year-end.</p> : null}
        <form className="stack" onSubmit={onPreview} noValidate>
          <div className="form-grid">
            <FormField label="Leave type" required error={errors.leaveTypeCode}>
              <select
                value={form.leaveTypeCode || types.data?.[0]?.code || ""}
                onChange={(e) => setForm({ ...form, leaveTypeCode: e.target.value })}
              >
                {(types.data ?? []).map((t) => (
                  <option key={t.code} value={t.code}>
                    {t.name}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="From year" required error={errors.fromYear}>
              <input type="number" value={form.fromYear} onChange={(e) => setForm({ ...form, fromYear: e.target.value })} />
            </FormField>
            <FormField label="To year" required error={errors.toYear}>
              <input type="number" value={form.toYear} onChange={(e) => setForm({ ...form, toYear: e.target.value })} />
            </FormField>
            <FormField label="Max carry days" error={errors.carryMaxDays} hint="Default 5">
              <input type="number" min={0} step="0.5" value={form.carryMaxDays} onChange={(e) => setForm({ ...form, carryMaxDays: e.target.value })} />
            </FormField>
          </div>
          {err ? <div className="notice bad">{err}</div> : null}
        <div className="row">
            <button type="submit" className="btn" disabled={!canManage || runPreview.isPending}>
              {runPreview.isPending ? "Previewing…" : "Preview"}
            </button>
            <button
              type="button"
              className="btn primary"
              disabled={!canManage || !preview || preview.length === 0 || confirm.isPending}
              onClick={() => setConfirmOpen(true)}
            >
              Confirm
            </button>
        </div>
        </form>
      </section>

      {preview ? (
        <section className="card stack">
          <h2>Preview</h2>
          <div className="tbl-wrap">
            <table>
              <thead>
                <tr>
                  <th>Employee</th>
                  <th className="n">Current balance</th>
                  <th className="n">Carry days</th>
                </tr>
              </thead>
              <tbody>
                {preview.length === 0 ? (
                  <tr>
                    <td colSpan={3} className="empty">
                      No balances to carry for this selection
                    </td>
                  </tr>
                ) : (
                  preview.map((r, i) => (
                    <tr key={String(r.employeeId ?? i)}>
                      <td>{str(r.employeeName) || `#${r.employeeId}`}</td>
                      <td className="n">{fmtNumber(r.currentBalance)}</td>
                      <td className="n">
                        <b>{fmtNumber(r.carryDays)}</b>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
      </div>
        </section>
      ) : null}

      <ConfirmModal
        open={confirmOpen}
        title="Confirm year-end carry-over?"
        confirmLabel="Confirm"
        busy={confirm.isPending}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => {
          const parsed = parseForm();
          if (!parsed) return setConfirmOpen(false);
          confirm.mutate(bodyFrom(parsed));
        }}
      >
        Carry leave into {form.toYear} for {preview?.length ?? 0} employee(s)? This posts ledger entries and cannot be undone from this screen.
      </ConfirmModal>
    </div>
  );
}

function Policy() {
  const policy = useQuery({
    queryKey: ["leave-policy"],
    queryFn: ({ signal }) => apiFetch<Rec>(api.hrms("leave-policy"), { signal }),
  });
  const p = policy.data;
  const days = Array.isArray(p?.workingDays) ? (p.workingDays as string[]).join(", ") : "—";
  const chain = Array.isArray(p?.approvalChain)
    ? (p.approvalChain as Rec[]).map((s) => s.role).join(" → ")
    : "—";
  return (
    <section className="card stack">
      <h2>Tenant leave policy</h2>
      <p className="muted small">Read-only view of the policy the leave engine is using right now.</p>
      <ErrorNote error={policy.error} />
      {policy.isLoading ? <p className="muted">Loading…</p> : null}
      {p ? (
        <div className="kv">
          <div>
            <div className="label">Annual days</div>
            {str(p.annualDays)}
          </div>
          <div>
            <div className="label">Working days</div>
            {days}
          </div>
          <div>
            <div className="label">Notice mode</div>
            {str(p.noticeMode)}
          </div>
          <div>
            <div className="label">Default notice days</div>
            {str(p.defaultNoticeDays)}
          </div>
          <div>
            <div className="label">Coverage check</div>
            {p.requireCoverageCheck ? "Required" : "Off"} ({str(p.coverageMode)})
          </div>
          <div>
            <div className="label">Default approval chain</div>
            {chain}
          </div>
        </div>
      ) : null}
    </section>
  );
}
