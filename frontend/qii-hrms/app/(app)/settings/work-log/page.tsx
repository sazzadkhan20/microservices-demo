"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/components/auth/AuthProvider";
import { FormField } from "@/components/ui/FormField";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote, Loading } from "@/components/ui/QueryState";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";

type Rules = {
  workLogRule?: string;
  workLogMinPercent?: number;
  workLogPercentOf?: string;
  editDaysBack?: number;
  stepMinutes?: number;
  minMinutes?: number;
  maxMinutesPerDay?: number;
  overPunchGraceMinutes?: number;
  missingNotifyDays?: number;
};

type FormState = {
  workLogRule: "BLOCK" | "FLAG" | "OFF";
  workLogMinPercent: string;
  workLogPercentOf: "WORKED" | "EXPECTED";
  editDaysBack: string;
  stepMinutes: string;
  minMinutes: string;
  maxMinutesPerDay: string;
  overPunchGraceMinutes: string;
  missingNotifyDays: string;
};

const DEFAULTS: FormState = {
  workLogRule: "BLOCK",
  workLogMinPercent: "80",
  workLogPercentOf: "WORKED",
  editDaysBack: "7",
  stepMinutes: "15",
  minMinutes: "15",
  maxMinutesPerDay: "960",
  overPunchGraceMinutes: "60",
  missingNotifyDays: "3",
};

function toForm(r?: Rules | null): FormState {
  const rule = String(r?.workLogRule ?? "BLOCK").toUpperCase();
  const normalizedRule = rule === "FLAG" ? "FLAG" : rule === "OFF" || rule === "NONE" || rule === "DISABLED" ? "OFF" : "BLOCK";
  const pctOf = String(r?.workLogPercentOf ?? "WORKED").toUpperCase() === "EXPECTED" ? "EXPECTED" : "WORKED";
  return {
    workLogRule: normalizedRule,
    workLogMinPercent: String(r?.workLogMinPercent ?? 80),
    workLogPercentOf: pctOf,
    editDaysBack: String(r?.editDaysBack ?? 7),
    stepMinutes: String(r?.stepMinutes ?? 15),
    minMinutes: String(r?.minMinutes ?? 15),
    maxMinutesPerDay: String(r?.maxMinutesPerDay ?? 960),
    overPunchGraceMinutes: String(r?.overPunchGraceMinutes ?? 60),
    missingNotifyDays: String(r?.missingNotifyDays ?? 3),
  };
}

export default function WorkLogSettingsPage() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canView = can("attendance_settings", "view") || can("attendance_settings", "edit");
  const canEdit = can("attendance_settings", "edit");

  const [form, setForm] = useState<FormState>(DEFAULTS);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  const rulesQ = useQuery({
    queryKey: ["work-log-rules", "admin"],
    queryFn: ({ signal }) => apiFetch<Rules>(api.hrms("work-logs/rules"), { signal }),
    enabled: canView,
  });

  useEffect(() => {
    if (rulesQ.data) setForm(toForm(rulesQ.data));
  }, [rulesQ.data]);

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiFetch<Rules>(api.hrms("work-logs/rules"), { method: "PUT", body }),
    onSuccess: (data) => {
      toast.show("Work log rules saved");
      setErr("");
      setForm(toForm(data));
      void qc.invalidateQueries({ queryKey: ["work-log-rules"] });
    },
    onError: (e: Error) => setErr(e.message || "Save failed"),
  });

  if (!canView) {
    return (
      <div className="stack">
        <PageHeader title="Work log rules" subtitle="Checkout and logging policy for this tenant." />
        <div className="card empty">You do not have permission to view attendance settings.</div>
      </div>
    );
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!canEdit) return;
    const next: Record<string, string> = {};
    const pct = Number(form.workLogMinPercent);
    const editDays = Number(form.editDaysBack);
    const step = Number(form.stepMinutes);
    const minM = Number(form.minMinutes);
    const maxDay = Number(form.maxMinutesPerDay);
    const grace = Number(form.overPunchGraceMinutes);
    const notify = Number(form.missingNotifyDays);

    if (!["BLOCK", "FLAG", "OFF"].includes(form.workLogRule)) next.workLogRule = "Choose Block, Flag, or Off";
    if (Number.isNaN(pct) || pct < 0 || pct > 100) next.workLogMinPercent = "Enter 0–100";
    if (Number.isNaN(editDays) || editDays < 0 || editDays > 365) next.editDaysBack = "Enter 0–365";
    if (Number.isNaN(step) || step < 1 || step > 240) next.stepMinutes = "Enter 1–240";
    if (Number.isNaN(minM) || minM < 1 || minM > 480) next.minMinutes = "Enter 1–480";
    if (Number.isNaN(maxDay) || maxDay < 60 || maxDay > 1440) next.maxMinutesPerDay = "Enter 60–1440";
    if (Number.isNaN(grace) || grace < 0 || grace > 480) next.overPunchGraceMinutes = "Enter 0–480";
    if (Number.isNaN(notify) || notify < 0 || notify > 30) next.missingNotifyDays = "Enter 0–30";
    if (Object.keys(next).length) {
      setErrors(next);
      return;
    }
    setErrors({});
    save.mutate({
      workLogRule: form.workLogRule,
      workLogMinPercent: pct,
      workLogPercentOf: form.workLogPercentOf,
      editDaysBack: editDays,
      stepMinutes: step,
      minMinutes: minM,
      maxMinutesPerDay: maxDay,
      overPunchGraceMinutes: grace,
      missingNotifyDays: notify,
    });
  }

  return (
    <div className="stack">
      <PageHeader
        title="Work log rules"
        subtitle="Checkout gate (Block / Flag) and logging limits. Applied immediately on the next check-out."
      />
      <ErrorNote error={rulesQ.error} />
      {rulesQ.isLoading ? <Loading /> : null}

      {!rulesQ.isLoading ? (
        <form className="card stack" onSubmit={submit} noValidate>
          <h2>Check-out rule</h2>
          <p className="small muted" style={{ marginTop: -4 }}>
            <b>Block</b> refuses check-out until the day has at least one work-log entry and, when set, logged time
            covers a minimum % of worked time. <b>Flag</b> allows check-out and stamps a missing/incomplete log note.{" "}
            <b>Off</b> disables the gate.
          </p>
          <div className="form-grid">
            <FormField label="Work log rule" required error={errors.workLogRule}>
              <select
                value={form.workLogRule}
                disabled={!canEdit}
                onChange={(e) => setForm({ ...form, workLogRule: e.target.value as FormState["workLogRule"] })}
              >
                <option value="BLOCK">Block (default)</option>
                <option value="FLAG">Flag</option>
                <option value="OFF">Off</option>
              </select>
            </FormField>
            <FormField
              label="Minimum % of time"
              required
              error={errors.workLogMinPercent}
              hint="0 = any entry is enough after the hard gate; 80 is the product default"
            >
              <input
                type="number"
                min={0}
                max={100}
                step={1}
                disabled={!canEdit || form.workLogRule === "OFF"}
                value={form.workLogMinPercent}
                onChange={(e) => setForm({ ...form, workLogMinPercent: e.target.value })}
              />
            </FormField>
            <FormField label="% of" error={errors.workLogPercentOf} hint="Compared against punched so far, or expected schedule hours">
              <select
                value={form.workLogPercentOf}
                disabled={!canEdit || form.workLogRule === "OFF"}
                onChange={(e) => setForm({ ...form, workLogPercentOf: e.target.value as FormState["workLogPercentOf"] })}
              >
                <option value="WORKED">Worked (punched so far)</option>
                <option value="EXPECTED">Expected (schedule)</option>
              </select>
            </FormField>
          </div>

          <h2>Logging limits</h2>
          <div className="form-grid">
            <FormField label="Editable days back" required error={errors.editDaysBack} hint="Today + last N days; older entries are read-only">
              <input
                type="number"
                min={0}
                max={365}
                disabled={!canEdit}
                value={form.editDaysBack}
                onChange={(e) => setForm({ ...form, editDaysBack: e.target.value })}
              />
            </FormField>
            <FormField label="Step (minutes)" required error={errors.stepMinutes}>
              <input
                type="number"
                min={1}
                max={240}
                disabled={!canEdit}
                value={form.stepMinutes}
                onChange={(e) => setForm({ ...form, stepMinutes: e.target.value })}
              />
            </FormField>
            <FormField label="Minimum entry (minutes)" required error={errors.minMinutes}>
              <input
                type="number"
                min={1}
                max={480}
                disabled={!canEdit}
                value={form.minMinutes}
                onChange={(e) => setForm({ ...form, minMinutes: e.target.value })}
              />
            </FormField>
            <FormField label="Max minutes / day" required error={errors.maxMinutesPerDay} hint="960 = 16 hours">
              <input
                type="number"
                min={60}
                max={1440}
                step={15}
                disabled={!canEdit}
                value={form.maxMinutesPerDay}
                onChange={(e) => setForm({ ...form, maxMinutesPerDay: e.target.value })}
              />
            </FormField>
            <FormField
              label="Over-punch grace (minutes)"
              required
              error={errors.overPunchGraceMinutes}
              hint="Logging more than punched + grace is allowed but flagged"
            >
              <input
                type="number"
                min={0}
                max={480}
                disabled={!canEdit}
                value={form.overPunchGraceMinutes}
                onChange={(e) => setForm({ ...form, overPunchGraceMinutes: e.target.value })}
              />
            </FormField>
            <FormField
              label="Missing-log notify days"
              required
              error={errors.missingNotifyDays}
              hint="Notify employee + manager once/day for worked days with no log (excluding today)"
            >
              <input
                type="number"
                min={0}
                max={30}
                disabled={!canEdit}
                value={form.missingNotifyDays}
                onChange={(e) => setForm({ ...form, missingNotifyDays: e.target.value })}
              />
            </FormField>
          </div>

          {err ? <div className="notice bad">{err}</div> : null}
          {canEdit ? (
            <div>
              <button type="submit" className="btn primary" disabled={save.isPending}>
                {save.isPending ? "Saving…" : "Save rules"}
              </button>
            </div>
          ) : (
            <p className="small muted">You can view these rules but need attendance settings edit to change them.</p>
          )}
        </form>
      ) : null}
    </div>
  );
}
