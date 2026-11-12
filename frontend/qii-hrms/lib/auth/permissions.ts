/**
 * Permission helpers. Pure (no server/client imports) so they can run anywhere.
 * Backend permission keys look like "<feature>:<action>" (lower-case), with `*:*` for admins.
 */

export const FEATURES = [
  "attendance",
  "attendance_settings",
  "leave",
  "leave_setup",
  "leave_requests",
  "leave_balances",
  "employees",
  "directory",
  "profile_personal",
  "profile_employment",
  "documents",
  "hierarchy",
  "holidays",
  "checklists",
  "tasks",
  "worklogs",
  "salary",
  "emails",
  "audit",
  "org",
  "roles",
  "settings",
  "payroll",
  "notifications",
  "reports",
] as const;
export type Feature = (typeof FEATURES)[number];

export const ACTIONS = [
  "view",
  "create",
  "edit",
  "delete",
  "approve",
  "manage",
  "assign",
  "checkin",
  "correct",
  "request",
  "export",
  "run",
] as const;
export type Action = (typeof ACTIONS)[number];

export type PermissionSet = ReadonlySet<string>;

export function toPermissionSet(permissions: readonly string[] | undefined | null): PermissionSet {
  return new Set((permissions ?? []).map((p) => p.trim().toLowerCase()));
}

/** view <-> read synonyms (also applied by the backend, kept here for safety). */
function synonyms(action: string): string[] {
  if (action === "view") return ["view", "read"];
  if (action === "read") return ["read", "view"];
  return [action];
}

/**
 * Coarse API aliases mirrored from people-service RbacService.API_ALIASES.
 * /me already expands these into the permission list; this keeps FE checks aligned
 * if a caller only holds an aliased matrix key (e.g. salary:view → payroll:view).
 */
const FE_ALIASES: Record<string, readonly string[]> = {
  "payroll:view": ["payroll:view", "salary:view", "salary:edit"],
  "salary:view": ["salary:view", "salary:edit"],
  // settings:view is not in the catalog; manage covers read of settings bags.
  "settings:view": ["settings:view", "settings:manage"],
  "worklogs:view": ["worklogs:view", "attendance:view"],
  "worklogs:edit": ["worklogs:edit", "attendance:checkin", "attendance_settings:edit"],
  "directory:view": ["directory:view", "employees:view"],
  "documents:view": ["documents:view", "employees:view"],
  "documents:create": ["documents:create", "employees:edit"],
  "documents:delete": ["documents:delete", "employees:edit"],
};

export function hasPermission(
  perms: PermissionSet | readonly string[] | undefined | null,
  feature: Feature | string,
  action: Action | string,
): boolean {
  const set = perms instanceof Set ? (perms as PermissionSet) : toPermissionSet(perms as readonly string[]);
  if (set.size === 0) return false;
  if (set.has("*:*")) return true;
  const f = feature.toLowerCase();
  const a = action.toLowerCase();
  if (set.has(`${f}:*`) || set.has(`*:${a}`)) return true;
  if (synonyms(a).some((syn) => set.has(`${f}:${syn}`))) return true;
  const want = `${f}:${a}`;
  const alts = FE_ALIASES[want];
  return !!alts?.some((alt) => set.has(alt));
}

/** [feature, action] */
export type PermissionRequirement = readonly [Feature, Action];

/** A rule passes when `always` is set, or ANY of the listed requirements is granted. */
export type AccessRule = { always: true } | { any: readonly PermissionRequirement[] };

export const ALWAYS: AccessRule = { always: true };
export const anyOf = (...reqs: PermissionRequirement[]): AccessRule => ({ any: reqs });

export function satisfies(perms: PermissionSet | readonly string[] | null | undefined, rule: AccessRule): boolean {
  if ("always" in rule) return true;
  return rule.any.some(([feature, action]) => hasPermission(perms, feature, action));
}

/** Scope map from /me (`employees:view` → OWN|TEAM|ALL). */
export type ScopeMap = Record<string, string> | null | undefined;

/**
 * Whether the actor may open `/employees/{id}` without a 403.
 * OWN → self only; ALL/YES → anyone; TEAM → allow link (server still enforces subtree).
 */
export function canOpenEmployeeRecord(
  actorEmployeeId: string | number | null | undefined,
  targetEmployeeId: string | number | null | undefined,
  perms: PermissionSet | readonly string[] | null | undefined,
  scopes?: ScopeMap,
): boolean {
  if (targetEmployeeId == null || targetEmployeeId === "") return false;
  if (!hasPermission(perms, "employees", "view")) return false;
  const set = perms instanceof Set ? perms : toPermissionSet(perms);
  if (set.has("*:*")) return true;
  const scope = String(scopes?.["employees:view"] ?? scopes?.["employees:read"] ?? "")
    .trim()
    .toUpperCase();
  if (scope === "ALL" || scope === "YES") return true;
  if (scope === "OWN" || !scope) {
    return String(actorEmployeeId) === String(targetEmployeeId);
  }
  // TEAM — link allowed; detail API enforces reporting subtree.
  return true;
}
