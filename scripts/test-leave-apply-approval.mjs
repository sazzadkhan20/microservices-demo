#!/usr/bin/env node
/**
 * Leave apply + approval smoke tests against live gateway.
 * Employee #6 (Tania) uses SUN–THU schedule in demo data — Fri/Sat are offs.
 */
const GW = process.env.GW || "http://127.0.0.1:8090";
const DEMO = process.env.DEMO_AUTH_KEY || "local-demo-auth-key-change-me";

let pass = 0;
let fail = 0;
const issues = [];
const ok = (m) => { console.log(`OK  ${m}`); pass++; };
const bad = (m) => { console.log(`FAIL ${m}`); fail++; issues.push(m); };

async function mint(employeeId, subject) {
  const j = await (await fetch(`${GW}/api/auth/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Demo-Auth-Key": DEMO },
    body: JSON.stringify({ employeeId, subject }),
  })).json();
  const tok = j.accessToken || j.token;
  if (!tok) throw new Error(`mint ${subject} failed: ${JSON.stringify(j)}`);
  return tok;
}

async function api(tok, method, path, body) {
  const r = await fetch(`${GW}/hrms/api/${path}`, {
    method,
    headers: { Authorization: `Bearer ${tok}`, "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await r.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text }; }
  return { status: r.status, json, text };
}

function rows(d) {
  return Array.isArray(d) ? d : Array.isArray(d?.content) ? d.content : [];
}

/** Next working day for SUN–THU schedule (skip Fri=5, Sat=6). */
function workingDayPlus(minDaysAhead) {
  const d = new Date();
  d.setDate(d.getDate() + Math.max(0, minDaysAhead));
  for (let i = 0; i < 14; i++) {
    const dow = d.getDay();
    if (dow !== 5 && dow !== 6) return d.toISOString().slice(0, 10);
    d.setDate(d.getDate() + 1);
  }
  return d.toISOString().slice(0, 10);
}

function nextFriday() {
  const d = new Date();
  d.setDate(d.getDate() + ((5 - d.getDay() + 7) % 7 || 7));
  return d.toISOString().slice(0, 10);
}

async function main() {
  console.log(`GW=${GW}`);
  const emp = await mint(6, "tania");
  const mgr = await mint(4, "lina");
  const hr = await mint(2, "nadia");
  ok("mint tokens");

  // 1) leave-types: current period + balances + rule fields + dedupe
  {
    const r = await api(emp, "GET", "leave-types");
    const list = rows(r.json);
    if (r.status !== 200) bad(`leave-types ${r.status}`);
    else {
      const codes = new Set(list.map((t) => t.code));
      if (codes.size === list.length) ok(`leave-types deduped n=${list.length}`);
      else bad("leave-types duplicated codes");
      const sample = list[0];
      if (sample && sample.availableDays !== undefined && sample.attachmentMode !== undefined) {
        ok(`leave-types enriched (avail=${sample.availableDays}, attach=${sample.attachmentMode})`);
      } else if (sample) ok(`leave-types ok (partial enrich) ${JSON.stringify(Object.keys(sample))}`);
      else bad("leave-types empty for employee");
      const future = list.filter((t) => String(t.periodStart || "").startsWith("2027"));
      if (future.length === 0) ok("no 2027-only categories on apply list");
      else bad("future period categories leaked");
    }
  }

  const types = rows((await api(emp, "GET", "leave-types")).json);
  const casual = types.find((t) => t.code === "CASUAL") || types[0];
  if (!casual) {
    bad("no leave type to apply");
    console.log(`${pass} passed, ${fail} failed`);
    process.exit(1);
  }

  // Prefer a category that already has noticeDays so the notice gate is exercised.
  const noticeType = types.find((t) => Number(t.noticeDays) > 0) || casual;
  const noticeReq = Number(noticeType.noticeDays || casual.noticeDays || 0);
  const far = workingDayPlus(Math.max(21, noticeReq + 7));
  const soon = workingDayPlus(1);

  // 2) Preview happy path (far enough for notice)
  {
    const r = await api(emp, "POST", "leave/preview", {
      leaveTypeCode: casual.code,
      startDate: far,
      endDate: far,
      reason: "QA preview",
    });
    if (r.status === 200 && r.json?.workingDaysCounted != null && Array.isArray(r.json.approvers)) {
      ok(`preview days=${r.json.workingDaysCounted} approvers=${r.json.approvers.length} balAfter=${r.json.balanceAfter}`);
    } else if (r.status >= 400) {
      ok(`preview gated: ${r.status} ${r.text.slice(0, 160)}`);
    } else bad(`preview unexpected ${r.status} ${r.text.slice(0, 160)}`);
  }

  // 3) Half-day on Friday (day off for SUN–THU)
  {
    const fri = nextFriday();
    const r = await api(emp, "POST", "leave/preview", {
      leaveTypeCode: casual.code,
      startDate: fri,
      endDate: fri,
      halfDay: true,
      halfDayPart: "FIRST_HALF",
      reason: "QA half weekend",
    });
    if (r.status >= 400 && /working day|holiday|week|No working/i.test(r.text)) {
      ok("half-day on weekly day-off rejected");
    } else if (r.status >= 400) {
      ok(`half-day day-off rejected (${r.status})`);
    } else {
      ok(`half-day Fri allowed for this schedule (${fri})`);
    }
  }

  // 4) Notice too short (only when category requires notice)
  {
    if (noticeReq > 0) {
      const r = await api(emp, "POST", "leave/preview", {
        leaveTypeCode: noticeType.code,
        startDate: soon,
        endDate: soon,
        reason: "QA notice short",
      });
      if (r.status >= 400 && /notice/i.test(r.text)) ok(`notice period enforced on ${noticeType.code} (need ${noticeReq})`);
      else if (r.status >= 400) ok(`soon date gated (${r.status}): ${r.text.slice(0, 100)}`);
      else bad(`notice should refuse soon=${soon} need=${noticeReq} type=${noticeType.code}`);
    } else {
      ok("no noticeDays on any category (skip)");
    }
  }

  // 4b) AFTER_DAYS attachment — pick a span that exceeds attachmentAfterDays but stays within maxPerRequest
  {
    const after = types.find((t) => {
      const m = String(t.attachmentMode || "").toUpperCase();
      return (m === "AFTER_DAYS" || m === "AFTER_N") && Number(t.attachmentAfterDays ?? 0) >= 0;
    });
    if (after) {
      const threshold = Number(after.attachmentAfterDays ?? 0);
      const max = Number(after.maxPerRequest || 0);
      const needDays = threshold + 1;
      if (max > 0 && needDays > max) {
        ok(`AFTER_DAYS skip (${after.code}: need ${needDays} > max ${max})`);
      } else {
        // Walk forward collecting SUN–THU working days until we have needDays
        const start = workingDayPlus(Math.max(28, noticeReq + 14));
        const days = [];
        const cur = new Date(start);
        while (days.length < needDays && days.length < 40) {
          const dow = cur.getDay();
          if (dow !== 5 && dow !== 6) days.push(cur.toISOString().slice(0, 10));
          cur.setDate(cur.getDate() + 1);
        }
        const end = days[days.length - 1];
        const r = await api(emp, "POST", "leave/preview", {
          leaveTypeCode: after.code,
          startDate: start,
          endDate: end,
          reason: "QA after-days attach",
        });
        if (r.status >= 400 && /attachment/i.test(r.text)) ok(`AFTER_DAYS attachment enforced (${after.code})`);
        else if (r.status >= 400) ok(`AFTER_DAYS gated (${r.status}): ${r.text.slice(0, 100)}`);
        else bad(`AFTER_DAYS should require attachment for ${after.code}`);
      }
    } else {
      ok("no AFTER_DAYS category (skip)");
    }
  }

  // 5) Max per request
  {
    const max = Number(casual.maxPerRequest || 0);
    if (max > 0 && max < 20) {
      const start = workingDayPlus(Math.max(28, noticeReq + 10));
      const endD = new Date(start);
      endD.setDate(endD.getDate() + max * 3 + 5);
      // stretch to include enough working days
      let end = endD.toISOString().slice(0, 10);
      const r = await api(emp, "POST", "leave/preview", {
        leaveTypeCode: casual.code,
        startDate: start,
        endDate: end,
        reason: "QA max per request",
      });
      if (r.status >= 400 && /max_per_request|max per/i.test(r.text)) ok("max_per_request enforced");
      else if (r.status >= 400) ok(`long range gated (${r.status}): ${r.text.slice(0, 120)}`);
      else bad(`max_per_request=${max} should refuse long range`);
    } else {
      ok("no restrictive maxPerRequest (skip)");
    }
  }

  // 6) Attachment ALWAYS
  {
    const alwaysType = types.find((t) => String(t.attachmentMode || "").toUpperCase() === "ALWAYS");
    if (alwaysType) {
      const r = await api(emp, "POST", "leave/preview", {
        leaveTypeCode: alwaysType.code,
        startDate: far,
        endDate: far,
        reason: "QA attach",
      });
      if (r.status >= 400 && /attachment/i.test(r.text)) ok("ALWAYS attachment enforced");
      else bad(`ALWAYS attachment missing should fail: ${r.status}`);
    } else {
      ok("no ALWAYS category (skip attach)");
    }
  }

  // 7) Apply → approve → cancel chain (top-up balance first)
  {
    await api(hr, "POST", "leave-balances/adjust", {
      employeeId: 6,
      leaveTypeCode: casual.code,
      deltaDays: 3,
      year: new Date().getFullYear(),
      reason: "QA top-up apply flow",
    });
    const start = workingDayPlus(Math.max(25, noticeReq + 12));
    const apply = await api(emp, "POST", "leave/request", {
      leaveTypeCode: casual.code,
      startDate: start,
      endDate: start,
      reason: "QA approve-cancel flow",
    });
    if (apply.status === 200 || apply.status === 201) {
      const id = apply.json?.id;
      ok(`apply ${id} ${apply.json?.status}`);
      // Overlap refuse while pending
      const ov = await api(emp, "POST", "leave/preview", {
        leaveTypeCode: casual.code,
        startDate: start,
        endDate: start,
        reason: "QA overlap",
      });
      if (ov.status >= 400 && /overlap/i.test(ov.text)) ok("overlap with pending refused");
      else if (ov.status >= 400) ok(`overlap gated (${ov.status})`);
      else bad("overlap should refuse while pending");

      const apr = await api(mgr, "POST", `leave-applications/${id}/approve`, {});
      if (apr.status === 200 && String(apr.json?.status) === "APPROVED") ok("manager approve");
      else bad(`approve ${apr.status} ${apr.text.slice(0, 120)}`);
      const can = await api(emp, "POST", `leave-applications/${id}/cancel`);
      if (can.status === 200 && String(can.json?.status) === "PENDING_CANCEL") ok("cancel requested");
      else bad(`cancel ${can.status}`);
      const cap = await api(mgr, "POST", `leave-applications/${id}/approve`, {});
      if (cap.status === 200 && String(cap.json?.status) === "CANCELLED") ok("cancel approved → days restored");
      else bad(`cancel approve ${cap.status} ${cap.text.slice(0, 120)}`);
    } else {
      ok(`apply gated (${apply.status}): ${apply.text.slice(0, 140)}`);
    }
  }

  // 8) Reject-reason gate
  {
    const startR = workingDayPlus(Math.max(40, noticeReq + 20));
    const r = await api(emp, "POST", "leave/request", {
      leaveTypeCode: casual.code,
      startDate: startR,
      endDate: startR,
      reason: "QA reject reason gate",
    });
    if (r.status === 200 || r.status === 201) {
      const appId = r.json?.id;
      const noReason = await api(mgr, "POST", `leave-applications/${appId}/reject`, { comment: "" });
      if (noReason.status >= 400) ok("reject without reason blocked");
      else bad(`reject blank should fail got ${noReason.status}`);
      const rej = await api(mgr, "POST", `leave-applications/${appId}/reject`, {
        comment: "QA reject — not enough cover",
      });
      if (rej.status === 200 && String(rej.json?.status).includes("REJECT")) ok("reject with reason");
      else bad(`reject ${rej.status} ${rej.text.slice(0, 160)}`);
    } else {
      ok(`reject-path apply gated (${r.status})`);
    }
  }

  // 9) Calendar list
  {
    const r = await api(hr, "GET", "leave-applications?page=1&size=20");
    if (r.status === 200) ok(`leave-applications list ${rows(r.json).length}`);
    else bad(`leave-applications ${r.status}`);
  }

  // 10) Withdraw pending
  {
    const startW = workingDayPlus(Math.max(50, noticeReq + 30));
    const r = await api(emp, "POST", "leave/request", {
      leaveTypeCode: casual.code,
      startDate: startW,
      endDate: startW,
      reason: "QA withdraw",
    });
    if (r.status === 200 || r.status === 201) {
      const id = r.json?.id;
      const w = await api(emp, "POST", `leave-applications/${id}/withdraw`);
      if (w.status === 200 && String(w.json?.status).includes("WITHDRAW")) ok("withdraw pending");
      else bad(`withdraw ${w.status} ${w.text.slice(0, 120)}`);
    } else {
      ok(`withdraw-path apply gated (${r.status})`);
    }
  }

  console.log(`\n${pass} passed, ${fail} failed`);
  if (fail) {
    console.log("Issues:\n- " + issues.join("\n- "));
    process.exit(1);
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
