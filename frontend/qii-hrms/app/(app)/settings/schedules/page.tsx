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
        // Still show rows if envelope is usable — don't blank the admin UI on minor shape drift.
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

  const rows = useMemo(() => {
    const all = (listQ.data?.content ?? []) as Rec[];
    if (scopeFilter === "ALL") return all;
    return all.filter((r) => String(r.scopeType).toUpperCase() === scopeFilter);
  }, [listQ.data, scopeFilter]);

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

  if (!canView) {
    return (
      <div className="stack">
        <PageHeader title="Work schedules" />
        <div className="notice">You need Attendance settings permission to view schedules.</div>
      </div>
    );
  }

  return (
    <div className="stack">
      <PageHeader
        title="Work schedules"
        subtitle={`A person’s schedule overrides their team’s, and a team’s overrides the location’s (${resolveOrder}).`}
        actions={
          canManage ? (
            <button
              type="button"
              className="btn primary"
              onClick={() => setEditor({ mode: "new", preferScope: "TEAM" })}
            >
              Add override
            </button>
          ) : null
        }
      />

      <div className="row" style={{ gap: 8, flexWrap: "wrap" }}>
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
            onClick={() => setScopeFilter(id)}
          >
            {label}
          </button>
        ))}
        {canManage ? (
          <button
            type="button"
            className="btn sm"
            onClick={() => setEditor({ mode: "new", preferScope: "LOCATION" })}
          >
            + Location schedule
          </button>
        ) : null}
      </div>

      <ResolveChecker employees={employees.data ?? []} />

      <div className="card">
        {listQ.error ? <div className="notice bad">{(listQ.error as Error).message}</div> : null}
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
                    {listQ.isLoading
                      ? "Loading…"
                      : "No schedules yet — add a location schedule (e.g. BD Sun–Thu 10:00–18:00), then team or person overrides."}
                  </td>
                </tr>
              ) : (
                rows.map((r) => {
                  const who = whoLabel(r);
                  const hourly = String(r.mode).toUpperCase() === "HOURLY";
                  const on = parseDays(r.workingDays);
                  return (
                    <tr key={String(r.id)}>
                      <td>
                        <b>
                          {who.kind} · {who.name}
                        </b>
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
          Location schedules are the base for everyone at that office. Team and person rows are overrides. A check-out
          up to 6 hours after midnight counts for the previous day.
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

function ResolveChecker({ employees }: { employees: Rec[] }) {
  const [employeeId, setEmployeeId] = useState("");
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const resolveQ = useQuery({
    queryKey: ["work-schedule-resolve", employeeId],
    queryFn: ({ signal }) =>
      apiFetch<{ source?: string; resolved?: Rec; workPattern?: string }>(
        api.hrms(`work-schedules/resolve/${employeeId}`),
        { signal },
      ),
    enabled: !!employeeId,
  });
  const hoursQ = useQuery({
    queryKey: ["work-schedule-hours", employeeId, date],
    queryFn: ({ signal }) =>
      apiFetch<{ expectedHours?: number | string }>(
        api.hrms(`work-schedules/expected-hours?employeeId=${employeeId}&date=${date}`),
        { signal },
      ),
    enabled: !!employeeId && !!date,
  });
  const resolved = resolveQ.data?.resolved;
  const days = parseDays(resolved?.workingDays);

  return (
    <div className="card stack">
      <div>
        <b>Resolve preview</b>
        <div className="small muted">
          Pick a person to see which schedule wins (person → team → location) and expected hours for a day.
        </div>
      </div>
      <div className="form-grid">
        <FormField label="Employee">
          <select value={employeeId} onChange={(e) => setEmployeeId(e.target.value)}>
            <option value="">Select…</option>
            {employees.map((e) => (
              <option key={String(e.id)} value={String(e.id)}>
                {str(e.fullName, `#${e.id}`)}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label="Date">
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </FormField>
      </div>
      {employeeId && resolveQ.data ? (
        <div className="small">
          Work pattern: <b>{str(resolveQ.data.workPattern, "—")}</b>
          {" · "}
          Source: <b>{str(resolveQ.data.source, "none")}</b>
          {resolved ? (
            <>
              {" · "}
              {str(resolved.mode)}{" "}
              {String(resolved.mode).toUpperCase() === "HOURLY"
                ? resolved.weeklyHours == null
                  ? "(no weekly hours)"
                  : `(${fmtNumber(resolved.weeklyHours)} h/wk)`
                : `(${days.join(", ") || "—"} ${str(resolved.startTime)}–${str(resolved.endTime)})`}
            </>
          ) : null}
          {" · "}
          Expected hours: <b>{hoursQ.data?.expectedHours == null ? "…" : fmtNumber(hoursQ.data.expectedHours)}</b>
        </div>
      ) : null}
    </div>
  );
}
