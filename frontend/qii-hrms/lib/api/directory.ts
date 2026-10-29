"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, apiFetchPage } from "@/lib/api/client";
import {
  directoryEmployeeSchema,
  holidayRowSchema,
  taskRowSchema,
  type DirectoryEmployee,
  type TaskRow,
} from "@/lib/schemas/admin";

const STALE = 60_000;

/** Employee directory (page 1, size 500) with id -> name helpers. Needs employees:view. */
export function useDirectory(enabled = true) {
  const q = useQuery({
    queryKey: ["directory", "employees"],
    queryFn: ({ signal }) => apiFetchPage(api.hrms("employees"), directoryEmployeeSchema, { page: 1, size: 500 }, { signal }),
    enabled,
    staleTime: STALE,
  });
  const employees: DirectoryEmployee[] = useMemo(() => q.data?.content ?? [], [q.data]);
  const byId = useMemo(() => {
    const m = new Map<string, DirectoryEmployee>();
    for (const e of employees) m.set(String(e.id), e);
    return m;
  }, [employees]);
  const nameOf = (id: unknown): string => {
    if (id === null || id === undefined || id === "") return "—";
    return byId.get(String(id))?.fullName || `#${id}`;
  };
  return { ...q, employees, byId, nameOf };
}

/** Tasks (size 500) with id -> title helper. */
export function useTaskDirectory(enabled = true) {
  const q = useQuery({
    queryKey: ["directory", "tasks"],
    queryFn: ({ signal }) => apiFetchPage(api.hrms("tasks"), taskRowSchema, { page: 1, size: 500 }, { signal }),
    enabled,
    staleTime: STALE,
  });
  const tasks: TaskRow[] = useMemo(() => q.data?.content ?? [], [q.data]);
  const titleOf = (id: unknown): string => {
    if (id === null || id === undefined || id === "") return "—";
    return tasks.find((t) => String(t.id) === String(id))?.title || `Task #${id}`;
  };
  return { ...q, tasks, titleOf };
}

/** All holidays for a year across locations (size 500). */
export function useHolidaysForYear(year: number, enabled = true) {
  return useQuery({
    queryKey: ["holidays-year", year],
    queryFn: ({ signal }) =>
      apiFetchPage(api.hrms("holidays"), holidayRowSchema, { page: 1, size: 500 }, { signal, query: { year } }),
    enabled,
    staleTime: STALE,
  });
}
