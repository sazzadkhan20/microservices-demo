import type { ReactNode } from "react";

/** Honest empty / unavailable state: explains what is missing instead of faking data. */
export function EmptyState({
  title,
  children,
  tone,
  action,
}: {
  title: string;
  children?: ReactNode;
  tone?: "warn" | "bad";
  action?: ReactNode;
}) {
  return (
    <div className="card stack" style={{ alignItems: "flex-start" }}>
      <h2 style={{ marginBottom: 0 }}>{title}</h2>
      {children ? <div className={`note${tone ? ` ${tone}` : ""}`} style={{ maxWidth: "70ch" }}>{children}</div> : null}
      {action}
    </div>
  );
}
