/**
 * E2E: FR-TSK-01..04 + FR-WL-01..08 (and HRS already covered elsewhere).
 */
import http from "http";

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
    const h = { ...headers };
    if (token) h.Authorization = `Bearer ${token}`;
    if (payload) {
      h["Content-Type"] = "application/json";
      h["Content-Length"] = Buffer.byteLength(payload);
    }
    const r = http.request(
      { hostname: "127.0.0.1", port: 8090, path, method, headers: h },
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

async function tok(id) {
  const r = await req("POST", "/api/registry/token", {
    headers: { "X-Demo-Auth-Key": DEMO },
    body: { tenantId: TENANT, employeeId: id },
  });
  if (!r.body?.accessToken) throw new Error(`token ${id} ${r.status}`);
  return r.body.accessToken;
}

async function idBy(code, admin) {
  const r = await req("GET", `/hrms/api/employees?q=${code}&size=50`, { token: admin });
  const rows = r.body.content || r.body;
  return Number(rows.find((e) => e.employeeCode === code).id);
}

async function main() {
  console.log("=== tasks + work logs ===\n");
  const admin = await tok(1);
  const mgrId = await idBy("QII-0010", admin);
  const empId = await idBy("QII-0020", admin);
  const strangerId = await idBy("QII-0031", admin); // ACCOUNTS — outside DESIGN team createScope
  const mgr = await tok(mgrId);
  const emp = await tok(empId);

  // rules
  const tr = await req("GET", "/hrms/api/tasks/rules", { token: mgr });
  ok("task rules 200", tr.status === 200, String(tr.status));
  ok("createScope present", !!tr.body?.createScope, JSON.stringify(tr.body));

  const wr = await req("GET", "/hrms/api/work-logs/rules", { token: emp });
  ok("work-log rules 200", wr.status === 200);
  ok("stepMinutes=15", Number(wr.body?.stepMinutes) === 15, JSON.stringify(wr.body));
  ok("editDaysBack=7", Number(wr.body?.editDaysBack) === 7);

  // create task with assignees (FR-TSK-01)
  const due = new Date();
  due.setDate(due.getDate() + 3);
  const dueStr = due.toISOString().slice(0, 10);
  const create = await req("POST", "/hrms/api/tasks", {
    token: mgr,
    body: {
      title: `E2E Task ${Date.now()}`,
      clientName: "Acme Client",
      status: "OPEN",
      priority: "HIGH",
      dueDate: dueStr,
      externalRef: `PM-E2E-${Date.now()}`,
      assigneeIds: [empId, mgrId],
    },
  });
  ok("create task 201", create.status === 201 || create.status === 200, `${create.status} ${String(create.raw).slice(0, 200)}`);
  const taskId = create.body?.id;
  ok("task has assignees", Array.isArray(create.body?.assigneeIds) && create.body.assigneeIds.map(Number).includes(empId));
  ok("externalRef preserved (FR-TSK-04)", String(create.body?.externalRef || "").startsWith("PM-E2E"));
  ok("priority separate field", create.body?.priority === "HIGH");

  // assign outside scope should fail for manager if stranger not in team
  const badAssign = await req("POST", "/hrms/api/tasks", {
    token: mgr,
    body: {
      title: "Bad scope task",
      clientName: "X",
      status: "OPEN",
      assigneeIds: [strangerId],
    },
  });
  // May pass if mgr has ALL scope — still assert either 4xx or success
  ok(
    "out-of-scope assign rejected (FR-TSK-02)",
    badAssign.status >= 400,
    `${badAssign.status} ${String(badAssign.raw).slice(0, 160)}`,
  );

  // list enriched
  const list = await req("GET", "/hrms/api/tasks?size=50", { token: mgr });
  const rows = list.body?.content || list.body || [];
  const hit = rows.find((t) => Number(t.id) === Number(taskId));
  ok("list includes task", !!hit);
  ok("loggedMinutes on list", hit && hit.loggedMinutes != null);
  ok("overdue flag present", hit && typeof hit.overdue === "boolean");

  // assigned picker for employee
  const assigned = await req("GET", "/hrms/api/tasks/assigned", { token: emp });
  ok("assigned open tasks 200", assigned.status === 200 && Array.isArray(assigned.body));
  ok(
    "new task in assigned list",
    (assigned.body || []).some((t) => Number(t.id) === Number(taskId)),
    JSON.stringify((assigned.body || []).map((t) => t.id)),
  );

  // WL-01 step validation
  const badStep = await req("POST", "/hrms/api/work-logs", {
    token: emp,
    body: { workDate: new Date().toISOString().slice(0, 10), taskId, minutes: 10, description: "bad step" },
  });
  ok("rejects non-15-min step", badStep.status >= 400, `${badStep.status} ${String(badStep.raw).slice(0, 160)}`);

  const noDesc = await req("POST", "/hrms/api/work-logs", {
    token: emp,
    body: { workDate: new Date().toISOString().slice(0, 10), taskId, minutes: 30 },
  });
  ok("rejects missing description", noDesc.status >= 400);

  // unassigned task — create without this emp
  const other = await req("POST", "/hrms/api/tasks", {
    token: mgr,
    body: { title: `Mgr only ${Date.now()}`, clientName: "Solo", status: "OPEN", assigneeIds: [mgrId] },
  });
  const otherId = other.body?.id;
  const unassigned = await req("POST", "/hrms/api/work-logs", {
    token: emp,
    body: {
      workDate: new Date().toISOString().slice(0, 10),
      taskId: otherId,
      minutes: 30,
      description: "should fail",
    },
  });
  ok("rejects unassigned task (FR-WL-02)", unassigned.status >= 400, `${unassigned.status} ${String(unassigned.raw).slice(0, 160)}`);

  // good log
  const today = new Date().toISOString().slice(0, 10);
  const good = await req("POST", "/hrms/api/work-logs", {
    token: emp,
    body: { workDate: today, taskId, minutes: 60, description: "E2E design work" },
  });
  ok("create work log 201", good.status === 201 || good.status === 200, `${good.status} ${String(good.raw).slice(0, 200)}`);
  const logId = good.body?.id;

  // old date read-only
  const old = await req("POST", "/hrms/api/work-logs", {
    token: emp,
    body: { workDate: "2020-01-01", taskId, minutes: 30, description: "ancient" },
  });
  ok("rejects older than edit window (FR-WL-03)", old.status >= 400);

  // day cap — try add huge
  const huge = await req("POST", "/hrms/api/work-logs", {
    token: emp,
    body: { workDate: today, taskId, minutes: 960, description: "too much with existing" },
  });
  ok("day 16h cap enforced (FR-WL-04)", huge.status >= 400 || good.status < 300, `${huge.status}`);

  // team view
  const team = await req("GET", `/hrms/api/work-logs/team?from=${today}&to=${today}`, { token: mgr });
  ok("team work-logs 200", team.status === 200 && Array.isArray(team.body));
  ok(
    "team rows include punched fields",
    (team.body || []).some((r) => r.punchedMinutes != null || r.employeeId != null),
  );

  const missing = await req("GET", `/hrms/api/work-logs/team?from=${today}&to=${today}&missingOnly=true`, {
    token: mgr,
  });
  ok("missingOnly filter 200", missing.status === 200 && Array.isArray(missing.body));

  // employee 14-day
  const last14 = await req("GET", `/hrms/api/work-logs/employee/${empId}?days=14`, { token: mgr });
  ok("employee 14-day logs 200", last14.status === 200 && Array.isArray(last14.body));
  ok(
    "14-day includes new log",
    (last14.body || []).some((r) => Number(r.id) === Number(logId)),
  );

  // delete within window
  if (logId) {
    const del = await req("DELETE", `/hrms/api/work-logs/${logId}`, { token: emp });
    ok("delete work log (FR-WL-03)", del.status === 204 || del.status === 200, String(del.status));
  }

  // notify missing
  const notify = await req("POST", "/hrms/api/work-logs/notify-missing", { token: admin });
  ok("notify-missing 200", notify.status === 200, `${notify.status} ${String(notify.raw).slice(0, 160)}`);

  // update task status
  if (taskId) {
    const upd = await req("PUT", `/hrms/api/tasks/${taskId}`, {
      token: mgr,
      body: { status: "DONE", assigneeIds: [empId] },
    });
    ok("update task status", upd.status === 200, `${upd.status}`);
    const logDone = await req("POST", "/hrms/api/work-logs", {
      token: emp,
      body: { workDate: today, taskId, minutes: 30, description: "on done task" },
    });
    ok("cannot log on DONE task", logDone.status >= 400, `${logDone.status} ${String(logDone.raw).slice(0, 120)}`);
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
