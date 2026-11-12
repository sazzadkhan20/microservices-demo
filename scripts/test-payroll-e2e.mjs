#!/usr/bin/env node
// Payroll end-to-end acceptance (SRS AT-14 / AT-15, guide PAY01–PAY03) against the local stack.
// Prereq: bash scripts/seed-payroll-scenarios.sh   (FORCE=1 to rerun)
// Flow: Sep calculate → finalise → reopen → re-finalise; Oct calculate + adjustments → finalise; Nov live draft.
// Leaves the database in that final state so the UI shows finalised Sep/Oct and a draft Nov.
import { readFileSync } from "node:fs";
import { execSync } from "node:child_process";

const G = process.env.GATEWAY_URL ?? "http://127.0.0.1:8090";
const env = Object.fromEntries(
  readFileSync(new URL("../.env", import.meta.url), "utf8")
    .split(/\r?\n/)
    .filter((l) => /^[A-Z0-9_]+=/.test(l))
    .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
);
const PROJECT = process.env.COMPOSE_PROJECT_NAME ?? "microservices-demo";

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
const same = (a, b) =>
  a !== null && a !== undefined && a !== "" && !Number.isNaN(Number(a)) && !Number.isNaN(Number(b))
    ? Number(a) === Number(b)
    : String(a) === String(b);
const eq = (name, actual, expected) => check(name, same(actual, expected), `expected ${expected}, got ${actual}`);

const tokens = {};
async function token(employeeId) {
  if (tokens[employeeId]) return tokens[employeeId];
  const r = await fetch(`${G}/api/auth/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Demo-Auth-Key": env.DEMO_AUTH_KEY },
    body: JSON.stringify({ employeeId }),
  });
  const j = await r.json();
  if (!r.ok) throw new Error(`token ${employeeId}: ${JSON.stringify(j)}`);
  tokens[employeeId] = j.accessToken ?? j.token;
  return tokens[employeeId];
}
async function call(who, method, path, body) {
  const r = await fetch(`${G}/payroll/api/payroll/${path}`, {
    method,
    headers: { Authorization: `Bearer ${await token(who)}`, ...(body ? { "Content-Type": "application/json" } : {}) },
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
const ADMIN = 1; // Asman — Super Admin
const HR = 2; // Nadia — HR (no salary access by default)
const sql = (db, q) =>
  execSync(`docker exec ${PROJECT}-postgres-hrms-1 psql -U postgres -d ${db} -tAc "${q}"`).toString().trim();
const empId = (code) => Number(sql("people_db", `select id from employee where employee_code='${code}'`));
const balance = (emp, code) =>
  Number(sql("leave_db", `select balance_days from leave_balance where employee_id=${emp} and leave_type_code='${code}' and year=2026`));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function lines(period) {
  const r = await call(ADMIN, "GET", `runs/${period}/lines?size=200`);
  const byName = {};
  for (const l of r.body.content ?? []) byName[l.employeeName] = l;
  return byName;
}
async function waitOperation(op) {
  if (op.status !== "PENDING") return op;
  for (let i = 0; i < 40; i++) {
    await sleep(500);
    const r = await call(ADMIN, "GET", `operations/${op.operationId}`);
    if (r.body.status !== "PENDING") return r.body;
  }
  return { status: "TIMEOUT" };
}
async function finalize(period) {
  const run = (await call(ADMIN, "GET", `runs/${period}`)).body;
  const r = await call(ADMIN, "POST", `runs/${period}/finalize`, { confirm: true, revision: run.revision });
  return { http: r.status, op: r.status < 300 ? await waitOperation(r.body) : r.body, run };
}

const ARPA = empId("QII-0050");
const TANVIR = empId("QII-0051");
const RUMANA = empId("QII-0052");

console.log("\n== Rules: SRS defaults (no extra deductions)");
{
  const s = await call(ADMIN, "GET", "settings");
  eq("per-day basis = working days", s.body.perDayRateBasis, "WORKING_DAYS");
  eq("absence rule = balance first", s.body.absenceRule, "BALANCE_FIRST");
  eq("cover order = Casual, Annual", (s.body.absenceCoverOrder ?? []).join(","), "CASUAL,ANNUAL");
  const bad = await call(ADMIN, "PUT", "settings", { absenceRule: "LATE_PENALTY" });
  eq("invalid rule rejected (422)", bad.status, 422);
}

console.log("\n== September 2026: calculate (AT-14)");
{
  const c = await call(ADMIN, "POST", "runs/2026-09/calculate");
  eq("calculate 200", c.status, 200);
  eq("status DRAFT", c.body.status, "DRAFT");
  const L = await lines("2026-09");
  const a = L["Arpa Sen"];
  eq("Arpa working days 22", a.workingDays, "22.00");
  eq("Arpa per-day rate 681.82", a.perDayRate, "681.82");
  eq("Arpa absent 2.5", a.absentDays, "2.50");
  eq("Arpa covered by Casual 1", a.coveredDays, "1.00");
  eq("Arpa deduction 1022.73", a.deduction, "1022.73");
  eq("Arpa net 13977.27 (SRS worked example)", a.netPay, "13977.27");
  eq("Tania unpaid leave 2 days", L["Tania Sultana"].unpaidLeaveDays, "2.00");
  eq("Tania deduction 5909.09", L["Tania Sultana"].deduction, "5909.09");
  eq("Tania net 59090.91", L["Tania Sultana"].netPay, "59090.91");
  eq("Tania leave days are not absences", L["Tania Sultana"].absentDays, "0.00");
  eq("Karim segmented base 60000.00", L["Karim Ali"].basePay, "60000.00");
  check("Karim salary-change note", (L["Karim Ali"].notes ?? []).some((n) => n.startsWith("Salary changed mid-month")));
  eq("Tanvir joiner 13 of 22 days", L["Tanvir Hasan"].daysEmployed, "13.00");
  eq("Tanvir pro-rated base 26000.00", L["Tanvir Hasan"].basePay, "26000.00");
  eq("Asman late arrivals: no penalty, net 150000.00", L["Asman Rahman"].netPay, "150000.00");
  eq("Imran 6-day schedule: 26 working days", L["Imran Hossain"].workingDays, "26.00");
  eq("Owen hourly 176 h", L["Owen Fraser"].hoursWorked, "176.00");
  eq("Owen hourly pay AUD 7920.00", L["Owen Fraser"].basePay, "7920.00");
  eq("Owen currency AUD", L["Owen Fraser"].currency, "AUD");
  check("joiners after September are not in the run", !L["Sazzad Khan"] && !L["Anindya Basak"]);
  const run = (await call(ADMIN, "GET", "runs/2026-09")).body;
  check("totals per currency (BDT + AUD)", (run.totals ?? []).map((t) => t.currency).join(",") === "AUD,BDT", JSON.stringify(run.totals));
  check("September can be finalised", run.canFinalize === true, JSON.stringify(run.finalizeBlockers));
}

console.log("\n== Guards");
{
  const run = (await call(ADMIN, "GET", "runs/2026-09")).body;
  const noConfirm = await call(ADMIN, "POST", "runs/2026-09/finalize", { revision: run.revision });
  eq("finalise without second confirmation → 422", noConfirm.status, 422);
  const stale = await call(ADMIN, "POST", "runs/2026-09/finalize", { confirm: true, revision: run.revision - 1 });
  eq("finalise a stale revision → 409", stale.status, 409);
  const nov = await call(ADMIN, "POST", "runs/2026-11/calculate");
  eq("November draft calculates", nov.status, 200);
  const novFin = await call(ADMIN, "POST", "runs/2026-11/finalize", { confirm: true, revision: nov.body.revision });
  eq("finalise November (month not over) → 422", novFin.status, 422);
  check("…reason: month has not ended", JSON.stringify(novFin.body).includes("has not ended"), JSON.stringify(novFin.body));
  const empRuns = await call(ARPA, "GET", "runs/2026-09");
  eq("employee cannot open payroll runs → 403", empRuns.status, 403);
  const hrSalary = await call(HR, "GET", `salary/employees/${ARPA}`);
  eq("HR has no salary access by default → 403", hrSalary.status, 403);
  const noReason = await call(ADMIN, "POST", `salary/employees/${TANVIR}`, {
    payType: "MONTHLY", amount: 45000, currency: "BDT", effectiveDate: "2026-12-01", reason: " ",
  });
  eq("salary change without reason → 422", noReason.status, 422);
  const dup = await call(ADMIN, "POST", `salary/employees/${TANVIR}`, {
    payType: "MONTHLY", amount: 45000, currency: "BDT", effectiveDate: "2026-09-14", reason: "dup",
  });
  eq("second salary change on the same date → 409", dup.status, 409);
  const adjNoNote = await call(ADMIN, "PUT", `runs/2026-09/adjustments/${ARPA}`, { amount: 100, note: "" });
  eq("adjustment without note → 422", adjNoNote.status, 422);
}

console.log("\n== September: finalise → leave balances reduced (AT-15)");
eq("Arpa Casual before finalise = 1", balance(ARPA, "CASUAL"), 1);
{
  const f = await finalize("2026-09");
  eq("finalise accepted (202)", f.http, 202);
  eq("operation SUCCEEDED", f.op.status, "SUCCEEDED");
  eq("run FINALIZED", (await call(ADMIN, "GET", "runs/2026-09")).body.status, "FINALIZED");
  eq("Arpa Casual after finalise = 0", balance(ARPA, "CASUAL"), 0);
  const again = await call(ADMIN, "POST", "runs/2026-09/finalize", { confirm: true, revision: f.run.revision });
  check("duplicate finalise returns the same operation", again.body.operationId === f.op.operationId, JSON.stringify(again.body));
  eq("…and does not deduct again", balance(ARPA, "CASUAL"), 0);
  const rec = await call(ADMIN, "GET", "runs/2026-09/reconciliation");
  check("reconciliation: payroll covers = leave ledger", rec.body.matches === true, JSON.stringify(rec.body.differences));
  const recalc = await call(ADMIN, "POST", "runs/2026-09/calculate");
  eq("recalculating a finalised month → 409", recalc.status, 409);
  const mine = await call(ARPA, "GET", "me/payslips");
  check("Arpa sees her finalised September payslip", (mine.body ?? []).some((p) => p.period === "2026-09" && p.netPay === 13977.27), JSON.stringify(mine.body));
  const slip = await call(ARPA, "GET", "me/payslips/2026-09");
  eq("Arpa payslip net 13977.27", slip.body.netPay, 13977.27);
  check("payslip shows covered Casual", JSON.stringify(slip.body.covers).includes("CASUAL"));
  const other = await call(ARPA, "GET", `salary/employees/${TANVIR}`);
  eq("employee cannot read another salary → 403", other.status, 403);
}

console.log("\n== September: reopen → leave restored (FR-PAY-14, PAY02)");
{
  const noReason = await call(ADMIN, "POST", "runs/2026-09/reopen", { confirm: true, reason: "" });
  eq("reopen without reason → 422", noReason.status, 422);
  const r = await call(ADMIN, "POST", "runs/2026-09/reopen", { confirm: true, reason: "E2E: verify reopen restores leave" });
  eq("reopen accepted (202)", r.status, 202);
  const op = await waitOperation(r.body);
  eq("reopen operation SUCCEEDED", op.status, "SUCCEEDED");
  const run = (await call(ADMIN, "GET", "runs/2026-09")).body;
  eq("run back to DRAFT", run.status, "DRAFT");
  eq("Arpa Casual restored = 1", balance(ARPA, "CASUAL"), 1);
  const mine = await call(ARPA, "GET", "me/payslips");
  check("reopened payslip no longer shown to the employee", !(mine.body ?? []).some((p) => p.period === "2026-09"));
}

console.log("\n== Ordering (BR-18)");
{
  const oct = await call(ADMIN, "POST", "runs/2026-10/calculate");
  eq("October calculates", oct.status, 200);
  const early = await call(ADMIN, "POST", "runs/2026-10/finalize", { confirm: true, revision: oct.body.revision });
  eq("finalise October before September → 422", early.status, 422);
  check("…reason: finalise 2026-09 first", JSON.stringify(early.body).includes("Finalise 2026-09 first"), JSON.stringify(early.body));
  const warn = (await call(ADMIN, "GET", "runs/2026-10")).body.warnings ?? [];
  check("October warns that September is not finalised (FR-PAY-13)", warn.some((w) => w.includes("2026-09 is not finalised")), JSON.stringify(warn));
}

console.log("\n== September: recalculate + finalise again");
{
  await call(ADMIN, "POST", "runs/2026-09/calculate");
  const f = await finalize("2026-09");
  eq("re-finalise SUCCEEDED", f.op.status, "SUCCEEDED");
  eq("Arpa Casual = 0 again (deducted once)", balance(ARPA, "CASUAL"), 0);
}

console.log("\n== October 2026: adjustments, leavers, half-day leave, cover order");
{
  const stale = (await call(ADMIN, "GET", "runs/2026-10")).body;
  check("October is stale after September finalise", (stale.finalizeBlockers ?? []).some((b) => b.startsWith("Recalculate")), JSON.stringify(stale.finalizeBlockers));
  await call(ADMIN, "POST", "runs/2026-10/calculate");
  const sophie = empId("QII-0022");
  const marcus = empId("QII-0011");
  const a1 = await call(ADMIN, "PUT", `runs/2026-10/adjustments/${sophie}`, { amount: 500, note: "Performance bonus" });
  eq("Sophie +500 adjustment", a1.body.netPay, "7700.00");
  const a2 = await call(ADMIN, "PUT", `runs/2026-10/adjustments/${marcus}`, { amount: -1000, note: "Salary advance recovery" });
  eq("Marcus −1000 advance recovery", a2.body.netPay, "8500.00");
  await call(ADMIN, "POST", "runs/2026-10/calculate");
  const L = await lines("2026-10");
  eq("adjustment survives recalculation (FR-PAY-08)", L["Sophie Nguyen"].adjustment, "500.00");
  eq("adjustment note on payslip", L["Sophie Nguyen"].adjustmentNote, "Performance bonus");
  eq("BD working days in October = 21", L["Arpa Sen"].workingDays, "21.00");
  eq("Arpa full October 15000.00", L["Arpa Sen"].netPay, "15000.00");
  eq("Rumana leaver: 11 of 21 days", L["Rumana Akter"].daysEmployed, "11.00");
  eq("Rumana leaver base 20952.38", L["Rumana Akter"].basePay, "20952.38");
  check("Rumana 'Left 15 Oct' note", (L["Rumana Akter"].notes ?? []).some((n) => n.startsWith("Left 15 Oct")));
  eq("Imran: half-day leave w/o check-in + 1 absent = 1.5", L["Imran Hossain"].absentDays, "1.50");
  eq("Imran: all covered by Casual", L["Imran Hossain"].coveredDays, "1.50");
  eq("Imran net 60000.00", L["Imran Hossain"].netPay, "60000.00");
  eq("Farhana absent 3", L["Farhana Begum"].absentDays, "3.00");
  check("Farhana cover: Annual 2 (Casual 0)", JSON.stringify(L["Farhana Begum"].covers) === JSON.stringify([{ family: "ANNUAL", category: "ANNUAL", days: 2.0 }]) || (L["Farhana Begum"].covers ?? []).every((c) => c.category === "ANNUAL"), JSON.stringify(L["Farhana Begum"].covers));
  eq("Farhana deduction 1 day = 3333.33", L["Farhana Begum"].deduction, "3333.33");
  eq("Farhana net 66666.67", L["Farhana Begum"].netPay, "66666.67");
  eq("Owen: missing check-out shift unpaid → 168 h", L["Owen Fraser"].hoursWorked, "168.00");
  eq("Owen pay AUD 7560.00", L["Owen Fraser"].basePay, "7560.00");
  check("Owen missing check-out flagged", (L["Owen Fraser"].notes ?? []).some((n) => n.includes("missing check-out")));
  eq("Karim on new salary 62000.00", L["Karim Ali"].netPay, "62000.00");
  check("Sazzad (hourly, joined 30 Oct weekend) not an exception", L["Sazzad Khan"] && !L["Sazzad Khan"].exception);
  const f = await finalize("2026-10");
  eq("October finalise SUCCEEDED", f.op.status, "SUCCEEDED");
  eq("Imran Casual 5 → 3.5", balance(7, "CASUAL"), 3.5);
  eq("Farhana Annual 2 → 0", balance(12, "ANNUAL"), 0);
  const reopenSep = await call(ADMIN, "POST", "runs/2026-09/reopen", { confirm: true, reason: "should be blocked" });
  eq("reopen September while October is final → 422", reopenSep.status, 422);
}

console.log("\n== November 2026: live draft (month not over)");
{
  // Seeded attendance runs to yesterday, so the only November absence is Priya's 4 Nov, whatever the
  // date. Working days are derived independently: Sun–Thu in November minus company holidays at her
  // location (holidays added by hand in this environment count too).
  const novemberOver = sql("timework_db", "select (now() at time zone 'Asia/Dhaka')::date > date '2026-11-30'") === "t";
  const holidays = Number(sql("timework_db", "select count(distinct d) from holiday h, generate_series(h.start_date, h.end_date, interval '1 day') d "
    + "where d between '2026-11-01' and '2026-11-30' and extract(dow from d) in (0,1,2,3,4) and not h.optional_flag "
    + "and (h.location_id is null or h.location_id = 1)"));
  const workingDays = 22 - holidays; // 22 Sun–Thu days in November 2026
  const round2 = (x) => (Math.round(x * 100) / 100).toFixed(2);
  await call(ADMIN, "POST", "runs/2026-11/calculate");
  const run = (await call(ADMIN, "GET", "runs/2026-11")).body;
  eq("November is a DRAFT", run.status, "DRAFT");
  if (!novemberOver) check("November cannot be finalised", run.canFinalize === false);
  const L = await lines("2026-11");
  eq(`Priya working days = 22 − ${holidays} holiday(s)`, L["Priya Das"].workingDays, workingDays);
  eq("Priya absent only 4 Nov", L["Priya Das"].absentDays, 1);
  eq(`Priya no balance → 45000 / ${workingDays} deducted`, L["Priya Das"].deduction, round2(45000 / workingDays));
  if (!novemberOver) check("month-not-over note", (L["Priya Das"].notes ?? []).some((n) => n.startsWith("Month not over")));
  eq("Anindya hourly 30 h", L["Anindya Basak"].hoursWorked, "30.00");
  eq("Anindya pay 6000.00", L["Anindya Basak"].basePay, "6000.00");
  check("employee without salary is flagged", L["No Checklist"]?.exception === true, JSON.stringify(L["No Checklist"]?.notes));
  const est = await call(ARPA, "GET", "me/estimate");
  eq("Arpa month-to-date estimate", est.body.estimate?.netPay, "15000.00");
}

console.log("\n== CSV export (FR-PAY-16)");
{
  const r = await fetch(`${G}/payroll/api/payroll/runs/2026-09/export.csv`, { headers: { Authorization: `Bearer ${await token(ADMIN)}` } });
  const bytes = new Uint8Array(await r.arrayBuffer());
  const bom = bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf;
  const csv = new TextDecoder("utf-8").decode(bytes);
  eq("export 200", r.status, 200);
  check("UTF-8 BOM + header", bom && csv.includes("employee_code,employee_name,currency"));
  check("Arpa row with net 13977.27", csv.split("\n").some((l) => l.includes("Arpa Sen") && l.includes("13977.27")));
  check("per-currency TOTAL rows", csv.includes("TOTAL,BDT") && csv.includes("TOTAL,AUD"));
  const emp = await fetch(`${G}/payroll/api/payroll/runs/2026-09/export.csv`, { headers: { Authorization: `Bearer ${await token(ARPA)}` } });
  eq("employee cannot export → 403", emp.status, 403);
}

console.log(`\n${pass} passed, ${fail} failed`);
if (fail) {
  console.log(failures.map((f) => ` - ${f}`).join("\n"));
  process.exit(1);
}
