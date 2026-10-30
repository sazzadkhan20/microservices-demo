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

export function hasPermission(
  perms: PermissionSet | readonly string[] | undefined | null,
  feature: Feature | string,
  action: Action | string,
): boolean {
  const set = perms instanceof Set ? (perms as PermissionSet) : toPermissionSet(perms as readonly string[]);
  if (set.size === 0) return false;
  if (set.has("*:*")) return true;
  const f = feature.toLowerCase();
  if (set.has(`${f}:*`) || set.has(`*:${action.toLowerCase()}`)) return true;
  return synonyms(action.toLowerCase()).some((a) => set.has(`${f}:${a}`));
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
