"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { EmployeeForm } from "@/components/employees/EmployeeForm";
import { useAuth } from "@/components/auth/AuthProvider";
import { ConfirmModal } from "@/components/ui/ConfirmModal";
import { FormField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { DataTable } from "@/components/ui/DataTable";
import { EmptyState, ErrorNote, Loading, NoAccess } from "@/components/ui/QueryState";
import { AttCode, StatusPill } from "@/components/ui/StatusPill";
import { Tabs } from "@/components/ui/Tabs";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch, downloadFile } from "@/lib/api/client";
import { useDirectory } from "@/lib/api/directory";
import {
  byId,
  rowsOf,
  useDepartments,
  useDesignations,
  useLocations,
  useTeams,
  type Rec,
} from "@/lib/api/lookups";
import {
  fmtDate,
  fmtDateTime,
  fmtMoney,
  fmtNumber,
  fmtRange,
  fmtTime,
  humanize,
  initials,
  pick,
  str,
} from "@/lib/format";
import { auditRowSchema, type AuditRow } from "@/lib/schemas/admin";
import { employeeSchema, type Employee } from "@/lib/schemas/employee";
import { attendanceRowSchema, type AttendanceRow } from "@/lib/schemas/hrms";

const rowSchema = z.object({ id: z.union([z.number(), z.string()]) }).passthrough();

const salaryChangeSchema = z.object({
  effectiveDate: z.string().min(1, "Required"),
  amount: z.coerce.number("Enter an amount").positive("Must be > 0"),
  payType: z.enum(["MONTHLY", "HOURLY"]),
  currency: z.string().trim().min(1, "Required"),
  note: z.string().optional(),
});

const TABS = [
  { id: "profile", label: "Overview" },
  { id: "employment", label: "Employment" },
  { id: "salary", label: "Salary" },
  { id: "attendance", label: "Attendance" },
  { id: "leave", label: "Leave" },
  { id: "documents", label: "Documents" },
  { id: "notes", label: "Notes" },
  { id: "history", label: "History" },
];

const DOC_TYPES = ["Contract", "ID / Passport", "Certificate", "Offer letter", "Other"];

function KV({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <div className="label">{label}</div>
      <div>{children}</div>
    </div>
  );
}

export default function EmployeeDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const { me, can } = useAuth();
  const [tab, setTab] = useState("profile");

  const emp = useQuery({
    queryKey: ["employee", id],
    queryFn: ({ signal }) => apiFetch(api.hrms(`employees/${id}`), { schema: employeeSchema, signal }),
  });

  if (emp.isLoading) return <Loading />;
  if (emp.error) {
    return (
      <div className="stack">
        <PageHeader title="Employee" actions={<Link href="/employees" className="btn">← All employees</Link>} />
        <ErrorNote error={emp.error} />
      </div>
    );
  }
  const e = emp.data!;
  // Every employee has payroll:view (own payslips), so salary is limited to HR / payroll / the person themself.
  const showSalary = can("payroll", "run") || can("employees", "edit") || String(e.id) === String(me.employeeId);
  const canRun = can("payroll", "run");

  return (
    <div className="stack">
      <PageHeader
        title={e.fullName || "Employee"}
        subtitle={
          <>
            <span className="mono">{e.employeeCode}</span> · {e.jobTitle || "—"}
            {e.departmentName ? ` · ${e.departmentName}` : ""}
          </>
        }
        actions={
          <>
            <StatusPill status={e.status} />
            <Link href="/employees" className="btn">
              ← All employees
            </Link>
          </>
        }
      />
      <Tabs tabs={TABS} value={tab} onChange={setTab} />
      {tab === "profile" ? <ProfileTab emp={e} canEdit={can("employees", "edit")} /> : null}
      {tab === "employment" ? (
        <EmploymentTab emp={e} canEdit={can("employees", "edit")} showSalary={showSalary} />
      ) : null}
      {tab === "salary" ? <SalaryTab empId={id} emp={e} canRun={canRun} showSalary={showSalary} /> : null}
      {tab === "attendance" ? <AttendanceTab empId={id} /> : null}
      {tab === "leave" ? <LeaveTab empId={id} /> : null}
      {tab === "documents" ? <DocumentsTab empId={id} canEdit={can("employees", "edit")} /> : null}
      {tab === "notes" ? <NotesTab emp={e} /> : null}
      {tab === "history" ? <HistoryTab empId={id} /> : null}
    </div>
  );
}

type EmpProp = { emp: Employee; canEdit: boolean };

function ProfileTab({ emp, canEdit }: EmpProp) {
  const [editing, setEditing] = useState(false);
  return (
    <div className="card stack">
      <div className="card-head">
        <div className="row">
          <div
            style={{
              width: 44,
              height: 44,
              borderRadius: "50%",
              background: "var(--accent-soft)",
              color: "var(--accent)",
              display: "grid",
              placeItems: "center",
              fontWeight: 700,
            }}
          >
            {initials(emp.fullName)}
          </div>
          <div>
            <h2>{emp.fullName}</h2>
            <p className="muted small">{emp.preferredName ? `Goes by ${emp.preferredName}` : "—"}</p>
          </div>
        </div>
        {canEdit && !editing ? (
          <button type="button" className="btn" onClick={() => setEditing(true)}>
            Edit profile
          </button>
        ) : null}
      </div>
      {editing ? (
        <EmployeeForm
          mode="edit"
          sections="profile"
          employee={emp}
          onSaved={() => setEditing(false)}
          onCancel={() => setEditing(false)}
        />
      ) : (
        <div className="kv">
          <KV label="Work email">{str(emp.email)}</KV>
          <KV label="Phone">{str(emp.phone)}</KV>
          <KV label="Personal email">{str(emp.personalEmail)}</KV>
          <KV label="Preferred name">{str(emp.preferredName)}</KV>
          <KV label="Employee code">
            <span className="mono">{str(emp.employeeCode)}</span>
          </KV>
          <KV label="Roles">
            <div className="row">
              {(emp.roles ?? []).length === 0 ? <span className="pill warn">No role</span> : null}
              {(emp.roles ?? []).map((r) => (
                <span key={r} className="pill acc">
                  {r}
                </span>
              ))}
            </div>
          </KV>
        </div>
      )}
    </div>
  );
}

function EmploymentTab({ emp, canEdit, showSalary }: EmpProp & { showSalary: boolean }) {
  const [editing, setEditing] = useState(false);
  const teams = useTeams();
  const locations = useLocations();
  const departments = useDepartments();
  const designations = useDesignations();
  const teamName = useMemo(() => byId(teams.data, (t) => t.name), [teams.data]);
  const locName = useMemo(() => byId(locations.data, (l) => l.name), [locations.data]);
  const deptName = useMemo(() => byId(departments.data, (d) => d.name), [departments.data]);
  const desigName = useMemo(() => byId(designations.data, (d) => d.name), [designations.data]);
  const dir = useDirectory();

  const reporting = useQuery({
    queryKey: ["reporting-line", emp.id],
    queryFn: ({ signal }) => apiFetch<Rec[]>(api.hrms(`employees/${emp.id}/reporting-line`), { signal }),
  });
  const line = rowsOf<Rec>(reporting.data);

  return (
    <div className="stack">
      <div className="card stack">
        <div className="card-head">
          <h2>Employment</h2>
          {canEdit && !editing ? (
            <button type="button" className="btn" onClick={() => setEditing(true)}>
              Edit employment
            </button>
          ) : null}
        </div>
        {editing ? (
          <EmployeeForm
            mode="edit"
            sections="employment"
            employee={emp}
            onSaved={() => setEditing(false)}
            onCancel={() => setEditing(false)}
          />
        ) : (
          <div className="kv">
            <KV label="Job title">{str(emp.jobTitle)}</KV>
            <KV label="Designation">
              {emp.designationName ?? (emp.designationId != null ? desigName.get(String(emp.designationId)) : null) ?? "—"}
            </KV>
            <KV label="Department">
              {emp.departmentName ?? (emp.departmentId != null ? deptName.get(String(emp.departmentId)) : null) ?? "—"}
            </KV>
            <KV label="Team">{emp.teamId != null ? (teamName.get(String(emp.teamId)) ?? "—") : "—"}</KV>
            <KV label="Manager">
              {emp.managerId != null ? (
                <Link href={`/employees/${emp.managerId}`}>{emp.managerName || `#${emp.managerId}`}</Link>
              ) : (
                "—"
              )}
            </KV>
            <KV label="Location">{emp.locationId != null ? (locName.get(String(emp.locationId)) ?? "—") : "—"}</KV>
            <KV label="Employment type">{humanize(emp.employmentType)}</KV>
            <KV label="Work pattern">{humanize(emp.workPattern)}</KV>
            <KV label="Start date">{fmtDate(emp.startDate)}</KV>
            <KV label="Probation end">{fmtDate(emp.probationEnd)}</KV>
            <KV label="Contract end">{fmtDate(emp.contractEnd)}</KV>
            <KV label="Pay currency">{str(emp.payCurrency)}</KV>
            {showSalary ? (
              <>
                <KV label="Base salary">{fmtMoney(emp.baseSalary, emp.payCurrency)}</KV>
                <KV label="Hourly rate">{fmtMoney(emp.hourlyRate, emp.payCurrency)}</KV>
              </>
            ) : null}
            <KV label="Status">
              <StatusPill status={emp.status} />
            </KV>
          </div>
        )}
      </div>

      <div className="card stack">
        <h2>Reporting line</h2>
        <ErrorNote error={reporting.error} />
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th>Manager</th>
                <th>From</th>
                <th>To</th>
                <th>Changed by</th>
              </tr>
            </thead>
            <tbody>
              {line.length === 0 ? (
                <tr>
                  <td colSpan={4} className="empty">
                    {reporting.isLoading ? "Loading…" : "No manager history"}
                  </td>
                </tr>
              ) : (
                line.map((r, i) => (
                  <tr key={String(r.id ?? i)}>
                    <td>
                      {r.managerId != null ? (
                        <Link href={`/employees/${r.managerId}`}>{str(r.managerName) || `#${r.managerId}`}</Link>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className="mono small">{fmtDate(r.fromDate)}</td>
                    <td className="mono small">{r.toDate == null ? "Current" : fmtDate(r.toDate)}</td>
                    <td>{r.changedBy != null ? dir.nameOf(r.changedBy) : "—"}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function SalaryTab({
  empId,
  emp,
  canRun,
  showSalary,
}: {
  empId: string;
  emp: Employee;
  canRun: boolean;
  showSalary: boolean;
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({
    effectiveDate: new Date().toISOString().slice(0, 10),
    amount: "",
    payType: "MONTHLY" as "MONTHLY" | "HOURLY",
    currency: emp.payCurrency || "BDT",
    note: "",
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  const history = useQuery({
    queryKey: ["salary-history", empId],
    queryFn: ({ signal }) => apiFetch<Rec[]>(api.payroll(`salary-history/employee/${empId}`), { signal }),
    enabled: showSalary,
  });
  const rows = rowsOf<Rec>(history.data);

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.payroll("salary-history"), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Salary change recorded");
      setOpen(false);
      setErr("");
      void qc.invalidateQueries({ queryKey: ["salary-history", empId] });
      void qc.invalidateQueries({ queryKey: ["employee", empId] });
    },
    onError: (e: Error) => setErr(e.message),
  });

  if (!showSalary) return <NoAccess what="salary information" />;

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = salaryChangeSchema.safeParse(form);
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setErrors(next);
    }
    setErrors({});
    save.mutate({
      employeeId: Number(empId),
      effectiveDate: parsed.data.effectiveDate,
      amount: parsed.data.amount,
      payType: parsed.data.payType,
      currency: parsed.data.currency,
      note: parsed.data.note || undefined,
    });
  }

  return (
    <div className="stack">
      <div className="card stack">
        <div className="card-head" style={{ marginBottom: 0 }}>
          <h2>Salary history</h2>
          {canRun ? (
            <button
              type="button"
              className="btn primary"
              onClick={() => {
                setErr("");
                setErrors({});
                setForm({
                  effectiveDate: new Date().toISOString().slice(0, 10),
                  amount: "",
                  payType: "MONTHLY",
                  currency: emp.payCurrency || "BDT",
                  note: "",
                });
                setOpen(true);
              }}
            >
              Record salary change
            </button>
          ) : null}
        </div>
        <ErrorNote error={history.error} />
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th>Effective</th>
                <th className="n">Amount</th>
                <th>Type</th>
                <th>Currency</th>
                <th>Note</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={5} className="empty">
                    {history.isLoading ? "Loading…" : "No salary changes recorded"}
                  </td>
                </tr>
              ) : (
                rows.map((r) => (
                  <tr key={String(r.id)}>
                    <td className="mono">{fmtDate(r.effectiveDate)}</td>
                    <td className="n">{fmtMoney(r.amount, str(r.currency, emp.payCurrency || undefined))}</td>
                    <td>{humanize(r.payType)}</td>
                    <td className="mono">{str(r.currency)}</td>
                    <td className="muted">{str(r.note)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card stack">
        <h2>Payslips</h2>
        <DataTable<Rec>
          queryKey={`emp-payslips-${empId}`}
          path={api.payroll("payslips")}
          itemSchema={rowSchema as never}
          search={false}
          filters={{ employeeId: empId }}
          emptyText="No payslips for this employee"
          rowKey={(r) => String(r.id)}
          columns={[
            { key: "periodLabel", header: "Period", className: "mono" },
            {
              key: "hours",
              header: "Hours (reg / OT / wknd)",
              className: "n",
              render: (r) => `${fmtNumber(r.regularHours)} / ${fmtNumber(r.overtimeHours)} / ${fmtNumber(r.weekendHours)}`,
            },
            { key: "grossAmount", header: "Gross", className: "n", render: (r) => fmtMoney(r.grossAmount) },
            { key: "netAmount", header: "Net", className: "n", render: (r) => <b>{fmtMoney(r.netAmount)}</b> },
            { key: "status", header: "Status", render: (r) => <StatusPill status={r.status} /> },
          ]}
        />
      </div>

      <Modal open={open} onClose={() => setOpen(false)} title="Record salary change">
        <form className="stack" onSubmit={submit} noValidate>
          <div className="form-grid">
            <FormField label="Effective date" required error={errors.effectiveDate}>
              <input
                type="date"
                value={form.effectiveDate}
                onChange={(e) => setForm({ ...form, effectiveDate: e.target.value })}
              />
            </FormField>
            <FormField label="Amount" required error={errors.amount}>
              <input
                type="number"
                min={0}
                step="0.01"
                value={form.amount}
                onChange={(e) => setForm({ ...form, amount: e.target.value })}
              />
            </FormField>
            <FormField label="Pay type" required error={errors.payType}>
              <select value={form.payType} onChange={(e) => setForm({ ...form, payType: e.target.value as "MONTHLY" | "HOURLY" })}>
                <option value="MONTHLY">Monthly</option>
                <option value="HOURLY">Hourly</option>
              </select>
            </FormField>
            <FormField label="Currency" required error={errors.currency}>
              <input type="text" value={form.currency} onChange={(e) => setForm({ ...form, currency: e.target.value })} />
            </FormField>
            <FormField label="Note" full>
              <input type="text" value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} />
            </FormField>
          </div>
          {err ? <div className="notice bad">{err}</div> : null}
          <div className="modal-foot">
            <button type="button" className="btn" onClick={() => setOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={save.isPending}>
              {save.isPending ? "Saving…" : "Save"}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

function HistoryTab({ empId }: { empId: string }) {
  const { can } = useAuth();
  if (!can("settings", "manage")) {
    return (
      <div className="card stack">
        <h2>History</h2>
        <p className="muted">Audit history requires settings:manage. Ask an admin if you need to review changes for this employee.</p>
      </div>
    );
  }
  return (
    <div className="card" style={{ padding: 0 }}>
      <DataTable<AuditRow>
        queryKey={`emp-audit-${empId}`}
        path={api.hrms("audit")}
        itemSchema={auditRowSchema}
        search={false}
        q={String(empId)}
        emptyText="No audit entries mentioning this employee id"
        rowKey={(r) => String(r.id)}
        columns={[
          { key: "at", header: "When", className: "mono small", render: (r) => fmtDateTime(r.at) },
          { key: "action", header: "Action", render: (r) => humanize(r.action) },
          {
            key: "entity",
            header: "Record",
            render: (r) => (
              <>
                {r.entity}
                {r.entityId ? <span className="mono small muted"> #{r.entityId}</span> : null}
              </>
            ),
          },
          { key: "actorEmployeeId", header: "Who", render: (r) => (r.actorEmployeeId != null ? `#${r.actorEmployeeId}` : "—") },
        ]}
      />
    </div>
  );
}

function DocumentsTab({ empId, canEdit }: { empId: string; canEdit: boolean }) {
  const qc = useQueryClient();
  const toast = useToast();
  const fileRef = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [docType, setDocType] = useState(DOC_TYPES[0]);
  const [name, setName] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [err, setErr] = useState("");

  const docs = useQuery({
    queryKey: ["employee-docs", empId],
    queryFn: ({ signal }) => apiFetch(api.hrms(`employees/${empId}/documents`), { signal }),
  });
  const rows = rowsOf<Rec>(docs.data);

  const attach = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error("Choose a file to upload (PDF, JPG, PNG or DOCX, max 10 MB).");
      const fd = new FormData();
      fd.append("file", file);
      const uploaded = await apiFetch<{ id: number | string; originalName?: string }>("/api/files", {
        method: "POST",
        body: fd,
      });
      return apiFetch(api.hrms(`employees/${empId}/documents`), {
        method: "POST",
        body: {
          fileId: String(uploaded.id),
          documentType: docType,
          name: name.trim() || uploaded.originalName || docType,
        },
      });
    },
    onSuccess: () => {
      toast.show("Document attached");
      setOpen(false);
      setName("");
      setFile(null);
      setErr("");
      void qc.invalidateQueries({ queryKey: ["employee-docs", empId] });
    },
    onError: (e: Error) => setErr(e.message),
  });

  const [removeDoc, setRemoveDoc] = useState<{ id: string; name: string } | null>(null);
  const remove = useMutation({
    mutationFn: (fileId: string) => apiFetch(api.hrms(`employees/${empId}/documents/${fileId}`), { method: "DELETE" }),
    onSuccess: () => {
      toast.show("Document removed");
      setRemoveDoc(null);
      void qc.invalidateQueries({ queryKey: ["employee-docs", empId] });
    },
    onError: (e: Error) => toast.error(e),
  });

  return (
    <div className="card stack">
      <div className="card-head">
        <h2>Documents</h2>
        {canEdit ? (
          <button type="button" className="btn primary" onClick={() => setOpen(true)}>
            + Attach document
          </button>
        ) : null}
      </div>
      <ErrorNote error={docs.error} />
      <div className="tbl-wrap">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Type</th>
              <th>Uploaded</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={4} className="empty">
                  {docs.isLoading ? "Loading…" : "No documents attached"}
                </td>
              </tr>
            ) : (
              rows.map((d) => (
                <tr key={String(d.fileId)}>
                  <td>{str(d.name)}</td>
                  <td>
                    <span className="pill">{str(d.documentType ?? d.type)}</span>
                  </td>
                  <td className="muted small">{fmtDateTime(d.uploadedAt)}</td>
                  <td>
                    <div className="row" style={{ justifyContent: "flex-end" }}>
                      <button
                        type="button"
                        className="btn sm"
                        onClick={() =>
                          void downloadFile(api.files(`${d.fileId}/download`), undefined, String(d.name ?? "document")).catch(
                            (er) => toast.error(er),
                          )
                        }
                      >
                        Download
                      </button>
                      {canEdit ? (
                        <button
                          type="button"
                          className="btn sm danger"
                          disabled={remove.isPending}
                          onClick={() => setRemoveDoc({ id: String(d.fileId), name: str(d.name) })}
                        >
                          Remove
                        </button>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Attach document"
        subtitle="The file is uploaded to the file service, then linked to this employee."
        footer={
          <>
            <button type="button" className="btn" onClick={() => setOpen(false)}>
              Cancel
            </button>
            <button type="button" className="btn primary" disabled={attach.isPending} onClick={() => attach.mutate()}>
              {attach.isPending ? "Uploading…" : "Upload & attach"}
            </button>
          </>
        }
      >
        <div className="form-grid">
          <label className="field">
            <span>Type</span>
            <select value={docType} onChange={(e) => setDocType(e.target.value)}>
              {DOC_TYPES.map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Display name</span>
            <input type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="Defaults to file name" />
          </label>
          <label className="field full">
            <span>File</span>
            <input
              ref={fileRef}
              type="file"
              accept=".pdf,.jpg,.jpeg,.png,.docx"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </label>
        </div>
        {err ? <div className="notice bad">{err}</div> : null}
      </Modal>

      <ConfirmModal
        open={!!removeDoc}
        title="Remove document?"
        confirmLabel="Remove"
        danger
        busy={remove.isPending}
        onCancel={() => setRemoveDoc(null)}
        onConfirm={() => removeDoc && remove.mutate(removeDoc.id)}
      >
        “{removeDoc?.name}” will be unlinked from this employee.
      </ConfirmModal>
    </div>
  );
}

function LeaveTab({ empId }: { empId: string }) {
  const balances = useQuery({
    queryKey: ["employee-balances", empId],
    queryFn: ({ signal }) => apiFetch(api.hrms(`leave-balances/employee/${empId}`), { signal }),
  });
  const requests = useQuery({
    queryKey: ["employee-leave", empId],
    queryFn: ({ signal }) =>
      apiFetch(api.hrms(`leave/employee/${empId}`), { query: { page: 1, size: 50 }, signal }),
  });
  const bal = rowsOf<Rec>(balances.data);
  const reqs = rowsOf<Rec>(requests.data);

  return (
    <div className="stack">
      <div className="card stack">
        <h2>Balances</h2>
        <ErrorNote error={balances.error} />
        <div className="grid g4">
          {bal.length === 0 ? <p className="muted">{balances.isLoading ? "Loading…" : "No balances recorded"}</p> : null}
          {bal.map((b) => (
            <div key={String(b.id)} className="card" style={{ background: "var(--sunk)" }}>
              <div className="stat">
                <span className="s">{str(b.leaveTypeCode)} · {str(b.year)}</span>
                <span className="v">{fmtNumber(b.balanceDays)}</span>
                <span className="s">days left</span>
              </div>
            </div>
          ))}
        </div>
      </div>
      <div className="card stack">
        <h2>Requests</h2>
        <ErrorNote error={requests.error} />
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th>Type</th>
                <th>Dates</th>
                <th className="n">Days</th>
                <th>Status</th>
                <th>Reason</th>
              </tr>
            </thead>
            <tbody>
              {reqs.length === 0 ? (
                <tr>
                  <td colSpan={5} className="empty">
                    {requests.isLoading ? "Loading…" : "No leave requests"}
                  </td>
                </tr>
              ) : (
                reqs.map((r) => (
                  <tr key={String(r.id)}>
                    <td>{str(r.leaveTypeCode)}</td>
                    <td>{fmtRange(r.startDate, r.endDate)}</td>
                    <td className="n">{fmtNumber(r.workingDaysCounted)}</td>
                    <td>
                      <StatusPill status={r.status} />
                    </td>
                    <td className="muted">{str(r.reason)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function AttendanceTab({ empId }: { empId: string }) {
  const { can } = useAuth();
  if (!can("attendance", "view")) return <NoAccess what="attendance" />;
  return (
    <div className="card" style={{ padding: 0 }}>
      <DataTable<AttendanceRow>
        queryKey={`emp-att-${empId}`}
        path={api.hrms(`attendance/employee/${empId}`)}
        itemSchema={attendanceRowSchema}
        search={false}
        initialSize={10}
        pageSizes={[10, 20, 50]}
        emptyText="No attendance records yet"
        columns={[
          { key: "workDate", header: "Date", className: "mono", render: (r) => fmtDate(pick(r, "workDate", "date")) },
          { key: "checkIn", header: "In", render: (r) => fmtTime(pick(r, "checkIn", "checkInAt")) },
          { key: "checkOut", header: "Out", render: (r) => fmtTime(pick(r, "checkOut", "checkOutAt")) },
          {
            key: "status",
            header: "Status",
            render: (r) => <AttCode code={r.statusCode} label={r.status ?? r.statusLabel} />,
          },
          { key: "workingHours", header: "Worked", className: "n", render: (r) => str(r.workingHours) },
          { key: "location", header: "Location", render: (r) => str(r.location) },
        ]}
      />
    </div>
  );
}

function NotesTab({ emp }: { emp: Employee }) {
  const custom = ((emp as Record<string, unknown>).customFields ?? {}) as Record<string, unknown>;
  const entries = Object.entries(custom).filter(([k, v]) => k !== "documents" && v !== null && v !== "");
  return (
    <div className="card stack">
      <h2>Notes &amp; custom fields</h2>
      {entries.length === 0 ? (
        <EmptyState>No notes or custom fields on this record.</EmptyState>
      ) : (
        <div className="kv">
          {entries.map(([k, v]) => (
            <KV key={k} label={k.replace(/([A-Z])/g, " $1").replace(/[_-]/g, " ")}>
              {typeof v === "object" ? <span className="mono small">{JSON.stringify(v)}</span> : String(v)}
            </KV>
          ))}
        </div>
      )}
      <p className="note">
        Free-text HR notes are not persisted by the HRMS API yet; this tab lists the tenant&apos;s custom fields for
        this record.
      </p>
    </div>
  );
}
