"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
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
  CONTRACT_TYPES_NEED_END,
  EMPLOYEE_STATUSES,
  EMPLOYMENT_TYPES,
  OFFBOARD_STATUSES,
  WORK_EMAIL_DOMAIN,
  WORK_EMAIL_DOMAIN_ALIASES,
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
  address: string;
  emergencyName: string;
  emergencyRelationship: string;
  emergencyPhone: string;
  jobTitle: string;
  departmentId: string;
  designationId: string;
  managerId: string;
  /** When the new reporting line takes effect (edit + manager change). */
  managerEffectiveFrom: string;
  teamId: string;
  locationId: string;
  employmentType: string;
  workPattern: string;
  startDate: string;
  probationEnd: string;
  contractEnd: string;
  payCurrency: string;
  payType: string;
  baseSalary: string;
  hourlyRate: string;
  status: string;
  exitDate: string;
  exitReason: string;
  offboardOverrideReason: string;
  reassignReportsTo: string;
  roleCodes: string[];
  attachOnboardingChecklist: boolean;
  /** Only used when tenant employeeCodeMode is MANUAL or OPTIONAL. */
  employeeCode: string;
};

const s = (v: unknown) => (v === null || v === undefined ? "" : String(v));

function customStr(e: Employee | null | undefined, key: string): string {
  if (!e) return "";
  const cf = (e as Employee & { customFields?: Record<string, unknown> }).customFields;
  const direct = (e as Record<string, unknown>)[key];
  if (direct != null && String(direct).trim()) return String(direct);
  if (cf && cf[key] != null) return String(cf[key]);
  return "";
}

export type CreateOptions = {
  employeeCodeMode?: string;
  employeeCodePattern?: string | null;
  employeeCodePrefix?: string | null;
  employeeCodeDigits?: number | null;
  nextEmployeeCode?: string | null;
  defaultCreateRoleCodes?: string[];
  workEmailDomain?: string | null;
  workEmailDomains?: string[];
  workPatterns?: string[];
  contractTypesRequiringEnd?: string[];
  employmentStatuses?: string[];
  requiredForActive?: string[];
  requireManagerOnCreate?: boolean;
};

export function valuesFrom(e?: Employee | null, defaults?: { roleCodes?: string[] }): EmployeeFormValues {
  return {
    fullName: s(e?.fullName),
    preferredName: s(e?.preferredName),
    email: s(e?.email),
    phone: s(e?.phone),
    personalEmail: s(e?.personalEmail),
    address: customStr(e, "address"),
    emergencyName: customStr(e, "emergencyName"),
    emergencyRelationship: customStr(e, "emergencyRelationship"),
    emergencyPhone: customStr(e, "emergencyPhone"),
    jobTitle: s(e?.jobTitle),
    departmentId: s(e?.departmentId),
    designationId: s(e?.designationId),
    managerId: s(e?.managerId),
    managerEffectiveFrom: todayIso(),
    teamId: s(e?.teamId),
    locationId: s(e?.locationId),
    employmentType: s(e?.employmentType) || "FULL_TIME",
    workPattern: s(e?.workPattern) || "LOCATION_DEFAULT",
    startDate: e ? isoDate(e.startDate) : todayIso(),
    probationEnd: e ? isoDate(e.probationEnd) : "",
    contractEnd: e ? isoDate(e.contractEnd) : "",
    payCurrency: s(e?.payCurrency),
    payType: customStr(e, "payType") || "MONTHLY",
    baseSalary: s(e?.baseSalary),
    hourlyRate: s(e?.hourlyRate),
    status: s(e?.status) || "Active",
    exitDate: customStr(e, "exitDate") || isoDate((e as { exitDate?: unknown } | undefined)?.exitDate) || "",
    exitReason: customStr(e, "exitReason"),
    offboardOverrideReason: "",
    reassignReportsTo: "",
    roleCodes: e?.roles ?? defaults?.roleCodes ?? ["EMPLOYEE"],
    attachOnboardingChecklist: !e,
    employeeCode: "",
  };
}

const dateStr = z.string().regex(/^(\d{4}-\d{2}-\d{2})?$/, "Use a valid date");
const money = z
  .string()
  .refine((v) => v === "" || (!Number.isNaN(Number(v)) && Number(v) >= 0), "Must be a number ≥ 0");

/** Walk the current manager chain — refuse assigning someone who already reports to this person. */
export function wouldCycle(
  employeeId: string | undefined,
  newManagerId: string,
  managerById: Map<string, string | undefined>,
): boolean {
  if (!newManagerId) return false;
  if (employeeId && newManagerId === employeeId) return true;
  let cur: string | undefined = newManagerId;
  let guard = 0;
  while (cur && guard++ < 64) {
    if (employeeId && cur === employeeId) return true;
    cur = managerById.get(cur);
  }
  return false;
}

function buildSchema(
  mode: "create" | "edit",
  opts: {
    employeeId?: string;
    originalManagerId?: string;
    managerById: Map<string, string | undefined>;
    codeMode?: string;
    emailDomains?: string[];
    requiredForActive?: string[];
    requireManagerOnCreate?: boolean;
  },
) {
  const required = new Set((opts.requiredForActive ?? []).map((f) => f.toLowerCase()));
  const managerRequired =
    mode === "create"
      && (opts.requireManagerOnCreate !== false || required.has("managerid"));
  const emailDomains =
    opts.emailDomains && opts.emailDomains.length > 0
      ? opts.emailDomains
      : [WORK_EMAIL_DOMAIN, ...WORK_EMAIL_DOMAIN_ALIASES];
  const primaryDomain = emailDomains[0] ?? WORK_EMAIL_DOMAIN;
  return z
    .object({
      fullName: z.string().trim().min(1, "Full name is required"),
      preferredName: z.string(),
      email: z
        .string()
        .trim()
        .min(1, "Email is required")
        .email("Enter a valid email")
        .refine(
          (v) => {
            const lower = v.toLowerCase();
            return emailDomains.some((d) => lower.endsWith(`@${String(d).toLowerCase()}`));
          },
          `Work email must end with @${primaryDomain}`,
        ),
      phone: z.string(),
      personalEmail: z
        .string()
        .trim()
        .refine((v) => v === "" || z.string().email().safeParse(v).success, "Enter a valid email"),
      address: z.string(),
      emergencyName: z.string(),
      emergencyRelationship: z.string(),
      emergencyPhone: z.string(),
      jobTitle: z.string(),
      departmentId: z.string(),
      designationId: z.string(),
      managerId: z.string(),
      managerEffectiveFrom: dateStr,
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
      payType: z.string(),
      baseSalary: money,
      hourlyRate: money,
      status: z.string(),
      exitDate: dateStr,
      exitReason: z.string(),
      offboardOverrideReason: z.string(),
      reassignReportsTo: z.string(),
      roleCodes: z.array(z.string()),
      attachOnboardingChecklist: z.boolean(),
      employeeCode: z.string(),
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
        if (managerRequired && !v.managerId) {
          ctx.addIssue({
            code: "custom",
            path: ["managerId"],
            message: "Manager is required — leave approvals go to the reporting manager first",
          });
        }
        if ((opts.codeMode ?? "AUTO").toUpperCase() === "MANUAL" && !v.employeeCode.trim()) {
          ctx.addIssue({ code: "custom", path: ["employeeCode"], message: "Employee code is required" });
        }
      }
      if (mode === "edit" && required.has("managerid") && !OFFBOARD_STATUSES.has(v.status) && !v.managerId) {
        ctx.addIssue({
          code: "custom",
          path: ["managerId"],
          message: "Manager is required for active employees",
        });
      }
      if (v.startDate && v.probationEnd && v.probationEnd < v.startDate) {
        ctx.addIssue({ code: "custom", path: ["probationEnd"], message: "Must be on/after start date" });
      }
      if (v.startDate && v.contractEnd && v.contractEnd < v.startDate) {
        ctx.addIssue({ code: "custom", path: ["contractEnd"], message: "Must be on/after start date" });
      }
      if (CONTRACT_TYPES_NEED_END.has(v.employmentType.toUpperCase()) && !v.contractEnd) {
        ctx.addIssue({
          code: "custom",
          path: ["contractEnd"],
          message: "Contract / internship end date is required for this employment type",
        });
      }
      if (OFFBOARD_STATUSES.has(v.status)) {
        if (!v.exitDate) {
          ctx.addIssue({ code: "custom", path: ["exitDate"], message: "Exit date is required" });
        }
        if (!v.exitReason.trim()) {
          ctx.addIssue({ code: "custom", path: ["exitReason"], message: "Exit reason is required" });
        }
      }
      if (v.managerId && wouldCycle(opts.employeeId, v.managerId, opts.managerById)) {
        ctx.addIssue({
          code: "custom",
          path: ["managerId"],
          message:
            "That person reports to this employee (directly or indirectly), so this would create a loop.",
        });
      }
      const managerChanged =
        mode === "edit" && v.managerId !== (opts.originalManagerId ?? "");
      if (managerChanged && !v.managerEffectiveFrom) {
        ctx.addIssue({
          code: "custom",
          path: ["managerEffectiveFrom"],
          message: "Choose when the new manager takes effect",
        });
      }
      // Default pack rule: past effective dates are refused so history for past approvals stays intact.
      if (managerChanged && v.managerEffectiveFrom && v.managerEffectiveFrom < todayIso()) {
        ctx.addIssue({
          code: "custom",
          path: ["managerEffectiveFrom"],
          message: "Effective date cannot be in the past — use today or a future date",
        });
      }
    });
}

const num = (v: string) => (v === "" ? undefined : Number(v));
const idOrUndef = (v: string) => (v === "" ? undefined : Number(v));
const txt = (v: string) => (v.trim() === "" ? undefined : v.trim());

/** Build the EmployeeRequest body. Undefined fields are omitted by JSON.stringify. */
export function toRequest(
  v: EmployeeFormValues,
  mode: "create" | "edit",
  opts?: { originalManagerId?: string },
) {
  const managerChanged = mode === "edit" && v.managerId !== (opts?.originalManagerId ?? "");
  return {
    fullName: v.fullName.trim(),
    email: v.email.trim(),
    preferredName: txt(v.preferredName),
    jobTitle: txt(v.jobTitle),
    departmentId: idOrUndef(v.departmentId),
    designationId: idOrUndef(v.designationId),
    managerId: idOrUndef(v.managerId),
    ...(managerChanged || (mode === "create" && v.managerId)
      ? { managerEffectiveFrom: txt(v.managerEffectiveFrom) || todayIso() }
      : {}),
    teamId: idOrUndef(v.teamId),
    locationId: idOrUndef(v.locationId),
    employmentType: v.employmentType || undefined,
    startDate: txt(v.startDate),
    probationEnd: txt(v.probationEnd),
    contractEnd: txt(v.contractEnd),
    payCurrency: txt(v.payCurrency)?.toUpperCase(),
    workPattern: v.workPattern || undefined,
    payType: txt(v.payType),
    baseSalary: num(v.baseSalary),
    hourlyRate: num(v.hourlyRate),
    phone: txt(v.phone),
    personalEmail: txt(v.personalEmail),
    address: txt(v.address),
    emergencyName: txt(v.emergencyName),
    emergencyRelationship: txt(v.emergencyRelationship),
    emergencyPhone: txt(v.emergencyPhone),
    ...(mode === "create"
      ? {
          roleCodes: v.roleCodes,
          attachOnboardingChecklist: v.attachOnboardingChecklist,
          ...(txt(v.employeeCode) ? { employeeCode: txt(v.employeeCode) } : {}),
        }
      : {
          status: v.status || undefined,
          ...(OFFBOARD_STATUSES.has(v.status)
            ? {
                exitDate: txt(v.exitDate),
                exitReason: txt(v.exitReason),
                offboardOverrideReason: txt(v.offboardOverrideReason),
                reassignReportsTo: idOrUndef(v.reassignReportsTo),
              }
            : {}),
        }),
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
  const { can, me } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const [v, setV] = useState<EmployeeFormValues>(() => valuesFrom(employee));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");

  /** Person↔role linking is roles:assign (not employees:edit). Matrix edit is roles:manage. */
  const canRolesAssign = can("roles", "assign");
  /** HR uses salary:edit; payroll roles use payroll:view/run. */
  const canSalary =
    can("salary", "edit") || can("salary", "view") || can("payroll", "run") || can("payroll", "view");
  const departments = useDepartments();
  const designations = useDesignations();
  const teams = useTeams();
  const locations = useLocations();
  const employees = useEmployeesLookup();
  const roles = useRoles(canRolesAssign || mode === "edit");
  const createOpts = useQuery({
    queryKey: ["employees-create-options"],
    queryFn: ({ signal }) => apiFetch<CreateOptions>(api.hrms("employees/create-options"), { signal }),
    enabled: mode === "create" && can("employees", "create"),
    staleTime: 60_000,
  });
  const codeMode = (createOpts.data?.employeeCodeMode ?? "AUTO").toUpperCase();
  const defaultRoles = createOpts.data?.defaultCreateRoleCodes ?? ["EMPLOYEE"];
  const emailDomain =
    createOpts.data?.workEmailDomain ||
    createOpts.data?.workEmailDomains?.[0] ||
    WORK_EMAIL_DOMAIN;

  // Once create-options load, seed default role checkboxes when still empty.
  useEffect(() => {
    if (mode !== "create" || !createOpts.data) return;
    setV((cur) => {
      if (cur.roleCodes.length > 0) return cur;
      return { ...cur, roleCodes: [...defaultRoles] };
    });
  }, [mode, createOpts.data, defaultRoles]);

  const actorRoleCodes = useMemo(
    () => new Set((me.roles ?? []).map((c) => String(c).toUpperCase())),
    [me.roles],
  );
  const actorHasWildcard = useMemo(
    () => (me.permissions ?? []).some((p) => p.trim().toLowerCase() === "*:*"),
    [me.permissions],
  );
  /** Match backend assignable_by gate (blank = anyone with roles edit; else actor must hold listed role). */
  const canAssignRole = useCallback(
    (r: { assignableBy?: string | null }) => {
      const gate = (r.assignableBy ?? "").trim();
      if (!gate) return true;
      if (actorHasWildcard) return true;
      const allowed = gate
        .split(",")
        .map((s) => s.trim().toUpperCase())
        .filter(Boolean);
      if (allowed.length === 0) return true;
      return allowed.some((code) => actorRoleCodes.has(code));
    },
    [actorHasWildcard, actorRoleCodes],
  );
  const protectFromOffboard = useMemo(() => {
    const codes = new Set((employee?.roles ?? v.roleCodes ?? []).map((c) => String(c).toUpperCase()));
    return (roles.data ?? []).some((r) => codes.has(String(r.code).toUpperCase()) && r.protectFromOffboard);
  }, [employee?.roles, v.roleCodes, roles.data]);

  const originalManagerId = s(employee?.managerId);
  const managerById = useMemo(() => {
    const map = new Map<string, string | undefined>();
    for (const e of employees.data ?? []) {
      map.set(String(e.id), e.managerId == null ? undefined : String(e.managerId));
    }
    return map;
  }, [employees.data]);
  const schema = useMemo(
    () =>
      buildSchema(mode, {
        employeeId: employee ? String(employee.id) : undefined,
        originalManagerId,
        managerById,
        codeMode,
        emailDomains: createOpts.data?.workEmailDomains,
        requiredForActive: createOpts.data?.requiredForActive,
        requireManagerOnCreate: createOpts.data?.requireManagerOnCreate,
      }),
    [
      mode,
      employee,
      originalManagerId,
      managerById,
      codeMode,
      createOpts.data?.workEmailDomains,
      createOpts.data?.requiredForActive,
      createOpts.data?.requireManagerOnCreate,
    ],
  );
  const managerRequired =
    (mode === "create" && createOpts.data?.requireManagerOnCreate !== false) ||
    (createOpts.data?.requiredForActive ?? []).some((f) => f.toLowerCase() === "managerid");
  const showProfile = mode === "create" || sections !== "employment";
  const showEmployment = mode === "create" || sections !== "profile";
  const managerChanged = mode === "edit" && v.managerId !== originalManagerId;
  const mgrName = (id: string) =>
    (employees.data ?? []).find((e) => String(e.id) === id)?.fullName ?? "That person";

  function set<K extends keyof EmployeeFormValues>(k: K, val: EmployeeFormValues[K]) {
    setV((cur) => ({ ...cur, [k]: val }));
    if (errors[k as string]) setErrors((e) => ({ ...e, [k]: "" }));
    if (formError) setFormError("");
  }

  function onTeamChange(teamId: string) {
    const team = (teams.data ?? []).find((t) => String(t.id) === teamId);
    setV((cur) => ({
      ...cur,
      teamId,
      // FR-EMP-05: choosing a team always sets the department from the team.
      departmentId: team?.departmentId ? String(team.departmentId) : cur.departmentId,
    }));
    if (errors.teamId) setErrors((e) => ({ ...e, teamId: "" }));
    if (errors.departmentId) setErrors((e) => ({ ...e, departmentId: "" }));
  }

  function onLocationChange(locationId: string) {
    const loc = (locations.data ?? []).find((l) => String(l.id) === locationId);
    setV((cur) => ({ ...cur, locationId, payCurrency: cur.payCurrency || loc?.defaultCurrency || "" }));
  }

  const save = useMutation({
    mutationFn: async (values: EmployeeFormValues) => {
      const body = toRequest(values, mode, { originalManagerId }) as Record<string, unknown>;
      if (!canSalary) {
        delete body.baseSalary;
        delete body.hourlyRate;
        delete body.payType;
      }
      if (mode === "create") {
        // AUTO: never send a client code — backend generates from pack prefix/digits.
        if (codeMode === "AUTO") {
          delete body.employeeCode;
        }
        // Without roles:assign, omit roleCodes so backend applies defaultCreateRoleCodes (EMPLOYEE).
        if (!canRolesAssign) {
          delete body.roleCodes;
        }
      }
      const saved: Employee =
        mode === "create"
          ? await apiFetch(api.hrms("employees"), { method: "POST", body, schema: employeeSchema })
          : await apiFetch(api.hrms(`employees/${employee!.id}`), { method: "PUT", body, schema: employeeSchema });
      return saved;
    },
    onSuccess: (saved) => {
      void qc.invalidateQueries({ queryKey: ["employees"] });
      void qc.invalidateQueries({ queryKey: ["employee", String(saved.id)] });
      void qc.invalidateQueries({ queryKey: ["lookup", "employees"] });
      void qc.invalidateQueries({ queryKey: ["employees-create-options"] });
      void qc.invalidateQueries({ queryKey: ["reporting-line", saved.id] });
      void qc.invalidateQueries({ queryKey: ["employment-history", String(saved.id)] });
      void qc.invalidateQueries({ queryKey: ["org-chart"] });
      toast.show(mode === "create" ? `Employee ${saved.employeeCode ?? ""} created` : "Employee saved");
      onSaved(saved);
    },
    onError: (e: Error) => {
      const msg = e.message || "Could not save";
      if (/effective date|managerEffectiveFrom|cannot be in the past|in the future/i.test(msg)) {
        setErrors((prev) => ({ ...prev, managerEffectiveFrom: msg }));
        setFormError("");
        return;
      }
      if (/loop|report|inactive|Manager is/i.test(msg)) {
        setErrors((prev) => ({ ...prev, managerId: msg }));
        setFormError("");
        return;
      }
      setFormError(msg);
    },
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
      return;
    }
    setErrors({});
    save.mutate(v);
  }

  const mgrs = (employees.data ?? []).filter((e) => {
    if (employee && String(e.id) === String(employee.id)) return false;
    // Hide people who already report (directly/indirectly) to this employee — would cycle.
    if (employee && wouldCycle(String(employee.id), String(e.id), managerById)) return false;
    return true;
  });
  const roleList = roles.data ?? [];

  return (
    <form className="stack" onSubmit={submit} noValidate>
      {showProfile ? (
        <div className="stack">
          {mode === "create" ? <h3>Profile</h3> : null}
          <div className="form-grid">
            {mode === "edit" ? (
              <FormField label="Employee code">
                <input type="text" value={employee?.employeeCode ?? ""} disabled readOnly />
              </FormField>
            ) : null}
            {mode === "create" && (codeMode === "MANUAL" || codeMode === "OPTIONAL") ? (
              <FormField
                label="Employee code"
                required={codeMode === "MANUAL"}
                error={errors.employeeCode}
                hint={
                  codeMode === "OPTIONAL"
                    ? "Leave blank to auto-generate from the business pack pattern."
                    : "Required for this tenant (employeeCodeMode=MANUAL)."
                }
              >
                <input
                  type="text"
                  value={v.employeeCode}
                  onChange={(e) => set("employeeCode", e.target.value)}
                  placeholder={createOpts.data?.employeeCodePattern ?? "Code"}
                />
              </FormField>
            ) : null}
            {mode === "create" && codeMode === "AUTO" ? (
              <p className="muted small full">
                Employee ID is generated automatically on save
                {createOpts.data?.employeeCodePrefix != null
                  ? ` as ${String(createOpts.data.employeeCodePrefix)}${"#".repeat(Number(createOpts.data.employeeCodeDigits ?? 4))}`
                  : ""}
                — you do not enter a code.
              </p>
            ) : null}
            <FormField label="Full name" required error={errors.fullName}>
              <input type="text" value={v.fullName} onChange={(e) => set("fullName", e.target.value)} />
            </FormField>
            <FormField label="Preferred name" error={errors.preferredName}>
              <input type="text" value={v.preferredName} onChange={(e) => set("preferredName", e.target.value)} />
            </FormField>
            <FormField
              label="Work email"
              required
              error={errors.email}
              hint={`Must be unique and end with @${emailDomain}`}
            >
              <input type="email" value={v.email} onChange={(e) => set("email", e.target.value)} />
            </FormField>
            <FormField label="Phone" error={errors.phone}>
              <input type="tel" value={v.phone} onChange={(e) => set("phone", e.target.value)} />
            </FormField>
            <FormField label="Personal email" error={errors.personalEmail}>
              <input type="email" value={v.personalEmail} onChange={(e) => set("personalEmail", e.target.value)} />
            </FormField>
            <FormField label="Address" error={errors.address} full>
              <input type="text" value={v.address} onChange={(e) => set("address", e.target.value)} />
            </FormField>
            <FormField label="Emergency contact" error={errors.emergencyName}>
              <input type="text" value={v.emergencyName} onChange={(e) => set("emergencyName", e.target.value)} />
            </FormField>
            <FormField label="Emergency relationship" error={errors.emergencyRelationship}>
              <input
                type="text"
                value={v.emergencyRelationship}
                onChange={(e) => set("emergencyRelationship", e.target.value)}
              />
            </FormField>
            <FormField label="Emergency phone" error={errors.emergencyPhone}>
              <input type="tel" value={v.emergencyPhone} onChange={(e) => set("emergencyPhone", e.target.value)} />
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
            <FormField
              label="Manager"
              required={managerRequired}
              error={errors.managerId}
              hint={
                managerChanged
                  ? `Changing from ${originalManagerId ? mgrName(originalManagerId) : "none"} — pick an effective date.`
                  : managerRequired
                    ? "Required — leave and other approvals go to this reporting manager first."
                    : undefined
              }
            >
              <select value={v.managerId} onChange={(e) => set("managerId", e.target.value)}>
                <option value="">{managerRequired ? "— Select manager —" : "— None —"}</option>
                {mgrs.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.fullName} {m.employeeCode ? `(${m.employeeCode})` : ""}
                  </option>
                ))}
              </select>
            </FormField>
            {managerChanged || (mode === "create" && v.managerId) ? (
              <FormField
                label="Manager effective from"
                required={managerChanged}
                error={errors.managerEffectiveFrom}
                hint="Required when the manager changes. Today or future — history before this date stays intact for past approvals."
              >
                <input
                  type="date"
                  min={todayIso()}
                  value={v.managerEffectiveFrom || todayIso()}
                  onChange={(e) => set("managerEffectiveFrom", e.target.value)}
                />
              </FormField>
            ) : null}
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
            <FormField
              label="Work pattern"
              error={errors.workPattern}
              hint="Location default, 5 days, 6 days, or hourly (FR-EMP-12)."
            >
              <select value={v.workPattern} onChange={(e) => set("workPattern", e.target.value)}>
                {[...new Set([v.workPattern, ...WORK_PATTERNS].filter(Boolean))].map((t) => (
                  <option key={t} value={t}>
                    {t === "LOCATION_DEFAULT"
                      ? "Location default"
                      : t === "DAYS_5"
                        ? "5 days"
                        : t === "DAYS_6"
                          ? "6 days"
                          : t === "HOURLY"
                            ? "Hourly"
                            : t}
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
            <FormField
              label="Contract end"
              required={CONTRACT_TYPES_NEED_END.has(v.employmentType.toUpperCase())}
              error={errors.contractEnd}
              hint={
                CONTRACT_TYPES_NEED_END.has(v.employmentType.toUpperCase())
                  ? "Required for contractors and interns."
                  : undefined
              }
            >
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
            {canSalary ? (
              <>
                <FormField label="Pay type" error={errors.payType}>
                  <select value={v.payType} onChange={(e) => set("payType", e.target.value)}>
                    <option value="MONTHLY">Monthly</option>
                    <option value="HOURLY">Hourly</option>
                  </select>
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
              </>
            ) : null}
            {mode === "edit" ? (
              <FormField
                label="Status"
                error={errors.status}
                hint={
                  protectFromOffboard
                    ? "This account holds a role configured with protectFromOffboard — cannot be Inactive, Resigned, or Terminated."
                    : undefined
                }
              >
                <select value={v.status} onChange={(e) => set("status", e.target.value)}>
                  {[...new Set([v.status, ...EMPLOYEE_STATUSES].filter(Boolean))].map((st) => {
                    const blocked = protectFromOffboard && ["Inactive", "Resigned", "Terminated"].includes(st);
                    return (
                      <option key={st} value={st} disabled={blocked}>
                        {st}
                      </option>
                    );
                  })}
                </select>
              </FormField>
            ) : null}
            {mode === "edit" && OFFBOARD_STATUSES.has(v.status) ? (
              <>
                <FormField label="Exit date" required error={errors.exitDate}>
                  <input type="date" value={v.exitDate} onChange={(e) => set("exitDate", e.target.value)} />
                </FormField>
                <FormField label="Exit reason" required error={errors.exitReason} full>
                  <input type="text" value={v.exitReason} onChange={(e) => set("exitReason", e.target.value)} />
                </FormField>
                <FormField
                  label="Checklist override reason"
                  error={errors.offboardOverrideReason}
                  hint="Required if the offboarding checklist is not completed."
                  full
                >
                  <input
                    type="text"
                    value={v.offboardOverrideReason}
                    onChange={(e) => set("offboardOverrideReason", e.target.value)}
                  />
                </FormField>
                <FormField
                  label="Reassign reports / approvals to"
                  error={errors.reassignReportsTo}
                  hint="Required when this person has direct reports or pending approvals."
                >
                  <select value={v.reassignReportsTo} onChange={(e) => set("reassignReportsTo", e.target.value)}>
                    <option value="">— Select —</option>
                    {mgrs.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.fullName} {m.employeeCode ? `(${m.employeeCode})` : ""}
                      </option>
                    ))}
                  </select>
                </FormField>
              </>
            ) : null}
            {mode === "create" && canRolesAssign && roleList.length > 0 ? (
              <div className="field full">
                <span>Roles</span>
                <div className="checks">
                  {roleList
                    .filter((r) => r.active !== false)
                    .filter((r) => canAssignRole(r))
                    .map((r) => {
                      const held = v.roleCodes.includes(r.code);
                      return (
                        <label key={r.code}>
                          <input
                            type="checkbox"
                            checked={held}
                            onChange={(e) =>
                              set(
                                "roleCodes",
                                e.target.checked
                                  ? [...v.roleCodes, r.code]
                                  : v.roleCodes.filter((c) => c !== r.code),
                              )
                            }
                          />
                          {r.name || r.code}
                        </label>
                      );
                    })}
                </div>
                <small>
                  Defaults to {defaultRoles.join(", ") || "EMPLOYEE"}. Leave empty to apply the tenant default on save.
                </small>
              </div>
            ) : null}
            {mode === "create" && !canRolesAssign ? (
              <p className="muted small full">
                Role <span className="mono">{defaultRoles.join(", ") || "EMPLOYEE"}</span> is assigned automatically
                (tenant default). An admin can change roles later.
              </p>
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

      {formError ? <p className="err" role="alert">{formError}</p> : null}
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
