"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/components/auth/AuthProvider";
import { api, apiFetch } from "@/lib/api/client";
import { initials } from "@/lib/format";
import styles from "./AppShell.module.css";

export function AppShell({ children }: { children: ReactNode }) {
  const { me, nav, signOut, can } = useAuth();
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  const unread = useQuery({
    queryKey: ["notifications-unread"],
    queryFn: ({ signal }) =>
      apiFetch<{ unreadCount?: number }>(api.notifications("unread-count"), { signal }),
    refetchInterval: 60_000,
    retry: false,
  });
  const unreadCount = Number(unread.data?.unreadCount ?? 0);

  const canApprove = can("leave", "approve");
  const pending = useQuery({
    queryKey: ["approvals-pending-count"],
    queryFn: ({ signal }) => apiFetch<unknown[]>(api.hrms("approvals/pending"), { signal }),
    enabled: canApprove,
    refetchInterval: 60_000,
    retry: false,
  });
  const pendingCount = Array.isArray(pending.data) ? pending.data.length : 0;

  // The most specific nav item wins, so /leave/calendar does not also light up /leave.
  const allHrefs = nav.flatMap((g) => g.items.map((i) => i.href));
  const activeHref = allHrefs
    .filter((h) => pathname === h || (h !== "/" && pathname.startsWith(`${h}/`)))
    .sort((a, b) => b.length - a.length)[0];

  return (
    <div className={styles.shell}>
      <aside className={`${styles.rail} ${open ? styles.open : ""}`}>
        <div className={styles.brand}>
          <div className={styles.mark}>Q</div>
          <div>
            <b>Qii HRMS</b>
            <span>Creative · v1</span>
          </div>
        </div>
        <nav className={styles.nav} aria-label="Primary">
          {nav.map((g) => (
            <div key={g.group} className={styles.grp}>
              <span className="label">{g.group}</span>
              {g.items.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className={activeHref === item.href ? styles.on : undefined}
                  aria-current={activeHref === item.href ? "page" : undefined}
                  onClick={() => setOpen(false)}
                >
                  <span>{item.label}</span>
                  {item.href === "/approvals" && pendingCount > 0 ? (
                    <span className={styles.count}>{pendingCount}</span>
                  ) : null}
                </Link>
              ))}
            </div>
          ))}
        </nav>
      </aside>
      {open ? <div className={styles.scrim} onClick={() => setOpen(false)} aria-hidden /> : null}
      <div className={styles.main}>
        <header className={styles.top}>
          <button
            type="button"
            className={`btn sm ${styles.menuBtn}`}
            onClick={() => setOpen((v) => !v)}
            aria-label="Toggle navigation"
          >
            ☰ Menu
          </button>
          <span className={styles.grow} />
          <Link href="/notifications" className={`btn sm ${styles.bell}`} aria-label="Notifications">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
              <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
              <path d="M13.7 21a2 2 0 0 1-3.4 0" />
            </svg>
            {unreadCount > 0 ? <span className={styles.dot}>{unreadCount > 99 ? "99+" : unreadCount}</span> : null}
          </Link>
          <Link href="/profile" className={styles.who} title="Open profile">
            <span className={styles.avatar}>{initials(me.name)}</span>
            <span className={styles.whoText}>
              <strong>{me.name}</strong>
              <span className="muted small">{(me.roles || []).join(", ") || "Employee"}</span>
            </span>
          </Link>
          <button type="button" className="btn sm" onClick={() => void signOut()}>
            Sign out
          </button>
        </header>
        <main className={styles.content}>{children}</main>
      </div>
    </div>
  );
}
