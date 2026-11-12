#!/usr/bin/env node
// Load / traffic test for the Qii HRMS backend through the API gateway (guide §14: p95 latency, errors,
// DB connections, queue backlog). Dependency-free (Node 20+). Closed-loop virtual users with think time.
//
//   node scripts/load-test.mjs --profile smoke            # 10 VUs, 30 s
//   node scripts/load-test.mjs --profile load             # ramp to 150 VUs (≈ busy morning)
//   node scripts/load-test.mjs --profile stress           # ramp to 600 VUs, no think time
//   node scripts/load-test.mjs --profile spike            # 20 → 800 VUs in 5 s
//   node scripts/load-test.mjs --profile soak --minutes 20
//   options: --gateway http://host:8090  --json out.json  --think 0.5 (seconds, mean)
//
// Only safe traffic: reads, previews and idempotent marks. Sessions are minted with the dev JWT secret
// (scripts/lib/dev-session.mjs) — never point this at production.
import { mintSession, GATEWAY, loadEnv } from "./lib/dev-session.mjs";
import fs from "node:fs";

const args = Object.fromEntries(process.argv.slice(2).reduce((acc, a, i, arr) => {
  if (a.startsWith("--")) acc.push([a.slice(2), arr[i + 1] && !arr[i + 1].startsWith("--") ? arr[i + 1] : "true"]);
  return acc;
}, []));
const G = (args.gateway || GATEWAY).replace(/\/+$/, "");
const profile = args.profile || "smoke";
const minutes = Number(args.minutes || 10);

// stages: [durationSeconds, targetVUs]; think = mean think time (s) between requests per VU
const PROFILES = {
  smoke: { stages: [[30, 10]], think: 0.5 },
  load: { stages: [[30, 50], [60, 150], [90, 150], [20, 0]], think: 0.5 },
  stress: { stages: [[30, 100], [45, 300], [60, 600], [30, 600], [15, 0]], think: 0 },
  spike: { stages: [[15, 20], [5, 800], [40, 800], [5, 20], [20, 20]], think: 0.2 },
  soak: { stages: [[30, 80], [minutes * 60, 80], [15, 0]], think: 1 },
};
const P = PROFILES[profile];
if (!P) { console.error(`unknown profile ${profile}`); process.exit(2); }
const thinkMean = args.think !== undefined ? Number(args.think) : P.think;

// Active seed employees by role (scripts/seed-qii-dev.sh)
const EMPLOYEES = [6, 7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 18, 19, 20, 21, 23, 24, 25, 26, 28, 29, 31];
const MANAGERS = [4, 5];
const HR = [2, 3];
const ADMIN = [1];
const tokens = new Map();
const tokenFor = (id) => { if (!tokens.has(id)) tokens.set(id, mintSession(id, { ttlSeconds: 6 * 3600 })); return tokens.get(id); };

const ym = new Date().toISOString().slice(0, 7);
const today = new Date().toISOString().slice(0, 10);
const year = new Date().getFullYear();

// [weight, name, method, path(id), body?]
const EMPLOYEE_MIX = [
  [12, "me", "GET", () => "/hrms/api/me"],
  [8, "dashboard.summary", "GET", () => "/hrms/api/dashboard/summary"],
  [8, "attendance.today", "GET", () => "/hrms/api/attendance/today"],
  [5, "attendance.history", "GET", () => `/hrms/api/attendance?month=${ym}`],
  [4, "attendance.hours", "GET", () => `/hrms/api/attendance/hours-summary?month=${ym}`],
  [8, "notifications.unread", "GET", () => "/notifications/api/notifications/unread-count"],
  [4, "notifications.list", "GET", () => "/notifications/api/notifications?page=0&size=20"],
  [6, "leave.my", "GET", () => "/hrms/api/leave/my"],
  [5, "leave.balance", "GET", (id) => `/hrms/api/leave-balances/employee/${id}`],
  [3, "leave.types", "GET", () => "/hrms/api/leave-types"],
  [3, "leave.preview", "POST", () => "/hrms/api/leave-applications/preview", () => ({ leaveTypeCode: "ANNUAL", startDate: `${year + 1}-01-12`, endDate: `${year + 1}-01-13`, reason: "load test preview" })],
  [4, "holidays", "GET", () => `/hrms/api/holidays?year=${year}`],
  [5, "worklogs", "GET", () => `/hrms/api/work-logs?from=${ym}-01&to=${today}`],
  [3, "tasks.assigned", "GET", () => "/hrms/api/tasks/assigned"],
  [3, "schedule.mine", "GET", () => "/hrms/api/work-schedules/mine"],
  [3, "payslips", "GET", () => "/payroll/api/payroll/me/payslips"],
  [2, "salary.me", "GET", () => "/payroll/api/payroll/me/salary"],
  [2, "checklists", "GET", () => "/hrms/api/checklists"],
  [2, "org.chart", "GET", () => "/hrms/api/org/chart"],
];
const MANAGER_MIX = [
  ...EMPLOYEE_MIX,
  [8, "approvals.pending", "GET", () => "/hrms/api/approvals/pending"],
  [5, "attendance.team", "GET", () => `/hrms/api/attendance/team?date=${today}`],
  [4, "worklogs.team", "GET", () => `/hrms/api/work-logs/team?from=${ym}-01&to=${today}&page=0&size=20`],
  [3, "dashboard.attendanceToday", "GET", () => "/hrms/api/dashboard/attendance-today"],
];
const HR_MIX = [
  ...MANAGER_MIX,
  [6, "employees.list", "GET", () => "/hrms/api/employees?page=0&size=20"],
  [4, "leave.balances.report", "GET", () => `/hrms/api/leave-balances?year=${year}`],
  [3, "payroll.runs", "GET", () => "/payroll/api/payroll/runs"],
  [3, "payroll.run", "GET", () => `/payroll/api/payroll/runs/${ym}`],
  [2, "audit", "GET", () => "/hrms/api/audit?page=0&size=20"],
  [2, "dashboard.admin", "GET", () => "/hrms/api/dashboard/admin"],
  [2, "dashboard.workhours", "GET", () => `/hrms/api/dashboard/work-hours?month=${ym}`],
];
const picker = (mix) => {
  const total = mix.reduce((s, m) => s + m[0], 0);
  return () => { let r = Math.random() * total; for (const m of mix) { if ((r -= m[0]) <= 0) return m; } return mix[0]; };
};
const pick = { emp: picker(EMPLOYEE_MIX), mgr: picker(MANAGER_MIX), hr: picker(HR_MIX) };

// ---- metrics ----
const stats = new Map(); // name -> {lat:[], codes:{}}
const timeline = []; // per-second {t, rps, err, p95}
let secBucket = { n: 0, err: 0, lat: [] };
const allLat = [];
let total = 0, errors = 0, netErrors = 0;
function record(name, ms, code) {
  let s = stats.get(name); if (!s) stats.set(name, s = { lat: [], codes: {} });
  s.lat.push(ms); s.codes[code] = (s.codes[code] || 0) + 1;
  allLat.push(ms); total++; secBucket.n++; secBucket.lat.push(ms);
  const bad = code === "ERR" || code >= 500 || code === 429;
  if (bad) { errors++; secBucket.err++; } if (code === "ERR") netErrors++;
}
const pct = (arr, p) => { if (!arr.length) return 0; const a = [...arr].sort((x, y) => x - y); return a[Math.min(a.length - 1, Math.floor(p / 100 * a.length))]; };

// ---- VU loop ----
let running = true;
let activeVUs = 0;
const vus = [];
async function vu(idx) {
  const role = idx % 20 === 0 ? "hr" : idx % 7 === 0 ? "mgr" : "emp";
  const pool = role === "hr" ? [...HR, ...ADMIN] : role === "mgr" ? MANAGERS : EMPLOYEES;
  const empId = pool[idx % pool.length];
  const token = tokenFor(empId);
  while (running && idx < activeVUs) {
    const [, name, method, pathFn, bodyFn] = pick[role]();
    const t0 = performance.now();
    let code;
    try {
      const ac = new AbortController(); const timer = setTimeout(() => ac.abort(), 30_000);
      const r = await fetch(G + pathFn(empId), {
        method, signal: ac.signal,
        headers: { Authorization: `Bearer ${token}`, Accept: "application/json", ...(bodyFn ? { "Content-Type": "application/json" } : {}) },
        body: bodyFn ? JSON.stringify(bodyFn()) : undefined,
      });
      await r.arrayBuffer(); clearTimeout(timer); code = r.status;
    } catch { code = "ERR"; }
    record(name, performance.now() - t0, code);
    if (thinkMean > 0) await new Promise((res) => setTimeout(res, -Math.log(1 - Math.random()) * thinkMean * 1000));
  }
  vus[idx] = null;
}
function setVUs(n) {
  activeVUs = n;
  for (let i = 0; i < n; i++) if (!vus[i]) vus[i] = vu(i);
}

async function sampleInfra() {
  const out = {};
  const mh = { "X-Metrics-Key": loadEnv().PLATFORM_METRICS_KEY || "" };
  try {
    for (const [svc, port] of [["people", 8081], ["leave", 8086], ["timework", 8087], ["payroll", 8082], ["notif", 8083]]) {
      const r = await fetch(`http://127.0.0.1:${port}/actuator/metrics/hikaricp.connections.active`, { headers: mh, signal: AbortSignal.timeout(2000) }).catch(() => null);
      const p = await fetch(`http://127.0.0.1:${port}/actuator/metrics/hikaricp.connections.pending`, { headers: mh, signal: AbortSignal.timeout(2000) }).catch(() => null);
      out[svc] = {
        active: r?.ok ? (await r.json()).measurements?.[0]?.value : null,
        pending: p?.ok ? (await p.json()).measurements?.[0]?.value : null,
      };
    }
  } catch { /* metrics are best-effort */ }
  return out;
}

async function main() {
  console.log(`Load test profile=${profile} gateway=${G} think=${thinkMean}s stages=${JSON.stringify(P.stages)}`);
  const start = Date.now();
  let maxPool = {};
  const ticker = setInterval(async () => {
    const t = Math.round((Date.now() - start) / 1000);
    const row = { t, vus: activeVUs, rps: secBucket.n, err: secBucket.err, p95: Math.round(pct(secBucket.lat, 95)) };
    timeline.push(row); secBucket = { n: 0, err: 0, lat: [] };
    if (t % 5 === 0) {
      const pool = await sampleInfra();
      for (const [k, v] of Object.entries(pool)) {
        maxPool[k] = { active: Math.max(maxPool[k]?.active ?? 0, v.active ?? 0), pending: Math.max(maxPool[k]?.pending ?? 0, v.pending ?? 0) };
      }
      console.log(`t=${String(t).padStart(4)}s vus=${String(row.vus).padStart(4)} rps=${String(row.rps).padStart(5)} err/s=${row.err} p95=${row.p95}ms pool=${Object.entries(pool).map(([k, v]) => `${k}:${v.active ?? "?"}/${v.pending ?? "?"}`).join(" ")}`);
    }
  }, 1000);

  let from = 0;
  for (const [dur, target] of P.stages) {
    const steps = Math.max(1, Math.round(dur));
    for (let s = 1; s <= steps; s++) {
      setVUs(Math.round(from + (target - from) * (s / steps)));
      await new Promise((r) => setTimeout(r, 1000));
    }
    from = target;
  }
  running = false; activeVUs = 0;
  await Promise.allSettled(vus.filter(Boolean));
  clearInterval(ticker);

  const elapsed = (Date.now() - start) / 1000;
  console.log(`\n=== ${profile}: ${total} requests in ${elapsed.toFixed(0)}s (${(total / elapsed).toFixed(1)} req/s), errors(5xx/429/net) ${errors} (${(100 * errors / Math.max(1, total)).toFixed(2)}%), net ${netErrors}`);
  console.log(`latency ms: p50 ${pct(allLat, 50).toFixed(0)}  p95 ${pct(allLat, 95).toFixed(0)}  p99 ${pct(allLat, 99).toFixed(0)}  max ${Math.max(0, ...allLat).toFixed(0)}`);
  console.log(`peak rps ${Math.max(0, ...timeline.map((r) => r.rps))}; max DB pool active/pending: ${JSON.stringify(maxPool)}`);
  console.log("\nendpoint                        n      p50    p95    p99   codes");
  const rows = [...stats.entries()].sort((a, b) => pct(b[1].lat, 95) - pct(a[1].lat, 95));
  for (const [name, s] of rows) {
    console.log(`${name.padEnd(30)} ${String(s.lat.length).padStart(6)} ${pct(s.lat, 50).toFixed(0).padStart(6)} ${pct(s.lat, 95).toFixed(0).padStart(6)} ${pct(s.lat, 99).toFixed(0).padStart(6)}   ${JSON.stringify(s.codes)}`);
  }
  if (args.json) {
    fs.writeFileSync(args.json, JSON.stringify({
      profile, gateway: G, total, errors, netErrors, elapsed,
      p50: pct(allLat, 50), p95: pct(allLat, 95), p99: pct(allLat, 99), maxPool, timeline,
      endpoints: Object.fromEntries(rows.map(([n, s]) => [n, { n: s.lat.length, p50: pct(s.lat, 50), p95: pct(s.lat, 95), p99: pct(s.lat, 99), codes: s.codes }])),
    }, null, 2));
  }
  // Budget gate (guide §14): p95 < 800 ms and < 1% errors under the load profile.
  if (profile === "load" || profile === "smoke") {
    const ok = pct(allLat, 95) < 800 && errors / Math.max(1, total) < 0.01;
    console.log(ok ? "\nBUDGET OK" : "\nBUDGET EXCEEDED");
    process.exit(ok ? 0 : 1);
  }
}
main().catch((e) => { console.error(e); process.exit(2); });
