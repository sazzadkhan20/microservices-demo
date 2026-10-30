/**
 * E2E: leave approval chain — reporting manager first, then HR.
 * Also: employee create requires managerId (config-driven).
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
          resolve({ status: res.statusCode, body: json, raw: b });
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
  const rows = Array.isArray(r.body) ? r.body : r.body?.content || r.body?.items || [];
  const hit = rows.find((e) => String(e.employeeCode).toUpperCase() === code.toUpperCase());
  if (!hit) throw new Error(`employee ${code} not found`);
  return Number(hit.id);
}

async function main() {
  console.log("=== leave approval chain + require manager ===\n");

  const adminTok = await tokenFor(1); // Asman SUPERADMIN
  const hrId = await empIdByCode(adminTok, "QII-0002");
  const mgrId = await empIdByCode(adminTok, "QII-0010");
  const empId = await empIdByCode(adminTok, "QII-0020");

  const hrTok = await tokenFor(hrId);
  const mgrTok = await tokenFor(mgrId);
  const empTok = await tokenFor(empId);

  ok("resolved seed people", hrId > 0 && mgrId > 0 && empId > 0, `hr=${hrId} mgr=${mgrId} emp=${empId}`);

  // ---- create employee without manager must fail ----
  const createNoMgr = await req("POST", "/hrms/api/employees", {
    token: adminTok,
    body: {
      fullName: "No Manager Test",
      email: `nomgr_${Date.now()}@qiicreative.local`,
      departmentId: 1,
      designationId: 1,
      locationId: 1,
      startDate: "2026-11-01",
      employmentType: "FULL_TIME",
      workPattern: "LOCATION_DEFAULT",
      status: "Active",
    },
  });
  ok(
    "create without managerId rejected",
    createNoMgr.status >= 400,
    `status=${createNoMgr.status} body=${String(createNoMgr.raw).slice(0, 180)}`,
  );

  // lookup real dept/desig/loc from emp
  const empMe = await req("GET", `/hrms/api/employees/${empId}`, { token: adminTok });
  const deptId = empMe.body?.departmentId;
  const desigId = empMe.body?.designationId;
  const locId = empMe.body?.locationId;

  const createWithMgr = await req("POST", "/hrms/api/employees", {
    token: adminTok,
    body: {
      fullName: "With Manager Test",
      email: `withmgr_${Date.now()}@qiicreative.local`,
      departmentId: deptId,
      designationId: desigId,
      locationId: locId,
      managerId: mgrId,
      managerEffectiveFrom: "2026-11-01",
      startDate: "2026-11-01",
      employmentType: "FULL_TIME",
      workPattern: "LOCATION_DEFAULT",
      status: "Active",
      roleCodes: ["EMPLOYEE"],
    },
  });
  ok(
    "create with managerId accepted",
    createWithMgr.status === 200 || createWithMgr.status === 201,
    `status=${createWithMgr.status} body=${String(createWithMgr.raw).slice(0, 220)}`,
  );
  const createdId = createWithMgr.body?.id;
  if (createdId) {
    ok("created employee has managerId", Number(createWithMgr.body.managerId) === mgrId);

    // cleanup soft: set inactive if possible
    await req("PUT", `/hrms/api/employees/${createdId}`, {
      token: adminTok,
      body: {
        status: "Terminated",
        exitDate: "2026-11-02",
        exitReason: "E2E cleanup",
        reassignReportsTo: mgrId,
      },
    }).catch(() => {});
  }

  // ---- leave apply by employee (within ANNUAL period; unique vs other E2E dates) ----
  const start = "2026-12-21"; // Mon (Qii Sun–Thu week)
  const end = "2026-12-21";
  const apply = await req("POST", "/hrms/api/leave-applications", {
    token: empTok,
    body: {
      leaveTypeCode: "ANNUAL",
      startDate: start,
      endDate: end,
      reason: "E2E manager-then-HR chain",
    },
  });
  ok(
    "employee leave apply",
    apply.status === 200 || apply.status === 201,
    `status=${apply.status} ${String(apply.raw).slice(0, 220)}`,
  );
  const appId = apply.body?.id;
  ok("application id present", !!appId);
  ok("starts at step 1", Number(apply.body?.currentStep) === 1, `step=${apply.body?.currentStep}`);
  const chainRoles = (apply.body?.approvals || []).map((a) => a.approverRole);
  ok("chain has MANAGER then HR", chainRoles[0] === "MANAGER" && chainRoles.includes("HR"), String(chainRoles));

  // ---- HR pending should NOT include this app at manager step ----
  const hrPending = await req("GET", "/hrms/api/approvals/pending", { token: hrTok });
  ok("HR pending HTTP 200", hrPending.status === 200, `status=${hrPending.status}`);
  const hrList = Array.isArray(hrPending.body) ? hrPending.body : [];
  ok(
    "HR pending does not include manager-step request",
    !hrList.some((a) => Number(a.id) === Number(appId)),
    `pendingIds=${hrList.map((a) => a.id).join(",")}`,
  );

  // ---- HR approve must fail ----
  const hrApprove = await req("POST", `/hrms/api/leave-applications/${appId}/approve`, {
    token: hrTok,
    body: { comment: "HR trying early" },
  });
  ok(
    "HR cannot approve manager step",
    hrApprove.status >= 400,
    `status=${hrApprove.status} ${String(hrApprove.raw).slice(0, 200)}`,
  );

  // ---- Manager pending includes it ----
  const mgrPending = await req("GET", "/hrms/api/approvals/pending", { token: mgrTok });
  const mgrList = Array.isArray(mgrPending.body) ? mgrPending.body : [];
  ok(
    "Manager pending includes request",
    mgrList.some((a) => Number(a.id) === Number(appId)),
    `pendingIds=${mgrList.map((a) => a.id).join(",")}`,
  );

  const mgrApprove = await req("POST", `/hrms/api/leave-applications/${appId}/approve`, {
    token: mgrTok,
    body: { comment: "Manager OK" },
  });
  ok(
    "Manager approves step 1",
    mgrApprove.status === 200,
    `status=${mgrApprove.status} ${String(mgrApprove.raw).slice(0, 200)}`,
  );
  ok(
    "after manager → step 2 still pending",
    mgrApprove.body?.status === "PENDING_APPROVAL" && Number(mgrApprove.body?.currentStep) === 2,
    `status=${mgrApprove.body?.status} step=${mgrApprove.body?.currentStep}`,
  );

  // ---- Now HR sees it and can approve ----
  const hrPending2 = await req("GET", "/hrms/api/approvals/pending", { token: hrTok });
  const hrList2 = Array.isArray(hrPending2.body) ? hrPending2.body : [];
  ok(
    "HR pending includes request after manager",
    hrList2.some((a) => Number(a.id) === Number(appId)),
    `pendingIds=${hrList2.map((a) => a.id).join(",")}`,
  );

  const hrApprove2 = await req("POST", `/hrms/api/leave-applications/${appId}/approve`, {
    token: hrTok,
    body: { comment: "HR final" },
  });
  ok(
    "HR approves step 2",
    hrApprove2.status === 200 && hrApprove2.body?.status === "APPROVED",
    `status=${hrApprove2.status} app=${hrApprove2.body?.status}`,
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
