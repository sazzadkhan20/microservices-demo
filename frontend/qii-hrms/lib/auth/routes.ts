import { ALWAYS, anyOf, satisfies, type AccessRule, type PermissionSet } from "./permissions";

export type NavGroup = "Me" | "Team" | "HR" | "Settings";

export type RouteDef = {
  /** App Router pattern; `[id]` matches one dynamic segment. */
  path: string;
  title: string;
  /** Short label for the left rail. Omit to hide from nav (e.g. detail routes). */
  nav?: { group: NavGroup; label: string };
  rule: AccessRule;
  description?: string;
};

/**
 * Single source of truth for routes: nav items, page titles and access rules.
 * Nav visibility mirrors backend permission keys (see README).
 */
export const ROUTES: readonly RouteDef[] = [
  // ---- Me ----
  { path: "/", title: "Home", nav: { group: "Me", label: "Home" }, rule: ALWAYS },
  {
    path: "/attendance",
    title: "My attendance",
    nav: { group: "Me", label: "My attendance" },
    rule: anyOf(["attendance", "view"]),
  },
  {
    path: "/work-log",
    title: "Work log",
    nav: { group: "Me", label: "Work log" },
    rule: anyOf(["attendance", "checkin"], ["attendance", "view"]),
  },
  {
    path: "/leave",
    title: "My leave",
    nav: { group: "Me", label: "My leave" },
    rule: anyOf(["leave", "view"]),
  },
  {
    path: "/reports/monthly",
    title: "Monthly reports",
    nav: { group: "Me", label: "Monthly reports" },
    rule: anyOf(["reports", "view"]),
  },
  { path: "/profile", title: "Profile", nav: { group: "Me", label: "Profile" }, rule: ALWAYS },
  { path: "/notifications", title: "Notifications", rule: ALWAYS },

  // ---- Team ----
  {
    path: "/approvals",
    title: "Approvals",
    nav: { group: "Team", label: "Approvals" },
    rule: anyOf(["leave", "approve"], ["attendance", "correct"]),
  },
  {
    path: "/team/attendance",
    title: "Team attendance",
    nav: { group: "Team", label: "Team attendance" },
    rule: anyOf(["attendance", "view"]),
  },
  {
    path: "/team/work-logs",
    title: "Team work logs",
    nav: { group: "Team", label: "Team work logs" },
    rule: anyOf(["attendance", "view"]),
  },
  {
    path: "/tasks",
    title: "Tasks",
    nav: { group: "Team", label: "Tasks" },
    rule: anyOf(["tasks", "view"], ["tasks", "create"]),
  },
  {
    path: "/leave/calendar",
    title: "Leave calendar",
    nav: { group: "Team", label: "Leave calendar" },
    rule: anyOf(["leave", "view"]),
  },
  {
    path: "/directory",
    title: "Directory",
    nav: { group: "Team", label: "Directory" },
    rule: anyOf(["employees", "view"], ["org", "view"]),
  },

  // ---- HR ----
  {
    path: "/admin/dashboard",
    title: "Admin dashboard",
    nav: { group: "HR", label: "Admin dashboard" },
    rule: anyOf(["reports", "view"]),
  },
  {
    path: "/employees",
    title: "Employees",
    nav: { group: "HR", label: "Employees" },
    rule: anyOf(["employees", "view"]),
  },
  { path: "/employees/new", title: "Add employee", rule: anyOf(["employees", "create"]) },
  { path: "/employees/[id]", title: "Employee", rule: anyOf(["employees", "view"]) },
  {
    path: "/payroll",
    title: "Payroll",
    nav: { group: "HR", label: "Payroll" },
    rule: anyOf(["payroll", "view"]),
  },
  {
    path: "/checklists",
    title: "Checklists",
    nav: { group: "HR", label: "Checklists" },
    rule: anyOf(["checklists", "view"], ["org", "view"]),
  },
  {
    path: "/reports",
    title: "Reports",
    nav: { group: "HR", label: "Reports" },
    rule: anyOf(["reports", "view"]),
  },

  // ---- Settings ----
  {
    path: "/settings/org",
    title: "Organization",
    nav: { group: "Settings", label: "Organization" },
    // Prototype: Organisation requires hierarchy.edit (not view-only HR/Manager).
    rule: anyOf(["hierarchy", "edit"], ["org", "manage"]),
  },
  {
    path: "/settings/leave",
    title: "Leave setup",
    nav: { group: "Settings", label: "Leave setup" },
    rule: anyOf(["leave", "manage"], ["leave_setup", "view"], ["leave_balances", "edit"]),
  },
  {
    path: "/settings/schedules",
    title: "Work schedules",
    nav: { group: "Settings", label: "Work schedules" },
    rule: anyOf(["attendance_settings", "view"], ["attendance_settings", "edit"]),
  },
  {
    path: "/settings/holidays",
    title: "Holidays",
    nav: { group: "Settings", label: "Holidays" },
    rule: anyOf(["holidays", "view"], ["holidays", "create"], ["holidays", "edit"]),
  },
  {
    path: "/settings/roles",
    title: "Roles & permissions",
    nav: { group: "Settings", label: "Roles & permissions" },
    rule: anyOf(["roles", "view"]),
  },
  {
    path: "/settings/notifications",
    title: "Notification settings",
    nav: { group: "Settings", label: "Notifications" },
    rule: anyOf(["settings", "manage"], ["roles", "view"]),
  },
  {
    path: "/settings/audit",
    title: "Audit log",
    nav: { group: "Settings", label: "Audit log" },
    rule: anyOf(["settings", "manage"]),
  },
  {
    path: "/settings/rules",
    title: "Business rules",
    nav: { group: "Settings", label: "Business rules" },
    rule: anyOf(["settings", "manage"]),
  },
];

export const NAV_GROUPS: readonly NavGroup[] = ["Me", "Team", "HR", "Settings"];

/** Public/auth routes handled outside the permission manifest. */
export const AUTH_ROUTES = ["/login", "/access-pending"] as const;

function normalize(pathname: string): string {
  const p = pathname.split("?")[0].split("#")[0];
  return p.length > 1 ? p.replace(/\/+$/, "") : p;
}

function toRegex(pattern: string): RegExp {
  const src = pattern
    .split("/")
    .map((seg) => (/^\[.+\]$/.test(seg) ? "[^/]+" : seg.replace(/[.*+?^${}()|\\]/g, "\\$&")))
    .join("/");
  return new RegExp(`^${src}$`);
}

const COMPILED = ROUTES.map((r) => ({ def: r, re: toRegex(r.path), dynamic: r.path.includes("[") }));

/** Static routes win over dynamic ones. Returns undefined for unknown paths. */
export function findRoute(pathname: string): RouteDef | undefined {
  const p = normalize(pathname);
  const exact = COMPILED.find((c) => !c.dynamic && c.re.test(p));
  if (exact) return exact.def;
  return COMPILED.find((c) => c.dynamic && c.re.test(p))?.def;
}

/**
 * Whether the given permission set may open `pathname`.
 * Unknown paths are allowed here so Next can render its own 404.
 */
export function canAccessRoute(perms: PermissionSet | readonly string[] | null | undefined, pathname: string): boolean {
  const route = findRoute(pathname);
  if (!route) return true;
  return satisfies(perms, route.rule);
}

export type NavItem = { href: string; label: string };
export type NavSection = { group: NavGroup; items: NavItem[] };

/** Nav sections visible to the permission set. Empty groups are dropped. */
export function visibleNav(perms: PermissionSet | readonly string[] | null | undefined): NavSection[] {
  return NAV_GROUPS.map((group) => ({
    group,
    items: ROUTES.filter((r) => r.nav?.group === group && satisfies(perms, r.rule)).map((r) => ({
      href: r.path,
      label: r.nav!.label,
    })),
  })).filter((s) => s.items.length > 0);
}

export function titleFor(pathname: string): string {
  return findRoute(pathname)?.title ?? "Qii HRMS";
}
