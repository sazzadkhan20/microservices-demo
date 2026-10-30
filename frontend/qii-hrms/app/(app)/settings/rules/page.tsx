"use client";

import { useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote, Loading } from "@/components/ui/QueryState";
import { api, apiFetch } from "@/lib/api/client";
import { humanize, str } from "@/lib/format";

const ruleEntrySchema = z
  .object({
    id: z.union([z.number(), z.string()]),
    key: z.string().optional(),
    description: z.string().optional(),
    value: z.unknown(),
    default: z.unknown().optional(),
    defaultValue: z.unknown().optional(),
    overridden: z.boolean().optional(),
  })
  .passthrough();

/** API returns `{ rules: { "BR-01": {...}, ... }, source }` — not a bare array. */
const businessRulesResponseSchema = z
  .object({
    rules: z.record(z.string(), ruleEntrySchema).optional(),
    source: z.string().optional(),
  })
  .passthrough()
  .or(z.array(ruleEntrySchema));

type BusinessRule = z.infer<typeof ruleEntrySchema>;

function renderValue(v: unknown): string {
  if (v === null || v === undefined) return "—";
  if (typeof v === "object") return JSON.stringify(v);
  return String(v);
}

function toRows(data: z.infer<typeof businessRulesResponseSchema> | undefined): BusinessRule[] {
  if (!data) return [];
  if (Array.isArray(data)) return data;
  const map = data.rules ?? {};
  return Object.entries(map).map(([key, row]) => ({
    ...row,
    id: row.id ?? key,
    key: row.key ?? key,
  }));
}

export default function BusinessRulesPage() {
  const rules = useQuery({
    queryKey: ["business-rules"],
    queryFn: ({ signal }) => apiFetch(api.hrms("business-rules"), { schema: businessRulesResponseSchema, signal }),
  });

  const rows = toRows(rules.data);

  return (
    <div className="stack">
      <PageHeader
        title="Business rules"
        subtitle="Read-only view of configurable business rules for this tenant. Contact your administrator to change a value."
      />
      <ErrorNote error={rules.error} />
      {rules.isLoading ? <Loading /> : null}
      {!rules.isLoading && !rules.error && rows.length === 0 ? (
        <div className="card empty">No business rules configured.</div>
      ) : null}
      {rows.length > 0 ? (
        <div className="card">
          <div className="tbl-wrap">
            <table>
              <thead>
                <tr>
                  <th>Rule</th>
                  <th>Value</th>
                  <th>Default</th>
                  <th>Description</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={String(r.id)}>
                    <td>
                      <b>
                        {humanize(
                          str(r.key ?? r.id, "—")
                            .replace(/([a-z])([A-Z])/g, "$1_$2")
                            .replace(/[._-]/g, " "),
                        )}
                      </b>
                      <br />
                      <span className="mono small muted">{str(r.key ?? r.id, "—")}</span>
                    </td>
                    <td className="mono small">{renderValue(r.value)}</td>
                    <td className="mono small muted">{renderValue(r.default ?? r.defaultValue)}</td>
                    <td className="small muted">{str(r.description, "—")}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}
    </div>
  );
}
