"use client";

import { useState } from "react";
import { useAuth } from "@/components/auth/AuthProvider";
import { DataTable, type Column } from "@/components/ui/DataTable";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { StatusPill } from "@/components/ui/StatusPill";
import { api } from "@/lib/api/client";
import { useDirectory } from "@/lib/api/directory";
import { oneLine, prettyJson } from "@/lib/admin";
import { fmtDateTime, humanize } from "@/lib/format";
import { auditRowSchema, type AuditRow } from "@/lib/schemas/admin";

function toneFor(action: string | null | undefined) {
  const a = (action ?? "").toUpperCase();
  if (a.includes("DELETE") || a.includes("REJECT")) return "bad" as const;
  if (a.includes("CREATE") || a.includes("APPROVE")) return "good" as const;
  if (a.includes("UPDATE") || a.includes("CORRECT")) return "warn" as const;
  return "info" as const;
}

export default function AuditPage() {
  const { can } = useAuth();
  const dir = useDirectory(can("employees", "view"));
  const [actor, setActor] = useState("");
  const [entity, setEntity] = useState("");
  const [open, setOpen] = useState<AuditRow | null>(null);

  const filters: Record<string, string> = {};
  if (actor) filters.actorEmployeeId = actor;
  if (entity.trim()) filters.entity = `*${entity.trim()}*`; // backend filter syntax: *text* = contains

  const columns: Column<AuditRow>[] = [
    { key: "at", header: "When", className: "mono small", render: (r) => fmtDateTime(r.at) },
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
    { key: "ip", header: "IP", className: "mono small", render: (r) => r.ip ?? "—" },
  ];

  return (
    <div className="stack">
      <PageHeader
        title="Audit log"
        subtitle="Read only. Role and permission changes, attendance check-ins and corrections, and other recorded changes. Click a row for the full before/after."
      />
      <DataTable<AuditRow>
        queryKey="audit-log"
        path={api.hrms("audit")}
        itemSchema={auditRowSchema}
        columns={columns}
        filters={filters}
        emptyText="No audit entries"
        onRowClick={setOpen}
        rowKey={(r) => String(r.id)}
        toolbar={
          <>
            {dir.employees.length ? (
              <select aria-label="Who" value={actor} onChange={(e) => setActor(e.target.value)} style={{ width: 190 }}>
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
              aria-label="Record type"
              placeholder="Record type, e.g. AppRole"
              value={entity}
              onChange={(e) => setEntity(e.target.value)}
              style={{ width: 200 }}
            />
          </>
        }
      />

      <Modal
        open={!!open}
        wide
        title={open ? `${humanize(open.action)} · ${open.entity ?? ""}${open.entityId ? ` #${open.entityId}` : ""}` : ""}
        subtitle={open ? `${fmtDateTime(open.at)} · ${dir.nameOf(open.actorEmployeeId)}${open.ip ? ` · ${open.ip}` : ""}` : undefined}
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
