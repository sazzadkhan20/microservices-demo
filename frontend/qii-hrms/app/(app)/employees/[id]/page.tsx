"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { AssignEmployeeRolesModal } from "@/components/employees/AssignEmployeeRolesModal";
import { EmployeeForm } from "@/components/employees/EmployeeForm";
import { EmployeePhoto } from "@/components/employees/EmployeePhoto";
import { PersonalContactForm } from "@/components/employees/PersonalContactForm";
import { ScheduleEditorModal, parseDays } from "@/components/schedules/ScheduleEditorModal";
import { useAuth } from "@/components/auth/AuthProvider";
import { SalaryPanel, useSalary } from "@/components/payroll/SalaryPanel";
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
  fmtLeaveDays,
  fmtNumber,
  fmtRange,
  fmtTime,
  humanize,
  initials,
  pick,
  str,
} from "@/lib/format";
import {
  DocumentScanPanel,
  ScanStatusBadge,
  isScanDownloadable,
  isScanPending,
  isScanRejected,
  normalizeScanStatus,
} from "@/components/files/ScanStatusBadge";
import { DOC_TYPES, employeeSchema, type Employee } from "@/lib/schemas/employee";
import { DOCUMENT_ACCEPT, validateDocumentFile } from "@/lib/uploadValidation";
import { attendanceRowSchema, type AttendanceRow } from "@/lib/schemas/hrms";

const rowSchema = z.object({ id: z.union([z.number(), z.string()]) }).passthrough();

const TABS = [
  { id: "profile", label: "Overview" },
  { id: "employment", label: "Employment" },
  { id: "salary", label: "Salary" },
  { id: "attendance", label: "Attendance" },
  { id: "worklog", label: "Work log" },
  { id: "leave", label: "Leave" },
  { id: "documents", label: "Documents" },
  { id: "notes", label: "Notes" },
  { id: "history", label: "History" },
];

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
  const [rolesOpen, setRolesOpen] = useState(false);

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
  const isSelf = String(e.id) === String(me.employeeId);
  // Align with backend compensationAccess: self, salary:view/edit (HR), or payroll:view/run.
  // FR-SAL-03 / NFR-05 — own pay with salary:view; anyone else's needs salary:edit or payroll:run (server-enforced).
  const showSalary = isSelf ? can("salary", "view") : can("salary", "edit") || can("payroll", "run");
  const canEditSalary = can("salary", "edit");
  const canAssignRoles = can("roles", "assign") && !isSelf;
  const tabs = showSalary ? TABS : TABS.filter((t) => t.id !== "salary");
  const activeTab = tab === "salary" && !showSalary ? "profile" : tab;
  const photoFileId =
    e.photoFileId ??
    (e as Employee & { customFields?: Record<string, unknown> }).customFields?.photoFileId;

  return (
    <div className="stack">
      <PageHeader
        title={
          <span className="row" style={{ gap: 12, alignItems: "center" }}>
            <EmployeePhoto photoFileId={photoFileId} fullName={e.fullName} size={48} />
            <span>{e.fullName || "Employee"}</span>
          </span>
        }
        subtitle={
          <>
            <span className="mono">{e.employeeCode}</span> · {e.jobTitle || "—"}
            {e.departmentName ? ` · ${e.departmentName}` : ""}
          </>
        }
        actions={
          <>
            <StatusPill status={e.status} />
            {canAssignRoles ? (
              <button type="button" className="btn" onClick={() => setRolesOpen(true)}>
                Roles
              </button>
            ) : null}
            <Link href="/employees" className="btn">
              ← All employees
            </Link>
          </>
        }
      />
      <Tabs tabs={tabs} value={activeTab} onChange={setTab} />
      {activeTab === "profile" ? (
        <ProfileTab
          emp={e}
          canEdit={can("employees", "edit")}
          isSelf={isSelf}
          canAssignRoles={canAssignRoles}
          onAssignRoles={() => setRolesOpen(true)}
        />
      ) : null}
      <AssignEmployeeRolesModal open={rolesOpen} employee={e} onClose={() => setRolesOpen(false)} />
      {activeTab === "employment" ? (
        <EmploymentTab emp={e} canEdit={can("employees", "edit")} showSalary={showSalary} />
      ) : null}
      {activeTab === "salary" && showSalary ? (
        <SalaryTab
          empId={id}
          emp={e}
          canEdit={canEditSalary}
        />
      ) : null}
      {activeTab === "attendance" ? <AttendanceTab empId={id} /> : null}
      {activeTab === "worklog" ? <WorkLogTab empId={id} /> : null}
      {activeTab === "leave" ? <LeaveTab empId={id} /> : null}
      {activeTab === "documents" ? <DocumentsTab empId={id} canEdit={can("employees", "edit")} /> : null}
      {activeTab === "notes" ? <NotesTab emp={e} /> : null}
      {activeTab === "history" ? <HistoryTab empId={id} /> : null}
    </div>
  );
}

type EmpProp = { emp: Employee; canEdit: boolean };

function ProfileTab({
  emp,
  canEdit,
  isSelf,
  canAssignRoles,
  onAssignRoles,
}: EmpProp & { isSelf?: boolean; canAssignRoles?: boolean; onAssignRoles?: () => void }) {
  const [editing, setEditing] = useState(false);
  const [selfEdit, setSelfEdit] = useState(false);
  const cf = (emp as Employee & { customFields?: Record<string, unknown> }).customFields;
  const pickCf = (k: string) => {
    const d = (emp as Record<string, unknown>)[k];
    if (d != null && String(d).trim()) return String(d);
    return cf?.[k] == null ? "—" : String(cf[k]);
  };
  const photoFileId =
    emp.photoFileId ??
    (emp as Employee & { customFields?: Record<string, unknown> }).customFields?.photoFileId;

  return (
    <div className="card stack">
      <div className="card-head">
        <div className="row">
          <EmployeePhoto photoFileId={photoFileId} fullName={emp.fullName} size={56} />
          <div>
            <h2>{emp.fullName}</h2>
            <p className="muted small">{emp.preferredName ? `Goes by ${emp.preferredName}` : "—"}</p>
          </div>
        </div>
        <div className="row">
          {canAssignRoles ? (
            <button type="button" className="btn" onClick={onAssignRoles}>
              Roles
            </button>
          ) : null}
          {canEdit && !editing && !selfEdit ? (
            <button type="button" className="btn" onClick={() => setEditing(true)}>
              Edit profile
            </button>
          ) : null}
          {isSelf && !canEdit && !selfEdit && !editing ? (
            <button type="button" className="btn" onClick={() => setSelfEdit(true)}>
              Edit my contact
            </button>
          ) : null}
        </div>
      </div>
      {editing ? (
        <EmployeeForm
          mode="edit"
          sections="profile"
          employee={emp}
          onSaved={() => setEditing(false)}
          onCancel={() => setEditing(false)}
        />
      ) : selfEdit ? (
        <PersonalContactForm employee={emp} onSaved={() => setSelfEdit(false)} onCancel={() => setSelfEdit(false)} />
      ) : (
        <div className="kv">
          <KV label="Work email">{str(emp.email)}</KV>
          <KV label="Phone">{str(emp.phone)}</KV>
          <KV label="Personal email">{str(emp.personalEmail)}</KV>
          <KV label="Address">{pickCf("address")}</KV>
          <KV label="Emergency contact">{pickCf("emergencyName")}</KV>
          <KV label="Emergency relationship">{pickCf("emergencyRelationship")}</KV>
          <KV label="Emergency phone">{pickCf("emergencyPhone")}</KV>
          <KV label="Preferred name">{str(emp.preferredName)}</KV>
          <KV label="Employee code">
            <span className="mono">{str(emp.employeeCode)}</span>
          </KV>
          <KV label="Roles">
            <div className="row">
              {(emp.roles ?? []).length === 0 ? <span className="pill warn">No role — access pending</span> : null}
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
  const { can } = useAuth();
  const [editing, setEditing] = useState(false);
  const [schedOpen, setSchedOpen] = useState(false);
  const canEditSchedule = can("attendance_settings", "edit");
  const teams = useTeams();
  const locations = useLocations();
  const departments = useDepartments();
  const designations = useDesignations();
  const teamName = useMemo(() => byId(teams.data, (t) => t.name), [teams.data]);
  const locName = useMemo(() => byId(locations.data, (l) => l.name), [locations.data]);
  const deptName = useMemo(() => byId(departments.data, (d) => d.name), [departments.data]);
  const desigName = useMemo(() => byId(designations.data, (d) => d.name), [designations.data]);
  const dir = useDirectory();
  const qc = useQueryClient();

  const reporting = useQuery({
    queryKey: ["reporting-line", emp.id],
    queryFn: ({ signal }) => apiFetch<Rec[]>(api.hrms(`employees/${emp.id}/reporting-line`), { signal }),
  });
  const line = rowsOf<Rec>(reporting.data);

  const scheduleQ = useQuery({
    queryKey: ["work-schedule-resolve", emp.id],
    queryFn: ({ signal }) =>
      apiFetch<{
        source?: string | null;
        workPattern?: string | null;
        resolved?: Rec | null;
      }>(api.hrms(`work-schedules/resolve/${emp.id}`), { signal }),
  });
  const resolved = scheduleQ.data?.resolved;
  const hourly = String(resolved?.mode ?? "").toUpperCase() === "HOURLY";
  const dayCodes = parseDays(resolved?.workingDays);

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
            {showSalary ? <CurrentPayKV empId={String(emp.id)} /> : null}
            <KV label="Status">
              <StatusPill status={emp.status} />
            </KV>
          </div>
        )}
      </div>

      <div className="card stack">
        <div className="card-head" style={{ margin: 0 }}>
          <h2>Work schedule</h2>
          {canEditSchedule ? (
            <button type="button" className="btn sm" onClick={() => setSchedOpen(true)}>
              Change schedule
            </button>
          ) : null}
        </div>
        {scheduleQ.isLoading ? (
          <p className="muted">Loading schedule…</p>
        ) : scheduleQ.error ? (
          <ErrorNote error={scheduleQ.error} />
        ) : !resolved ? (
          <p className="muted">No location, team or person schedule resolved yet. Set one under Settings → Work schedules.</p>
        ) : hourly ? (
          <>
            <p>
              <b>Hourly</b>
              {resolved.weeklyHours != null ? ` · expected ${fmtNumber(resolved.weeklyHours)} h/week` : ""}
              {" · "}
              from {humanize(scheduleQ.data?.source)}
            </p>
            <p className="small muted">
              Paid by hours worked from check-in to check-out. No late or absent marking from fixed office hours.
            </p>
          </>
        ) : (
          <>
            <p>
              <b>{dayCodes.length || "—"} days</b>
              {dayCodes.length ? ` (${dayCodes.join(", ")})` : ""} · {str(resolved.startTime)}–{str(resolved.endTime)}
              {" · "}
              from {humanize(scheduleQ.data?.source)}
            </p>
            <p className="small muted">
              Late after {str(resolved.startTime)} plus {str(resolved.graceMinutes, "15")} minutes
              {resolved.halfDayHours != null ? ` · half day under ${fmtNumber(resolved.halfDayHours)} h` : ""}.
            </p>
          </>
        )}
        <ScheduleEditorModal
          open={schedOpen}
          target={
            schedOpen
              ? { mode: "employee", employeeId: emp.id, employeeName: emp.fullName || undefined }
              : null
          }
          onClose={() => setSchedOpen(false)}
          onSaved={() => {
            void qc.invalidateQueries({ queryKey: ["work-schedule-resolve", emp.id] });
            void qc.invalidateQueries({ queryKey: ["work-schedules"] });
          }}
          allowScopes={["EMPLOYEE"]}
        />
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

function SalaryTab({ empId, emp, canEdit }: { empId: string; emp: Employee; canEdit: boolean }) {
  return <SalaryPanel employeeId={empId} defaultCurrency={emp.payCurrency} canEdit={canEdit} />;
}

function HistoryTab({ empId }: { empId: string }) {
  const hist = useQuery({
    queryKey: ["employment-history", empId],
    queryFn: ({ signal }) => apiFetch<Rec[]>(api.hrms(`employees/${empId}/employment-history`), { signal }),
  });
  const rows = rowsOf<Rec>(hist.data);
  return (
    <div className="card stack">
      <h2>Employment history</h2>
      <p className="muted small">Every employment field change — old value, new value, editor and date (FR-EMP-07).</p>
      <ErrorNote error={hist.error} />
      <div className="tbl-wrap">
        <table>
          <thead>
            <tr>
              <th>When</th>
              <th>Field</th>
              <th>Old</th>
              <th>New</th>
              <th>Editor</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={5} className="empty">
                  {hist.isLoading ? "Loading…" : "No employment changes recorded yet"}
                </td>
              </tr>
            ) : (
              rows.map((r) => (
                <tr key={String(r.id)}>
                  <td className="mono small">{fmtDateTime(r.changedAt)}</td>
                  <td>{humanize(String(r.fieldName ?? ""))}</td>
                  <td className="muted small">{str(r.oldValue)}</td>
                  <td className="small">{str(r.newValue)}</td>
                  <td className="mono small">{r.changedBy != null ? `#${r.changedBy}` : "—"}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

async function sleep(ms: number) {
  await new Promise((r) => setTimeout(r, ms));
}

/** Poll file-service until scan leaves quarantine (guide §8) or timeout. */
async function waitForScanRelease(
  fileId: string | number,
  opts?: {
    minMs?: number;
    onTick?: (meta: { scanStatus?: string; scanDetail?: string | null; downloadable?: boolean }) => void;
  },
): Promise<{
  scanStatus?: string;
  scanDetail?: string | null;
  downloadable?: boolean;
}> {
  const started = Date.now();
  const minMs = opts?.minMs ?? 2200;
  let last: { scanStatus?: string; scanDetail?: string | null; downloadable?: boolean } = {
    scanStatus: "SCANNING",
  };
  opts?.onTick?.(last);
  for (let i = 0; i < 45; i++) {
    try {
      last = await apiFetch<{
        scanStatus?: string;
        scanDetail?: string | null;
        downloadable?: boolean;
      }>(api.files(String(fileId)));
      // While still pending, keep the UI on SCANNING so the animation stays obvious.
      if (isScanPending(last.scanStatus)) {
        last = { ...last, scanStatus: last.scanStatus || "SCANNING" };
      }
      opts?.onTick?.(last);
      const status = normalizeScanStatus(last.scanStatus);
      if (!isScanPending(status)) {
        const elapsed = Date.now() - started;
        if (elapsed < minMs) await sleep(minMs - elapsed);
        return last;
      }
    } catch {
      /* keep waiting — metadata may briefly 403/404 during bind */
      opts?.onTick?.({ scanStatus: "SCANNING" });
    }
    await sleep(900);
  }
  const elapsed = Date.now() - started;
  if (elapsed < minMs) await sleep(minMs - elapsed);
  return last;
}

function DocumentsTab({ empId, canEdit }: { empId: string; canEdit: boolean }) {
  const qc = useQueryClient();
  const toast = useToast();
  const fileRef = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [docType, setDocType] = useState<string>(DOC_TYPES[0]);
  const [name, setName] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [err, setErr] = useState("");
  const [phase, setPhase] = useState<"form" | "uploading" | "scanning" | "ready" | "rejected">("form");
  const [liveScan, setLiveScan] = useState<{ status?: string; detail?: string | null }>({});

  const docs = useQuery({
    queryKey: ["employee-docs", empId],
    queryFn: ({ signal }) => apiFetch(api.hrms(`employees/${empId}/documents`), { signal }),
    refetchInterval: (q) => {
      const rows = rowsOf<Rec>(q.state.data);
      return rows.some((d) => isScanPending(d.scanStatus as string)) ? 2500 : false;
    },
  });
  const rows = rowsOf<Rec>(docs.data);

  function resetModal() {
    setOpen(false);
    setPhase("form");
    setLiveScan({});
    setName("");
    setExpiresAt("");
    setFile(null);
    setErr("");
    if (fileRef.current) fileRef.current.value = "";
  }

  async function logAccess(fileId: string, action: "OPEN" | "DOWNLOAD") {
    try {
      await apiFetch(api.hrms(`employees/${empId}/documents/${fileId}/access`), {
        method: "POST",
        body: { action },
      });
    } catch {
      /* access log is best-effort — still allow download */
    }
  }

  const attach = useMutation({
    mutationFn: async () => {
      const invalid = validateDocumentFile(file);
      if (invalid) throw new Error(invalid);
      // Hold uploading long enough for the radar UI to paint (fast networks otherwise skip it).
      await sleep(700);
      const fd = new FormData();
      fd.append("file", file!);
      fd.append("employeeId", String(empId));
      fd.append("classification", "GENERAL");
      const uploaded = await apiFetch<{
        id: number | string;
        originalName?: string;
        scanStatus?: string;
      }>("/api/files", {
        method: "POST",
        body: fd,
      });
      setPhase("scanning");
      setLiveScan({ status: uploaded.scanStatus || "SCANNING" });
      await sleep(50);
      // Guide §8: attach metadata while quarantined — download stays locked until RELEASED.
      await apiFetch(api.hrms(`employees/${empId}/documents`), {
        method: "POST",
        body: {
          fileId: String(uploaded.id),
          documentType: docType,
          name: name.trim() || uploaded.originalName || docType,
          ...(expiresAt ? { expiresAt } : {}),
        },
      });
      const scanned = await waitForScanRelease(uploaded.id, {
        minMs: 2400,
        onTick: (meta) => setLiveScan({ status: meta.scanStatus || "SCANNING", detail: meta.scanDetail }),
      });
      setLiveScan({ status: scanned.scanStatus, detail: scanned.scanDetail });
      if (isScanRejected(scanned.scanStatus)) {
        setPhase("rejected");
        throw new Error(
          scanned.scanDetail?.trim() ||
            `File scan ${normalizeScanStatus(scanned.scanStatus).toLowerCase()} — document was attached but cannot be downloaded`,
        );
      }
      if (isScanPending(scanned.scanStatus)) {
        setPhase("scanning");
      } else {
        setPhase("ready");
        await sleep(1200);
      }
      return scanned;
    },
    onSuccess: (scanned) => {
      void qc.invalidateQueries({ queryKey: ["employee-docs", empId] });
      if (isScanPending(scanned.scanStatus)) {
        toast.show("Document attached — malware scan still running");
      } else {
        toast.show("Document attached and scan released");
      }
      resetModal();
    },
    onError: (e: Error) => {
      setErr(e.message);
      setPhase((p) => (p === "rejected" ? p : "form"));
      void qc.invalidateQueries({ queryKey: ["employee-docs", empId] });
    },
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

  const busy = attach.isPending || phase === "uploading" || phase === "scanning";

  return (
    <div className="card stack">
      <div className="card-head">
        <h2>Documents</h2>
        {canEdit ? (
          <button
            type="button"
            className="btn primary"
            onClick={() => {
              setPhase("form");
              setLiveScan({});
              setErr("");
              setOpen(true);
            }}
          >
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
              <th>Scan</th>
              <th>Expires</th>
              <th>Uploaded</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={6} className="empty">
                  {docs.isLoading ? "Loading…" : "No documents attached"}
                </td>
              </tr>
            ) : (
              rows.map((d) => {
                const status = d.scanStatus as string | undefined;
                const canDownload = isScanDownloadable(status, d.downloadable as boolean | undefined);
                const pending = isScanPending(status);
                const rejected = isScanRejected(status);
                return (
                  <tr key={String(d.fileId)}>
                    <td>{str(d.name)}</td>
                    <td>
                      <span className="pill">{str(d.documentType ?? d.type)}</span>
                    </td>
                    <td>
                      <ScanStatusBadge
                        status={status}
                        detail={typeof d.scanDetail === "string" ? d.scanDetail : null}
                      />
                    </td>
                    <td className="muted small">{d.expiresAt ? fmtDate(d.expiresAt) : "—"}</td>
                    <td className="muted small">{fmtDateTime(d.uploadedAt)}</td>
                    <td>
                      <div className="row" style={{ justifyContent: "flex-end" }}>
                        <button
                          type="button"
                          className="btn sm"
                          disabled={!canDownload || pending || rejected}
                          title={
                            pending
                              ? "Download locked until malware scan releases the file"
                              : rejected
                                ? "File rejected by content scan"
                                : undefined
                          }
                          onClick={() =>
                            void (async () => {
                              try {
                                await logAccess(String(d.fileId), "DOWNLOAD");
                                // Guide §8: authorize then issue short-lived URL (blocked while quarantined).
                                const issued = await apiFetch<{ url: string }>(
                                  api.files(`${d.fileId}/download-url`),
                                  { method: "POST" },
                                );
                                const path = issued.url.startsWith("/")
                                  ? issued.url
                                  : api.files(`${d.fileId}/content`);
                                await downloadFile(path, undefined, String(d.name ?? "document"));
                              } catch (er) {
                                toast.error(er as Error);
                              }
                            })()
                          }
                        >
                          {pending ? "Scanning…" : "Download"}
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
                );
              })
            )}
          </tbody>
        </table>
      </div>

      <Modal
        open={open}
        onClose={() => {
          if (!busy) resetModal();
        }}
        title="Attach document"
        subtitle="Uploads quarantine in MinIO, then async malware scan releases download (guide §8)."
        footer={
          <>
            <button type="button" className="btn" onClick={() => resetModal()} disabled={busy && phase !== "rejected"}>
              {phase === "ready" || phase === "rejected" ? "Close" : "Cancel"}
            </button>
            {phase === "form" || phase === "rejected" ? (
              <button
                type="button"
                className="btn primary"
                disabled={busy || !file || !!validateDocumentFile(file)}
                onClick={() => {
                  setErr("");
                  // Switch to the animated panel immediately (before the mutation starts).
                  setPhase("uploading");
                  setLiveScan({ status: "QUARANTINED" });
                  attach.mutate();
                }}
              >
                Upload & attach
              </button>
            ) : null}
          </>
        }
      >
        {phase !== "form" ? (
          <div className="stack" style={{ gap: 12 }}>
            <DocumentScanPanel
              phase={
                phase === "uploading" || phase === "scanning" || phase === "ready" || phase === "rejected"
                  ? phase
                  : "scanning"
              }
              fileName={file?.name}
              status={liveScan.status || (phase === "scanning" ? "SCANNING" : phase === "uploading" ? "QUARANTINED" : liveScan.status)}
              detail={liveScan.detail}
            />
            {err ? <div className="notice bad">{err}</div> : null}
          </div>
        ) : (
          <div className="stack" style={{ gap: 12 }}>
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
              <label className="field">
                <span>Expiry date (optional)</span>
                <input type="date" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)} />
              </label>
              <label className="field full">
                <span>File (PDF, JPG, PNG or DOCX — max 10 MB)</span>
                <input
                  ref={fileRef}
                  type="file"
                  accept={DOCUMENT_ACCEPT}
                  onChange={(e) => {
                    const next = e.target.files?.[0] ?? null;
                    setFile(next);
                    const invalid = validateDocumentFile(next);
                    setErr(invalid && next ? invalid : "");
                  }}
                />
              </label>
            </div>
            {err ? <div className="notice bad">{err}</div> : null}
          </div>
        )}
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
    queryKey: ["employee-balances-current", empId],
    queryFn: ({ signal }) =>
      apiFetch(api.hrms(`leave-balances/employee/${empId}`), {
        query: { currentOnly: "true" },
        signal,
      }),
  });
  const requests = useQuery({
    queryKey: ["employee-leave", empId],
    queryFn: ({ signal }) =>
      apiFetch(api.hrms(`leave/employee/${empId}`), { query: { page: 1, size: 50 }, signal }),
  });
  const bal = rowsOf<Rec>(balances.data)
    .slice()
    .sort(
      (a, b) =>
        Number(b.availableDays ?? b.balanceDays ?? 0) - Number(a.availableDays ?? a.balanceDays ?? 0),
    )
    .slice(0, 5);
  const reqs = rowsOf<Rec>(requests.data);

  return (
    <div className="stack">
      <div className="card stack">
        <h2>Balances</h2>
        <p className="small muted" style={{ marginTop: -8 }}>
          Current period only — up to 5 categories.
        </p>
        <ErrorNote error={balances.error} />
        <div className="grid g4">
          {bal.length === 0 ? (
            <p className="muted">{balances.isLoading ? "Loading…" : "No current-period balances"}</p>
          ) : null}
          {bal.map((b) => (
            <div key={String(b.id ?? `${b.leaveTypeCode}-${b.year}`)} className="card" style={{ background: "var(--sunk)" }}>
              <div className="stat">
                <span className="s">{str(b.leaveTypeName, str(b.leaveTypeCode))}</span>
                <span className="v">{fmtLeaveDays(b.availableDays ?? b.balanceDays)}</span>
                <span className="s">available{b.year != null ? ` · ${str(b.year)}` : ""}</span>
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
                    <td className="n">{fmtLeaveDays(r.workingDaysCounted)}</td>
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

/** FR-WL-08 — last 14 days of work logs. */
function WorkLogTab({ empId }: { empId: string }) {
  const { can } = useAuth();
  const canSee = can("attendance", "view") || can("worklogs", "view");
  const logs = useQuery({
    queryKey: ["emp-worklog-14", empId],
    queryFn: async ({ signal }) => {
      const raw = await apiFetch(api.hrms(`work-logs/employee/${empId}`), { query: { days: 14 }, signal });
      return rowsOf<Rec>(raw);
    },
    enabled: canSee,
  });
  if (!canSee) return <NoAccess what="work logs" />;
  const rows = logs.data ?? [];
  return (
    <div className="card stack">
      <h2>Work log — last 14 days</h2>
      <ErrorNote error={logs.error} />
      <div className="tbl-wrap">
        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Task</th>
              <th className="n">Time</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={4} className="empty">
                  {logs.isLoading ? "Loading…" : "No work logs in the last 14 days"}
                </td>
              </tr>
            ) : (
              rows.map((r) => (
                <tr key={String(r.id)}>
                  <td className="mono">{fmtDate(r.workDate)}</td>
                  <td>{str(r.taskTitle, `Task ${r.taskId}`)}</td>
                  <td className="n">
                    {fmtNumber(Number(r.minutes || 0) / 60, 2)} h
                    {r.flagged ? <span className="pill warn" style={{ marginLeft: 6 }}>Flagged</span> : null}
                  </td>
                  <td>
                    {str(r.note, "")}
                    {r.flagReason ? <div className="small muted">{String(r.flagReason)}</div> : null}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
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

/** Current pay from Payroll (salary owner) — never People's legacy salary columns. */
function CurrentPayKV({ empId }: { empId: string }) {
  const salary = useSalary(empId);
  const cur = (salary.data?.current ?? null) as Rec | null;
  if (!cur) return <KV label="Pay">{salary.isLoading ? "Loading…" : "No salary record"}</KV>;
  const hourly = String(cur.payType) === "HOURLY";
  return (
    <>
      <KV label="Pay type">{hourly ? "Hourly" : "Monthly"}</KV>
      <KV label={hourly ? "Hourly rate" : "Monthly gross"}>{fmtMoney(cur.amount, str(cur.currency, ""))}</KV>
    </>
  );
}
