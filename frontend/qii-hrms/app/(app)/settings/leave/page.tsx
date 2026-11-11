"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { ConfirmModal } from "@/components/ui/ConfirmModal";
import { DataTable } from "@/components/ui/DataTable";
import { FormField } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote } from "@/components/ui/QueryState";
import { Tabs } from "@/components/ui/Tabs";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import {
  byId,
  rowsOf,
  useEmployeesLookup,
  useLeaveTypeCatalogue,
  useLocations,
  useTeams,
  type Emp,
  type Rec,
} from "@/lib/api/lookups";
import { DEACTIVATE_STATUSES, EMPLOYMENT_TYPES } from "@/lib/schemas/employee";
import { fmtLeaveDays, fmtNumber, humanize, isLeaveHalfDayStep, str } from "@/lib/format";

type AppliesKind = "locations" | "employmentTypes" | "teams" | "employeeIds";
type AppliesTo = {
  locations: string[];
  employmentTypes: string[];
  teams: string[];
  employeeIds: string[];
};

type EmpHourlyHints = Emp & {
  hourlyRate?: number | null;
  baseSalary?: number | null;
  customFields?: Record<string, unknown> | null;
};

const EMPTY_APPLIES: AppliesTo = { locations: [], employmentTypes: [], teams: [], employeeIds: [] };

function parseAppliesTo(raw: unknown): AppliesTo {
  let src: Record<string, unknown> = {};
  if (typeof raw === "string" && raw.trim()) {
    try {
      src = JSON.parse(raw) as Record<string, unknown>;
    } catch {
      src = {};
    }
  } else if (raw && typeof raw === "object" && !Array.isArray(raw)) {
    src = raw as Record<string, unknown>;
  }
  const asIds = (v: unknown) =>
    Array.isArray(v) ? v.map((x) => String(x).trim()).filter(Boolean) : [];
  // Accept both canonical keys and occasional aliases from older payloads.
  return {
    locations: asIds(src.locations ?? src.locationIds ?? src.location_ids),
    employmentTypes: asIds(src.employmentTypes ?? src.employment_types ?? src.employmentType)
      .map((s) => s.toUpperCase()),
    teams: asIds(src.teams ?? src.teamIds ?? src.team_ids),
    employeeIds: asIds(src.employeeIds ?? src.employee_ids ?? src.employees),
  };
}

/** API may send LocalDate as "YYYY-MM-DD" or [y,m,d]. */
function dateField(v: unknown): string {
  if (v == null || v === "") return "";
  if (typeof v === "string") return v.slice(0, 10);
  if (Array.isArray(v) && v.length >= 3) {
    const y = String(v[0]);
    const m = String(v[1]).padStart(2, "0");
    const d = String(v[2]).padStart(2, "0");
    return `${y}-${m}-${d}`;
  }
  return str(v, "").slice(0, 10);
}

function appliesIsEveryone(a: AppliesTo) {
  return !a.locations.length && !a.employmentTypes.length && !a.teams.length && !a.employeeIds.length;
}

function appliesSummary(a: AppliesTo): string {
  if (appliesIsEveryone(a)) return "Everyone";
  const parts: string[] = [];
  if (a.locations.length) parts.push(`${a.locations.length} location(s)`);
  if (a.employmentTypes.length) parts.push(a.employmentTypes.join(", "));
  if (a.teams.length) parts.push(`${a.teams.length} team(s)`);
  if (a.employeeIds.length) parts.push(`${a.employeeIds.length} people`);
  return parts.join(" · ");
}

/** Match BE LeaveAppliesToService: resolved schedule mode only (not pay type). */
function resolvedScheduleMode(e: Emp, schedules: Rec[]): string | null {
  const find = (scope: string, id: number | null | undefined) => {
    if (id == null) return null;
    const row = schedules.find(
      (s) => String(s.scopeType ?? "").toUpperCase() === scope && String(s.scopeId) === String(id),
    );
    return row ? String(row.mode ?? "").toUpperCase() : null;
  };
  // Same resolve order as work schedules: EMPLOYEE → TEAM → LOCATION
  return find("EMPLOYEE", e.id) || find("TEAM", e.teamId ?? null) || find("LOCATION", e.locationId ?? null);
}

/** FR-LV-03 — hourly work schedule (incl. location default HOURLY). Pay type HOURLY alone does not block. */
function isHourlyEmployee(e: EmpHourlyHints, schedules: Rec[] = []) {
  const pattern = String(e.workPattern ?? "").toUpperCase();
  if (pattern === "HOURLY") return true;
  return resolvedScheduleMode(e, schedules) === "HOURLY";
}

const rowSchema = z.object({ id: z.union([z.number(), z.string()]) }).passthrough();

const TABS = [
  { id: "categories", label: "Leave categories" },
  { id: "balances", label: "Adjust balances" },
  { id: "opening", label: "Opening balances" },
  { id: "yearend", label: "Year end" },
  { id: "policy", label: "Policy" },
];

const ATTACH = ["NEVER", "ALWAYS", "AFTER_DAYS"];
const CARRY_MODES = ["NONE", "ALL", "CAP"] as const;
type CarryMode = (typeof CARRY_MODES)[number];

const CHAIN_ROLE_OPTIONS: { value: string; label: string }[] = [
  { value: "MANAGER", label: "Direct manager" },
  { value: "MANAGER_OF_MANAGER", label: "Manager's manager" },
  { value: "STORE_MANAGER", label: "Store manager" },
  { value: "HR", label: "HR (any HR role)" },
  { value: "ADMIN", label: "Admin (any Admin)" },
  { value: "SUPERADMIN", label: "Super Admin" },
  { value: "NAMED", label: "Specific person…" },
];

type ChainStep =
  | { kind: "ROLE"; role: string }
  | { kind: "NAMED"; employeeId: string };

const DEFAULT_CHAIN_STEPS: ChainStep[] = [
  { kind: "ROLE", role: "MANAGER" },
  { kind: "ROLE", role: "ADMIN" },
];

function isApproverEligible(e: Emp): boolean {
  const st = String(e.status ?? "").trim();
  if (!st) return true; // treat missing status as selectable
  if (DEACTIVATE_STATUSES.has(st)) return false;
  const upper = st.toUpperCase();
  return upper !== "INACTIVE" && upper !== "RESIGNED" && upper !== "TERMINATED";
}

function roleLabel(role: string): string {
  return CHAIN_ROLE_OPTIONS.find((o) => o.value === role)?.label ?? role;
}

function parseChainSteps(raw: unknown): ChainStep[] {
  try {
    const arr = typeof raw === "string" ? JSON.parse(raw) : raw;
    if (!Array.isArray(arr) || arr.length === 0) return [...DEFAULT_CHAIN_STEPS];
    const out: ChainStep[] = [];
    for (const s of arr) {
      const role = String(s?.role ?? "").toUpperCase();
      const namedId = s?.namedEmployeeId;
      if (role === "NAMED_EMPLOYEE" || namedId != null) {
        out.push({ kind: "NAMED", employeeId: namedId != null ? String(namedId) : "" });
      } else if (role) {
        out.push({ kind: "ROLE", role });
      }
    }
    return out.length ? out.slice(0, 4) : [...DEFAULT_CHAIN_STEPS];
  } catch {
    return [...DEFAULT_CHAIN_STEPS];
  }
}

function chainStepsSummary(steps: ChainStep[], names: Map<string, string>): string {
  if (!steps.length) return "—";
  return steps
    .map((s) => {
      if (s.kind === "NAMED") {
        const n = names.get(s.employeeId);
        return n || (s.employeeId ? `Person #${s.employeeId}` : "Specific person");
      }
      return roleLabel(s.role);
    })
    .join(" → ");
}

/** Table column helper — ids shown as NAMED:# until names map is available. */
function chainRoles(v: unknown, names?: Map<string, string>): string {
  return chainStepsSummary(parseChainSteps(v), names ?? new Map());
}

function validateChainSteps(steps: ChainStep[], activeIds: Set<string>): string | null {
  if (steps.length === 0) return "Add at least one approval step";
  if (steps.length > 4) return "Approval chain may have at most 4 steps";
  for (let i = 0; i < steps.length; i++) {
    const s = steps[i];
    if (s.kind === "NAMED") {
      if (!s.employeeId) return `Step ${i + 1}: choose a person`;
      if (activeIds.size > 0 && !activeIds.has(s.employeeId)) {
        return `Step ${i + 1}: selected person is inactive or no longer available`;
      }
    } else if (!s.role) {
      return `Step ${i + 1}: choose a role`;
    }
    if (i > 0) {
      const p = steps[i - 1];
      if (p.kind === "ROLE" && s.kind === "ROLE" && p.role === s.role) {
        return `Duplicate consecutive step (${roleLabel(s.role)})`;
      }
      if (p.kind === "NAMED" && s.kind === "NAMED" && p.employeeId && p.employeeId === s.employeeId) {
        return "Duplicate consecutive person in the chain";
      }
    }
  }
  return null;
}

function chainStepsToPayload(steps: ChainStep[]): Array<Record<string, unknown>> {
  return steps.map((s, i) => {
    if (s.kind === "NAMED") {
      return {
        step: i + 1,
        role: "NAMED_EMPLOYEE",
        resolver: "NAMED_EMPLOYEE",
        namedEmployeeId: Number(s.employeeId),
      };
    }
    const resolver =
      s.role === "MANAGER"
        ? "REPORTING_MANAGER"
        : s.role === "MANAGER_OF_MANAGER"
          ? "MANAGER_OF_MANAGER"
          : s.role === "STORE_MANAGER"
            ? "REPORTING_MANAGER_AND_DESIGNATION"
            : "RBAC_ROLE";
    return { step: i + 1, role: s.role, resolver };
  });
}

const catSchema = z.object({
  code: z.string().trim().min(2, "Code is required").regex(/^[A-Za-z0-9_]+$/, "Letters, numbers and underscores"),
  name: z.string().trim().min(2, "Name is required"),
  quotaDays: z.coerce.number("Enter a number").min(0, "Must be ≥ 0"),
  maxPerRequest: z.coerce.number("Enter a number").int().min(0, "Must be ≥ 0"),
  noticeDays: z.coerce.number("Enter a number").int().min(0, "Must be ≥ 0"),
  negativeCap: z.string().refine((v) => v === "" || !Number.isNaN(Number(v)), "Must be a number"),
  attachmentMode: z.string(),
  attachmentAfterDays: z.coerce.number("Enter a number").int().min(0),
  paid: z.boolean(),
  halfDayAllowed: z.boolean(),
  active: z.boolean(),
  // period & carry
  periodStart: z.string().optional(),
  periodEnd: z.string().optional(),
  carryMode: z.enum(["NONE", "ALL", "CAP"]).default("NONE"),
  carryCapDays: z.coerce.number().min(0).default(0),
  carryExpiryDate: z.string().optional(),
  proRata: z.boolean().default(false),
});

export default function LeaveSetupPage() {
  const [tab, setTab] = useState("categories");
  return (
    <div className="stack">
      <PageHeader title="Leave setup" subtitle="Categories, quotas, approval chains and manual balance adjustments." />
      <Tabs tabs={TABS} value={tab} onChange={setTab} />
      {tab === "categories" ? <Categories /> : null}
      {tab === "balances" ? <Balances /> : null}
      {tab === "opening" ? <OpeningBalances /> : null}
      {tab === "yearend" ? <YearEnd /> : null}
      {tab === "policy" ? <Policy /> : null}
    </div>
  );
}

function Categories() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canManage = can("leave", "manage");
  const locations = useLocations(canManage);
  const teams = useTeams(canManage);
  const employees = useEmployeesLookup(canManage);
  const schedulesQ = useQuery({
    queryKey: ["work-schedules", "all"],
    queryFn: async ({ signal }) => {
      const data = await apiFetch(api.hrms("work-schedules"), {
        query: { page: 1, size: 500 },
        signal,
      });
      return rowsOf<Rec>(data);
    },
    enabled: canManage,
    staleTime: 5 * 60_000,
  });
  const scheduleRows = schedulesQ.data ?? [];
  const nonHourlyEmployees = useMemo(
    () => (employees.data ?? []).filter((e) => !isHourlyEmployee(e as EmpHourlyHints, scheduleRows)),
    [employees.data, scheduleRows],
  );
  const nonHourlyIdSet = useMemo(
    () => new Set(nonHourlyEmployees.map((e) => String(e.id))),
    [nonHourlyEmployees],
  );
  const activeApprovers = useMemo(
    () => (employees.data ?? []).filter(isApproverEligible),
    [employees.data],
  );
  const employeeNames = useMemo(
    () => byId(employees.data ?? [], (e) => e.fullName || e.employeeCode || `#${e.id}`),
    [employees.data],
  );
  const activeApproverIds = useMemo(
    () => new Set(activeApprovers.map((e) => String(e.id))),
    [activeApprovers],
  );

  const thisYear = new Date().getFullYear();
  const blank = {
    code: "",
    name: "",
    quotaDays: "10",
    maxPerRequest: "0",
    noticeDays: "0",
    negativeCap: "",
    attachmentMode: "NEVER",
    attachmentAfterDays: "0",
    paid: true,
    halfDayAllowed: true,
    active: true,
    // period & carry — default current calendar year
    periodStart: `${thisYear}-01-01`,
    periodEnd: `${thisYear}-12-31`,
    carryMode: "NONE" as CarryMode,
    carryCapDays: "0",
    carryExpiryDate: "",
    proRata: false,
  };
  const [editing, setEditing] = useState<Rec | "new" | null>(null);
  const [form, setForm] = useState(blank);
  const [chainSteps, setChainSteps] = useState<ChainStep[]>([...DEFAULT_CHAIN_STEPS]);
  const [appliesTo, setAppliesTo] = useState<AppliesTo>(EMPTY_APPLIES);
  const [appliesKind, setAppliesKind] = useState<AppliesKind | "">("");
  const [appliesValue, setAppliesValue] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");
  const [loadingCategory, setLoadingCategory] = useState(false);
  const nextYear = new Date().getFullYear() + 1;
  const [renewTarget, setRenewTarget] = useState<Rec | null>(null);
  const [renewForm, setRenewForm] = useState({
    periodStart: `${nextYear}-01-01`,
    periodEnd: `${nextYear}-12-31`,
    quotaDays: "",
  });
  const [renewErr, setRenewErr] = useState("");

  const categoriesQ = useQuery({
    queryKey: ["leave-categories", "all-for-renew"],
    queryFn: async ({ signal }) => {
      const data = await apiFetch(api.hrms("leave-categories"), {
        query: { page: 1, size: 500 },
        signal,
      });
      return rowsOf<Rec>(data);
    },
    enabled: canManage,
  });

  function alreadyRenewedForNextYear(code: unknown) {
    const c = String(code ?? "").toUpperCase();
    return (categoriesQ.data ?? []).some((r) => {
      if (String(r.code ?? "").toUpperCase() !== c) return false;
      const ps = str(r.periodStart, "");
      return ps.startsWith(String(nextYear));
    });
  }

  function openRenew(r: Rec) {
    setRenewErr("");
    if (alreadyRenewedForNextYear(r.code)) {
      setRenewErr(`Already renewed for ${nextYear}. Only one next-year period is allowed.`);
    }
    setRenewForm({
      periodStart: `${nextYear}-01-01`,
      periodEnd: `${nextYear}-12-31`,
      quotaDays: String(r.quotaDays ?? ""),
    });
    setRenewTarget(r);
  }

  function hydrateCategoryForm(r: Rec) {
    const applies = parseAppliesTo(r.appliesTo);
    setAppliesTo(applies);
    // Prefer showing the first non-empty scope so existing rules are obvious in the editor.
    const firstKind = (["locations", "employmentTypes", "teams", "employeeIds"] as AppliesKind[]).find(
      (k) => applies[k].length > 0,
    );
    setAppliesKind(firstKind ?? "");
    setAppliesValue("");
    setForm({
      code: str(r.code, ""),
      name: str(r.name, ""),
      quotaDays: String(r.quotaDays ?? 0),
      maxPerRequest: String(r.maxPerRequest ?? 0),
      noticeDays: String(r.noticeDays ?? 0),
      negativeCap: r.negativeCap == null ? "" : String(r.negativeCap),
      attachmentMode: str(r.attachmentMode, "NEVER"),
      attachmentAfterDays: String(r.attachmentAfterDays ?? 0),
      paid: Boolean(r.paid),
      halfDayAllowed: Boolean(r.halfDayAllowed),
      active: r.active !== false,
      periodStart: dateField(r.periodStart),
      periodEnd: dateField(r.periodEnd),
      carryMode: (str(r.carryMode, "NONE") as CarryMode) || "NONE",
      carryCapDays: String(r.carryCapDays ?? 0),
      carryExpiryDate: dateField(r.carryExpiryDate),
      proRata: Boolean(r.proRata),
    });
  }

  function openFor(r: Rec | "new") {
    setErr("");
    setErrors({});
    setAppliesKind("");
    setAppliesValue("");
    if (r === "new") {
      setLoadingCategory(false);
      setAppliesTo({ ...EMPTY_APPLIES });
      setForm(blank);
      setChainSteps([...DEFAULT_CHAIN_STEPS]);
      setEditing("new");
      return;
    }
    // Seed from list row immediately, then refresh from GET /{id} so applies-to is never stale/missing.
    hydrateCategoryForm(r);
    setChainSteps(parseChainSteps(r.approvalChain));
    setEditing(r);
    setLoadingCategory(true);
    void apiFetch<Rec>(api.hrms(`leave-categories/${r.id}`))
      .then((fresh) => {
        hydrateCategoryForm(fresh);
        setChainSteps(parseChainSteps(fresh.approvalChain));
        setEditing(fresh);
      })
      .catch((e: Error) => {
        setErr(e.message || "Could not reload category details");
      })
      .finally(() => setLoadingCategory(false));
  }

  function addAppliesValue() {
    if (!appliesKind || !appliesValue) return;
    if (appliesKind === "employeeIds" && !nonHourlyIdSet.has(appliesValue)) return;
    setAppliesTo((prev) => {
      const next = { ...prev, [appliesKind]: [...prev[appliesKind]] };
      if (!next[appliesKind].includes(appliesValue)) next[appliesKind] = [...next[appliesKind], appliesValue];
      return next;
    });
    setAppliesValue("");
  }

  function removeAppliesValue(kind: AppliesKind, value: string) {
    setAppliesTo((prev) => ({
      ...prev,
      [kind]: prev[kind].filter((v) => v !== value),
    }));
  }

  const appliesValueOptions = useMemo(() => {
    if (appliesKind === "locations") {
      return (locations.data ?? []).map((l) => ({ value: String(l.id), label: l.name }));
    }
    if (appliesKind === "teams") {
      return (teams.data ?? []).map((t) => ({ value: String(t.id), label: t.name }));
    }
    if (appliesKind === "employmentTypes") {
      return EMPLOYMENT_TYPES.map((t) => ({ value: t, label: humanize(t) }));
    }
    if (appliesKind === "employeeIds") {
      return nonHourlyEmployees.map((e) => ({
        value: String(e.id),
        label: `${e.fullName ?? `#${e.id}`}${e.employeeCode ? ` (${e.employeeCode})` : ""}`,
      }));
    }
    return [];
  }, [appliesKind, locations.data, teams.data, nonHourlyEmployees]);

  function labelForApplies(kind: AppliesKind, value: string): string {
    if (kind === "locations") return (locations.data ?? []).find((l) => String(l.id) === value)?.name ?? value;
    if (kind === "teams") return (teams.data ?? []).find((t) => String(t.id) === value)?.name ?? value;
    if (kind === "employmentTypes") return humanize(value);
    if (kind === "employeeIds") {
      const e = nonHourlyEmployees.find((x) => String(x.id) === value) || (employees.data ?? []).find((x) => String(x.id) === value);
      return e?.fullName ? String(e.fullName) : `#${value}`;
    }
    return value;
  }

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      editing && editing !== "new"
        ? apiFetch(api.hrms(`leave-categories/${editing.id}`), { method: "PUT", body })
        : apiFetch(api.hrms("leave-categories"), { method: "POST", body }),
    onSuccess: () => {
      toast.show(editing === "new" ? "Category created — available as a leave type to request" : "Category saved");
      setEditing(null);
      void qc.invalidateQueries({ queryKey: ["leave-categories"] });
      // Apply forms use leave-types (kept in sync with categories on the server).
      void qc.invalidateQueries({ queryKey: ["leave-types"] });
      void qc.invalidateQueries({ queryKey: ["lookup", "leave-types"] });
    },
    onError: (e: Error) => setErr(e.message),
  });

  const renew = useMutation({
    mutationFn: () => {
      if (!renewTarget) throw new Error("Nothing to renew");
      const startY = Number(String(renewForm.periodStart).slice(0, 4));
      const endY = Number(String(renewForm.periodEnd).slice(0, 4));
      if (startY !== nextYear || endY !== nextYear) {
        throw new Error(`Renew period must stay inside calendar year ${nextYear}`);
      }
      if (renewForm.periodEnd < renewForm.periodStart) {
        throw new Error("Period end must be on/after period start");
      }
      return apiFetch(api.hrms(`leave-categories/${renewTarget.id}/renew`), {
        method: "POST",
        body: {
          periodStart: renewForm.periodStart,
          periodEnd: renewForm.periodEnd,
          quotaDays: renewForm.quotaDays === "" ? undefined : Number(renewForm.quotaDays),
        },
      });
    },
    onSuccess: () => {
      toast.show(`Renewed ${str(renewTarget?.code)} for ${nextYear} (same code & name)`);
      setRenewTarget(null);
      void qc.invalidateQueries({ queryKey: ["leave-categories"] });
      void qc.invalidateQueries({ queryKey: ["leave-types"] });
      void qc.invalidateQueries({ queryKey: ["lookup", "leave-types"] });
    },
    onError: (e: Error) => {
      setRenewErr(e.message);
      toast.error(e);
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = catSchema.safeParse(form);
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setErrors(next);
    }
    const chainErr = validateChainSteps(chainSteps, activeApproverIds);
    if (chainErr) {
      setErrors({ approvalChain: chainErr });
      return;
    }
    setErrors({});
    setErr("");
    const v = parsed.data;
    save.mutate({
      code: v.code.toUpperCase(),
      name: v.name,
      quotaDays: v.quotaDays,
      maxPerRequest: v.maxPerRequest,
      noticeDays: v.noticeDays,
      negativeCap: v.negativeCap === "" ? undefined : Number(v.negativeCap),
      attachmentMode: v.attachmentMode,
      attachmentAfterDays: v.attachmentAfterDays,
      approvalChain: JSON.stringify(chainStepsToPayload(chainSteps)),
      paid: v.paid,
      halfDayAllowed: v.halfDayAllowed,
      active: v.active,
      periodStart: v.periodStart || undefined,
      periodEnd: v.periodEnd || undefined,
      carryMode: v.carryMode,
      carryCapDays: v.carryMode === "CAP" ? v.carryCapDays : undefined,
      carryExpiryDate: v.carryExpiryDate || undefined,
      proRata: v.proRata,
      appliesTo: {
        locations: appliesTo.locations.map(Number).filter((n) => !Number.isNaN(n)),
        employmentTypes: appliesTo.employmentTypes,
        teams: appliesTo.teams.map(Number).filter((n) => !Number.isNaN(n)),
        employeeIds: appliesTo.employeeIds
          .filter((id) => nonHourlyIdSet.has(id))
          .map(Number)
          .filter((n) => !Number.isNaN(n)),
        blockHourly: true,
      },
    });
  }

  return (
    <section className="card stack">
      <div className="card-head" style={{ marginBottom: 0 }}>
        <h2>Leave categories</h2>
        {canManage ? (
          <button type="button" className="btn primary" onClick={() => openFor("new")}>
            + New category
          </button>
        ) : null}
      </div>
      <DataTable<Rec>
        queryKey="leave-categories"
        path={api.hrms("leave-categories")}
        itemSchema={rowSchema as never}
        emptyText="No categories"
        rowKey={(r) => String(r.id)}
        onRowClick={canManage ? (r) => openFor(r) : undefined}
        columns={[
          { key: "code", header: "Code", sortable: true, className: "mono" },
          { key: "name", header: "Name", sortable: true, render: (r) => <b>{str(r.name)}</b> },
          { key: "quotaDays", header: "Quota", className: "n", render: (r) => `${fmtLeaveDays(r.quotaDays)} d` },
          {
            key: "period",
            header: "Period",
            render: (r) => (
              <span className="small">
                {str(r.periodStart, "—")} → {str(r.periodEnd, "—")}
              </span>
            ),
          },
          { key: "paid", header: "Paid", render: (r) => <span className={`pill ${r.paid ? "good" : "warn"}`}>{r.paid ? "Paid" : "Unpaid"}</span> },
          {
            key: "approvalChain",
            header: "Approval chain",
            render: (r) => chainRoles(r.approvalChain, employeeNames) || "Auto",
          },
          {
            key: "appliesTo",
            header: "Applies to",
            render: (r) => <span className="small">{appliesSummary(parseAppliesTo(r.appliesTo))}</span>,
          },
          { key: "active", header: "State", render: (r) => <span className={`pill ${r.active ? "good" : ""}`}>{r.active ? "Active" : "Inactive"}</span> },
          {
            key: "renew_action",
            header: "",
            render: (r) =>
              canManage ? (
                <button
                  type="button"
                  className="btn sm"
                  disabled={renew.isPending || alreadyRenewedForNextYear(r.code)}
                  title={alreadyRenewedForNextYear(r.code) ? `Already renewed for ${nextYear}` : `Renew for ${nextYear}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    openRenew(r);
                  }}
                >
                  Renew
                </button>
              ) : null,
          },
        ]}
      />
      <Modal
        open={!!renewTarget}
        onClose={() => setRenewTarget(null)}
        title={`Renew ${str(renewTarget?.code)} for ${nextYear}`}
      >
        <form
          className="stack"
          onSubmit={(e) => {
            e.preventDefault();
            setRenewErr("");
            renew.mutate();
          }}
        >
          <p className="small muted" style={{ margin: 0 }}>
            Keeps the same <b>code</b> and <b>name</b>. Only next calendar year ({nextYear}) is allowed, once.
            Employees will not see this period on apply until it starts.
          </p>
          <div className="kv">
            <div>
              <div className="label">Code</div>
              <div>{str(renewTarget?.code)}</div>
            </div>
            <div>
              <div className="label">Name</div>
              <div>{str(renewTarget?.name)}</div>
            </div>
          </div>
          <div className="form-grid">
            <FormField label="Period start" required>
              <input
                type="date"
                value={renewForm.periodStart}
                onChange={(e) => setRenewForm({ ...renewForm, periodStart: e.target.value })}
              />
            </FormField>
            <FormField label="Period end" required>
              <input
                type="date"
                value={renewForm.periodEnd}
                onChange={(e) => setRenewForm({ ...renewForm, periodEnd: e.target.value })}
              />
            </FormField>
            <FormField label="Quota (days)" hint="Leave blank to copy current quota">
              <input
                type="number"
                min={0}
                step="0.5"
                value={renewForm.quotaDays}
                onChange={(e) => setRenewForm({ ...renewForm, quotaDays: e.target.value })}
              />
            </FormField>
          </div>
          {renewErr ? <div className="notice bad">{renewErr}</div> : null}
          <div className="row">
            <button type="button" className="btn" onClick={() => setRenewTarget(null)}>
              Cancel
            </button>
            <button
              type="submit"
              className="btn primary"
              disabled={renew.isPending || alreadyRenewedForNextYear(renewTarget?.code)}
            >
              {renew.isPending ? "Renewing…" : "Renew"}
            </button>
          </div>
        </form>
      </Modal>
      <Modal
        open={!!editing}
        onClose={() => {
          if (loadingCategory) return;
          setEditing(null);
        }}
        wide
        title={editing === "new" ? "New leave category" : "Edit leave category"}
      >
        <form className="stack" onSubmit={submit} noValidate>
          {loadingCategory ? (
            <p className="small muted" style={{ margin: 0 }}>
              Loading saved applies-to rules…
            </p>
          ) : null}
          <div className="form-grid">
            <FormField label="Code" required error={errors.code}>
              <input type="text" value={form.code} disabled={editing !== "new"} onChange={(e) => setForm({ ...form, code: e.target.value })} />
            </FormField>
            <FormField label="Name" required error={errors.name}>
              <input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </FormField>
            <FormField label="Yearly quota (days)" error={errors.quotaDays}>
              <input type="number" min={0} step="0.5" value={form.quotaDays} onChange={(e) => setForm({ ...form, quotaDays: e.target.value })} />
            </FormField>
            <FormField label="Max days per request" error={errors.maxPerRequest} hint="0 = no limit">
              <input type="number" min={0} value={form.maxPerRequest} onChange={(e) => setForm({ ...form, maxPerRequest: e.target.value })} />
            </FormField>
            <FormField label="Notice (days)" error={errors.noticeDays}>
              <input type="number" min={0} value={form.noticeDays} onChange={(e) => setForm({ ...form, noticeDays: e.target.value })} />
            </FormField>
            <FormField
              label="Negative balance cap"
              error={errors.negativeCap}
              hint="e.g. -2 or 2 = may overdraw by 2 days on apply/approve"
            >
              <input type="number" step="0.5" value={form.negativeCap} onChange={(e) => setForm({ ...form, negativeCap: e.target.value })} />
            </FormField>
            <FormField label="Attachment required">
              <select value={form.attachmentMode} onChange={(e) => setForm({ ...form, attachmentMode: e.target.value })}>
                {[...new Set([form.attachmentMode, ...ATTACH])].map((m) => (
                  <option key={m}>{m}</option>
                ))}
              </select>
            </FormField>
            <FormField label="…after (days)" error={errors.attachmentAfterDays}>
              <input type="number" min={0} value={form.attachmentAfterDays} onChange={(e) => setForm({ ...form, attachmentAfterDays: e.target.value })} />
            </FormField>
            <FormField
              label="Approval chain"
              full
              required
              error={errors.approvalChain}
              hint="Up to 4 steps. Pick a role or a specific active person. Preview shows names (e.g. Direct manager → Nadia)."
            >
              <div className="stack" style={{ gap: 8 }}>
                <div className="muted small">
                  <b>{chainStepsSummary(chainSteps, employeeNames)}</b>
                </div>
                {chainSteps.map((step, idx) => (
                  <div key={idx} className="row" style={{ gap: 8, flexWrap: "wrap", alignItems: "center" }}>
                    <span className="muted small" style={{ minWidth: 52 }}>
                      Step {idx + 1}
                    </span>
                    <select
                      value={step.kind === "NAMED" ? "NAMED" : step.role}
                      onChange={(e) => {
                        const v = e.target.value;
                        setChainSteps((prev) => {
                          const next = [...prev];
                          next[idx] =
                            v === "NAMED"
                              ? { kind: "NAMED", employeeId: step.kind === "NAMED" ? step.employeeId : "" }
                              : { kind: "ROLE", role: v };
                          return next;
                        });
                        setErrors((er) => {
                          const n = { ...er };
                          delete n.approvalChain;
                          return n;
                        });
                      }}
                      style={{ minWidth: 180 }}
                    >
                      {CHAIN_ROLE_OPTIONS.map((o) => (
                        <option key={o.value} value={o.value}>
                          {o.label}
                        </option>
                      ))}
                    </select>
                    {step.kind === "NAMED" ? (
                      <select
                        value={step.employeeId}
                        onChange={(e) => {
                          const id = e.target.value;
                          setChainSteps((prev) => {
                            const next = [...prev];
                            next[idx] = { kind: "NAMED", employeeId: id };
                            return next;
                          });
                          setErrors((er) => {
                            const n = { ...er };
                            delete n.approvalChain;
                            return n;
                          });
                        }}
                        style={{ minWidth: 200, flex: 1 }}
                      >
                        <option value="">Select person…</option>
                        {activeApprovers.map((e) => (
                          <option key={e.id} value={String(e.id)}>
                            {e.fullName || e.employeeCode || `#${e.id}`}
                          </option>
                        ))}
                        {/* Keep current selection visible if they became inactive (edit legacy). */}
                        {step.employeeId && !activeApproverIds.has(step.employeeId) ? (
                          <option value={step.employeeId}>
                            {employeeNames.get(step.employeeId) || `#${step.employeeId}`} (inactive — replace)
                          </option>
                        ) : null}
                      </select>
                    ) : null}
                    <button
                      type="button"
                      className="btn sm"
                      disabled={chainSteps.length <= 1}
                      onClick={() => setChainSteps((prev) => prev.filter((_, i) => i !== idx))}
                    >
                      Remove
                    </button>
                  </div>
                ))}
                <div>
                  <button
                    type="button"
                    className="btn sm"
                    disabled={chainSteps.length >= 4}
                    onClick={() =>
                      setChainSteps((prev) =>
                        prev.length >= 4 ? prev : [...prev, { kind: "ROLE", role: "ADMIN" }],
                      )
                    }
                  >
                    Add step
                  </button>
                </div>
              </div>
            </FormField>
            <FormField label="Period start" hint="Leave accrual period start date">
              <input type="date" value={form.periodStart} onChange={(e) => setForm({ ...form, periodStart: e.target.value })} />
            </FormField>
            <FormField label="Period end" hint="Leave accrual period end date">
              <input type="date" value={form.periodEnd} onChange={(e) => setForm({ ...form, periodEnd: e.target.value })} />
            </FormField>
            <FormField label="Carry mode" hint="NONE / ALL / CAP — applied at year-end carry">
              <select
                value={form.carryMode}
                onChange={(e) => setForm({ ...form, carryMode: e.target.value as CarryMode })}
              >
                <option value="NONE">None — no carry-over</option>
                <option value="ALL">All — carry all unused days</option>
                <option value="CAP">Cap — carry up to a limit</option>
              </select>
            </FormField>
            {form.carryMode === "CAP" ? (
              <FormField label="Carry cap (days)" error={errors.carryCapDays}>
                <input
                  type="number"
                  min={0}
                  step="0.5"
                  value={form.carryCapDays}
                  onChange={(e) => setForm({ ...form, carryCapDays: e.target.value })}
                />
              </FormField>
            ) : null}
            <FormField
              label="Carry expiry date"
              hint="Stored on year-end carry; unused carried days auto-expire on/after this date"
            >
              <input
                type="date"
                value={form.carryExpiryDate}
                onChange={(e) => setForm({ ...form, carryExpiryDate: e.target.value })}
              />
            </FormField>
            <div className="checks full">
              <label>
                <input type="checkbox" checked={form.paid} onChange={(e) => setForm({ ...form, paid: e.target.checked })} /> Paid
              </label>
              <label>
                <input type="checkbox" checked={form.halfDayAllowed} onChange={(e) => setForm({ ...form, halfDayAllowed: e.target.checked })} /> Half days allowed
              </label>
              <label title="Mid-year joiners get quota × remaining period days ÷ period length. Saving quota/pro-rata resyncs balances.">
                <input type="checkbox" checked={form.proRata} onChange={(e) => setForm({ ...form, proRata: e.target.checked })} /> Pro-rata accrual
              </label>
              <label>
                <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Active
              </label>
            </div>

            <div className="stack full" style={{ gap: 10 }}>
              <h3 style={{ margin: 0 }}>Who it applies to</h3>
              <p className="small muted" style={{ margin: 0 }}>
                Empty = everyone (except hourly-schedule staff — FR-LV-03). Pay type HOURLY with a day schedule still
                qualifies. Add locations, employment
                types, teams, or named people; a person matches if they hit <b>any</b> selected rule.
              </p>
              <div className="form-grid">
                <FormField label="Rule type">
                  <select
                    value={appliesKind}
                    onChange={(e) => {
                      setAppliesKind(e.target.value as AppliesKind | "");
                      setAppliesValue("");
                    }}
                  >
                    <option value="">Select type…</option>
                    <option value="locations">Locations</option>
                    <option value="employmentTypes">Employment types</option>
                    <option value="teams">Teams</option>
                    <option value="employeeIds">Named people</option>
                  </select>
                </FormField>
                <FormField label={appliesKind ? humanize(appliesKind) : "Value"}>
                  <select
                    value={appliesValue}
                    disabled={!appliesKind}
                    onChange={(e) => setAppliesValue(e.target.value)}
                  >
                    <option value="">{appliesKind ? "Select…" : "Pick a type first"}</option>
                    {appliesValueOptions
                      .filter((o) => !(appliesKind && appliesTo[appliesKind].includes(o.value)))
                      .map((o) => (
                        <option key={o.value} value={o.value}>
                          {o.label}
                        </option>
                      ))}
                  </select>
                </FormField>
                <div style={{ display: "flex", alignItems: "end" }}>
                  <button type="button" className="btn" disabled={!appliesKind || !appliesValue} onClick={addAppliesValue}>
                    Add rule
                  </button>
                </div>
              </div>
              {appliesIsEveryone(appliesTo) ? (
                <div className="note small">Currently applies to <b>everyone</b> (except hourly-schedule).</div>
              ) : (
                <div className="stack" style={{ gap: 8 }}>
                  <div className="note small" style={{ margin: 0 }}>
                    Current rules: <b>{appliesSummary(appliesTo)}</b>. Remove a chip or add more below.
                  </div>
                  {(["locations", "employmentTypes", "teams", "employeeIds"] as AppliesKind[]).map((kind) =>
                    appliesTo[kind].length ? (
                      <div key={kind}>
                        <div className="small muted" style={{ marginBottom: 4 }}>
                          {kind === "locations"
                            ? "Locations"
                            : kind === "employmentTypes"
                              ? "Employment types"
                              : kind === "teams"
                                ? "Teams"
                                : "Named people"}
                        </div>
                        <div className="row" style={{ flexWrap: "wrap", gap: 6 }}>
                          {appliesTo[kind].map((v) => (
                            <span key={`${kind}-${v}`} className="pill" style={{ display: "inline-flex", gap: 6, alignItems: "center" }}>
                              {labelForApplies(kind, v)}
                              <button
                                type="button"
                                className="btn sm"
                                style={{ padding: "0 6px" }}
                                onClick={() => removeAppliesValue(kind, v)}
                                aria-label={`Remove ${labelForApplies(kind, v)}`}
                                disabled={loadingCategory}
                              >
                                ×
                              </button>
                            </span>
                          ))}
                        </div>
                      </div>
                    ) : null,
                  )}
                </div>
              )}
            </div>
          </div>
          {err ? <div className="notice bad">{err}</div> : null}
          <div className="modal-foot">
            <button type="button" className="btn" onClick={() => setEditing(null)} disabled={loadingCategory}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={save.isPending || loadingCategory}>
              {save.isPending ? "Saving…" : loadingCategory ? "Loading…" : "Save"}
            </button>
          </div>
        </form>
      </Modal>
    </section>
  );
}

const adjustSchema = z.object({
  employeeId: z.string().min(1, "Choose an employee"),
  leaveTypeCode: z.string().min(1, "Choose a leave type"),
  deltaDays: z.coerce
    .number("Enter a number")
    .refine((n) => n !== 0, "Cannot be zero")
    .refine(isLeaveHalfDayStep, "Use half-day steps (0.5)"),
  reason: z.string().trim().min(3, "Please give a reason"),
  year: z.coerce.number().int().min(2000).max(2100),
});

function Balances() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canManage = can("leave", "manage");
  const employees = useEmployeesLookup();
  const types = useLeaveTypeCatalogue(canManage);
  const thisYear = new Date().getFullYear();
  const [employeeId, setEmployeeId] = useState("");
  const [form, setForm] = useState({
    leaveTypeCode: "",
    deltaDays: "1",
    reason: "",
    year: String(thisYear),
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  const balances = useQuery({
    queryKey: ["employee-balances", employeeId, form.year],
    queryFn: ({ signal }) =>
      apiFetch<Rec[]>(api.hrms(`leave-balances/employee/${employeeId}`), {
        query: { year: form.year },
        signal,
      }),
    enabled: !!employeeId,
  });

  const ledger = useQuery({
    queryKey: ["employee-balance-ledger", employeeId, form.year],
    queryFn: ({ signal }) =>
      apiFetch<Rec[]>(api.hrms(`leave-balances/employee/${employeeId}/ledger`), {
        query: { year: form.year, sourceType: "ADJUST" },
        signal,
      }),
    enabled: !!employeeId,
  });

  const adjust = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.hrms("leave-balances/adjust"), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Balance adjusted");
      setForm((f) => ({ ...f, reason: "" }));
      setErr("");
      void qc.invalidateQueries({ queryKey: ["employee-balances"] });
      void qc.invalidateQueries({ queryKey: ["employee-balance-ledger"] });
      void qc.invalidateQueries({ queryKey: ["my-balances"] });
      void qc.invalidateQueries({ queryKey: ["leave-types"] });
      void qc.invalidateQueries({ queryKey: ["lookup", "leave-types"] });
    },
    onError: (e: Error) => setErr(e.message),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = adjustSchema.safeParse({
      employeeId,
      ...form,
      leaveTypeCode: form.leaveTypeCode || types.data?.[0]?.code || "",
    });
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setErrors(next);
    }
    setErrors({});
    adjust.mutate({
      employeeId: Number(parsed.data.employeeId),
      leaveTypeCode: parsed.data.leaveTypeCode,
      deltaDays: parsed.data.deltaDays,
      reason: parsed.data.reason,
      year: parsed.data.year,
    });
  }

  return (
    <div className="stack">
      <div className="grid g2" style={{ alignItems: "start" }}>
        <section className="card stack">
          <h2>Adjust a balance</h2>
          <p className="muted small">
            Mandatory reason. Available = quota (pro-rata) + carried + adjustments − taken − pending.
          </p>
          {!canManage ? <p className="muted">You need leave:manage to adjust balances.</p> : null}
          <form className="stack" onSubmit={submit} noValidate>
            <FormField label="Employee" required error={errors.employeeId}>
              <select value={employeeId} onChange={(e) => setEmployeeId(e.target.value)}>
                <option value="">Select…</option>
                {(employees.data ?? []).map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.fullName}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Leave type" required error={errors.leaveTypeCode}>
              <select
                value={form.leaveTypeCode || types.data?.[0]?.code || ""}
                onChange={(e) => setForm({ ...form, leaveTypeCode: e.target.value })}
              >
                {(types.data ?? []).map((t) => (
                  <option key={t.code} value={t.code}>
                    {t.name}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Year" required error={errors.year}>
              <input
                type="number"
                min={2000}
                max={2100}
                value={form.year}
                onChange={(e) => setForm({ ...form, year: e.target.value })}
              />
            </FormField>
            <FormField label="Days to add (use a minus to deduct)" required error={errors.deltaDays}>
              <input
                type="number"
                step="0.5"
                value={form.deltaDays}
                onChange={(e) => setForm({ ...form, deltaDays: e.target.value })}
              />
            </FormField>
            <FormField label="Reason" required error={errors.reason}>
              <input type="text" value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} />
            </FormField>
            {err ? <div className="notice bad">{err}</div> : null}
            <div>
              <button type="submit" className="btn primary" disabled={!canManage || adjust.isPending}>
                {adjust.isPending ? "Saving…" : "Apply adjustment"}
              </button>
            </div>
          </form>
        </section>
        <section className="card stack">
          <h2>Balances · {form.year}</h2>
          {!employeeId ? <p className="muted">Choose an employee to see their balances.</p> : null}
          <ErrorNote error={balances.error} />
          <div className="tbl-wrap">
            <table>
              <thead>
                <tr>
                  <th>Type</th>
                  <th className="n">Quota</th>
                  <th className="n">Carried</th>
                  <th className="n">Adj</th>
                  <th className="n">Taken</th>
                  <th className="n">Pending</th>
                  <th className="n">Balance</th>
                  <th className="n">Available</th>
                </tr>
              </thead>
              <tbody>
                {(balances.data ?? []).length === 0 ? (
                  <tr>
                    <td colSpan={8} className="empty">
                      {employeeId ? "No balances for this year" : "—"}
                    </td>
                  </tr>
                ) : (
                  (balances.data ?? []).map((b) => (
                    <tr key={String(b.id)}>
                      <td>
                        <b>{str(b.leaveTypeName) || str(b.leaveTypeCode)}</b>
                        <div className="muted small">{str(b.leaveTypeCode)}</div>
                      </td>
                      <td className="n">{fmtLeaveDays(b.quotaDays)}</td>
                      <td className="n">{fmtLeaveDays(b.carriedDays)}</td>
                      <td className="n">{fmtLeaveDays(b.adjustmentDays)}</td>
                      <td className="n">{fmtLeaveDays(b.takenDays)}</td>
                      <td className="n">{fmtLeaveDays(b.pendingDays)}</td>
                      <td className="n">{fmtLeaveDays(b.balanceDays)}</td>
                      <td className="n">
                        <b>{fmtLeaveDays(b.availableDays ?? b.balanceDays)}</b>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>
      </div>
      <section className="card stack">
        <h2>Adjustment history</h2>
        <p className="muted small">Audited ADJUST ledger rows for the selected employee / year.</p>
        <ErrorNote error={ledger.error} />
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th>When</th>
                <th>Type</th>
                <th className="n">Delta</th>
                <th>Reason</th>
                <th>Actor</th>
              </tr>
            </thead>
            <tbody>
              {!employeeId ? (
                <tr>
                  <td colSpan={5} className="empty">
                    Choose an employee
                  </td>
                </tr>
              ) : (ledger.data ?? []).length === 0 ? (
                <tr>
                  <td colSpan={5} className="empty">
                    No adjustments yet
                  </td>
                </tr>
              ) : (
                (ledger.data ?? []).map((r) => (
                  <tr key={String(r.id)}>
                    <td className="small">{str(r.createdAt)}</td>
                    <td className="mono">{str(r.leaveTypeCode)}</td>
                    <td className="n">{fmtLeaveDays(r.deltaDays)}</td>
                    <td>{str(r.reason)}</td>
                    <td className="small">{r.actorEmployeeId != null ? `#${r.actorEmployeeId}` : "—"}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

const openingSchema = z.object({
  employeeId: z.string().min(1, "Choose an employee"),
  leaveTypeCode: z.string().min(1, "Choose a leave type"),
  deltaDays: z.coerce
    .number("Enter a number")
    .positive("Opening grant must be positive")
    .refine(isLeaveHalfDayStep, "Use half-day steps (0.5)"),
  year: z.coerce.number("Enter a year").int().min(2000).max(2100),
  reason: z.string().trim().min(3, "Please give a reason"),
});

function OpeningBalances() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canManage = can("leave", "manage");
  const employees = useEmployeesLookup();
  const types = useLeaveTypeCatalogue(canManage);
  const thisYear = new Date().getFullYear();
  const [employeeId, setEmployeeId] = useState("");
  const [form, setForm] = useState({
    leaveTypeCode: "",
    deltaDays: "10",
    year: String(thisYear),
    reason: "Opening / join grant",
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  const balances = useQuery({
    queryKey: ["employee-balances", employeeId, form.year],
    queryFn: ({ signal }) =>
      apiFetch<Rec[]>(api.hrms(`leave-balances/employee/${employeeId}`), {
        query: { year: form.year },
        signal,
      }),
    enabled: !!employeeId,
  });

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiFetch(api.hrms("leave-balances/opening"), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Opening balance recorded");
      setErr("");
      void qc.invalidateQueries({ queryKey: ["employee-balances"] });
      void qc.invalidateQueries({ queryKey: ["employee-balance-ledger"] });
      void qc.invalidateQueries({ queryKey: ["my-balances"] });
      void qc.invalidateQueries({ queryKey: ["leave-types"] });
      void qc.invalidateQueries({ queryKey: ["lookup", "leave-types"] });
    },
    onError: (e: Error) => setErr(e.message),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = openingSchema.safeParse({
      employeeId,
      ...form,
      leaveTypeCode: form.leaveTypeCode || types.data?.[0]?.code || "",
    });
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setErrors(next);
    }
    setErrors({});
    save.mutate({
      employeeId: Number(parsed.data.employeeId),
      leaveTypeCode: parsed.data.leaveTypeCode,
      deltaDays: parsed.data.deltaDays,
      year: parsed.data.year,
      reason: parsed.data.reason,
    });
  }

  return (
    <div className="grid g2" style={{ alignItems: "start" }}>
      <section className="card stack">
        <h2>Opening / join grant</h2>
        <p className="muted small">
          Categories already seed opening quota for eligible staff (pro-rata when enabled). Use this only for late
          joiners or people missed by seed — once per employee + type + year. Later changes go under Adjust balances.
        </p>
        {!canManage ? <p className="muted">You need leave:manage to set opening balances.</p> : null}
        <form className="stack" onSubmit={submit} noValidate>
          <FormField label="Employee" required error={errors.employeeId}>
            <select value={employeeId} onChange={(e) => setEmployeeId(e.target.value)}>
              <option value="">Select…</option>
              {(employees.data ?? []).map((m) => (
                <option key={m.id} value={m.id}>
                  {m.fullName}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Leave type" required error={errors.leaveTypeCode}>
            <select
              value={form.leaveTypeCode || types.data?.[0]?.code || ""}
              onChange={(e) => setForm({ ...form, leaveTypeCode: e.target.value })}
            >
              {(types.data ?? []).map((t) => (
                <option key={t.code} value={t.code}>
                  {t.name}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Year" required error={errors.year}>
            <input type="number" min={2000} max={2100} value={form.year} onChange={(e) => setForm({ ...form, year: e.target.value })} />
          </FormField>
          <FormField label="Days to grant" required error={errors.deltaDays}>
            <input type="number" min={0.5} step="0.5" value={form.deltaDays} onChange={(e) => setForm({ ...form, deltaDays: e.target.value })} />
          </FormField>
          <FormField label="Reason" required error={errors.reason}>
            <input type="text" value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} />
          </FormField>
          {err ? <div className="notice bad">{err}</div> : null}
          <div>
            <button type="submit" className="btn primary" disabled={!canManage || save.isPending}>
              {save.isPending ? "Saving…" : "Record opening balance"}
            </button>
          </div>
        </form>
      </section>
      <section className="card stack">
        <h2>Balances for {form.year || "year"}</h2>
        {!employeeId ? <p className="muted">Choose an employee to see their balances.</p> : null}
        <ErrorNote error={balances.error} />
        <div className="stack" style={{ gap: 8 }}>
          {(balances.data ?? []).map((b) => (
            <div key={String(b.id)} className="row between">
              <span>
                {str(b.leaveTypeName) || str(b.leaveTypeCode)}{" "}
                <span className="muted small">
                  avail {fmtLeaveDays(b.availableDays ?? b.balanceDays)} · quota {fmtLeaveDays(b.quotaDays)}
                </span>
              </span>
              <b className="num">{fmtLeaveDays(b.balanceDays)} d</b>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

const yearEndSchema = z
  .object({
    leaveTypeCode: z.string().min(1, "Choose a leave type"),
    fromYear: z.coerce.number("Enter a year").int().min(2000).max(2100),
    toYear: z.coerce.number("Enter a year").int().min(2000).max(2100),
    carryMaxDays: z.coerce
      .number("Enter a number")
      .min(0, "Must be ≥ 0")
      .refine(isLeaveHalfDayStep, "Use half-day steps (0.5)"),
  })
  .superRefine((v, ctx) => {
    if (v.toYear !== v.fromYear + 1) {
      ctx.addIssue({ code: "custom", path: ["toYear"], message: "To year must be from year + 1" });
    }
  });

function YearEnd() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canManage = can("leave", "manage");
  const types = useLeaveTypeCatalogue(canManage);
  const thisYear = new Date().getFullYear();
  const [form, setForm] = useState({
    leaveTypeCode: "",
    fromYear: String(thisYear - 1),
    toYear: String(thisYear),
    carryMaxDays: "5",
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");
  const [preview, setPreview] = useState<Rec[] | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const selectedCode = form.leaveTypeCode || types.data?.[0]?.code || "";

  const defaultsQ = useQuery({
    queryKey: ["year-end-defaults", selectedCode],
    queryFn: ({ signal }) =>
      apiFetch<Rec>(api.hrms("leave-balances/year-end/defaults"), {
        query: { leaveTypeCode: selectedCode },
        signal,
      }),
    enabled: canManage && !!selectedCode,
  });

  useEffect(() => {
    const d = defaultsQ.data;
    if (!d) return;
    setForm((f) => ({
      ...f,
      leaveTypeCode: selectedCode,
      fromYear: String(d.fromYear ?? thisYear - 1),
      toYear: String(d.toYear ?? thisYear),
      carryMaxDays: String(d.carryMaxDays ?? 5),
    }));
    setPreview(null);
    setErr("");
  }, [defaultsQ.data, selectedCode, thisYear]);

  function bodyFrom(parsed: z.infer<typeof yearEndSchema>) {
    return {
      leaveTypeCode: parsed.leaveTypeCode,
      fromYear: parsed.fromYear,
      toYear: parsed.toYear,
      carryMaxDays: parsed.carryMaxDays,
    };
  }

  function parseForm() {
    const parsed = yearEndSchema.safeParse({
      ...form,
      leaveTypeCode: selectedCode,
    });
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      setErrors(next);
      setErr(Object.values(next)[0] || "Invalid form");
      return null;
    }
    setErrors({});
    return parsed.data;
  }

  const runPreview = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiFetch<Rec[]>(api.hrms("leave-balances/year-end/preview"), { method: "POST", body }),
    onSuccess: (data) => {
      setPreview(Array.isArray(data) ? data : []);
      setErr("");
      toast.show("Preview ready");
    },
    onError: (e: Error) => {
      setPreview(null);
      setErr(e.message);
    },
  });

  const confirm = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiFetch<Rec>(api.hrms("leave-balances/year-end/confirm"), { method: "POST", body }),
    onSuccess: (data) => {
      toast.show(`Year-end applied · ${str(data.employeesUpdated, "0")} employees updated`);
      setConfirmOpen(false);
      setErr("");
      void qc.invalidateQueries({ queryKey: ["employee-balances"] });
      void qc.invalidateQueries({ queryKey: ["employee-balance-ledger"] });
      void qc.invalidateQueries({ queryKey: ["my-balances"] });
      void qc.invalidateQueries({ queryKey: ["leave-types"] });
      void qc.invalidateQueries({ queryKey: ["lookup", "leave-types"] });
      void qc.invalidateQueries({ queryKey: ["year-end-defaults"] });
      const parsed = parseForm();
      if (parsed) runPreview.mutate(bodyFrom(parsed));
    },
    onError: (e: Error) => {
      setConfirmOpen(false);
      setErr(e.message);
    },
  });

  function onPreview(e: FormEvent) {
    e.preventDefault();
    const parsed = parseForm();
    if (!parsed) return;
    runPreview.mutate(bodyFrom(parsed));
  }

  return (
    <div className="stack">
      <section className="card stack">
        <h2>Year-end carry-over</h2>
        <p className="muted small">
          Selecting a leave type auto-fills from/to year and max carry from the category. Only consecutive years are
          allowed; overlapping periods or a missing prior year fail immediately.
        </p>
        {!canManage ? <p className="muted">You need leave:manage to run year-end.</p> : null}
        <form className="stack" onSubmit={onPreview} noValidate>
          <div className="form-grid">
            <FormField label="Leave type" required error={errors.leaveTypeCode}>
              <select
                value={selectedCode}
                onChange={(e) => setForm((f) => ({ ...f, leaveTypeCode: e.target.value }))}
              >
                <option value="">Select…</option>
                {(types.data ?? []).map((t) => (
                  <option key={t.code} value={t.code}>
                    {t.name}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="From year" required error={errors.fromYear}>
              <input
                type="number"
                value={form.fromYear}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    fromYear: e.target.value,
                    toYear: String(Number(e.target.value) + 1 || thisYear),
                  }))
                }
              />
            </FormField>
            <FormField label="To year" required error={errors.toYear}>
              <input type="number" value={form.toYear} onChange={(e) => setForm({ ...form, toYear: e.target.value })} />
            </FormField>
            <FormField label="Max carry days" error={errors.carryMaxDays} hint="From category carry cap when set">
              <input
                type="number"
                min={0}
                step="0.5"
                value={form.carryMaxDays}
                onChange={(e) => setForm({ ...form, carryMaxDays: e.target.value })}
              />
            </FormField>
          </div>
          {err ? <div className="notice bad">{err}</div> : null}
          <div className="row">
            <button type="submit" className="btn" disabled={!canManage || !selectedCode || runPreview.isPending}>
              {runPreview.isPending ? "Previewing…" : "Preview"}
            </button>
            <button
              type="button"
              className="btn primary"
              disabled={!canManage || !preview || preview.length === 0 || confirm.isPending}
              onClick={() => setConfirmOpen(true)}
            >
              Confirm
            </button>
          </div>
        </form>
      </section>

      {preview ? (
        <section className="card stack">
          <h2>Preview</h2>
          <div className="tbl-wrap">
            <table>
              <thead>
                <tr>
                  <th>Employee</th>
                  <th className="n">Current balance</th>
                  <th className="n">Carry days</th>
                </tr>
              </thead>
              <tbody>
                {preview.length === 0 ? (
                  <tr>
                    <td colSpan={3} className="empty">
                      No balances to carry for this selection
                    </td>
                  </tr>
                ) : (
                  preview.map((r, i) => (
                    <tr key={String(r.employeeId ?? i)}>
                      <td>{str(r.employeeName) || `#${r.employeeId}`}</td>
                      <td className="n">{fmtLeaveDays(r.currentBalance)}</td>
                      <td className="n">
                        <b>{fmtLeaveDays(r.carryDays)}</b>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}

      <ConfirmModal
        open={confirmOpen}
        title="Confirm year-end carry-over?"
        confirmLabel="Confirm"
        busy={confirm.isPending}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => {
          const parsed = parseForm();
          if (!parsed) return setConfirmOpen(false);
          confirm.mutate(bodyFrom(parsed));
        }}
      >
        Carry leave into {form.toYear} for {preview?.length ?? 0} employee(s)? This posts ledger entries and cannot be
        undone from this screen.
      </ConfirmModal>
    </div>
  );
}

function Policy() {
  const policy = useQuery({
    queryKey: ["leave-policy"],
    queryFn: ({ signal }) => apiFetch<Rec>(api.hrms("leave-policy"), { signal }),
  });
  const p = policy.data;
  const days = Array.isArray(p?.workingDays) ? (p.workingDays as string[]).join(", ") : "—";
  const chain = Array.isArray(p?.approvalChain)
    ? (p.approvalChain as Rec[]).map((s) => s.role).join(" → ")
    : "—";
  return (
    <section className="card stack">
      <h2>Tenant leave policy</h2>
      <p className="muted small">Read-only view of the policy the leave engine is using right now.</p>
      <ErrorNote error={policy.error} />
      {policy.isLoading ? <p className="muted">Loading…</p> : null}
      {p ? (
        <div className="kv">
          <div>
            <div className="label">Annual days</div>
            {str(p.annualDays)}
          </div>
          <div>
            <div className="label">Working days</div>
            {days}
          </div>
          <div>
            <div className="label">Notice mode</div>
            {str(p.noticeMode)}
          </div>
          <div>
            <div className="label">Default notice days</div>
            {str(p.defaultNoticeDays)}
          </div>
          <div>
            <div className="label">Coverage check</div>
            {p.requireCoverageCheck ? "Required" : "Off"} ({str(p.coverageMode)})
          </div>
          <div>
            <div className="label">Default approval chain</div>
            {chain}
          </div>
        </div>
      ) : null}
    </section>
  );
}
