#!/usr/bin/env node
/**
 * Deep Qii business-logic / corner-case suite (gateway).
 * Covers leave chain, payroll locks, attendance,
 * files/notif/report services, RBAC corners. Exit 1 on FAIL.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const GW = process.env.GATEWAY || 'http://127.0.0.1:8090';
const DEMO = process.env.DEMO_AUTH_KEY || 'local-service-key-change-me';
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.join(__dirname, '..', 'build', 'qa');
fs.mkdirSync(outDir, { recursive: true });

const results = [];
let area = 'BOOT';
const stamp = Date.now();

async function req(method, url, { headers = {}, body, formData, raw } = {}) {
  const h = { ...headers };
  let payload;
  if (formData) payload = formData;
  else if (body !== undefined) {
    h['Content-Type'] = h['Content-Type'] || 'application/json';
    payload = typeof body === 'string' ? body : JSON.stringify(body);
  }
  const r = await fetch(url, { method, headers: h, body: payload });
  const buf = Buffer.from(await r.arrayBuffer());
  const text = buf.toString('utf8');
  let json = null;
  if (!raw) {
    try { json = text ? JSON.parse(text) : null; } catch { /* keep */ }
  }
  return { status: r.status, text, json, buf };
}

function check(id, pass, detail) {
  const row = { area, id, result: pass ? 'PASS' : 'FAIL', detail: String(detail ?? '') };
  results.push(row);
  console.log(`${row.result.padEnd(4)} [${area}] ${id} — ${row.detail}`);
  return pass;
}

const demoH = () => ({ 'X-Demo-Auth-Key': DEMO });
const auth = (tok) => ({ Authorization: `Bearer ${tok}` });

async function mint(employeeId) {
  const r = await req('POST', `${GW}/api/auth/token`, {
    headers: demoH(),
    body: { employeeId },
  });
  return r.json?.accessToken || r.json?.token || null;
}

function content(j) {
  if (Array.isArray(j)) return j;
  if (j?.content && Array.isArray(j.content)) return j.content;
  return [];
}

async function section(name, fn) {
  area = name;
  console.log(`\n======== ${name} ========`);
  await fn();
}

async function main() {
  const accounts = await req('GET', `${GW}/hrms/api/auth/accounts`, { headers: demoH() });
  const cast = accounts.json || [];
  const byRole = (role) => cast.find((a) => String(a.role || '').includes(role));
  const byName = (re) => cast.find((a) => re.test(String(a.name || a.fullName || '')));
  const idA = Number(byRole('SUPERADMIN')?.employeeId || 1);
  const idH = Number(byRole('HR')?.employeeId || 2);
  const idM = Number(byRole('MANAGER')?.employeeId || byName(/Lina/i)?.employeeId || 4);
  const idE = Number(byName(/Karim/i)?.employeeId || 9);

  const tokA = await mint(idA);
  const tokH = await mint(idH);
  const tokM = await mint(idM);
  const tokE = await mint(idE);
  check('cast-mint', !!(tokA && tokH && tokM && tokE),
    `A=${idA} H=${idH} M=${idM} E=${idE}`);

  // ---- Leave multi-step + reject + balance ----
  await section('LEAVE_CHAIN', async () => {
    const types = await req('GET', `${GW}/hrms/api/leave-types`, { headers: auth(tokE) });
    const codes = content(types.json).map((t) => t.code);
    check('leave-types-config', types.status === 200 && codes.includes('CASUAL') && codes.includes('ANNUAL'),
      `codes=${codes.join(',')}`);

    const bal0 = await req('GET', `${GW}/hrms/api/leave-balances/employee/${idE}`, { headers: auth(tokE) });
    check('balance-self', bal0.status === 200 && Array.isArray(bal0.json), `n=${bal0.json?.length}`);

    // IDOR
    const idor = await req('GET', `${GW}/hrms/api/leave-balances/employee/${idA}`, { headers: auth(tokE) });
    check('balance-idor-403', idor.status === 403, `status=${idor.status}`);

    // Top-up CASUAL (config-driven adjust) — prior E2E runs can exhaust seed balance
    const adj = await req('POST', `${GW}/hrms/api/leave-balances/adjust`, {
      headers: auth(tokA),
      body: {
        employeeId: idE,
        leaveTypeCode: 'CASUAL',
        deltaDays: 5,
        reason: `deep top-up ${stamp}`,
        year: 2027,
      },
    });
    check('balance-topup', adj.status === 200 || adj.status === 201,
      `status=${adj.status} ${(adj.text || '').slice(0, 120)}`);

    // Request far-future casual day
    const start = '2027-03-15';
    const leave = await req('POST', `${GW}/hrms/api/leave/request`, {
      headers: auth(tokE),
      body: {
        startDate: start,
        endDate: start,
        leaveTypeCode: 'CASUAL',
        reason: `deep-logic ${stamp}`,
      },
    });
    const leaveId = leave.json?.id;
    check('leave-create', (leave.status === 200 || leave.status === 201) && !!leaveId,
      `status=${leave.status} id=${leaveId} body=${(leave.text || '').slice(0, 120)}`);

    if (leaveId) {
      // Employee cannot self-approve
      const self = await req('POST', `${GW}/hrms/api/leave-applications/${leaveId}/approve`, {
        headers: auth(tokE),
        body: { comment: 'self' },
      });
      check('leave-self-approve-blocked', self.status === 403 || self.status === 400,
        `status=${self.status}`);

      // Manager approve step
      const mApp = await req('POST', `${GW}/hrms/api/leave-applications/${leaveId}/approve`, {
        headers: auth(tokM),
        body: { comment: 'mgr ok' },
      });
      check('leave-mgr-approve', mApp.status === 200 || mApp.status === 201,
        `status=${mApp.status} st=${mApp.json?.status}`);

      // Second leave for reject path
      const leave2 = await req('POST', `${GW}/hrms/api/leave/request`, {
        headers: auth(tokE),
        body: {
          startDate: '2027-03-16',
          endDate: '2027-03-16',
          leaveTypeCode: 'CASUAL',
          reason: `deep-reject ${stamp}`,
        },
      });
      const lid2 = leave2.json?.id;
      if (lid2) {
        const rej = await req('POST', `${GW}/hrms/api/leave-applications/${lid2}/reject`, {
          headers: auth(tokM),
          body: { comment: 'coverage gap' },
        });
        check('leave-mgr-reject',
          (rej.status === 200 || rej.status === 201) && /REJECT/i.test(String(rej.json?.status || '')),
          `status=${rej.status} st=${rej.json?.status}`);
      } else {
        check('leave-mgr-reject', false, `create2 status=${leave2.status}`);
      }

      // HR final approve if still pending
      if (/PENDING|WAITING|HR/i.test(String(mApp.json?.status || 'PENDING'))) {
        const hr = await req('POST', `${GW}/hrms/api/leave-applications/${leaveId}/approve`, {
          headers: auth(tokH),
          body: { comment: 'hr ok' },
        });
        check('leave-hr-approve', hr.status === 200 || hr.status === 201,
          `status=${hr.status} st=${hr.json?.status}`);
      } else {
        check('leave-hr-approve', true, `already ${mApp.json?.status}`);
      }
    }
  });

  // ---- Attendance corners ----
  await section('ATTENDANCE', async () => {
    const today = await req('GET', `${GW}/hrms/api/attendance/today`, { headers: auth(tokE) });
    check('today', today.status === 200, `date=${today.json?.date} status=${today.json?.status}`);

    const cin = await req('POST', `${GW}/hrms/api/attendance/check-in`, {
      headers: auth(tokE),
      body: {},
    });
    check('check-in-idempotent',
      cin.status === 200 || cin.status === 201 || cin.status === 409 || cin.status === 400,
      `status=${cin.status}`);

    const team = await req('GET', `${GW}/hrms/api/attendance/team?page=1&size=20`, { headers: auth(tokM) });
    check('team-attendance-mgr', team.status === 200, `n=${content(team.json).length}`);

    const teamEmp = await req('GET', `${GW}/hrms/api/attendance/team?page=1&size=5`, { headers: auth(tokE) });
    check('team-attendance-emp-scope',
      teamEmp.status === 200 || teamEmp.status === 403,
      `status=${teamEmp.status}`);
  });

  // ---- Payroll corners ----
  // Payroll moved to month runs (docs/PAYROLL_SPEC.md). Full money/finalise/reopen coverage lives in
  // scripts/test-payroll-e2e.mjs — this section only checks RBAC and read paths (no data written).
  await section('PAYROLL', async () => {
    const settings = await req('GET', `${GW}/payroll/api/payroll/settings`, { headers: auth(tokA) });
    check('payroll-settings', settings.status === 200 && settings.json?.absenceRule != null, `status=${settings.status}`);
    const runs = await req('GET', `${GW}/payroll/api/payroll/runs`, { headers: auth(tokA) });
    check('payroll-runs-list', runs.status === 200, `status=${runs.status}`);
    const empRuns = await req('GET', `${GW}/payroll/api/payroll/runs`, { headers: auth(tokE) });
    check('payroll-runs-emp-403', empRuns.status === 403, `status=${empRuns.status}`);
    const empCalc = await req('POST', `${GW}/payroll/api/payroll/runs/2026-09/calculate`, { headers: auth(tokE) });
    check('payroll-calc-emp-403', empCalc.status === 403, `status=${empCalc.status}`);
    const own = await req('GET', `${GW}/payroll/api/payroll/me/payslips`, { headers: auth(tokE) });
    check('payroll-own-payslips', own.status === 200 && Array.isArray(own.json), `status=${own.status}`);
    const other = await req('GET', `${GW}/payroll/api/payroll/salary/employees/1`, { headers: auth(tokE) });
    check('salary-other-403', other.status === 403, `status=${other.status}`);
  });

  await section('SATELLITES', async () => {
    const fd = new FormData();
    fd.append('file', new Blob(['%PDF-1.4 deep'], { type: 'application/pdf' }), `deep-${stamp}.pdf`);
    const up = await req('POST', `${GW}/files/api/files`, { headers: auth(tokA), formData: fd });
    const fileId = up.json?.id;
    check('file-upload', up.status === 201 && !!fileId, `status=${up.status} id=${fileId}`);

    if (fileId) {
      const dl = await req('GET', `${GW}/files/api/files/${fileId}/download`, {
        headers: auth(tokA),
        raw: true,
      });
      check('file-download', dl.status === 200 && dl.buf.length > 0, `bytes=${dl.buf.length}`);

      // Cross-employee download blocked
      const cross = await req('GET', `${GW}/files/api/files/${fileId}/download`, {
        headers: auth(tokE),
        raw: true,
      });
      check('file-cross-employee-blocked',
        cross.status === 404 || cross.status === 403 || cross.status === 400,
        `status=${cross.status}`);
    }

    const nCreate = await req('POST', `${GW}/notifications/api/notifications`, {
      headers: { ...auth(tokA), 'X-Internal-Key': 'local-internal-key-change-me' },
      body: {
        employeeId: idE,
        title: `Deep ping ${stamp}`,
        body: 'logic suite',
        category: 'SYSTEM',
      },
    });
    check('notif-create', nCreate.status === 201 || nCreate.status === 200,
      `status=${nCreate.status}`);

    const report = await req('POST', `${GW}/reports/api/reports/attendance/monthly`, {
      headers: auth(tokA),
      body: { year: 2027, month: 1, employeeId: idE },
      raw: true,
    });
    const csv = report.buf.toString('utf8');
    check('report-csv', report.status === 200 && csv.includes('employeeId'),
      `status=${report.status} len=${csv.length}`);

    const exports = await req('GET', `${GW}/reports/api/reports/exports`, { headers: auth(tokA) });
    check('report-export-archive',
      exports.status === 200 && content(exports.json).length >= 1,
      `n=${content(exports.json).length}`);

    const reportEmp = await req('POST', `${GW}/reports/api/reports/attendance/monthly`, {
      headers: auth(tokE),
      body: { year: 2027, month: 1 },
      raw: true,
    });
    check('report-rbac-emp',
      reportEmp.status === 403 || reportEmp.status === 200,
      `status=${reportEmp.status}`);
  });

  // ---- Org / RBAC config-driven ----
  await section('ORG_RBAC', async () => {
    const roles = await req('GET', `${GW}/hrms/api/roles`, { headers: auth(tokA) });
    const codes = content(roles.json).map((r) => r.code);
    check('roles-config',
      roles.status === 200 && ['SUPERADMIN', 'HR', 'MANAGER', 'EMPLOYEE'].every((c) => codes.includes(c)),
      `codes=${codes.join(',')}`);

    const meE = await req('GET', `${GW}/hrms/api/me`, { headers: auth(tokE) });
    const perms = meE.json?.permissions || [];
    check('employee-no-star',
      meE.status === 200 && !perms.includes('*:*') && !perms.some((p) => /^payroll:run$/i.test(p)),
      `permsSample=${perms.slice(0, 8).join(',')}`);

    const depts = await req('GET', `${GW}/hrms/api/departments`, { headers: auth(tokA) });
    check('departments', depts.status === 200 && content(depts.json).length >= 1,
      `n=${content(depts.json).length}`);

    const schedules = await req('GET', `${GW}/hrms/api/work-schedules`, { headers: auth(tokA) });
    check('work-schedules-config',
      schedules.status === 200 && content(schedules.json).length >= 1,
      `n=${content(schedules.json).length}`);
  });

  // ---- gRPC RBAC cold path (payroll permissions) ----
  await section('GRPC_RBAC', async () => {
    // Burst payroll config reads — previously flaked with EntityManager
    let ok = 0;
    let last = 0;
    for (let i = 0; i < 8; i++) {
      const r = await req('GET', `${GW}/payroll/api/payroll/settings`, { headers: auth(tokA) });
      last = r.status;
      if (r.status === 200) ok++;
    }
    check('payroll-rbac-burst', ok === 8, `ok=${ok}/8 last=${last}`);
  });

  area = 'SUMMARY';
  const pass = results.filter((r) => r.result === 'PASS').length;
  const fail = results.filter((r) => r.result === 'FAIL').length;
  const summary = { pass, fail, total: results.length, generatedAt: new Date().toISOString() };
  fs.writeFileSync(path.join(outDir, 'qii-deep-logic-results.json'), JSON.stringify({ summary, results }, null, 2));
  console.log('\nSUMMARY', summary);
  if (fail > 0) process.exit(1);
}

main().catch((e) => {
  console.error('FATAL', e);
  process.exit(2);
});
