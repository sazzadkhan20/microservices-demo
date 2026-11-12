#!/usr/bin/env node
// Payroll finalise / reopen recovery against the live local stack (guide §6, docs/PAYROLL_SPEC.md §2.3, §5).
// DEVELOPMENT ONLY — it reopens October 2026, edits balances/attendance temporarily and reroutes RabbitMQ
// bindings through a trap queue to simulate lost and delayed messages. Every change is put back.
//
// Prereq: FORCE=1 bash scripts/seed-payroll-scenarios.sh && node scripts/test-payroll-e2e.mjs
// Run:    node scripts/test-payroll-recovery.mjs          (≈ 12 minutes: waits for real re-send / resolve timers)
//
// Scenarios: concurrency · Leave rejection · facts changed after review · lost command → automatic re-send ·
// manual resolve while the command is still in flight (fence) · manual resolve when only the result was lost ·
// reconciliation finds cover held under an orphan key.
import { readFileSync } from "node:fs";
import { execSync } from "node:child_process";

const G = process.env.GATEWAY_URL ?? "http://127.0.0.1:8090";
const MQ = process.env.RABBITMQ_API ?? "http://127.0.0.1:15672/api";
const env = Object.fromEntries(
  readFileSync(new URL("../.env", import.meta.url), "utf8")
    .split(/\r?\n/)
    .filter((l) => /^[A-Z0-9_]+=/.test(l))
    .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
);
const PROJECT = process.env.COMPOSE_PROJECT_NAME ?? "microservices-demo";
const ADMIN = 1;
const P = "2026-10";

let pass = 0;
let fail = 0;
const failures = [];
function check(name, cond, detail = "") {
  if (cond) {
    pass++;
    console.log(`  PASS ${name}`);
  } else {
    fail++;
    failures.push(`${name} ${detail}`);
    console.log(`  FAIL ${name} ${detail}`);
  }
}
const eq = (name, actual, expected) =>
  check(name, String(actual) === String(expected) || (actual !== null && actual !== "" && Number(actual) === Number(expected)),
    `expected ${expected}, got ${actual}`);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const tokens = {};
async function token(id) {
  if (tokens[id]) return tokens[id];
  const r = await fetch(`${G}/api/auth/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Demo-Auth-Key": env.DEMO_AUTH_KEY },
    body: JSON.stringify({ employeeId: id }),
  });
  const j = await r.json();
  tokens[id] = j.accessToken ?? j.token;
  return tokens[id];
}
async function call(method, path, body) {
  const r = await fetch(`${G}/payroll/api/payroll/${path}`, {
    method,
    headers: { Authorization: `Bearer ${await token(ADMIN)}`, ...(body ? { "Content-Type": "application/json" } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await r.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = text;
  }
  return { status: r.status, body: json };
}
const sql = (host, db, q) =>
  execSync(`docker exec ${PROJECT}-postgres-${host}-1 psql -U postgres -d ${db} -tAc "${q.replace(/"/g, '\\"')}"`).toString().trim();
const balance = (emp, code) =>
  Number(sql("hrms", "leave_db", `select balance_days from leave_balance where employee_id=${emp} and leave_type_code='${code}' and year=2026`));
const coverRows = (key) => Number(sql("hrms", "leave_db", `select count(*) from leave_balance_ledger where operation_key like 'COVER:${key}:%'`));
const run = async () => (await call("GET", `runs/${P}`)).body;
async function waitOp(id, ms = 20000) {
  const t = Date.now();
  for (;;) {
    const r = (await call("GET", `operations/${id}`)).body;
    if (r.status !== "PENDING" || Date.now() - t > ms) return r;
    await sleep(1000);
  }
}
async function reopen(reason) {
  const r = await run();
  if (r.status === "FINALIZED") {
    const o = await call("POST", `runs/${P}/reopen`, { confirm: true, reason });
    await waitOp(o.body.operationId);
  }
  if ((await run()).status === "ACTION_REQUIRED") await call("POST", `runs/${P}/return-to-draft`);
}
async function calculate() {
  return (await call("POST", `runs/${P}/calculate`)).body;
}
async function finalize(revision) {
  return call("POST", `runs/${P}/finalize`, { confirm: true, revision });
}

// ---- RabbitMQ: divert a routing key into a trap queue (message published and confirmed, never consumed)
const auth = { Authorization: "Basic " + Buffer.from(`${env.RABBITMQ_USER}:${env.RABBITMQ_PASSWORD}`).toString("base64"), "Content-Type": "application/json" };
const mq = (path, method = "GET", body) => fetch(`${MQ}${path}`, { method, headers: auth, body: body ? JSON.stringify(body) : undefined });
async function divert(routingKey, realQueue) {
  await mq("/queues/%2F/q.test.payroll-trap", "PUT", { durable: false });
  await mq("/bindings/%2F/e/platform.events/q/q.test.payroll-trap", "POST", { routing_key: routingKey });
  await mq(`/bindings/%2F/e/platform.events/q/${realQueue}/${encodeURIComponent(routingKey)}`, "DELETE");
}
async function restore(routingKey, realQueue) {
  await mq(`/bindings/%2F/e/platform.events/q/${realQueue}`, "POST", { routing_key: routingKey });
  await mq("/queues/%2F/q.test.payroll-trap", "DELETE");
}
async function takeTrapped() {
  const r = await mq("/queues/%2F/q.test.payroll-trap/get", "POST", { count: 50, ackmode: "ack_requeue_false", encoding: "auto" });
  return r.ok ? r.json() : [];
}
async function republish(routingKey, msgs) {
  for (const m of msgs) {
    await mq("/exchanges/%2F/platform.events/publish", "POST",
      { routing_key: routingKey, payload: m.payload, payload_encoding: "string", properties: m.properties });
  }
}
const CMD = ["payroll.leave-cover.command", "q.payroll.leave-cover.command"];
const RES = ["leave.cover.result", "q.leave.cover.result"];

const IMRAN = 7; // Casual 1.5 covered in October
const FARHANA = 12; // Annual 2 covered in October
const before = { imran: balance(IMRAN, "CASUAL"), farhana: balance(FARHANA, "ANNUAL") };

try {
  console.log("\n== Concurrency: double clicks and parallel requests");
  {
    const rs = await Promise.all([1, 2, 3].map(() => call("POST", `runs/${P}/reopen`, { confirm: true, reason: "parallel reopen" })));
    eq("3 parallel reopens → exactly one accepted", rs.filter((x) => x.status === 202).length, 1);
    await waitOp(rs.find((x) => x.status === 202).body.operationId);
    eq("leave returned once (Imran Casual)", balance(IMRAN, "CASUAL"), before.imran + 1.5);
    const c = await calculate();
    const fs = await Promise.all([1, 2, 3, 4, 5].map(() => finalize(c.revision)));
    check("5 parallel finalises → all 202", fs.every((x) => x.status === 202), JSON.stringify(fs.map((x) => x.status)));
    eq("…one operation id", new Set(fs.map((x) => x.body.operationId)).size, 1);
    const op = await waitOp(fs[0].body.operationId);
    eq("finalise SUCCEEDED", op.status, "SUCCEEDED");
    eq("leave taken once (Imran Casual)", balance(IMRAN, "CASUAL"), before.imran);
    eq("ledger rows for the key", coverRows(op.operationKey), 2);
    eq("reconciliation matches", (await call("GET", `runs/${P}/reconciliation`)).body.matches, true);
  }

  console.log("\n== Balance used up after review → finalise refused before anything is sent");
  {
    await reopen("balance changed test");
    const c = await calculate();
    sql("hrms", "leave_db", `update leave_balance set balance_days=0 where employee_id=${FARHANA} and leave_type_code='ANNUAL' and year=2026`);
    const f = await finalize(c.revision);
    eq("finalise → 409 FACTS_CHANGED", f.body.code, "FACTS_CHANGED");
    check("…names Farhana's lost cover", JSON.stringify(f.body.details?.changes ?? []).includes("Farhana Begum"), JSON.stringify(f.body.details));
    eq("run still DRAFT", (await run()).status, "DRAFT");
    sql("hrms", "leave_db", `update leave_balance set balance_days=${before.farhana + 2} where employee_id=${FARHANA} and leave_type_code='ANNUAL' and year=2026`);
  }

  console.log("\n== Leave rejects (balance used up between payroll's check and Leave) → nothing taken, recoverable");
  {
    const c = await calculate();
    await divert(...CMD); // hold the command so the balance can change after payroll's check
    const f = await finalize(c.revision);
    await sleep(3000);
    const held = await takeTrapped();
    await restore(...CMD);
    sql("hrms", "leave_db", `update leave_balance set balance_days=0 where employee_id=${FARHANA} and leave_type_code='ANNUAL' and year=2026`);
    await republish(CMD[0], held);
    const op = await waitOp(f.body.operationId);
    eq("operation REJECTED", op.status, "REJECTED");
    check("reason names the employee", String(op.error).includes(`Employee ${FARHANA}: ANNUAL has 0`), op.error);
    eq("run ACTION_REQUIRED", (await run()).status, "ACTION_REQUIRED");
    eq("no cover rows written", coverRows(op.operationKey), 0);
    eq("Imran's Casual untouched (all or nothing)", balance(IMRAN, "CASUAL"), before.imran + 1.5);
    await call("POST", `runs/${P}/return-to-draft`);
    const again = await finalize((await run()).revision);
    eq("same draft cannot be finalised again → 422", again.status, 422);
    check("…blocker says recalculate", JSON.stringify(again.body).includes("Recalculate — Leave rejected"), JSON.stringify(again.body));
    sql("hrms", "leave_db", `update leave_balance set balance_days=${before.farhana + 2} where employee_id=${FARHANA} and leave_type_code='ANNUAL' and year=2026`);
    const c2 = await calculate();
    const ok = await waitOp((await finalize(c2.revision)).body.operationId);
    eq("after restoring the balance: SUCCEEDED", ok.status, "SUCCEEDED");
    eq("Farhana Annual taken once", balance(FARHANA, "ANNUAL"), before.farhana);
  }

  console.log("\n== Facts changed after review → finalise refused until recalculated (FR-PAY-11/12)");
  {
    await reopen("facts test");
    const c = await calculate();
    const arpa = Number(sql("hrms", "people_db", "select id from employee where employee_code='QII-0050'"));
    const day = sql("hrms", "timework_db", `select work_date from attendance_record where employee_id=${arpa} and work_date between '2026-10-01' and '2026-10-31' and status_code='P' order by work_date limit 1`);
    const out = sql("hrms", "timework_db", `select check_out_at from attendance_record where employee_id=${arpa} and work_date='${day}'`);
    // An attendance correction turns a full day into a 3-hour half day after the draft was reviewed.
    sql("hrms", "timework_db", `update attendance_record set check_out_at = check_in_at + interval '3 hours', status_code='HD', status_label='Half Day' where employee_id=${arpa} and work_date='${day}'`);
    const f = await finalize(c.revision);
    eq("finalise → 409", f.status, 409);
    eq("…code FACTS_CHANGED", f.body.code, "FACTS_CHANGED");
    check("…names Arpa and what changed", JSON.stringify(f.body.details?.changes ?? []).includes("Arpa Sen (QII-0050)"), JSON.stringify(f.body.details));
    eq("run still DRAFT", (await run()).status, "DRAFT");
    sql("hrms", "timework_db", `update attendance_record set check_out_at='${out}', status_code='P', status_label='Present' where employee_id=${arpa} and work_date='${day}'`);
    const ok = await waitOp((await finalize(c.revision)).body.operationId);
    eq("facts back to what was reviewed → SUCCEEDED", ok.status, "SUCCEEDED");
  }

  console.log("\n== Lost command (broker accepted it, Leave never saw it) → automatic re-send");
  {
    await reopen("lost command test");
    const c = await calculate();
    await divert(...CMD);
    const t0 = Date.now();
    const f = await finalize(c.revision);
    await sleep(4000);
    const lost = await takeTrapped();
    await restore(...CMD);
    eq("command was lost", lost.length, 1);
    eq("operation PENDING", (await call("GET", `operations/${f.body.operationId}`)).body.status, "PENDING");
    const op = await waitOp(f.body.operationId, 300000);
    eq("re-sent automatically → SUCCEEDED", op.status, "SUCCEEDED");
    check("…on attempt 2 after the first back-off", op.attempts === 2, `attempts=${op.attempts} after ${Math.round((Date.now() - t0) / 1000)}s`);
    eq("leave taken once", balance(IMRAN, "CASUAL"), before.imran);
  }

  console.log("\n== Manual resolve while the command is still in flight → Leave fenced, no late deduction");
  {
    await reopen("fence test");
    const c = await calculate();
    const mid = balance(IMRAN, "CASUAL");
    await divert(...CMD);
    const f = await finalize(c.revision);
    const early = await call("POST", `operations/${f.body.operationId}/resolve`, { reason: "too early" });
    eq("resolve right away → 409 TOO_EARLY", early.body.code, "TOO_EARLY");
    const at = new Date(f.body.resolvableAt).getTime();
    console.log(`  … waiting ${Math.round((at - Date.now()) / 1000)}s until the operation may be resolved`);
    await sleep(Math.max(0, at - Date.now()) + 2000);
    const res = await call("POST", `operations/${f.body.operationId}/resolve`, { reason: "operator: Leave seems down", outcome: "SUCCEEDED" });
    eq("resolve → 200", res.status, 200);
    eq("Leave had not processed it → REJECTED", res.body.status, "REJECTED");
    eq("…Leave status CANCELLED (fence)", res.body.leaveStatus, "CANCELLED");
    check("…operator's wrong guess is reported, not applied", String(res.body.note).includes("Leave's outcome was applied"), res.body.note);
    eq("run ACTION_REQUIRED", (await run()).status, "ACTION_REQUIRED");
    const delayed = await takeTrapped();
    await restore(...CMD);
    check("command(s) still in flight", delayed.length >= 1, `trapped=${delayed.length}`);
    await republish(CMD[0], delayed); // the delayed command finally reaches Leave
    await sleep(6000);
    eq("delayed command took nothing (fence)", coverRows(f.body.operationKey), 0);
    eq("Imran Casual unchanged", balance(IMRAN, "CASUAL"), mid);
    eq("operation stays REJECTED", (await call("GET", `operations/${f.body.operationId}`)).body.status, "REJECTED");
    await call("POST", `runs/${P}/return-to-draft`);
    const c2 = await calculate();
    const ok = await waitOp((await finalize(c2.revision)).body.operationId);
    eq("re-finalise SUCCEEDED", ok.status, "SUCCEEDED");
    eq("leave taken exactly once", balance(IMRAN, "CASUAL"), before.imran);
    eq("reconciliation matches (no orphan)", (await call("GET", `runs/${P}/reconciliation`)).body.matches, true);
  }

  console.log("\n== Manual resolve when Leave applied it but every result was lost → completed, not repeated");
  {
    await reopen("lost result test");
    const c = await calculate();
    await divert(...RES);
    const f = await finalize(c.revision);
    await sleep(5000);
    eq("Leave applied it (rows written)", coverRows(f.body.operationKey), 2);
    eq("payroll still PENDING", (await call("GET", `operations/${f.body.operationId}`)).body.status, "PENDING");
    const at = new Date(f.body.resolvableAt).getTime();
    console.log(`  … waiting ${Math.round((at - Date.now()) / 1000)}s until the operation may be resolved`);
    await sleep(Math.max(0, at - Date.now()) + 2000);
    const res = await call("POST", `operations/${f.body.operationId}/resolve`, { reason: "results lost" });
    eq("resolve → SUCCEEDED (Leave's outcome)", res.body.status, "SUCCEEDED");
    eq("run FINALIZED", (await run()).status, "FINALIZED");
    const lostResults = await takeTrapped();
    await restore(...RES);
    await republish(RES[0], lostResults); // late duplicates must change nothing
    await sleep(4000);
    eq("late results ignored, still FINALIZED", (await run()).status, "FINALIZED");
    eq("leave taken exactly once", balance(IMRAN, "CASUAL"), before.imran);
    eq("reconciliation matches", (await call("GET", `runs/${P}/reconciliation`)).body.matches, true);
  }

  console.log("\n== Reconciliation finds cover held under a key that is not the month's finalise");
  {
    sql("hrms", "leave_db", "insert into leave_balance_ledger (employee_id, leave_type_code, year, delta_days, reason, actor_employee_id, created_at, operation_key, source_type, source_id) "
      + `values (${IMRAN}, 'CASUAL', 2026, -1, 'recovery test orphan', 1, now(), 'COVER:FINALIZE:${P}:r1:${IMRAN}:CASUAL', 'COVER', '${P}')`);
    const r = (await call("GET", `runs/${P}/reconciliation`)).body;
    eq("mismatch reported", r.matches, false);
    check("orphan key listed", JSON.stringify(r.orphanKeys ?? {}).includes(`FINALIZE:${P}:r1`), JSON.stringify(r.orphanKeys));
    sql("hrms", "leave_db", `delete from leave_balance_ledger where operation_key='COVER:FINALIZE:${P}:r1:${IMRAN}:CASUAL'`);
    eq("after removing it: matches", (await call("GET", `runs/${P}/reconciliation`)).body.matches, true);
  }
} finally {
  // Never leave the broker rerouted.
  await restore(...CMD).catch(() => {});
  await restore(...RES).catch(() => {});
}

eq("final state: October FINALIZED", (await run()).status, "FINALIZED");
eq("final balances as seeded (Imran Casual)", balance(IMRAN, "CASUAL"), before.imran);
eq("final balances as seeded (Farhana Annual)", balance(FARHANA, "ANNUAL"), before.farhana);

console.log(`\n${pass} passed, ${fail} failed`);
if (fail) {
  console.log(failures.map((f) => ` - ${f}`).join("\n"));
  process.exit(1);
}
