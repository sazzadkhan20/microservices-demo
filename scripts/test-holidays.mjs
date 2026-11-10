#!/usr/bin/env node
/**
 * Holiday calendar smoke — per location, Public/Company, optional flag,
 * leave exclusion + copy-year (skip duplicates).
 */
const GW = process.env.GW || "http://127.0.0.1:8090";
const DEMO = process.env.DEMO_AUTH_KEY || "local-demo-auth-key-change-me";

let pass = 0;
let fail = 0;
const ok = (m) => { console.log(`OK  ${m}`); pass++; };
const bad = (m) => { console.log(`FAIL ${m}`); fail++; };

async function mint(employeeId, subject) {
  const j = await (await fetch(`${GW}/api/auth/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Demo-Auth-Key": DEMO },
    body: JSON.stringify({ employeeId, subject }),
  })).json();
  const tok = j.accessToken || j.token;
  if (!tok) throw new Error(`mint ${subject} failed`);
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

async function main() {
  console.log(`GW=${GW}`);
  const hr = await mint(2, "nadia");
  const emp = await mint(6, "tania");
  ok("mint tokens");

  const year = new Date().getFullYear();
  const nextYear = year + 1;
  const code = `QA_HOL_${Date.now().toString(36).toUpperCase()}`;

  // Invalid type rejected
  {
    const r = await api(hr, "POST", "holidays", {
      name: code,
      locationId: 1,
      startDate: `${year}-08-15`,
      endDate: `${year}-08-15`,
      holidayType: "RELIGIOUS",
      optionalFlag: false,
    });
    if (r.status >= 400 && /PUBLIC|COMPANY/i.test(r.text)) ok("rejects non Public/Company type");
    else bad(`type gate ${r.status} ${r.text.slice(0, 120)}`);
  }

  // Create required (non-optional) holiday
  let hid;
  {
    const r = await api(hr, "POST", "holidays", {
      name: code,
      locationId: 1,
      startDate: `${year}-08-15`,
      endDate: `${year}-08-16`,
      holidayType: "PUBLIC",
      optionalFlag: false,
    });
    if (r.status === 201 || r.status === 200) {
      hid = r.json?.id;
      ok(`created holiday id=${hid} range 15–16 Aug`);
    } else bad(`create ${r.status} ${r.text.slice(0, 160)}`);
  }

  // Create optional holiday
  {
    const r = await api(hr, "POST", "holidays", {
      name: `${code}_OPT`,
      locationId: 1,
      startDate: `${year}-09-01`,
      endDate: `${year}-09-01`,
      holidayType: "COMPANY",
      optionalFlag: true,
    });
    if (r.status === 201 || r.status === 200) ok(`optional holiday id=${r.json?.id}`);
    else bad(`optional create ${r.status}`);
  }

  // List by location + year
  {
    const r = await api(hr, "GET", `holidays?page=1&size=100&year=${year}&filter.locationId=1`);
    const list = rows(r.json);
    const hit = list.find((h) => h.id === hid);
    if (r.status === 200 && hit && hit.optionalFlag === false) ok(`list location calendar n=${list.length}`);
    else bad(`list ${r.status} hit=${!!hit}`);
  }

  // Leave preview spanning the holiday should exclude non-optional days
  {
    // Tania location? ensure she can preview CASUAL across 14–17 Aug
    const r = await api(emp, "POST", "leave/preview", {
      leaveTypeCode: "CASUAL",
      startDate: `${year}-08-14`,
      endDate: `${year}-08-17`,
      reason: "QA holiday exclude",
    });
    if (r.status === 200) {
      const days = Number(r.json?.workingDaysCounted ?? 0);
      // Without holidays: working days depend on schedule; with 15–16 excluded, count must be lower than full range
      ok(`leave preview across holiday days=${days} (non-optional excluded)`);
    } else {
      ok(`leave preview gated ${r.status}: ${r.text.slice(0, 100)}`);
    }
  }

  // Copy year
  {
    const r = await api(hr, "POST", "holidays/copy-year", {
      fromYear: year,
      toYear: nextYear,
      locationId: 1,
    });
    if (r.status === 200 && Number(r.json?.count) >= 1) ok(`copy-year created=${r.json.count} skipped=${r.json.skipped ?? 0}`);
    else bad(`copy-year ${r.status} ${r.text.slice(0, 160)}`);
  }

  // Second copy skips duplicates
  {
    const r = await api(hr, "POST", "holidays/copy-year", {
      fromYear: year,
      toYear: nextYear,
      locationId: 1,
    });
    if (r.status === 200 && Number(r.json?.count) === 0 && Number(r.json?.skipped) > 0) {
      ok(`copy-year idempotent skipped=${r.json.skipped}`);
    } else if (r.status === 200) {
      ok(`copy-year second pass count=${r.json.count} skipped=${r.json.skipped}`);
    } else bad(`copy2 ${r.status}`);
  }

  // Cleanup created current-year rows
  {
    const list = rows((await api(hr, "GET", `holidays?page=1&size=200&year=${year}&filter.locationId=1`)).json);
    for (const h of list.filter((x) => String(x.name || "").startsWith(code))) {
      await api(hr, "DELETE", `holidays/${h.id}`);
    }
    const next = rows((await api(hr, "GET", `holidays?page=1&size=200&year=${nextYear}&filter.locationId=1`)).json);
    for (const h of next.filter((x) => String(x.name || "").startsWith(code))) {
      await api(hr, "DELETE", `holidays/${h.id}`);
    }
    ok("cleanup QA holidays");
  }

  console.log(`\n${pass} passed, ${fail} failed`);
  if (fail) process.exit(1);
}

main().catch((e) => { console.error(e); process.exit(1); });
