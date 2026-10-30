import { GW, req, token, A, check, summary } from "./lib.mjs";
const P = `${GW}/payroll/api/payroll`;
const tA = await token(1);

let r = await req("GET", `${P}/config`, { headers: A(tA) });
console.log("config:", r.text.slice(0, 600));

r = await req("POST", `${P}/calculate-month?period=2026-09`, { headers: A(tA) });
check("PAY run month 2026-09", r.status === 200, `status=${r.status} ms=${r.ms} ${r.text.slice(0, 400)}`);
check("NFR-08 month calc fast (<10s for 100)", r.ms < 10000, `ms=${r.ms} for ~21 employees`, "Medium");
const run = r.json;
console.log("keys:", Object.keys(run || {}));
const rows = run?.payslips || run?.rows || run?.content || [];
console.log("rows:", rows.length);
for (const x of rows.slice(0, 25)) console.log(`  emp${x.employeeId} ${x.currency ?? x.payCurrency} gross=${x.grossAmount} ded=${x.deductionAmount} net=${x.netAmount} absent=${x.absentDays} covered=${x.coveredDays ?? x.daysCoveredByLeave} unpaid=${x.unpaidDays} status=${x.status} notes=${JSON.stringify(x.notes ?? x.exceptionNotes ?? "").slice(0, 120)}`);
const dp = rows.filter((x) => /\.\d{3,}/.test(String(x.netAmount)) || /\.\d{3,}/.test(String(x.grossAmount)));
check("DR-02 money 2 decimals", dp.length === 0, `rows with >2dp: ${dp.length} e.g. ${dp.slice(0, 3).map((x) => x.netAmount).join(", ")}`, "High");
console.log("totals:", JSON.stringify(run?.totals ?? run?.totalsByCurrency ?? run?.summary ?? null));
check("PAY-09 totals per currency", !!(run?.totalsByCurrency || run?.totals?.BDT || run?.totals?.AUD), JSON.stringify(run?.totals ?? run?.totalsByCurrency ?? "none").slice(0, 200), "Medium");

const ids = new Set(rows.map((x) => Number(x.employeeId)));
check("PAY-01 row for every employee", ids.size >= 20, `employees with rows=${ids.size}`, "High");

// finalize future month
r = await req("POST", `${P}/calculate-month?period=2026-11`, { headers: A(tA) });
const nov = (r.json?.payslips || r.json?.rows || r.json?.content || [])[0];
if (nov) {
  r = await req("POST", `${P}/payslips/${nov.id}/finalize`, { headers: A(tA), body: { confirm: true } });
  check("PAY-12 finalize before month end refused", r.status >= 400, `status=${r.status} ${r.text.slice(0, 160)}`, "High");
}
// finalize without confirm
const sep = rows.find((x) => x.status === "DRAFT");
if (sep) {
  r = await req("POST", `${P}/payslips/${sep.id}/finalize`, { headers: A(tA), body: {} });
  check("PAY-12 second confirmation required", r.status >= 400, `status=${r.status} ${r.text.slice(0, 160)}`, "Medium");
  r = await req("GET", `${P}/warnings?period=2026-09`, { headers: A(tA) });
  console.log("warnings 2026-09:", r.text.slice(0, 300));
  r = await req("POST", `${P}/payslips/${sep.id}/finalize`, { headers: A(tA), body: { confirm: true } });
  check("BR-18 finalize Sep while Aug not finalized refused (in order)", r.status >= 400, `status=${r.status} ${r.text.slice(0, 200)}`, "Medium");
  if (r.status === 200) {
    const r2 = await req("POST", `${P}/calculate-month?period=2026-09`, { headers: A(tA) });
    const same = (r2.json?.payslips || r2.json?.rows || []).find((x) => x.id === sep.id || (x.employeeId === sep.employeeId));
    check("DR-03 finalized snapshot unchanged by recalculation", same?.status === "FINALIZED" && String(same?.netAmount) === String(r.json?.netAmount), `status=${same?.status}`, "High");
    const ro = await req("POST", `${P}/payslips/${sep.id}/reopen`, { headers: A(tA) });
    check("PAY-14 reopen", ro.status === 200 && ro.json?.status === "DRAFT", `status=${ro.status} st=${ro.json?.status}`);
  }
}
// concurrency: parallel calculate-month
const par = await Promise.all([1, 2, 3, 4].map(() => req("POST", `${P}/calculate-month?period=2026-08`, { headers: A(tA) })));
check("PAY concurrent calculate-month no 500", par.every((x) => x.status === 200), par.map((x) => x.status).join(","), "High");
r = await req("GET", `${P}/payslips?size=200`, { headers: A(tA) });
const aug = (r.json?.content || []).filter((x) => x.periodLabel === "2026-08");
const per = {};
for (const x of aug) per[x.employeeId] = (per[x.employeeId] || 0) + 1;
const dup = Object.entries(per).filter(([, n]) => n > 1);
check("PAY concurrent calc -> no duplicate payslips per employee/month", dup.length === 0, `dups=${JSON.stringify(dup).slice(0, 200)}`, "High");

// CSV
r = await req("GET", `${P}/export.csv?period=2026-09`, { headers: A(tA) });
console.log("CSV head:\n" + r.text.split("\n").slice(0, 3).join("\n"));
check("PAY-16 CSV export", r.status === 200 && r.text.includes(","), `status=${r.status}`);
check("IR-05 CSV has UTF-8 BOM for Excel", r.text.charCodeAt(0) === 0xfeff, "no BOM", "Low");

// adjustment one per month
summary("payroll");
