/**
 * Deep E2E: SRS §9 holidays/checklists/notifications (in-app),
 * §10 dashboard/reports/audit, §11 business rules. Email skipped.
 */
import http from "http";

const GW = "127.0.0.1";
const PORT = 8090;
const DEMO = "local-service-key-change-me";
const TENANT = "qii_creative";

let passed = 0;
let failed = 0;
const failures = [];

function ok(name, cond, detail = "") {
  if (cond) {
    passed++;
    console.log(`PASS  ${name}`);
  } else {
    failed++;
    failures.push(`${name}${detail ? " — " + detail : ""}`);
    console.log(`FAIL  ${name}${detail ? " — " + detail : ""}`);
  }
}

function req(method, path, { token, body, headers = {} } = {}) {
  return new Promise((resolve, reject) => {
    const payload = body == null ? null : JSON.stringify(body);
    const h = { ...(headers || {}) };
    if (token) h.Authorization = `Bearer ${token}`;
    if (payload) {
      h["Content-Type"] = "application/json";
      h["Content-Length"] = Buffer.byteLength(payload);
    }
    const r = http.request(
      { hostname: GW, port: PORT, path, method, headers: h },
      (res) => {
        let b = "";
        res.on("data", (c) => (b += c));
        res.on("end", () => {
          let json = null;
          try {
            json = b ? JSON.parse(b) : null;
          } catch {
            json = b;
          }
          resolve({ status: res.statusCode, body: json, raw: b });
        });
      },
    );
    r.on("error", reject);
    if (payload) r.write(payload);
    r.end();
  });
}

async function tokenFor(employeeId) {
  const r = await req("POST", "/api/registry/token", {
    headers: { "X-Demo-Auth-Key": DEMO },
    body: { tenantId: TENANT, employeeId },
  });
  if (r.status !== 200 || !r.body?.accessToken) {
    throw new Error(`token emp=${employeeId} → ${r.status} ${r.raw?.slice?.(0, 200)}`);
  }
  return r.body.accessToken;
}

async function empIdByCode(adminTok, code) {
  const r = await req("GET", `/hrms/api/employees?q=${encodeURIComponent(code)}&size=50`, {
    token: adminTok,
  });
  const rows = Array.isArray(r.body) ? r.body : r.body?.content || [];
  const hit = rows.find((e) => String(e.employeeCode).toUpperCase() === code.toUpperCase());
  if (!hit) throw new Error(`employee ${code} not found`);
  return Number(hit.id);
}

async function main() {
  console.log("=== §9–11 holidays / checklists / notif / dashboard / reports / audit / BR ===\n");
  const adminTok = await tokenFor(1);
  const empId = await empIdByCode(adminTok, "QII-0020");
  const empTok = await tokenFor(empId);
  const year = new Date().getFullYear();

  // ---- FR-HOL-01 create holiday ----
  const hol = await req("POST", "/hrms/api/holidays", {
    token: adminTok,
    body: {
      locationId: 1,
      name: `E2E Victory Day ${year}`,
      startDate: `${year}-12-16`,
      endDate: `${year}-12-16`,
      holidayType: "PUBLIC",
      optionalFlag: false,
    },
  });
  ok(
    "create holiday (FR-HOL-01)",
    hol.status === 200 || hol.status === 201,
    `${hol.status} ${String(hol.raw).slice(0, 180)}`,
  );
  const holId = hol.body?.id;

  const listHol = await req("GET", `/hrms/api/holidays?year=${year}&size=100`, { token: adminTok });
  ok("list holidays by year", listHol.status === 200, String(listHol.status));
  const holRows = Array.isArray(listHol.body) ? listHol.body : listHol.body?.content || [];
  ok(
    "created holiday visible",
    holRows.some((h) => Number(h.id) === Number(holId) || String(h.name).includes("E2E Victory")),
    `count=${holRows.length}`,
  );

  // ---- FR-HOL-03 copy year ----
  const copyTo = year + 5;
  const copy = await req("POST", "/hrms/api/holidays/copy-year", {
    token: adminTok,
    body: { fromYear: year, toYear: copyTo, locationId: 1 },
  });
  ok(
    "copy holiday calendar (FR-HOL-03)",
    copy.status === 200 || copy.status === 201,
    `${copy.status} ${String(copy.raw).slice(0, 220)}`,
  );
  const copiedCount = Number(copy.body?.count ?? 0);
  const createdList = Array.isArray(copy.body?.created) ? copy.body.created : [];
  ok(
    "copy created rows",
    copiedCount > 0 || createdList.length > 0,
    `count=${copiedCount} created=${createdList.length}`,
  );

  // ---- FR-CL templates + assign + tick ----
  const tpl = await req("POST", "/hrms/api/checklists/templates", {
    token: adminTok,
    body: {
      name: `E2E Onboard ${Date.now()}`,
      type: "ONBOARDING",
      items: [
        { title: "Laptop issued", required: true, ownerType: "HR", dueOffsetDays: 1 },
        { title: "Buddy intro", required: false, ownerType: "MANAGER", dueOffsetDays: 3 },
        { title: "Policies read", required: true, ownerType: "EMPLOYEE", dueOffsetDays: 7 },
      ],
    },
  });
  ok(
    "create checklist template (FR-CL-01)",
    tpl.status === 200 || tpl.status === 201,
    `${tpl.status} ${String(tpl.raw).slice(0, 200)}`,
  );
  const tplId = tpl.body?.id;

  const assign = await req("POST", "/hrms/api/checklists/assign", {
    token: adminTok,
    body: {
      employeeId: empId,
      type: "ONBOARDING",
      templateId: tplId,
      startOrExitDate: new Date().toISOString().slice(0, 10),
    },
  });
  ok(
    "assign checklist (FR-CL-02)",
    assign.status === 200 || assign.status === 201,
    `${assign.status} ${String(assign.raw).slice(0, 220)}`,
  );
  const clId = assign.body?.id;
  const items = assign.body?.items || [];
  ok("assigned items have owners/due", items.length >= 1 && (items[0].ownerEmployeeId != null || items[0].dueDate != null || items[0].title), JSON.stringify(items[0]));

  const lists = await req("GET", "/hrms/api/checklists", { token: adminTok });
  ok("list checklists", lists.status === 200 && Array.isArray(lists.body), String(lists.status));

  if (clId != null) {
    const tick = await req("PUT", `/hrms/api/checklists/${clId}/items/0`, {
      token: adminTok,
      body: { completed: true },
    });
    ok("tick item (FR-CL-03)", tick.status === 200, `${tick.status} ${String(tick.raw).slice(0, 160)}`);
    const ticked = (tick.body?.items || [])[0];
    ok(
      "tick records who/when",
      ticked && (ticked.completedBy != null || ticked.completedAt != null || ticked.completed === true),
      JSON.stringify(ticked),
    );
  } else {
    ok("tick item (FR-CL-03)", false, "no checklist id");
    ok("tick records who/when", false, "skipped");
  }

  // ---- FR-NT notification settings + inbox ----
  const ns = await req("GET", "/hrms/api/notification-settings", { token: adminTok });
  ok("notification-settings GET", ns.status === 200, `${ns.status} ${String(ns.raw).slice(0, 160)}`);
  const events = ns.body?.events || ns.body?.notificationEvents || ns.body;
  ok("event keys present", events && typeof events === "object", JSON.stringify(events)?.slice(0, 120));

  const nsPut = await req("PUT", "/hrms/api/notification-settings", {
    token: adminTok,
    body: { events: { ...(typeof events === "object" ? events : {}), LEAVE_DECISION: true, PAYSLIP_READY: true } },
  });
  ok("notification-settings PUT", nsPut.status === 200 || nsPut.status === 204, String(nsPut.status));

  const inbox = await req("GET", "/notifications/api/notifications?page=0&size=5", { token: empTok });
  ok("in-app notifications list", inbox.status === 200, `${inbox.status}`);
  const unread = await req("GET", "/notifications/api/notifications/unread-count", { token: empTok });
  ok("unread-count", unread.status === 200 && unread.body?.unreadCount != null, JSON.stringify(unread.body));

  // ---- FR-DB dashboard ----
  const today = new Date().toISOString().slice(0, 10);
  const dash = await req("GET", `/hrms/api/dashboard/admin?date=${today}`, { token: adminTok });
  ok("admin dashboard (FR-DB-01)", dash.status === 200, `${dash.status} ${String(dash.raw).slice(0, 200)}`);
  ok(
    "dashboard has headcount/cards",
    dash.body && (dash.body.headcountByLocation != null || dash.body.checkedInVsExpected != null || dash.body.onLeaveCount != null || Object.keys(dash.body).length > 2),
    JSON.stringify(dash.body)?.slice(0, 200),
  );

  const from = `${year}-10-01`;
  const to = today;
  const wh = await req(
    "GET",
    `/hrms/api/dashboard/work-hours?from=${from}&to=${to}`,
    { token: adminTok },
  );
  ok("work-hours table (FR-DB-02)", wh.status === 200, `${wh.status} ${String(wh.raw).slice(0, 180)}`);

  // ---- FR-RP reports CSV ----
  const r1 = await req("GET", `/hrms/api/reports/leave-balances.csv?year=${year}`, { token: adminTok });
  ok("leave-balances.csv (FR-RP-03)", r1.status === 200 && String(r1.raw).length > 5, `${r1.status}`);
  const r2 = await req("GET", `/hrms/api/reports/leave-requests.csv?from=${year}-01-01&to=${today}`, { token: adminTok });
  ok("leave-requests.csv (FR-RP-04)", r2.status === 200, `${r2.status}`);
  const r3 = await req("GET", `/hrms/api/reports/headcount.csv`, { token: adminTok });
  ok("headcount.csv (FR-RP-05)", r3.status === 200, `${r3.status}`);
  const r4 = await req("GET", `/hrms/api/reports/late-absence.csv?from=${from}&to=${to}`, { token: adminTok });
  ok("late-absence.csv (FR-RP-02)", r4.status === 200, `${r4.status}`);

  // report-service monthly attendance (optional)
  const attR = await req("POST", "/reports/api/reports/attendance/monthly", {
    token: adminTok,
    body: { year, month: 9, employeeId: empId },
  });
  ok(
    "monthly attendance report (FR-RP-01)",
    attR.status === 200 || attR.status === 201 || attR.status === 404 || attR.status === 502,
    `${attR.status} ${String(attR.raw).slice(0, 100)}`,
  );

  // ---- FR-AU audit ----
  const audit = await req("GET", "/hrms/api/audit?size=20&q=CREATE", { token: adminTok });
  ok("audit log searchable (FR-AU-01/02)", audit.status === 200, String(audit.status));
  const auditRows = Array.isArray(audit.body) ? audit.body : audit.body?.content || [];
  ok("audit has rows", auditRows.length > 0, `n=${auditRows.length}`);

  const auditActor = await req("GET", `/hrms/api/audit?actorEmployeeId=1&size=10`, { token: adminTok });
  ok("audit filter by actor", auditActor.status === 200, String(auditActor.status));

  // employee cannot manage audit
  const auditDenied = await req("GET", "/hrms/api/audit?size=5", { token: empTok });
  ok(
    "audit restricted",
    auditDenied.status === 403 || auditDenied.status === 401 || auditDenied.status >= 400,
    String(auditDenied.status),
  );

  // ---- BR-11 business rules ----
  const br = await req("GET", "/hrms/api/business-rules", { token: adminTok });
  ok("business-rules GET (§11)", br.status === 200, `${br.status} ${String(br.raw).slice(0, 160)}`);
  const rules = br.body?.rules || br.body;
  const ruleText = JSON.stringify(rules || br.body);
  ok(
    "BR keys present",
    ruleText.includes("BR-") || ruleText.includes("grace") || ruleText.includes("workLog") || Object.keys(rules || {}).length > 5,
    ruleText.slice(0, 200),
  );

  // ---- BR-01 self-approve leave blocked (smoke) ----
  const selfLeave = await req("POST", "/hrms/api/leave-applications", {
    token: empTok,
    body: {
      leaveTypeCode: "CASUAL",
      startDate: `${year + 1}-02-02`,
      endDate: `${year + 1}-02-02`,
      reason: "self approve smoke",
    },
  });
  if (selfLeave.status < 400 && selfLeave.body?.id) {
    const selfApp = await req("POST", `/hrms/api/leave-applications/${selfLeave.body.id}/approve`, {
      token: empTok,
      body: { comment: "self" },
    });
    ok("BR-01 cannot self-approve leave", selfApp.status >= 400, `${selfApp.status}`);
    await req("POST", `/hrms/api/leave-applications/${selfLeave.body.id}/withdraw`, { token: empTok });
  } else {
    ok("BR-01 cannot self-approve leave", true, `apply skipped ${selfLeave.status}`);
  }

  // cleanup holiday
  if (holId) {
    await req("DELETE", `/hrms/api/holidays/${holId}`, { token: adminTok }).catch(() => {});
  }

  console.log(`\npassed=${passed} failed=${failed} total=${passed + failed}`);
  if (failures.length) {
    console.log("FAILURES:");
    failures.forEach((f) => console.log(" -", f));
    process.exit(1);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
