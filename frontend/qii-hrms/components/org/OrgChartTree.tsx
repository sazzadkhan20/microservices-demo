"use client";

import Link from "next/link";
import { useMemo, useState, type ReactNode } from "react";
import { EmptyState } from "@/components/ui/QueryState";
import { str } from "@/lib/format";

export type OrgChartNode = {
  id: number | string;
  employeeCode?: string | null;
  fullName?: string | null;
  jobTitle?: string | null;
  locationName?: string | null;
  departmentName?: string | null;
  status?: string | null;
  managerId?: number | string | null;
  unresolvedManager?: boolean | null;
  children?: OrgChartNode[];
};

type Props = {
  roots: OrgChartNode[];
  canOpen?: boolean;
  emptyMessage?: string;
};

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

function countNodes(nodes: OrgChartNode[]): number {
  let n = 0;
  const walk = (list: OrgChartNode[]) => {
    for (const node of list) {
      n += 1;
      if (node.children?.length) walk(node.children);
    }
  };
  walk(nodes);
  return n;
}

function collectIds(nodes: OrgChartNode[], out: string[] = []): string[] {
  for (const n of nodes) {
    out.push(String(n.id));
    if (n.children?.length) collectIds(n.children, out);
  }
  return out;
}

function matchesQuery(n: OrgChartNode, q: string): boolean {
  if (!q) return true;
  const hay = [n.fullName, n.jobTitle, n.departmentName, n.locationName, n.employeeCode]
    .map((v) => String(v ?? "").toLowerCase())
    .join(" ");
  return hay.includes(q);
}

/** Subtree kept if node or any descendant matches. */
function filterTree(nodes: OrgChartNode[], q: string): OrgChartNode[] {
  if (!q) return nodes;
  const out: OrgChartNode[] = [];
  for (const n of nodes) {
    const kids = filterTree(n.children ?? [], q);
    if (matchesQuery(n, q) || kids.length) {
      out.push({ ...n, children: kids });
    }
  }
  return out;
}

/**
 * Org chart drawn from reporting lines (manager → reports).
 * Nested tree with expand/collapse, search, and person cards.
 */
export function OrgChartTree({ roots, canOpen = true, emptyMessage = "No employees to draw." }: Props) {
  const [query, setQuery] = useState("");
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());

  const total = useMemo(() => countNodes(roots), [roots]);
  const filtered = useMemo(() => filterTree(roots, query.trim().toLowerCase()), [roots, query]);
  const visible = useMemo(() => countNodes(filtered), [filtered]);
  const rootCount = filtered.length;

  if (!roots.length) {
    return (
      <div className="card">
        <EmptyState>{emptyMessage}</EmptyState>
      </div>
    );
  }

  function toggle(id: string) {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function expandAll() {
    setCollapsed(new Set());
  }

  function collapseAll() {
    setCollapsed(new Set(collectIds(filtered)));
  }

  const render = (n: OrgChartNode, depth: number): ReactNode => {
    if (depth > 64) return null;
    const id = String(n.id);
    const kids = Array.isArray(n.children) ? n.children : [];
    const hasKids = kids.length > 0;
    const isCollapsed = collapsed.has(id);
    const name = str(n.fullName) || `Employee ${n.id}`;
    const title = str(n.jobTitle);
    const dept = str(n.departmentName);
    const loc = str(n.locationName);
    const code = str(n.employeeCode);
    const isRoot = depth === 0 && (n.managerId == null || n.managerId === "");

    return (
      <li key={id} className={`org-li${isRoot ? " org-li-root" : ""}`}>
        <div className={`org-card${isRoot ? " org-card-root" : ""}${n.unresolvedManager ? " org-card-warn" : ""}`}>
          {hasKids ? (
            <button
              type="button"
              className="org-toggle"
              aria-expanded={!isCollapsed}
              aria-label={isCollapsed ? `Expand ${name}` : `Collapse ${name}`}
              onClick={() => toggle(id)}
            >
              {isCollapsed ? "+" : "−"}
            </button>
          ) : (
            <span className="org-toggle-spacer" aria-hidden />
          )}

          <span className="org-avatar" aria-hidden>
            {initials(name)}
          </span>

          <div className="org-body">
            <span className="org-title-row">
              {canOpen ? (
                <Link href={`/employees/${n.id}`} className="org-name">
                  {name}
                </Link>
              ) : (
                <span className="org-name">{name}</span>
              )}
              {code ? <span className="org-code mono">{code}</span> : null}
              {isRoot ? <span className="pill acc">Root</span> : null}
              {n.unresolvedManager ? <span className="pill warn">No mgr in chart</span> : null}
            </span>
            {(title || dept || loc) ? (
              <span className="org-meta">
                {title ? <span>{title}</span> : null}
                {dept ? <span>{dept}</span> : null}
                {loc ? <span>{loc}</span> : null}
              </span>
            ) : null}
          </div>

          {hasKids ? (
            <span className="org-reports pill info" title="Direct reports">
              {kids.length}
            </span>
          ) : null}
        </div>

        {hasKids && !isCollapsed ? <ul className="org-children">{kids.map((c) => render(c, depth + 1))}</ul> : null}
      </li>
    );
  };

  return (
    <section className="card org-chart stack">
      <div className="org-toolbar">
        <div className="org-toolbar-copy">
          <h2>Reporting chart</h2>
          <p className="muted small">
            {visible}
            {query ? ` of ${total}` : ""} people · {rootCount} root
            {rootCount === 1 ? "" : "s"} · built from manager reporting lines
          </p>
        </div>
        <div className="org-toolbar-actions">
          <input
            type="search"
            className="org-search"
            placeholder="Search name, title, dept…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Filter org chart"
          />
          <button type="button" className="btn" onClick={expandAll}>
            Expand
          </button>
          <button type="button" className="btn" onClick={collapseAll}>
            Collapse
          </button>
        </div>
      </div>

      {filtered.length === 0 ? (
        <EmptyState>No people match “{query.trim()}”.</EmptyState>
      ) : (
        <ul className="org-roots">{filtered.map((r) => render(r, 0))}</ul>
      )}
    </section>
  );
}
