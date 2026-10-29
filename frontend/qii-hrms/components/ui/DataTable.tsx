"use client";

import { useMemo, useState, type ReactNode } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import { pageResponseSchema, type PageResponse } from "@/lib/schemas/page";
import { z } from "zod";

export type Column<T> = {
  key: string;
  header: string;
  render?: (row: T) => ReactNode;
  sortable?: boolean;
  className?: string;
};

type Props<T> = {
  queryKey: string;
  path: string; // e.g. /api/hrms/employees
  columns: Column<T>[];
  itemSchema: z.ZodType<T>;
  defaultSort?: string;
  /** Sent as filter.<key>=<value>; empty values are skipped. */
  filters?: Record<string, string>;
  /** Plain extra query params (e.g. year=2026) merged into every request. */
  query?: Record<string, string>;
  onRowClick?: (row: T) => void;
  /** Show the built-in search box (default true). Ignored when `q` is supplied. */
  search?: boolean;
  /** Externally controlled search text (the table hides its own search box). */
  q?: string;
  /** Extra controls rendered next to the search / page size. */
  toolbar?: ReactNode;
  emptyText?: string;
  pageSizes?: number[];
  initialSize?: number;
  rowKey?: (row: T, index: number) => string | number;
};

export function DataTable<T>({
  queryKey,
  path,
  columns,
  itemSchema,
  defaultSort,
  filters = {},
  query = {},
  onRowClick,
  search = true,
  q: externalQ,
  toolbar,
  emptyText = "No rows",
  pageSizes = [10, 20, 50, 100],
  initialSize = 20,
  rowKey,
}: Props<T>) {
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(initialSize);
  const [localQ, setLocalQ] = useState("");
  const [sort, setSort] = useState(defaultSort || "");

  const controlled = externalQ !== undefined;
  const q = controlled ? externalQ : localQ;

  const schema = useMemo(() => pageResponseSchema(itemSchema), [itemSchema]);

  // Reset to page 1 whenever the filter set changes (without an effect: derive a key).
  const filterSig = JSON.stringify([filters, query, q]);
  const [lastSig, setLastSig] = useState(filterSig);
  if (lastSig !== filterSig) {
    setLastSig(filterSig);
    if (page !== 1) setPage(1);
  }

  const { data, isFetching, error } = useQuery({
    queryKey: [queryKey, page, size, q, sort, filters, query],
    queryFn: ({ signal }) => {
      const params = new URLSearchParams();
      params.set("page", String(page));
      params.set("size", String(size));
      if (q.trim()) params.set("q", q.trim());
      if (sort) params.set("sort", sort);
      Object.entries(query).forEach(([k, v]) => {
        if (v) params.set(k, v);
      });
      Object.entries(filters).forEach(([k, v]) => {
        if (v) params.set(`filter.${k}`, v);
      });
      return apiFetch(`${path}?${params}`, { schema, signal }) as Promise<PageResponse<T>>;
    },
    placeholderData: keepPreviousData,
  });

  const rows = data?.content ?? [];
  const totalPages = Math.max(data?.totalPages ?? 1, 1);

  function toggleSort(field: string) {
    if (!field) return;
    if (sort === `${field},asc`) setSort(`${field},desc`);
    else setSort(`${field},asc`);
    setPage(1);
  }

  return (
    <div className="stack">
      <div className="row between">
        <div className="row">
          {!controlled && search ? (
            <input
              type="search"
              placeholder="Search…"
              aria-label="Search"
              value={localQ}
              onChange={(e) => {
                setLocalQ(e.target.value);
                setPage(1);
              }}
              style={{ width: 260 }}
            />
          ) : null}
          {toolbar}
        </div>
        <label className="row small muted" style={{ gap: 6 }}>
          Rows
          <select
            value={size}
            onChange={(e) => {
              setSize(Number(e.target.value));
              setPage(1);
            }}
            style={{ width: 72 }}
          >
            {pageSizes.map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </label>
      </div>
      {error ? <div className="notice bad">{(error as Error).message}</div> : null}
      <div className="tbl-wrap">
        <table>
          <thead>
            <tr>
              {columns.map((c) => (
                <th
                  key={c.key}
                  className={c.className?.split(" ").includes("n") ? "n" : undefined}
                  style={c.sortable ? { cursor: "pointer" } : undefined}
                  onClick={() => c.sortable && toggleSort(c.key)}
                >
                  {c.header}
                  {sort.startsWith(`${c.key},`) ? (sort.endsWith("desc") ? " ↓" : " ↑") : ""}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={columns.length} className="empty">
                  {isFetching ? "Loading…" : emptyText}
                </td>
              </tr>
            ) : (
              rows.map((row, i) => (
                <tr
                  key={rowKey ? rowKey(row, i) : i}
                  className={onRowClick ? "click" : undefined}
                  onClick={() => onRowClick?.(row)}
                >
                  {columns.map((c) => (
                    <td key={c.key} className={c.className}>
                      {c.render
                        ? c.render(row)
                        : String((row as Record<string, unknown>)[c.key] ?? "")}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <div className="row between">
        <span className="muted small">
          {data ? `${data.totalElements} total · page ${Math.min(page, totalPages)}/${totalPages}` : null}
          {isFetching ? " · refreshing" : ""}
        </span>
        <div className="row">
          <button type="button" className="btn sm" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
            Prev
          </button>
          <button
            type="button"
            className="btn sm"
            disabled={page >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </button>
        </div>
      </div>
    </div>
  );
}
