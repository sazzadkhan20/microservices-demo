"use client";

import Link from "next/link";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/auth/AuthProvider";
import { EmployeePhoto } from "@/components/employees/EmployeePhoto";
import { OrgChartTree, type OrgChartNode } from "@/components/org/OrgChartTree";
import { PageHeader } from "@/components/ui/PageHeader";
import { EmptyState, ErrorNote, Loading } from "@/components/ui/QueryState";
import { StatusPill } from "@/components/ui/StatusPill";
import { Tabs } from "@/components/ui/Tabs";
import { api, apiFetch, apiFetchPage } from "@/lib/api/client";
import { useLocations, useTeams } from "@/lib/api/lookups";
import { canOpenEmployeeRecord } from "@/lib/auth/permissions";
import { fmtNumber, str } from "@/lib/format";
import { useDebounced } from "@/lib/hooks";
import { employeeSchema, type Employee } from "@/lib/schemas/employee";

const TABS = [
  { id: "list", label: "Directory" },
  { id: "chart", label: "Org chart" },
];

const PAGE_SIZES = [12, 24, 48];

const text = (v: unknown) => (typeof v === "string" ? v : "");

export default function DirectoryPage() {
  const { can, me, canAccessRoute } = useAuth();
  const router = useRouter();
  const [tab, setTab] = useState("list");
  const [q, setQ] = useState("");
  const [teamId, setTeamId] = useState("");
  const [locationId, setLocationId] = useState("");
  const [showInactive, setShowInactive] = useState(false);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(24);
  const dq = useDebounced(q, 300);

  const allowed = can("employees", "view") || can("org", "view") || can("directory", "view");
  const openEmployee = (id: string | number) =>
    canOpenEmployeeRecord(me.employeeId, id, me.permissions, me.scopes);

  useEffect(() => {
    if (!allowed && !canAccessRoute("/directory")) {
      router.replace("/");
    }
  }, [allowed, canAccessRoute, router]);

  const teams = useTeams(allowed && tab === "list");
  const locations = useLocations(allowed && tab === "list");

  // Reset to page 1 when filters change (derive without an effect).
  const filterSig = `${dq}|${teamId}|${locationId}|${showInactive}|${size}`;
  const [lastSig, setLastSig] = useState(filterSig);
  if (lastSig !== filterSig) {
    setLastSig(filterSig);
    if (page !== 1) setPage(1);
  }

  const staff = useQuery({
    queryKey: ["directory-page", page, size, dq, teamId, locationId, showInactive],
    queryFn: ({ signal }) =>
      apiFetchPage(
        api.hrms("employees"),
        employeeSchema,
        {
          page,
          size,
          sort: "fullName,asc",
          q: dq.trim() || undefined,
          filters: {
            ...(teamId ? { teamId } : {}),
            ...(locationId ? { locationId } : {}),
            ...(!showInactive ? { active: "true" } : {}),
          },
        },
        { signal },
      ),
    enabled: allowed && tab === "list",
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  });

  const chart = useQuery({
    queryKey: ["org-chart", showInactive],
    queryFn: ({ signal }) =>
      apiFetch<OrgChartNode[]>(api.hrms("org/chart"), {
        query: { includeInactive: showInactive },
        signal,
      }),
    enabled: allowed && tab === "chart",
    staleTime: 30_000,
  });

  const people = staff.data?.content ?? [];
  const totalElements = Number(staff.data?.totalElements ?? 0);
  const totalPages = Math.max(1, Number(staff.data?.totalPages ?? 1) || 1);
  const pageFrom = totalElements === 0 ? 0 : (page - 1) * size + 1;
  const pageTo = Math.min(page * size, totalElements);

  const teamOptions = useMemo(
    () => [...(teams.data ?? [])].sort((a, b) => String(a.name ?? "").localeCompare(String(b.name ?? ""))),
    [teams.data],
  );
  const locationOptions = useMemo(
    () =>
      [...(locations.data ?? [])].sort((a, b) => String(a.name ?? "").localeCompare(String(b.name ?? ""))),
    [locations.data],
  );

  if (!allowed) {
    return null;
  }

  return (
    <div className="stack">
      <PageHeader
        title="Directory & org chart"
        subtitle="Everyone at your company. The org chart is drawn from reporting lines."
      />
      <Tabs tabs={TABS} value={tab} onChange={setTab} />

      {tab === "list" ? (
        <>
          <ErrorNote error={staff.error} />
          <div className="filters">
            <label className="field wide">
              <span>Search</span>
              <input
                type="search"
                placeholder="Search name, code or email"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                aria-label="Search directory"
              />
            </label>
            <label className="field">
              <span>Team</span>
              <select value={teamId} onChange={(e) => setTeamId(e.target.value)} aria-label="Filter by team">
                <option value="">All teams</option>
                {teamOptions.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Location</span>
              <select
                value={locationId}
                onChange={(e) => setLocationId(e.target.value)}
                aria-label="Filter by location"
              >
                <option value="">All locations</option>
                {locationOptions.map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Per page</span>
              <select
                value={size}
                onChange={(e) => setSize(Number(e.target.value))}
                aria-label="Page size"
              >
                {PAGE_SIZES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </label>
            <label className="row small muted" style={{ paddingBottom: 8 }}>
              <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
              Show former staff
            </label>
          </div>

          {staff.isLoading && !staff.data ? <Loading /> : null}

          <div className="row between" style={{ flexWrap: "wrap", gap: 8 }}>
            <p className="muted small" style={{ margin: 0 }}>
              {staff.isFetching && staff.data ? "Updating… · " : null}
              {totalElements === 0
                ? "No people"
                : `Showing ${fmtNumber(pageFrom, 0)}–${fmtNumber(pageTo, 0)} of ${fmtNumber(totalElements, 0)}`}
              {dq.trim() ? ` · “${dq.trim()}”` : ""}
            </p>
            <div className="row" style={{ gap: 6 }}>
              <button
                type="button"
                className="btn sm"
                disabled={page <= 1 || staff.isFetching}
                onClick={() => setPage((p) => Math.max(1, p - 1))}
              >
                Previous
              </button>
              <span className="mono small" style={{ alignSelf: "center", minWidth: 72, textAlign: "center" }}>
                {page} / {totalPages}
              </span>
              <button
                type="button"
                className="btn sm"
                disabled={page >= totalPages || staff.isFetching}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          </div>

          {!staff.isLoading && people.length === 0 ? (
            <div className="card">
              <EmptyState>No one matches your search.</EmptyState>
            </div>
          ) : (
            <div className="grid g3" style={{ opacity: staff.isFetching ? 0.75 : 1, transition: "opacity .15s" }}>
              {people.map((e) => (
                <PersonCard
                  key={e.id}
                  e={e}
                  href={openEmployee(e.id) ? `/employees/${e.id}` : undefined}
                />
              ))}
            </div>
          )}

          {totalElements > size ? (
            <div className="row" style={{ justifyContent: "center", gap: 6 }}>
              <button
                type="button"
                className="btn sm"
                disabled={page <= 1 || staff.isFetching}
                onClick={() => setPage((p) => Math.max(1, p - 1))}
              >
                Previous
              </button>
              <span className="mono small" style={{ alignSelf: "center" }}>
                Page {page} of {totalPages}
              </span>
              <button
                type="button"
                className="btn sm"
                disabled={page >= totalPages || staff.isFetching}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          ) : null}
        </>
      ) : (
        <>
          <label className="row small muted">
            <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
            Show former staff
          </label>
          <ErrorNote error={chart.error} />
          {chart.isLoading ? <Loading /> : null}
          {!chart.isLoading ? (
            <OrgChartTree
              roots={Array.isArray(chart.data) ? chart.data : []}
              canOpenId={openEmployee}
              emptyMessage="No reporting lines to draw yet. Assign managers on employee records."
            />
          ) : null}
        </>
      )}
    </div>
  );
}

function PersonCard({ e, href }: { e: Employee; href?: string }) {
  const photoFileId = (e.photoFileId ??
    (e as Employee & { customFields?: Record<string, unknown> }).customFields?.photoFileId) as
    | string
    | number
    | null
    | undefined;
  const body = (
    <div className="card stack" style={{ height: "100%" }}>
      <div className="row" style={{ gap: 12, flexWrap: "nowrap" }}>
        <EmployeePhoto photoFileId={photoFileId} fullName={e.fullName} size={42} />
        <div style={{ minWidth: 0 }}>
          <b style={{ display: "block", overflow: "hidden", textOverflow: "ellipsis" }}>{e.fullName}</b>
          <span className="muted small">{str(e.jobTitle)}</span>
        </div>
      </div>
      <div className="small stack" style={{ gap: 4 }}>
        <span>
          <span className="muted">Team · </span>
          {str(text(e.teamName) || e.departmentName)}
        </span>
        <span>
          <span className="muted">Location · </span>
          {str(text(e.locationName))}
        </span>
        <span>
          <span className="muted">Reports to · </span>
          {str(e.managerName)}
        </span>
        <span style={{ overflowWrap: "anywhere" }}>
          <span className="muted">Email · </span>
          {str(e.email)}
        </span>
      </div>
      {String(e.status).toLowerCase() !== "active" ? (
        <div>
          <StatusPill status={e.status} />
        </div>
      ) : null}
    </div>
  );
  return href ? (
    <Link href={href} style={{ color: "inherit", textDecoration: "none" }}>
      {body}
    </Link>
  ) : (
    body
  );
}
