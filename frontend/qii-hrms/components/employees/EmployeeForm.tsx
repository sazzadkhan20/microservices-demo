"use client";

import { useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { FormField } from "@/components/ui/FormField";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { isoDate, todayIso } from "@/lib/format";
import {
  useDepartments,
  useDesignations,
  useEmployeesLookup,
  useLocations,
  useRoles,
  useTeams,
} from "@/lib/api/lookups";
import {
  EMPLOYEE_STATUSES,
  EMPLOYMENT_TYPES,
  WORK_PATTERNS,
  employeeSchema,
  type Employee,
} from "@/lib/schemas/employee";

export type EmployeeFormValues = {
  fullName: string;
  preferredName: string;
  email: string;
  phone: string;
  personalEmail: string;
  jobTitle: string;
  departmentId: string;
  designationId: string;
  managerId: string;
  teamId: string;
  locationId: string;
  employmentType: string;
  workPattern: string;
  startDate: string;
  probationEnd: string;
  contractEnd: string;
  payCurrency: string;
  baseSalary: string;
  hourlyRate: string;
  status: string;
  roleCodes: string[];
  attachOnboardingChecklist: boolean;
};

const s = (v: unknown) => (v === null || v === undefined ? "" : String(v));

export function valuesFrom(e?: Employee | null): EmployeeFormValues {
  return {
    fullName: s(e?.fullName),
    preferredName: s(e?.preferredName),
    email: s(e?.email),
    phone: s(e?.phone),
    personalEmail: s(e?.personalEmail),
    jobTitle: s(e?.jobTitle),
    departmentId: s(e?.departmentId),
    designationId: s(e?.designationId),
    managerId: s(e?.managerId),
    teamId: s(e?.teamId),
    locationId: s(e?.locationId),
    employmentType: s(e?.employmentType) || "FULL_TIME",
    workPattern: s(e?.workPattern) || "FIXED",
    startDate: e ? isoDate(e.startDate) : todayIso(),
    probationEnd: e ? isoDate(e.probationEnd) : "",
    contractEnd: e ? isoDate(e.contractEnd) : "",
    payCurrency: s(e?.payCurrency),
    baseSalary: s(e?.baseSalary),
    hourlyRate: s(e?.hourlyRate),
    status: s(e?.status) || "Active",
    roleCodes: e?.roles ?? [],
    attachOnboardingChecklist: !e,
  };
}

const dateStr = z.string().regex(/^(\d{4}-\d{2}-\d{2})?$/, "Use a valid date");
const money = z
  .string()
  .refine((v) => v === "" || (!Number.isNaN(Number(v)) && Number(v) >= 0), "Must be a number ≥ 0");

function buildSchema(mode: "create" | "edit") {
  return z
    .object({
      fullName: z.string().trim().min(1, "Full name is required"),
      preferredName: z.string(),
      email: z.string().trim().min(1, "Email is required").email("Enter a valid email"),
      phone: z.string(),
      personalEmail: z
        .string()
        .trim()
        .refine((v) => v === "" || z.string().email().safeParse(v).success, "Enter a valid email"),
      jobTitle: z.string(),
      departmentId: z.string(),
      designationId: z.string(),
      managerId: z.string(),
      teamId: z.string(),
      locationId: z.string(),
      employmentType: z.string(),
      workPattern: z.string(),
      startDate: dateStr,
      probationEnd: dateStr,
      contractEnd: dateStr,
      payCurrency: z
        .string()
        .trim()
        .refine((v) => v === "" || /^[A-Za-z]{3}$/.test(v), "Use a 3-letter currency code (e.g. BDT)"),
      baseSalary: money,
      hourlyRate: money,
      status: z.string(),
      roleCodes: z.array(z.string()),
      attachOnboardingChecklist: z.boolean(),
    })
    .superRefine((v, ctx) => {
      if (mode === "create") {
        if (!v.designationId) {
          ctx.addIssue({ code: "custom", path: ["designationId"], message: "Designation is required" });
        }
        if (!v.locationId) {
          ctx.addIssue({ code: "custom", path: ["locationId"], message: "Location is required" });
        }
        if (!v.departmentId && !v.teamId) {
          ctx.addIssue({
            code: "custom",
            path: ["teamId"],
            message: "Choose a team (or department)",
          });
        }
      }
      if (v.startDate && v.probationEnd && v.probationEnd < v.startDate) {
        ctx.addIssue({ code: "custom", path: ["probationEnd"], message: "Must be on/after start date" });
      }
      if (v.startDate && v.contractEnd && v.contractEnd < v.startDate) {
        ctx.addIssue({ code: "custom", path: ["contractEnd"], message: "Must be on/after start date" });
      }
    });
}

const num = (v: string) => (v === "" ? undefined : Number(v));
const idOrUndef = (v: string) => (v === "" ? undefined : Number(v));
const txt = (v: string) => (v.trim() === "" ? undefined : v.trim());

/** Build the EmployeeRequest body. Undefined fields are omitted by JSON.stringify. */
export function toRequest(v: EmployeeFormValues, mode: "create" | "edit") {
  return {
    fullName: v.fullName.trim(),
    email: v.email.trim(),
    preferredName: txt(v.preferredName),
    jobTitle: txt(v.jobTitle),
    departmentId: idOrUndef(v.departmentId),
    designationId: idOrUndef(v.designationId),
    managerId: idOrUndef(v.managerId),
    teamId: idOrUndef(v.teamId),
    locationId: idOrUndef(v.locationId),
    employmentType: v.employmentType || undefined,
    startDate: txt(v.startDate),
    probationEnd: txt(v.probationEnd),
    contractEnd: txt(v.contractEnd),
    payCurrency: txt(v.payCurrency)?.toUpperCase(),
    workPattern: v.workPattern || undefined,
    baseSalary: num(v.baseSalary),
    hourlyRate: num(v.hourlyRate),
    phone: txt(v.phone),
    personalEmail: txt(v.personalEmail),
    ...(mode === "create"
      ? { roleCodes: v.roleCodes, attachOnboardingChecklist: v.attachOnboardingChecklist }
      : { status: v.status || undefined }),
  };
}

type Props = {
  mode: "create" | "edit";
  employee?: Employee | null;
  /** Which part of the form to show when editing. Create always shows everything. */
  sections?: "all" | "profile" | "employment";
  onSaved: (e: Employee) => void;
  onCancel?: () => void;
  submitLabel?: string;
};

export function EmployeeForm({ mode, employee, sections = "all", onSaved, onCancel, submitLabel }: Props) {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const [v, setV] = useState<EmployeeFormValues>(() => valuesFrom(employee));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");

  const canRolesView = can("roles", "view");
  const departments = useDepartments();
  const designations = useDesignations();
  const teams = useTeams();
  const locations = useLocations();
  const employees = useEmployeesLookup();
  const roles = useRoles(canRolesView);
  const nextCode = useQuery({
    queryKey: ["employees-next-code"],
    queryFn: ({ signal }) => apiFetch<{ employeeCode?: string }>(api.hrms("employees/next-code"), { signal }),
    enabled: mode === "create" && can("employees", "create"),
    staleTime: 0,
  });

  const schema = useMemo(() => buildSchema(mode), [mode]);
  const showProfile = mode === "create" || sections !== "employment";
  const showEmployment = mode === "create" || sections !== "profile";

  function set<K extends keyof EmployeeFormValues>(k: K, val: EmployeeFormValues[K]) {
    setV((cur) => ({ ...cur, [k]: val }));
    if (errors[k]) setErrors((e) => ({ ...e, [k]: "" }));
  }

  function onTeamChange(teamId: string) {
    const team = (teams.data ?? []).find((t) => String(t.id) === teamId);
    setV((cur) => ({
      ...cur,
      teamId,
      // FR-EMP-05: a team implies its department when none is chosen yet.
      departmentId: cur.departmentId || (team?.departmentId ? String(team.departmentId) : ""),
    }));
  }

  function onLocationChange(locationId: string) {
    const loc = (locations.data ?? []).find((l) => String(l.id) === locationId);
    setV((cur) => ({ ...cur, locationId, payCurrency: cur.payCurrency || loc?.defaultCurrency || "" }));
  }

  const save = useMutation({
    mutationFn: async (values: EmployeeFormValues) => {
      const body = toRequest(values, mode);
      const saved: Employee =
        mode === "create"
          ? await apiFetch(api.hrms("employees"), { method: "POST", body, schema: employeeSchema })
          : await apiFetch(api.hrms(`employees/${employee!.id}`), { method: "PUT", body, schema: employeeSchema });
      let result: Employee = saved;
      if (mode === "edit" && showEmployment && can("employees", "edit") && canRolesView) {
        const before = [...(employee?.roles ?? [])].sort().join(",");
        const after = [...values.roleCodes].sort().join(",");
        if (before !== after) {
          await apiFetch(api.hrms(`employees/${employee!.id}/roles`), {
            method: "POST",
            body: { roleCodes: values.roleCodes },
          });
          result = { ...saved, roles: values.roleCodes };
        }
      }
      return result;
    },
    onSuccess: (saved) => {
      void qc.invalidateQueries({ queryKey: ["employees"] });
      void qc.invalidateQueries({ queryKey: ["employee", String(saved.id)] });
      void qc.invalidateQueries({ queryKey: ["lookup", "employees"] });
      toast.show(mode === "create" ? `Employee ${saved.employeeCode ?? ""} created` : "Employee saved");
      onSaved(saved);
    },
    onError: (e: Error) => setFormError(e.message),
  });

  function submit(ev: FormEvent) {
    ev.preventDefault();
    setFormError("");
    const parsed = schema.safeParse(v);
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const issue of parsed.error.issues) {
        const key = String(issue.path[0] ?? "form");
        if (!next[key]) next[key] = issue.message;
      }
      setErrors(next);
      setFormError("Please fix the highlighted fields.");
      return;
    }
    setErrors({});
    save.mutate(v);
  }

  const mgrs = (employees.data ?? []).filter((e) => !employee || String(e.id) !== String(employee.id));
  const roleList = roles.data ?? [];

  return (
    <form className="stack" onSubmit={submit} noValidate>
      {showProfile ? (
        <div className="stack">
          {mode === "create" ? <h3>Profile</h3> : null}
          <div className="form-grid">
            {mode === "create" ? (
              <FormField label="Employee code" hint="Generated automatically on save.">
                <input type="text" value={nextCode.data?.employeeCode ?? "Auto"} disabled readOnly />
              </FormField>
            ) : (
              <FormField label="Employee code">
                <input type="text" value={employee?.employeeCode ?? ""} disabled readOnly />
              </FormField>
            )}
            <FormField label="Full name" required error={errors.fullName}>
              <input type="text" value={v.fullName} onChange={(e) => set("fullName", e.target.value)} />
            </FormField>
            <FormField label="Preferred name" error={errors.preferredName}>
              <input type="text" value={v.preferredName} onChange={(e) => set("preferredName", e.target.value)} />
            </FormField>
            <FormField label="Work email" required error={errors.email}>
              <input type="email" value={v.email} onChange={(e) => set("email", e.target.value)} />
            </FormField>
            <FormField label="Phone" error={errors.phone}>
              <input type="tel" value={v.phone} onChange={(e) => set("phone", e.target.value)} />
            </FormField>
            <FormField label="Personal email" error={errors.personalEmail}>
              <input type="email" value={v.personalEmail} onChange={(e) => set("personalEmail", e.target.value)} />
            </FormField>
          </div>
        </div>
      ) : null}

      {showEmployment ? (
        <div className="stack">
          {mode === "create" ? <h3>Employment</h3> : null}
          <div className="form-grid">
            <FormField label="Job title" hint="Defaults to the designation name when blank." error={errors.jobTitle}>
              <input type="text" value={v.jobTitle} onChange={(e) => set("jobTitle", e.target.value)} />
            </FormField>
            <FormField label="Designation" required={mode === "create"} error={errors.designationId}>
              <select value={v.designationId} onChange={(e) => set("designationId", e.target.value)}>
                <option value="">— Select —</option>
                {(designations.data ?? []).map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Team" required={mode === "create"} error={errors.teamId}>
              <select value={v.teamId} onChange={(e) => onTeamChange(e.target.value)}>
                <option value="">— Select —</option>
                {(teams.data ?? []).map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField
              label="Department"
              required={mode === "create"}
              hint="Picking a team fills this in."
              error={errors.departmentId}
            >
              <select value={v.departmentId} onChange={(e) => set("departmentId", e.target.value)}>
                <option value="">— Select —</option>
                {(departments.data ?? []).map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Manager" error={errors.managerId}>
              <select value={v.managerId} onChange={(e) => set("managerId", e.target.value)}>
                <option value="">— None —</option>
                {mgrs.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.fullName} {m.employeeCode ? `(${m.employeeCode})` : ""}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Location" required={mode === "create"} error={errors.locationId}>
              <select value={v.locationId} onChange={(e) => onLocationChange(e.target.value)}>
                <option value="">— Select —</option>
                {(locations.data ?? []).map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.name}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Employment type" error={errors.employmentType}>
              <select value={v.employmentType} onChange={(e) => set("employmentType", e.target.value)}>
                {[...new Set([v.employmentType, ...EMPLOYMENT_TYPES].filter(Boolean))].map((t) => (
                  <option key={t} value={t}>
                    {t.replace(/_/g, " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase())}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Work pattern" error={errors.workPattern}>
              <select value={v.workPattern} onChange={(e) => set("workPattern", e.target.value)}>
                {[...new Set([v.workPattern, ...WORK_PATTERNS].filter(Boolean))].map((t) => (
                  <option key={t} value={t}>
                    {t.charAt(0) + t.slice(1).toLowerCase()}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Start date" error={errors.startDate}>
              <input type="date" value={v.startDate} onChange={(e) => set("startDate", e.target.value)} />
            </FormField>
            <FormField label="Probation end" error={errors.probationEnd}>
              <input type="date" value={v.probationEnd} onChange={(e) => set("probationEnd", e.target.value)} />
            </FormField>
            <FormField label="Contract end" error={errors.contractEnd}>
              <input type="date" value={v.contractEnd} onChange={(e) => set("contractEnd", e.target.value)} />
            </FormField>
            <FormField label="Pay currency" hint="Follows the location by default." error={errors.payCurrency}>
              <input
                type="text"
                maxLength={3}
                value={v.payCurrency}
                onChange={(e) => set("payCurrency", e.target.value.toUpperCase())}
              />
            </FormField>
            <FormField label="Base salary" error={errors.baseSalary}>
              <input
                type="number"
                min={0}
                step="0.01"
                value={v.baseSalary}
                onChange={(e) => set("baseSalary", e.target.value)}
              />
            </FormField>
            <FormField label="Hourly rate" error={errors.hourlyRate}>
              <input
                type="number"
                min={0}
                step="0.01"
                value={v.hourlyRate}
                onChange={(e) => set("hourlyRate", e.target.value)}
              />
            </FormField>
            {mode === "edit" ? (
              <FormField label="Status" error={errors.status}>
                <select value={v.status} onChange={(e) => set("status", e.target.value)}>
                  {[...new Set([v.status, ...EMPLOYEE_STATUSES].filter(Boolean))].map((st) => (
                    <option key={st} value={st}>
                      {st}
                    </option>
                  ))}
                </select>
              </FormField>
            ) : null}
            {canRolesView && roleList.length > 0 && (mode === "create" || can("employees", "edit")) ? (
              <div className="field full">
                <span>Roles</span>
                <div className="checks">
                  {roleList
                    .filter((r) => r.active !== false)
                    .map((r) => (
                      <label key={r.code}>
                        <input
                          type="checkbox"
                          checked={v.roleCodes.includes(r.code)}
                          onChange={(e) =>
                            set(
                              "roleCodes",
                              e.target.checked ? [...v.roleCodes, r.code] : v.roleCodes.filter((c) => c !== r.code),
                            )
                          }
                        />
                        {r.name || r.code}
                      </label>
                    ))}
                </div>
                <small>A person with no role cannot sign in (access pending).</small>
              </div>
            ) : null}
            {mode === "create" ? (
              <div className="checks full">
                <label>
                  <input
                    type="checkbox"
                    checked={v.attachOnboardingChecklist}
                    onChange={(e) => set("attachOnboardingChecklist", e.target.checked)}
                  />
                  Attach onboarding checklist
                </label>
              </div>
            ) : null}
          </div>
        </div>
      ) : null}

      {formError ? <div className="notice bad">{formError}</div> : null}
      <div className="row">
        <button type="submit" className="btn primary" disabled={save.isPending}>
          {save.isPending ? "Saving…" : (submitLabel ?? (mode === "create" ? "Create employee" : "Save changes"))}
        </button>
        {onCancel ? (
          <button type="button" className="btn" onClick={onCancel} disabled={save.isPending}>
            Cancel
          </button>
        ) : null}
      </div>
    </form>
  );
}
