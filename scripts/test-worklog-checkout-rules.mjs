#!/usr/bin/env node
/**
 * Live QA: FR-WL-05 work-log checkout rules (BLOCK / FLAG / OFF + min %).
 * Uses Gateway demo auth. Restores original rules at the end.
 */
const GW = process.env.GATEWAY || "http://127.0.0.1:8090";
const DEMO = process.env.DEMO_AUTH_KEY || "local-demo-auth-key-change-me";

const results = [];
function check(id, pass, detail) {
  results.push({ id, result: pass ? "PASS" : "FAIL", detail });
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
    /* raw */
  }
  return { status: r.status, text, json };
}

async function token(employeeId) {
  const r = await req("POST", `${GW}/api/auth/token`, {
    headers: { "X-Demo-Auth-Key": DEMO },
    body: { employeeId },
  });
  return r.json?.accessToken || r.json?.token || null;
}

function msg(r) {
  return r.json?.message || r.json?.detail || r.json?.error || r.text?.slice(0, 180) || `HTTP ${r.status}`;
}

async function main() {
  console.log(`Gateway ${GW}\n`);

  const accounts = await req("GET", `${GW}/hrms/api/auth/accounts`, {
    headers: { "X-Demo-Auth-Key": DEMO },
  });
  if (accounts.status !== 200 || !Array.isArray(accounts.json)) {
    check("SETUP-ACCOUNTS", false, `cannot list accounts: ${accounts.status}`);
    process.exit(1);
  }
  const admin = accounts.json.find((a) => String(a.role).includes("SUPERADMIN"));
  const employee =
    accounts.json.find((a) => String(a.role) === "EMPLOYEE" && String(a.name).includes("With Manager")) ||
    accounts.json.find((a) => String(a.role) === "EMPLOYEE");
  check("SETUP-CAST", !!admin && !!employee, `admin=${admin?.name} emp=${employee?.name} (#${employee?.employeeId})`);

  const tokA = await token(Number(admin.employeeId));
  const tokE = await token(Number(employee.employeeId));
  check("SETUP-TOKENS", !!tokA && !!tokE, `admin+employee tokens`);
  if (!tokA || !tokE) process.exit(1);

  const original = await req("GET", `${GW}/hrms/api/work-logs/rules`, { token: tokA });
  check("RULES-GET", original.status === 200 && !!original.json?.workLogRule, `rule=${original.json?.workLogRule} pct=${original.json?.workLogMinPercent}`);

  async function setRules(patch) {
    const r = await req("PUT", `${GW}/hrms/api/work-logs/rules`, { token: tokA, body: patch });
    if (r.status >= 300) throw new Error(`setRules failed: ${msg(r)}`);
    return r.json;
  }

  async function today() {
    return req("GET", `${GW}/hrms/api/attendance/today`, { token: tokE });
  }

  async function ensureOpenPunch() {
    let t = await today();
    const now = new Date();
    const twoHoursAgo = new Date(now.getTime() - 2 * 60 * 60 * 1000).toISOString();
    const workDate = t.json?.workDate || t.json?.date || now.toISOString().slice(0, 10);

    if (!t.json?.checkIn) {
      const cin = await req("POST", `${GW}/hrms/api/attendance/check-in`, { token: tokE, body: {} });
      if (cin.status >= 300 && !/already/i.test(msg(cin))) {
        throw new Error(`check-in failed: ${msg(cin)}`);
      }
    }

    // Age the check-in and clear any check-out so checkout rule tests can run.
    const corr = await req("POST", `${GW}/hrms/api/attendance/correct`, {
      token: tokA,
      body: {
        employeeId: Number(employee.employeeId),
        workDate,
        checkInAt: twoHoursAgo,
        clearCheckOut: true,
        reason: "QA reopen / age punch for work-log rule test",
      },
    });
    if (corr.status >= 300) throw new Error(`correct/reopen failed: ${msg(corr)}`);
    t = await today();
    if (t.json?.checkOut) throw new Error(`expected open punch but checkOut=${t.json.checkOut}`);
    return t;
  }

  async function clearTodayLogs() {
    const logs = await req("GET", `${GW}/hrms/api/work-logs?page=1&size=100&sort=workDate,desc`, { token: tokE });
    const todayIso = new Date().toISOString().slice(0, 10);
    const rows = logs.json?.content || (Array.isArray(logs.json) ? logs.json : []);
    for (const row of rows) {
      if (String(row.workDate) !== todayIso) continue;
      // May fail if outside edit window — ignore.
      await req("DELETE", `${GW}/hrms/api/work-logs/${row.id}`, { token: tokE });
    }
  }

  async function addLog(minutes) {
    const tasks = await req("GET", `${GW}/hrms/api/tasks/assigned`, { token: tokE });
    const list = Array.isArray(tasks.json) ? tasks.json : tasks.json?.content || [];
    const open = list.find((t) => ["OPEN", "IN_PROGRESS"].includes(String(t.status || "").toUpperCase())) || list[0];
    if (!open?.id) throw new Error("No assigned open task for employee — cannot add work log");
    const todayIso = new Date().toISOString().slice(0, 10);
    const r = await req("POST", `${GW}/hrms/api/work-logs`, {
      token: tokE,
      body: {
        workDate: todayIso,
        taskId: open.id,
        minutes,
        note: `QA work-log ${minutes}m`,
        description: `QA work-log ${minutes}m`,
      },
    });
    if (r.status >= 300) throw new Error(`add work-log failed: ${msg(r)}`);
    return r.json;
  }

  // ---------- BLOCK: no log ----------
  try {
    await setRules({ workLogRule: "BLOCK", workLogMinPercent: 80, workLogPercentOf: "WORKED" });
    await ensureOpenPunch();
    await clearTodayLogs();
    const out = await req("POST", `${GW}/hrms/api/attendance/check-out`, { token: tokE, body: {} });
    const blocked =
      out.status >= 400 &&
      /work log|before checking out|incomplete/i.test(msg(out));
    check("BLOCK-NO-LOG", blocked, `status=${out.status} ${msg(out)}`);
  } catch (e) {
    check("BLOCK-NO-LOG", false, String(e.message || e));
  }

  // ---------- BLOCK: under min % ----------
  try {
    await setRules({ workLogRule: "BLOCK", workLogMinPercent: 80, workLogPercentOf: "WORKED" });
    await ensureOpenPunch();
    await clearTodayLogs();
    await addLog(15); // ~15m vs ~120m punched → ~12.5% < 80%
    const out = await req("POST", `${GW}/hrms/api/attendance/check-out`, { token: tokE, body: {} });
    const blocked =
      out.status >= 400 &&
      /incomplete|need ≥|need >=|work log/i.test(msg(out));
    check("BLOCK-UNDER-PERCENT", blocked, `status=${out.status} ${msg(out)}`);
  } catch (e) {
    check("BLOCK-UNDER-PERCENT", false, String(e.message || e));
  }

  // ---------- FLAG: no/under log allowed + note ----------
  try {
    await setRules({ workLogRule: "FLAG", workLogMinPercent: 80, workLogPercentOf: "WORKED" });
    await ensureOpenPunch();
    await clearTodayLogs();
    // keep under % (or zero)
    await addLog(15);
    const out = await req("POST", `${GW}/hrms/api/attendance/check-out`, { token: tokE, body: {} });
    const ok = out.status >= 200 && out.status < 300;
    const note = String(out.json?.note || "");
    const flagged = /FLAG/i.test(note);
    check("FLAG-ALLOWS-CHECKOUT", ok, `status=${out.status}`);
    check("FLAG-STAMPS-NOTE", flagged, `note=${note || "(empty)"}`);
  } catch (e) {
    check("FLAG-ALLOWS-CHECKOUT", false, String(e.message || e));
    check("FLAG-STAMPS-NOTE", false, String(e.message || e));
  }

  // ---------- OFF: no gate ----------
  try {
    await setRules({ workLogRule: "OFF", workLogMinPercent: 80 });
    await ensureOpenPunch();
    await clearTodayLogs();
    const out = await req("POST", `${GW}/hrms/api/attendance/check-out`, { token: tokE, body: {} });
    const ok = out.status >= 200 && out.status < 300;
    const note = String(out.json?.note || "");
    check("OFF-ALLOWS-NO-LOG", ok, `status=${out.status} note=${note || "(none)"}`);
  } catch (e) {
    check("OFF-ALLOWS-NO-LOG", false, String(e.message || e));
  }

  // ---------- BLOCK: enough log passes ----------
  try {
    await setRules({ workLogRule: "BLOCK", workLogMinPercent: 80, workLogPercentOf: "WORKED" });
    await ensureOpenPunch();
    await clearTodayLogs();
    await addLog(120); // covers ~2h punch at 100%
    const out = await req("POST", `${GW}/hrms/api/attendance/check-out`, { token: tokE, body: {} });
    check("BLOCK-ENOUGH-LOG", out.status >= 200 && out.status < 300, `status=${out.status} ${msg(out)}`);
  } catch (e) {
    check("BLOCK-ENOUGH-LOG", false, String(e.message || e));
  }

  // Restore product defaults (BLOCK / 80%) — avoid leaving FLAG+0 from earlier QA noise.
  try {
    const restore = {
      workLogRule: "BLOCK",
      workLogMinPercent: 80,
      workLogPercentOf: "WORKED",
      editDaysBack: original.json?.editDaysBack ?? 7,
      stepMinutes: original.json?.stepMinutes ?? 15,
      minMinutes: original.json?.minMinutes ?? 15,
      maxMinutesPerDay: original.json?.maxMinutesPerDay ?? 960,
      overPunchGraceMinutes: original.json?.overPunchGraceMinutes ?? 60,
      missingNotifyDays: original.json?.missingNotifyDays ?? 3,
    };
    await setRules(restore);
    const verify = await req("GET", `${GW}/hrms/api/work-logs/rules`, { token: tokA });
    check(
      "RULES-RESTORED",
      verify.json?.workLogRule === "BLOCK" && Number(verify.json?.workLogMinPercent) === 80,
      JSON.stringify({ rule: verify.json?.workLogRule, pct: verify.json?.workLogMinPercent }),
    );
  } catch (e) {
    check("RULES-RESTORED", false, String(e.message || e));
  }

  const failed = results.filter((r) => r.result === "FAIL").length;
  console.log(`\n${results.length - failed}/${results.length} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
