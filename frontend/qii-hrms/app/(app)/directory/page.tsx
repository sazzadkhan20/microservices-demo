"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useAuth } from "@/components/auth/AuthProvider";
import { PageHeader } from "@/components/ui/PageHeader";
import { EmptyState, ErrorNote, Loading, NoAccess } from "@/components/ui/QueryState";
import { StatusPill } from "@/components/ui/StatusPill";
import { Tabs } from "@/components/ui/Tabs";
import { api, apiFetchPage } from "@/lib/api/client";
import { initials, str } from "@/lib/format";
import { useDebounced } from "@/lib/hooks";
import { employeeSchema, type Employee } from "@/lib/schemas/employee";

const TABS = [
  { id: "list", label: "Directory" },
  { id: "chart", label: "Org chart" },
];

const INACTIVE = new Set(["inactive", "resigned", "terminated"]);

const text = (v: unknown) => (typeof v === "string" ? v : "");

export default function DirectoryPage() {
  const { can } = useAuth();
  const [tab, setTab] = useState("list");
  const [q, setQ] = useState("");
  const [team, setTeam] = useState("");
  const [loc, setLoc] = useState("");
  const [showInactive, setShowInactive] = useState(false);
  const dq = useDebounced(q, 200);

  const allowed = can("employees", "view") || can("org", "view");
  const canOpen = can("employees", "view");

  const staff = useQuery({
    queryKey: ["directory-page"],
    queryFn: ({ signal }) =>
      apiFetchPage(api.hrms("employees"), employeeSchema, { page: 1, size: 500, sort: "fullName,asc" }, { signal }),
    enabled: allowed,
    staleTime: 60_000,
  });

  const people = useMemo(
    () => (staff.data?.content ?? []).filter((e) => showInactive || !INACTIVE.has(String(e.status).toLowerCase())),
    [staff.data, showInactive],
  );

  const teams = useMemo(
    () => [...new Set(people.map((e) => text(e.teamName)).filter(Boolean))].sort(),
    [people],
  );
  const locations = useMemo(
    () => [...new Set(people.map((e) => text(e.locationName)).filter(Boolean))].sort(),
    [people],
  );

  const shown = useMemo(() => {
    const needle = dq.trim().toLowerCase();
    return people.filter((e) => {
      if (team && text(e.teamName) !== team) return false;
      if (loc && text(e.locationName) !== loc) return false;
      if (!needle) return true;
      return [e.fullName, e.preferredName, e.jobTitle, e.email, e.employeeCode, text(e.teamName), text(e.departmentName)]
        .filter(Boolean)
        .some((v) => String(v).toLowerCase().includes(needle));
    });
  }, [people, dq, team, loc]);

  if (!allowed) {
    return (
      <div className="stack">
        <PageHeader title="Directory" />
        <NoAccess what="the directory" />
      </div>
    );
  }

  return (
    <div className="stack">
      <PageHeader
        title="Directory & org chart"
        subtitle="Everyone at your company. The org chart is drawn from reporting lines."
      />
      <Tabs tabs={TABS} value={tab} onChange={setTab} />
      <ErrorNote error={staff.error} />
      {staff.isLoading ? <Loading /> : null}

      {tab === "list" ? (
        <>
          <div className="filters">
            <label className="field wide">
              <span>Search</span>
              <input
                type="search"
                placeholder="Search name, title, team or email"
                value={q}
                onChange={(e) => setQ(e.target.value)}
              />
            </label>
            <label className="field">
              <span>Team</span>
              <select value={team} onChange={(e) => setTeam(e.target.value)}>
                <option value="">All teams</option>
                {teams.map((t) => (
                  <option key={t}>{t}</option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Location</span>
              <select value={loc} onChange={(e) => setLoc(e.target.value)}>
                <option value="">All locations</option>
                {locations.map((l) => (
                  <option key={l}>{l}</option>
                ))}
              </select>
            </label>
            <label className="row small muted" style={{ paddingBottom: 8 }}>
              <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
              Show former staff
            </label>
          </div>
          <p className="muted small">
            {shown.length} of {people.length} people
          </p>
          {!staff.isLoading && shown.length === 0 ? (
            <div className="card">
              <EmptyState>No one matches your search.</EmptyState>
            </div>
          ) : (
            <div className="grid g3">
              {shown.map((e) => (
                <PersonCard key={e.id} e={e} href={canOpen ? `/employees/${e.id}` : undefined} />
              ))}
            </div>
          )}
        </>
      ) : (
        <OrgChart people={people} canOpen={canOpen} />
      )}
    </div>
  );
}

function PersonCard({ e, href }: { e: Employee; href?: string }) {
  const body = (
    <div className="card stack" style={{ height: "100%" }}>
      <div className="row" style={{ gap: 12, flexWrap: "nowrap" }}>
        <div
          aria-hidden
          style={{
            width: 42,
            height: 42,
            flex: "none",
            borderRadius: "50%",
            background: "var(--accent-soft)",
            color: "var(--accent)",
            display: "grid",
            placeItems: "center",
            fontWeight: 600,
          }}
        >
          {initials(e.fullName)}
        </div>
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

function OrgChart({ people, canOpen }: { people: Employee[]; canOpen: boolean }) {
  const { roots, kids } = useMemo(() => {
    const ids = new Set(people.map((p) => String(p.id)));
    const kids = new Map<string, Employee[]>();
    const roots: Employee[] = [];
    for (const p of people) {
      const m = p.managerId == null ? null : String(p.managerId);
      if (m && ids.has(m) && m !== String(p.id)) {
        kids.set(m, [...(kids.get(m) ?? []), p]);
      } else {
        roots.push(p);
      }
    }
    return { roots, kids };
  }, [people]);

  if (people.length === 0) {
    return (
      <div className="card">
        <EmptyState>No employees to draw.</EmptyState>
      </div>
    );
  }

  const node = (e: Employee, depth: number): React.ReactNode => {
    if (depth > 12) return null;
    const children = kids.get(String(e.id)) ?? [];
    return (
      <li key={e.id}>
        <span className="node">
          {canOpen ? <Link href={`/employees/${e.id}`}>{e.fullName}</Link> : <b>{e.fullName}</b>}
          <span className="small muted">
            {str(e.jobTitle)}
            {text(e.locationName) ? ` · ${text(e.locationName)}` : ""}
          </span>
        </span>
        {children.length ? <ul>{children.map((c) => node(c, depth + 1))}</ul> : null}
      </li>
    );
  };

  return (
    <section className="card tree" style={{ overflowX: "auto" }}>
      <ul>{roots.map((r) => node(r, 0))}</ul>
    </section>
  );
}
