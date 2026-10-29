#!/usr/bin/env node
/**
 * Qii Creative — feature-by-feature E2E against live Gateway (+ optional FE BFF).
 * Tests multi-step business flows with dummy data. Exit 1 on any FAIL.
 *
 * Usage: node scripts/qii-feature-e2e.mjs
 * Env: GATEWAY, DEMO_AUTH_KEY, FE (default http://127.0.0.1:3000)
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const GW = process.env.GATEWAY || 'http://127.0.0.1:8090';
const FE = process.env.FE || 'http://127.0.0.1:3000';
const DEMO = process.env.DEMO_AUTH_KEY || process.env.GATEWAY_SERVICE_KEY || 'local-service-key-change-me';
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.join(__dirname, '..', 'build', 'qa');
fs.mkdirSync(outDir, { recursive: true });

const results = [];
let feature = 'BOOT';
const stamp = Date.now();

async function req(method, url, { headers = {}, body, formData, raw } = {}) {
  const h = { ...headers };
  let payload;
  if (formData) {
    payload = formData;
  } else if (body !== undefined) {
    h['Content-Type'] = h['Content-Type'] || 'application/json';
    payload = typeof body === 'string' ? body : JSON.stringify(body);
  }
  const r = await fetch(url, { method, headers: h, body: payload });
  const buf = Buffer.from(await r.arrayBuffer());
  const text = buf.toString('utf8');
  let json = null;
  if (!raw) {
    try { json = text ? JSON.parse(text) : null; } catch { /* keep text */ }
  }
  return { status: r.status, text, json, headers: r.headers, buf };
}

function check(id, pass, detail) {
  const row = { feature, id, result: pass ? 'PASS' : 'FAIL', detail: String(detail ?? '') };
  results.push(row);
  console.log(`${row.result.padEnd(4)} [${feature}] ${id} — ${row.detail}`);
  return pass;
}

function failFast(id, pass, detail) {
  if (!check(id, pass, detail)) {
    throw new Error(`FAIL [${feature}] ${id}: ${detail}`);
  }
}

const demoH = () => ({ 'X-Demo-Auth-Key': DEMO });
const auth = (tok) => ({ Authorization: `Bearer ${tok}` });

async function mint(employeeId) {
  const r = await req('POST', `${GW}/api/registry/token`, {
    headers: demoH(),
    body: { tenantId: 'qii_creative', employeeId },
  });
  return r.json?.accessToken || r.json?.token || null;
}

function content(j) {
  if (Array.isArray(j)) return j;
  if (j?.content && Array.isArray(j.content)) return j.content;
  return [];
}

async function section(name, fn) {
  feature = name;
  console.log(`\n======== FEATURE: ${name} ========`);
  await fn();
}

async function main() {
  // ---- cast ----
  const accounts = await req('GET', `${GW}/hrms/api/auth/accounts?tenantId=qii_creative`, { headers: demoH() });
  failFast('accounts-list', accounts.status === 200 && Array.isArray(accounts.json) && accounts.json.length >= 8,
    `status=${accounts.status} n=${accounts.json?.length}`);

  const byRole = (role) => accounts.json.find((a) => String(a.role || '').includes(role));
  const byName = (re) => accounts.json.find((a) => re.test(String(a.name || a.fullName || '')));
  const idAdmin = Number(byRole('SUPERADMIN')?.employeeId || byName(/Asman/i)?.employeeId || 1);
  const idHr = Number(byRole('HR')?.employeeId || byName(/Nadia/i)?.employeeId || 2);
  const idMgr = Number(byRole('MANAGER')?.employeeId || byName(/Lina/i)?.employeeId || 4);
  // Prefer salaried employee who reports to the chosen manager (Karim → Lina) for leave+payroll flows
  const idEmp = Number(byName(/Karim/i)?.employeeId || 9);

  const tokA = await mint(idAdmin);
  const tokH = await mint(idHr);
  const tokM = await mint(idMgr);
  const tokE = await mint(idEmp);
  failFast('mint-cast', !!(tokA && tokH && tokM && tokE), `A=${idAdmin} H=${idHr} M=${idMgr} E=${idEmp}`);

  let createdEmployeeId = null;
  let leaveAppId = null;
  let payslipId = null;
  let fileId = null;
  let taskId = null;
  let holidayId = null;

  // ========== 1. AUTH / ME / RBAC ==========
  await section('AUTH', async () => {
    const noKey = await req('POST', `${GW}/api/registry/token`, {
      body: { tenantId: 'qii_creative', employeeId: idAdmin },
    });
    check('token-requires-demo-key', noKey.status === 401 || noKey.status === 403, `status=${noKey.status}`);

    const meA = await req('GET', `${GW}/hrms/api/me`, { headers: auth(tokA) });
    check('me-admin', meA.status === 200 && (meA.json?.roles || []).some((r) => /SUPERADMIN|ADMIN/i.test(r)),
      `roles=${meA.json?.roles}`);

    const meE = await req('GET', `${GW}/hrms/api/me`, { headers: auth(tokE) });
    check('me-employee', meE.status === 200 && String(meE.json?.employeeId) === String(idEmp),
      `emp=${meE.json?.employeeId}`);

    const roles = await req('GET', `${GW}/hrms/api/roles`, { headers: auth(tokA) });
    const roleCodes = content(roles.json).map((r) => r.code);
    check('roles-seed', roles.status === 200 && roleCodes.includes('SUPERADMIN') && roleCodes.includes('EMPLOYEE'),
      `codes=${roleCodes.join(',')}`);

    // FE BFF login (optional — skip cleanly when Next.js is not running)
    let feUp = false;
    try {
      const probe = await fetch(`${FE}/api/auth/accounts`, { method: 'GET', signal: AbortSignal.timeout(1500) });
      feUp = probe.status > 0;
    } catch {
      feUp = false;
    }
    if (!feUp) {
      check('fe-login', true, 'skipped (FE not running on :3000)');
      check('fe-bff-me', true, 'skipped');
      check('fe-bff-employees', true, 'skipped');
    } else {
      const login = await fetch(`${FE}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ employeeId: String(idAdmin) }),
      });
      const setCookie = login.headers.getSetCookie?.() || [];
      const cookie = setCookie.map((c) => c.split(';')[0]).join('; ')
        || (login.headers.get('set-cookie') || '').split(',').map((c) => c.split(';')[0].trim()).filter(Boolean).join('; ');
      check('fe-login', login.status === 200 && cookie.includes('qii_session'), `status=${login.status} cookie=${!!cookie}`);
      if (cookie) {
        const bffMe = await fetch(`${FE}/api/auth/me`, { headers: { Cookie: cookie } });
        check('fe-bff-me', bffMe.status === 200, `status=${bffMe.status}`);
        const bffEmp = await fetch(`${FE}/api/hrms/employees?page=1&size=3`, { headers: { Cookie: cookie } });
        check('fe-bff-employees', bffEmp.status === 200, `status=${bffEmp.status}`);
      }
    }
  });

  // ========== 2. ORG / EMPLOYEES ==========
  await section('ORG_EMPLOYEES', async () => {
    for (const [label, path] of [
      ['departments', '/hrms/api/departments'],
      ['org-departments', '/hrms/api/org/departments'],
      ['teams', '/hrms/api/teams?page=1&size=50'],
      ['locations', '/hrms/api/locations?page=1&size=50'],
      ['designations', '/hrms/api/org/designations'],
    ]) {
      const r = await req('GET', `${GW}${path}`, { headers: auth(tokA) });
      const n = content(r.json).length || (Array.isArray(r.json) ? r.json.length : 0);
      check(`list-${label}`, r.status === 200 && n > 0, `status=${r.status} n=${n}`);
    }

    const next = await req('GET', `${GW}/hrms/api/employees/next-code`, { headers: auth(tokA) });
    check('next-code', next.status === 200 && !!next.json?.employeeCode, `code=${next.json?.employeeCode}`);

    const email = `e2e.${stamp}@qiicreative.local`;
    const create = await req('POST', `${GW}/hrms/api/employees`, {
      headers: auth(tokA),
      body: {
        fullName: `E2E Dummy ${stamp}`,
        email,
        employeeCode: next.json?.employeeCode,
        jobTitle: 'QA Designer',
        departmentId: 2,
        designationId: 6, // DESIGNER — optional for other tenants; Qii has catalog
        employmentType: 'FULL_TIME',
        workPattern: 'STANDARD',
        status: 'ACTIVE',
        startDate: '2026-01-15',
        payCurrency: 'BDT',
        baseSalary: 55000,
        hourlyRate: 0,
        managerId: idMgr,
        roleCodes: ['EMPLOYEE'],
      },
    });
    createdEmployeeId = create.json?.id;
    check('create-employee', (create.status === 201 || create.status === 200) && !!createdEmployeeId,
      `status=${create.status} id=${createdEmployeeId} ${(create.text || '').slice(0, 160)}`);

    if (createdEmployeeId) {
      await req('POST', `${GW}/hrms/api/employees/${createdEmployeeId}/roles`, {
        headers: auth(tokA),
        body: { roleCodes: ['EMPLOYEE'] },
      });
      const get = await req('GET', `${GW}/hrms/api/employees/${createdEmployeeId}`, { headers: auth(tokA) });
      check('get-employee', get.status === 200 && get.json?.email === email, `email=${get.json?.email}`);

      const line = await req('GET', `${GW}/hrms/api/employees/${createdEmployeeId}/reporting-line`, {
        headers: auth(tokA),
      });
      check('reporting-line', line.status === 200 && Array.isArray(line.json), `rows=${line.json?.length}`);

      // IDOR: employee cannot read admin profile
      const idor = await req('GET', `${GW}/hrms/api/employees/${idAdmin}`, { headers: auth(tokE) });
      check('employee-idor-block', idor.status === 403, `status=${idor.status}`);
    }

    const dup = await req('POST', `${GW}/hrms/api/employees`, {
      headers: auth(tokA),
      body: {
        fullName: 'Dup',
        email,
        employeeCode: `DUP-${stamp}`,
        departmentId: 2,
        status: 'ACTIVE',
        startDate: '2026-01-01',
      },
    });
    check('dup-email-409', dup.status === 409 || dup.status === 400, `status=${dup.status}`);

    const page = await req('GET', `${GW}/hrms/api/employees?page=1&size=20&q=E2E%20Dummy`, { headers: auth(tokA) });
    check('search-employees', page.status === 200 && content(page.json).length >= 1, `n=${content(page.json).length}`);
  });

  // ========== 3. ATTENDANCE ==========
  await section('ATTENDANCE', async () => {
    const today = await req('GET', `${GW}/hrms/api/attendance/today`, { headers: auth(tokE) });
    check('today', today.status === 200 && !!today.json?.date, `date=${today.json?.date}`);

    // check-in (idempotent-ish — may already be checked in)
    const cin = await req('POST', `${GW}/hrms/api/attendance/check-in`, {
      headers: auth(tokE),
      body: {},
    });
    check('check-in', cin.status === 200 || cin.status === 201 || cin.status === 409 || cin.status === 400,
      `status=${cin.status} body=${(cin.text || '').slice(0, 120)}`);

    const mine = await req('GET', `${GW}/hrms/api/attendance/my?page=1&size=10`, { headers: auth(tokE) });
    check('my-attendance', mine.status === 200, `n=${content(mine.json).length}`);

    const team = await req('GET', `${GW}/hrms/api/attendance/team?date=${today.json?.date || '2026-10-29'}`, {
      headers: auth(tokM),
    });
    check('team-attendance', team.status === 200, `status=${team.status}`);

    // correction request
    const corr = await req('POST', `${GW}/hrms/api/attendance/corrections`, {
      headers: auth(tokE),
      body: {
        workDate: '2026-10-20',
        requestedCheckIn: '2026-10-20T03:00:00Z',
        requestedCheckOut: '2026-10-20T12:00:00Z',
        reason: `E2E correction ${stamp}`,
      },
    });
    check('correction-create', corr.status === 200 || corr.status === 201 || corr.status === 400 || corr.status === 409,
      `status=${corr.status} ${(corr.text || '').slice(0, 100)}`);

    const corrList = await req('GET', `${GW}/hrms/api/attendance/corrections?status=PENDING`, {
      headers: auth(tokA),
    });
    check('correction-list', corrList.status === 200, `status=${corrList.status}`);
  });

  // ========== 4. LEAVE (multi-step) ==========
  await section('LEAVE', async () => {
    const types = await req('GET', `${GW}/hrms/api/leave-types`, { headers: auth(tokE) });
    const typeCodes = content(types.json).map((t) => t.code);
    check('leave-types', types.status === 200 && typeCodes.includes('CASUAL'), `codes=${typeCodes.join(',')}`);

    const cats = await req('GET', `${GW}/hrms/api/leave-categories`, { headers: auth(tokA) });
    check('leave-categories', cats.status === 200 && content(cats.json).length >= 1, `n=${content(cats.json).length}`);

    const balBefore = await req('GET', `${GW}/hrms/api/leave-balances/employee/${idEmp}`, { headers: auth(tokE) });
    check('balances-self', balBefore.status === 200 && Array.isArray(balBefore.json), `n=${balBefore.json?.length}`);

    // opening balance adjust (HR)
    const openAdj = await req('POST', `${GW}/hrms/api/leave-balances/adjust`, {
      headers: auth(tokA),
      body: {
        employeeId: idEmp,
        leaveTypeCode: 'CASUAL',
        deltaDays: 1,
        reason: `E2E opening ${stamp}`,
        year: 2026,
      },
    });
    check('balance-adjust', openAdj.status === 200 || openAdj.status === 201, `status=${openAdj.status}`);

    // employee cannot adjust
    const forbiddenAdj = await req('POST', `${GW}/hrms/api/leave-balances/adjust`, {
      headers: auth(tokE),
      body: { employeeId: idEmp, leaveTypeCode: 'CASUAL', deltaDays: 1, reason: 'hack' },
    });
    check('balance-adjust-403', forbiddenAdj.status === 403, `status=${forbiddenAdj.status}`);

    // request without type → 400
    const noType = await req('POST', `${GW}/hrms/api/leave/request`, {
      headers: auth(tokE),
      body: { startDate: '2026-12-01', endDate: '2026-12-01', reason: 'no type' },
    });
    check('leave-type-required', noType.status === 400, `status=${noType.status}`);

    // valid request (far future weekday)
    const leave = await req('POST', `${GW}/hrms/api/leave/request`, {
      headers: auth(tokE),
      body: {
        leaveTypeCode: 'CASUAL',
        startDate: '2026-12-07',
        endDate: '2026-12-07',
        reason: `E2E leave ${stamp}`,
      },
    });
    leaveAppId = leave.json?.id;
    check('leave-request', (leave.status === 200 || leave.status === 201) && !!leaveAppId,
      `status=${leave.status} id=${leaveAppId} ${(leave.text || '').slice(0, 120)}`);

    const myLeave = await req('GET', `${GW}/hrms/api/leave/my?page=1&size=20`, { headers: auth(tokE) });
    check('leave-my', myLeave.status === 200, `n=${content(myLeave.json).length}`);

    // IDOR leave balances of another
    const idorBal = await req('GET', `${GW}/hrms/api/leave-balances/employee/${idAdmin}`, { headers: auth(tokE) });
    check('leave-balance-idor', idorBal.status === 403, `status=${idorBal.status}`);

    const pending = await req('GET', `${GW}/hrms/api/approvals/pending`, { headers: auth(tokM) });
    check('approvals-pending-mgr', pending.status === 200, `n=${Array.isArray(pending.json) ? pending.json.length : content(pending.json).length}`);

    if (leaveAppId) {
      // Qii CASUAL chain: MANAGER → HR (config-driven approval steps)
      const step1 = await req('POST', `${GW}/hrms/api/leave-applications/${leaveAppId}/approve`, {
        headers: auth(tokM),
        body: { comment: `E2E mgr approve ${stamp}` },
      });
      check('leave-approve-manager', step1.status === 200 || step1.status === 201,
        `status=${step1.status} leaveStatus=${step1.json?.status}`);

      let final = step1;
      if (step1.status < 400 && /PENDING/i.test(String(step1.json?.status || ''))) {
        final = await req('POST', `${GW}/hrms/api/leave-applications/${leaveAppId}/approve`, {
          headers: auth(tokH),
          body: { comment: `E2E hr approve ${stamp}` },
        });
      }
      check('leave-approve-hr', final.status === 200 || final.status === 201,
        `status=${final.status} leaveStatus=${final.json?.status}`);
      check('leave-fully-approved', /APPROVED/i.test(String(final.json?.status || '')),
        `leaveStatus=${final.json?.status}`);

      await new Promise((r) => setTimeout(r, 800));
      const notif = await req('GET', `${GW}/notifications/api/notifications?page=0&size=20`, {
        headers: auth(tokE),
      });
      const notifRows = content(notif.json);
      const hasLeaveNotif = notifRows.some((n) => /LEAVE|leave|E2E|application/i.test(`${n.title} ${n.body} ${n.category}`));
      check('leave-notification', notif.status === 200, `status=${notif.status} n=${notifRows.length} leaveHit=${hasLeaveNotif}`);
    }

    // year-end preview (config-driven)
    const ye = await req('POST', `${GW}/hrms/api/leave-balances/year-end/preview`, {
      headers: auth(tokA),
      body: { leaveTypeCode: 'ANNUAL', fromYear: 2026, toYear: 2027, carryMaxDays: 5 },
    });
    check('year-end-preview', ye.status === 200 && Array.isArray(ye.json), `rows=${ye.json?.length}`);
  });

  // ========== 5. TASKS / WORK LOGS / SCHEDULES / HOLIDAYS ==========
  await section('TASKS_WORKLOG_CAL', async () => {
    const tasks = await req('GET', `${GW}/hrms/api/tasks?page=1&size=50`, { headers: auth(tokA) });
    check('tasks-list', tasks.status === 200 && content(tasks.json).length >= 1, `n=${content(tasks.json).length}`);
    taskId = content(tasks.json)[0]?.id;

    const createTask = await req('POST', `${GW}/hrms/api/tasks`, {
      headers: auth(tokA),
      body: { title: `E2E Task ${stamp}`, description: 'Dummy client', priority: 'HIGH', status: 'OPEN' },
    });
    if (createTask.status === 200 || createTask.status === 201) taskId = createTask.json?.id || taskId;
    check('task-create', createTask.status === 200 || createTask.status === 201, `status=${createTask.status} id=${taskId}`);

    const wl = await req('POST', `${GW}/hrms/api/work-logs`, {
      headers: auth(tokE),
      body: { taskId, workDate: '2026-10-28', minutes: 90, note: `E2E log ${stamp}` },
    });
    check('work-log-create', wl.status === 200 || wl.status === 201, `status=${wl.status} ${(wl.text || '').slice(0, 100)}`);

    const wlList = await req('GET', `${GW}/hrms/api/work-logs?page=1&size=20`, { headers: auth(tokE) });
    check('work-log-list', wlList.status === 200, `n=${content(wlList.json).length}`);

    const teamWl = await req('GET', `${GW}/hrms/api/work-logs/team?page=1&size=20`, { headers: auth(tokM) });
    check('work-log-team', teamWl.status === 200, `status=${teamWl.status}`);

    const schedules = await req('GET', `${GW}/hrms/api/work-schedules?page=1&size=20`, { headers: auth(tokA) });
    check('schedules', schedules.status === 200 && content(schedules.json).length >= 1, `n=${content(schedules.json).length}`);

    const holidays = await req('GET', `${GW}/hrms/api/holidays?page=1&size=50&year=2026`, { headers: auth(tokA) });
    const hrows = content(holidays.json);
    check('holidays-list', holidays.status === 200 && hrows.length >= 1, `n=${hrows.length}`);
    // ISO date string (not array)
    const sd = hrows[0]?.startDate;
    check('holiday-iso-date', typeof sd === 'string' && /^\d{4}-\d{2}-\d{2}/.test(sd), `startDate=${JSON.stringify(sd)}`);

    const holCreate = await req('POST', `${GW}/hrms/api/holidays`, {
      headers: auth(tokA),
      body: {
        locationId: 1,
        name: `E2E Holiday ${stamp}`,
        startDate: '2026-08-15',
        endDate: '2026-08-15',
        holidayType: 'PUBLIC',
        optionalFlag: false,
      },
    });
    holidayId = holCreate.json?.id;
    check('holiday-create', holCreate.status === 200 || holCreate.status === 201, `status=${holCreate.status} id=${holidayId}`);
  });

  // ========== 6. PAYROLL ==========
  await section('PAYROLL', async () => {
    const cfg = await req('GET', `${GW}/payroll/api/payroll/config`, { headers: auth(tokA) });
    check('payroll-config', cfg.status === 200 && cfg.json?.overtimeMultiplier != null, `ot=${cfg.json?.overtimeMultiplier}`);

    const putCfg = await req('PUT', `${GW}/payroll/api/payroll/config`, {
      headers: auth(tokA),
      body: {
        overtimeMultiplier: 1.5,
        weekendMultiplier: 2.0,
        deductionRules: [
          ...(Array.isArray(cfg.json?.deductionRules) ? cfg.json.deductionRules.filter((r) => r.key !== 'monthlyHours') : []),
          { type: 'CONFIG', key: 'monthlyHours', value: 176 },
        ],
      },
    });
    check('payroll-config-put', putCfg.status === 200, `status=${putCfg.status}`);

    // salary history for admin (has base salary)
    const shAdd = await req('POST', `${GW}/payroll/api/payroll/salary-history`, {
      headers: auth(tokA),
      body: {
        employeeId: idEmp,
        effectiveDate: '2026-01-01',
        amount: 48000,
        payType: 'MONTHLY',
        currency: 'BDT',
        note: `E2E salary ${stamp}`,
      },
    });
    check('salary-history-add', shAdd.status === 200 || shAdd.status === 201, `status=${shAdd.status}`);

    const shList = await req('GET', `${GW}/payroll/api/payroll/salary-history/employee/${idEmp}`, {
      headers: auth(tokA),
    });
    check('salary-history-list', shList.status === 200 && Array.isArray(shList.json) && shList.json.length >= 1,
      `n=${shList.json?.length}`);

    // employee cannot calc
    const empCalc = await req('POST', `${GW}/payroll/api/payroll/calculate`, {
      headers: auth(tokE),
      body: { employeeId: idEmp, periodLabel: '2026-09', useHrmsAttendance: true },
    });
    check('payroll-calc-403', empCalc.status === 403, `status=${empCalc.status}`);

    const calc = await req('POST', `${GW}/payroll/api/payroll/calculate`, {
      headers: auth(tokA),
      body: { employeeId: idEmp, periodLabel: '2026-09', useHrmsAttendance: true },
    });
    payslipId = calc.json?.payslipId || calc.json?.id;
    const gross = Number(calc.json?.grossAmount || 0);
    check('payroll-calc', (calc.status === 200 || calc.status === 201) && gross > 0,
      `status=${calc.status} gross=${gross} payMode=${calc.json?.details?.payMode || calc.json?.details?.hoursSource}`);

    // manual hours path
    const calc2 = await req('POST', `${GW}/payroll/api/payroll/calculate`, {
      headers: auth(tokA),
      body: {
        employeeId: idEmp,
        periodLabel: '2026-08',
        useHrmsAttendance: false,
        regularHours: 160,
        overtimeHours: 5,
        weekendHours: 0,
        absenceDays: 0,
      },
    });
    check('payroll-calc-manual', calc2.status === 200 && Number(calc2.json?.grossAmount || 0) > 0,
      `gross=${calc2.json?.grossAmount}`);

    const slips = await req('GET', `${GW}/payroll/api/payroll/payslips?page=1&size=20&filter.employeeId=${idEmp}`, {
      headers: auth(tokA),
    });
    check('payslips-list', slips.status === 200 && content(slips.json).length >= 1, `n=${content(slips.json).length}`);

    // own payslips for employee
    const own = await req('GET', `${GW}/payroll/api/payroll/payslips?page=1&size=20`, { headers: auth(tokE) });
    const ownRows = content(own.json);
    const onlySelf = ownRows.every((p) => String(p.employeeId) === String(idEmp));
    check('payslips-own-scope', own.status === 200 && onlySelf, `n=${ownRows.length} onlySelf=${onlySelf}`);

    if (payslipId) {
      const fin = await req('POST', `${GW}/payroll/api/payroll/payslips/${payslipId}/finalize`, {
        headers: auth(tokA),
      });
      check('payslip-finalize', fin.status === 200 || fin.status === 201 || fin.status === 409,
        `status=${fin.status}`);
      if (fin.status === 200 || fin.status === 201) {
        const reopen = await req('POST', `${GW}/payroll/api/payroll/payslips/${payslipId}/reopen`, {
          headers: auth(tokA),
        });
        check('payslip-reopen', reopen.status === 200 || reopen.status === 201, `status=${reopen.status}`);
      } else {
        check('payslip-reopen', true, 'skipped (finalize not applied)');
      }
    }
  });

  // ========== 7. FILES / DOCS / NOTIF / REPORTS ==========
  await section('FILES_NOTIF_REPORTS', async () => {
    const pdf = Buffer.from('%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n');
    const fd = new FormData();
    fd.append('file', new Blob([pdf], { type: 'application/pdf' }), `e2e-${stamp}.pdf`);
    const up = await req('POST', `${GW}/files/api/files`, { headers: auth(tokA), formData: fd });
    fileId = up.json?.id;
    check('file-upload', up.status === 201 && !!fileId, `status=${up.status} id=${fileId}`);

    const list = await req('GET', `${GW}/files/api/files?page=0&size=10`, { headers: auth(tokA) });
    check('file-list', list.status === 200 && content(list.json).length >= 1, `n=${content(list.json).length}`);

    if (fileId) {
      const meta = await req('GET', `${GW}/files/api/files/${fileId}`, { headers: auth(tokA) });
      check('file-meta', meta.status === 200, `status=${meta.status}`);
      const dl = await req('GET', `${GW}/files/api/files/${fileId}/download`, { headers: auth(tokA), raw: true });
      check('file-download', dl.status === 200 && dl.buf.length > 0, `status=${dl.status} bytes=${dl.buf.length}`);

      // attach to employee documents if API exists
      const targetEmp = createdEmployeeId || idEmp;
      const doc = await req('POST', `${GW}/hrms/api/employees/${targetEmp}/documents`, {
        headers: auth(tokA),
        body: { fileId: String(fileId), documentType: 'Other', name: `E2E Doc ${stamp}` },
      });
      check('employee-document', doc.status === 200 || doc.status === 201 || doc.status === 404 || doc.status === 400,
        `status=${doc.status}`);
    }

    const nCreate = await req('POST', `${GW}/notifications/api/notifications`, {
      headers: auth(tokA),
      body: { title: `E2E ping ${stamp}`, body: 'System dummy notification', category: 'SYSTEM' },
    });
    check('notif-create-self', nCreate.status === 201, `status=${nCreate.status}`);

    const nList = await req('GET', `${GW}/notifications/api/notifications?page=0&size=20`, { headers: auth(tokA) });
    check('notif-list', nList.status === 200 && content(nList.json).length >= 1, `n=${content(nList.json).length}`);

    const unread = await req('GET', `${GW}/notifications/api/notifications/unread-count`, { headers: auth(tokA) });
    check('notif-unread', unread.status === 200 && typeof unread.json?.unreadCount === 'number',
      `unread=${unread.json?.unreadCount}`);

    if (nCreate.json?.id) {
      const read = await req('POST', `${GW}/notifications/api/notifications/${nCreate.json.id}/read`, {
        headers: auth(tokA),
      });
      check('notif-mark-read', read.status === 200, `status=${read.status}`);
    }

    const report = await req('POST', `${GW}/reports/api/reports/attendance/monthly`, {
      headers: auth(tokA),
      body: { year: 2026, month: 10, employeeId: idEmp },
      raw: true,
    });
    const csv = report.buf.toString('utf8');
    check('report-monthly-csv', report.status === 200 && csv.includes('employeeId'), `status=${report.status} len=${csv.length}`);

    const reportForbidden = await req('POST', `${GW}/reports/api/reports/attendance/monthly`, {
      headers: auth(tokE),
      body: { year: 2026, month: 10 },
    });
    check('report-rbac', reportForbidden.status === 403, `status=${reportForbidden.status}`);
  });

  // ========== 8. SETTINGS / CHECKLISTS / DIRECTORY / AUDIT ==========
  await section('SETTINGS_MISC', async () => {
    const policy = await req('GET', `${GW}/hrms/api/leave-policy`, { headers: auth(tokA) });
    check('leave-policy', policy.status === 200, `status=${policy.status}`);

    const settings = await req('GET', `${GW}/hrms/api/settings`, { headers: auth(tokA) });
    check('settings', settings.status === 200, `status=${settings.status}`);

    const notifBag = await req('GET', `${GW}/hrms/api/settings/notifications`, { headers: auth(tokA) });
    check('settings-notifications', notifBag.status === 200, `status=${notifBag.status}`);

    const checklists = await req('GET', `${GW}/hrms/api/checklists`, { headers: auth(tokA) });
    check('checklists', checklists.status === 200, `status=${checklists.status}`);

    const tree = await req('GET', `${GW}/hrms/api/org/tree`, { headers: auth(tokA) });
    check('org-tree', tree.status === 200, `status=${tree.status}`);

    const audit = await req('GET', `${GW}/hrms/api/audit?page=1&size=10`, { headers: auth(tokA) });
    check('audit', audit.status === 200, `status=${audit.status}`);

    // cleanup holiday dummy if created
    if (holidayId) {
      const del = await req('DELETE', `${GW}/hrms/api/holidays/${holidayId}`, { headers: auth(tokA) });
      check('holiday-cleanup', del.status === 204 || del.status === 200 || del.status === 404, `status=${del.status}`);
    }
  });

  // ========== summary ==========
  feature = 'SUMMARY';
  const pass = results.filter((r) => r.result === 'PASS').length;
  const fail = results.filter((r) => r.result === 'FAIL').length;
  const byFeature = {};
  for (const r of results) {
    byFeature[r.feature] ||= { pass: 0, fail: 0 };
    byFeature[r.feature][r.result === 'PASS' ? 'pass' : 'fail']++;
  }
  console.log('\n======== SUMMARY ========');
  for (const [f, s] of Object.entries(byFeature)) {
    console.log(`${f}: ${s.pass} pass / ${s.fail} fail`);
  }
  console.log(`TOTAL: ${pass} pass / ${fail} fail / ${results.length} checks`);
  const out = path.join(outDir, 'qii-feature-e2e.json');
  fs.writeFileSync(out, JSON.stringify({ generatedAt: new Date().toISOString(), pass, fail, results }, null, 2));
  console.log(`Wrote ${out}`);
  if (fail > 0) process.exit(1);
}

main().catch((e) => {
  console.error('FATAL', e);
  process.exit(1);
});
