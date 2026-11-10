#!/usr/bin/env node
/**
 * Live checks: reporting-loop refuse + eligible-managers hide + due/offboarding jobs.
 * Prefers People :8081 (service key). Also exercises gateway JWT when available.
 */
import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");
for (const line of fs.readFileSync(path.join(ROOT, ".env"), "utf8").split(/\r?\n/)) {
  const t = line.trim();
  if (!t || t.startsWith("#") || !t.includes("=")) continue;
  const i = t.indexOf("=");
  const k = t.slice(0, i).trim();
  let v = t.slice(i + 1).trim();
  if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
  if (process.env[k] === undefined) process.env[k] = v;
}

const GW = process.env.GATEWAY_URL || "http://127.0.0.1:8090";
const PEOPLE = process.env.PEOPLE_URL || "http://127.0.0.1:8081";
const DEMO = process.env.DEMO_AUTH_KEY || "local-demo-auth-key-change-me";
const SVC = process.env.PLATFORM_SERVICE_KEY || "local-service-key-change-me";
const INT = process.env.PLATFORM_INTERNAL_KEY || "local-internal-key-change-me";

function req(method, url, { headers = {}, body } = {}) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const data = body == null ? null : JSON.stringify(body);
    const h = { ...headers };
    if (data != null) {
      h["Content-Type"] = "application/json";
      h["Content-Length"] = Buffer.byteLength(data);
    }
    const r = http.request(
      { hostname: u.hostname, port: u.port, path: u.pathname + u.search, method, headers: h, timeout: 30000 },
      (res) => {
        const chunks = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () => {
          const raw = Buffer.concat(chunks).toString("utf8");
          let json = null;
          try {
            json = JSON.parse(raw);
          } catch {
            /* */
          }
          resolve({ status: res.statusCode || 0, json, raw });
        });
      },
    );
    r.on("error", reject);
    if (data) r.write(data);
    r.end();
  });
}

const fails = [];
function ok(name, cond, detail) {
  if (cond) console.log(`PASS  ${name}`);
  else {
    console.error(`FAIL  ${name} — ${detail}`);
    fails.push(name);
  }
}

/** Calendar date in Asia/Dhaka (same zone as people.offboarding.handover-zone / reporting-line due job). */
function dhakaDate(offsetDays = 0) {
  const base = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Dhaka",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date()); // YYYY-MM-DD
  const [y, m, d] = base.split("-").map(Number);
  const utc = Date.UTC(y, m - 1, d + offsetDays);
  return new Date(utc).toISOString().slice(0, 10);
}

const peopleHeaders = {
  "X-Service-Key": SVC,
  "X-Employee-ID": "1",
};

const list = await req("GET", `${PEOPLE}/api/employees?size=200`, { headers: peopleHeaders });
ok("list employees", list.status === 200, `status=${list.status}`);
const employees = list.json?.content ?? list.json?.items ?? (Array.isArray(list.json) ? list.json : []);
const byId = new Map(employees.map((e) => [Number(e.id), e]));

let manager = null;
let report = null;
for (const e of employees) {
  if (e.managerId == null) continue;
  const m = byId.get(Number(e.managerId));
  if (m) {
    report = e;
    manager = m;
    break;
  }
}
ok("found manager→report pair in seed", !!(manager && report), "no reporting pair");

function putBody(emp, patch) {
  return {
    fullName: emp.fullName,
    email: emp.email,
    preferredName: emp.preferredName ?? null,
    jobTitle: emp.jobTitle ?? null,
    departmentId: emp.departmentId ?? null,
    designationId: emp.designationId ?? null,
    managerId: emp.managerId == null ? 0 : Number(emp.managerId),
    teamId: emp.teamId ?? null,
    locationId: emp.locationId ?? null,
    employmentType: emp.employmentType ?? null,
    startDate: emp.startDate ?? null,
    status: emp.status ?? "Active",
    phone: emp.phone ?? null,
    ...patch,
  };
}

if (manager && report) {
  console.log(`Using pair manager=#${manager.id} ${manager.fullName} → report=#${report.id} ${report.fullName}`);

  const loopPut = await req("PUT", `${PEOPLE}/api/employees/${manager.id}`, {
    headers: peopleHeaders,
    body: putBody(manager, {
      managerId: Number(report.id),
      managerEffectiveFrom: new Date().toISOString().slice(0, 10),
    }),
  });
  const loopMsg = String(loopPut.json?.message || loopPut.json?.detail || loopPut.json?.error || loopPut.raw || "");
  ok(
    "BE refuses manager→own-report loop",
    loopPut.status >= 400 && /loop|report/i.test(loopMsg),
    `status=${loopPut.status} body=${loopMsg.slice(0, 240)}`,
  );

  const eligible = await req("GET", `${PEOPLE}/api/employees/${manager.id}/eligible-managers`, {
    headers: peopleHeaders,
  });
  ok("eligible-managers 200", eligible.status === 200, `status=${eligible.status}`);
  const ids = (Array.isArray(eligible.json) ? eligible.json : []).map((r) => Number(r.id));
  ok(
    "eligible-managers hides direct report (loop)",
    !ids.includes(Number(report.id)),
    `report ${report.id} still listed among ${ids.length} candidates`,
  );
  ok("eligible-managers hides self", !ids.includes(Number(manager.id)), `self ${manager.id} listed`);

  const hand = await req("GET", `${PEOPLE}/api/employees/${manager.id}/offboarding-handover`, {
    headers: peopleHeaders,
  });
  ok("offboarding-handover 200", hand.status === 200, `status=${hand.status}`);
  if (hand.status === 200 && hand.json?.requiresReassignment) {
    const takeIds = (hand.json.eligibleHandoverTo ?? []).map((r) => Number(r.id));
    ok(
      "handover select never includes the leaving manager",
      !takeIds.includes(Number(manager.id)),
      "self in handover list",
    );
    for (const r of hand.json.directReports ?? []) {
      ok(
        `handover hides direct report ${r.id} as takeover`,
        !takeIds.includes(Number(r.id)),
        "report still eligible",
      );
    }
  } else {
    console.log("SKIP  handover loop checks (no reassignment required for this manager)");
  }

  // --- Effective-dated history + due job ---
  // Pick a safe new manager for the report (not self, not creating loop).
  const safeMgr = ids.find((id) => id !== Number(report.id) && id !== Number(manager.id)) ?? 1;
  const today = dhakaDate(0);
  // Schedule a FUTURE change first (tomorrow Dhaka) — pointer must stay on current manager
  const tomorrow = dhakaDate(1);
  const schedule = await req("PUT", `${PEOPLE}/api/employees/${report.id}`, {
    headers: peopleHeaders,
    body: putBody(report, { managerId: Number(safeMgr), managerEffectiveFrom: tomorrow }),
  });
  ok("schedule future manager change", schedule.status === 200, `status=${schedule.status} ${schedule.raw?.slice?.(0, 160)}`);
  const afterSched = await req("GET", `${PEOPLE}/api/employees/${report.id}`, { headers: peopleHeaders });
  ok(
    "future change keeps current manager_id until effective date",
    Number(afterSched.json?.managerId) === Number(manager.id),
    `managerId=${afterSched.json?.managerId} expected still ${manager.id}`,
  );

  // Force history due today while pointer still old → job must flip
  const sql = await req("POST", `${PEOPLE}/api/internal/jobs/reporting-line-due`, {
    headers: { "X-Service-Key": SVC, "X-Internal-Key": INT },
  });
  // Bring the scheduled line forward via a same-day re-plan is immediate; instead simulate
  // due by writing effective date = today with a PUT that keeps pointer if we use SQL.
  // Practical path: PUT with today (immediate) then restore via job-path using tomorrow→SQL.
  const makeDue = await req("PUT", `${PEOPLE}/api/employees/${report.id}`, {
    headers: peopleHeaders,
    body: putBody(report, { managerId: Number(safeMgr), managerEffectiveFrom: today }),
  });
  ok("apply same-day manager change", makeDue.status === 200, `status=${makeDue.status} ${makeDue.raw?.slice?.(0, 160)}`);
  const afterDue = await req("GET", `${PEOPLE}/api/employees/${report.id}`, { headers: peopleHeaders });
  ok(
    "same-day change flips manager_id immediately",
    Number(afterDue.json?.managerId) === Number(safeMgr),
    `managerId=${afterDue.json?.managerId} expected ${safeMgr}`,
  );

  // Schedule another future change, then pull from_date to today in DB and run due job
  const schedule2 = await req("PUT", `${PEOPLE}/api/employees/${report.id}`, {
    headers: peopleHeaders,
    body: putBody({ ...report, managerId: Number(safeMgr) }, {
      managerId: Number(manager.id),
      managerEffectiveFrom: tomorrow,
    }),
  });
  ok("schedule second future change for job test", schedule2.status === 200, `status=${schedule2.status}`);
  const mid = await req("GET", `${PEOPLE}/api/employees/${report.id}`, { headers: peopleHeaders });
  ok(
    "second future change keeps pointer on interim manager",
    Number(mid.json?.managerId) === Number(safeMgr),
    `managerId=${mid.json?.managerId}`,
  );

  // Advance history from_date to today via docker postgres when available
  let dbOk = false;
  try {
    const { spawnSync } = await import("node:child_process");
    const containers = ["microservices-demo-postgres-hrms-1", "postgres-hrms", "postgres"];
    let lastErr = "";
    for (const c of containers) {
      // Pull future open line to today AND close the line that ended "today" back to yesterday
      // so ranges do not overlap (mirrors calendar day rolling forward).
      const sql = `
UPDATE reporting_line_history
   SET to_date = (DATE '${today}' - INTERVAL '1 day')
 WHERE employee_id = ${report.id}
   AND to_date = DATE '${today}';
UPDATE reporting_line_history
   SET from_date = DATE '${today}'
 WHERE employee_id = ${report.id}
   AND to_date IS NULL
   AND from_date > DATE '${today}';`;
      const upd = spawnSync(
        "docker",
        ["exec", "-i", c, "psql", "-U", "people_app", "-d", "people_db", "-c", sql],
        { cwd: ROOT, encoding: "utf8" },
      );
      if (upd.status === 0) {
        dbOk = true;
        break;
      }
      lastErr = (upd.stderr || upd.stdout || "").trim();
      const upd2 = spawnSync(
        "docker",
        ["exec", "-i", c, "psql", "-U", "postgres", "-d", "people_db", "-c", sql],
        { cwd: ROOT, encoding: "utf8" },
      );
      if (upd2.status === 0) {
        dbOk = true;
        break;
      }
      lastErr = (upd2.stderr || upd2.stdout || lastErr).trim();
    }
    if (!dbOk) console.log("SKIP  db advance:", lastErr.slice(0, 200));
  } catch (e) {
    console.log("SKIP  db advance:", e.message);
  }
  if (dbOk) {
    const beforeJob = await req("GET", `${PEOPLE}/api/employees/${report.id}`, { headers: peopleHeaders });
    ok(
      "before due-job pointer still old",
      Number(beforeJob.json?.managerId) === Number(safeMgr),
      `managerId=${beforeJob.json?.managerId}`,
    );
    const job = await req("POST", `${PEOPLE}/api/internal/jobs/reporting-line-due`, {
      headers: { "X-Service-Key": SVC, "X-Internal-Key": INT },
    });
    ok("due-job applied at least one", job.status === 200 && Number(job.json?.applied) >= 1, JSON.stringify(job.json));
    const afterJob = await req("GET", `${PEOPLE}/api/employees/${report.id}`, { headers: peopleHeaders });
    ok(
      "due-job flipped manager_id from history",
      Number(afterJob.json?.managerId) === Number(manager.id),
      `managerId=${afterJob.json?.managerId} expected ${manager.id}`,
    );
  }

  // Restore original manager for seed cleanliness
  await req("PUT", `${PEOPLE}/api/employees/${report.id}`, {
    headers: peopleHeaders,
    body: putBody({ ...report, managerId: Number(safeMgr) }, {
      managerId: Number(manager.id),
      managerEffectiveFrom: today,
    }),
  });
  void sql;
}

// Optional gateway JWT smoke
const tok = await req("POST", `${GW}/api/auth/token`, {
  headers: { "X-Demo-Auth-Key": DEMO, "Content-Type": "application/json" },
  body: { employeeId: 1 },
});
if (tok.status === 200 && tok.json?.accessToken) {
  const gwList = await req("GET", `${GW}/hrms/api/employees?size=5`, {
    headers: { Authorization: `Bearer ${tok.json.accessToken}` },
  });
  ok("gateway JWT can list employees", gwList.status === 200, `status=${gwList.status}`);
} else {
  console.log("SKIP  gateway JWT smoke");
}

const dueJob = await req("POST", `${PEOPLE}/api/internal/jobs/reporting-line-due`, {
  headers: { "X-Service-Key": SVC, "X-Internal-Key": INT },
});
ok("reporting-line-due job runs", dueJob.status === 200, `status=${dueJob.status}`);
ok("reporting-line-due payload", dueJob.json?.job === "reporting-line-due", JSON.stringify(dueJob.json));

const offJob = await req("POST", `${PEOPLE}/api/internal/jobs/offboarding-handover`, {
  headers: { "X-Service-Key": SVC, "X-Internal-Key": INT },
});
ok("offboarding-handover job runs", offJob.status === 200, `status=${offJob.status}`);

if (fails.length) {
  console.error(`\n${fails.length} failure(s)`);
  process.exit(1);
}
console.log("\nAll reporting-line loop / effective-date / job checks passed.");
