export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null;
  return <div className="notice bad">{error instanceof Error ? error.message : String(error)}</div>;
}

export function Loading({ text = "Loading…" }: { text?: string }) {
  return <p className="muted">{text}</p>;
}

/** Page-level permission denial. */
export function NoAccess({ what = "this page" }: { what?: string }) {
  return (
    <div className="card empty">
      <h2 style={{ marginBottom: 6 }}>No access</h2>
      <p className="muted">Your roles don&apos;t include permission to view {what}.</p>
    </div>
  );
}

/** Empty state block inside a card. */
export function EmptyState({ children }: { children: React.ReactNode }) {
  return <div className="empty">{children}</div>;
}
