"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { FormField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { useEmployeesLookup, useLocations, useTeams, type Rec } from "@/lib/api/lookups";
import { str } from "@/lib/format";

const DAYS = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"] as const;

export type ScheduleScope = "LOCATION" | "TEAM" | "EMPLOYEE";

export type ScheduleEditorTarget =
  | { mode: "new"; preferScope?: ScheduleScope; preferScopeId?: string | number }
  | { mode: "edit"; row: Rec }
  | { mode: "employee"; employeeId: string | number; employeeName?: string };

type Rules = {
  modes?: string[];
  scopes?: string[];
  defaultGraceMinutes?: number;
  defaultHalfDayHours?: number | string;
  defaultCheckoutCutoff?: string;
};

function parseDays(v: unknown): string[] {
  if (Array.isArray(v)) return v.map(String);
  if (typeof v === "string") {
    try {
      const p = JSON.parse(v);
      if (Array.isArray(p)) return p.map(String);
    } catch {
      return v.split(/[,\s]+/).filter(Boolean);
    }
  }
  return [];
}

function blankForm(rules?: Rules, prefer?: { scopeType?: string; scopeId?: string }) {
  return {
    scopeType: (prefer?.scopeType || "TEAM").toUpperCase(),
    scopeId: prefer?.scopeId ? String(prefer.scopeId) : "",
    mode: "FIXED",
    workingDays: ["SUN", "MON", "TUE", "WED", "THU"] as string[],
    startTime: "10:00",
    endTime: "18:00",
    graceMinutes: String(rules?.defaultGraceMinutes ?? 15),
    halfDayHours: String(rules?.defaultHalfDayHours ?? 4),
    checkoutCutoff: String(rules?.defaultCheckoutCutoff ?? "23:59"),
    weeklyHours: "40",
  };
}

type Props = {
  open: boolean;
  target: ScheduleEditorTarget | null;
  onClose: () => void;
  /** Called after successful save. */
  onSaved?: () => void;
  /** Limit which scopes can be chosen when creating. */
  allowScopes?: ScheduleScope[];
};

export function ScheduleEditorModal({ open, target, onClose, onSaved, allowScopes }: Props) {
  const qc = useQueryClient();
  const toast = useToast();
  const locations = useLocations(open);
  const teams = useTeams(open);
  const employees = useEmployeesLookup(open);

  const rulesQ = useQuery({
    queryKey: ["work-schedule-rules"],
    queryFn: ({ signal }) => apiFetch<Rules>(api.hrms("work-schedules/rules"), { signal }),
    staleTime: 60_000,
    enabled: open,
  });

  const employeeTargetId = target?.mode === "employee" ? target.employeeId : null;
  const existingEmpSchedule = useQuery({
    queryKey: ["work-schedule-resolve", employeeTargetId],
    queryFn: ({ signal }) =>
      apiFetch<{ resolved?: Rec; source?: string }>(
        api.hrms(`work-schedules/resolve/${employeeTargetId}`),
        { signal },
      ),
    enabled: open && employeeTargetId != null,
  });

  const [form, setForm] = useState(blankForm());
  const [editingId, setEditingId] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");
  const [lockedScope, setLockedScope] = useState(false);

  const modes = (rulesQ.data?.modes?.length ? rulesQ.data.modes : ["FIXED", "HOURLY"]).map((m) =>
    String(m).toUpperCase(),
  );
  const allScopes = (rulesQ.data?.scopes?.length ? rulesQ.data.scopes : ["LOCATION", "TEAM", "EMPLOYEE"]).map((s) =>
    String(s).toUpperCase(),
  ) as ScheduleScope[];
  const scopes = (allowScopes?.length ? allowScopes : allScopes).filter((s) => allScopes.includes(s));

  useEffect(() => {
    if (!open || !target) return;
    setErr("");
    setErrors({});

    if (target.mode === "edit") {
      const r = target.row;
      setEditingId(String(r.id));
      setLockedScope(true);
      setForm({
        scopeType: str(r.scopeType, "LOCATION").toUpperCase(),
        scopeId: String(r.scopeId ?? ""),
        mode: str(r.mode, "FIXED").toUpperCase() === "HOURLY" ? "HOURLY" : "FIXED",
        workingDays: parseDays(r.workingDays),
        startTime: str(r.startTime, "10:00"),
        endTime: str(r.endTime, "18:00"),
        graceMinutes: String(r.graceMinutes ?? 15),
        halfDayHours: r.halfDayHours == null ? "" : String(r.halfDayHours),
        checkoutCutoff: str(r.checkoutCutoff, "23:59"),
        weeklyHours: r.weeklyHours == null ? "" : String(r.weeklyHours),
      });
      return;
    }

    if (target.mode === "employee") {
      const resolved = existingEmpSchedule.data?.resolved;
      const source = existingEmpSchedule.data?.source;
      setLockedScope(true);
      if (resolved && String(source).toUpperCase() === "EMPLOYEE" && resolved.id != null) {
        setEditingId(String(resolved.id));
        setForm({
          scopeType: "EMPLOYEE",
          scopeId: String(target.employeeId),
          mode: str(resolved.mode, "FIXED").toUpperCase() === "HOURLY" ? "HOURLY" : "FIXED",
          workingDays: parseDays(resolved.workingDays),
          startTime: str(resolved.startTime, "10:00"),
          endTime: str(resolved.endTime, "18:00"),
          graceMinutes: String(resolved.graceMinutes ?? 15),
          halfDayHours: resolved.halfDayHours == null ? "" : String(resolved.halfDayHours),
          checkoutCutoff: str(resolved.checkoutCutoff, "23:59"),
          weeklyHours: resolved.weeklyHours == null ? "" : String(resolved.weeklyHours),
        });
      } else {
        setEditingId(null);
        const base = resolved ?? {};
        setForm({
          ...blankForm(rulesQ.data, { scopeType: "EMPLOYEE", scopeId: String(target.employeeId) }),
          mode: str(base.mode, "FIXED").toUpperCase() === "HOURLY" ? "HOURLY" : "FIXED",
          workingDays: parseDays(base.workingDays).length
            ? parseDays(base.workingDays)
            : ["SUN", "MON", "TUE", "WED", "THU"],
          startTime: str(base.startTime, "10:00"),
          endTime: str(base.endTime, "18:00"),
          graceMinutes: String(base.graceMinutes ?? rulesQ.data?.defaultGraceMinutes ?? 15),
          halfDayHours: String(base.halfDayHours ?? rulesQ.data?.defaultHalfDayHours ?? 4),
          checkoutCutoff: str(base.checkoutCutoff, rulesQ.data?.defaultCheckoutCutoff ?? "23:59"),
          weeklyHours: base.weeklyHours == null ? "40" : String(base.weeklyHours),
        });
      }
      return;
    }

    // new
    setEditingId(null);
    setLockedScope(false);
    setForm(
      blankForm(rulesQ.data, {
        scopeType: target.preferScope || "TEAM",
        scopeId: target.preferScopeId != null ? String(target.preferScopeId) : "",
      }),
    );
  }, [open, target, existingEmpSchedule.data, rulesQ.data]);

  const isHourly = form.mode.toUpperCase() === "HOURLY";

  const schema = useMemo(
    () =>
      z
        .object({
          scopeType: z.string().min(1),
          scopeId: z.string().min(1, "Choose who this applies to"),
          mode: z.string().min(1),
          workingDays: z.array(z.string()),
          startTime: z.string(),
          endTime: z.string(),
          graceMinutes: z.coerce.number("Enter minutes").int().min(0).max(240),
          halfDayHours: z.string(),
          checkoutCutoff: z.string(),
          weeklyHours: z.string(),
        })
        .superRefine((v, ctx) => {
          const hourly = v.mode.toUpperCase() === "HOURLY";
          if (!hourly) {
            if (!v.workingDays.length) {
              ctx.addIssue({ code: "custom", path: ["workingDays"], message: "Pick at least one working day" });
            }
            if (!v.startTime) ctx.addIssue({ code: "custom", path: ["startTime"], message: "Start time is required" });
            if (!v.endTime) ctx.addIssue({ code: "custom", path: ["endTime"], message: "End time is required" });
            if (v.startTime && v.endTime && v.endTime <= v.startTime) {
              ctx.addIssue({ code: "custom", path: ["endTime"], message: "End must be after start" });
            }
          } else if (v.weeklyHours && (Number.isNaN(Number(v.weeklyHours)) || Number(v.weeklyHours) < 0)) {
            ctx.addIssue({ code: "custom", path: ["weeklyHours"], message: "Must be a number ≥ 0" });
          }
        }),
    [],
  );

  function scopeOptions(type: string): { id: string; label: string }[] {
    if (type === "LOCATION") return (locations.data ?? []).map((l) => ({ id: String(l.id), label: l.name }));
    if (type === "TEAM") return (teams.data ?? []).map((l) => ({ id: String(l.id), label: l.name }));
    return (employees.data ?? []).map((l) => ({ id: String(l.id), label: l.fullName || `#${l.id}` }));
  }

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      editingId
        ? apiFetch(api.hrms(`work-schedules/${editingId}`), { method: "PUT", body })
        : apiFetch(api.hrms("work-schedules"), { method: "POST", body }),
    onSuccess: () => {
      toast.show(editingId ? "Schedule saved" : "Schedule override added");
      void qc.invalidateQueries({ queryKey: ["work-schedules"] });
      void qc.invalidateQueries({ queryKey: ["work-schedule-resolve"] });
      onSaved?.();
      onClose();
    },
    onError: (e: Error) => setErr(e.message),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = schema.safeParse(form);
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setErrors(next);
    }
    setErrors({});
    setErr("");
    const v = parsed.data;
    const hourly = v.mode.toUpperCase() === "HOURLY";
    save.mutate({
      scopeType: v.scopeType,
      scopeId: Number(v.scopeId),
      mode: hourly ? "HOURLY" : "FIXED",
      workingDays: JSON.stringify(hourly ? [] : DAYS.filter((d) => v.workingDays.includes(d))),
      startTime: hourly ? null : v.startTime,
      endTime: hourly ? null : v.endTime,
      graceMinutes: hourly ? 0 : v.graceMinutes,
      halfDayHours: hourly || v.halfDayHours === "" ? null : Number(v.halfDayHours),
      checkoutCutoff: hourly ? null : v.checkoutCutoff || null,
      weeklyHours: !hourly || v.weeklyHours === "" ? null : Number(v.weeklyHours),
    });
  }

  const title =
    target?.mode === "employee"
      ? `Work schedule · ${target.employeeName || `#${target.employeeId}`}`
      : editingId
        ? "Edit schedule"
        : "Add schedule override";

  return (
    <Modal
      open={open && !!target}
      onClose={onClose}
      title={title}
      subtitle="A person’s schedule overrides their team’s, and a team’s overrides the location’s. Changes are audited."
    >
      <form className="stack" onSubmit={submit} noValidate>
        <div className="form-grid">
          <FormField
            label="Work pattern"
            hint="Fixed days and hours (5 or 6 day weeks), or hourly with no fixed days."
            full
          >
            <select value={form.mode} onChange={(e) => setForm({ ...form, mode: e.target.value })}>
              {modes.map((m) => (
                <option key={m} value={m}>
                  {m === "HOURLY"
                    ? "Hourly — paid for hours worked, no fixed days"
                    : "Fixed days and hours (e.g. 5 or 6 days a week)"}
                </option>
              ))}
            </select>
          </FormField>

          <FormField label="Applies to">
            <select
              value={form.scopeType}
              disabled={lockedScope}
              onChange={(e) => setForm({ ...form, scopeType: e.target.value, scopeId: "" })}
            >
              {scopes.map((s) => (
                <option key={s} value={s}>
                  {s === "LOCATION" ? "A location" : s === "TEAM" ? "A team" : "One person"}
                </option>
              ))}
            </select>
          </FormField>

          <FormField
            label={form.scopeType === "LOCATION" ? "Location" : form.scopeType === "TEAM" ? "Team" : "Person"}
            required
            error={errors.scopeId}
          >
            <select
              value={form.scopeId}
              disabled={lockedScope && target?.mode === "employee"}
              onChange={(e) => setForm({ ...form, scopeId: e.target.value })}
            >
              <option value="">Select…</option>
              {scopeOptions(form.scopeType).map((o) => (
                <option key={o.id} value={o.id}>
                  {o.label}
                </option>
              ))}
            </select>
          </FormField>

          {!isHourly ? (
            <>
              <div className="field full">
                <span>Working days</span>
                <div className="checks">
                  {DAYS.map((d) => (
                    <label key={d}>
                      <input
                        type="checkbox"
                        checked={form.workingDays.includes(d)}
                        onChange={(e) =>
                          setForm({
                            ...form,
                            workingDays: e.target.checked
                              ? [...form.workingDays, d]
                              : form.workingDays.filter((x) => x !== d),
                          })
                        }
                      />
                      {d}
                    </label>
                  ))}
                </div>
                {errors.workingDays ? <span className="field-err">{errors.workingDays}</span> : null}
                <small className="muted">Any number of days — 5-day and 6-day people can share a team.</small>
              </div>
              <FormField label="Office start" required error={errors.startTime}>
                <input
                  type="time"
                  value={form.startTime}
                  onChange={(e) => setForm({ ...form, startTime: e.target.value })}
                />
              </FormField>
              <FormField label="Office end" required error={errors.endTime}>
                <input type="time" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} />
              </FormField>
              <FormField label="Late grace (minutes)" error={errors.graceMinutes}>
                <input
                  type="number"
                  min={0}
                  value={form.graceMinutes}
                  onChange={(e) => setForm({ ...form, graceMinutes: e.target.value })}
                />
              </FormField>
              <FormField label="Half day if worked under (hours)" error={errors.halfDayHours}>
                <input
                  type="number"
                  min={0}
                  step="0.25"
                  value={form.halfDayHours}
                  onChange={(e) => setForm({ ...form, halfDayHours: e.target.value })}
                />
              </FormField>
              <FormField label="Missing check-out cut-off">
                <input
                  type="time"
                  value={form.checkoutCutoff}
                  onChange={(e) => setForm({ ...form, checkoutCutoff: e.target.value })}
                />
              </FormField>
            </>
          ) : (
            <FormField
              label="Expected hours a week"
              error={errors.weeklyHours}
              hint="Optional. Period expected hours = weekly × days in period ÷ 7."
              full
            >
              <input
                type="number"
                min={0}
                step="0.25"
                value={form.weeklyHours}
                onChange={(e) => setForm({ ...form, weeklyHours: e.target.value })}
                placeholder="e.g. 40"
              />
            </FormField>
          )}
        </div>
        {err ? <div className="notice bad">{err}</div> : null}
        <div className="modal-foot">
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn primary" disabled={save.isPending}>
            {save.isPending ? "Saving…" : "Save schedule"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

export { DAYS, parseDays };
