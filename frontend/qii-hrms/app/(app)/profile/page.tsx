"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { PersonalContactForm } from "@/components/employees/PersonalContactForm";
import { useAuth } from "@/components/auth/AuthProvider";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { StatusPill } from "@/components/ui/StatusPill";
import { api, apiFetch } from "@/lib/api/client";
import { fmtDate, humanize, initials, str } from "@/lib/format";
import { employeeSchema } from "@/lib/schemas/employee";

function KV({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="label">{label}</div>
      <div>{children}</div>
    </div>
  );
}

export default function ProfilePage() {
  const { me, can, signOut } = useAuth();
  const [editingContact, setEditingContact] = useState(false);

  // Richer record (team, location, manager…) — needs employees:view, which falls back to /me data.
  const self = useQuery({
    queryKey: ["employee", me.employeeId],
    queryFn: ({ signal }) => apiFetch(api.hrms(`employees/${me.employeeId}`), { schema: employeeSchema, signal }),
    enabled: can("employees", "view"),
    retry: false,
  });
  const e = self.data;
  const cf = (e as { customFields?: Record<string, unknown> } | undefined)?.customFields;
  const pickCf = (k: string) => {
    const d = e ? (e as Record<string, unknown>)[k] : undefined;
    if (d != null && String(d).trim()) return String(d);
    return cf?.[k] == null ? "—" : String(cf[k]);
  };

  const permissionCount = me.permissions.length;

  return (
    <div className="stack">
      <PageHeader
        title="My profile"
        subtitle="Your details as held in the HR record. Ask HR if something is out of date."
        actions={
          <>
            {can("employees", "view") ? (
              <Link href={`/employees/${me.employeeId}`} className="btn">
                Open full record
              </Link>
            ) : null}
            <button type="button" className="btn" onClick={() => void signOut()}>
              Sign out
            </button>
          </>
        }
      />

      <div className="card">
        <div className="row" style={{ gap: 16 }}>
          <div
            aria-hidden
            style={{
              width: 64,
              height: 64,
              borderRadius: "50%",
              background: "var(--accent-soft)",
              color: "var(--accent)",
              display: "grid",
              placeItems: "center",
              fontWeight: 600,
              fontSize: 22,
            }}
          >
            {initials(me.name)}
          </div>
          <div style={{ flex: 1, minWidth: 200 }}>
            <h2>{me.name}</h2>
            <p className="muted">
              {str(e?.jobTitle ?? me.position)}
              {e?.departmentName ?? me.department ? ` · ${e?.departmentName ?? me.department}` : ""}
            </p>
          </div>
          <StatusPill status={e?.status ?? me.status ?? "Active"} />
        </div>
      </div>

      <ErrorNote error={self.error} />

      <div className="grid g2">
        <section className="card stack">
          <div className="card-head">
            <h2>Contact</h2>
            {!editingContact ? (
              <button type="button" className="btn" onClick={() => setEditingContact(true)}>
                Edit my contact
              </button>
            ) : null}
          </div>
          {editingContact ? (
            <PersonalContactForm
              employee={e}
              onSaved={() => setEditingContact(false)}
              onCancel={() => setEditingContact(false)}
            />
          ) : (
            <div className="kv">
              <KV label="Work email">{str(e?.email ?? me.email)}</KV>
              <KV label="Phone">{str(e?.phone ?? me.phoneNumber)}</KV>
              <KV label="Preferred name">{str(e?.preferredName)}</KV>
              <KV label="Personal email">{str(e?.personalEmail)}</KV>
              <KV label="Address">{pickCf("address")}</KV>
              <KV label="Emergency contact">{pickCf("emergencyName")}</KV>
              <KV label="Emergency phone">{pickCf("emergencyPhone")}</KV>
            </div>
          )}
        </section>

        <section className="card stack">
          <h2>Employment</h2>
          <div className="kv">
            <KV label="Employee ID">
              <span className="mono">{str(e?.employeeCode ?? me.employeeId)}</span>
            </KV>
            <KV label="Joined">{fmtDate(e?.startDate ?? me.joinDate)}</KV>
            <KV label="Team">{str(e?.teamName)}</KV>
            <KV label="Location">{str(e?.locationName)}</KV>
            <KV label="Reports to">
              {e?.managerId && can("employees", "view") ? (
                <Link href={`/employees/${e.managerId}`}>{str(e.managerName)}</Link>
              ) : (
                str(e?.managerName)
              )}
            </KV>
            <KV label="Employment type">{e?.employmentType ? humanize(e.employmentType) : "—"}</KV>
          </div>
        </section>
      </div>

      <section className="card stack">
        <h2>Access</h2>
        <div className="kv">
          <KV label="Roles">
            {me.roles.length ? (
              <span className="row">
                {me.roles.map((r) => (
                  <span key={r} className="pill acc">
                    {r}
                  </span>
                ))}
              </span>
            ) : (
              <span className="pill warn">No role assigned</span>
            )}
          </KV>
          <KV label="Permissions">
            <span className="mono">{permissionCount}</span> <span className="muted small">granted by your roles</span>
          </KV>
          <KV label="Workspace">
            <span className="mono">{str(me.tenantId)}</span>
          </KV>
        </div>
      </section>
    </div>
  );
}
