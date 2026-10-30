"use client";

import { createContext, useCallback, useContext, useMemo, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import { meSchema, type Me } from "@/lib/schemas/me";
import {
  hasPermission,
  satisfies,
  toPermissionSet,
  type AccessRule,
  type Action,
  type Feature,
} from "@/lib/auth/permissions";
import { canAccessRoute, visibleNav, type NavSection } from "@/lib/auth/routes";

type AuthContextValue = {
  me: Me;
  can: (feature: Feature | string, action: Action | string) => boolean;
  canAny: (rule: AccessRule) => boolean;
  canAccessRoute: (pathname: string) => boolean;
  nav: NavSection[];
  refresh: () => Promise<void>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export const ME_QUERY_KEY = ["me"] as const;

export function AuthProvider({ initialMe, children }: { initialMe: Me; children: ReactNode }) {
  const qc = useQueryClient();

  const { data } = useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: ({ signal }) => apiFetch("/api/auth/me", { schema: meSchema, signal }),
    initialData: initialMe,
    staleTime: 60_000,
    refetchOnWindowFocus: true,
  });
  const me = data ?? initialMe;

  const perms = useMemo(() => toPermissionSet(me.permissions), [me.permissions]);

  const refresh = useCallback(async () => {
    await qc.invalidateQueries({ queryKey: ME_QUERY_KEY });
  }, [qc]);

  const signOut = useCallback(async () => {
    try {
      await apiFetch("/api/auth/logout", { method: "POST" });
    } finally {
      qc.clear();
      // Hard navigation drops all client state and re-runs server auth checks.
      window.location.assign("/login");
    }
  }, [qc]);

  const value = useMemo<AuthContextValue>(
    () => ({
      me,
      can: (feature, action) => hasPermission(perms, feature, action),
      canAny: (rule) => satisfies(perms, rule),
      canAccessRoute: (pathname) => canAccessRoute(perms, pathname, me.scopes),
      nav: visibleNav(perms, me.scopes),
      refresh,
      signOut,
    }),
    [me, perms, refresh, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside <AuthProvider>");
  return ctx;
}

/** Convenience: `const canApprove = useCan("leave", "approve")`. */
export function useCan(feature: Feature | string, action: Action | string): boolean {
  return useAuth().can(feature, action);
}
