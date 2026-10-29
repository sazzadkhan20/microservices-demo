"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { DataTable } from "@/components/ui/DataTable";
import { PageHeader } from "@/components/ui/PageHeader";
import { StatusPill } from "@/components/ui/StatusPill";
import { useAuth } from "@/components/auth/AuthProvider";
import { api } from "@/lib/api/client";
import { byId, useLocations, useTeams } from "@/lib/api/lookups";
import { fmtDate, humanize } from "@/lib/format";
import { useDebounced } from "@/lib/hooks";
import { EMPLOYEE_STATUSES, employeeSchema, type Employee } from "@/lib/schemas/employee";

export default function EmployeesPage() {
  const router = useRouter();
  const { can } = useAuth();
  const [q, setQ] = useState("");
  const [locationId, setLocationId] = useState("");
  const [teamId, setTeamId] = useState("");
  const [status, setStatus] = useState("");
  const dq = useDebounced(q, 300);

  const locations = useLocations();
  const teams = useTeams();
  const teamName = useMemo(() => byId(teams.data, (t) => t.name), [teams.data]);
  const locName = useMemo(() => byId(locations.data, (l) => l.name), [locations.data]);

  const filters = useMemo(() => ({ locationId, teamId, status }), [locationId, teamId, status]);
  const hasFilters = !!(q || locationId || teamId || status);

  return (
    <div className="stack">
      <PageHeader
        title="Employees"
        subtitle="Everyone in the organisation. Click a row to open the profile."
        actions={
          can("employees", "create") ? (
            <Link href="/employees/new" className="btn primary">
              + Add employee
            </Link>
          ) : null
        }
      />

      <div className="card stack">
        <div className="filters">
          <label className="field wide">
            <span>Search</span>
            <input
              type="search"
              placeholder="Name, code or email"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </label>
          <label className="field">
            <span>Location</span>
            <select value={locationId} onChange={(e) => setLocationId(e.target.value)}>
              <option value="">All locations</option>
              {(locations.data ?? []).map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Team</span>
            <select value={teamId} onChange={(e) => setTeamId(e.target.value)}>
              <option value="">All teams</option>
              {(teams.data ?? []).map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Status</span>
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">Any status</option>
              {EMPLOYEE_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>
          {hasFilters ? (
            <button
              type="button"
              className="btn"
              onClick={() => {
                setQ("");
                setLocationId("");
                setTeamId("");
                setStatus("");
              }}
            >
              Clear
            </button>
          ) : null}
        </div>

        <DataTable<Employee>
          queryKey="employees"
          path={api.hrms("employees")}
          itemSchema={employeeSchema as never}
          defaultSort="fullName,asc"
          q={dq}
          filters={filters}
          emptyText="No employees match these filters"
          onRowClick={(r) => router.push(`/employees/${r.id}`)}
          rowKey={(r) => String(r.id)}
          columns={[
            { key: "employeeCode", header: "Code", sortable: true, className: "mono" },
            {
              key: "fullName",
              header: "Name",
              sortable: true,
              render: (r) => (
                <div>
                  <b>{r.fullName || "—"}</b>
                  <div className="muted small">{r.email}</div>
                </div>
              ),
            },
            { key: "jobTitle", header: "Title", render: (r) => r.jobTitle || "—" },
            {
              key: "teamName",
              header: "Team",
              render: (r) =>
                r.teamName || (r.teamId != null ? (teamName.get(String(r.teamId)) ?? "—") : "—"),
            },
            { key: "employmentType", header: "Type", render: (r) => humanize(r.employmentType) },
            {
              key: "locationName",
              header: "Location",
              render: (r) =>
                r.locationName ||
                (r.locationId != null ? (locName.get(String(r.locationId)) ?? "—") : "—"),
            },
            { key: "managerName", header: "Manager", render: (r) => r.managerName || "—" },
            { key: "startDate", header: "Start", render: (r) => fmtDate(r.startDate) },
            { key: "status", header: "Status", sortable: true, render: (r) => <StatusPill status={r.status} /> },
          ]}
        />
      </div>
    </div>
  );
}
