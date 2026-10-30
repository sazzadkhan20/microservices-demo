import { GW, req, token, A, check, summary } from "./lib.mjs";
const H = `${GW}/hrms/api`;
const tA = await token(1);
const emps = (await req("GET", `${H}/employees?page=0&size=100`, { headers: A(tA) })).json.content;
const byId = Object.fromEntries(emps.map((e) => [e.id, e]));
const TANIA = 6, PRIYA = 10, LINA = 4, MARCUS = 5;
console.log("Tania team", byId[TANIA].teamId, "mgr", byId[TANIA].managerId, "| Priya team", byId[PRIYA].teamId, "mgr", byId[PRIYA].managerId);
const hourly = emps.find((e) => e.workPattern === "HOURLY" || e.employmentType === "HOURLY");
console.log("hourly employee:", hourly?.id, hourly?.fullName);
const hol = (await req("GET", `${H}/holidays?year=2026`, { headers: A(tA) })).json;
console.log("holidays 2026:", JSON.stringify((hol?.content || hol || []).map((h) => [h.date || h.startDate, h.name, h.locationId, h.optional])).slice(0, 600));

const tT = await token(TANIA), tP = await token(PRIYA), tL = await token(LINA), tM = await token(MARCUS);
const bal = async (t, id) => (await req("GET", `${H}/leave-balances/employee/${id}`, { headers: A(t) })).json;
const casualBal = async (id) => (await bal(tA, id))?.find?.((b) => b.leaveTypeCode === "CASUAL")?.balanceDays;
const prev = (t, b) => req("POST", `${H}/leave/preview`, { headers: A(t), body: b });
const apply = (t, b) => req("POST", `${H}/leave/request`, { headers: A(t), body: b });

let r = await prev(tT, { leaveTypeCode: "CASUAL", startDate: "2026-11-06", endDate: "2026-11-08", reason: "x" });
check("LV-10 weekend excluded (Fri-Sun => 1 day BD Sun-Thu)", Number(r.json?.workingDaysCounted ?? r.json?.days) === 1, r.text.slice(0, 300));
r = await prev(tT, { leaveTypeCode: "SICK", startDate: "2026-12-14", endDate: "2026-12-17", reason: "x" });
check("LV-10 holiday excluded (14-17 Dec incl. 16 Dec Victory Day)", Number(r.json?.workingDaysCounted ?? r.json?.days) === 3, `days=${r.json?.workingDaysCounted ?? r.json?.days} ${r.text.slice(0, 200)}`, "Medium");
r = await prev(tT, { leaveTypeCode: "CASUAL", startDate: "2026-11-09", endDate: "2026-11-09", halfDay: true, halfDayPart: "FIRST", reason: "x" });
check("LV-10 half day = 0.5", Number(r.json?.workingDaysCounted ?? r.json?.days) === 0.5, r.text.slice(0, 200));
r = await prev(tT, { leaveTypeCode: "CASUAL", startDate: "2026-11-09", endDate: "2026-11-10", halfDay: true, halfDayPart: "FIRST", reason: "x" });
check("LV-08 half day only on single date", r.status >= 400 || r.json?.valid === false || (r.json?.errors || []).length > 0, `status=${r.status} ${r.text.slice(0, 200)}`);

r = await apply(tT, { leaveTypeCode: "CASUAL", startDate: "2026-11-15", endDate: "2026-11-18", reason: "4 days" });
check("LV-11 maxPerRequest(3) enforced", r.status >= 400, `status=${r.status} ${r.text.slice(0, 160)}`);
r = await apply(tT, { leaveTypeCode: "ANNUAL", startDate: "2026-11-02", endDate: "2026-11-02", reason: "short notice" });
check("LV-11 notice(7d) enforced", r.status >= 400, `status=${r.status} ${r.text.slice(0, 160)}`);
if (r.status < 400) await req("POST", `${H}/leave-applications/${r.json.id}/withdraw`, { headers: A(tT) });
r = await apply(tT, { leaveTypeCode: "ANNUAL", startDate: "2027-01-10", endDate: "2027-01-10", reason: "outside period" });
check("LV-11 outside category period refused", r.status >= 400, `status=${r.status} ${r.text.slice(0, 160)}`);
if (r.status < 400) await req("POST", `${H}/leave-applications/${r.json.id}/withdraw`, { headers: A(tT) });
r = await apply(tT, { leaveTypeCode: "ANNUAL_2025", startDate: "2026-12-01", endDate: "2026-12-01", reason: "inactive cat" });
check("LV inactive category refused", r.status >= 400, `status=${r.status} ${r.text.slice(0, 160)}`);
if (r.status < 400) await req("POST", `${H}/leave-applications/${r.json.id}/withdraw`, { headers: A(tT) });
r = await apply(tT, { leaveTypeCode: "CASUAL", startDate: "2026-11-20", endDate: "2026-11-19", reason: "reversed" });
check("LV end<start -> 400", r.status === 400, `status=${r.status} ${r.text.slice(0, 160)}`, "Medium");
r = await apply(tT, { leaveTypeCode: "CASUAL", startDate: "2026-11-06", endDate: "2026-11-07", reason: "only weekend" });
check("LV request covering only weekly-off refused (0 days)", r.status >= 400, `status=${r.status} days=${r.json?.workingDays ?? r.json?.days}`, "Medium");
if (r.status < 400) await req("POST", `${H}/leave-applications/${r.json.id}/withdraw`, { headers: A(tT) });
if (hourly) {
  const tH = await token(hourly.id);
  r = await apply(tH, { leaveTypeCode: "CASUAL", startDate: "2026-11-24", endDate: "2026-11-24", reason: "hourly" });
  check("LV-03 hourly employee cannot take day-based leave", r.status >= 400, `status=${r.status} ${r.text.slice(0, 160)}`);
  if (r.status < 400) await req("POST", `${H}/leave-applications/${r.json.id}/withdraw`, { headers: A(tH) });
}

// main flow
const b0 = await casualBal(TANIA);
r = await apply(tT, { leaveTypeCode: "CASUAL", startDate: "2026-11-23", endDate: "2026-11-23", reason: "QA main" });
check("LV main request created", r.status === 200 || r.status === 201, `status=${r.status} ${r.text.slice(0, 300)}`);
const L1 = r.json?.id;
const pendBal = await casualBal(TANIA);
check("LV-04 pending deducted from available balance", Number(pendBal) === Number(b0) - 1, `before=${b0} afterPending=${pendBal}`, "Medium");
r = await apply(tT, { leaveTypeCode: "SICK", startDate: "2026-11-23", endDate: "2026-11-23", reason: "overlap" });
check("LV-11 overlap refused", r.status >= 400, `status=${r.status} ${r.text.slice(0, 160)}`);
if (r.status < 400) await req("POST", `${H}/leave-applications/${r.json.id}/withdraw`, { headers: A(tT) });

r = await req("POST", `${H}/leave-applications/${L1}/approve`, { headers: A(tT), body: { comment: "self" } });
check("BR-01 self approve refused", r.status === 403 || r.status === 409, `status=${r.status}`, "Critical");
r = await req("POST", `${H}/leave-applications/${L1}/approve`, { headers: A(tM), body: { comment: "other mgr" } });
check("LV-12 non-chain manager cannot approve", r.status === 403 || r.status === 409, `status=${r.status} ${r.text.slice(0, 120)}`, "Critical");

// team clash
r = await apply(tP, { leaveTypeCode: "CASUAL", startDate: "2026-11-23", endDate: "2026-11-23", reason: "clash" });
const L2 = r.json?.id;
check("LV-14 team limit flagged on second same-team request", r.json?.teamLimitExceeded === true || /team/i.test(JSON.stringify(r.json?.warnings || "")), `status=${r.status} sameTeam=${byId[TANIA].teamId === byId[PRIYA].teamId} body=${r.text.slice(0, 250)}`, "High");
r = await req("POST", `${H}/leave-applications/${L2}/reject`, { headers: A(tL), body: {} });
check("LV-15 reject requires reason", r.status >= 400, `status=${r.status} ${r.text.slice(0, 120)}`);
if (r.status >= 400) await req("POST", `${H}/leave-applications/${L2}/reject`, { headers: A(tL), body: { comment: "clash" } });

r = await req("POST", `${H}/leave-applications/${L1}/approve`, { headers: A(tL), body: { comment: "ok" } });
check("LV approve by direct manager", r.status === 200, `status=${r.status} st=${r.json?.status}`);
const chainLen = r.json?.approvals?.length ?? r.json?.steps?.length;
check("LV-12/AT-10 default chain manager -> Fayaz (2 steps)", r.json?.status !== "APPROVED", `after manager approve status=${r.json?.status} (single-step chain)`, "High");
r = await req("POST", `${H}/leave-applications/${L1}/approve`, { headers: A(tL), body: { comment: "again" } });
check("LV double-approve rejected", r.status >= 400, `status=${r.status} st=${r.json?.status}`, "Medium");
const b1 = await casualBal(TANIA);
check("LV balance after approval = before - 1", Number(b1) === Number(b0) - 1, `before=${b0} after=${b1}`);

r = await req("POST", `${H}/leave-applications/${L1}/cancel`, { headers: A(tT), body: { comment: "plans changed" } });
check("LV-18 cancel approved goes through chain (not instant)", r.status === 200 && r.json?.status !== "CANCELLED", `status=${r.status} st=${r.json?.status}`, "Medium");
if (r.json?.status && r.json.status !== "CANCELLED") {
  r = await req("POST", `${H}/leave-applications/${L1}/approve`, { headers: A(tL), body: { comment: "cancel ok" } });
  console.log("   cancel-approve:", r.status, r.json?.status);
}
const b2 = await casualBal(TANIA);
check("LV-18 days returned after cancel", Number(b2) === Number(b0), `before=${b0} afterCancel=${b2}`);

// concurrency: 6 parallel identical requests
const rs = await Promise.all(Array.from({ length: 6 }, () => apply(tT, { leaveTypeCode: "CASUAL", startDate: "2026-11-25", endDate: "2026-11-25", reason: "race" })));
const ok = rs.filter((x) => x.status === 200 || x.status === 201);
check("LV race: parallel duplicate requests -> exactly 1 created", ok.length === 1, `created=${ok.length} statuses=${rs.map((x) => x.status)}`, "High");
for (const x of ok) await req("POST", `${H}/leave-applications/${x.json.id}/withdraw`, { headers: A(tT) });

// withdraw by someone else
r = await apply(tT, { leaveTypeCode: "CASUAL", startDate: "2026-11-26", endDate: "2026-11-26", reason: "w" });
const L3 = r.json?.id;
r = await req("POST", `${H}/leave-applications/${L3}/withdraw`, { headers: A(tP) });
check("LV withdraw by non-owner refused", r.status === 403, `status=${r.status}`, "High");
r = await req("POST", `${H}/leave-applications/${L3}/withdraw`, { headers: A(tT) });
check("LV withdraw own pending", r.status === 200, `status=${r.status} st=${r.json?.status}`);

// approvals queue scope
r = await req("GET", `${H}/approvals/pending`, { headers: A(tP) });
check("Approvals queue for employee empty/forbidden", r.status === 403 || (r.json?.content || r.json || []).length === 0, `status=${r.status} n=${(r.json?.content || r.json || []).length}`, "Medium");

// past-dated leave
r = await apply(tT, { leaveTypeCode: "CASUAL", startDate: "2026-09-01", endDate: "2026-09-01", reason: "backdated" });
check("LV backdated casual leave (info)", true, `status=${r.status} -> backdated allowed=${r.status < 400}`, "Info");
if (r.status < 400) await req("POST", `${H}/leave-applications/${r.json.id}/withdraw`, { headers: A(tT) });

const f = summary("leave");
