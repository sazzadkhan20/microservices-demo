#!/usr/bin/env node
/**
 * Adversarial QA harness for Qii HRMS (no mutations that destroy seed permanently where avoidable).
 * Outputs JSON findings to stdout.
 */
import http from "node:http";

const GW = process.env.GATEWAY_URL || "http://127.0.0.1:8090";
const FE = process.env.FE_URL || "http://127.0.0.1:3000";
const DEMO = process.env.DEMO_AUTH_KEY || process.env.GATEWAY_SERVICE_KEY || "local-service-key-change-me";
const findings = [];

function find(sev, area, id, title, evidence, impact) {
  findings.push({ sev, area, id, title, evidence, impact });
}

function req(method, url, { headers = {}, body, timeout = 15000 } = {}) {
  return new Promise((resolve) => {
    const u = new URL(url);
    const data = body == null ? null : typeof body === "string" ? body : JSON.stringify(body);
    const h = { ...headers };
    if (data != null && !h["Content-Type"]) h["Content-Type"] = "application/json";
    if (data != null) h["Content-Length"] = Buffer.byteLength(data);
    const r = http.request(
      {
        hostname: u.hostname,
        port: u.port,
        path: u.pathname + u.search,
        method,
        headers: h,
        timeout,
      },
      (res) => {
        const chunks = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () => {
          const raw = Buffer.concat(chunks).toString("utf8");
          let json = null;
          try {
            json = JSON.parse(raw);
          } catch {}
          resolve({ status: res.statusCode, headers: res.headers, raw, json });
        });
      },
    );
    r.on("error", (e) => resolve({ status: 0, error: String(e.message || e), raw: "", json: null }));
    r.on("timeout", () => {
      r.destroy();
      resolve({ status: 0, error: "timeout", raw: "", json: null });
    });
    if (data != null) r.write(data);
    r.end();
  });
}

async function token(employeeId) {
  const r = await req("POST", `${GW}/api/registry/token`, {
    headers: { "X-Demo-Auth-Key": DEMO },
    body: { tenantId: "qii_creative", employeeId: Number(employeeId) },
  });
  return r.json?.accessToken || r.json?.token || null;
}

function auth(t) {
  return { Authorization: `Bearer ${t}` };
}

async function main() {
  // Health
  for (const [name, port] of [
    ["registry", 8080],
    ["hrms", 8081],
    ["payroll", 8082],
    ["notif", 8083],
    ["files", 8084],
    ["reports", 8085],
    ["gateway", 8090],
  ]) {
    const r = await req("GET", `http://127.0.0.1:${port}/actuator/health`);
    if (r.status !== 200) find("Blocker", "System", `HEALTH-${name}`, `${name} unhealthy`, `status=${r.status}`, "Stack not testable");
  }

  const tAdmin = await token(1); // SUPERADMIN Asman
  const tHr = await token(2); // HR Nadia
  const tMgr = await token(4); // MANAGER Lina
  const tEmp = await token(9); // employee (from acceptance script cast)
  if (!tAdmin || !tHr || !tMgr || !tEmp) {
    find("Blocker", "Auth", "TOKEN", "Failed to mint tokens for cast", { tAdmin: !!tAdmin, tHr: !!tHr, tMgr: !!tMgr, tEmp: !!tEmp }, "Cannot continue RBAC tests");
    console.log(JSON.stringify({ findings }, null, 2));
    return;
  }

  // Spoof tenant header
  {
    const r = await req("GET", `${GW}/hrms/api/employees?page=1&size=1`, {
      headers: { ...auth(tAdmin), "X-Tenant-ID": "techshop" },
    });
    const tenantLeak = JSON.stringify(r.json || {}).includes("techshop");
    // Should still be qii data
    const sample = r.json?.content?.[0];
    if (r.status === 200 && sample?.email && String(sample.email).includes("techshop")) {
      find("Blocker", "Security", "TENANT-SPOOF", "Client X-Tenant-ID spoof switched tenant data", sample, "Cross-tenant data breach");
    }
  }

  // Direct service bypass (no JWT) on HRMS
  {
    const r = await req("GET", `http://127.0.0.1:8081/api/employees?page=1&size=1`, {
      headers: { "X-Tenant-ID": "qii_creative" },
    });
    if (r.status === 200 && (r.json?.content?.length || r.json?.length)) {
      find(
        "Critical",
        "Security",
        "DIRECT-HRMS-NO-JWT",
        "HRMS accepts unauthenticated calls with only X-Tenant-ID",
        { status: r.status, n: r.json?.content?.length ?? r.json?.length },
        "Gateway trust boundary bypassable on internal ports",
      );
    } else if (r.status === 200) {
      find("Major", "Security", "DIRECT-HRMS-200", "HRMS returned 200 without JWT", { status: r.status, body: (r.raw || "").slice(0, 200) }, "Auth may be optional on service");
    }
  }

  // Employee IDOR: emp tries to read another employee leave balances / docs
  {
    const r = await req("GET", `${GW}/hrms/api/leave-balances/employee/1`, { headers: auth(tEmp) });
    if (r.status === 200) {
      find("Critical", "RBAC", "IDOR-LEAVE-BAL", "Employee can read SUPERADMIN leave balances", { status: r.status, body: (r.raw || "").slice(0, 240) }, "PII / HR data leak");
    }
    const r2 = await req("GET", `${GW}/hrms/api/employees/1`, { headers: auth(tEmp) });
    if (r2.status === 200 && r2.json?.baseSalary != null) {
      find("Critical", "RBAC", "IDOR-SALARY", "Employee can read another person's baseSalary", { status: r2.status, salary: r2.json.baseSalary, name: r2.json.fullName }, "Compensation leak");
    } else if (r2.status === 200) {
      find("Major", "RBAC", "IDOR-EMP-DETAIL", "Employee can open another employee full profile via API", { status: r2.status, code: r2.json?.employeeCode }, "Directory may be OK but full record includes sensitive fields");
    }
  }

  // Payroll permission: emp calculate/list others
  {
    const r = await req("GET", `${GW}/payroll/api/payslips?page=1&size=20`, { headers: auth(tEmp) });
    const rows = r.json?.content || r.json || [];
    const others = Array.isArray(rows) ? rows.filter((p) => String(p.employeeId) !== "9" && p.employeeId != 9) : [];
    if (r.status === 200 && others.length > 0) {
      find("Critical", "RBAC", "PAYROLL-LIST-ALL", "Employee sees other employees' payslips", { status: r.status, others: others.length, sample: others[0] }, "Payroll confidentiality broken");
    }
    const calc = await req("POST", `${GW}/payroll/api/payslips/calculate`, {
      headers: auth(tEmp),
      body: { employeeId: 1, year: 2026, month: 10 },
    });
    if (calc.status >= 200 && calc.status < 300) {
      find("Critical", "RBAC", "PAYROLL-CALC-EMP", "Employee can trigger payroll calculate for others", { status: calc.status, body: (calc.raw || "").slice(0, 200) }, "Unauthorized payroll mutation");
    }
  }

  // Create employee validation gaps
  {
    const email = `qa.bad.${Date.now()}@qiicreative.local`;
    const r = await req("POST", `${GW}/hrms/api/employees`, {
      headers: auth(tAdmin),
      body: {
        fullName: "QA Dup",
        email,
        designationId: 6,
        departmentId: 2,
        teamId: 1,
        locationId: 1,
        employmentType: "FULL_TIME",
        startDate: "2026-10-29",
        payCurrency: "BDT",
      },
    });
    const r2 = await req("POST", `${GW}/hrms/api/employees`, {
      headers: auth(tAdmin),
      body: {
        fullName: "QA Dup2",
        email,
        designationId: 6,
        departmentId: 2,
        teamId: 1,
        locationId: 1,
        employmentType: "FULL_TIME",
        startDate: "2026-10-29",
        payCurrency: "BDT",
      },
    });
    if (r.status >= 200 && r.status < 300 && r2.status >= 200 && r2.status < 300) {
      find("Major", "Employees", "DUP-EMAIL", "Duplicate work email allowed", { email, id1: r.json?.id, id2: r2.json?.id }, "Identity integrity broken");
    }
  }

  // Leave request without category / invalid range
  {
    const bad = await req("POST", `${GW}/hrms/api/leave/request`, {
      headers: auth(tEmp),
      body: { startDate: "2026-12-20", endDate: "2026-12-10", leaveTypeCode: "CASUAL", reason: "QA inverted" },
    });
    if (bad.status >= 200 && bad.status < 300) {
      find("Major", "Leave", "LEAVE-INVERTED-DATES", "Leave request accepts end before start", { status: bad.status, body: (bad.raw || "").slice(0, 200) }, "Corrupt leave ledger");
    }
    const noType = await req("POST", `${GW}/hrms/api/leave/request`, {
      headers: auth(tEmp),
      body: { startDate: "2026-11-02", endDate: "2026-11-02", reason: "QA no type" },
    });
    if (noType.status >= 200 && noType.status < 300) {
      find("Major", "Leave", "LEAVE-NO-TYPE", "Leave request accepted without type/category", { status: noType.status }, "Policy engine bypass");
    }
  }

  // Attendance double check-in
  {
    const a = await req("POST", `${GW}/hrms/api/attendance/check-in`, { headers: auth(tEmp), body: {} });
    const b = await req("POST", `${GW}/hrms/api/attendance/check-in`, { headers: auth(tEmp), body: {} });
    if (a.status >= 200 && a.status < 300 && b.status >= 200 && b.status < 300) {
      // may be idempotent OK — check if times differ wildly
      const today = await req("GET", `${GW}/hrms/api/attendance/today`, { headers: auth(tEmp) });
      find("Minor", "Attendance", "CHECKIN-DOUBLE", "Second check-in returned success", { a: a.status, b: b.status, today: today.json }, "Confirm idempotent vs overwrite");
    }
  }

  // Corrections approve without permission
  {
    const list = await req("GET", `${GW}/hrms/api/attendance/corrections?status=PENDING`, { headers: auth(tEmp) });
    if (list.status === 200 && Array.isArray(list.json) && list.json.length) {
      find("Major", "RBAC", "CORR-LIST-EMP", "Employee listed all pending corrections", { n: list.json.length }, "Manager queue leak");
    }
    const create = await req("POST", `${GW}/hrms/api/attendance/corrections`, {
      headers: auth(tEmp),
      body: { date: "2026-10-28", checkIn: "09:00", checkOut: "18:00", reason: "QA forgot" },
    });
    // try approve as self if id returned
    const id = create.json?.id;
    if (id) {
      const ap = await req("POST", `${GW}/hrms/api/attendance/corrections/${id}/approve`, {
        headers: auth(tEmp),
        body: {},
      });
      if (ap.status >= 200 && ap.status < 300) {
        find("Critical", "RBAC", "CORR-SELF-APPROVE", "Employee approved own attendance correction", { id, status: ap.status }, "Attendance fraud");
      }
    }
  }

  // Roles: emp tries manage
  {
    const r = await req("POST", `${GW}/hrms/api/roles`, {
      headers: auth(tEmp),
      body: { code: "HACKER", name: "Hacker", permissions: ["*:*"] },
    });
    if (r.status >= 200 && r.status < 300) {
      find("Blocker", "RBAC", "ROLE-CREATE-EMP", "Employee created a role", { status: r.status, body: r.json }, "Privilege escalation");
    }
  }

  // Assign self SUPERADMIN
  {
    const r = await req("POST", `${GW}/hrms/api/employees/9/roles`, {
      headers: auth(tEmp),
      body: { roleCodes: ["SUPERADMIN"] },
    });
    if (r.status >= 200 && r.status < 300) {
      find("Blocker", "RBAC", "SELF-PROMOTE", "Employee assigned self SUPERADMIN", { status: r.status, body: r.json }, "Full privilege escalation");
    }
  }

  // Files: upload type/size if endpoint exists
  {
    const r = await req("GET", `${GW}/files/api/files?page=1&size=1`, { headers: auth(tEmp) });
    if (r.status === 404) {
      find("Major", "Files", "FILES-ROUTE", "Files list route 404 via gateway", { status: r.status, body: (r.raw || "").slice(0, 120) }, "Document module incomplete");
    }
  }

  // Notifications mark-read IDOR
  {
    const list = await req("GET", `${GW}/notifications/api/notifications?page=0&size=5`, { headers: auth(tAdmin) });
    const n = list.json?.content?.[0] || list.json?.[0];
    if (n?.id) {
      const mark = await req("POST", `${GW}/notifications/api/notifications/${n.id}/read`, { headers: auth(tEmp), body: {} });
      if (mark.status >= 200 && mark.status < 300) {
        find("Major", "RBAC", "NOTIF-IDOR-READ", "Employee marked another user's notification read", { id: n.id, status: mark.status }, "Cross-user notification mutation");
      }
    }
  }

  // PageQuery contract
  {
    const r = await req("GET", `${GW}/hrms/api/employees?page=1&size=2&sort=fullName,asc&q=Asman`, { headers: auth(tAdmin) });
    if (!r.json || (!("content" in (r.json || {})) && !("items" in (r.json || {})))) {
      find("Major", "API", "PAGE-ENVELOPE", "Employees list missing PageResponse envelope", { keys: Object.keys(r.json || {}), status: r.status }, "FE DataTable contract risk");
    }
    const r0 = await req("GET", `${GW}/hrms/api/employees?page=0&size=2`, { headers: auth(tAdmin) });
    // document whether page is 0 or 1 based
    findings.push({
      sev: "Info",
      area: "API",
      id: "PAGE-INDEX",
      title: "Observed page indexing behavior",
      evidence: { page1: r.status, page0: r0.status, p1total: r.json?.totalElements, p0total: r0.json?.totalElements, p0n: r0.json?.content?.length },
      impact: "Inconsistent page=0 vs page=1 across services breaks FE",
    });
  }

  // TechShop tenant still provisioned? (canonical id = techshop_retail)
  {
    const r = await req("POST", `${GW}/api/registry/token`, {
      headers: { "X-Demo-Auth-Key": DEMO },
      body: { tenantId: "techshop_retail", employeeId: 1 },
    });
    if (r.status >= 200 && r.status < 300 && (r.json?.accessToken || r.json?.token)) {
      const tt = r.json.accessToken || r.json.token;
      const em = await req("GET", `${GW}/hrms/api/employees?page=1&size=1`, { headers: auth(tt) });
      findings.push({
        sev: "Info",
        area: "MultiTenant",
        id: "TECHSHOP-ALIVE",
        title: "TechShop tenant token + employees reachable",
        evidence: { token: true, empStatus: em.status, sample: em.json?.content?.[0]?.employeeCode || em.json?.content?.[0]?.fullName },
        impact: "Positive signal for multi-business reuse",
      });
    } else {
      find("Major", "MultiTenant", "TECHSHOP-DOWN", "TechShop tenant token failed", { status: r.status, body: (r.raw || "").slice(0, 200) }, "Only Qii pack may work — reusability claim weak");
    }
  }

  // Settings / org write as employee
  {
    const r = await req("PUT", `${GW}/hrms/api/settings`, {
      headers: auth(tEmp),
      body: { companyName: "Hacked Co" },
    });
    if (r.status >= 200 && r.status < 300) {
      find("Critical", "RBAC", "SETTINGS-WRITE-EMP", "Employee mutated tenant settings", { status: r.status, body: (r.raw || "").slice(0, 200) }, "Tenant config takeover");
    }
  }

  // FE BFF session (skip when Next.js is not running — backend QA still valid)
  {
    const feProbe = await req("GET", `${FE}/api/auth/accounts`, { timeout: 2000 });
    if (feProbe.status === 0) {
      findings.push({
        sev: "Info",
        area: "Frontend",
        id: "FE-SKIPPED",
        title: "FE not running — BFF checks skipped",
        evidence: { error: feProbe.error },
        impact: "Backend adversarial coverage still applies",
      });
    } else {
    const login = await req("POST", `${FE}/api/auth/login`, { body: { employeeId: "1" } });
    const setCookie = login.headers?.["set-cookie"];
    if (login.status !== 200) {
      find("Blocker", "Frontend", "FE-LOGIN", "BFF login failed", { status: login.status, body: (login.raw || "").slice(0, 200) }, "App unusable");
    } else if (!setCookie || !String(setCookie).includes("qii_session")) {
      find("Critical", "Frontend", "FE-COOKIE", "Login OK but qii_session cookie missing", { setCookie }, "Session may not persist");
    }
    const cookie = Array.isArray(setCookie) ? setCookie.map((c) => c.split(";")[0]).join("; ") : String(setCookie || "").split(";")[0];
    const routes = [
      "/",
      "/employees",
      "/employees/new",
      "/employees/1",
      "/leave",
      "/approvals",
      "/attendance",
      "/payroll",
      "/directory",
      "/checklists",
      "/work-log",
      "/tasks",
      "/notifications",
      "/profile",
      "/reports",
      "/reports/monthly",
      "/admin/dashboard",
      "/settings/org",
      "/settings/roles",
      "/settings/leave",
      "/settings/holidays",
      "/settings/schedules",
      "/settings/notifications",
      "/settings/audit",
      "/team/attendance",
      "/team/work-logs",
      "/leave/calendar",
    ];
    for (const p of routes) {
      const rr = await req("GET", `${FE}${p}`, { headers: cookie ? { Cookie: cookie } : {} });
      if (rr.status >= 500) find("Critical", "Frontend", `FE-5XX-${p}`, `Page ${p} returned ${rr.status}`, { status: rr.status }, "Runtime crash");
      else if (rr.status === 404) find("Major", "Frontend", `FE-404-${p}`, `Prototype/requirements route missing: ${p}`, { status: rr.status }, "Feature gap vs prototype");
      else if (rr.status === 401 || rr.status === 403) find("Major", "Frontend", `FE-AUTH-${p}`, `Authenticated route denied: ${p}`, { status: rr.status }, "Session/RBAC FE bug");
    }

    // API through BFF as employee — salary leak via FE proxy
    const loginEmp = await req("POST", `${FE}/api/auth/login`, { body: { employeeId: "9" } });
    const sc2 = loginEmp.headers?.["set-cookie"];
    const cookie2 = Array.isArray(sc2) ? sc2.map((c) => c.split(";")[0]).join("; ") : String(sc2 || "").split(";")[0];
    const leak = await req("GET", `${FE}/api/hrms/employees/1`, { headers: cookie2 ? { Cookie: cookie2 } : {} });
    if (leak.status === 200 && leak.json?.baseSalary != null) {
      find("Critical", "Frontend", "FE-PROXY-SALARY", "BFF proxies salary of other employee to low-privilege session", { salary: leak.json.baseSalary }, "UI may hide but API still leaks");
    }
    } // end FE-up else
  }

  // Leave approve as wrong role
  {
    // create leave as emp
    const created = await req("POST", `${GW}/hrms/api/leave/request`, {
      headers: auth(tEmp),
      body: {
        startDate: "2026-11-10",
        endDate: "2026-11-10",
        leaveTypeCode: "CASUAL",
        leaveCategoryCode: "CASUAL",
        reason: "QA approve test",
      },
    });
    const lid = created.json?.id;
    if (lid) {
      const selfApprove = await req("POST", `${GW}/hrms/api/leave-applications/${lid}/approve`, {
        headers: auth(tEmp),
        body: { approverEmployeeId: 9, comment: "self" },
      });
      if (selfApprove.status >= 200 && selfApprove.status < 300) {
        find("Critical", "Leave", "LEAVE-SELF-APPROVE", "Requester approved own leave", { id: lid, status: selfApprove.status }, "Leave policy fraud");
      }
    } else if (created.status >= 400) {
      findings.push({
        sev: "Info",
        area: "Leave",
        id: "LEAVE-CREATE-SHAPE",
        title: "Leave request create rejected — check FE payload shape",
        evidence: { status: created.status, body: (created.raw || "").slice(0, 300) },
        impact: "FE leave form may be incompatible with API",
      });
    }
  }

  // Reports without permission
  {
    const r = await req("POST", `${GW}/reports/api/attendance/monthly`, {
      headers: auth(tEmp),
      body: { year: 2026, month: 10 },
    });
    if (r.status >= 200 && r.status < 300) {
      find("Major", "RBAC", "REPORTS-EMP", "Employee can generate attendance monthly report", { status: r.status }, "Org-wide attendance export leak");
    }
  }

  // Audit as employee
  {
    const r = await req("GET", `${GW}/hrms/api/audit?page=1&size=5`, { headers: auth(tEmp) });
    if (r.status === 200) {
      find("Major", "RBAC", "AUDIT-EMP", "Employee can read audit log", { status: r.status, n: r.json?.content?.length }, "Security monitoring data leak");
    }
  }

  console.log(JSON.stringify({ generatedAt: new Date().toISOString(), count: findings.length, findings }, null, 2));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
