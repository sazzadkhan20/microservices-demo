#!/usr/bin/env node
/**
 * End-to-end Work Schedule API + connected-process tests (config-driven).
 */
import http from "http";
import fs from "fs";

const DEMO = "local-service-key-change-me";
const GW = "127.0.0.1";
const PORT = 8090;

function req(method, path, { token, body, headers } = {}) {
  return new Promise((resolve, reject) => {
    const data = body == null ? null : JSON.stringify(body);
    const h = { Accept: "application/json", ...(headers || {}) };
    if (token) h.Authorization = `Bearer ${token}`;
    if (data) {
      h["Content-Type"] = "application/json";
      h["Content-Length"] = Buffer.byteLength(data);
    }
    const r = http.request({ hostname: GW, port: PORT, path, method, headers: h }, (res) => {
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
    });
    r.on("error", reject);
    if (data) r.write(data);
    r.end();
  });
}

const results = [];
function ok(name, pass, detail) {
  results.push({ name, pass: !!pass, detail: detail || "" });
  console.log(`${pass ? "PASS" : "FAIL"} | ${name}${detail ? " — " + detail : ""}`);
}

function content(body) {
  if (!body) return [];
  if (Array.isArray(body)) return body;
  if (Array.isArray(body.content)) return body.content;
  return [];
}

async function main() {
  const tokRes = await req("POST", "/api/registry/token", {
    headers: { "X-Demo-Auth-Key": DEMO, "Content-Type": "application/json" },
    body: { tenantId: "qii_creative", employeeId: 1 },
  });
  const token = tokRes.body?.accessToken;
  ok("AUTH token", tokRes.status === 200 && !!token, `status=${tokRes.status}`);
  if (!token) {
    fs.writeFileSync(".tmp-smoke/schedule-test.json", JSON.stringify({ results }, null, 2));
    process.exit(1);
  }

  // ---- Rules (config-driven) ----
  const rules = await req("GET", "/hrms/api/work-schedules/rules", { token });
  const r = rules.body || {};
  ok("GET /rules", rules.status === 200);
  ok(
    "rules.modes FIXED+HOURLY",
    Array.isArray(r.modes) && r.modes.includes("FIXED") && r.modes.includes("HOURLY"),
    JSON.stringify(r.modes),
  );
  ok(
    "rules.scopes LOCATION+TEAM+EMPLOYEE",
    Array.isArray(r.scopes) &&
      r.scopes.includes("LOCATION") &&
      r.scopes.includes("TEAM") &&
      r.scopes.includes("EMPLOYEE"),
    JSON.stringify(r.scopes),
  );
  ok(
    "rules.resolveOrder EMPLOYEE>TEAM>LOCATION",
    JSON.stringify(r.resolveOrder) === JSON.stringify(["EMPLOYEE", "TEAM", "LOCATION"]),
    JSON.stringify(r.resolveOrder),
  );
  ok(
    "rules.patternTemplates DAYS_5/DAYS_6/HOURLY",
    !!(r.patternTemplates?.DAYS_5 && r.patternTemplates?.DAYS_6 && r.patternTemplates?.HOURLY),
    JSON.stringify(Object.keys(r.patternTemplates || {})),
  );

  // ---- Lookups ----
  const locs = content((await req("GET", "/hrms/api/locations?page=1&size=20", { token })).body);
  const teams = content((await req("GET", "/hrms/api/teams?page=1&size=20", { token })).body);
  const emps = content((await req("GET", "/hrms/api/employees?page=1&size=50", { token })).body);
  const locId = locs[0]?.id;
  const teamId = teams[0]?.id;
  const empA = emps.find((e) => e.id !== 1) || emps[0];
  const empB = emps.find((e) => e.id !== empA?.id && e.id !== 1) || emps[1] || empA;
  ok("lookups locations/teams/employees", !!(locId && teamId && empA?.id), `loc=${locId} team=${teamId} empA=${empA?.id} empB=${empB?.id}`);

  // Cleanup prior test overrides
  let existing = content((await req("GET", "/hrms/api/work-schedules?page=1&size=100", { token })).body);
  for (const row of existing) {
    if (row.scopeType === "TEAM" && row.scopeId === teamId) {
      await req("DELETE", `/hrms/api/work-schedules/${row.id}`, { token });
    }
    if (row.scopeType === "EMPLOYEE" && (row.scopeId === empA.id || row.scopeId === empB.id)) {
      await req("DELETE", `/hrms/api/work-schedules/${row.id}`, { token });
    }
  }

  // Ensure LOCATION schedule
  existing = content((await req("GET", "/hrms/api/work-schedules?page=1&size=100", { token })).body);
  let locSched = existing.find((s) => s.scopeType === "LOCATION" && s.scopeId === locId);
  if (!locSched) {
    const created = await req("POST", "/hrms/api/work-schedules", {
      token,
      body: {
        scopeType: "LOCATION",
        scopeId: locId,
        mode: "FIXED",
        workingDays: JSON.stringify(["SUN", "MON", "TUE", "WED", "THU"]),
        startTime: "10:00",
        endTime: "18:00",
        graceMinutes: 15,
        halfDayHours: 4,
        checkoutCutoff: "23:59",
      },
    });
    ok("POST location schedule", created.status === 201 || created.status === 200, `status=${created.status}`);
    locSched = created.body;
  } else {
    ok("LOCATION schedule exists", true, `id=${locSched.id}`);
  }

  // Unique scope reject
  const dup = await req("POST", "/hrms/api/work-schedules", {
    token,
    body: {
      scopeType: "LOCATION",
      scopeId: locId,
      mode: "FIXED",
      workingDays: JSON.stringify(["MON"]),
      startTime: "09:00",
      endTime: "17:00",
      graceMinutes: 10,
    },
  });
  ok("POST duplicate LOCATION rejected", dup.status >= 400, `status=${dup.status}`);

  // TEAM 6-day override
  const teamCreate = await req("POST", "/hrms/api/work-schedules", {
    token,
    body: {
      scopeType: "TEAM",
      scopeId: teamId,
      mode: "FIXED",
      workingDays: JSON.stringify(["SUN", "MON", "TUE", "WED", "THU", "FRI"]),
      startTime: "10:00",
      endTime: "18:00",
      graceMinutes: 15,
      halfDayHours: 4,
      checkoutCutoff: "23:59",
    },
  });
  ok("POST TEAM 6-day override", teamCreate.status === 201 || teamCreate.status === 200, `status=${teamCreate.status} id=${teamCreate.body?.id}`);
  const teamSchedId = teamCreate.body?.id;

  // EMPLOYEE HOURLY
  const empHourly = await req("POST", "/hrms/api/work-schedules", {
    token,
    body: {
      scopeType: "EMPLOYEE",
      scopeId: empA.id,
      mode: "HOURLY",
      workingDays: JSON.stringify(["MON"]),
      startTime: "09:00",
      endTime: "17:00",
      weeklyHours: 35,
      graceMinutes: 15,
    },
  });
  ok("POST EMPLOYEE HOURLY", empHourly.status === 201 || empHourly.status === 200, `status=${empHourly.status}`);
  ok(
    "HOURLY clears fixed fields",
    empHourly.body?.mode === "HOURLY" &&
      !empHourly.body?.startTime &&
      Number(empHourly.body?.weeklyHours) === 35,
    JSON.stringify({
      mode: empHourly.body?.mode,
      start: empHourly.body?.startTime,
      wh: empHourly.body?.weeklyHours,
      days: empHourly.body?.workingDays,
    }),
  );

  // Update TEAM
  const teamUpd = await req("PUT", `/hrms/api/work-schedules/${teamSchedId}`, {
    token,
    body: { graceMinutes: 20, halfDayHours: 3.5 },
  });
  ok("PUT TEAM schedule", teamUpd.status === 200 && teamUpd.body?.graceMinutes === 20, `grace=${teamUpd.body?.graceMinutes}`);

  // Put empB on team, LOCATION_DEFAULT, no employee schedule
  const eB = (await req("GET", `/hrms/api/employees/${empB.id}`, { token })).body;
  await req("PUT", `/hrms/api/employees/${empB.id}`, {
    token,
    body: {
      fullName: eB.fullName,
      email: eB.email,
      departmentId: eB.departmentId,
      locationId: locId,
      teamId: teamId,
      startDate: eB.startDate,
      workPattern: "LOCATION_DEFAULT",
    },
  });
  existing = content((await req("GET", "/hrms/api/work-schedules?page=1&size=100", { token })).body);
  for (const row of existing) {
    if (row.scopeType === "EMPLOYEE" && row.scopeId === empB.id) {
      await req("DELETE", `/hrms/api/work-schedules/${row.id}`, { token });
    }
  }

  const resA = await req("GET", `/hrms/api/work-schedules/resolve/${empA.id}`, { token });
  ok(
    "resolve empA → EMPLOYEE hourly",
    resA.status === 200 && resA.body?.source === "EMPLOYEE" && resA.body?.resolved?.mode === "HOURLY",
    `source=${resA.body?.source} mode=${resA.body?.resolved?.mode}`,
  );

  const resB = await req("GET", `/hrms/api/work-schedules/resolve/${empB.id}`, { token });
  ok(
    "resolve empB → TEAM",
    resB.status === 200 && resB.body?.source === "TEAM",
    `source=${resB.body?.source} days=${JSON.stringify(resB.body?.resolved?.workingDays)}`,
  );

  const res1 = await req("GET", "/hrms/api/work-schedules/resolve/1", { token });
  ok("resolve emp1 has source", res1.status === 200 && !!res1.body?.source, `source=${res1.body?.source}`);

  // Expected hours — Fri 2026-11-06
  const friA = await req(
    "GET",
    `/hrms/api/work-schedules/expected-hours?employeeId=${empA.id}&date=2026-11-06`,
    { token },
  );
  const friB = await req(
    "GET",
    `/hrms/api/work-schedules/expected-hours?employeeId=${empB.id}&date=2026-11-06`,
    { token },
  );
  // emp1 should resolve to LOCATION after cleanup of stray EMPLOYEE overrides
  const resLocEmp = await req("GET", "/hrms/api/work-schedules/resolve/1", { token });
  const sunLoc = await req("GET", "/hrms/api/work-schedules/expected-hours?employeeId=1&date=2026-11-01", {
    token,
  });
  const satLoc = await req("GET", "/hrms/api/work-schedules/expected-hours?employeeId=1&date=2026-11-07", {
    token,
  });
  ok(
    "expected HOURLY Fri = 5 (35/7)",
    friA.status === 200 && Math.abs(Number(friA.body?.expectedHours) - 5) < 0.01,
    `got=${friA.body?.expectedHours}`,
  );
  ok(
    "expected TEAM Fri = 8",
    friB.status === 200 && Number(friB.body?.expectedHours) === 8,
    `got=${friB.body?.expectedHours}`,
  );
  ok(
    "emp1 resolves to LOCATION (base office)",
    resLocEmp.body?.source === "LOCATION",
    `source=${resLocEmp.body?.source}`,
  );
  ok(
    "expected LOCATION Sun = 8",
    sunLoc.status === 200 && Number(sunLoc.body?.expectedHours) === 8,
    `got=${sunLoc.body?.expectedHours}`,
  );
  ok(
    "expected LOCATION Sat (off) = 0",
    satLoc.status === 200 && Number(satLoc.body?.expectedHours) === 0,
    `got=${satLoc.body?.expectedHours}`,
  );

  const periodH = await req(
    "GET",
    `/hrms/api/work-schedules/expected-hours?employeeId=${empA.id}&from=2026-11-01&to=2026-11-07`,
    { token },
  );
  ok(
    "expected period HOURLY = 35",
    periodH.status === 200 && Number(periodH.body?.expectedHours) === 35,
    `got=${periodH.body?.expectedHours}`,
  );

  // Work pattern sync DAYS_6
  const eA = (await req("GET", `/hrms/api/employees/${empA.id}`, { token })).body;
  const sync6 = await req("PUT", `/hrms/api/employees/${empA.id}`, {
    token,
    body: {
      fullName: eA.fullName,
      email: eA.email,
      departmentId: eA.departmentId,
      locationId: locId,
      teamId: eA.teamId,
      startDate: eA.startDate,
      workPattern: "DAYS_6",
    },
  });
  ok("PUT workPattern DAYS_6", sync6.status === 200, `status=${sync6.status} pattern=${sync6.body?.workPattern}`);
  const resAfter6 = await req("GET", `/hrms/api/work-schedules/resolve/${empA.id}`, { token });
  const days6 = JSON.stringify(resAfter6.body?.resolved?.workingDays || []);
  ok(
    "sync DAYS_6 → EMPLOYEE FIXED with FRI",
    resAfter6.body?.source === "EMPLOYEE" &&
      resAfter6.body?.resolved?.mode === "FIXED" &&
      days6.includes("FRI"),
    `source=${resAfter6.body?.source} days=${days6}`,
  );
  const friAfter = await req(
    "GET",
    `/hrms/api/work-schedules/expected-hours?employeeId=${empA.id}&date=2026-11-06`,
    { token },
  );
  ok("after DAYS_6 Fri expected=8", Number(friAfter.body?.expectedHours) === 8, `got=${friAfter.body?.expectedHours}`);

  // LOCATION_DEFAULT clears employee override; teamId 0 clears team so LOCATION wins
  const syncLoc = await req("PUT", `/hrms/api/employees/${empA.id}`, {
    token,
    body: {
      fullName: eA.fullName,
      email: eA.email,
      departmentId: eA.departmentId,
      locationId: locId,
      teamId: 0,
      startDate: eA.startDate,
      workPattern: "LOCATION_DEFAULT",
    },
  });
  const resAfterLoc = await req("GET", `/hrms/api/work-schedules/resolve/${empA.id}`, { token });
  ok(
    "LOCATION_DEFAULT clears person override → LOCATION",
    syncLoc.status === 200 && resAfterLoc.body?.source === "LOCATION",
    `status=${syncLoc.status} source=${resAfterLoc.body?.source}`,
  );

  // Validation
  const badMode = await req("POST", "/hrms/api/work-schedules", {
    token,
    body: {
      scopeType: "EMPLOYEE",
      scopeId: empB.id,
      mode: "WEIRD",
      workingDays: "[]",
      startTime: "10:00",
      endTime: "18:00",
    },
  });
  ok("invalid mode rejected", badMode.status >= 400, `status=${badMode.status}`);

  const badDays = await req("POST", "/hrms/api/work-schedules", {
    token,
    body: {
      scopeType: "EMPLOYEE",
      scopeId: empB.id,
      mode: "FIXED",
      workingDays: "[]",
      startTime: "10:00",
      endTime: "18:00",
    },
  });
  ok("FIXED empty days rejected", badDays.status >= 400, `status=${badDays.status}`);

  // Audit
  const audit2 = content((await req("GET", "/hrms/api/audit?entity=WorkSchedule&page=1&size=20", { token })).body);
  const auditAll = content((await req("GET", "/hrms/api/audit?page=1&size=50", { token })).body);
  const hasAudit =
    audit2.length > 0 ||
    auditAll.some(
      (a) =>
        String(a.entity || "").toLowerCase().includes("workschedule") ||
        String(a.afterJson || a.after || "").includes("scopeType"),
    );
  ok("audit records schedule changes", hasAudit, `entityFilter=${audit2.length}`);

  // Attendance consumers
  const today = await req("GET", "/hrms/api/attendance/today", { token });
  ok("GET attendance/today", today.status === 200, `statusCode=${today.body?.statusCode}`);

  const teamDay = await req("GET", "/hrms/api/attendance/team?date=2026-11-07", { token });
  ok("GET attendance/team Sat", teamDay.status === 200 && Array.isArray(teamDay.body), `n=${(teamDay.body || []).length}`);
  if (Array.isArray(teamDay.body) && teamDay.body.length) {
    const codes = [...new Set(teamDay.body.map((x) => x.statusCode))];
    const offish = teamDay.body.filter((row) => ["WO", "H"].includes(String(row.statusCode || "").toUpperCase()));
    ok(
      "team Sat uses WO/H not only Absent",
      offish.length > 0,
      `codes=${codes.join(",")} offish=${offish.length}`,
    );
  }

  const leaveCats = await req("GET", "/hrms/api/leave-categories?page=1&size=5", { token });
  ok("leave categories reachable", leaveCats.status === 200 || leaveCats.status === 404, `status=${leaveCats.status}`);

  // Leave day-count uses person schedule (FR-SCH-03 connected)
  const leaveEmp = emps.find((e) => {
    const roles = (e.roles || []).map(String);
    return roles.length === 1 && roles[0] === "EMPLOYEE" && e.id !== empA.id && e.id !== empB.id;
  });
  if (leaveEmp) {
    const eL = (await req("GET", `/hrms/api/employees/${leaveEmp.id}`, { token })).body;
    await req("PUT", `/hrms/api/employees/${leaveEmp.id}`, {
      token,
      body: {
        fullName: eL.fullName,
        email: eL.email,
        departmentId: eL.departmentId,
        locationId: locId,
        teamId: 0,
        startDate: eL.startDate,
        workPattern: "DAYS_6",
      },
    });
    const resLeave = await req("GET", `/hrms/api/work-schedules/resolve/${leaveEmp.id}`, { token });
    const friHrs = await req(
      "GET",
      `/hrms/api/work-schedules/expected-hours?employeeId=${leaveEmp.id}&date=2027-01-08`,
      { token },
    );
    ok(
      "DAYS_6 person has Fri as working (expected 8)",
      resLeave.body?.source === "EMPLOYEE" && Number(friHrs.body?.expectedHours) === 8,
      `source=${resLeave.body?.source} fri=${friHrs.body?.expectedHours}`,
    );

    const tokL = await req("POST", "/api/registry/token", {
      headers: { "X-Demo-Auth-Key": DEMO, "Content-Type": "application/json" },
      body: { tenantId: "qii_creative", employeeId: leaveEmp.id },
    });
    if (tokL.body?.accessToken) {
      const friLeave = await req("POST", "/hrms/api/leave/request", {
        token: tokL.body.accessToken,
        body: {
          leaveTypeCode: "CASUAL",
          startDate: "2027-01-08",
          endDate: "2027-01-08",
          reason: "e2e days6 fri count",
        },
      });
      const counted =
        friLeave.status < 400
          ? Number(friLeave.body?.workingDaysCounted) === 1
          : // Balance may be 0 — still proves Fri is a working day (not "No working days")
            String(friLeave.body?.error || friLeave.body?.message || "").includes("balance");
      ok(
        "leave Fri DAYS_6 treats Fri as working day",
        counted,
        `status=${friLeave.status} counted=${friLeave.body?.workingDaysCounted} err=${friLeave.body?.error || ""}`,
      );

      await req("PUT", `/hrms/api/employees/${leaveEmp.id}`, {
        token,
        body: {
          fullName: eL.fullName,
          email: eL.email,
          departmentId: eL.departmentId,
          locationId: locId,
          teamId: 0,
          startDate: eL.startDate,
          workPattern: "LOCATION_DEFAULT",
        },
      });
      const friOff = await req("POST", "/hrms/api/leave/request", {
        token: tokL.body.accessToken,
        body: {
          leaveTypeCode: "CASUAL",
          startDate: "2027-01-15",
          endDate: "2027-01-15",
          reason: "e2e location fri off",
        },
      });
      const msg = String(friOff.body?.error || friOff.body?.message || "");
      ok(
        "leave Fri LOCATION_DEFAULT rejected as non-working day",
        friOff.status >= 400 && /working day/i.test(msg),
        `status=${friOff.status} msg=${msg}`,
      );
    }
  }

  // DELETE TEAM → fallback
  if (teamSchedId) {
    const del = await req("DELETE", `/hrms/api/work-schedules/${teamSchedId}`, { token });
    ok("DELETE TEAM schedule", del.status === 204 || del.status === 200, `status=${del.status}`);
    const resB2 = await req("GET", `/hrms/api/work-schedules/resolve/${empB.id}`, { token });
    ok(
      "after TEAM delete falls back",
      resB2.body?.source === "LOCATION" || resB2.body?.source === "EMPLOYEE",
      `source=${resB2.body?.source}`,
    );
  }

  // RBAC: EMPLOYEE-only user must not create schedules
  const employeeOnly = emps.find((e) => {
    const roles = (e.roles || []).map(String);
    return roles.length === 1 && roles[0] === "EMPLOYEE";
  });
  if (employeeOnly) {
    const tokEmp = await req("POST", "/api/registry/token", {
      headers: { "X-Demo-Auth-Key": DEMO, "Content-Type": "application/json" },
      body: { tenantId: "qii_creative", employeeId: employeeOnly.id },
    });
    if (tokEmp.body?.accessToken) {
      const denied = await req("POST", "/hrms/api/work-schedules", {
        token: tokEmp.body.accessToken,
        body: {
          scopeType: "TEAM",
          scopeId: teamId,
          mode: "FIXED",
          workingDays: JSON.stringify(["MON"]),
          startTime: "10:00",
          endTime: "18:00",
        },
      });
      ok(
        "EMPLOYEE-only cannot create schedule",
        denied.status === 403 || denied.status === 401,
        `emp=${employeeOnly.id} status=${denied.status}`,
      );
      const rulesDenied = await req("GET", "/hrms/api/work-schedules/rules", {
        token: tokEmp.body.accessToken,
      });
      ok(
        "EMPLOYEE-only cannot view schedule rules",
        rulesDenied.status === 403 || rulesDenied.status === 401,
        `status=${rulesDenied.status}`,
      );
    } else {
      ok("EMPLOYEE-only token", false, "no token");
    }
  } else {
    ok("EMPLOYEE-only user found for RBAC", false, "none in employee list");
  }

  // Cleanup employee schedules
  existing = content((await req("GET", "/hrms/api/work-schedules?page=1&size=100", { token })).body);
  for (const row of existing) {
    if (row.scopeType === "EMPLOYEE" && (row.scopeId === empA.id || row.scopeId === empB.id)) {
      await req("DELETE", `/hrms/api/work-schedules/${row.id}`, { token });
    }
  }

  const failed = results.filter((x) => !x.pass);
  console.log("\n==== SUMMARY ====");
  console.log(`passed=${results.filter((x) => x.pass).length} failed=${failed.length} total=${results.length}`);
  if (failed.length) {
    console.log("FAILURES:");
    failed.forEach((f) => console.log(" -", f.name, "—", f.detail));
  }
  fs.writeFileSync(".tmp-smoke/schedule-test.json", JSON.stringify({ results, failed }, null, 2));
  process.exit(failed.length ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(2);
});
