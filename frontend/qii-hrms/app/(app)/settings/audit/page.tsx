"use client";

import { useMemo, useState } from "react";
import { useAuth } from "@/components/auth/AuthProvider";
import { DataTable, type Column } from "@/components/ui/DataTable";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { NoAccess } from "@/components/ui/QueryState";
import { StatusPill } from "@/components/ui/StatusPill";
import { api } from "@/lib/api/client";
import { useDirectory } from "@/lib/api/directory";
import { oneLine, prettyJson } from "@/lib/admin";
import { fmtDateTime, humanize } from "@/lib/format";
import { auditRowSchema, type AuditRow } from "@/lib/schemas/admin";

const SERVICES = [
  { id: "", label: "All services" },
  { id: "people", label: "People" },
  { id: "leave", label: "Leave" },
  { id: "time-work", label: "Time & work" },
  { id: "payroll", label: "Payroll" },
  { id: "files", label: "Files" },
  { id: "notification", label: "Notifications" },
  { id: "report", label: "Reports" },
];

/**
 * FR-AU-01 category presets. Values are sent as filter.* (supports exact, *contains*,
 * prefix*, and a|b OR). Avoid wrapping short tokens like OPEN in *…* — that matches REOPEN.
 */
const CATEGORIES: {
  id: string;
  label: string;
  action?: string;
  entity?: string;
  service?: string;
}[] = [
  { id: "", label: "All categories" },
  { id: "signin", label: "Sign-ins", action: "SIGN_IN", entity: "UserAccount" },
  { id: "roles", label: "Roles & permissions", entity: "App*" },
  { id: "employment", label: "Employment edits", action: "UPDATE", entity: "Employee" },
  { id: "salary", label: "Salary changes", action: "SALARY_CHANGE", entity: "salary*" },
  { id: "payroll-adj", label: "Payroll adjustments", action: "*ADJUSTMENT*", entity: "payroll*" },
  {
    id: "finalize",
    label: "Finalise",
    action: "PAYROLL_FINALIZE|PAYROLL_FINALIZE_REQUEST",
    entity: "payroll_run",
  },
  {
    id: "reopen",
    label: "Reopen",
    action: "PAYROLL_REOPEN|PAYROLL_REOPEN_REQUEST",
    entity: "payroll_run",
  },
  { id: "balance", label: "Leave balances", entity: "LeaveBalance", service: "leave" },
  { id: "attendance", label: "Attendance edits", action: "ATTENDANCE*", service: "time-work" },
  {
    id: "leave-decisions",
    label: "Leave decisions",
    entity: "LeaveApplication",
    action:
      "APPROVE|REJECT|STEP_APPROVE|CANCEL_APPROVE|CANCEL_REJECT|CANCEL_STEP_APPROVE|CANCEL_STEP_REJECT",
  },
  { id: "doc-open", label: "Document opens", action: "OPEN", entity: "EmployeeDocument" },
  { id: "doc-download", label: "Document downloads", action: "DOWNLOAD", entity: "EmployeeDocument" },
  { id: "doc-delete", label: "Document deletions", action: "DELETE", entity: "EmployeeDocument" },
  { id: "file-delete", label: "File deletions", action: "DELETE", entity: "FileObject" },
  { id: "settings", label: "Setting changes", entity: "*Settings*" },
  { id: "exports", label: "Exports", action: "EXPORT" },
];

function toneFor(action: string | null | undefined) {
  const a = (action ?? "").toUpperCase();
  if (a.includes("DELETE") || a.includes("REJECT") || a.includes("FAIL")) return "bad" as const;
  if (a.includes("CREATE") || a.includes("APPROVE") || a.includes("SIGN_IN") || a.includes("FINALIZE")) return "good" as const;
  if (a.includes("UPDATE") || a.includes("CORRECT") || a.includes("ADJUST") || a.includes("REOPEN") || a.includes("EXPORT")) {
    return "warn" as const;
  }
  return "info" as const;
}

export default function AuditPage() {
  const { can } = useAuth();
  const allowed = can("audit", "view") || can("settings", "manage");
  const dir = useDirectory(allowed && can("employees", "view"));
  const [actor, setActor] = useState("");
  const [action, setAction] = useState("");
  const [entity, setEntity] = useState("");
  const [entityId, setEntityId] = useState("");
  const [service, setService] = useState("");
  const [category, setCategory] = useState("");
  const [open, setOpen] = useState<AuditRow | null>(null);

  const filters = useMemo(() => {
    const f: Record<string, string> = {};
    const cat = CATEGORIES.find((c) => c.id === category);
    if (actor) f.actorEmployeeId = actor;
    // Manual fields win; category presets keep their own wildcard/OR shape.
    const actionVal = action.trim() || cat?.action || "";
    const entityVal = entity.trim() || cat?.entity || "";
    if (actionVal) {
      f.action = action.trim()
        ? actionVal.includes("*") || actionVal.includes("|")
          ? actionVal
          : `*${actionVal}*`
        : actionVal;
    }
    if (entityVal) {
      f.entity = entity.trim()
        ? entityVal.includes("*") || entityVal.includes("|")
          ? entityVal
          : `*${entityVal}*`
        : entityVal;
    }
    if (entityId.trim()) f.entityId = entityId.trim();
    if (service || cat?.service) f.service = service || cat?.service || "";
    return f;
  }, [actor, action, entity, entityId, service, category]);

  const columns: Column<AuditRow>[] = [
    { key: "at", header: "When", className: "mono small", render: (r) => fmtDateTime(r.at) },
    {
      key: "service",
      header: "Service",
      sortable: true,
      className: "mono small",
      render: (r) => r.service ?? "people",
    },
    { key: "actorEmployeeId", header: "Who", sortable: true, render: (r) => dir.nameOf(r.actorEmployeeId) },
    {
      key: "action",
      header: "Action",
      sortable: true,
      render: (r) => <StatusPill tone={toneFor(r.action)}>{humanize(r.action)}</StatusPill>,
    },
    {
      key: "entity",
      header: "Record",
      sortable: true,
      render: (r) => (
        <>
          {r.entity}
          {r.entityId ? <span className="mono small muted"> #{r.entityId}</span> : null}
        </>
      ),
    },
    { key: "beforeJson", header: "Before", className: "small muted", render: (r) => oneLine(r.beforeJson, 60) || "—" },
    { key: "afterJson", header: "After", className: "small", render: (r) => oneLine(r.afterJson, 60) || "—" },
    { key: "ip", header: "IP", className: "mono small", render: (r) => r.ip || "—" },
  ];

  if (!allowed) {
    return (
      <div className="stack">
        <PageHeader title="Audit log" />
        <NoAccess what="the audit log" />
      </div>
    );
  }

  return (
    <div className="stack">
      <PageHeader
        title="Audit log"
        subtitle="Read-only trail across every service — sign-ins, roles, employment, salary, payroll, leave, attendance, files, settings and exports. Search by person, action or record."
      />
      <DataTable<AuditRow>
        queryKey="audit-log"
        path={api.hrms("audit")}
        itemSchema={auditRowSchema}
        columns={columns}
        filters={filters}
        emptyText="No audit entries match these filters"
        onRowClick={setOpen}
        rowKey={(r) => String(r.id)}
        toolbar={
          <>
            <select
              aria-label="Category"
              value={category}
              onChange={(e) => {
                setCategory(e.target.value);
                // Clear free-text action/entity when picking a preset so filters don't fight.
                if (e.target.value) {
                  setAction("");
                  setEntity("");
                }
              }}
              style={{ width: 200 }}
            >
              {CATEGORIES.map((c) => (
                <option key={c.id || "all"} value={c.id}>
                  {c.label}
                </option>
              ))}
            </select>
            <select aria-label="Service" value={service} onChange={(e) => setService(e.target.value)} style={{ width: 150 }}>
              {SERVICES.map((s) => (
                <option key={s.id || "all"} value={s.id}>
                  {s.label}
                </option>
              ))}
            </select>
            {dir.employees.length ? (
              <select aria-label="Person" value={actor} onChange={(e) => setActor(e.target.value)} style={{ width: 190 }}>
                <option value="">Anyone</option>
                {dir.employees.map((e) => (
                  <option key={String(e.id)} value={String(e.id)}>
                    {e.fullName}
                  </option>
                ))}
              </select>
            ) : null}
            <input
              type="search"
              aria-label="Action"
              placeholder="Action (approve, export…)"
              value={action}
              onChange={(e) => setAction(e.target.value)}
              style={{ width: 170 }}
            />
            <input
              type="search"
              aria-label="Record type"
              placeholder="Record (Employee, Leave…)"
              value={entity}
              onChange={(e) => setEntity(e.target.value)}
              style={{ width: 190 }}
            />
            <input
              type="search"
              aria-label="Record id"
              placeholder="Record id"
              value={entityId}
              onChange={(e) => setEntityId(e.target.value)}
              style={{ width: 110 }}
            />
          </>
        }
      />

      <Modal
        open={!!open}
        wide
        title={open ? `${humanize(open.action)} · ${open.entity ?? ""}${open.entityId ? ` #${open.entityId}` : ""}` : ""}
        subtitle={
          open
            ? `${fmtDateTime(open.at)} · ${open.service ?? "people"} · ${dir.nameOf(open.actorEmployeeId)}${open.ip ? ` · ${open.ip}` : ""}`
            : undefined
        }
        onClose={() => setOpen(null)}
      >
        <div className="grid g2">
          <div className="stack">
            <span className="label">Before</span>
            <pre className="mono small note" style={{ whiteSpace: "pre-wrap", overflow: "auto", margin: 0 }}>
              {prettyJson(open?.beforeJson) || "—"}
            </pre>
          </div>
          <div className="stack">
            <span className="label">After</span>
            <pre className="mono small note" style={{ whiteSpace: "pre-wrap", overflow: "auto", margin: 0 }}>
              {prettyJson(open?.afterJson) || "—"}
            </pre>
          </div>
        </div>
      </Modal>
    </div>
  );
}
