#!/usr/bin/env node
/**
 * Qii Creative — integration + system acceptance against live Gateway.
 * Exit 1 if any FAIL (strict QA).
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const GW = process.env.GATEWAY || 'http://127.0.0.1:8090';
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.join(__dirname, '..', 'build', 'qa');
fs.mkdirSync(outDir, { recursive: true });
const results = [];

async function req(method, url, { headers = {}, body } = {}) {
  const r = await fetch(url, {
    method,
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...headers },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await r.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* raw */ }
  return { status: r.status, text, json };
}

function check(layer, id, pass, detail, extra = {}) {
  const row = { layer, id, result: pass ? 'PASS' : 'FAIL', detail, ...extra };
  results.push(row);
  console.log(`${row.result.padEnd(4)} [${layer}] ${id} — ${detail}`);
}

const token = async (employeeId) => {
  const r = await req('POST', `${GW}/api/registry/token`, {
    body: { tenantId: 'qii_creative', employeeId },
  });
  return r.json?.token || r.json?.accessToken;
};

async function main() {
  // Health
  for (const p of [8080, 8081, 8082, 8083, 8084, 8085, 8090]) {
    try {
      const r = await req('GET', `http://127.0.0.1:${p}/actuator/health`);
      check('SYS', `HEALTH-${p}`, r.status === 200, `actuator ${r.status}`);
    } catch (e) {
      check('SYS', `HEALTH-${p}`, false, String(e.message || e));
    }
  }

  const accounts = await req('GET', `${GW}/hrms/api/auth/accounts?tenantId=qii_creative`);
  check('SYS', 'SEED-ACCOUNTS', accounts.status === 200 && Array.isArray(accounts.json), `status=${accounts.status}`);
  const asman = accounts.json?.find((a) => String(a.name).includes('Asman'));
  check('SYS', 'SEED-ASMAN', !!asman && String(asman.role).includes('SUPERADMIN'), `role=${asman?.role}`);
  check('SYS', 'SEED-CAST-SIZE', (accounts.json?.length || 0) >= 12, `count=${accounts.json?.length}`);

  // Resolve stable cast IDs from account picker (seeder restarts sequences so Asman=1, but don't hard-fail if shifted)
  const byRole = (role) => accounts.json?.find((a) => String(a.role).includes(role));
  const idA = Number(byRole('SUPERADMIN')?.employeeId || 1);
  const idM = Number(byRole('MANAGER')?.employeeId || 4);
  const idE = Number([...((accounts.json || []).filter((a) => String(a.role) === 'EMPLOYEE'))].pop()?.employeeId || 12);
  const tokA = await token(idA);
  const tokE = await token(idE);
  const tokM = await token(idM);
  check('INT', 'TOKEN-ISSUE', !!tokA && !!tokE && !!tokM, `tokens minted A=${idA} M=${idM} E=${idE}`);

  const me = await req('GET', `${GW}/hrms/api/me`, { headers: { Authorization: `Bearer ${tokA}` } });
  check('INT', 'ME', me.status === 200 && me.json?.roles?.includes('SUPERADMIN'), `me=${me.status}`);

  const emps = await req('GET', `${GW}/hrms/api/employees?page=1&size=5`, {
    headers: { Authorization: `Bearer ${tokA}` },
  });
  const empEnvelope = Array.isArray(emps.json) ? 'LIST' : emps.json?.content ? 'PAGE' : 'OTHER';
  check('INT', 'EMP-PAGE-ENVELOPE', empEnvelope === 'PAGE', `expected PAGEResponse, got ${empEnvelope}`, {
    expected: 'PAGE', actual: empEnvelope,
  });

  const att = await req('GET', `${GW}/hrms/api/attendance/my?page=1&size=5`, {
    headers: { Authorization: `Bearer ${tokA}` },
  });
  check('INT', 'ATT-PAGE', att.status === 200 && !!att.json?.content, `status=${att.status}`);

  const bal = await req('POST', `${GW}/hrms/api/leave-balances/adjust`, {
    headers: { Authorization: `Bearer ${tokE}` },
    body: { employeeId: 12, leaveTypeCode: 'SICK', year: 2026, deltaDays: 1, reason: 'sys' },
  });
  check('INT', 'RBAC-BALANCE-403', bal.status === 403, `employee adjust got ${bal.status} (must be 403)`, {
    expected: '403', actual: String(bal.status),
  });

  const idor = await req('POST', `${GW}/hrms/api/leave-applications`, {
    headers: { Authorization: `Bearer ${tokE}` },
    body: {
      employeeId: 1,
      leaveTypeCode: 'CASUAL',
      startDate: '2026-12-13',
      endDate: '2026-12-13',
      reason: 'idor',
    },
  });
  check('INT', 'LEAVE-IDOR-BLOCKED', !(idor.status === 200 || idor.status === 201), `status=${idor.status}`, {
    expected: '4xx', actual: String(idor.status),
  });

  const selfLeave = await req('POST', `${GW}/hrms/api/leave/request`, {
    headers: { Authorization: `Bearer ${tokE}` },
    body: {
      leaveTypeCode: 'CASUAL',
      startDate: '2026-12-14',
      endDate: '2026-12-14',
      reason: 'self',
    },
  });
  check('INT', 'LEAVE-SELF', selfLeave.status === 200 || selfLeave.status === 201, `status=${selfLeave.status}`);

  // Manager approve pending if any
  const pending = await req('GET', `${GW}/hrms/api/approvals/pending`, {
    headers: { Authorization: `Bearer ${tokM}` },
  });
  check('INT', 'APPROVALS-LIST', pending.status === 200 && Array.isArray(pending.json), `status=${pending.status}`);

  const notif = await req('GET', `${GW}/notifications/api/notifications`, {
    headers: { Authorization: `Bearer ${tokA}` },
  });
  check('INT', 'NOTIF-GW', notif.status === 200, `status=${notif.status}`);

  const files = await req('GET', `${GW}/files/api/files/1`, {
    headers: { Authorization: `Bearer ${tokA}` },
  });
  check('INT', 'FILES-GW', files.status === 404 || files.status === 200, `routed status=${files.status}`);

  const report = await req('POST', `${GW}/reports/api/reports/attendance/monthly`, {
    headers: { Authorization: `Bearer ${tokA}` },
    body: { year: 2026, month: 10 },
  });
  check('INT', 'REPORTS-GW', report.status === 200, `status=${report.status}`);

  const roles = await req('GET', `${GW}/hrms/api/roles`, { headers: { Authorization: `Bearer ${tokA}` } });
  const roleRows = Array.isArray(roles.json) ? roles.json : (roles.json?.content || []);
  const codes = roleRows.map((r) => r.code).sort().join(',');
  check('SYS', 'ROLES-SEED', roles.status === 200 && codes === 'ADMIN,EMPLOYEE,HR,MANAGER,SUPERADMIN', `status=${roles.status} codes=${codes}`);

  const adminKey = process.env.PLATFORM_ADMIN_KEY || 'local-platform-admin-change-me';
  const tenants = await req('GET', `${GW}/api/registry/tenants`, {
    headers: { 'X-Platform-Admin-Key': adminKey },
  });
  const tenantRows = Array.isArray(tenants.json) ? tenants.json : [];
  const fileRoutes = tenantRows.filter((t) => String(t.serviceName).includes('file')).length;
  check('SYS', 'MINIO-FILE-ROUTING', tenants.status === 200 && fileRoutes >= 1, `status=${tenants.status} file routing rows=${fileRoutes}`, {
    expected: '>=1', actual: String(fileRoutes),
  });

  const meE = await req('GET', `${GW}/hrms/api/me`, { headers: { Authorization: `Bearer ${tokE}` } });
  const perms = meE.json?.permissions || [];
  const hasView = perms.includes('attendance:view');
  const hasRead = perms.includes('attendance:read');
  check('SYS', 'FE-PERM-KEY-ALIGN', hasView && hasRead, `view=${hasView} read=${hasRead} (sidebar needs :read)`, {
    expected: 'both', actual: `view=${hasView},read=${hasRead}`,
  });

  const provision = await req('POST', `${GW}/api/registry/provision`, {
    body: { tenantId: 'qa_should_not_exist', businessPack: 'qii_creative' },
  });
  check('SYS', 'PROVISION-AUTH', provision.status === 401 || provision.status === 403, `status=${provision.status}`, {
    expected: '401/403', actual: String(provision.status),
  });

  // Tasks / work logs APIs
  const tasks = await req('GET', `${GW}/hrms/api/tasks`, { headers: { Authorization: `Bearer ${tokA}` } });
  check('SYS', 'TASKS-API', tasks.status === 200, `status=${tasks.status} (work_task module)`, {
    expected: '200', actual: String(tasks.status),
  });

  const worklogs = await req('GET', `${GW}/hrms/api/work-logs`, { headers: { Authorization: `Bearer ${tokA}` } });
  check('SYS', 'WORKLOGS-API', worklogs.status === 200, `status=${worklogs.status}`, {
    expected: '200', actual: String(worklogs.status),
  });

  const locations = await req('GET', `${GW}/hrms/api/locations?page=1&size=20`, { headers: { Authorization: `Bearer ${tokA}` } });
  const locItems = Array.isArray(locations.json) ? locations.json : (locations.json?.content || []);
  check('SYS', 'LOCATIONS-API', locations.status === 200 && locItems.length >= 2,
    `status=${locations.status} n=${locItems.length}`, { expected: 'BD+AU', actual: String(locations.status) });

  const teams = await req('GET', `${GW}/hrms/api/teams`, { headers: { Authorization: `Bearer ${tokA}` } });
  check('SYS', 'TEAMS-API', teams.status === 200, `status=${teams.status}`, {
    expected: '200', actual: String(teams.status),
  });

  const holidays = await req('GET', `${GW}/hrms/api/holidays`, { headers: { Authorization: `Bearer ${tokA}` } });
  check('SYS', 'HOLIDAYS-API', holidays.status === 200, `status=${holidays.status}`, {
    expected: '200', actual: String(holidays.status),
  });

  const payrollCalc = await req('POST', `${GW}/payroll/api/payroll/calculate`, {
    headers: { Authorization: `Bearer ${tokA}` },
    body: {
      employeeId: idE,
      periodLabel: '2026-10',
      hoursWorked: 160,
      regularHours: 160,
      overtimeHours: 0,
      weekendHours: 0,
    },
  });
  check('INT', 'PAYROLL-CALC', payrollCalc.status === 200, `status=${payrollCalc.status} emp=${idE}`);

  const pkg = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'frontend', 'qii-hrms', 'package.json'), 'utf8'));
  check('UT', 'FE-TEST-SCRIPT', !!pkg.scripts?.test, pkg.scripts?.test ? 'scripts.test present' : 'scripts.test missing');

  const pass = results.filter((r) => r.result === 'PASS').length;
  const fail = results.filter((r) => r.result === 'FAIL').length;
  const summary = { pass, fail, total: results.length, generatedAt: new Date().toISOString() };
  fs.writeFileSync(path.join(outDir, 'qii-system-results.json'), JSON.stringify({ summary, results }, null, 2));
  console.log('\nSUMMARY', summary);
  if (fail > 0) process.exit(1);
}

main().catch((e) => {
  console.error(e);
  process.exit(2);
});
