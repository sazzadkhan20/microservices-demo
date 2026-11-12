# System test & production hardening report

**Date:** 2026-11-12 · **Baseline:** `main` @ `1a2c971` · **Guide:** HRMS Microservices Developer Guide v1.0
**Scope:** whole system (8 Spring Boot services, gateway, Next.js BFF) — security, RBAC, resilience under
traffic, database/query/API performance, CI/CD. **Out of scope by instruction:** business rules (unchanged),
UI design (unchanged), OIDC login flow (skipped).

Every change below was made without altering a business rule: where a code path was optimised, the result
is proven identical by a test (period expected hours, directory views, RBAC reads, leave impact rule).

---

## 1. Findings and fixes

### Security

| # | Finding (evidence) | Fix |
|---|---|---|
| S1 | **IDOR — employee documents.** Any employee could list (and attach to / unlink from) another employee's document metadata: `GET /hrms/api/employees/15/documents` as employee 6 → 200 with rows. The endpoint checked the feature (`employees:view`, which every employee holds at OWN scope) but not the record scope. | Record scope enforced on list / attach / access-log / delete (self, `employees:*` data scope — manager → team, HR → all — or an explicit `documents:*` grant). `EmployeeDocumentScopeTest`, probe checks. |
| S2 | **Operational endpoints exposed.** `/payroll/actuator/prometheus` was publicly reachable through the gateway route; gateway `/actuator/prometheus` and `/actuator/metrics` were public. | Gateway never proxies `…/actuator/…` (404). Every service and the gateway serve `/actuator/health/**` openly and everything else only with `Authorization: Bearer $PLATFORM_METRICS_KEY` (or loopback when no key is set). |
| S3 | **Internal details leaked in errors.** Unhandled errors returned `{"error": <exception message>, "cause": <root cause>}` — SQL, class names (`Unrecognized field … (class com.platform.notification…)`). Malformed JSON returned 500. | Shared `PlatformErrorAdvice` + `PlatformErrors`: client mistakes → 4xx, unexpected → `500 {"error":"Unexpected error — reference <correlationId>", code, correlationId}`; detail only in the log. Null-message NPE in notification/report handlers fixed. |
| S4 | **Offboarded employee sessions.** A session for a terminated employee was still honoured until JWT expiry when the Redis revocation entry was missing/expired. Employee-wide revocation also blocked *new* logins for 7 days (re-hire within a week impossible). | Gateway `EmployeeSessionGuard`: every session is checked against People's side-effect-free `session-status` (30 s cache, last-known value for 10 min if People is down, 503 — not logout — with no answer). Employee revocation now stores the revocation instant: sessions issued before it are refused, a later login works (legacy entries still revoke all). |
| S5 | **Salary in the general directory.** `/api/internal/employees` (used by Leave, Time&Work, Documents) carried `baseSalary`/`hourlyRate`; no consumer uses them (audit MSG-08). | Removed from the directory view; Payroll keeps its own pay contract. |
| S6 | **Vulnerable / unsupported framework versions.** Spring Boot 3.3.5 (OSS support ended), Tomcat 10.1.31 (partial-PUT CVE-2025-24813, DoS CVE-2025-31650), Spring Cloud Gateway 4.1.5 (forwarded-header CVE-2025-41235), Netty HTTP/2 DoS (CVE-2025-55163) in Reactor and in grpc-netty-shaded, nimbus-jose-jwt 9.37.3 (CVE-2025-53864). | Spring Boot **3.5.16**, Spring Cloud **2025.0.3** (Gateway 4.3, properties migrated to `server.webflux`), springdoc 2.8.17, gRPC **1.85.1**, protobuf 3.25.9, nimbus 9.37.4, jjwt 0.12.7, MinIO client 8.5.17, BOM-managed Caffeine/HikariCP. Dependabot + Trivy + CodeQL keep it that way. |
| S7 | Frontend had no browser security headers; image ran as root. | CSP (same-origin, no framing, no plugins), `X-Frame-Options: DENY`, `nosniff`, strict referrer, permissions policy, COOP, HSTS in production. Image runs as `node`, read-only root files, healthcheck. |
| S8 | Payroll's "fresh authority" lookup for privileged writes would have been served from People's new RBAC cache. | gRPC permission lookup always reads the database (`permissionKeysForFresh`). |
| S9 | After a People restart, Payroll's first gRPC permission call failed fast (503) while the channel reconnected. | Idempotent permission reads use `withWaitForReady()` within the 5 s deadline. |

### Resilience under traffic (the system no longer collapses)

| # | Finding (baseline stress test) | Fix |
|---|---|---|
| R1 | At ≈130 req/s People's pool saturated (10 active / **202 waiting**), every service that asks People for permissions/directory timed out, errors surfaced as misleading **409/500**, and the system kept failing **20 s after the load stopped**. | See R2–R7. Same stress test now: 0 × 500, overload answered with `503 + Retry-After`, recovery immediate (first request after the ramp-down: 173 ms). |
| R2 | RBAC recomputed per request: 4–5 queries for every permission check, every `/me`, every other service's authz lookup. | People `RbacCache` (Caffeine, 10 s TTL, cleared after commit of any role / permission / assignment / employee change; reads in transactions that change RBAC always hit the DB). |
| R3 | `/me` called Time&Work **inside a DB transaction** (connection held while waiting — guide §7 "no long DB transaction may wait on a remote call"). | DB work in a short read-only transaction, remote call after it, successful schedule-mode lookups cached 30 s. |
| R4 | Six services built `RestClient` with **no read timeout**; a shared singleton builder leaked default headers between clients. | `ResilientHttp` (connect 3 s / read 10 s, env-tunable) as a prototype bean in every service. |
| R5 | People directory lists (team / manager / all) uncached; concurrent misses stampeded People; no protection when People is down. | `PeopleDirectoryClient`: list caching (invalidated by `people.employee.upserted`), single-flight, stale-on-error (≤5 min), circuit breaker (5 failures → fail fast 10 s), `DependencyUnavailableException` → 503. |
| R6 | No edge protection or deadlines at the gateway; idle pooled connections outlived Tomcat keep-alive. | Redis token-bucket rate limit per signed-in employee (20/s, burst 60 → 429), connect 3 s / response 30 s (files 120 s), bounded pool with idle eviction below Tomcat keep-alive, graceful shutdown. |
| R7 | Pool exhaustion and DB lock waits had no bound. | Per-connection `statement_timeout` 60 s, `lock_timeout` 15 s, `idle_in_transaction_session_timeout` 120 s (runtime role only); exhaustion / resource failures → 503 `SERVICE_BUSY`; Tomcat worker / accept budgets explicit; Leave & Time&Work pools 10 → 15. |

### Database, query and API optimisation (no business rule changed)

| Endpoint | Before | After | What changed |
|---|---|---|---|
| `GET /attendance/today` | ≈300 ms; 1 HTTP call to Leave **per calendar day** of week + month inside a transaction | ≈150 ms | Period expected hours: schedule, holidays and approved leave fetched **once per period** (`PeriodLookups`, `LeaveClient.leaveImpacts`) — `periodBatchesLeaveLookupAndMatchesPerDaySum` proves the same totals. |
| `GET /work-logs/team` | 432 queries per request (work log + attendance per member per day) | 2 queries per member | Range queries grouped by day; tasks memoised per request. |
| `GET /attendance/team` | 1 work-log query per member, duplicate Leave calls | 1 query; Leave calls memoised per request | Leave data cannot change during a Time&Work request. |
| `GET /checklists` | ≈600 ms, ≈206 RBAC queries | 35 ms p50 under load | RBAC cache. |
| `GET /api/internal/employees` (People) | 3 lookups per employee row | 3 lookups per list | Reference data preloaded once. |
| `GET /leave-types` (Leave) | N HTTP calls to Time&Work (one per category) | 1 per request | Per-request memo (`RequestMemo`) of schedule / holiday lookups. |
| All JPA services | — | — | `default_batch_fetch_size=32`, `in_clause_parameter_padding`. |

### Observability & operations

* Correlation id now on **every log line** (MDC), forwarded on every service-to-service HTTP call and restored
  from every consumed RabbitMQ message (guide §9). Production log format ECS JSON via `LOG_FORMAT`.
* Latency histograms + SLO buckets for `http.server.requests`, Tomcat thread metrics, `application` tag.
* Docker: container-aware JVM heap, exit on OOM, UTC; deploy stack read-only, `no-new-privileges`, caps
  dropped, CPU/memory limits, health-gated.
* `docs/RUNBOOK.md` (signals, release/rollback, identity/broker/DB/People outage, stuck payroll, malicious
  upload, credential exposure, traffic spikes). `docs/CICD.md` (tag-driven release).

### Frontend (design untouched)

* Real bug: the attendance **"This month"** button referenced an undefined variable (crashed on click) —
  now uses the same location-zone month as the initial view.
* Real bug: employee page *Leave* tab called hooks conditionally (could crash when permissions change).
* 15 TypeScript errors that blocked `next build` (production image) fixed with type-only changes; 0 lint
  errors (the React-compiler advisory `set-state-in-effect` is a warning — 14 effects, listed as debt).

---

## 2. Load and traffic evidence (`scripts/load-test.mjs`, single laptop running all 8 JVMs + 3 Postgres)

| Run | Requests | Throughput (avg / peak req/s) | Errors | p50 / p95 / p99 (ms) | 500s | Max pool waiting |
|---|---|---|---|---|---|---|
| smoke **before** | 250 | 7.9 / 21 | 0 % | 39 / 700 / 1634 | 0 | people 0 |
| smoke **after** | 283 | 8.6 / 21 | 0 % | 27 / 356 / 603 | 0 | 0 |
| stress 600 VUs, zero think time **before** | 9 156 | 43.9 / 131 | 19.9 % (+3 628 mislabelled 409) | 8 005 / 30 003 / 30 012 | 1 106 | **people 202** |
| stress **after** | 18 420 | **101.7 / 303** | 19.9 % — all clean `503 Retry-After` | **237** / 15 052 / 21 730 | **0** | people 0; leave 156, time&work 185 |
| load 150 VUs (realistic mix) **final** | 31 621 | **156.9** | **0 %** | **43 / 776 / 2 084** | 0 | — (budget gate passed) |
| spike 20 → 800 VUs in 5 s **final** | 6 710 | 77.7 | 31.8 % — 2 114 × 503 + 22 client timeouts | 421 / 20 840 / 21 830 | **0** | errors back to 0 within ≈2 s of the spike ending |

Reading the numbers: under a deliberately abusive 600-user zero-think-time test the system now degrades
gracefully (fast 503s, nothing crashes, People stays healthy, recovery is immediate) instead of collapsing.
The realistic 150-concurrent-user test (≈4× a busy morning for a company of this size) has zero errors.
The remaining hot spots are the Leave balance / leave-type reads (≈100 small queries per request because
ledger maintenance runs inside the read path) — see §4.

---

## 3. Guideline compliance (MUST items)

| § | Requirement | Status |
|---|---|---|
| 1 | Supported framework releases via vulnerability check | **Done** (S6) — CI: Trivy, dependency review, CodeQL, Dependabot |
| 2 | Source, contracts, migrations, tests, **CI config, deployment manifests, example config, runbooks** | **Done** (`.github/`, `deploy/`, `.env.example`, `docs/RUNBOOK.md`). *Restore report:* needs a real environment (§4) |
| 3 | No cross-database access; services own data | Done (unchanged) + salary minimised in directory (S5) |
| 4 | OIDC + PKCE, httpOnly cookies, CSRF | Done earlier (OIDC skipped this round by instruction) |
| 4 | Validate issuer / audience / signature / algorithm / expiry; strip client identity headers | **Verified** — 112-check probe (`scripts/test-security-rbac.mjs`) |
| 4 | Feature permission **and** record scope | **Fixed** (S1); 30+ cross-employee / cross-role negative checks pass |
| 4 | Session revocation and offboarding | **Hardened** (S4) |
| 4 | Privileged payroll writes check current authority; bounded cache delay documented | **Done** (S8; RBAC ≤ 10 s across replicas, directory ≤ 30 s, session status ≤ 30 s) |
| 4 | Workload credentials + mTLS; asymmetric keys; MFA | *Open* — shared service key + HS256 remain (needs platform/IdP decision, guide §15) |
| 5 | Restricted runtime roles, constraints, concurrency control, decimal money, UTC, expand/contract | Done earlier; DB statement/lock/idle-transaction budgets added (R7) |
| 6 | Payroll finalisation / recovery | Done earlier — e2e and recovery suites re-run (§5) |
| 7 | Common error envelope with code + correlationId | **Done** for platform/infra errors (S3); service business errors keep their `{error}` body for the UI |
| 7 | Explicit HTTP / gRPC deadlines, bounded budgets | **Done** (R4, R6, R7; gRPC 5 s) |
| 7 | Retries with backoff, DLQ, outbox/inbox | Done earlier (outbox relay backoff, listener retry, DLQs) |
| 8 | Document ACLs, quarantine, scan before release | Verified (upload → QUARANTINED → RELEASED; fake PDF rejected; wrong-role download 403) + metadata scope (S1) |
| 9 | Build once, promote the same digest; non-root; limits; least FS permissions | **Done** (`release.yml`, `deploy/`) |
| 9 | Liveness/readiness, graceful shutdown | Done (gateway graceful shutdown added; readiness includes DB/Redis) |
| 9 | Private ports, secrets management, reject dev credentials | Done (deploy stack publishes only HTTPS; `ProdSecretsGuard`) |
| 9 | Structured logs, metrics, traces, correlation across HTTP/gRPC/messages | Logs + metrics + correlation **done**; distributed *tracing export* not yet wired (§4) |
| 9 | Monitor latency, errors, saturation, pools, queues, outbox, DLQ, payroll mismatches | Metrics exposed; signals and thresholds in `RUNBOOK.md` |
| 10 | Backups, PITR, restore drill | *Needs the target platform* (§4) |
| 13 | SEC01 / DOC01 | Probe 112/112 |
| 14 | CI runs unit, PostgreSQL integration, authorization, frontend checks, scans with retained reports | **Done** (`ci.yml`, system-test job on real Postgres/RabbitMQ/Redis/MinIO) |
| 14 | Load tests with p95, errors, DB connections | **Done** (§2; CI smoke gate p95 < 800 ms, errors < 1 %) |

---

## 4. Known limitations / next steps (honest list)

1. **Leave read path** (`/leave-types`, `/leave-balances`, `/leave/my`): ≈80–145 small queries per request
   because category sync and ledger reconcile run on every GET. Optimising it means restructuring leave
   business code — deliberately not done without HR/Finance sign-off. Capacity is handled by pool sizing
   and load shedding meanwhile. (Read-path indexes for 3-year volumes and the batched leave-list approval
   load are done — see [DATABASE_INDEXING.md](DATABASE_INDEXING.md).)
2. Service-to-service identity is still a shared service key + HS256 session JWT; production should add
   workload identity / mTLS (or a service mesh) and asymmetric session keys (audit SEC-03 / SEC-10).
3. Distributed tracing export (OpenTelemetry) is not wired; correlation ids cover request tracing today.
4. Backup / PITR / restore and release-recovery drills must run on the chosen hosting platform.
5. Frontend: 14 `set-state-in-effect` warnings (one extra render each); no automated UI tests yet.
6. Some older QA scripts (`qii-feature-e2e`, `qii-deep-logic`, `test-checklists`, `test-worklog-missing-notify`)
   carry stale expectations (demo login on, fixed dates, accumulated test data). Their failures were each
   checked and are not regressions (§5). `test-holidays.mjs` leaves past QA holidays behind that then skew
   payroll test months — clean with `DELETE FROM holiday WHERE name LIKE 'QA\_HOL\_%'` in dev.

---

## 5. Verification log (final code, 2026-11-12)

| Check | Result |
|---|---|
| `./gradlew test` | **313 / 313** pass (287 before this round + 26 new regression tests) |
| `scripts/test-security-rbac.mjs` | **112 / 112** |
| `scripts/test-payroll-recovery.mjs` | **56 / 56** |
| `scripts/test-payroll-e2e.mjs` | 103 / 104 — the one difference is Arpa's *approved unpaid half-day leave today* (created this morning), correctly deducted |
| holidays · leave setup · leave apply/approval · reporting lines · work-log checkout · adversarial QA | all pass |
| `qii-system-acceptance` | 31/31 on first run; a re-run hits the leave-overlap rule on the leave the first run created (correct) |
| Frontend | `tsc` 0 errors, ESLint 0 errors, production Docker image builds and runs as `node` |
| Load / spike | §2 |

Commands:

```bash
./gradlew test
node scripts/test-security-rbac.mjs
node scripts/load-test.mjs --profile smoke|load|stress|spike|soak
FORCE=1 bash scripts/seed-payroll-scenarios.sh && node scripts/test-payroll-e2e.mjs && node scripts/test-payroll-recovery.mjs
```
