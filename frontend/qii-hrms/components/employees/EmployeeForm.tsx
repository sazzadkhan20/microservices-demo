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
  DEACTIVATE_STATUSES,
  EMPLOYEE_STATUSES,
  EMPLOYMENT_TYPES,
  OFFBOARD_STATUSES,
  WORK_EMAIL_DOMAIN,
  WORK_EMAIL_DOMAIN_ALIASES,
  WORK_PATTERNS,
  employeeSchema,
  type Employee,
} from "@/lib/schemas/employee";
import { EmployeePhoto } from "@/components/employees/EmployeePhoto";
import { PHOTO_ACCEPT, validatePhotoFile } from "@/lib/uploadValidation";

export type EmployeeFormValues = {
  fullName: string;
  preferredName: string;
  email: string;
  phone: string;
  personalEmail: string;
  address: string;
  dateOfBirth: string;
  emergencyName: string;
  emergencyRelationship: string;
  emergencyPhone: string;
  photoFileId: string;
  photoPreviewUrl: string;
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
  employmentTypes?: string[];
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
    address: s(e?.address) || customStr(e, "address"),
    dateOfBirth: e ? isoDate(e.dateOfBirth) || customStr(e, "dateOfBirth") : "",
    emergencyName: s(e?.emergencyName) || customStr(e, "emergencyName"),
    emergencyRelationship:
      s(e?.emergencyRelation) ||
      s(e?.emergencyRelationship) ||
      customStr(e, "emergencyRelation") ||
      customStr(e, "emergencyRelationship"),
    emergencyPhone: s(e?.emergencyPhone) || customStr(e, "emergencyPhone"),
    photoFileId: s(e?.photoFileId) || customStr(e, "photoFileId"),
    // Only blob:/data: previews — EmployeePhoto fetches the server file via photoFileId.
    photoPreviewUrl: "",
    jobTitle: s(e?.jobTitle),
    departmentId: s(e?.departmentId),
    designationId: s(e?.designationId),
    managerId: s(e?.managerId),
    managerEffectiveFrom: todayIso(),
    teamId: s(e?.teamId),
    locationId: s(e?.locationId),
    employmentType: (() => {
      const t = s(e?.employmentType) || "FULL_TIME";
      return t.toUpperCase() === "CONTRACTOR" ? "CONTRACT" : t;
    })(),
    workPattern: (() => {
      const p = s(e?.workPattern) || "LOCATION_DEFAULT";
      return p.toUpperCase() === "HOURLY" ? "HOURLY" : p;
    })(),
    startDate: e ? isoDate(e.startDate) : todayIso(),
    probationEnd: e ? isoDate(e.probationEnd) : "",
    contractEnd: e ? isoDate(e.contractEnd) : "",
    payCurrency: s(e?.payCurrency),
    payType: (() => {
      const pattern = (s(e?.workPattern) || "").toUpperCase();
      const fromCf = customStr(e, "payType").toUpperCase();
      if (pattern === "HOURLY") return "HOURLY";
      if (fromCf === "HOURLY" || fromCf === "MONTHLY") return fromCf;
      // Heuristic: rate-only employees are hourly.
      const base = Number(s(e?.baseSalary) || 0);
      const hourly = Number(s(e?.hourlyRate) || 0);
      if (hourly > 0 && base <= 0) return "HOURLY";
      return "MONTHLY";
    })(),
    baseSalary: s(e?.baseSalary),
    hourlyRate: s(e?.hourlyRate),
    status: s(e?.status) || "Active",
    exitDate: isoDate(e?.exitDate) || customStr(e, "exitDate") || "",
    exitReason: s(e?.exitReason) || customStr(e, "exitReason"),
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
    /** FR-EMP-11 — block Inactive / Resigned / Terminated when editing yourself. */
    blockSelfDeactivate?: boolean;
    /** FR-EMP-10 — only when leaver has direct reports and/or pending approvals. */
    requireReassignment?: boolean;
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
      dateOfBirth: dateStr,
      emergencyName: z.string(),
      emergencyRelationship: z.string(),
      emergencyPhone: z.string(),
      photoFileId: z.string(),
      photoPreviewUrl: z.string(),
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
        .refine((v) => v === "" || /^(BDT|AUD)$/i.test(v), "Pay currency must be BDT or AUD (DR-02)"),
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
      if (opts.blockSelfDeactivate && DEACTIVATE_STATUSES.has(v.status)) {
        ctx.addIssue({
          code: "custom",
          path: ["status"],
          message: "Nobody shall deactivate their own account",
        });
      }
      if (OFFBOARD_STATUSES.has(v.status)) {
        if (!v.exitDate) {
          ctx.addIssue({ code: "custom", path: ["exitDate"], message: "Exit date is required" });
        }
        if (!v.exitReason.trim()) {
          ctx.addIssue({ code: "custom", path: ["exitReason"], message: "Exit reason is required" });
        }
        if (opts.requireReassignment && !v.reassignReportsTo) {
          ctx.addIssue({
            code: "custom",
            path: ["reassignReportsTo"],
            message: "Pick who takes over this person’s direct reports and pending approvals",
          });
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
    // Only send the amount that matches pay type — avoids mixed monthly+hourly payloads.
    ...(v.payType.toUpperCase() === "HOURLY"
      ? { hourlyRate: num(v.hourlyRate), baseSalary: 0 }
      : { baseSalary: num(v.baseSalary), hourlyRate: 0 }),
    phone: txt(v.phone),
    personalEmail: txt(v.personalEmail),
    address: txt(v.address),
    dateOfBirth: txt(v.dateOfBirth),
    emergencyName: txt(v.emergencyName),
    emergencyRelation: txt(v.emergencyRelationship),
    emergencyRelationship: txt(v.emergencyRelationship),
    emergencyPhone: txt(v.emergencyPhone),
    ...(txt(v.photoFileId) ? { photoFileId: Number(v.photoFileId) } : {}),
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
  /** FR-EMP-12 — starting pay is captured only by users with salary:edit; it is stored by Payroll (salary owner). */
  const canSalary = can("salary", "edit") && mode === "create";
  const departments = useDepartments();
  const designations = useDesignations();
  const teams = useTeams();
  const locations = useLocations();
  const employees = useEmployeesLookup();
  const roles = useRoles(canRolesAssign || mode === "edit");
  const createOpts = useQuery({
    queryKey: ["employees-create-options"],
    queryFn: ({ signal }) => apiFetch<CreateOptions>(api.hrms("employees/create-options"), { signal }),
    // Needed on edit too — employment types, work patterns, email domains, required fields.
    enabled:
      (mode === "create" && can("employees", "create")) ||
      (mode === "edit" && can("employees", "edit")),
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
  /** FR-EMP-11 — never allow the signed-in user to deactivate themselves in the UI. */
  const editingSelf =
    mode === "edit" &&
    employee?.id != null &&
    me?.employeeId != null &&
    String(employee.id) === String(me.employeeId);
  const holdsProtectedRole = useMemo(() => {
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

  const needsHandoverPreview = mode === "edit" && OFFBOARD_STATUSES.has(v.status) && !!employee?.id;
  const handover = useQuery({
    queryKey: ["offboarding-handover", String(employee?.id ?? "")],
    queryFn: ({ signal }) =>
      apiFetch<{
        directReportCount?: number;
        pendingApprovalCount?: number;
        requiresReassignment?: boolean;
        today?: string;
        handoverDeferred?: boolean;
        scheduledHandover?: {
          status?: string;
          effectiveOn?: string;
          reassignReportsTo?: number | string;
        } | null;
        directReports?: { id: number | string; fullName?: string; employeeCode?: string }[];
        eligibleHandoverTo?: { id: number | string; fullName?: string; employeeCode?: string }[];
      }>(api.hrms(`employees/${employee!.id}/offboarding-handover`), { signal }),
    enabled: needsHandoverPreview && can("employees", "edit"),
    staleTime: 15_000,
  });
  const requireReassignment = Boolean(handover.data?.requiresReassignment);
  /** Server list + client belt: never show self / loop-creating takeover targets. */
  const handoverCandidates = useMemo(() => {
    const raw = handover.data?.eligibleHandoverTo ?? [];
    const reports = handover.data?.directReports ?? [];
    return raw.filter((c) => {
      const cid = String(c.id);
      if (employee && cid === String(employee.id)) return false;
      for (const r of reports) {
        if (wouldCycle(String(r.id), cid, managerById)) return false;
      }
      return true;
    });
  }, [handover.data?.eligibleHandoverTo, handover.data?.directReports, employee, managerById]);
  const eligibleIds = useMemo(
    () => new Set(handoverCandidates.map((c) => String(c.id))),
    [handoverCandidates],
  );

  const eligibleManagersQuery = useQuery({
    queryKey: ["eligible-managers", String(employee?.id ?? "")],
    queryFn: ({ signal }) =>
      apiFetch<{ id: number | string; fullName?: string; employeeCode?: string }[]>(
        api.hrms(`employees/${employee!.id}/eligible-managers`),
        { signal },
      ),
    enabled: mode === "edit" && !!employee?.id && can("employees", "view"),
    staleTime: 30_000,
  });

  // Clear stale handover pick when status leaves offboarding or target becomes ineligible.
  useEffect(() => {
    setV((cur) => {
      if (!OFFBOARD_STATUSES.has(cur.status) && cur.reassignReportsTo) {
        return { ...cur, reassignReportsTo: "" };
      }
      if (!requireReassignment && cur.reassignReportsTo && handover.isSuccess) {
        return { ...cur, reassignReportsTo: "" };
      }
      if (cur.reassignReportsTo && eligibleIds.size > 0 && !eligibleIds.has(cur.reassignReportsTo)) {
        return { ...cur, reassignReportsTo: "" };
      }
      return cur;
    });
  }, [v.status, requireReassignment, handover.isSuccess, eligibleIds]);

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
        blockSelfDeactivate: editingSelf,
        requireReassignment: needsHandoverPreview && requireReassignment,
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
      editingSelf,
      needsHandoverPreview,
      requireReassignment,
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
    setV((cur) => {
      const next = { ...cur, [k]: val };
      const parsed = schema.safeParse(next);
      const fieldKey = String(k);
      const issue = parsed.success
        ? undefined
        : parsed.error.issues.find((i) => String(i.path[0]) === fieldKey);
      setTimeout(() => {
        setErrors((e) => {
          const copy = { ...e };
          if (issue) copy[fieldKey] = issue.message;
          else delete copy[fieldKey];
          return copy;
        });
      }, 0);
      return next;
    });
    if (formError) setFormError("");
  }

  async function onPhotoSelected(file: File | null) {
    if (!file) return;
    const invalid = validatePhotoFile(file);
    if (invalid) {
      setErrors((e) => ({ ...e, photoFileId: invalid }));
      return;
    }
    try {
      const fd = new FormData();
      fd.append("file", file);
      if (employee?.id) fd.append("employeeId", String(employee.id));
      // PHOTO → file-service enforces JPG/PNG ≤ 2 MB + quarantine/scan (guide §8).
      fd.append("classification", "PHOTO");
      const uploaded = await apiFetch<{ id: number | string }>("/api/files", { method: "POST", body: fd });
      const id = String(uploaded.id);
      set("photoFileId", id);
      set("photoPreviewUrl", URL.createObjectURL(file));
      setErrors((e) => {
        const copy = { ...e };
        delete copy.photoFileId;
        return copy;
      });
    } catch (err) {
      setErrors((e) => ({
        ...e,
        photoFileId: err instanceof Error ? err.message : "Photo upload failed",
      }));
    }
  }

  function onTeamChange(teamId: string) {
    const team = (teams.data ?? []).find((t) => String(t.id) === teamId);
    setV((cur) => ({
      ...cur,
      teamId,
      // FR-EMP-05: choosing a team always sets the department from the team.
      departmentId: team?.departmentId ? String(team.departmentId) : cur.departmentId,
    }));
    setErrors((e) => {
      const next = { ...e };
      delete next.teamId;
      delete next.departmentId;
      return next;
    });
    if (formError) setFormError("");
  }

  function onDepartmentChange(departmentId: string) {
    setV((cur) => {
      const team = (teams.data ?? []).find((t) => String(t.id) === cur.teamId);
      const teamBelongs =
        !cur.teamId ||
        !departmentId ||
        (team?.departmentId != null && String(team.departmentId) === departmentId);
      return {
        ...cur,
        departmentId,
        // Changing department clears a team that is not under the new department.
        teamId: teamBelongs ? cur.teamId : "",
      };
    });
    setErrors((e) => {
      const next = { ...e };
      delete next.departmentId;
      delete next.teamId;
      return next;
    });
    if (formError) setFormError("");
  }

  const teamOptions = useMemo(() => {
    const all = teams.data ?? [];
    if (!v.departmentId) return all;
    return all.filter((t) => t.departmentId != null && String(t.departmentId) === v.departmentId);
  }, [teams.data, v.departmentId]);

  const departmentOptions = useMemo(() => {
    const all = departments.data ?? [];
    if (!v.teamId) return all;
    const team = (teams.data ?? []).find((t) => String(t.id) === v.teamId);
    if (!team?.departmentId) return all;
    return all.filter((d) => String(d.id) === String(team.departmentId));
  }, [departments.data, teams.data, v.teamId]);

  function onLocationChange(locationId: string) {
    const loc = (locations.data ?? []).find((l) => String(l.id) === locationId);
    const currency = (loc?.defaultCurrency ?? "").trim().toUpperCase();
    setV((cur) => ({
      ...cur,
      locationId,
      // Always sync from location default when a location is chosen; user can still edit pay currency after.
      payCurrency: locationId && currency ? currency : cur.payCurrency,
    }));
    setErrors((e) => {
      const next = { ...e };
      delete next.locationId;
      if (locationId && currency) delete next.payCurrency;
      return next;
    });
    if (formError) setFormError("");
  }

  /** HOURLY pattern forces hourly pay; MONTHLY pay forbids HOURLY pattern. */
  function onWorkPatternChange(workPattern: string) {
    setV((cur) => {
      const hourlyPattern = workPattern.toUpperCase() === "HOURLY";
      return {
        ...cur,
        workPattern,
        payType: hourlyPattern ? "HOURLY" : cur.payType,
        // Hourly pay uses rate only — clear the unused amount field for a clean payload.
        ...(hourlyPattern || cur.payType === "HOURLY" ? { baseSalary: "" } : {}),
      };
    });
    setErrors((e) => {
      const next = { ...e };
      delete next.workPattern;
      delete next.payType;
      delete next.baseSalary;
      return next;
    });
    if (formError) setFormError("");
  }

  function onPayTypeChange(payType: string) {
    setV((cur) => {
      const monthly = payType.toUpperCase() === "MONTHLY";
      const hourly = payType.toUpperCase() === "HOURLY";
      return {
        ...cur,
        payType,
        // Monthly staff cannot stay on an hourly work pattern.
        workPattern:
          monthly && cur.workPattern.toUpperCase() === "HOURLY" ? "LOCATION_DEFAULT" : cur.workPattern,
        baseSalary: hourly ? "" : cur.baseSalary,
        hourlyRate: monthly ? "" : cur.hourlyRate,
      };
    });
    setErrors((e) => {
      const next = { ...e };
      delete next.payType;
      delete next.workPattern;
      delete next.baseSalary;
      delete next.hourlyRate;
      return next;
    });
    if (formError) setFormError("");
  }

  const payTypeIsHourly = v.payType.toUpperCase() === "HOURLY";
  const payTypeIsMonthly = v.payType.toUpperCase() === "MONTHLY";
  const workPatternOptions = useMemo(() => {
    const all = [...new Set([v.workPattern, ...(createOpts.data?.workPatterns ?? [...WORK_PATTERNS])].filter(Boolean))];
    // Monthly pay cannot use the hourly schedule pattern.
    if (payTypeIsMonthly) return all.filter((t) => t.toUpperCase() !== "HOURLY");
    return all;
  }, [v.workPattern, createOpts.data?.workPatterns, payTypeIsMonthly]);

  const save = useMutation({
    mutationFn: async (values: EmployeeFormValues) => {
      const body = toRequest(values, mode, { originalManagerId }) as Record<string, unknown>;
      // Salary is owned by Payroll (guide §3): never written to People.
      delete body.baseSalary;
      delete body.hourlyRate;
      delete body.payType;
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
      if (mode === "create" && canSalary) {
        const hourly = values.payType.toUpperCase() === "HOURLY";
        const amount = Number(hourly ? values.hourlyRate : values.baseSalary);
        if (amount > 0) {
          try {
            await apiFetch(api.payroll(`salary/employees/${saved.id}`), {
              method: "POST",
              body: {
                payType: hourly ? "HOURLY" : "MONTHLY",
                amount,
                currency: (values.payCurrency || "BDT").toUpperCase(),
                effectiveDate: values.startDate || new Date().toISOString().slice(0, 10),
                reason: "Starting salary",
              },
            });
          } catch (e) {
            toast.error(new Error(`Employee created, but the salary was not saved: ${(e as Error).message}. Add it on the Salary tab.`));
          }
        }
      }
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
      if (/deactivate their own|protected role|fewer than/i.test(msg)) {
        setErrors((prev) => ({ ...prev, status: msg }));
        setFormError("");
        return;
      }
      if (/reassign|handover|reporting loop|hand over/i.test(msg)) {
        setErrors((prev) => ({ ...prev, reassignReportsTo: msg }));
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
    // Never allow terminate/resign before we know if handover is required.
    if (needsHandoverPreview && (handover.isLoading || handover.isFetching)) {
      setErrors({
        reassignReportsTo: "Still checking direct reports and pending approvals — wait a moment",
      });
      return;
    }
    if (needsHandoverPreview && handover.isError) {
      setErrors({
        reassignReportsTo: "Cannot offboard until handover preview loads (reports / pending approvals)",
      });
      return;
    }
    if (needsHandoverPreview && requireReassignment && !v.reassignReportsTo) {
      setErrors({
        reassignReportsTo: "Pick who takes over this person’s direct reports and pending approvals",
      });
      return;
    }
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

  const mgrs = useMemo(() => {
    // Prefer server-filtered list (authoritative loop check); fall back to client walk.
    if (mode === "edit" && eligibleManagersQuery.data && eligibleManagersQuery.data.length >= 0) {
      const byId = new Map((employees.data ?? []).map((e) => [String(e.id), e]));
      return eligibleManagersQuery.data
        .map((row) => {
          const full = byId.get(String(row.id));
          return (
            full ?? {
              id: row.id,
              fullName: row.fullName ?? `#${row.id}`,
              employeeCode: row.employeeCode,
            }
          );
        })
        .filter((e) => {
          if (employee && String(e.id) === String(employee.id)) return false;
          if (employee && wouldCycle(String(employee.id), String(e.id), managerById)) return false;
          return true;
        });
    }
    return (employees.data ?? []).filter((e) => {
      if (employee && String(e.id) === String(employee.id)) return false;
      if (employee && wouldCycle(String(employee.id), String(e.id), managerById)) return false;
      return true;
    });
  }, [
    mode,
    eligibleManagersQuery.data,
    employees.data,
    employee,
    managerById,
  ]);
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
            <FormField
              label="Photo"
              error={errors.photoFileId}
              hint="JPG or PNG, max 2 MB. Stored in MinIO after virus scan."
            >
              <div className="row" style={{ gap: 12, alignItems: "center" }}>
                <EmployeePhoto
                  photoFileId={v.photoFileId || null}
                  previewUrl={v.photoPreviewUrl || null}
                  fullName={v.fullName}
                  size={56}
                />
                <input
                  type="file"
                  accept={PHOTO_ACCEPT}
                  onChange={(e) => void onPhotoSelected(e.target.files?.[0] ?? null)}
                />
              </div>
            </FormField>
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
            <FormField label="Date of birth" error={errors.dateOfBirth}>
              <input type="date" value={v.dateOfBirth} onChange={(e) => set("dateOfBirth", e.target.value)} />
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
            {!showProfile ? (
              <FormField
                label="Photo"
                error={errors.photoFileId}
                hint="JPG or PNG, max 2 MB. Upload a new image to replace the current photo."
                full
              >
                <div className="row" style={{ gap: 12, alignItems: "center" }}>
                  <EmployeePhoto
                    photoFileId={v.photoFileId || null}
                    previewUrl={v.photoPreviewUrl || null}
                    fullName={v.fullName || employee?.fullName}
                    size={56}
                  />
                  <input
                    type="file"
                    accept={PHOTO_ACCEPT}
                    onChange={(e) => void onPhotoSelected(e.target.files?.[0] ?? null)}
                  />
                </div>
              </FormField>
            ) : null}
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
            <FormField
              label="Department"
              required={mode === "create" && !v.teamId}
              hint={
                v.teamId
                  ? "Locked to the selected team’s department."
                  : "Choose a department to filter teams, or pick a team first."
              }
              error={errors.departmentId}
            >
              <select value={v.departmentId} onChange={(e) => onDepartmentChange(e.target.value)}>
                <option value="">— Select —</option>
                {departmentOptions.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField
              label="Team"
              required={mode === "create" && !v.departmentId}
              error={errors.teamId}
              hint={
                v.departmentId
                  ? "Only teams in the selected department."
                  : "Optional if a department is set — selecting a team sets the department."
              }
            >
              <select value={v.teamId} onChange={(e) => onTeamChange(e.target.value)}>
                <option value="">— None —</option>
                {teamOptions.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField
              label="Manager"
              required={managerRequired}
              error={errors.managerId}
              hint={
                [
                  managerChanged
                    ? `Changing from ${originalManagerId ? mgrName(originalManagerId) : "none"} — pick an effective date.`
                    : managerRequired
                      ? "Required — leave and other approvals go to this reporting manager first."
                      : null,
                  "People who already report to this employee (direct or indirect) are hidden — that would create a loop.",
                ]
                  .filter(Boolean)
                  .join(" ")
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
                hint="Today: manager + this person’s pending leave approvals switch immediately. Future: history is written now; daily 02:00 Asia/Dhaka job flips manager and moves pending approvals on that date."
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
                {[
                  ...new Set(
                    [
                      v.employmentType === "CONTRACTOR" ? "CONTRACT" : v.employmentType,
                      ...(createOpts.data?.employmentTypes ?? [...EMPLOYMENT_TYPES]),
                    ].filter(Boolean),
                  ),
                ].map((t) => (
                  <option key={t} value={t}>
                    {t.replace(/_/g, " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase())}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField
              label="Work pattern"
              error={errors.workPattern}
              hint={
                payTypeIsMonthly
                  ? "Hourly pattern is hidden while pay type is monthly."
                  : "Location default, 5 days, 6 days, or hourly (FR-EMP-12)."
              }
            >
              <select value={v.workPattern} onChange={(e) => onWorkPatternChange(e.target.value)}>
                {workPatternOptions.map((t) => (
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
            <FormField
              label="Pay currency"
              hint="Updates when you change location; you can override it manually."
              error={errors.payCurrency}
            >
              <input
                type="text"
                maxLength={3}
                value={v.payCurrency}
                onChange={(e) => set("payCurrency", e.target.value.toUpperCase())}
                placeholder="e.g. BDT"
              />
            </FormField>
            {canSalary ? (
              <>
                <FormField
                  label="Pay type"
                  error={errors.payType}
                  hint={
                    v.workPattern.toUpperCase() === "HOURLY"
                      ? "Locked to hourly while work pattern is hourly."
                      : undefined
                  }
                >
                  <select
                    value={v.payType}
                    onChange={(e) => onPayTypeChange(e.target.value)}
                    disabled={v.workPattern.toUpperCase() === "HOURLY"}
                  >
                    <option value="MONTHLY">Monthly</option>
                    <option value="HOURLY">Hourly</option>
                  </select>
                </FormField>
                {payTypeIsMonthly ? (
                  <FormField label="Base salary" error={errors.baseSalary}>
                    <input
                      type="number"
                      min={0}
                      step="0.01"
                      value={v.baseSalary}
                      onChange={(e) => set("baseSalary", e.target.value)}
                    />
                  </FormField>
                ) : null}
                {payTypeIsHourly ? (
                  <FormField label="Hourly rate" error={errors.hourlyRate}>
                    <input
                      type="number"
                      min={0}
                      step="0.01"
                      value={v.hourlyRate}
                      onChange={(e) => set("hourlyRate", e.target.value)}
                    />
                  </FormField>
                ) : null}
              </>
            ) : null}
            {mode === "edit" ? (
              <FormField
                label="Status"
                error={errors.status}
                hint={
                  editingSelf
                    ? "You cannot set your own account to Inactive, Resigned, or Terminated."
                    : holdsProtectedRole
                      ? "Protected role (e.g. Super Admin): the last active holder cannot be deactivated."
                      : undefined
                }
              >
                <select
                  value={v.status}
                  onChange={(e) => {
                    const next = e.target.value;
                    if (editingSelf && DEACTIVATE_STATUSES.has(next)) {
                      setErrors((prev) => ({
                        ...prev,
                        status: "Nobody shall deactivate their own account",
                      }));
                      return;
                    }
                    set("status", next);
                  }}
                >
                  {[...new Set([v.status, ...EMPLOYEE_STATUSES].filter(Boolean))].map((st) => {
                    const blocked = editingSelf && DEACTIVATE_STATUSES.has(st);
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
                  hint="Only if no OFFBOARDING template exists — otherwise a checklist attaches automatically when offboarding starts."
                  full
                >
                  <input
                    type="text"
                    value={v.offboardOverrideReason}
                    onChange={(e) => set("offboardOverrideReason", e.target.value)}
                  />
                </FormField>
                {handover.isLoading ? (
                  <p className="muted small full">Checking direct reports and pending approvals…</p>
                ) : null}
                {handover.isError ? (
                  <p className="notice bad full">
                    Could not load handover preview — fix leave/people connectivity before terminating.
                  </p>
                ) : null}
                {handover.isSuccess && !requireReassignment ? (
                  <p className="notice good full">
                    No direct reports and no pending approvals — handover person is not required.
                  </p>
                ) : null}
                {requireReassignment ? (
                  <FormField
                    label="Hand over team & approvals to"
                    required
                    error={errors.reassignReportsTo}
                    hint={
                      [
                        Number(handover.data?.directReportCount ?? 0) > 0
                          ? `${handover.data?.directReportCount} direct report(s) will report to this person`
                          : null,
                        Number(handover.data?.pendingApprovalCount ?? 0) > 0
                          ? `${handover.data?.pendingApprovalCount} pending approval(s) will move to their queue`
                          : null,
                        v.exitDate && handover.data?.today && v.exitDate > handover.data.today
                          ? `Exit date is in the future — manager + approvals switch on ${v.exitDate} (daily job 02:00 Asia/Dhaka), not immediately`
                          : "Exit date is today or past — handover applies as soon as you save",
                        "People who would create a reporting loop (A↔B) are hidden.",
                      ]
                        .filter(Boolean)
                        .join(". ")
                    }
                    full
                  >
                    <select
                      value={v.reassignReportsTo}
                      onChange={(e) => set("reassignReportsTo", e.target.value)}
                      disabled={handover.isLoading || handover.isFetching}
                    >
                      <option value="">— Select takeover person —</option>
                      {handoverCandidates.map((m) => (
                        <option key={String(m.id)} value={String(m.id)}>
                          {m.fullName} {m.employeeCode ? `(${m.employeeCode})` : ""}
                        </option>
                      ))}
                    </select>
                    {Number(handover.data?.directReportCount ?? 0) > 0 ? (
                      <p className="muted small" style={{ marginTop: 6 }}>
                        Reports today:{" "}
                        {(handover.data?.directReports ?? [])
                          .map((r) => r.fullName || `#${r.id}`)
                          .join(", ") || "—"}
                      </p>
                    ) : null}
                    {v.exitDate && handover.data?.today && v.exitDate > handover.data.today ? (
                      <p className="notice warn" style={{ marginTop: 8 }}>
                        Scheduled handover: on <b>{v.exitDate}</b> the system auto-moves manager links and
                        any remaining pending approvals to the person you select (02:00 Asia/Dhaka job).
                      </p>
                    ) : null}
                  </FormField>
                ) : null}
                {handover.data?.handoverDeferred && handover.data?.scheduledHandover ? (
                  <p className="notice warn full">
                    Handover already scheduled for{" "}
                    <b>{String(handover.data.scheduledHandover.effectiveOn ?? "—")}</b> (status{" "}
                    {String(handover.data.scheduledHandover.status ?? "PENDING")}). The daily job applies it
                    on that date.
                  </p>
                ) : null}
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
        <button
          type="submit"
          className="btn primary"
          disabled={
            save.isPending
            || (needsHandoverPreview && (handover.isLoading || handover.isFetching || handover.isError))
          }
        >
          {save.isPending
            ? "Saving…"
            : needsHandoverPreview && (handover.isLoading || handover.isFetching)
              ? "Checking handover…"
              : (submitLabel ?? (mode === "create" ? "Create employee" : "Save changes"))}
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
