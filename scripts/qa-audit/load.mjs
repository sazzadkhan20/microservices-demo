import { GW, req, token, A } from "./lib.mjs";
const H = `${GW}/hrms/api`;
const ids = [1, 2, 3, 4, 5, 6, 7, 9, 10, 12];
const toks = await Promise.all(ids.map((i) => token(i)));
const tNew = await token(1, "qa_newbiz"), tTs = await token(1, "impit_limited");

async function run(name, n, conc, fn) {
  const lat = []; let err = 0, i = 0;
  const t0 = performance.now();
  async function worker() { while (i < n) { const k = i++; const r = await fn(k); lat.push(r.ms ?? 0); if (r.status >= 500 || r.status === 0) err++; } }
  await Promise.all(Array.from({ length: conc }, worker));
  const dt = (performance.now() - t0) / 1000;
  lat.sort((a, b) => a - b);
  const p = (q) => lat[Math.min(lat.length - 1, Math.floor(q * lat.length))];
  console.log(`${name.padEnd(38)} n=${n} c=${conc} rps=${(n / dt).toFixed(0)} p50=${p(0.5)}ms p95=${p(0.95)}ms p99=${p(0.99)}ms max=${lat.at(-1)}ms 5xx=${err}`);
}
await run("GET /me (warm)", 300, 20, (k) => req("GET", `${H}/me`, { headers: A(toks[k % toks.length]) }));
await run("GET /employees list", 300, 20, (k) => req("GET", `${H}/employees?page=0&size=20`, { headers: A(toks[k % toks.length]) }));
await run("GET /attendance/today", 300, 20, (k) => req("GET", `${H}/attendance/today`, { headers: A(toks[k % toks.length]) }));
await run("GET /dashboard/work-hours month", 100, 10, () => req("GET", `${H}/dashboard/work-hours?period=month&date=2026-10-01`, { headers: A(toks[0]) }));
await run("GET /dashboard/admin", 100, 10, () => req("GET", `${H}/dashboard/admin`, { headers: A(toks[0]) }));
await run("GET /approvals/pending (mgr)", 100, 10, () => req("GET", `${H}/approvals/pending`, { headers: A(toks[3]) }));
await run("mixed 3 tenants /employees", 300, 30, (k) => req("GET", `${H}/employees?page=0&size=20`, { headers: A([toks[0], tNew, toks[1]][k % 3]) }));
await run("burst c=100 /me", 500, 100, (k) => req("GET", `${H}/me`, { headers: A(toks[k % toks.length]) }));
