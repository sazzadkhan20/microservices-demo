"use client";

import { useQuery } from "@tanstack/react-query";
import { api, apiFetch, listParams } from "@/lib/api/client";

export type Rec = Record<string, unknown>;

/** Accepts a bare array or a PageResponse envelope and returns the rows. */
export function rowsOf<T = Rec>(data: unknown): T[] {
  if (Array.isArray(data)) return data as T[];
  if (data && typeof data === "object" && Array.isArray((data as { content?: unknown }).content)) {
    return (data as { content: T[] }).content;
  }
  return [];
}

export type Dept = { id: number; code?: string | null; name: string };
export type Desig = { id: number; code?: string | null; name: string; approverRole?: string | null };
export type Team = {
  id: number;
  code?: string | null;
  name: string;
  departmentId?: number | null;
  leadEmployeeId?: number | null;
  maxOnLeave?: number | null;
  clashAction?: string | null;
};
export type Loc = {
  id: number;
  code?: string | null;
  name: string;
  country?: string | null;
  timeZone?: string | null;
  defaultCurrency?: string | null;
};
export type Emp = {
  id: number;
  employeeCode?: string | null;
  fullName?: string | null;
  email?: string | null;
  jobTitle?: string | null;
  status?: string | null;
  departmentId?: number | null;
  teamId?: number | null;
  locationId?: number | null;
  managerId?: number | null;
  managerName?: string | null;
};
export type Role = {
  id: number;
  code: string;
  name?: string | null;
  description?: string | null;
  systemRole?: boolean;
  active?: boolean;
  /** Config-driven policy flags from tenant DB (not hard-coded role names). */
  protectFromOffboard?: boolean;
  immutablePermissions?: boolean;
  grantsWildcard?: boolean;
  minHolders?: number;
  assignableBy?: string | null;
  holderCount?: number;
  permissions?: {
    feature: string;
    action: string;
    scope?: string | null;
    scoped?: boolean;
    label?: string | null;
    key?: string;
  }[];
};
export type LeaveType = { id?: number; code: string; name: string; paid?: boolean };

const STALE = 5 * 60_000;

function useList<T>(key: string, path: string, opts: { enabled?: boolean; paged?: boolean } = {}) {
  return useQuery({
    queryKey: ["lookup", key],
    queryFn: async ({ signal }) => {
      const data = opts.paged
        ? await apiFetch(path, { query: listParams({ page: 1, size: 500 }), signal })
        : await apiFetch(path, { signal });
      return rowsOf<T>(data);
    },
    enabled: opts.enabled ?? true,
    staleTime: STALE,
  });
}

export const useDepartments = (enabled = true) => useList<Dept>("departments", api.hrms("org/departments"), { enabled });
export const useDesignations = (enabled = true) =>
  useList<Desig>("designations", api.hrms("org/designations"), { enabled });
export const useTeams = (enabled = true) => useList<Team>("teams", api.hrms("teams"), { enabled, paged: true });
export const useLocations = (enabled = true) =>
  useList<Loc>("locations", api.hrms("locations"), { enabled, paged: true });
export const useEmployeesLookup = (enabled = true) =>
  useList<Emp>("employees", api.hrms("employees"), { enabled, paged: true });
export const useRoles = (enabled = true) => useList<Role>("roles", api.hrms("roles"), { enabled });
export const useLeaveTypes = (enabled = true) =>
  useList<LeaveType>("leave-types", api.hrms("leave-types"), { enabled });

/** id -> label maps for rendering names in tables. */
export function byId<T extends { id: number | string }>(rows: T[] | undefined, label: (r: T) => string) {
  const m = new Map<string, string>();
  for (const r of rows ?? []) m.set(String(r.id), label(r));
  return m;
}
