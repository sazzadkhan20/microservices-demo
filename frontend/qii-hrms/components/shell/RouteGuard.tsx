"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useAuth } from "@/components/auth/AuthProvider";

/**
 * FR-ACC-04/05 — never mount a page the user cannot open.
 * Nav already hides links; this blocks deep-links by replacing to Home
 * (no in-page Forbidden after navigation).
 */
export function RouteGuard({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { canAccessRoute } = useAuth();
  const allowed = canAccessRoute(pathname);

  useEffect(() => {
    if (!allowed) {
      router.replace("/");
    }
  }, [allowed, router, pathname]);

  if (!allowed) {
    return null;
  }
  return <>{children}</>;
}
