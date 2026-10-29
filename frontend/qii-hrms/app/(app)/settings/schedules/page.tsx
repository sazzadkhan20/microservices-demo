"use client";

import { useMemo, useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { DataTable } from "@/components/ui/DataTable";
import { FormField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { byId, useEmployeesLookup, useLocations, useTeams, type Rec } from "@/lib/api/lookups";
import { fmtNumber, str } from "@/lib/format";

const rowSchema = z.object({ id: z.union([z.number(), z.string()]) }).passthrough();
const DAYS = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"];
const SCOPES = ["LOCATION", "TEAM", "EMPLOYEE"];
const MODES = ["FIXED", "SHIFT", "FLEXIBLE"];

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

const schema = z
  .object({
    scopeType: z.string().min(1),
    scopeId: z.string().min(1, "Choose who this applies to"),
    mode: z.string().min(1),
    workingDays: z.array(z.string()).min(1, "Pick at least one working day"),
    startTime: z.string().min(1, "Start time is required"),
    endTime: z.string().min(1, "End time is required"),
    graceMinutes: z.coerce.number("Enter minutes").int().min(0).max(240),
    halfDayHours: z.string(),
    checkoutCutoff: z.string(),
  })
  .superRefine((v, ctx) => {
    if (v.startTime && v.endTime && v.endTime <= v.startTime) {
      ctx.addIssue({ code: "custom", path: ["endTime"], message: "End must be after start" });
    }
    if (v.halfDayHours && (Number.isNaN(Number(v.halfDayHours)) || Number(v.halfDayHours) < 0)) {
      ctx.addIssue({ code: "custom", path: ["halfDayHours"], message: "Must be a number ≥ 0" });
    }
  });

export default function SchedulesPage() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canManage = can("org", "manage");

  const blank = {
    scopeType: "LOCATION",
    scopeId: "",
    mode: "FIXED",
    workingDays: ["SUN", "MON", "TUE", "WED", "THU"],
    startTime: "10:00",
    endTime: "18:00",
    graceMinutes: "15",
    halfDayHours: "4",
    checkoutCutoff: "23:59",
  };
  const [editing, setEditing] = useState<Rec | "new" | null>(null);
  const [form, setForm] = useState(blank);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  const locations = useLocations();
  const teams = useTeams();
  const employees = useEmployeesLookup();
  const locName = useMemo(() => byId(locations.data, (l) => l.name), [locations.data]);
  const teamName = useMemo(() => byId(teams.data, (l) => l.name), [teams.data]);
  const empName = useMemo(() => byId(employees.data, (l) => l.fullName || `#${l.id}`), [employees.data]);

  const scopeLabel = (r: Rec) => {
    const id = String(r.scopeId);
    const t = String(r.scopeType);
    const n = t === "LOCATION" ? locName.get(id) : t === "TEAM" ? teamName.get(id) : empName.get(id);
    return n ?? `#${id}`;
  };

  function scopeOptions(type: string): { id: string; label: string }[] {
    if (type === "LOCATION") return (locations.data ?? []).map((l) => ({ id: String(l.id), label: l.name }));
    if (type === "TEAM") return (teams.data ?? []).map((l) => ({ id: String(l.id), label: l.name }));
    return (employees.data ?? []).map((l) => ({ id: String(l.id), label: l.fullName || `#${l.id}` }));
  }

  function openFor(r: Rec | "new") {
    setErr("");
    setErrors({});
    setForm(
      r === "new"
        ? blank
        : {
            scopeType: str(r.scopeType, "LOCATION"),
            scopeId: String(r.scopeId ?? ""),
            mode: str(r.mode, "FIXED"),
            workingDays: parseDays(r.workingDays),
            startTime: str(r.startTime, "10:00"),
            endTime: str(r.endTime, "18:00"),
            graceMinutes: String(r.graceMinutes ?? 15),
            halfDayHours: r.halfDayHours == null ? "" : String(r.halfDayHours),
            checkoutCutoff: str(r.checkoutCutoff, "23:59"),
          },
    );
    setEditing(r);
  }

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      editing && editing !== "new"
        ? apiFetch(api.hrms(`work-schedules/${editing.id}`), { method: "PUT", body })
        : apiFetch(api.hrms("work-schedules"), { method: "POST", body }),
    onSuccess: () => {
      toast.show(editing === "new" ? "Schedule created" : "Schedule saved");
      setEditing(null);
      void qc.invalidateQueries({ queryKey: ["work-schedules"] });
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
    save.mutate({
      scopeType: v.scopeType,
      scopeId: Number(v.scopeId),
      mode: v.mode,
      // The backend stores working days as a JSON array serialised in a string column.
      workingDays: JSON.stringify(DAYS.filter((d) => v.workingDays.includes(d))),
      startTime: v.startTime,
      endTime: v.endTime,
      graceMinutes: v.graceMinutes,
      halfDayHours: v.halfDayHours === "" ? undefined : Number(v.halfDayHours),
      checkoutCutoff: v.checkoutCutoff || undefined,
    });
  }

  return (
    <div className="stack">
      <PageHeader
        title="Schedules"
        subtitle="Working days, hours and grace periods. An employee's own schedule wins over their team's, which wins over their location's."
        actions={
          canManage ? (
            <button type="button" className="btn primary" onClick={() => openFor("new")}>
              + New schedule
            </button>
          ) : null
        }
      />
      <div className="card">
        <DataTable<Rec>
          queryKey="work-schedules"
          path={api.hrms("work-schedules")}
          itemSchema={rowSchema as never}
          search={false}
          emptyText="No schedules configured"
          rowKey={(r) => String(r.id)}
          onRowClick={canManage ? (r) => openFor(r) : undefined}
          columns={[
            { key: "scope", header: "Applies to", render: (r) => <div><b>{scopeLabel(r)}</b><div className="small muted">{str(r.scopeType)}</div></div> },
            { key: "mode", header: "Mode", render: (r) => <span className="pill info">{str(r.mode)}</span> },
            {
              key: "workingDays",
              header: "Working days",
              render: (r) => {
                const on = parseDays(r.workingDays);
                return (
                  <div className="row" style={{ gap: 4 }}>
                    {DAYS.map((d) => (
                      <span key={d} className={`code ${on.includes(d) ? "c-P" : "c-WO"}`} style={{ minWidth: 34 }}>
                        {d}
                      </span>
                    ))}
                  </div>
                );
              },
            },
            { key: "hours", header: "Hours", className: "mono", render: (r) => `${str(r.startTime)} – ${str(r.endTime)}` },
            { key: "graceMinutes", header: "Grace", className: "n", render: (r) => `${str(r.graceMinutes, "0")} min` },
            { key: "halfDayHours", header: "Half day", className: "n", render: (r) => (r.halfDayHours == null ? "—" : `${fmtNumber(r.halfDayHours)} h`) },
          ]}
        />
      </div>

      <Modal open={!!editing} onClose={() => setEditing(null)} title={editing === "new" ? "New schedule" : "Edit schedule"}>
        <form className="stack" onSubmit={submit} noValidate>
          <div className="form-grid">
            <FormField label="Applies to">
              <select
                value={form.scopeType}
                disabled={editing !== "new"}
                onChange={(e) => setForm({ ...form, scopeType: e.target.value, scopeId: "" })}
              >
                {SCOPES.map((s) => (
                  <option key={s}>{s}</option>
                ))}
              </select>
            </FormField>
            <FormField label="Who" required error={errors.scopeId}>
              <select value={form.scopeId} disabled={editing !== "new"} onChange={(e) => setForm({ ...form, scopeId: e.target.value })}>
                <option value="">Select…</option>
                {scopeOptions(form.scopeType).map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.label}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Mode">
              <select value={form.mode} onChange={(e) => setForm({ ...form, mode: e.target.value })}>
                {[...new Set([form.mode, ...MODES])].map((m) => (
                  <option key={m}>{m}</option>
                ))}
              </select>
            </FormField>
            <FormField label="Grace (minutes)" error={errors.graceMinutes}>
              <input type="number" min={0} value={form.graceMinutes} onChange={(e) => setForm({ ...form, graceMinutes: e.target.value })} />
            </FormField>
            <FormField label="Start" required error={errors.startTime}>
              <input type="time" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} />
            </FormField>
            <FormField label="End" required error={errors.endTime}>
              <input type="time" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} />
            </FormField>
            <FormField label="Half-day hours" error={errors.halfDayHours} hint="Worked hours below which a day counts as half">
              <input type="number" min={0} step="0.25" value={form.halfDayHours} onChange={(e) => setForm({ ...form, halfDayHours: e.target.value })} />
            </FormField>
            <FormField label="Check-out cut-off">
              <input type="time" value={form.checkoutCutoff} onChange={(e) => setForm({ ...form, checkoutCutoff: e.target.value })} />
            </FormField>
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
                          workingDays: e.target.checked ? [...form.workingDays, d] : form.workingDays.filter((x) => x !== d),
                        })
                      }
                    />
                    {d}
                  </label>
                ))}
              </div>
              {errors.workingDays ? <span className="field-err">{errors.workingDays}</span> : null}
            </div>
          </div>
          {err ? <div className="notice bad">{err}</div> : null}
          <div className="modal-foot">
            <button type="button" className="btn" onClick={() => setEditing(null)}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={save.isPending}>
              {save.isPending ? "Saving…" : "Save"}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
