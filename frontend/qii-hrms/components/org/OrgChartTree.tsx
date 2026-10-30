"use client";

import Link from "next/link";
import { useMemo, type ReactNode } from "react";
import { EmptyState } from "@/components/ui/QueryState";
import { str } from "@/lib/format";

export type OrgChartNode = {
  id: number | string;
  fullName?: string | null;
  jobTitle?: string | null;
  locationName?: string | null;
  departmentName?: string | null;
  status?: string | null;
  managerId?: number | string | null;
  children?: OrgChartNode[];
};

type Props = {
  roots: OrgChartNode[];
  canOpen?: boolean;
  emptyMessage?: string;
};

/** Nested tree UI — same structure as prototype `.tree` / reporting-line org chart. */
export function OrgChartTree({ roots, canOpen = true, emptyMessage = "No employees to draw." }: Props) {
  const count = useMemo(() => countNodes(roots), [roots]);

  if (!roots.length) {
    return (
      <div className="card">
        <EmptyState>{emptyMessage}</EmptyState>
      </div>
    );
  }

  const render = (n: OrgChartNode, depth: number): ReactNode => {
    if (depth > 64) return null;
    const kids = Array.isArray(n.children) ? n.children : [];
    const title = str(n.jobTitle);
    const loc = str(n.locationName);
    const meta = [title, loc].filter(Boolean).join(" · ");
    return (
      <li key={String(n.id)}>
        <span className="node">
          {canOpen ? (
            <Link href={`/employees/${n.id}`}>{str(n.fullName) || `Employee ${n.id}`}</Link>
          ) : (
            <b>{str(n.fullName) || `Employee ${n.id}`}</b>
          )}
          {meta ? <span className="small muted">{meta}</span> : null}
        </span>
        {kids.length ? <ul>{kids.map((c) => render(c, depth + 1))}</ul> : null}
      </li>
    );
  };

  return (
    <section className="card tree stack" style={{ overflowX: "auto" }}>
      <p className="muted small" style={{ margin: 0 }}>
        {count} people · drawn from reporting lines
      </p>
      <ul>{roots.map((r) => render(r, 0))}</ul>
    </section>
  );
}

function countNodes(nodes: OrgChartNode[]): number {
  let n = 0;
  const walk = (list: OrgChartNode[]) => {
    for (const node of list) {
      n += 1;
      if (Array.isArray(node.children) && node.children.length) walk(node.children);
    }
  };
  walk(nodes);
  return n;
}
