"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { useAuth } from "@/components/auth/AuthProvider";

export function RouteGuard({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { canAccessRoute, me } = useAuth();
  if (!canAccessRoute(pathname)) {
    return (
      <div className="card stack">
        <h2>No access</h2>
        <p className="muted">
          {me.name}&apos;s roles do not grant this page. An admin can change it in Roles &amp; permissions.
        </p>
        <div>
          <Link href="/" className="btn primary">
            Go to Home
          </Link>
        </div>
      </div>
    );
  }
  return <>{children}</>;
}
