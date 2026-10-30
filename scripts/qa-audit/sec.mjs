import { GW, DEMO, req, token, A, check, signJwt, decode, summary } from "./lib.mjs";

const SECRET = "practice-jwt-secret-change-me-32chars-min";
const H = `${GW}/hrms/api`;
const P = `${GW}/payroll/api`;

const ADMIN = 1, HR = 2, ADM2 = 3, MGR = 4, MGR2 = 5, EMP = 9, EMP_LINA = 6;
const tAdm = await token(ADMIN), tHr = await token(HR), tMgr = await token(MGR), tEmp = await token(EMP), tEmp2 = await token(EMP_LINA);

// ---- token issuance ----
{
  const r = await req("POST", `${GW}/api/registry/token`, { headers: { "X-Demo-Auth-Key": DEMO }, body: { tenantId: "qii_creative" } });
  check("TOKEN-NO-EMPLOYEE", r.status >= 400, `status=${r.status} ${r.text.slice(0, 120)}`, "Medium");
  const r2 = await req("POST", `${GW}/api/registry/token`, { headers: { "X-Demo-Auth-Key": DEMO }, body: { tenantId: "qii_creative", employeeId: 99999 } });
  check("TOKEN-NONEXISTENT-EMPLOYEE", r2.status >= 400, `status=${r2.status} ${r2.text.slice(0, 160)}`, "High");
  const r3 = await req("POST", `${GW}/api/registry/token`, { headers: { "X-Demo-Auth-Key": DEMO }, body: { tenantId: "qa_should_not_exist", employeeId: 1 } });
  check("TOKEN-HALF-PROVISIONED-TENANT", r3.status >= 400, `status=${r3.status} ${r3.text.slice(0, 160)}`, "Medium");
  const r4 = await req("POST", `${GW}/api/registry/token`, { headers: { "X-Demo-Auth-Key": DEMO }, body: { tenantId: "techshop_retail", employeeId: 1 } });
  check("TOKEN-OTHER-TENANT", r4.status === 200, `techshop status=${r4.status}`, "Info");
  const exp = decode(tAdm); check("SESSION-TTL-7D (FR-AUTH-06)", exp.exp - exp.iat >= 7 * 86400, `ttl=${exp.exp - exp.iat}s`, "Medium");
}

// ---- JWT forgery ----
{
  const now = Math.floor(Date.now() / 1000);
  const none = signJwt({ sub: "x", tenant_id: "qii_creative", employee_id: 1, iat: now, exp: now + 999 }, "", "none");
  let r = await req("GET", `${H}/employees?page=0&size=1`, { headers: A(none) });
  check("JWT-ALG-NONE", r.status === 401, `status=${r.status}`, "Critical");
  const bad = signJwt({ sub: "x", tenant_id: "qii_creative", employee_id: 1, iat: now, exp: now + 999 }, "wrong-secret-wrong-secret-wrong-secret");
  r = await req("GET", `${H}/employees?page=0&size=1`, { headers: A(bad) });
  check("JWT-WRONG-SECRET", r.status === 401, `status=${r.status}`, "Critical");
  const expired = signJwt({ sub: "x", tenant_id: "qii_creative", employee_id: 1, iat: now - 999, exp: now - 10 }, SECRET);
  r = await req("GET", `${H}/employees?page=0&size=1`, { headers: A(expired) });
  check("JWT-EXPIRED", r.status === 401, `status=${r.status}`, "Critical");
  const forged = signJwt({ sub: "x", tenant_id: "qii_creative", employee_id: 1, iat: now, exp: now + 999 }, SECRET);
  r = await req("GET", `${H}/me`, { headers: A(forged) });
  check("JWT-DEFAULT-SECRET-FORGE (secret in repo)", r.status === 401, `status=${r.status} -> forged SUPERADMIN works if 200`, "Critical");
  const noExp = signJwt({ sub: "x", tenant_id: "qii_creative", employee_id: 1, iat: now }, SECRET);
  r = await req("GET", `${H}/me`, { headers: A(noExp) });
  check("JWT-NO-EXP-ACCEPTED", r.status === 401, `status=${r.status}`, "High");
}

// ---- header spoofing via gateway ----
{
  let r = await req("GET", `${H}/me`, { headers: { ...A(tEmp), "X-Employee-ID": "1", "X-Tenant-ID": "techshop_retail" } });
  check("SPOOF-X-EMPLOYEE-ID", r.json?.employeeId == EMP || r.json?.id == EMP || r.json?.employee?.id == EMP, `me=${r.text.slice(0, 160)}`, "Critical");
  r = await req("GET", `${H}/me`, { headers: { ...A(tEmp), "X-Service-Key": "x", "X-Roles": "SUPERADMIN" } });
  check("SPOOF-EXTRA-HEADERS", r.status === 200, `status=${r.status}`, "Info");
}

// ---- direct service bypass (internal ports) ----
for (const [n, url] of [["hrms", "http://127.0.0.1:8081/api/employees?page=0&size=2"], ["payroll", "http://127.0.0.1:8082/api/payroll/payslips"], ["notif", "http://127.0.0.1:8083/api/notifications"], ["files", "http://127.0.0.1:8084/api/files"], ["reports", "http://127.0.0.1:8085/api/reports/exports"]]) {
  const r = await req("GET", url, { headers: { "X-Tenant-ID": "qii_creative", "X-Employee-ID": "1" } });
  check(`DIRECT-${n}-NO-SERVICE-KEY`, r.status === 401 || r.status === 403, `status=${r.status}`, "Critical");
  const r2 = await req("GET", url, { headers: { "X-Tenant-ID": "qii_creative", "X-Employee-ID": "1", "X-Service-Key": DEMO } });
  check(`DIRECT-${n}-WITH-DEFAULT-KEY (demo key == service key)`, !(r2.status === 200), `status=${r2.status}`, "High");
}

// ---- registry admin ----
{
  let r = await req("POST", `${GW}/api/registry/provision`, { body: { tenantId: "qa_probe_x" } });
  check("PROVISION-NO-KEY", r.status === 401 || r.status === 403, `status=${r.status}`, "Critical");
  r = await req("GET", `${GW}/api/registry/tenants`, {});
  check("REGISTRY-LIST-TENANTS-NO-KEY", r.status === 401 || r.status === 403, `status=${r.status} ${r.text.slice(0, 120)}`, "High");
  r = await req("GET", `${GW}/api/registry/tenants/qii_creative`, { headers: A(tEmp) });
  check("REGISTRY-TENANT-DETAIL-AS-EMPLOYEE", !(r.status === 200 && /password|jdbc/i.test(r.text)), `status=${r.status} ${r.text.slice(0, 160)}`, "Critical");
  r = await req("POST", `${GW}/api/registry/provision`, { headers: { "X-Platform-Admin-Key": "local-platform-admin-change-me" }, body: { tenantId: "bad'; DROP DATABASE x; --" } });
  check("PROVISION-SQLI-TENANT-ID", r.status === 400, `status=${r.status} ${r.text.slice(0, 160)}`, "Critical");
}

// ---- IDOR / RBAC as plain employee (Karim id=9) ----
const idor = [
  ["GET", `${H}/employees/1`, "other employee detail"],
  ["GET", `${H}/leave-balances/employee/1`, "other leave balance"],
  ["GET", `${H}/attendance/employee/1`, "other attendance"],
  ["GET", `${H}/leave/employee/1`, "other leave list"],
  ["GET", `${H}/employees/1/documents`, "other documents"],
  ["GET", `${H}/work-logs/employee/1`, "other work logs"],
  ["GET", `${H}/payroll-facts/employee/1?month=2026-09`, "payroll facts"],
  ["GET", `${P}/payroll/salary-history/employee/1`, "other salary history"],
  ["GET", `${P}/payroll/payslips/employee/1`, "other payslips"],
  ["GET", `${P}/payroll/payslips`, "all payslips"],
  ["GET", `${P}/payroll/export.csv?month=2026-09`, "payroll CSV"],
  ["GET", `${P}/payroll/config`, "payroll config"],
  ["GET", `${H}/audit`, "audit log"],
  ["GET", `${H}/dashboard/admin`, "admin dashboard"],
  ["GET", `${H}/reports/leave-balances.csv`, "leave balances CSV"],
  ["GET", `${H}/reports/headcount.csv`, "headcount CSV"],
  ["GET", `${H}/attendance/team`, "team attendance"],
  ["GET", `${H}/attendance/corrections`, "all corrections"],
  ["GET", `${GW}/files/api/files`, "all files"],
  ["GET", `${GW}/reports/api/reports/exports`, "report exports"],
  ["GET", `${H}/roles`, "roles"],
  ["GET", `${H}/settings`, "settings"],
];
for (const [m, url, what] of idor) {
  const r = await req(m, url, { headers: A(tEmp) });
  const leaked = r.status === 200 && r.text.length > 2 && r.text !== "[]" && !/"content":\[\]/.test(r.text);
  check(`IDOR-EMP ${what}`, !leaked, `status=${r.status} len=${r.text.length} ${r.text.slice(0, 140)}`, /salary|payslip|payroll|document|audit/.test(what) ? "Critical" : "High");
}

// employee list leaks salary?
{
  const r = await req("GET", `${H}/employees?page=0&size=50`, { headers: A(tEmp) });
  const rows = r.json?.content || [];
  const sal = rows.filter((x) => x.baseSalary != null || x.hourlyRate != null || x.salary != null);
  check("EMP-LIST-SALARY-FIELDS", sal.length === 0, `status=${r.status} rows=${rows.length} withSalary=${sal.length} sample=${JSON.stringify(rows[0] || {}).slice(0, 200)}`, "Critical");
  const pii = rows.filter((x) => x.dateOfBirth || x.address || x.personalEmail || x.phone);
  check("EMP-LIST-PERSONAL-PII", pii.length === 0, `withPII=${pii.length}`, "High");
}

// ---- privilege escalation ----
{
  const roles = await req("GET", `${H}/roles`, { headers: A(tAdm) });
  const sa = (roles.json?.content || roles.json || []).find?.((r) => r.code === "SUPERADMIN");
  let r = await req("POST", `${H}/roles/${sa?.id}/assign`, { headers: A(tEmp), body: { employeeId: EMP } });
  check("PRIVESC-EMP-ASSIGN-SUPERADMIN", r.status === 403, `status=${r.status}`, "Critical");
  r = await req("POST", `${H}/roles/${sa?.id}/assign`, { headers: A(tHr), body: { employeeId: HR } });
  check("PRIVESC-HR-ASSIGN-SELF-SUPERADMIN", r.status === 403, `status=${r.status}`, "Critical");
  r = await req("POST", `${H}/roles/${sa?.id}/unassign`, { headers: A(tAdm), body: { employeeId: ADMIN } });
  check("LAST-SUPERADMIN-UNASSIGN (FR-AUTH-07)", r.status >= 400, `status=${r.status} ${r.text.slice(0, 150)}`, "Critical");
  r = await req("PUT", `${H}/employees/${EMP}`, { headers: A(tEmp), body: { baseSalary: 999999, status: "ACTIVE", designationId: 1 } });
  check("SELF-EDIT-EMPLOYMENT-FIELDS (FR-EMP-06)", r.status === 403, `status=${r.status} ${r.text.slice(0, 150)}`, "Critical");
  r = await req("PATCH", `${H}/me/personal`, { headers: A(tEmp), body: { phone: "+8801700000000", baseSalary: 999999, email: "hack@x.com", managerId: null } });
  const me = await req("GET", `${H}/me`, { headers: A(tEmp) });
  check("ME-PERSONAL-MASS-ASSIGN", !/999999|hack@x.com/.test(me.text), `patch=${r.status} me=${me.text.slice(0, 200)}`, "Critical");
  r = await req("PUT", `${H}/employees/${ADMIN}`, { headers: A(tAdm), body: { status: "INACTIVE" } });
  check("SELF-DEACTIVATE (FR-EMP-11)", r.status >= 400, `status=${r.status} ${r.text.slice(0, 150)}`, "High");
}

// ---- notifications isolation ----
{
  const r = await req("GET", `${GW}/notifications/api/notifications?recipientId=1`, { headers: A(tEmp) });
  const others = (r.json?.content || r.json || []).filter?.((n) => n.recipientId && n.recipientId != EMP) || [];
  check("NOTIF-READ-OTHERS", others.length === 0, `status=${r.status} others=${others.length}`, "High");
  const r2 = await req("POST", `${GW}/notifications/api/notifications`, { headers: A(tEmp), body: { recipientId: 1, type: "PHISH", text: "Click http://evil", title: "x" } });
  check("NOTIF-EMPLOYEE-CAN-CREATE-FOR-OTHERS", r2.status === 403 || r2.status === 401, `status=${r2.status} ${r2.text.slice(0, 120)}`, "High");
}

// ---- tenant isolation: qii token -> request carrying techshop ids ----
{
  const tTs = await token(1, "techshop_retail");
  const r = await req("GET", `${H}/employees?page=0&size=3`, { headers: A(tTs) });
  check("TECHSHOP-EMPLOYEES-WORK", r.status === 200, `status=${r.status} ${r.text.slice(0, 200)}`, "High");
  const tIm = await token(1, "impit_limited");
  const r2 = await req("GET", `${H}/employees?page=0&size=3`, { headers: A(tIm) });
  check("IMPIT-EMPLOYEES-WORK", r2.status === 200, `status=${r2.status} ${r2.text.slice(0, 200)}`, "High");
  const r3 = await req("GET", `${P}/payroll/config`, { headers: A(tTs) });
  check("TECHSHOP-PAYROLL-CONFIG", r3.status === 200, `status=${r3.status} ${r3.text.slice(0, 200)}`, "High");
}

// ---- input robustness: 500s ----
{
  const cases = [
    ["GET", `${H}/employees/abc`],
    ["GET", `${H}/employees?page=-1&size=100000`],
    ["GET", `${H}/employees?sort=password,asc`],
    ["GET", `${H}/employees?sort=fullName;drop`],
    ["POST", `${H}/leave-applications`, "{bad json"],
    ["POST", `${H}/attendance/check-in`, { note: "x".repeat(20000) }],
    ["GET", `${P}/payroll/export.csv?month=2026-13`],
    ["GET", `${H}/dashboard/work-hours?period=xyz&date=notadate`],
  ];
  for (const [m, url, body] of cases) {
    const r = await req(m, url, { headers: A(tAdm), body });
    check(`NO-500 ${m} ${url.replace(GW, "")}`, r.status < 500, `status=${r.status} ${r.text.slice(0, 140)}`, "Medium");
  }
}

const f = summary("security");
import fs from "node:fs";
fs.writeFileSync("sec-results.json", JSON.stringify(f, null, 2));
