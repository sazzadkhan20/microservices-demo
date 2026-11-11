"use client";

import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/components/auth/AuthProvider";
import { ConfirmModal } from "@/components/ui/ConfirmModal";
import { DataTable } from "@/components/ui/DataTable";
import { FormField } from "@/components/ui/FormField";
import { PageHeader } from "@/components/ui/PageHeader";
import { useToast } from "@/components/ui/Toast";
import { ScheduleEditorModal, DAYS, parseDays, type ScheduleEditorTarget } from "@/components/schedules/ScheduleEditorModal";
import { api, apiFetch } from "@/lib/api/client";
import { byId, useEmployeesLookup, useLocations, useTeams, type Rec } from "@/lib/api/lookups";
import { fmtNumber, str } from "@/lib/format";
import { scheduleRowSchema, type ScheduleRow } from "@/lib/schemas/admin";

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

  // Backfill missing LOCATION base schedules (idempotent; never overwrites existing hours).
  useEffect(() => {
    if (!canManage || !locations.data?.length) return;
    let cancelled = false;
    void apiFetch(api.hrms("work-schedules/ensure-location-defaults"), {
      method: "POST",
      body: { locationIds: locations.data.map((l) => l.id) },
    })
      .then(() => {
        if (!cancelled) void qc.invalidateQueries({ queryKey: ["work-schedules"] });
      })
      .catch(() => {
        /* create path still seeds on new locations */
      });
    return () => {
      cancelled = true;
    };
  }, [canManage, locations.data, qc]);

  const showEmployeeFilter = scopeFilter === "ALL" || scopeFilter === "EMPLOYEE";
  const resolveEmployee = showEmployeeFilter && !!employeeFilter;

  const resolveQ = useQuery({
    queryKey: ["work-schedule-resolve", employeeFilter],
    queryFn: ({ signal }) =>
      apiFetch<{ source?: string; sourceId?: number | string; resolved?: Rec; workPattern?: string }>(
        api.hrms(`work-schedules/resolve/${employeeFilter}`),
        { signal },
      ),
    enabled: canView && resolveEmployee,
  });

  const tableFilters = useMemo(() => {
    const f: Record<string, string> = {};
    if (scopeFilter === "LOCATION") {
      f.scopeType = "LOCATION";
      if (locationFilter) f.scopeId = locationFilter;
    } else if (scopeFilter === "TEAM") {
      f.scopeType = "TEAM";
      if (teamFilter) f.scopeId = teamFilter;
    } else if (scopeFilter === "EMPLOYEE") {
      f.scopeType = "EMPLOYEE";
    }
    return f;
  }, [scopeFilter, locationFilter, teamFilter]);

  const rulesQ = useQuery({
    queryKey: ["work-schedule-rules"],
    queryFn: ({ signal }) =>
      apiFetch<{ resolveOrder?: string[] }>(api.hrms("work-schedules/rules"), { signal }),
    staleTime: 60_000,
    enabled: canView,
  });
  const resolveOrder = (rulesQ.data?.resolveOrder ?? ["EMPLOYEE", "TEAM", "LOCATION"]).join(" → ");

  function whoLabel(r: Rec | ScheduleRow) {
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

  const empResolvedSource = resolveEmployee ? String(resolveQ.data?.source ?? "").toUpperCase() : "";
  const empNameLabel = employeeFilter ? empName.get(employeeFilter) ?? `#${employeeFilter}` : "";
  const resolvedRow = resolveEmployee ? (resolveQ.data?.resolved as Rec | undefined) : undefined;

  function renderScheduleCells(r: Rec | ScheduleRow, appliesLabel: string) {
    const hourly = String(r.mode).toUpperCase() === "HOURLY";
    const on = parseDays(r.workingDays);
    return {
      applies: <b>{appliesLabel}</b>,
      mode: <span className="pill info">{str(r.mode)}</span>,
      days: hourly ? (
        <span className="muted">No fixed days</span>
      ) : (
        <div className="row" style={{ gap: 4, flexWrap: "wrap" }}>
          {DAYS.map((d) => (
            <span key={d} className={`code ${on.includes(d) ? "c-P" : "c-WO"}`} style={{ minWidth: 34 }}>
              {d}
            </span>
          ))}
        </div>
      ),
      hours: (
        <span className="mono">
          {hourly
            ? r.weeklyHours == null
              ? "—"
              : `${fmtNumber(r.weeklyHours)} h/wk`
            : `${str(r.startTime)}–${str(r.endTime)}`}
        </span>
      ),
      grace: hourly ? "—" : `${str(r.graceMinutes, "0")} min`,
      half: hourly || r.halfDayHours == null ? "—" : `${fmtNumber(r.halfDayHours)} h`,
      cutoff: <span className="mono">{hourly ? "—" : str(r.checkoutCutoff, "—")}</span>,
    };
  }

  return (
    <div className="stack">
      <PageHeader
        title="Work schedules"
        subtitle={`Resolve order ${resolveOrder}. Locations always show their base hours; team/person rows appear only when they have an override.`}
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
            ["TEAM", "Team overrides"],
            ["EMPLOYEE", "Person overrides"],
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
        {resolveEmployee && resolveQ.data ? (
          <p className="small muted" style={{ marginTop: 8 }}>
            {empNameLabel}: effective source <b>{empResolvedSource || "none"}</b>
            {resolveQ.data.workPattern ? (
              <>
                {" "}
                · work pattern <b>{str(resolveQ.data.workPattern)}</b>
              </>
            ) : null}
          </p>
        ) : null}
      </div>

      <div className="card stack">
        {resolveEmployee ? (
          <>
            {resolveQ.error ? <div className="notice bad">{(resolveQ.error as Error).message}</div> : null}
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
                  {!resolvedRow ? (
                    <tr>
                      <td colSpan={canManage ? 8 : 7} className="empty">
                        {resolveQ.isLoading
                          ? `Resolving schedule for ${empNameLabel}…`
                          : `No schedule resolved for ${empNameLabel} — set a location schedule or a team/person override.`}
                      </td>
                    </tr>
                  ) : (
                    (() => {
                      const who = whoLabel(resolvedRow);
                      const appliesLabel =
                        empResolvedSource === "EMPLOYEE"
                          ? `Person · ${empNameLabel}`
                          : `${who.kind} · ${who.name} (via ${empNameLabel})`;
                      const cells = renderScheduleCells(resolvedRow, appliesLabel);
                      return (
                        <tr key={String(resolvedRow.id ?? `${resolvedRow.scopeType}-${resolvedRow.scopeId}`)}>
                          <td>{cells.applies}</td>
                          <td>{cells.mode}</td>
                          <td>{cells.days}</td>
                          <td>{cells.hours}</td>
                          <td className="n">{cells.grace}</td>
                          <td className="n">{cells.half}</td>
                          <td>{cells.cutoff}</td>
                          {canManage ? (
                            <td>
                              <div className="row" style={{ gap: 6, justifyContent: "flex-end" }}>
                                <button
                                  type="button"
                                  className="btn sm"
                                  onClick={() => setEditor({ mode: "edit", row: resolvedRow })}
                                >
                                  Edit
                                </button>
                                {String(resolvedRow.scopeType).toUpperCase() !== "LOCATION" ? (
                                  <button
                                    type="button"
                                    className="btn sm danger"
                                    onClick={() => setRemoveRow(resolvedRow)}
                                  >
                                    Remove
                                  </button>
                                ) : null}
                              </div>
                            </td>
                          ) : null}
                        </tr>
                      );
                    })()
                  )}
                </tbody>
              </table>
            </div>
          </>
        ) : (
          <DataTable<ScheduleRow>
            queryKey={`work-schedules-${scopeFilter}-${locationFilter}-${teamFilter}`}
            path={api.hrms("work-schedules")}
            itemSchema={scheduleRowSchema}
            filters={tableFilters}
            search
            pageSizes={[10, 20, 50, 100]}
            initialSize={20}
            emptyText={
              scopeFilter === "TEAM"
                ? "No team overrides yet — locations use their own base hours until a team override is added."
                : scopeFilter === "EMPLOYEE"
                  ? "No person overrides yet."
                  : "No location schedules yet — create a location in Organization to seed Mon–Fri 09:00–17:00 defaults."
            }
            rowKey={(r) => String(r.id)}
            columns={[
              {
                key: "scopeType",
                header: "Applies to",
                sortable: true,
                render: (r) => {
                  const who = whoLabel(r);
                  return <b>{`${who.kind} · ${who.name}`}</b>;
                },
              },
              {
                key: "mode",
                header: "Mode",
                sortable: true,
                render: (r) => <span className="pill info">{str(r.mode)}</span>,
              },
              {
                key: "workingDays",
                header: "Working days",
                render: (r) => {
                  const hourly = String(r.mode).toUpperCase() === "HOURLY";
                  const on = parseDays(r.workingDays);
                  if (hourly) return <span className="muted">No fixed days</span>;
                  return (
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
                  );
                },
              },
              {
                key: "startTime",
                header: "Office hours",
                sortable: true,
                render: (r) => {
                  const hourly = String(r.mode).toUpperCase() === "HOURLY";
                  return (
                    <span className="mono">
                      {hourly
                        ? r.weeklyHours == null
                          ? "—"
                          : `${fmtNumber(r.weeklyHours)} h/wk`
                        : `${str(r.startTime)}–${str(r.endTime)}`}
                    </span>
                  );
                },
              },
              {
                key: "graceMinutes",
                header: "Grace",
                className: "n",
                render: (r) =>
                  String(r.mode).toUpperCase() === "HOURLY" ? "—" : `${str(r.graceMinutes, "0")} min`,
              },
              {
                key: "halfDayHours",
                header: "Half day under",
                className: "n",
                render: (r) =>
                  String(r.mode).toUpperCase() === "HOURLY" || r.halfDayHours == null
                    ? "—"
                    : `${fmtNumber(r.halfDayHours)} h`,
              },
              {
                key: "checkoutCutoff",
                header: "Check-out cut-off",
                render: (r) => (
                  <span className="mono">
                    {String(r.mode).toUpperCase() === "HOURLY" ? "—" : str(r.checkoutCutoff, "—")}
                  </span>
                ),
              },
              ...(canManage
                ? [
                    {
                      key: "actions",
                      header: "",
                      render: (r: ScheduleRow) => (
                        <div className="row" style={{ gap: 6, justifyContent: "flex-end" }}>
                          <button
                            type="button"
                            className="btn sm"
                            onClick={(e) => {
                              e.stopPropagation();
                              setEditor({ mode: "edit", row: r as Rec });
                            }}
                          >
                            Edit
                          </button>
                          {String(r.scopeType).toUpperCase() !== "LOCATION" ? (
                            <button
                              type="button"
                              className="btn sm danger"
                              onClick={(e) => {
                                e.stopPropagation();
                                setRemoveRow(r as Rec);
                              }}
                            >
                              Remove
                            </button>
                          ) : null}
                        </div>
                      ),
                    },
                  ]
                : []),
            ]}
          />
        )}
        <p className="small muted" style={{ marginTop: 4 }}>
          Every location keeps a base schedule (new sites default to Mon–Fri 09:00–17:00, grace 15, half-day 4h,
          cut-off 23:59). Team overrides location; person overrides both. Location rows cannot be removed — edit them
          instead.
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
