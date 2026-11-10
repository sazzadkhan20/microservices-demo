"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/components/auth/AuthProvider";
import { ConfirmModal } from "@/components/ui/ConfirmModal";
import { FormField } from "@/components/ui/FormField";
import { PageHeader } from "@/components/ui/PageHeader";
import { useToast } from "@/components/ui/Toast";
import { ScheduleEditorModal, DAYS, parseDays, type ScheduleEditorTarget } from "@/components/schedules/ScheduleEditorModal";
import { api, apiFetch } from "@/lib/api/client";
import { byId, useEmployeesLookup, useLocations, useTeams, type Rec } from "@/lib/api/lookups";
import { fmtNumber, str } from "@/lib/format";
import { scheduleRowSchema } from "@/lib/schemas/admin";
import { pageResponseSchema } from "@/lib/schemas/page";

type ScopeFilter = "ALL" | "LOCATION" | "TEAM" | "EMPLOYEE";

export default function SchedulesPage() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canView = can("attendance_settings", "view") || can("attendance_settings", "edit");
  const canManage = can("attendance_settings", "edit");

  const [scopeFilter, setScopeFilter] = useState<ScopeFilter>("ALL");
  const [locationFilter, setLocationFilter] = useState("");
  const [teamFilter, setTeamFilter] = useState("");
  const [employeeFilter, setEmployeeFilter] = useState("");
  const [editor, setEditor] = useState<ScheduleEditorTarget | null>(null);
  const [removeRow, setRemoveRow] = useState<Rec | null>(null);

  const locations = useLocations(canView);
  const teams = useTeams(canView);
  const employees = useEmployeesLookup(canView);
  const locName = useMemo(() => byId(locations.data, (l) => l.name), [locations.data]);
  const teamName = useMemo(() => byId(teams.data, (l) => l.name), [teams.data]);
  const empName = useMemo(() => byId(employees.data, (l) => l.fullName || `#${l.id}`), [employees.data]);

  const listQ = useQuery({
    queryKey: ["work-schedules", "all"],
    queryFn: async ({ signal }) => {
      const data = await apiFetch(api.hrms("work-schedules"), {
        query: { page: 1, size: 200 },
        signal,
      });
      const parsed = pageResponseSchema(scheduleRowSchema).safeParse(data);
      if (!parsed.success) {
        const content = Array.isArray((data as { content?: unknown })?.content)
          ? ((data as { content: Rec[] }).content as Rec[])
          : [];
        if (content.length) return { content, page: 1, size: 200, totalElements: content.length, totalPages: 1 };
        throw new Error("Schedules response shape was unexpected");
      }
      return parsed.data;
    },
    enabled: canView,
  });

  const showEmployeeFilter = scopeFilter === "ALL" || scopeFilter === "EMPLOYEE";

  /** When an employee is picked, show the single schedule that wins for them (person → team → location). */
  const resolveQ = useQuery({
    queryKey: ["work-schedule-resolve", employeeFilter],
    queryFn: ({ signal }) =>
      apiFetch<{ source?: string; sourceId?: number | string; resolved?: Rec; workPattern?: string }>(
        api.hrms(`work-schedules/resolve/${employeeFilter}`),
        { signal },
      ),
    enabled: canView && showEmployeeFilter && !!employeeFilter,
  });

  const rows = useMemo(() => {
    const all = (listQ.data?.content ?? []) as Rec[];

    // Employee picked: one resolved row only (personal if set, else team, else location default).
    if (showEmployeeFilter && employeeFilter) {
      const resolved = resolveQ.data?.resolved;
      if (!resolved) return [];
      // Prefer the live list row (has id for edit/remove) when it matches the resolved source.
      const source = String(resolveQ.data?.source ?? resolved.scopeType ?? "").toUpperCase();
      const sourceId = String(resolveQ.data?.sourceId ?? resolved.scopeId ?? "");
      const fromList = all.find(
        (r) =>
          String(r.scopeType).toUpperCase() === source && String(r.scopeId) === sourceId,
      );
      return [fromList ?? ({ ...resolved, scopeType: source, scopeId: sourceId } as Rec)];
    }

    const byScope =
      scopeFilter === "ALL" ? all : all.filter((r) => String(r.scopeType).toUpperCase() === scopeFilter);
    if (scopeFilter === "LOCATION" && locationFilter) {
      return byScope.filter((r) => String(r.scopeId) === locationFilter);
    }
    if (scopeFilter === "TEAM" && teamFilter) {
      return byScope.filter((r) => String(r.scopeId) === teamFilter);
    }
    return byScope;
  }, [
    listQ.data,
    scopeFilter,
    locationFilter,
    teamFilter,
    employeeFilter,
    showEmployeeFilter,
    resolveQ.data,
  ]);

  const rulesQ = useQuery({
    queryKey: ["work-schedule-rules"],
    queryFn: ({ signal }) =>
      apiFetch<{ resolveOrder?: string[] }>(api.hrms("work-schedules/rules"), { signal }),
    staleTime: 60_000,
    enabled: canView,
  });
  const resolveOrder = (rulesQ.data?.resolveOrder ?? ["EMPLOYEE", "TEAM", "LOCATION"]).join(" → ");

  function whoLabel(r: Rec) {
    const id = String(r.scopeId);
    const t = String(r.scopeType).toUpperCase();
    const name =
      t === "LOCATION" ? locName.get(id) : t === "TEAM" ? teamName.get(id) : empName.get(id);
    const kind = t === "LOCATION" ? "Location" : t === "TEAM" ? "Team" : "Person";
    return { kind, name: name ?? `#${id}` };
  }

  const remove = useMutation({
    mutationFn: (id: string) => apiFetch(api.hrms(`work-schedules/${id}`), { method: "DELETE" }),
    onSuccess: () => {
      toast.show("Override removed");
      setRemoveRow(null);
      void qc.invalidateQueries({ queryKey: ["work-schedules"] });
    },
    onError: (e: Error) => toast.error(e),
  });

  function resetFilters() {
    setLocationFilter("");
    setTeamFilter("");
    setEmployeeFilter("");
  }

  if (!canView) {
    return (
      <div className="stack">
        <PageHeader title="Work schedules" />
        <div className="notice">You need Attendance settings permission to view schedules.</div>
      </div>
    );
  }

  const empResolvedSource =
    showEmployeeFilter && employeeFilter
      ? String(resolveQ.data?.source ?? "").toUpperCase()
      : "";
  const empNameLabel = employeeFilter
    ? empName.get(employeeFilter) ?? `#${employeeFilter}`
    : "";

  return (
    <div className="stack">
      <PageHeader
        title="Work schedules"
        subtitle={`Resolve order ${resolveOrder}. Saving a location or team schedule never changes an existing personal schedule.`}
        actions={
          canManage ? (
            <button
              type="button"
              className="btn primary"
              onClick={() =>
                setEditor({
                  mode: "new",
                  preferScope:
                    scopeFilter === "LOCATION"
                      ? "LOCATION"
                      : scopeFilter === "EMPLOYEE"
                        ? "EMPLOYEE"
                        : "TEAM",
                  preferScopeId:
                    scopeFilter === "LOCATION"
                      ? locationFilter || undefined
                      : scopeFilter === "TEAM"
                        ? teamFilter || undefined
                        : scopeFilter === "EMPLOYEE"
                          ? employeeFilter || undefined
                          : undefined,
                })
              }
            >
              Add override
            </button>
          ) : null
        }
      />

      <div className="row" style={{ gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        {(
          [
            ["ALL", "All"],
            ["LOCATION", "Locations"],
            ["TEAM", "Teams"],
            ["EMPLOYEE", "People"],
          ] as const
        ).map(([id, label]) => (
          <button
            key={id}
            type="button"
            className={`btn sm ${scopeFilter === id ? "primary" : ""}`}
            onClick={() => {
              setScopeFilter(id);
              resetFilters();
            }}
          >
            {label}
          </button>
        ))}
        {canManage && scopeFilter === "ALL" ? (
          <button
            type="button"
            className="btn sm"
            onClick={() => setEditor({ mode: "new", preferScope: "LOCATION" })}
          >
            + Location schedule
          </button>
        ) : null}
      </div>

      <div className="card">
        <div className="form-grid">
          {scopeFilter === "LOCATION" ? (
            <FormField label="Filter by location">
              <select value={locationFilter} onChange={(e) => setLocationFilter(e.target.value)}>
                <option value="">All locations</option>
                {(locations.data ?? []).map((l) => (
                  <option key={String(l.id)} value={String(l.id)}>
                    {l.name}
                  </option>
                ))}
              </select>
            </FormField>
          ) : null}

          {scopeFilter === "TEAM" ? (
            <FormField label="Filter by team">
              <select value={teamFilter} onChange={(e) => setTeamFilter(e.target.value)}>
                <option value="">All teams</option>
                {(teams.data ?? []).map((t) => (
                  <option key={String(t.id)} value={String(t.id)}>
                    {t.name}
                  </option>
                ))}
              </select>
            </FormField>
          ) : null}

          {showEmployeeFilter ? (
            <FormField
              label="Filter by employee"
              hint="Shows the one schedule that applies to this person (personal, else team, else location)."
            >
              <select value={employeeFilter} onChange={(e) => setEmployeeFilter(e.target.value)}>
                <option value="">All people</option>
                {(employees.data ?? []).map((e) => (
                  <option key={String(e.id)} value={String(e.id)}>
                    {str(e.fullName, `#${e.id}`)}
                  </option>
                ))}
              </select>
            </FormField>
          ) : null}
        </div>
        {showEmployeeFilter && employeeFilter && resolveQ.data ? (
          <p className="small muted" style={{ marginTop: 8 }}>
            {empNameLabel}: effective source{" "}
            <b>{empResolvedSource || "none"}</b>
            {resolveQ.data.workPattern ? (
              <>
                {" "}
                · work pattern <b>{str(resolveQ.data.workPattern)}</b>
              </>
            ) : null}
          </p>
        ) : null}
      </div>

      <div className="card">
        {listQ.error ? <div className="notice bad">{(listQ.error as Error).message}</div> : null}
        {showEmployeeFilter && employeeFilter && resolveQ.isLoading ? (
          <div className="notice">Resolving schedule for {empNameLabel}…</div>
        ) : null}
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th>Applies to</th>
                <th>Mode</th>
                <th>Working days</th>
                <th>Office hours</th>
                <th className="n">Grace</th>
                <th className="n">Half day under</th>
                <th>Check-out cut-off</th>
                {canManage ? <th></th> : null}
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={canManage ? 8 : 7} className="empty">
                    {listQ.isLoading || (employeeFilter && resolveQ.isLoading)
                      ? "Loading…"
                      : employeeFilter
                        ? `No schedule resolved for ${empNameLabel} — set a location/team schedule or a personal override.`
                        : "No schedules yet — add a location schedule (e.g. BD Sun–Thu 10:00–18:00), then team or person overrides."}
                  </td>
                </tr>
              ) : (
                rows.map((r) => {
                  const who = whoLabel(r);
                  const hourly = String(r.mode).toUpperCase() === "HOURLY";
                  const on = parseDays(r.workingDays);
                  const isResolvedView = !!employeeFilter;
                  const appliesLabel = isResolvedView
                    ? empResolvedSource === "EMPLOYEE"
                      ? `Person · ${empNameLabel}`
                      : `${who.kind} · ${who.name} (via ${empNameLabel})`
                    : `${who.kind} · ${who.name}`;
                  return (
                    <tr key={String(r.id ?? `${r.scopeType}-${r.scopeId}`)}>
                      <td>
                        <b>{appliesLabel}</b>
                      </td>
                      <td>
                        <span className="pill info">{str(r.mode)}</span>
                      </td>
                      <td>
                        {hourly ? (
                          <span className="muted">No fixed days</span>
                        ) : (
                          <div className="row" style={{ gap: 4, flexWrap: "wrap" }}>
                            {DAYS.map((d) => (
                              <span
                                key={d}
                                className={`code ${on.includes(d) ? "c-P" : "c-WO"}`}
                                style={{ minWidth: 34 }}
                              >
                                {d}
                              </span>
                            ))}
                          </div>
                        )}
                      </td>
                      <td className="mono">
                        {hourly
                          ? r.weeklyHours == null
                            ? "—"
                            : `${fmtNumber(r.weeklyHours)} h/wk`
                          : `${str(r.startTime)}–${str(r.endTime)}`}
                      </td>
                      <td className="n">{hourly ? "—" : `${str(r.graceMinutes, "0")} min`}</td>
                      <td className="n">
                        {hourly || r.halfDayHours == null ? "—" : `${fmtNumber(r.halfDayHours)} h`}
                      </td>
                      <td className="mono">{hourly ? "—" : str(r.checkoutCutoff, "—")}</td>
                      {canManage ? (
                        <td>
                          <div className="row" style={{ gap: 6, justifyContent: "flex-end" }}>
                            <button
                              type="button"
                              className="btn sm"
                              onClick={() => setEditor({ mode: "edit", row: r })}
                            >
                              Edit
                            </button>
                            {String(r.scopeType).toUpperCase() !== "LOCATION" ? (
                              <button
                                type="button"
                                className="btn sm danger"
                                onClick={() => setRemoveRow(r)}
                              >
                                Remove
                              </button>
                            ) : null}
                          </div>
                        </td>
                      ) : null}
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
        <p className="small muted" style={{ marginTop: 12 }}>
          Location is the base; team overrides location; person overrides both. Less-specific saves never rewrite a
          more-specific row. Missing check-out cut-off defaults to office end + 6 hours.
        </p>
      </div>

      <ScheduleEditorModal
        open={!!editor}
        target={editor}
        onClose={() => setEditor(null)}
        onSaved={() => void qc.invalidateQueries({ queryKey: ["work-schedules"] })}
      />

      <ConfirmModal
        open={!!removeRow}
        title="Remove schedule override?"
        confirmLabel="Remove"
        danger
        busy={remove.isPending}
        onCancel={() => setRemoveRow(null)}
        onConfirm={() => removeRow && remove.mutate(String(removeRow.id))}
      >
        This removes the {str(removeRow?.scopeType)} override for{" "}
        {removeRow ? `${whoLabel(removeRow).kind} · ${whoLabel(removeRow).name}` : "—"}. Resolution falls back to the
        next level in {resolveOrder}.
      </ConfirmModal>
    </div>
  );
}
