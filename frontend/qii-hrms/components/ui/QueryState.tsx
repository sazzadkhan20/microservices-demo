import type { ReactNode } from "react";
import { ApiError } from "@/lib/api/client";

/** Prefer hiding UI over raw Forbidden text (FR-ACC-04/05). */
export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null;
  if (error instanceof ApiError && error.status === 403) {
    return null;
  }
  const msg = error instanceof Error ? error.message : String(error);
  if (/forbidden/i.test(msg)) {
    return null;
  }
  return <div className="notice bad">{msg}</div>;
}

export function Loading({ text = "Loading…" }: { text?: string }) {
  return <p className="muted">{text}</p>;
}

/**
 * Intentionally blank — callers should hide the section or let RouteGuard redirect.
 * Kept so existing imports compile without flashing a Forbidden card.
 */
export function NoAccess(_props: { what?: string }) {
  return null;
}

/** Empty state block inside a card. */
export function EmptyState({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}
