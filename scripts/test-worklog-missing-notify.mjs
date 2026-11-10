#!/usr/bin/env node
/**
 * FR-WL-06 — checkout with no work log must notify employee + manager (once/day).
 */
const GW = process.env.GATEWAY || "http://127.0.0.1:8090";
const DEMO = process.env.DEMO_AUTH_KEY || "local-demo-auth-key-change-me";

const results = [];
function check(id, pass, detail) {
  results.push({ id, pass });
  console.log(`${pass ? "PASS" : "FAIL"}  ${id} — ${detail}`);
}

async function req(method, url, { token, body, headers = {} } = {}) {
  const r = await fetch(url, {
    method,
    headers: {
      ...(body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await r.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    /* */
  }
  return { status: r.status, text, json };
}

async function token(employeeId) {
  const r = await req("POST", `${GW}/api/auth/token`, {
    headers: { "X-Demo-Auth-Key": DEMO },
    body: { employeeId },
  });
  return r.json?.accessToken || r.json?.token;
}

function msg(r) {
  return r.json?.message || r.json?.detail || r.json?.error || r.text?.slice(0, 200) || `HTTP ${r.status}`;
}

async function main() {
  const accounts = await req("GET", `${GW}/hrms/api/auth/accounts`, {
    headers: { "X-Demo-Auth-Key": DEMO },
  });
  const admin = accounts.json.find((a) => String(a.role).includes("SUPERADMIN"));
  const emp = accounts.json.find((a) => String(a.name).includes("With Manager")) || accounts.json.find((a) => a.role === "EMPLOYEE");
  const empId = Number(emp.employeeId);
  const mgrId = Number(admin.employeeId); // With Manager Test → manager Asman (#1)
  const tokA = await token(mgrId);
  const tokE = await token(empId);
  check("SETUP", !!tokA && !!tokE, `emp=#${empId} mgr=#${mgrId}`);

  const original = await req("GET", `${GW}/hrms/api/work-logs/rules`, { token: tokA });

  // FLAG so checkout without log is allowed.
  await req("PUT", `${GW}/hrms/api/work-logs/rules`, {
    token: tokA,
    body: { workLogRule: "FLAG", workLogMinPercent: 0 },
  });

  const today = await req("GET", `${GW}/hrms/api/attendance/today`, { token: tokE });
  const workDate = today.json?.workDate || today.json?.date || new Date().toISOString().slice(0, 10);
  const twoHoursAgo = new Date(Date.now() - 2 * 3600 * 1000).toISOString();

  // Unique dedupe suffix via correct+checkout on today's date — use notify-missing job keys based on workDate.
  // Clear prior notifications for this test key by using a fresh date isn't possible; rely on delete via API if any.
  // Reopen punch and wipe today's logs.
  await req("POST", `${GW}/hrms/api/attendance/correct`, {
    token: tokA,
    body: {
      employeeId: empId,
      workDate,
      checkInAt: twoHoursAgo,
      clearCheckOut: true,
      reason: "QA missing-worklog notify reopen",
    },
  });

  const logs = await req("GET", `${GW}/hrms/api/work-logs?page=1&size=100`, { token: tokE });
  for (const row of logs.json?.content || []) {
    if (String(row.workDate) === String(workDate)) {
      await req("DELETE", `${GW}/hrms/api/work-logs/${row.id}`, { token: tokE });
    }
  }

  // Snapshot unread before checkout
  const beforeE = await req("GET", `${GW}/hrms/api/../notifications/api/notifications?page=0&size=20`, { token: tokE }).catch(() => ({ status: 0 }));
  // Notifications go through /notifications gateway prefix
  const listE0 = await req("GET", `${GW}/notifications/api/notifications?page=0&size=50`, { token: tokE });
  const listM0 = await req("GET", `${GW}/notifications/api/notifications?page=0&size=50`, { token: tokA });
  const countKey = (list, key) => {
    const rows = list.json?.content || list.json || [];
    return (Array.isArray(rows) ? rows : []).filter((n) => String(n.dedupeKey || "") === key).length;
  };
  const empKey = `worklog_missing-${empId}-${workDate}`;
  const mgrKey = `worklog_missing_mgr-${empId}-${workDate}`;

  const out = await req("POST", `${GW}/hrms/api/attendance/check-out`, { token: tokE, body: {} });
  check("CHECKOUT-NO-LOG", out.status >= 200 && out.status < 300, `status=${out.status} ${msg(out)}`);

  // Small wait for sync create
  await new Promise((r) => setTimeout(r, 500));

  const listE1 = await req("GET", `${GW}/notifications/api/notifications?page=0&size=50`, { token: tokE });
  const listM1 = await req("GET", `${GW}/notifications/api/notifications?page=0&size=50`, { token: tokA });
  const empHits = countKey(listE1, empKey);
  const mgrHits = countKey(listM1, mgrKey);
  check("EMP-NOTIFIED", empHits >= 1, `dedupe=${empKey} hits=${empHits} status=${listE1.status}`);
  check("MGR-NOTIFIED", mgrHits >= 1, `dedupe=${mgrKey} hits=${mgrHits} status=${listM1.status}`);

  // Once per day — second checkout attempt shouldn't create duplicates; reopen + checkout again.
  await req("POST", `${GW}/hrms/api/attendance/correct`, {
    token: tokA,
    body: {
      employeeId: empId,
      workDate,
      checkInAt: twoHoursAgo,
      clearCheckOut: true,
      reason: "QA dedupe reopen",
    },
  });
  await req("POST", `${GW}/hrms/api/attendance/check-out`, { token: tokE, body: {} });
  await new Promise((r) => setTimeout(r, 400));
  const listE2 = await req("GET", `${GW}/notifications/api/notifications?page=0&size=50`, { token: tokE });
  const listM2 = await req("GET", `${GW}/notifications/api/notifications?page=0&size=50`, { token: tokA });
  check("EMP-DEDUPE", countKey(listE2, empKey) === empHits, `hits stayed ${countKey(listE2, empKey)}`);
  check("MGR-DEDUPE", countKey(listM2, mgrKey) === mgrHits, `hits stayed ${countKey(listM2, mgrKey)}`);

  // Daily job path still works
  const job = await req("POST", `${GW}/hrms/api/work-logs/notify-missing`, { token: tokA, body: {} });
  check("JOB-TRIGGER", job.status >= 200 && job.status < 300, `status=${job.status} body=${JSON.stringify(job.json)}`);

  // Restore rules
  await req("PUT", `${GW}/hrms/api/work-logs/rules`, {
    token: tokA,
    body: {
      workLogRule: "BLOCK",
      workLogMinPercent: 80,
      workLogPercentOf: original.json?.workLogPercentOf || "WORKED",
    },
  });

  const failed = results.filter((r) => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
