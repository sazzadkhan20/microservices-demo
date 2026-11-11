"use client";

import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
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

function toMinutes(hhmm: string): number | null {
  if (!/^\d{2}:\d{2}$/.test(hhmm)) return null;
  const [h, m] = hhmm.split(":").map(Number);
  if (h > 23 || m > 59) return null;
  return h * 60 + m;
}

function fromMinutes(total: number): string {
  const t = ((total % (24 * 60)) + 24 * 60) % (24 * 60);
  const h = Math.floor(t / 60);
  const m = t % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

/** Minutes from `from` forward to `to` (0..1439), wrapping midnight. */
export function minutesFrom(from: string, to: string): number | null {
  const a = toMinutes(from);
  const b = toMinutes(to);
  if (a == null || b == null) return null;
  return (b - a + 24 * 60) % (24 * 60);
}

/** Default cut-off = end + 6h, clamped before next office start. */
export function defaultCutoffAfterEnd(start: string, end: string): string {
  const endM = toMinutes(end);
  if (endM == null) return "23:59";
  const plus6 = fromMinutes(endM + 6 * 60);
  const fromEnd = minutesFrom(end, plus6);
  let untilStart = minutesFrom(end, start);
  if (untilStart == null) return plus6;
  if (untilStart <= 0) untilStart = 24 * 60;
  if (fromEnd != null && fromEnd >= untilStart) {
    const startM = toMinutes(start);
    if (startM == null) return plus6;
    return fromMinutes(startM - 1);
  }
  return plus6;
}

export function isCutoffInWindow(start: string, end: string, cutoff: string): boolean {
  const fromEnd = minutesFrom(end, cutoff);
  let untilStart = minutesFrom(end, start);
  if (fromEnd == null || untilStart == null) return false;
  if (untilStart <= 0) untilStart = 24 * 60;
  return fromEnd >= 0 && fromEnd < untilStart;
}

/** Product default for new locations / empty override preview (Mon–Fri 09:00–17:00). */
function blankForm(rules?: Rules, prefer?: { scopeType?: string; scopeId?: string }) {
  const start = "09:00";
  const end = "17:00";
  return {
    scopeType: (prefer?.scopeType || "TEAM").toUpperCase(),
    scopeId: prefer?.scopeId ? String(prefer.scopeId) : "",
    mode: "FIXED",
    workingDays: ["MON", "TUE", "WED", "THU", "FRI"] as string[],
    startTime: start,
    endTime: end,
    graceMinutes: String(rules?.defaultGraceMinutes ?? 15),
    halfDayHours: String(rules?.defaultHalfDayHours ?? 4),
    checkoutCutoff: String(rules?.defaultCheckoutCutoff ?? "23:59"),
    weeklyHours: "40",
  };
}

function formFromPreview(
  preview: Rec | null | undefined,
  scopeType: string,
  scopeId: string,
  rules?: Rules,
) {
  const base = blankForm(rules, { scopeType, scopeId });
  if (!preview) return base;
  const start = str(preview.startTime, base.startTime);
  const end = str(preview.endTime, base.endTime);
  const days = parseDays(preview.workingDays);
  return {
    ...base,
    mode: str(preview.mode, "FIXED").toUpperCase() === "HOURLY" ? "HOURLY" : "FIXED",
    workingDays: days.length ? days : base.workingDays,
    startTime: start,
    endTime: end,
    graceMinutes: String(preview.graceMinutes ?? rules?.defaultGraceMinutes ?? 15),
    halfDayHours: preview.halfDayHours == null ? base.halfDayHours : String(preview.halfDayHours),
    checkoutCutoff: str(preview.checkoutCutoff, rules?.defaultCheckoutCutoff ?? "23:59"),
    weeklyHours: preview.weeklyHours == null ? base.weeklyHours : String(preview.weeklyHours),
  };
}

type Props = {
  open: boolean;
  target: ScheduleEditorTarget | null;
  onClose: () => void;
  onSaved?: () => void;
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

  const [form, setForm] = useState(blankForm());
  const [editingId, setEditingId] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");
  const [lockedScope, setLockedScope] = useState(false);
  /** When false, changing end time refreshes cut-off to end+6h. */
  const cutoffTouched = useRef(false);
  /** Skip one scope-preview hydrate after we intentionally clear scopeId on scopeType change. */
  const skipScopePreview = useRef(false);

  const scopePreviewQ = useQuery({
    queryKey: ["work-schedule-for-scope", form.scopeType, form.scopeId],
    queryFn: ({ signal }) =>
      apiFetch<{
        found?: boolean;
        source?: string;
        schedule?: Rec | null;
        preview?: Rec | null;
      }>(api.hrms("work-schedules/for-scope"), {
        query: { scopeType: form.scopeType, scopeId: form.scopeId },
        signal,
      }),
    enabled:
      open &&
      !!form.scopeType &&
      !!form.scopeId &&
      (target?.mode === "new" || target?.mode === "employee"),
  });

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
    cutoffTouched.current = false;
    skipScopePreview.current = false;

    if (target.mode === "edit") {
      const r = target.row;
      setEditingId(String(r.id));
      setLockedScope(true);
      setForm(formFromPreview(r, str(r.scopeType, "LOCATION").toUpperCase(), String(r.scopeId ?? ""), rulesQ.data));
      cutoffTouched.current = true;
      return;
    }

    if (target.mode === "employee") {
      setLockedScope(true);
      setEditingId(null);
      setForm(blankForm(rulesQ.data, { scopeType: "EMPLOYEE", scopeId: String(target.employeeId) }));
      return;
    }

    setEditingId(null);
    setLockedScope(false);
    setForm(
      blankForm(rulesQ.data, {
        scopeType: target.preferScope || "TEAM",
        scopeId: target.preferScopeId != null ? String(target.preferScopeId) : "",
      }),
    );
  }, [open, target, rulesQ.data]);

  // When scope + id are chosen on "Add override", hydrate from current stored/resolved schedule.
  useEffect(() => {
    if (!open || !target || target.mode === "edit") return;
    if (!form.scopeId || !scopePreviewQ.data) return;
    if (skipScopePreview.current) {
      skipScopePreview.current = false;
      return;
    }
    const preview = (scopePreviewQ.data.preview ?? scopePreviewQ.data.schedule) as Rec | null | undefined;
    const source = String(scopePreviewQ.data.source ?? "").toUpperCase();
    const stored = scopePreviewQ.data.schedule as Rec | null | undefined;
    const ownRow =
      scopePreviewQ.data.found &&
      stored?.id != null &&
      (form.scopeType === "LOCATION" ||
        form.scopeType === "TEAM" ||
        (form.scopeType === "EMPLOYEE" && source === "EMPLOYEE"));

    setEditingId(ownRow ? String(stored!.id) : null);
    setForm(formFromPreview(preview, form.scopeType, form.scopeId, rulesQ.data));
    cutoffTouched.current = !!(preview && preview.checkoutCutoff);
  }, [open, target, form.scopeType, form.scopeId, scopePreviewQ.data, rulesQ.data]);

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
            const startM = toMinutes(v.startTime);
            const endM = toMinutes(v.endTime);
            if (startM != null && endM != null && endM <= startM) {
              ctx.addIssue({ code: "custom", path: ["endTime"], message: "End must be after start" });
            }
            if (startM != null && endM != null && endM > startM) {
              const workMinutes = endM - startM;
              const workHours = workMinutes / 60;
              if (v.checkoutCutoff) {
                if (!isCutoffInWindow(v.startTime, v.endTime, v.checkoutCutoff)) {
                  ctx.addIssue({
                    code: "custom",
                    path: ["checkoutCutoff"],
                    message: "Must be at/after office end and before next office start (may cross midnight)",
                  });
                }
              }
              if (v.halfDayHours !== "") {
                const hd = Number(v.halfDayHours);
                if (Number.isNaN(hd) || hd < 0) {
                  ctx.addIssue({ code: "custom", path: ["halfDayHours"], message: "Must be a number ≥ 0" });
                } else if (hd >= workHours) {
                  ctx.addIssue({
                    code: "custom",
                    path: ["halfDayHours"],
                    message: `Must be less than office day length (${workHours} h)`,
                  });
                }
              }
              if (v.graceMinutes >= workMinutes) {
                ctx.addIssue({
                  code: "custom",
                  path: ["graceMinutes"],
                  message: `Must be less than office day length (${workMinutes} min)`,
                });
              }
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

  function setOfficeEnd(end: string) {
    setForm((prev) => {
      const next = { ...prev, endTime: end };
      if (!cutoffTouched.current && prev.startTime && end) {
        next.checkoutCutoff = defaultCutoffAfterEnd(prev.startTime, end);
      }
      return next;
    });
  }

  function setOfficeStart(start: string) {
    setForm((prev) => {
      const next = { ...prev, startTime: start };
      if (!cutoffTouched.current && start && prev.endTime) {
        next.checkoutCutoff = defaultCutoffAfterEnd(start, prev.endTime);
      }
      return next;
    });
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
      subtitle="Person overrides team, team overrides location. Saving location/team never changes an existing personal schedule."
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
              onChange={(e) => {
                skipScopePreview.current = true;
                setEditingId(null);
                cutoffTouched.current = false;
                setForm(blankForm(rulesQ.data, { scopeType: e.target.value, scopeId: "" }));
              }}
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
            hint={
              scopePreviewQ.isFetching && form.scopeId
                ? "Loading current schedule…"
                : form.scopeId && scopePreviewQ.data
                  ? scopePreviewQ.data.found
                    ? `Showing current ${form.scopeType.toLowerCase()} schedule`
                    : `No stored ${form.scopeType.toLowerCase()} schedule — showing product defaults`
                  : undefined
            }
          >
            <select
              value={form.scopeId}
              disabled={lockedScope && target?.mode === "employee"}
              onChange={(e) => {
                cutoffTouched.current = false;
                setEditingId(null);
                setForm((prev) => ({ ...prev, scopeId: e.target.value }));
              }}
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
                <input type="time" value={form.startTime} onChange={(e) => setOfficeStart(e.target.value)} />
              </FormField>
              <FormField label="Office end" required error={errors.endTime}>
                <input type="time" value={form.endTime} onChange={(e) => setOfficeEnd(e.target.value)} />
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
              <FormField
                label="Missing check-out cut-off"
                error={errors.checkoutCutoff}
                hint="Auto-fills office end + 6 hours. Must stay at/after end and before next office start (may cross midnight)."
              >
                <input
                  type="time"
                  value={form.checkoutCutoff}
                  onChange={(e) => {
                    cutoffTouched.current = true;
                    setForm({ ...form, checkoutCutoff: e.target.value });
                  }}
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
