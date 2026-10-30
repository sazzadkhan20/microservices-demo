/**
 * Deep E2E: FR-SAL + FR-PAY including Arpa Sep-2026 worked example.
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

function approx(a, b, eps = 0.02) {
  return Math.abs(Number(a) - Number(b)) <= eps;
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
          resolve({ status: res.statusCode, body: json, raw: b, headers: res.headers });
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
  console.log("=== payroll deep FR-SAL / FR-PAY ===\n");
  const adminTok = await tokenFor(1);
  const empId = await empIdByCode(adminTok, "QII-0020");
  const empTok = await tokenFor(empId);
  const period = "2026-09"; // ended month — finalize allowed

  // ---- FR-SAL-01/02 salary history ----
  const sal = await req("POST", "/payroll/api/payroll/salary-history", {
    token: adminTok,
    body: {
      employeeId: empId,
      effectiveDate: "2026-09-01",
      amount: 15000,
      payType: "MONTHLY",
      currency: "BDT",
      note: "Arpa E2E Sep 2026 gross",
    },
  });
  ok(
    "salary history create (FR-SAL-01)",
    sal.status === 200 || sal.status === 201,
    `${sal.status} ${String(sal.raw).slice(0, 200)}`,
  );
  ok("salary history has reason/note", !!sal.body?.note, JSON.stringify(sal.body));

  const hist = await req("GET", `/payroll/api/payroll/salary-history/employee/${empId}`, {
    token: adminTok,
  });
  ok("salary history list (FR-SAL-02)", hist.status === 200 && Array.isArray(hist.body) && hist.body.length > 0);

  // Employee without salary:edit must not see others' salary
  const histDenied = await req("GET", `/payroll/api/payroll/salary-history/employee/1`, {
    token: empTok,
  });
  ok(
    "other employee salary forbidden (FR-SAL-03)",
    histDenied.status === 403 || histDenied.status === 401 || histDenied.status >= 400,
    String(histDenied.status),
  );

  // ---- payroll facts from HRMS ----
  const facts = await req("GET", `/hrms/api/payroll-facts/employee/${empId}?period=${period}`, {
    token: adminTok,
  });
  ok(
    "payroll-facts 200",
    facts.status === 200,
    `${facts.status} ${String(facts.raw).slice(0, 220)}`,
  );

  // ---- Arpa worked example (FR-PAY-02..06,09) ----
  // monthly 15000, 22 WD, absent 2.5, CASUAL=1 → deduct 1.5 → net 13977.27
  const calc = await req("POST", "/payroll/api/payroll/calculate", {
    token: adminTok,
    body: {
      employeeId: empId,
      periodLabel: period,
      useHrmsAttendance: true,
      absenceDays: 2.5,
      leaveBalances: { CASUAL: 1, ANNUAL: 0 },
      workingDaysInMonth: 22,
      daysEmployed: 22,
    },
  });
  ok(
    "calculate draft 200",
    calc.status === 200 || calc.status === 201,
    `${calc.status} ${String(calc.raw).slice(0, 240)}`,
  );
  const d = calc.body?.details || {};
  const perDay = Number(d.perDayRate ?? d.dayRate);
  const ded = Number(calc.body?.deductionAmount);
  const net = Number(calc.body?.netAmount);
  const base = Number(calc.body?.grossAmount ?? d.basePay);
  ok("Arpa per-day ≈ 681.82", approx(perDay, 681.8182, 0.01) || approx(perDay, 681.82, 0.01), `perDay=${perDay}`);
  ok("Arpa deduction ≈ 1022.73", approx(ded, 1022.73, 0.05), `ded=${ded}`);
  ok("Arpa net ≈ 13977.27", approx(net, 13977.27, 0.05), `net=${net} base=${base}`);
  ok(
    "notes present (FR-PAY-10)",
    Array.isArray(d.notes) || Array.isArray(d.exceptionNotes),
    JSON.stringify(d.notes || d.exceptionNotes),
  );
  const payslipId = calc.body?.payslipId ?? calc.body?.id;
  ok("payslip id present", !!payslipId, JSON.stringify(calc.body));

  // ---- adjustment (FR-PAY-08) ----
  const calcAdj = await req("POST", "/payroll/api/payroll/calculate", {
    token: adminTok,
    body: {
      employeeId: empId,
      periodLabel: period,
      absenceDays: 2.5,
      leaveBalances: { CASUAL: 1 },
      workingDaysInMonth: 22,
      daysEmployed: 22,
      adjustmentAmount: 500,
      adjustmentNote: "Festival bonus",
    },
  });
  ok(
    "adjustment applied (FR-PAY-08)",
    calcAdj.status < 300 && approx(Number(calcAdj.body?.netAmount), 13977.27 + 500, 0.1),
    `net=${calcAdj.body?.netAmount} ${String(calcAdj.raw).slice(0, 160)}`,
  );
  // reset without bonus for finalize
  const reset = await req("POST", "/payroll/api/payroll/calculate", {
    token: adminTok,
    body: {
      employeeId: empId,
      periodLabel: period,
      absenceDays: 2.5,
      leaveBalances: { CASUAL: 1 },
      workingDaysInMonth: 22,
      daysEmployed: 22,
      adjustmentAmount: 0,
    },
  });
  const id = reset.body?.payslipId ?? payslipId;

  // ---- finalize without confirm ----
  const badFin = await req("POST", `/payroll/api/payroll/payslips/${id}/finalize`, {
    token: adminTok,
    body: { confirm: false },
  });
  ok(
    "finalize without confirm refused (FR-PAY-12)",
    badFin.status >= 400,
    `${badFin.status} ${String(badFin.raw).slice(0, 160)}`,
  );

  // current month cannot finalize (today is still inside the month)
  const curPeriod = new Date().toISOString().slice(0, 7);
  const curCalc = await req("POST", "/payroll/api/payroll/calculate", {
    token: adminTok,
    body: {
      employeeId: empId,
      periodLabel: curPeriod,
      absenceDays: 0,
      leaveBalances: { CASUAL: 5 },
      workingDaysInMonth: 22,
      daysEmployed: 10,
    },
  });
  const curId = curCalc.body?.payslipId ?? curCalc.body?.id;
  ok("current-month draft created", curCalc.status < 300 && !!curId, `${curCalc.status}`);
  if (curId) {
    const earlyFin = await req("POST", `/payroll/api/payroll/payslips/${curId}/finalize`, {
      token: adminTok,
      body: { confirm: true },
    });
    ok(
      "finalize current month refused (FR-PAY-12)",
      earlyFin.status >= 400,
      `${earlyFin.status} ${String(earlyFin.raw).slice(0, 160)}`,
    );
  }

  const fin = await req("POST", `/payroll/api/payroll/payslips/${id}/finalize`, {
    token: adminTok,
    body: { confirm: true },
  });
  ok(
    "finalize Sep with confirm",
    fin.status === 200 && String(fin.body?.details?.status || fin.body?.status).toUpperCase().includes("FINAL"),
    `${fin.status} ${String(fin.raw).slice(0, 200)}`,
  );

  // ---- reopen (FR-PAY-14) ----
  const reopen = await req("POST", `/payroll/api/payroll/payslips/${id}/reopen`, {
    token: adminTok,
  });
  ok(
    "reopen to draft (FR-PAY-14)",
    reopen.status === 200 && String(reopen.body?.details?.status || reopen.body?.status).toUpperCase().includes("DRAFT"),
    `${reopen.status} ${String(reopen.raw).slice(0, 160)}`,
  );

  // ---- warnings (FR-PAY-13) ----
  const warn = await req("GET", `/payroll/api/payroll/warnings?period=2026-10`, { token: adminTok });
  ok("warnings endpoint 200 (FR-PAY-13)", warn.status === 200, `${warn.status}`);

  // ---- calculate-month (FR-PAY-01) ----
  const month = await req("POST", `/payroll/api/payroll/calculate-month?period=${period}`, {
    token: adminTok,
  });
  ok(
    "calculate-month (FR-PAY-01)",
    month.status === 200 && Number(month.body?.processed ?? month.body?.created ?? 0) >= 0,
    `${month.status} ${String(month.raw).slice(0, 200)}`,
  );

  // ---- CSV export (FR-PAY-16) ----
  const csv = await req("GET", `/payroll/api/payroll/export.csv?period=${period}`, { token: adminTok });
  ok(
    "CSV export (FR-PAY-16)",
    csv.status === 200 && (String(csv.raw).includes("employee") || String(csv.raw).includes("net") || String(csv.raw).length > 10),
    `${csv.status} ${String(csv.raw).slice(0, 120)}`,
  );

  // ---- me/current (FR-SAL-03) ----
  const me = await req("GET", "/payroll/api/payroll/me/current", { token: empTok });
  ok("me/current 200 (FR-SAL-03)", me.status === 200, `${me.status} ${String(me.raw).slice(0, 160)}`);

  // ---- DIRECT absence rule via config ----
  const cfg = await req("GET", "/payroll/api/payroll/config", { token: adminTok });
  ok("payroll config 200", cfg.status === 200, String(cfg.status));

  // full month, zero absence, with Sep salary history → net ≈ 15000
  const zeroAbs = await req("POST", "/payroll/api/payroll/calculate", {
    token: adminTok,
    body: {
      employeeId: empId,
      periodLabel: period,
      absenceDays: 0,
      leaveBalances: { CASUAL: 5 },
      workingDaysInMonth: 22,
      daysEmployed: 22,
    },
  });
  ok(
    "full month no absence ≈ gross",
    zeroAbs.status < 300 && approx(Number(zeroAbs.body?.netAmount), 15000, 1),
    `net=${zeroAbs.body?.netAmount}`,
  );

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
