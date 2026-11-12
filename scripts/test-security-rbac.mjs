#!/usr/bin/env node
// System security + RBAC acceptance probe (guide §4, §8, §13 SEC01 / DOC01) against the RUNNING local stack.
// Uses dev-minted sessions (scripts/lib/dev-session.mjs). Exit code 1 on any failed check.
//   node scripts/test-security-rbac.mjs
import { mintSession, GATEWAY as G, loadEnv } from "./lib/dev-session.mjs";

const env = loadEnv();
const SVC = {
  people: "http://127.0.0.1:8081", leave: "http://127.0.0.1:8086", timework: "http://127.0.0.1:8087",
  payroll: "http://127.0.0.1:8082", files: "http://127.0.0.1:8084", notif: "http://127.0.0.1:8083", report: "http://127.0.0.1:8085",
};
// Seed actors (scripts/seed-qii-dev.sh): 1 SUPERADMIN, 2 HR, 4 MANAGER of 6/7/19, 5 MANAGER of 8, 6/8 EMPLOYEE, 22 Terminated.
const A = { SUPER: 1, HR: 2, MGR4: 4, MGR5: 5, EMP6: 6, EMP8: 8, TERMINATED: 22 };
const tok = {};
const T = (id) => (tok[id] ??= mintSession(id));

let pass = 0, fail = 0;
const failures = [];
function check(name, ok, detail = "") {
  if (ok) { pass++; console.log(`  PASS ${name}`); }
  else { fail++; failures.push(`${name} ${detail}`); console.log(`  FAIL ${name} ${detail}`); }
}
async function call(method, url, { token, headers = {}, body, raw = false } = {}) {
  const h = { ...headers };
  if (token) h.Authorization = `Bearer ${token}`;
  if (body !== undefined) h["Content-Type"] = "application/json";
  const r = await fetch(url.startsWith("http") ? url : G + url, {
    method, headers: h, body: body === undefined ? undefined : (raw ? body : JSON.stringify(body)), redirect: "manual",
  });
  const text = await r.text();
  return { status: r.status, text, headers: r.headers };
}
const is = (s, ...allowed) => allowed.includes(s);
const denied = (s) => s === 401 || s === 403 || s === 404;

async function authBoundary() {
  console.log("\n[1] Gateway authentication boundary");
  check("no token → 401", (await call("GET", "/hrms/api/me")).status === 401);
  check("garbage token → 401", (await call("GET", "/hrms/api/me", { token: "abc.def.ghi" })).status === 401);
  check("alg=none token → 401", (await call("GET", "/hrms/api/me", { token: mintSession(1, { header: { alg: "none" } }) })).status === 401);
  check("wrong signing key → 401", (await call("GET", "/hrms/api/me", { token: mintSession(1, { overrides: { __secret: "x".repeat(48) } }) })).status === 401);
  check("expired token → 401", (await call("GET", "/hrms/api/me", { token: mintSession(1, { overrides: { exp: Math.floor(Date.now() / 1000) - 60 } }) })).status === 401);
  check("wrong issuer → 401", (await call("GET", "/hrms/api/me", { token: mintSession(1, { overrides: { iss: "evil" } }) })).status === 401);
  check("wrong audience → 401", (await call("GET", "/hrms/api/me", { token: mintSession(1, { overrides: { aud: ["other"] } }) })).status === 401);
  check("missing jti → 401", (await call("GET", "/hrms/api/me", { token: mintSession(1, { overrides: { jti: undefined } }) })).status === 401);
  check("unknown employee id → denied", denied((await call("GET", "/hrms/api/me", { token: mintSession(999999) })).status));

  const spoof = await call("GET", "/hrms/api/me", { token: T(A.EMP6), headers: { "X-Employee-ID": "1", "X-Service-Key": "spoof" } });
  check("client X-Employee-ID / X-Service-Key stripped (identity stays 6)", spoof.status === 200 && JSON.parse(spoof.text).employeeId == 6, `got ${spoof.status}`);

  check("/internal/** never proxied", (await call("GET", "/payroll/api/internal/payroll/facts", { token: T(A.SUPER) })).status === 404);
  check("encoded traversal rejected", is((await call("GET", "/hrms/api/%2e%2e/internal/x", { token: T(A.SUPER) })).status, 400, 404));

  const term = await call("GET", "/hrms/api/me", { token: T(A.TERMINATED) });
  check("terminated employee session refused", denied(term.status), `got ${term.status}`);

  // Session revocation (logout / offboarding)
  const t = mintSession(A.EMP6);
  check("fresh session works", (await call("GET", "/hrms/api/me", { token: t })).status === 200);
  const rv = await call("POST", "/api/session/revoke", { token: t });
  check("revoke endpoint accepts own session", is(rv.status, 200, 204), `got ${rv.status}`);
  check("revoked session → 401", (await call("GET", "/hrms/api/me", { token: t })).status === 401);
  check("other sessions of same employee still valid", (await call("GET", "/hrms/api/me", { token: T(A.EMP6) })).status === 200);
}

async function directServiceBypass() {
  console.log("\n[2] Services refuse traffic that bypasses the gateway");
  for (const [name, base] of Object.entries(SVC)) {
    const path = { people: "/api/me", leave: "/api/leave-types", timework: "/api/holidays", payroll: "/api/payroll/me/payslips",
      files: "/api/files", notif: "/api/notifications", report: "/api/reports/exports" }[name];
    const r = await call("GET", base + path, { headers: { "X-Employee-ID": "1" } });
    check(`${name}: direct call with forged X-Employee-ID, no service key → denied`, denied(r.status), `got ${r.status}`);
    const r2 = await call("GET", base + path, { headers: { "X-Employee-ID": "1", "X-Service-Key": "wrong-key" } });
    check(`${name}: direct call with wrong service key → denied`, denied(r2.status), `got ${r2.status}`);
  }
}

async function edgeExposure() {
  console.log("\n[3] Edge exposure (actuator, docs, headers, errors)");
  const prom = await call("GET", "/actuator/prometheus");
  check("gateway /actuator/prometheus not public", denied(prom.status), `got ${prom.status}`);
  if (env.PLATFORM_METRICS_KEY) {
    const ok = await call("GET", "/actuator/prometheus", { headers: { Authorization: `Bearer ${env.PLATFORM_METRICS_KEY}` } });
    check("gateway /actuator/prometheus served with metrics key", ok.status === 200, `got ${ok.status}`);
    const bad = await call("GET", "/actuator/prometheus", { headers: { "X-Metrics-Key": "wrong" } });
    check("gateway /actuator/prometheus refuses wrong metrics key", denied(bad.status), `got ${bad.status}`);
    for (const [n, base] of Object.entries(SVC)) {
      const r = await call("GET", base + "/actuator/prometheus");
      check(`${n}: /actuator/prometheus needs metrics key`, denied(r.status), `got ${r.status}`);
    }
  }
  for (const p of ["/actuator/env", "/actuator/heapdump", "/actuator/configprops", "/actuator/beans", "/actuator/loggers"]) {
    check(`gateway ${p} not exposed`, denied((await call("GET", p)).status));
  }
  check("gateway /actuator/health/liveness public", (await call("GET", "/actuator/health/liveness")).status === 200);
  const health = await call("GET", "/actuator/health");
  check("health does not leak component details", !/"components"|"details"/.test(health.text), health.text.slice(0, 80));
  for (const p of ["/people/actuator/env", "/payroll/actuator/prometheus", "/hrms/actuator/env", "/files/actuator/heapdump"]) {
    const r = await call("GET", p, { token: T(A.SUPER) });
    check(`service actuator via gateway route ${p} not reachable`, denied(r.status), `got ${r.status}`);
  }
  const big = await call("POST", "/hrms/api/leave-applications/preview", { token: T(A.EMP6), body: "x".repeat(3 * 1024 * 1024), raw: true, headers: { "Content-Type": "application/json" } });
  check("oversized JSON body rejected (413/400)", is(big.status, 413, 400), `got ${big.status}`);
  const err = await call("GET", "/hrms/api/employees/not-a-number", { token: T(A.SUPER) });
  check("bad input → 4xx, not 500", err.status >= 400 && err.status < 500, `got ${err.status}`);
  check("error body has no stack trace / SQL", !/Exception|at com\.|SQL|org\.hibernate|jdbc/i.test(err.text), err.text.slice(0, 120));
  check("error carries X-Correlation-ID", !!err.headers.get("x-correlation-id"));
}

async function scope() {
  console.log("\n[4] Record scope — employee self, manager reports, HR (SEC01)");
  const e6 = T(A.EMP6), m4 = T(A.MGR4), hr = T(A.HR), m5 = T(A.MGR5);
  const otherEmp = A.EMP8;
  const cases = [
    ["employee reads another employee's leave balance", e6, "GET", `/hrms/api/leave-balances/employee/${otherEmp}`],
    ["employee reads another employee's leave ledger", e6, "GET", `/hrms/api/leave-balances/employee/${otherEmp}/ledger`],
    ["employee reads another employee's attendance", e6, "GET", `/hrms/api/attendance/employee/${otherEmp}`],
    ["employee reads another employee's work logs", e6, "GET", `/hrms/api/work-logs/employee/${otherEmp}`],
    ["employee reads another employee's leave list", e6, "GET", `/hrms/api/leave/employee/${otherEmp}`],
    ["employee reads another employee's salary", e6, "GET", `/payroll/api/payroll/salary/employees/${otherEmp}`],
    ["employee lists payroll runs", e6, "GET", `/payroll/api/payroll/runs`],
    ["employee reads payroll line of another", e6, "GET", `/payroll/api/payroll/runs/2026-10/lines/${otherEmp}`],
    ["employee exports payroll CSV", e6, "GET", `/payroll/api/payroll/runs/2026-10/export.csv`],
    ["employee reads payroll operations", e6, "GET", `/payroll/api/payroll/operations`],
    ["employee reads audit log", e6, "GET", `/hrms/api/audit`],
    ["employee downloads another employee's document", e6, "GET", `/files/api/files/39/download`],
    ["employee reads another employee's document metadata", e6, "GET", `/files/api/files/39`],
    ["employee exports employees CSV", e6, "GET", `/reports/api/reports/employees.csv`],
    ["employee exports headcount CSV", e6, "GET", `/reports/api/reports/headcount.csv`],
    ["manager reads non-report attendance", m4, "GET", `/hrms/api/attendance/employee/${otherEmp}`],
    ["manager reads non-report leave balance", m4, "GET", `/hrms/api/leave-balances/employee/${otherEmp}`],
    ["manager reads non-report salary", m4, "GET", `/payroll/api/payroll/salary/employees/${otherEmp}`],
    ["other manager reads someone else's report document", m5, "GET", `/files/api/files/39/download`],
    ["employee removes another employee's document", e6, "DELETE", `/hrms/api/employees/15/documents/33`],
  ];
  for (const [name, t, m, p] of cases) {
    const r = await call(m, p, { token: t });
    check(`${name} → denied`, denied(r.status), `got ${r.status} ${r.text.slice(0, 80)}`);
  }
  const attach = await call("POST", "/hrms/api/employees/15/documents", { token: e6, body: { fileId: "1", documentType: "Contract", name: "x" } });
  check("employee attaches a document to another employee → denied", denied(attach.status), `got ${attach.status} ${attach.text.slice(0, 80)}`);
  const docs = await call("GET", "/hrms/api/employees/15/documents", { token: e6 });
  check("employee listing another employee's documents gets nothing", denied(docs.status) || (docs.status === 200 && JSON.parse(docs.text).length === 0), `got ${docs.status} ${docs.text.slice(0, 80)}`);
  const hrDocs = await call("GET", "/hrms/api/employees/15/documents", { token: hr });
  check("HR may list employee documents → 200", hrDocs.status === 200, `got ${hrDocs.status}`);
  if (hrDocs.status === 200) console.log(`  info HR sees ${JSON.parse(hrDocs.text).length} document(s) for employee 15`);
  const allowed = [
    ["employee reads own leave balance", e6, `/hrms/api/leave-balances/employee/${A.EMP6}`],
    ["employee reads own attendance", e6, `/hrms/api/attendance`],
    ["employee reads own payslips", e6, `/payroll/api/payroll/me/payslips`],
    ["manager reads direct report attendance", m4, `/hrms/api/attendance/employee/${A.EMP6}`],
    ["HR reads any employee leave balance", hr, `/hrms/api/leave-balances/employee/${otherEmp}`],
  ];
  for (const [name, t, p] of allowed) {
    const r = await call("GET", p, { token: t });
    check(`${name} → 200`, r.status === 200, `got ${r.status} ${r.text.slice(0, 80)}`);
  }
}

async function privilegedWrites() {
  console.log("\n[5] Privileged writes refused for ordinary employees (feature permission)");
  const e6 = T(A.EMP6);
  const writes = [
    ["create role", "POST", "/hrms/api/roles", { code: "HACK", name: "hack" }],
    ["assign role to self", "POST", "/hrms/api/roles/1/assign", { employeeId: A.EMP6 }],
    ["grant self roles", "POST", `/hrms/api/employees/${A.EMP6}/roles`, { roleIds: [1] }],
    ["change company settings", "PUT", "/hrms/api/settings", {}],
    ["terminate another employee", "PATCH", `/hrms/api/employees/${A.EMP8}/status`, { status: "Terminated" }],
    ["create employee", "POST", "/hrms/api/employees", { fullName: "x", email: "x@x.x" }],
    ["adjust leave balance", "POST", "/hrms/api/leave-balances/adjust", { employeeId: A.EMP6, days: 30 }],
    ["opening balance grant", "POST", "/hrms/api/leave-balances/opening", { employeeId: A.EMP6, days: 30 }],
    ["year-end confirm", "POST", "/hrms/api/leave-balances/year-end/confirm", {}],
    ["create holiday", "POST", "/hrms/api/holidays", { name: "x", date: "2026-12-30" }],
    ["create leave category", "POST", "/hrms/api/leave-categories", { name: "x" }],
    ["record own salary", "POST", `/payroll/api/payroll/salary/employees/${A.EMP6}`, { amount: 999999 }],
    ["calculate payroll", "POST", "/payroll/api/payroll/runs/2026-11/calculate", {}],
    ["finalize payroll", "POST", "/payroll/api/payroll/runs/2026-10/finalize", {}],
    ["change payroll settings", "PUT", "/payroll/api/payroll/settings", {}],
    ["set payroll adjustment", "PUT", `/payroll/api/payroll/runs/2026-11/adjustments/${A.EMP6}`, { amount: 1000 }],
    ["approve own attendance correction", "POST", "/hrms/api/attendance/corrections/1/approve", {}],
    ["close missing attendance", "POST", "/hrms/api/attendance/close-missing", {}],
    ["create work schedule", "POST", "/hrms/api/work-schedules", {}],
    ["notify missing work logs", "POST", "/hrms/api/work-logs/notify-missing", {}],
    ["delete another employee's document", "DELETE", "/files/api/files/39", undefined],
    ["create department", "POST", "/hrms/api/org/departments", { name: "x" }],
    ["create location", "POST", "/hrms/api/locations", { name: "x" }],
    ["create team", "POST", "/hrms/api/teams", { name: "x" }],
  ];
  for (const [name, m, p, b] of writes) {
    const r = await call(m, p, { token: e6, body: b, headers: { Origin: G } });
    check(`employee: ${name} → denied`, denied(r.status), `got ${r.status} ${r.text.slice(0, 100)}`);
  }
  // Valid body (so validation cannot mask authorization): employee re-submits colleague's own record.
  const other = await call("GET", `/hrms/api/employees/${A.EMP8}`, { token: T(A.HR) });
  if (other.status === 200) {
    const o = JSON.parse(other.text);
    const r = await call("PUT", `/hrms/api/employees/${A.EMP8}`, { token: e6, body: { fullName: o.fullName, email: o.email } });
    check("employee: edit another employee (valid body) → denied", denied(r.status), `got ${r.status} ${r.text.slice(0, 100)}`);
  } else check("HR can read employee for edit test", false, `got ${other.status}`);
  // Notifications created by a user are always bound to the caller, whatever employeeId is sent.
  const n = await call("POST", "/notifications/api/notifications", { token: e6, body: { employeeId: A.EMP8, title: "rbac probe", body: "rbac probe", category: "TEST" } });
  check("employee notification create is bound to self (no cross-employee send)", denied(n.status) || (n.status === 201 && String(JSON.parse(n.text).employeeId) === String(A.EMP6)), `got ${n.status} ${n.text.slice(0, 100)}`);
  const junk = await call("POST", "/notifications/api/notifications", { token: e6, body: { nope: 1 } });
  check("unknown/invalid JSON fields → 400 (not 500)", junk.status === 400, `got ${junk.status} ${junk.text.slice(0, 100)}`);
  check("400 body leaks no class names", !/com.platform|Exception|Unrecognized field/.test(junk.text), junk.text.slice(0, 120));
}

async function main() {
  console.log(`Gateway ${G}`);
  await authBoundary();
  await directServiceBypass();
  await edgeExposure();
  await scope();
  await privilegedWrites();
  console.log(`\n${pass} passed, ${fail} failed`);
  if (fail) { console.log("Failures:\n - " + failures.join("\n - ")); process.exit(1); }
}
main().catch((e) => { console.error(e); process.exit(2); });
