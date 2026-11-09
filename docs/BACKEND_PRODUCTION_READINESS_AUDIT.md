# Backend Production-Readiness Audit (Guideline Compliance)

**Audit date:** 2026-11-09
**Baseline:** `HRMS_Microservices_Developer_Guide.pdf` v1.0 (4 Nov 2026), plus general production-grade microservice practice where the guide is silent or weaker.
**Code state audited:** `main` @ `28548a0` **plus uncommitted working-tree changes** (gateway session revocation, payroll finalize/recovery, JwtSupport). The fixing agent must start from the same working tree.
**Scope:** All Java services and libraries (`api-gateway`, `tenant-registry`, `people-service`, `leave-service`, `time-work-service`, `payroll-service`, `file-service`, `notification-service`, `report-service`, `platform-common`, `platform-messaging`, `hrms-api`), Liquibase changelogs, Docker/Compose, RabbitMQ topology, and the Next.js BFF auth/proxy layer (`frontend/qii-hrms/app/api/**`, `lib/api/server.ts`). Frontend UI business logic is out of scope.

**Agreed audit rules (from the owner):**
- Multi-tenancy is **kept** as an intentional design. Its risks are audited (guide §11 "Dynamic tenant routing and pools"), but it is not reported as a violation by itself.
- Synchronous vs asynchronous communication is judged by the **best production practice**. Sync reads are allowed only with deadlines, contracts, and resilience. Cross-service **state mutation** must go through durable messaging (outbox → RabbitMQ → inbox).
- The BFF auth layer is in scope because the guide places identity there (§4).

### Remediation status (2026-11-09 working tree; OIDC explicitly deferred)

| Status | Issue IDs |
|---|---|
| **FIXED** | SEC-02, SEC-04, SEC-05 (Redis AUTH + no password in routing cache), SEC-06, SEC-07, SEC-08, SEC-11 (partial Origin/Referer), SEC-12, SEC-13, SEC-14 (leave deny-default + org/team/location/file annotations), SEC-16, SEC-17 (dockerignore/gitignore/dotenv gating), SEC-18, DB-04 (`splitStatements`), DB-10 (Redis fallback pattern), DB-11, PAY-01, PAY-02 (unblocked by facts), PAY-04, PAY-05, PAY-09, PAY-14/15 (partial), PAY-12 (messaging mandatory), LV-01, LV-02, LV-03 (partial retry), LV-04, LV-05, MSG-01, TW-01, TW-02, DOC-01/02, RPT-01, OPS metrics/Prometheus exposure |
| **DEFERRED (OIDC / IdP)** | SEC-01 (OIDC+PKCE login), SEC-09 (opaque server sessions — needs IdP refresh), SEC-10 (asymmetric JWT/JWKS), SEC-15 (MFA `acr`) |
| **PARTIAL / FOLLOW-UP** | SEC-03 (still shared service key; identity stamps remain until workload JWT/mTLS), DB-01/DB-02/DB-03 (per-service PG roles + move changelogs out of registry), DB-05/DB-06/DB-07/DB-08/DB-09, PAY-03 (full `payslip_revision` table), PAY-06/07/08/10/11/13, LV-06/07, MSG-02+, OPS HA/TLS/backups/CI restore drills, AUD-* |

`./gradlew compileJava test` was green after these remediations. Set `REDIS_PASSWORD` in `.env` before `docker compose up`.

---

## 0. How to use this document (for the fixing agent)

1. Fix issues in the order given in **§13 Fix order**. It follows guide §12 stages: security first, then data integrity, then workflows, then features, then operations.
2. Every issue has an **ID**, **Severity**, **Guide ref**, **Where** (file:line, relative to repo root), **Problem**, **Evidence**, **Fix**, and **Done when**. "Done when" is the acceptance check. Add an **automated test** for it; console scripts don't count (guide §13).
3. Severity:
   - **CRITICAL**: security breach, money/leave corruption, or the system cannot be deployed or restored. Release blocker.
   - **HIGH**: violates a MUST in the guide, or will fail under realistic production load or failure. Release blocker.
   - **MEDIUM**: violates a SHOULD or best practice; fix before pilot.
   - **LOW**: hygiene.
4. "LIVE" in Evidence means it was reproduced against the running local stack on 2026-11-09 (see Appendix A). "CODE" means it was proven by reading the code.
5. Don't weaken any integrity guarantee while fixing. Where a fix changes service ownership or a business policy, write an ADR under `docs/adr/` first (guide §15).

---

## 0.1 Status update — single company, fixed DB connections (2026-11-09)

Multi-tenancy was removed to follow guide §1 ("One company. Use fixed service database connections"). See
[DEPLOYMENT_FIXED_DB.md](DEPLOYMENT_FIXED_DB.md). Effect on the findings below (verified on the local stack):

| Issue | Status | Evidence |
|---|---|---|
| DB-01 shared SUPERUSER | **Resolved** | Per-service `<svc>_app` (DML only) + `<svc>_migrator` (owner) roles; `leave_app` → `people_db` refused (no CONNECT); `people_app` DDL refused |
| DB-02 provisioning creds = runtime creds | **Resolved** | No provisioning in the runtime; each service gets only its own credentials from env / secrets manager |
| DB-03 registry owns all schemas / writes into service DBs | **Resolved** | `tenant-registry` deleted; each service runs its own Liquibase changelog (`db/changelog/db.changelog-master.yaml`) as its migrator role |
| DB-04 fresh environment cannot be built | **Resolved** | Empty Postgres → first-boot role/DB init → all 7 services migrated from scratch → `seed-qii-dev.sh load` reproduced all row counts |
| DB-07 legacy archive DB | **Resolved (local)** | `archive_hrms_*` and all non-Qii tenant databases dropped |
| DB-10 dynamic tenant routing / pool races / Redis on DB path | **Resolved** | Routing DataSource, pool lifecycle, Redis routing and registry fallback removed; one Hikari pool per service; services no longer depend on Redis |
| SEC-04 control plane on public edge | **Resolved** | No registry routes; `/api/registry/**` → 404 |
| SEC-05 registry/Redis hold DB credentials | **Resolved** | No credential distribution API or Redis payload exists |
| SEC-16 registry admin auth | **Resolved (removed)** | Registry deleted |
| MSG-07 outbox draining depends on Redis SCAN | **Resolved** | `OutboxRelay` drains on its own schedule; no tenant iteration |
| DB-06 notification dedupe not unique | **Resolved** | `uq_notif_dedupe_key` (partial unique index) |
| OPS-07 control plane in startup/login path | **Partly resolved** | Fleet migration removed; login token minted by the gateway (still demo auth — SEC-01 open) |
| API-01 gRPC without deadline | **Partly resolved** | People gRPC stub now has a 5 s deadline; HTTP clients unchanged |

All other findings remain open and are unchanged by this work.

## 1. Executive verdict

**The backend is NOT production-ready.** The service split, outbox/inbox library, RabbitMQ topology, and finalize state machine are a good skeleton. Several release-blocking defects remain:

- **Anyone can log in as anyone.** No password or OIDC, demo auth is on by default, and a SUPERADMIN session is obtainable unauthenticated (LIVE).
- **There is no real trust boundary behind the gateway.** One shared static `X-Service-Key` plus plain `X-Employee-ID` headers let any holder impersonate any employee on any service (LIVE).
- **The database layer has no isolation.** Every service connects as a PostgreSQL **SUPERUSER** that owns every service DB, and those credentials sit in plain text in an unauthenticated Redis (LIVE).
- **Payroll can't calculate at all after the People/Leave/Time-Work split.** The facts contract is broken and stubbed, so every calculate returns `409 Employee not employed in period` (LIVE). The durable leave-cover finalize workflow therefore never triggers.
- **Leave balances drift from the ledger** (clamp-to-zero, partial deduction, full restore, quota without a ledger row). Historical payroll finals are overwritten.
- **Any employee can list and download every document** in the tenant (LIVE).
- **A fresh environment can't be built.** Tenant provisioning fails on a Liquibase changeset (LIVE), so restores and new deployments are impossible.
- **The operations layer is missing:** no metrics, tracing, CI, backups, runbooks, TLS, or HA broker.

### 1.1 Mandatory acceptance tests (guide §13) — current status

| Test | Status | Reason (issue IDs) |
|---|---|---|
| SEC01 | **FAIL (LIVE)** | Passwordless login by employeeId; service-key impersonation (SEC-01, SEC-02, SEC-03) |
| DATA01 | **FAIL (CODE)** | Ledger ≠ balance; no payslip locking; concurrent calculate surfaces 500 (LV-01, PAY-04, LV-03) |
| PAY01 | **FAIL / NOT EXERCISABLE (LIVE)** | Cover command never sent because facts are stubbed; calculate fails (PAY-01, PAY-02) |
| PAY02 | **FAIL (CODE)** | Stale results not checked; business rejection lost in a rollback-only transaction (PAY-05, LV-02, PAY-06) |
| PAY03 | **FAIL (CODE)** | No salary segmentation; leavers excluded; daysEmployed missing (PAY-10, PAY-01) |
| REP01 | **FAIL (CODE)** | Export reads one page, sorted ascending, server-capped at 100 (RPT-01) |
| DOC01 | **FAIL (LIVE)** | Employee downloads another employee's file (DOC-01) |
| OPS01 | **FAIL (CODE/LIVE)** | Outbox gives up after ~20 s of broker outage; Redis failure is unhandled; restore impossible (MSG-01, DB-10, DB-04, OPS-06) |

### 1.2 Issue count

| Area | Critical | High | Medium | Low |
|---|---|---|---|---|
| Identity & authorization (SEC) | 5 | 9 | 3 | 1 |
| Data ownership & database (DB) | 3 | 4 | 3 | 1 |
| Payroll (PAY) | 4 | 7 | 4 | 0 |
| Leave (LV) | 2 | 3 | 2 | 0 |
| Time & Work (TW) | 0 | 2 | 2 | 0 |
| Documents (DOC) | 1 | 3 | 3 | 0 |
| Reporting (RPT) | 0 | 2 | 1 | 0 |
| Messaging (MSG) | 0 | 4 | 4 | 1 |
| Contracts & resilience (API) | 0 | 3 | 3 | 0 |
| Operations & delivery (OPS) | 0 | 6 | 3 | 0 |
| Audit (AUD) | 0 | 0 | 2 | 1 |
| **Total (92 issues)** | **15** | **43** | **30** | **4** |

---

## 2. Identity & Authorization (guide §4, §11 row 1, §13 SEC01)

### SEC-01 — Passwordless "account picker" login gives anyone any identity, including SUPERADMIN
- **Severity:** CRITICAL · **Guide:** §4 MUST (OIDC code+PKCE, never accept employeeId as proof of login), §11 row 1, §13 SEC01, §14
- **Where:** `frontend/qii-hrms/app/api/auth/login/route.ts:43-79`, `frontend/qii-hrms/app/api/auth/accounts/route.ts`, `tenant-registry/.../api/RegistryController.java:180-213` (`POST /api/registry/token`), `people-service/.../api/rest/AuthController.java:25` (`/api/auth/accounts`)
- **Problem:** The BFF accepts `{"employeeId": N}` from an unauthenticated browser. It calls the registry token endpoint with the server-held demo key and sets a 7-day session cookie. `/api/auth/accounts` publicly lists every employee with role and email.
- **Evidence (LIVE):** `curl -X POST :3000/api/auth/login -d '{"employeeId":1}'` → `200`, cookie `Max-Age=604800`, `/me` shows `roles:["SUPERADMIN"]`, `"*:*"`. `GET :3000/api/auth/accounts` (no auth) → full account list.
- **Fix:** Implement OIDC Authorization Code + PKCE in the BFF with verified `state` and `nonce`. Validate the ID token (iss, aud, signature, alg allow-list, exp, JWKS rotation), then map the verified `sub` to an active employee through a People `identity_mapping` table. Issue the platform session only after that mapping. Delete `/api/auth/accounts`, `/api/registry/token` (demo mint) and People `/api/auth/accounts` from production builds, or hard-gate them behind a non-prod profile that refuses to start in prod (see SEC-02).
- **Done when:** An automated test shows that POSTing an employeeId to any endpoint can't produce a session, `/accounts` returns 404 in the prod profile, and login works only with a valid IdP code exchange.

### SEC-02 — Demo auth is enabled by default and production startup doesn't reject it
- **Severity:** CRITICAL · **Guide:** §14 ("No demo authentication or known default secrets can start in production"), §9 (startup rejection of dev credentials)
- **Where:** `api-gateway/src/main/resources/application.yml` (`demo-auth.enabled: ${DEMO_AUTH_ENABLED:true}`), `api-gateway/.../config/GatewayProperties.java` (`enabled = true`), `docker-compose.yml` (`DEMO_AUTH_ENABLED: ${DEMO_AUTH_ENABLED:-true}`), `.env.example`, `payroll-service/src/main/resources/application-prod.yml` (comment "DEMO_AUTH can stay for now"), `platform-common/.../security/ProdSecretsGuard.java:25`
- **Problem:** `ProdSecretsGuard` checks only a few secret strings, and only when `spring.profiles.active` contains `prod`. It never checks `demo-auth.enabled`. Only the gateway, payroll and registry call it, and no other service has a prod profile.
- **Fix:** Default `demo-auth.enabled=false` everywhere. In every service, add a startup guard that fails when the prod profile is active **or** the profile is missing (fail-safe), and any of these hold: demo auth enabled, `require-gateway=false`, dotenv loading enabled, known dev secret fragments, or default DB/broker/MinIO credentials. Make the prod profile mandatory in deployment manifests.
- **Done when:** Starting any service with the prod profile and `DEMO_AUTH_ENABLED=true` (or any default secret) exits non-zero, and a test covers each condition.

### SEC-03 — Behind the gateway, identity is plain headers trusted on one shared static key
- **Severity:** CRITICAL · **Guide:** §4 MUST ("Propagate user identity only in a verifiable token or signed assertion. Use workload-specific credentials for service calls, plus mTLS"; "services validate service-audience credentials")
- **Where:** `people-service/.../infrastructure/multitenancy/TenantFilter.java:21-60` (same pattern copied into all 7 services), `platform-common/.../constants/PlatformHeaders.java`, `api-gateway/.../filter/JwtTenantGatewayFilter.java` (stamps `X-Service-Key`, `X-Tenant-ID`, `X-Employee-ID`)
- **Problem:** Each service accepts any request carrying the shared `PLATFORM_SERVICE_KEY` and then trusts `X-Tenant-ID` and `X-Employee-ID` as-is. The same key is held by the gateway and by every service, so any compromised service, leaked key, or SSRF can impersonate any employee in any tenant. Service ports are published (OPS-05).
- **Evidence (LIVE):** `curl :8081/api/me -H "X-Service-Key: <key>" -H "X-Tenant-ID: qii_creative" -H "X-Employee-ID: 1"` → SUPERADMIN profile, no JWT needed.
- **Fix:** The gateway forwards a short-lived, signed internal token (asymmetric, e.g. RS256/ES256, with `aud=<target-service>`, `sub`, `tenant_id`, `employee_id`, `jti`, `exp ≤ 5 min`), or each service validates the user JWT directly. Every service validates signature, issuer, audience and expiry, and derives identity **only** from the token. For service-to-service calls use per-workload credentials (OAuth2 client-credentials with a per-service audience, or SPIFFE/mTLS). Remove `X-Employee-ID`/`X-Tenant-ID` trust. Enforce mTLS or a private encrypted network between services.
- **Done when:** A request with forged headers and the old shared key is rejected (401) by every service, and an integration test proves service A's credential can't call service B endpoints outside its allowed audience.

### SEC-04 — Gateway forwards control-plane calls when an admin-key header is merely present
- **Severity:** CRITICAL · **Guide:** §4 (edge validation), §9 (only HTTPS ingress public)
- **Where:** `api-gateway/.../filter/JwtTenantGatewayFilter.java` (block starting `String adminKey = ...getFirst("X-Platform-Admin-Key")`, ~line 80)
- **Problem:** If the header is **non-blank, with any value**, and the path is `/api/registry/provision|tenants/**` or `/api/session/revoke-employee/**`, the gateway skips JWT validation and stamps the real `X-Service-Key`. Security then depends entirely on the registry checking the key. Tenant provisioning, migrate-all, reseed and split-workforce are exposed on the public edge.
- **Evidence (LIVE):** `GET :8090/api/registry/tenants -H "X-Platform-Admin-Key: wrong"` reached the registry (it rejected the key, but returned HTTP **500** instead of 401; see API-03).
- **Fix:** Remove control-plane routes from the public gateway entirely. Expose the registry only on a private admin network or VPN, with admin SSO + MFA. If an edge route must stay, validate the admin credential at the gateway (constant-time) and never stamp the service key on it.
- **Done when:** No `/api/registry/**` path is routable through the public gateway in the prod profile, and a test asserts 404.

### SEC-05 — Registry hands per-service DB credentials to any service-key holder; credentials stored in plain text
- **Severity:** HIGH · **Guide:** §5 MUST ("Provisioning credentials must not be distributed to application services"), §9 (secrets management)
- **Where:** `tenant-registry/.../api/RegistryController.java:166-177` (`GET /api/registry/internal/routing/{service}/{tenant}`), `tenant-registry/.../domain/TenantRouting.java` (`password` column), `tenant-registry/.../infrastructure/redis/RoutingCachePublisher.java` (writes the password to Redis), `platform-common/.../redis/RoutingPayload.java`
- **Problem:** Any caller with the shared service key can read **any** service's JDBC URL, username and password (for example notification can read payroll's). The passwords are stored unencrypted in `registry_db.tenant_routing` and in Redis (`tenant-routing:<service>:<tenant>`, 24h TTL). Redis has no auth.
- **Evidence (LIVE):** `GET :8080/api/registry/internal/routing/payroll/qii_creative` with the service key → payroll credentials. `redis-cli GET tenant-routing:people:qii_creative` → password present; `CONFIG GET requirepass` is empty.
- **Fix:** Remove credentials from routing payloads. Each service gets its **own** runtime DB credential from a secrets manager (Vault, cloud secret store) or IAM DB auth, scoped to its own DB(s). Routing contains only host/db name/status/version. If routing must stay dynamic, authorize per service identity (service X may resolve only `service=X`). Enable Redis AUTH, ACLs and TLS.
- **Done when:** No API, Redis key, or DB row contains a DB password, and service A can't obtain service B's credentials (test).

### SEC-06 — Internal endpoints guarded only by the shared gateway key (not the internal key)
- **Severity:** HIGH · **Guide:** §4 (workload-specific credentials), §3
- **Where:** `leave-service/.../api/rest/InternalLeaveBridgeController.java:45` (`POST /api/internal/approvals/reassign`, mutating), `:53` (`leave-facts`), `:64` (`approved-leave`); `time-work-service/.../api/rest/InternalTimeWorkBridgeController.java:110` and its sync endpoints; every `InternalAuditController` in leave, time-work, payroll, notification, report and files
- **Problem:** These controllers don't check `X-Internal-Key` (People's and Files' internal controllers do). Any holder of the gateway key can mutate approval chains or read audit and leave data. The gateway blocks `/internal/`, but service ports are directly reachable.
- **Evidence (LIVE):** `POST :8086/api/internal/approvals/reassign` with only `X-Service-Key` → `200 {"updated":0}`.
- **Fix:** Replace the key checks with per-service workload authentication (SEC-03). Until then, require `X-Internal-Key` on every `/api/internal/**` endpoint through one shared interceptor, deny by default, and allow-list the caller service per endpoint.
- **Done when:** Every `/api/internal/**` endpoint rejects requests without a valid internal workload credential (parameterized test over all handler mappings).

### SEC-07 — `PLATFORM_REQUIRE_GATEWAY=false` turns off authentication in every service
- **Severity:** HIGH · **Guide:** §14, §9
- **Where:** `platform.security.require-gateway: ${PLATFORM_REQUIRE_GATEWAY:true}` in every service's `application.yml`; `TenantFilter`; `people-service/.../api/grpc/TenantGrpcInterceptor.java`
- **Problem:** One environment variable disables the only authentication check (REST and gRPC). No startup guard stops this in production.
- **Fix:** Remove the flag, or allow `false` only in the `test` profile and fail startup otherwise.
- **Done when:** A service with the prod profile refuses to start when the flag is false.

### SEC-08 — Offboarded or deactivated employees keep access for up to 7 days
- **Severity:** HIGH · **Guide:** §4 MUST ("administrative session revocation and offboarding"; "Privileged payroll writes must check current authority")
- **Where:** `api-gateway/.../api/SessionController.java:73` (`ttlSeconds` default `43200` = 12 h), `tenant-registry/src/main/resources/application.yml` (`token-ttl-seconds: 604800` = 7 d), `people-service/.../application/OffboardingService.java` (no revocation call), `people-service/.../application/RbacService.java` (permissions ignore `employee.active`)
- **Problem:** (a) Offboarding never calls the gateway revoke endpoint. (b) Even when called, the employee revocation key expires after 12 h while the JWT stays valid for 7 days, so access returns. (c) RBAC resolves permissions for inactive employees. (d) Re-login doesn't revoke the previous session.
- **Fix:** On status change to inactive or terminated, People publishes `people.employee.deactivated` through the outbox. The gateway/session service consumes it and revokes all sessions until max token expiry (TTL ≥ token TTL, or store a per-employee "tokens issued before T are invalid" timestamp). RBAC returns no permissions for inactive employees. Shorten access tokens (≤ 15 min) with server-side refresh in the BFF.
- **Done when:** A test deactivates an employee and shows their existing session gets 401 within the documented bounded delay on every service.

### SEC-09 — Session model isn't server-side; long-lived bearer JWT in a cookie; `Secure` not enforced
- **Severity:** HIGH · **Guide:** §4 MUST (server-side session storage, rotate sessions, keep refresh tokens out of JS)
- **Where:** `frontend/qii-hrms/lib/api/server.ts:12-24` (cookie value = raw JWT), `frontend/qii-hrms/lib/config.ts:26` (`cookieSecure` env-driven), `tenant-registry` token TTL 7 days
- **Problem:** The cookie *is* the 7-day platform JWT, and there is no server-side session record. In the observed run the cookie had no `Secure` flag (LIVE `Set-Cookie` without `Secure`). There is no refresh rotation and no idle timeout.
- **Fix:** Use an opaque session id in the cookie (`__Host-` prefix, `Secure`, `HttpOnly`, `SameSite=Lax/Strict`) mapped to a server-side session store (Redis with AUTH/TLS) holding the IdP tokens. Rotate the session id on login and privilege change. Add idle and absolute timeouts. Force `Secure` in production.
- **Done when:** The cookie contains no JWT, the server-side session can be revoked, and prod refuses to issue non-Secure cookies.

### SEC-10 — HS256 shared symmetric JWT secret across services; no key rotation
- **Severity:** HIGH · **Guide:** §4 MUST ("validate ... allowed algorithms ... signing-key rotation")
- **Where:** `platform-common/.../security/JwtSupport.java:982-1068` (HMAC key, no `kid`), `JWT_SECRET` given to the gateway, registry and file-service (`docker-compose.yml`), `file-service/.../application/DownloadTokenService.java` (defaults to `JWT_SECRET`)
- **Problem:** Every component holding `JWT_SECRET` can **mint** user tokens (the file-service can forge a SUPERADMIN session). There's no `kid` and no JWKS rotation, and one key is reused for two token types.
- **Fix:** Tokens are issued by the IdP or a single auth component with an asymmetric key. Verifiers use only the public JWKS, with `kid`-based rotation and an algorithm allow-list. Give download tokens a separate dedicated key.
- **Done when:** No service other than the issuer holds a signing key, and a rotation test passes (old and new `kid` accepted during overlap, old rejected after).

### SEC-11 — CSRF protection is incomplete
- **Severity:** MEDIUM · **Guide:** §4 MUST (CSRF controls)
- **Where:** `frontend/qii-hrms/lib/api/server.ts:204-216`, `app/api/auth/login/route.ts:24-35`, `app/api/auth/logout/route.ts`
- **Problem:** The Origin check returns "not cross-origin" when `Origin` is absent, and there's no CSRF token. Protection relies on `SameSite=Lax`, which doesn't protect top-level GETs that mutate, or older browsers.
- **Fix:** Add a synchronizer or double-submit CSRF token for all state-changing BFF routes, reject requests with no `Origin`/`Referer` on mutations, and make sure no GET endpoint mutates state.
- **Done when:** A test shows a mutation without a CSRF token is rejected.

### SEC-12 — Gateway CORS reflects any origin
- **Severity:** HIGH · **Guide:** §9 (only HTTPS ingress public), best practice
- **Where:** `api-gateway/src/main/resources/application.yml` (`globalcors ... allowedOriginPatterns: "*"`, methods `*`, headers `*`)
- **Evidence (LIVE):** A preflight from `Origin: https://evil.example` → `Access-Control-Allow-Origin: https://evil.example`.
- **Fix:** The gateway should be reachable only from the BFF (server-to-server), so disable CORS entirely in prod, or allow-list exact origins.
- **Done when:** A preflight from an unlisted origin gets no ACAO header.

### SEC-13 — Swagger UI and every service's OpenAPI spec are public through the gateway
- **Severity:** MEDIUM · **Guide:** §9 (keep service ports private; least exposure)
- **Where:** `api-gateway/src/main/resources/application.yml` `public-paths` (`/swagger-ui/**`, `/docs/*/v3/api-docs/**`, including registry)
- **Evidence (LIVE):** `GET :8090/docs/registry/v3/api-docs` → 200 without auth.
- **Fix:** Disable springdoc UI and docs in the prod profile. Publish contracts as versioned files in the repo (API-04) instead.
- **Done when:** Those paths return 404 under the prod profile.

### SEC-14 — Authorization is fail-open and not scope-aware in several services
- **Severity:** CRITICAL · **Guide:** §4 MUST ("enforce feature permission and record scope together"; "Do not rely on hidden UI controls")
- **Where:** `people-service/.../security/RbacInterceptor.java:34` (no annotation → allowed; same in every copy). Unguarded handlers: `file-service/.../api/rest/FileController.java` (all 7 endpoints), `notification-service/.../NotificationController.java`, `people-service/.../OrgController.java` (`/chart`, `/tree`, `/departments`, `/designations`), `TeamController` GET, `LocationController` GET, `leave-service/.../LeaveTypeController.java`, `LeavePolicyController.java`. Scope-blind checks: `file-service/.../FileService.java:522` (`employees:view` ⇒ all documents), `report-service/.../AttendanceReportService.java` (`employees:edit` or `payroll:run` ⇒ any employee's export).
- **Problem:** Endpoints without `@RequiresPermission` are open to any authenticated user. Several services check a feature key without the record scope (OWN/TEAM/ALL) that People defines (`scopes` in `/me`).
- **Fix:** Deny by default. The interceptor rejects handlers that lack an explicit `@RequiresPermission` or `@PublicEndpoint`. Provide one shared, scope-aware authorization component (feature + action + target employee → OWN/TEAM/ALL) in a library and use it in every service, including files and reports.
- **Done when:** A test enumerates all handler methods and fails if any lacks an annotation, and cross-role negative tests (employee vs manager vs HR) pass for every resource type.

### SEC-15 — No MFA for privileged identities; privileged payroll writes use cached authority
- **Severity:** MEDIUM · **Guide:** §4 MUST ("Privileged payroll writes must check current authority; document any bounded cache delay ... Require MFA for privileged identities")
- **Where:** `payroll-service/.../infrastructure/grpc/HrmsEmployeeClient.java` (10 s permission cache), `platform-common/.../security/HrmsPermissionClient.java` (TTL cache), `platform-common/.../people/PeopleDirectoryClient.java` (authz cache)
- **Problem:** No MFA claim (`amr`/`acr`) is required for payroll:run, salary:edit or roles:*. Finalize, reopen, salary and config writes use cached permissions, and the delay isn't documented.
- **Fix:** Require an MFA `acr` on privileged routes (enforced from the IdP token). Bypass the cache for privileged writes (fetch fresh authority, or use event-driven invalidation). Document the read-cache bound in an ADR.
- **Done when:** A privileged write without MFA is rejected (403), and a role revoked takes effect on the next privileged write (test).

### SEC-16 — Registry admin auth is weak: plain `equals`, one static key, no audit
- **Severity:** HIGH · **Guide:** §4, §10 (emergency access, key rotation)
- **Where:** `tenant-registry/.../api/PlatformAuthSupport.java:22,29`
- **Problem:** Non-constant-time comparison; one platform-wide static admin key with no identity, MFA or rotation; provisioning, reseed and migrate actions aren't audited.
- **Fix:** Admin SSO with MFA and named operators, audited control-plane actions (actor, reason, before/after), and constant-time comparison for any remaining secret.
- **Done when:** Every control-plane call records an audit row with the operator identity.

### SEC-17 — Secret hygiene: committed session token, committed broker hash, `.env` in Docker context, dotenv auto-load
- **Severity:** HIGH · **Guide:** §2 (example config without secrets), §9 (secrets management, rotation, startup rejection)
- **Where:** `.tmp-smoke/cookie.txt` (tracked; contains a `qii_session` JWT), `docker/rabbitmq/definitions.json` (tracked; `administrator` user with a password hash), missing `.dockerignore` while every `*/Dockerfile` does `COPY . .`, `platform-common/.../config/DotEnvEnvironmentPostProcessor.java` (walks up 8 parent directories and loads `.env` in any environment), `ProdSecretsGuard` message mentions `RABBITMQ_PASSWORD` but never checks it
- **Fix:** Remove `.tmp-smoke/`, `.tmp-enrich/` and `data/*.mv.db` from git and purge them from history if any real secret was used; rotate `JWT_SECRET`. Generate RabbitMQ definitions at deploy time from the secrets manager, not git. Add a `.dockerignore` that excludes `.env*`, `.git`, `node_modules`, `build`, `.tmp*`. Enable the dotenv loader only in an explicit `local` profile. Extend the guard to all credentials.
- **Done when:** `git ls-files` contains no tokens or hashes, the Docker build context has no `.env`, and the prod profile doesn't read `.env`.

### SEC-18 — Client-supplied correlation ID accepted unvalidated
- **Severity:** LOW · **Guide:** §9 (logs must exclude tokens; correlation propagation)
- **Where:** `api-gateway/.../filter/CorrelationIdGatewayFilter.java`, `platform-messaging/.../config/CorrelationIdFilter.java`
- **Fix:** Accept only `^[A-Za-z0-9-]{8,64}$`, otherwise generate a new one.

---

## 3. Data ownership & database (guide §1 Data, §3, §5, §11)

### DB-01 — All services run as a PostgreSQL SUPERUSER that owns every service DB
- **Severity:** CRITICAL · **Guide:** §3 ("MUST NOT read or write another service database"), §5 MUST (restricted runtime roles; separate migration identity)
- **Where:** `docker/postgres/init-*-host.sh` (`CREATE USER ${USER_NAME} ... SUPERUSER`), `.env.example` (every `*_PG_USER=platform`), `tenant-registry/.../provision/PostgresDatabaseProvisioner.java:34` (`CREATE DATABASE ... OWNER <provisioning user>`)
- **Evidence (LIVE):** `pg_roles`: `platform | rolsuper=t`. The people, leave, timework and archive DBs are all owned by `platform`. The Leave runtime credential reads `people_qii_creative.employee` (21 rows).
- **Problem:** No isolation. Any service, or a SQL injection in any service, can read or alter any service's data, drop databases, or read files on the DB host.
- **Fix:** Create three role tiers per service per DB: `<svc>_owner` (NOLOGIN, owns schema), `<svc>_migrator` (LOGIN, member of owner, used only by migration jobs), `<svc>_app` (LOGIN, `CONNECT` only on its own DB, `USAGE` on schema, DML on tables, `USAGE` on sequences; no DDL, not superuser, no `CREATEDB`). `REVOKE CONNECT ... FROM PUBLIC` on each DB. Use separate hosts or clusters for People/Leave/Time-Work or strict `pg_hba` rules.
- **Done when:** A test connects with each `<svc>_app` credential and proves it can't connect to another service's DB and can't run DDL.

### DB-02 — Provisioning (admin) credentials are reused as runtime credentials
- **Severity:** CRITICAL · **Guide:** §5 MUST ("Only a separate migration identity may change schema. Provisioning credentials must not be distributed to application services"), §1 ("Keep future tenant provisioning outside the request path")
- **Where:** `tenant-registry/.../application/ProvisioningService.java:741,812` (`row.setUsername(target.getUsername()); row.setPassword(target.getPassword())` — same target used for `CREATE DATABASE`), `PostgresDatabaseProvisioner.java:56` (`DriverManager.getConnection(adminUrl, username, password)`)
- **Fix:** Use a provisioning identity used only by the registry or a provisioning job; it creates DBs and roles. Runtime credentials are per-service app roles (DB-01), never the provisioning one. Provisioning runs as an async job, not a synchronous HTTP request.
- **Done when:** The provisioning credential appears nowhere in any service's config, routing, or Redis.

### DB-03 — Schema and migration ownership is centralized in the registry; the registry writes into every service DB
- **Severity:** HIGH · **Guide:** §1 ("Each service owns its database, migrations and runtime role"), §3, §14 ("Each service has ... migrations"; "No service accesses another service database")
- **Where:** `tenant-registry/src/main/resources/db/tenant-changelog/**` (all services' changelogs live in the registry), `.../leave/db.changelog-master.yaml`, `.../people/...`, `.../timework/...` (each includes the **entire legacy monolith** `hrms/changes/001..020` and then drops foreign tables), `tenant-registry/.../provision/DatabaseCloner.java`, `SettingsDistributor.java`, `AuditDataPartitioner.java`, `QiiBusinessSeeder.java`, `ImpitBusinessSeeder.java`, `PracticeBusinessSeeder.java`, `ProvisioningService.java:103-121` (seed into People, then clone tables into Leave and Time-Work over JDBC)
- **Problem:** The registry is a hidden shared-database integration point that knows every service's tables. People, Leave and Time-Work schemas are built by creating other domains' tables and then dropping them. A change in one service's schema requires changing and redeploying the registry.
- **Fix:** Move each service's changelog into that service's module (`<svc>/src/main/resources/db/changelog`). Run it with a per-service migration job (Liquibase/Flyway container or init job) using `<svc>_migrator`. Rewrite each service's baseline as a clean changelog containing only its own tables (squash), with an upgrade path for existing DBs. Move seeding into each service as idempotent seed commands triggered by a `tenant.provisioned` event. The registry only orchestrates through events and APIs, never JDBC into service DBs.
- **Done when:** `tenant-registry` has no changelog or JDBC code for service DBs, and each service migrates itself from an empty DB in CI.

### DB-04 — Fresh provisioning fails; migrations can't build a new environment; provisioning isn't atomic
- **Severity:** CRITICAL · **Guide:** §5 MUST (expand-and-contract, test upgrades), §10 (restore into isolated environment), §12 stage 5
- **Where:** `tenant-registry/.../tenant-changelog/people/changes/003-rename-audit-log.yaml`, `leave/changes/003-*.yaml`, `leave/changes/004-*.yaml`, `timework/changes/003-*.yaml`, `timework/changes/004-*.yaml` (`DO $$ ... $$` blocks in a `sql` change with no `splitStatements: false`); `ProvisioningService.provision` (7 DBs created sequentially, no compensation)
- **Evidence (LIVE):** `POST /api/registry/provision {"tenantId":"audit_probe","businessPack":"qii_creative"}` → `Liquibase failed ... people-003-rename-audit-log ... Unterminated dollar quote`. It left an orphan `people_audit_probe` DB with no routing rows (cleaned up by the auditor).
- **Fix:** Add `splitStatements: false` (and `endDelimiter` where needed) to every changeset containing `DO $$`, or replace them with plain Liquibase changes plus preconditions. Make provisioning an idempotent saga with recorded steps, safe retry and compensation, not a single HTTP call. Add a CI job that migrates every service from an empty DB and from the previous release's schema.
- **Done when:** CI provisions a brand-new tenant end-to-end from empty Postgres and a retry after a mid-way failure succeeds.

### DB-05 — Destructive, non-expand/contract migrations and out-of-band SQL
- **Severity:** HIGH · **Guide:** §5 MUST (expand-and-contract; test upgrades from previous production schema; rollback-compatible or reviewed forward recovery)
- **Where:** `payroll/changes/004-payroll-audit-log.yaml` (`DROP TABLE IF EXISTS audit_log CASCADE`), `leave/changes/002-drop-foreign-domain-tables.yaml`, `timework/changes/002-*`, `people/changes/003-*` (renames and drops with `CASCADE`), `scripts/migrate-011-permission-catalog.sql`, `migrate-012-employment-history.sql`, `fix-org-manage-gates.sql`, `upgrade-techshop-*.sql` (manual schema changes outside Liquibase), `FleetSchemaMigrationRunner.java` (auto-migrates **all tenants on registry startup**, `FLEET_AUTO_MIGRATE` default `true`; failures are logged and readiness is set to ACCEPTING_TRAFFIC anyway, line ~72; follower timeout also accepts traffic)
- **Fix:** Rules: (1) expand (add nullable, dual-write), (2) migrate data, (3) contract in a later release. Forbid `DROP ... CASCADE` in changesets without a reviewed forward-recovery plan. Fold all `scripts/*.sql` into versioned changesets or delete them. Run migrations as a release step per service, not on registry boot. A failed migration must keep that service not-ready.
- **Done when:** CI runs "previous release schema → current" for every service, and no changeset contains an unreviewed `DROP`.

### DB-06 — Missing integrity constraints (no CHECKs, almost no FKs, weak natural keys)
- **Severity:** HIGH · **Guide:** §5 MUST ("uniqueness on ... leave operation keys and other natural keys. Use typed status columns, foreign keys within a service and check constraints")
- **Where (CODE):** a grep for `CHECK (` across all changelogs finds 0 results, and only 5 FK references exist in total (`hrms/001`, `hrms/003`). Specific gaps:
  - `leave_balance`: no `CHECK (balance_days >= 0)`.
  - `leave_balance_ledger`: no `operation_key`/`source_type`/`source_id`, no unique dedupe key, no FK to `leave_balance` or `leave_application`.
  - `leave_application`: no `EXCLUDE` constraint against overlapping active leave for the same employee; status is free text.
  - `leave_approval`, `leave_operation.status`, `payslip.status`, `finalize_operation.status/action`, `message_outbox.status`, `file_object.scan_status`, `attendance_record.status`: free-text VARCHAR, no CHECK or enum.
  - `finalize_operation.payslip_id`: no FK to `payslip`.
  - `salary_history`: no `UNIQUE (employee_id, effective_date)`, no `CHECK (amount >= 0)`, `pay_type`/`currency` unchecked.
  - `notifications`: `idx_notif_tenant_dedupe` is **not unique** (`notification/changes/001-notifications.yaml:66`), so concurrent creates duplicate.
  - `work_log`: no natural key; `work_task_assignee` uniqueness unknown.
- **Fix:** Add the constraints above (expand/contract with data cleanup first). Map statuses to Java enums with `@Enumerated(STRING)` plus DB CHECK.
- **Done when:** A schema test asserts the constraints exist, and inserting invalid rows fails at the DB.

### DB-07 — Legacy monolith archive DB retained with all domains' data
- **Severity:** HIGH · **Guide:** §3 (ownership), §15 (data retention decision)
- **Where (LIVE):** `archive_hrms_qii_creative` exists on `postgres-hrms`, owned by `platform`, readable by every service credential.
- **Fix:** Decide a retention policy (ADR), export the archive to encrypted cold storage with restricted access, then drop it from the runtime cluster.
- **Done when:** No runtime role can connect to any archive DB.

### DB-08 — Money precision and currency handling not specified
- **Severity:** MEDIUM · **Guide:** §5 MUST ("Store money as decimal with documented currency precision and rounding")
- **Where:** `payroll-service/.../application/PayrollCalculationService.java` (`setScale(2, HALF_UP)` for all currencies; default currency `"BDT"`), `payslip` table (no currency column; currency only inside `details_json`), many `BigDecimal.valueOf(n.doubleValue())` conversions (`PayrollFinalizeService.extractConsumedByType`, `HrmsPayrollFactsClient.normalizeBd`, `LeaveCoverCommandListener.toConsumed`)
- **Fix:** Add a `currency CHAR(3)` column on payslip and salary rows. Keep a per-currency minor-unit table (ISO 4217) and a documented rounding policy (ADR) applied at defined steps. Parse JSON numbers as BigDecimal (`DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS`), never via double.
- **Done when:** Golden tests for BDT, AUD and a 0- or 3-decimal currency match signed-off values.

### DB-09 — Time zones: server-default "now", client-chosen zones, free-text periods
- **Severity:** MEDIUM · **Guide:** §5 MUST ("Store instants in UTC and preserve location time zones for schedules and period boundaries")
- **Where:** 33 uses of `LocalDate.now()`/system zone (`WorkLogService`, `TaskService`, `ReportingLineService`, `LeaveApplicationService`, `LeaveBalanceService`, `PayrollFinalizeService` period gate, etc.), hard-coded `Asia/Dhaka` (`AttendanceService.java:601`, `AttendanceReportService`), `payslip.period_label VARCHAR(32)` and `leave_operation.period_label`
- **Fix:** Inject a `Clock`. Resolve "today" in the employee's location zone (stored on Location/Employee, owned by People and projected). Store periods as `period_start DATE`, `period_end DATE`, `zone`. Remove hard-coded zones.
- **Done when:** Tests at UTC midnight boundaries for Dhaka and Darwin employees produce correct work dates and finalize gates.

### DB-10 — Dynamic tenant routing and pool lifecycle risks (guide §11 row 5)
- **Severity:** MEDIUM (HIGH for availability) · **Guide:** §11 ("If retained, fix capacity and lifecycle races and handle Redis failure explicitly"), §1 ("fixed service database connections" for initial deployment)
- **Where:** `platform-common/.../tenant/TenantPoolLifecycle.java` (`evictIfNeeded` and `invalidate` close pools that in-flight requests may still use), `people-service/.../TenantDataSourceManager.java:94` (`redis.opsForValue().get` not guarded, so a Redis outage on cache miss fails the request with no registry fallback; same in all 7 copies), the registry `RestClient` with no timeouts, `maxOpenPools=50 × maximum-pool-size 5 × 7 services` (no connection budget vs Postgres `max_connections`), `TenancyMode.FIXED` supported **only** in payroll, tenant header not validated with `TenantIds.normalize` before routing lookup (`TenantFilter.java`), outbox scheduling depends on a Redis SCAN (`RoutedTenantScanner` swallows errors)
- **Fix:** Implement `FIXED` mode in every service and use it for the single-company launch (per guide). If DYNAMIC stays: reference-count pools (close only when idle after drain), guard Redis with fallback and a circuit breaker, validate tenant ids, set registry client timeouts, compute and document a connection budget, and write an ADR for the tenancy decision.
- **Done when:** Chaos tests (Redis down, registry down, pool eviction under load) pass without request errors beyond documented limits.

### DB-11 — Local artifacts committed
- **Severity:** LOW
- **Where:** `data/*.mv.db`, `file-service/data/*.mv.db`, `notification-service/data/*.mv.db`, `.tmp-smoke/**`, `.tmp-enrich/**`
- **Fix:** Remove them and add them to `.gitignore`.

---

## 4. Payroll (guide §5, §6, §11 rows 2-4, §13 PAY01-03, DATA01)

### PAY-01 — Payroll facts contract is broken after the split; every calculation fails
- **Severity:** CRITICAL · **Guide:** §6, §13 PAY03, §7 (typed contracts)
- **Where:** `payroll-service/.../application/PayrollCalculationService.java:119` (throws when `daysEmployed <= 0`), `people-service/.../application/PayrollFactsService.java:35-56` (returns no `daysEmployed`, `workingDaysInMonth`, `absentDays`, `leaveBalances` or `unpaidLeaveDays`), `time-work-service/.../api/rest/InternalTimeWorkBridgeController.java:110-138` (keys `regularHours`/`absenceDays`; absence and late **hard-coded 0**), `leave-service/.../api/rest/InternalLeaveBridgeController.java:53-62` (**stub**: `leaveDays: 0`, `consumedByType: {}`), `payroll-service/.../infrastructure/HrmsPayrollFactsClient.java` (expects `hoursWorked`, `absentDays`, `leaveBalances`), `people-service/.../client/LeaveClient.java:70` (swallows Leave errors and returns `{}`, so it fails open)
- **Evidence (LIVE):** `POST /payroll/calculate {"employeeId":12,"periodLabel":"2026-08"}` → `409 "Employee not employed in period: 12"`. People facts for employee 8 contain only `regularHours`/`absenceDays:0`/`leaveDays:0`/`consumedByType:{}`. `calculate-month` would skip the whole company. Existing payslips (with `daysEmployed:22`) date from before the 2026-11-08 split.
- **Fix:** Define a versioned, typed `PayrollFactsV1` contract (OpenAPI/proto) that every owner implements:
  - People: employment periods → `daysEmployed`, join and exit dates.
  - Time-Work: scheduled working days, worked hours, absence days, late count for the period, computed in SQL.
  - Leave: approved paid and unpaid leave days by type, and current balances.
  Payroll calls each owner directly (or reads event-fed projections). People must not orchestrate. Remove fail-open fallbacks; missing facts must fail the calculation explicitly.
- **Done when:** A contract test per provider validates the schema, and an integration test calculates a month for joiner, leaver, full-month and unpaid-leave employees and matches signed-off values.

### PAY-02 — The durable leave-cover finalize workflow never runs
- **Severity:** CRITICAL · **Guide:** §6 (finalization workflow), §13 PAY01/PAY02
- **Where:** `PayrollFinalizeService.java:135-230` (`if (consumedByType.isEmpty()) completeFinalizeLocal(...)`); `consumedByType` is derived from facts that are always empty (PAY-01)
- **Problem:** Finalize always takes the local shortcut. The outbox command → Leave → result path is never exercised in production, so PAY01 and PAY02 can't pass.
- **Fix:** Fix PAY-01, then add an end-to-end test: absence → consumedByType → command → ledger rows → result → FINALIZED.
- **Done when:** The E2E test shows exactly one ledger deduction per finalize under duplicate delivery and a crash after commit.

### PAY-03 — Finalized payroll history is overwritten; no immutable revisions or snapshots
- **Severity:** CRITICAL · **Guide:** §5 MUST ("retain finalized payroll revisions and rule/fact snapshots. Use explicit corrections and reversals rather than overwriting historical finals"), §6 ("On reopen, retain the original final ... Create a new editable revision only after reversal confirmation")
- **Where:** `payroll/changes/003-*.yaml` (`uq_payslip_employee_period` on `(employee_id, period_label)`, so one row per period), `PayrollFinalizeService.completeReopenLocal` (stores prior finals only inside `details_json.priorFinals`), `PayrollCalculationService.java:167` (`details = new LinkedHashMap<>(result.details())` **replaces** `details_json` on recalculation and wipes `priorFinals`/`retainedFinal`), no `payroll_run` table, no rule-config snapshot id on payslip
- **Problem:** After reopen and recalculation, the previously finalized amounts are gone. Auditors can't reconstruct what was paid.
- **Fix:** Model `payroll_run` (period, status), `payslip` (employee+run, unique), and an **append-only** `payslip_revision` (revision no, status, amounts, currency, facts_snapshot, rules_snapshot/config_version, operation_id, created_by, created_at). The unique key becomes `(employee_id, period, revision)`, with a partial unique index "only one current/editable revision". Finalized revisions are immutable (a DB trigger or privileges deny UPDATE). Corrections create a new revision. Version `payroll_config` (effective-dated) and reference it from each revision.
- **Done when:** A test finalizes, reopens, recalculates and refinalizes, and shows both finals are retrievable unchanged, with no row UPDATE on a finalized revision.

### PAY-04 — No concurrency control on payslips; calculate can clobber an in-flight finalize
- **Severity:** CRITICAL · **Guide:** §5 MUST ("protect competing ... state updates using optimistic version checks, explicit row locks or atomic conditional SQL. Define the retry behavior"), §13 DATA01
- **Where:** `payroll-service/.../domain/Payslip.java` (no `@Version`), `PayrollCalculationService.java:92` (only `FINALIZED` blocks recalculation; `FINALIZING`, `REOPENING` and `ACTION_REQUIRED` don't), `PayrollFinalizeService.startFinalize` (reads without lock), concurrent first-time calculates hit `uq_payslip_employee_period` and surface as 500
- **Failure scenario:** Finalize sets FINALIZING and sends the cover command. A parallel recalculate sets status=DRAFT with new amounts. The leave result arrives and finalizes the *new* amounts against the *old* cover.
- **Fix:** Add `@Version` to Payslip/revision. Make transitions conditional (`UPDATE ... WHERE id=? AND status=? AND version=?`) and allow calculate only from DRAFT, rejecting with 409 otherwise. On unique-key conflict, re-read and return the existing draft (idempotent). Document retry behavior.
- **Done when:** A concurrency test (N parallel calculates, plus calculate racing finalize) yields exactly one payslip and no state regression.

### PAY-05 — Leave-cover result handler doesn't check revision or current operation (stale results apply)
- **Severity:** HIGH · **Guide:** §6 ("The result handler checks operation ID and revision before changing payroll state"; "Ignore stale or already processed results safely"), §13 PAY02
- **Where:** `PayrollFinalizeService.java:290-320` (`onLeaveCoverResult` checks only that `op.status` isn't SUCCEEDED/FAILED; it never compares `op.revision` with the payslip revision or `payslip.operationId == op.operationId`)
- **Failure scenario:** The recovery job escalates an op to ACTION_REQUIRED, then a late success result still finalizes. A result for revision r1 finalizes revision r2.
- **Fix:** Apply only if `payslip.operationId == op.operationId && payslip.revision == op.revision && payslip.status in (FINALIZING|REOPENING|ACTION_REQUIRED-for-this-op)`. Otherwise record it as stale and ignore.
- **Done when:** A test delivers out-of-order, duplicate and stale results and the state stays correct.

### PAY-06 — ACTION_REQUIRED is a dead end: no safe replay, manual resolution, alerting or reconciliation
- **Severity:** HIGH · **Guide:** §6 ("Run reconciliation for pending operations and ledger totals. Alert on age thresholds; provide safe replay and manual resolution with reason and audit"), §10 ("Every manual financial correction requires reason, actor and before/after references")
- **Where:** `PayrollFinalizeService.java:159` (a retried finalize at the same revision throws "Finalize operation already exists for this revision"), `FinalizeRecoveryJob.java` (log-only "alert"; replays a new command every minute until 30 min; no ledger-total reconciliation; `@Transactional protected reconcileTenant()` is invoked via `this::reconcileTenant`, so the transaction is **not applied**, line 76)
- **Fix:** Add `POST /payroll/operations/{id}/resolve` (retry, mark resolved, or abandon) requiring reason, actor and MFA, writing an audit row with before/after. Make retry reuse the same operation key. Add a reconciliation job comparing payroll cover operations to Leave ledger rows by operation key. Emit metrics and alerts (OPS-01). Fix the self-invocation by moving the method to a separate bean.
- **Done when:** A business-rejected finalize can be resolved through the API with audit, and reconciliation reports zero discrepancies in tests.

### PAY-07 — Reopen doesn't create a new revision after reversal; escalation can desynchronize leave
- **Severity:** HIGH · **Guide:** §6 (reopen rules), §13 PAY02 ("Duplicate reversals restore leave once")
- **Where:** `PayrollFinalizeService.startReopen`/`completeReopenLocal` (mutates the same row), `FinalizeRecoveryJob.escalateActionRequired` (a reopen timeout sets the payslip back to FINALIZED while the reversal command may still apply later; a late success then moves it to DRAFT)
- **Fix:** Reopen = a separately keyed reversal operation. Only on confirmed reversal create new revision N+1 (DRAFT) and keep the final revision immutable. A timed-out reversal stays REOPEN_PENDING/ACTION_REQUIRED and is never silently restored.
- **Done when:** A test for duplicate reversals restores leave exactly once and the final revision is untouched.

### PAY-08 — Remote calls inside database transactions
- **Severity:** HIGH · **Guide:** §7 ("No long database transaction may wait on a remote call"), §11 row 2 ("Replace remote mutation inside local transaction")
- **Where:** `PayrollCalculationService.calculate` (`@Transactional` wraps gRPC `getEmployee` and HTTP `fetchFacts`), `calculateMonth` (one transaction across all employees with N×(gRPC+HTTP); exceptions caught inside a transaction already marked rollback-only can produce `UnexpectedRollbackException` and lose the whole batch; internal `calculate(req)` is self-invocation), `PayrollFinalizeService.java:437` (`notificationHook.notifyPayslipFinalized` is synchronous HTTP inside `@Transactional` result handling), `OffboardingService.applyStatusChange` (People → Leave HTTP inside a transaction), many Leave/Time-Work `@Transactional` methods calling People over HTTP (`PeopleEmployeeGateway`, `PeopleAuthzClient`)
- **Fix:** Fetch remote data **before** opening the transaction, then do a short local transaction. Run month calculation as a background job with one transaction per employee, recorded per-employee results, and resumability. Send notifications only through the outbox. Replace cross-service mutations with commands or events.
- **Done when:** An architecture test (ArchUnit) forbids HTTP or gRPC client calls from within `@Transactional` methods, and the month run survives individual employee failures.

### PAY-09 — Legacy synchronous finalize path still present, and silently "succeeds" without deducting leave
- **Severity:** HIGH · **Guide:** §11 row 2
- **Where:** `PayrollFinalizeService.finalize/reopen` (`if (!messagingEnabled) return calculationService.finalizeLegacySync(...)`), `PayrollCalculationService.java:315` (`applyLeaveCover` via HTTP inside a transaction), `people-service/.../PayrollFactsController.applyLeaveCover` (now returns **HTTP 200** `{"status":"GONE"}`), `HrmsPayrollFactsClient.applyLeaveCover` (treats `< 300` as success)
- **Failure scenario:** `PLATFORM_MESSAGING_ENABLED=false` → finalize marks `leaveCoverApplied=true` while no leave is deducted.
- **Fix:** Delete the legacy sync finalize/reopen and `apply-leave-cover`. Messaging is mandatory; fail startup if it's disabled in prod. Retired endpoints should return 410.
- **Done when:** No code path finalizes without the durable workflow.

### PAY-10 — Salary resolution and employment overlap don't meet policy
- **Severity:** HIGH · **Guide:** §6 ("Salary calculations must split effective-date segments when required by the approved policy and include leavers employed during the period"), §11 row 4, §13 PAY03
- **Where:** `PayrollCalculationService.java:513` (`resolveEffectiveSalary`: the single latest row on or before period end, no segmentation), `people-service/.../PayrollFactsService.java:62` (`listActiveEmployeeIds` filters `isActive`, which **excludes leavers** who left mid-period), `PayrollCalculationService` fallbacks (`workingDaysInMonth` default 22, currency default BDT), dual salary source of truth (`people.employee.base_salary/hourly_rate` **and** `payroll.salary_history`)
- **Fix:** Payroll owns salary (`salary_history` effective-dated, unique per date). Remove salary fields from People or make them read-only projections. Build segments from salary changes, employment periods (People) and schedule days (Time-Work); prorate per segment. The month run selects everyone whose employment period overlaps the payroll period. Remove magic defaults. Record the policy in an ADR signed by HR/Finance (§15).
- **Done when:** Golden tests for joiner, leaver, mid-month raise, unpaid leave and rounding match independently calculated values (PAY03).

### PAY-11 — Payroll policy and salary writes: untyped, unvalidated, unaudited, no maker-checker
- **Severity:** HIGH · **Guide:** §2 ("Do not silently choose business policies"), §5, §10 (manual financial corrections require reason/actor/before-after), §15 (approval separation)
- **Where:** `PayrollController.java:199` (`PUT /config` takes a `Map`, no validation, no audit, not versioned, and retroactively changes all drafts), `PayrollController.java:240` (`POST /salary-history` takes a `Map`; negative amounts, any currency or payType accepted; no audit; an HR user can edit their own salary), `CalculatePayrollRequest` overrides (`absenceDays`, `leaveBalances`, `daysEmployed`, `workingDaysInMonth`, `adjustmentAmount`) accepted from the client with no reason or audit
- **Fix:** Typed, validated DTOs. Audit every write with before/after, reason and actor. Effective-dated, versioned config. Separation of duties (no self-salary edit; approval workflow for config or salary changes above policy thresholds). Overrides only through a dedicated "adjustment" entity with reason and approval.
- **Done when:** Tests show invalid inputs rejected, every write audited, self-edit blocked, and the config version recorded on each revision.

### PAY-12 — Finalize API and job mechanics
- **Severity:** MEDIUM · **Guide:** §6 ("Return HTTP 202 with operationId and status URL"), §7
- **Where:** `PayrollController.finalize/reopen` (202 but no `Location`/status URL header; `wait=true` blocks the request thread up to 25 s and drains the outbox in-thread), `FinalizeRecoveryJob.replayLeaveCover` (enqueues a **new** event every minute for each stuck op, so the broker floods during an outage)
- **Fix:** Return `202` plus `Location: /payroll/operations/{id}` and the body `{operationId,status,statusUrl}`. Remove `wait`. Replay with exponential backoff and a max replay count per operation.
- **Done when:** The API contract test checks the 202 header and body, and the replay count is bounded.

### PAY-13 — Payroll lists and exports load all rows into memory; CSV export unsafe
- **Severity:** MEDIUM · **Guide:** §7 (bounded pages, deterministic sort, filter/aggregate in SQL, exports through background jobs)
- **Where:** `PayrollController.java:290` (`payslipRepository.findAll()` then Java filter and sort), `PayrollCalculationService.warnings`/`exportCsv` (`findAll()`), `csvEscape` (doesn't neutralize leading `= + - @`, so CSV formula injection is possible)
- **Fix:** Repository queries with `Pageable` and SQL filters; export as an async job (report service) streaming to object storage; prefix dangerous leading characters.
- **Done when:** A test with 100k payslips pages in < budget without loading all rows.

### PAY-14 — Notifications: double delivery path and dedupe ignores revision
- **Severity:** MEDIUM · **Guide:** §3 (Notifications own deduplication/retries)
- **Where:** `PayrollFinalizeService.enqueueNotification` (HTTP hook **and** outbox), `dedupeKey = "payslip-final-" + payslipId` (refinalizing after reopen is suppressed forever)
- **Fix:** Use the outbox only. Dedupe key = `payslip-final-{id}-r{revision}`.

### PAY-15 — Actor missing on async completions
- **Severity:** MEDIUM · **Guide:** §11 ("Derive actor server-side"), §10
- **Where:** `PayrollFinalizeService.completeFinalizeLocal/completeReopenLocal` (`AuthContext.getEmployeeId()` is null on the Rabbit consumer thread, so `finalizedBy` and the audit actor are null)
- **Fix:** Persist the initiating actor on `finalize_operation` at start and use it at completion.
- **Done when:** The audit row for an async finalize has a non-null actor (test).

---

## 5. Leave (guide §3 Leave, §5, §6, §13 DATA01/PAY01/PAY02)

### LV-01 — Leave balance and immutable ledger diverge
- **Severity:** CRITICAL · **Guide:** §3 ("balances and immutable balance ledger"), §13 DATA01 ("Concurrent balance changes match the ledger sum")
- **Where:** `leave-service/.../application/LeaveBalanceService.java`:
  - `:330` `adjustBySystem` clamps the balance to 0 but writes the full `delta` to the ledger.
  - `:177` `cascadeQuotaDelta` clamps the balance but writes the full `delta`.
  - `:208` `deductOnApprove` deducts only `min(days, balance)`, so approval succeeds over-balance silently.
  - `:152` `restoreOnCancel` restores the **full** days even if only part was deducted, so balance is created from nothing.
  - `LeaveCategorySyncService.java:107` creates initial balances with **no ledger row**.
- **Fix:** The balance is derived from the ledger (or updated atomically with exactly matching ledger rows). No clamping: reject with a business error instead. Approval must fail (or require an explicit negative-balance policy) when the balance is insufficient. Cancel restores exactly what was deducted (by referencing the original ledger entry). Initial quota and carry-forward write ledger rows. Add a nightly reconciliation `SUM(ledger) == balance`.
- **Done when:** A property-based test with random concurrent operations always gives `balance == SUM(ledger.delta)`.

### LV-02 — Invalid cover isn't rejected explicitly; business failures are lost and the message dead-letters
- **Severity:** CRITICAL · **Guide:** §6 ("Leave MUST record the command inbox key, ledger adjustments and result outbox in one local transaction. Duplicate commands return the recorded result. Reject invalid cover explicitly"), §13 PAY02 ("A business rejection remains visible and recoverable")
- **Where:** `leave-service/.../messaging/LeaveCoverCommandListener.java:89` (`@Transactional protected void handle` is called from inside a lambda on `this`, so the proxy is bypassed and no transaction applies), `:176` (`leaveCoverService.applyLeaveCover` is a `@Transactional` proxy inside `InboxGuard.processOnce`'s transaction; when it throws, the shared transaction is marked **rollback-only**; the catch block records FAILED and enqueues a result, but commit throws `UnexpectedRollbackException`; everything rolls back; retries ×5 then DLQ; payroll waits 30 min then ACTION_REQUIRED with no reason)
- **Fix:** Validate cover first (sufficient balance, valid type) without throwing out of a transactional proxy, or run the business attempt in `REQUIRES_NEW`/savepoint semantics. Persist `leave_operation(FAILED, reason)`, the inbox row and the result outbox in one committed transaction. Move `handle` into a separate bean so `@Transactional` applies.
- **Done when:** A test sends an over-balance cover; Leave commits FAILED and emits a rejection result, payroll shows ACTION_REQUIRED with the reason, and nothing goes to the DLQ.

### LV-03 — No reservations; check-then-act on balances; no conflict retry policy
- **Severity:** HIGH · **Guide:** §3 (Leave owns "reservations"), §5 MUST (protect competing balance updates; define retry)
- **Where:** `LeaveBalanceService.java:130` (`availableAfterPending` computed in Java from all applications; TOCTOU against concurrent applications or approvals), `LeaveBalance.@Version` exists but no code handles `ObjectOptimisticLockingFailureException` (surfaces as 500), `getOrCreate` race on the unique key
- **Fix:** Add a `leave_reservation` table (application id unique, days, status) created atomically with the application under a balance row lock (`SELECT ... FOR UPDATE`) or a conditional UPDATE. Approve converts the reservation into a ledger deduction; reject or cancel releases it. Add a retry with backoff (bounded) on optimistic conflicts, and upsert for getOrCreate.
- **Done when:** A concurrency test (N parallel applications exceeding the balance) approves at most the available days.

### LV-04 — Ledger rows aren't traceable or protected
- **Severity:** HIGH · **Guide:** §3 (immutable ledger), §5 ("leave operation keys"), §10
- **Where:** `hrms/changes/004-configurable-leave-types.yaml:119-160` (`leave_balance_ledger`: no `operation_key`, `source_type`, `source_id` or unique constraint); `actor_employee_id` null in system paths
- **Fix:** Add `operation_key UNIQUE`, `source_type` (APPLICATION/PAYROLL_COVER/QUOTA/CARRY/MANUAL), `source_id`, `actor`, `reason NOT NULL`. Revoke UPDATE/DELETE from the app role, or add an immutability trigger.
- **Done when:** A replayed operation key can't create a second ledger row (DB-enforced).

### LV-05 — Leave doesn't provide payroll-period leave facts
- **Severity:** HIGH · **Guide:** §3, §6
- **Where:** `InternalLeaveBridgeController.java:53` (stub)
- **Fix:** Part of PAY-01: compute approved paid and unpaid days by type within the period (SQL), plus balances, through a versioned contract.

### LV-06 — In-memory full scans
- **Severity:** MEDIUM · **Guide:** §7 (filter/aggregate in SQL), §11 row 6
- **Where:** `InternalLeaveBridgeController.hasPendingApprovals/reassignPendingApprovals` (`applicationRepository.findAll()`), `LeaveBalanceService.cascadeQuotaDelta` (`findAll()`), `LeaveCategorySyncService` (People `findAll()` over HTTP)
- **Fix:** Targeted repository queries (pending approvals by approver), batch updates.

### LV-07 — Offboarding approval reassignment is a synchronous cross-service mutation
- **Severity:** MEDIUM · **Guide:** §7 (retries only idempotent; durable workflows), best practice
- **Where:** `people-service/.../OffboardingService.java` (`leaveClient.reassignPendingApprovals` inside a People `@Transactional`), `LeaveClient` (no retry, no idempotency key)
- **Fix:** People publishes `employee.offboarded{reassignTo}` through the outbox, and Leave consumes it idempotently. Show a "pending reassignment" status until Leave confirms.
- **Done when:** Offboarding succeeds while Leave is down and converges once Leave recovers.

---

## 6. Time & Work (guide §3, §11 row 6)

### TW-01 — Attendance facts scan the entire tenant's attendance in memory; absence and late are hard-coded
- **Severity:** HIGH · **Guide:** §7, §11 row 6 ("Query bounded date ranges and database aggregates")
- **Where:** `InternalTimeWorkBridgeController.java:110-138` (`attendanceRecordRepository.findAll()` then Java filter; `absenceDays: 0`, `lateCount: 0`)
- **Fix:** A SQL aggregate per employee and period (`WHERE employee_id=? AND work_date BETWEEN ? AND ?`) computing hours, absences against the schedule, and lates. Expose it through the typed facts contract (PAY-01).

### TW-02 — The client chooses the time zone used for work date and lateness
- **Severity:** HIGH · **Guide:** §5 MUST (preserve location time zones), §4 (don't trust the client)
- **Where:** `time-work-service/.../application/AttendanceService.java:587-602` (`clientTimeZone` wins over tenant or location; fallback `Asia/Dhaka`), `AttendanceActionRequest.timeZone`
- **Failure scenario:** An employee sends `timeZone=Pacific/Kiritimati` to shift the work date or avoid "late".
- **Fix:** Derive the zone from the employee's assigned location/schedule (server-side). Treat client tz as informational only.
- **Done when:** A test where the client sends a wrong tz still records the correct work date and status.

### TW-03 — Task and work-log lists and reports in memory
- **Severity:** MEDIUM · **Guide:** §11 row 6
- **Where:** `TaskService.java` (5× `findAll()`), `WorkLogService.java` (2×), `WorkScheduleService.java`, `HolidayController.java`
- **Fix:** Paged SQL queries with bounded date ranges.

### TW-04 — TEAM scope pulls the whole People directory over HTTP per request
- **Severity:** MEDIUM · **Guide:** §7 (bounded budgets)
- **Where:** `time-work-service/.../application/DataScopeService.java` (`employeeRepository.findAll()` → `/api/internal/employees` unbounded), same in Leave
- **Fix:** People exposes `GET /internal/employees/{id}/subtree-ids` (SQL recursive CTE) or publishes reporting-line events consumed into a local projection.

---

## 7. Documents (guide §8, §13 DOC01)

### DOC-01 — Any employee can list and download every document in the tenant
- **Severity:** CRITICAL · **Guide:** §8 MUST ("authorize each download against document classification and employee scope"), §13 DOC01
- **Where:** `file-service/.../application/FileService.java:491-520` (`assertCanAccess`), `:522` (`canViewDocuments` = `documents:view` **or** `employees:view`, and every EMPLOYEE has `employees:view` with OWN scope), `list(all=true)` uses the same check, and classification (`CONFIDENTIAL`, `HR_ONLY`, `SENSITIVE`) is never enforced
- **Evidence (LIVE):** As employee 8 (role EMPLOYEE): `GET /files?all=true` → all 16 tenant files. `POST /files/16/download-url` → 200. `GET content` → 200 and 23,733 bytes of employee 11's document.
- **Fix:** Implement a document ACL matrix (classification × role × record scope). Subject employee and owner get access per policy; managers only TEAM-scoped and only allowed classifications; HR only explicitly allowed classifications. Use the shared scope-aware authorizer (SEC-14). Remove `employees:view` as a documents permission.
- **Done when:** DOC01 tests pass: wrong-role download fails, authorized HR succeeds, unscanned fails.

### DOC-02 — A missing requester identity bypasses ACLs
- **Severity:** HIGH · **Guide:** §8, §4
- **Where:** `FileService.java:491-494` (`if (requesterEmployeeId == null) return; // trusted internal / legacy`)
- **Problem:** Any request with the service key and no `X-Employee-ID` (internal callers, tenant-only tokens from `JwtSupport.issueToken(subject, tenant, ttl)`) gets unrestricted access.
- **Fix:** Internal callers must authenticate as a workload and get an explicit, narrowly scoped capability. A null user means deny.
- **Done when:** A request without user identity gets 401/403 on every file endpoint.

### DOC-03 — No real malware scanning; messaging-disabled path releases unscanned files
- **Severity:** HIGH · **Guide:** §8 MUST ("quarantine uploads ... then scan before release")
- **Where:** `FileService.java:634` (`assertNoMalwareMarkers`: EICAR string only), `:174` (`messaging-disabled-auto-release`)
- **Fix:** Integrate ClamAV/ICAP or a cloud AV in the scan worker, with a fail-closed timeout. Remove auto-release, so files stay quarantined until scanned. Define scan-failure handling (retry, alert, REJECTED after N attempts) and record it in an ADR.
- **Done when:** An EICAR and an AV-detected sample are rejected, and with the scanner down files remain unreleased.

### DOC-04 — Upload has no permission check and no attachment-authority validation
- **Severity:** HIGH · **Guide:** §8 MUST ("Validate file existence and attachment authority before creating the link")
- **Where:** `FileController.java:43-55` (no `@RequiresPermission`; client-supplied `employeeId` and `classification` are accepted as-is)
- **Problem:** Any employee can attach files to another employee's record and pick any classification (for example label HR files PUBLIC).
- **Fix:** Require `documents:create` with scope over the subject employee. Classification is set by policy or by authorized roles only. People-side links validate through `internalDescribe` (already present) plus the subject check.
- **Done when:** Uploading for an out-of-scope employee returns 403.

### DOC-05 — Download token weaknesses
- **Severity:** MEDIUM · **Guide:** §8 ("Issue short-lived URLs only after authorization; audit issuance and actual access")
- **Where:** `DownloadTokenService.java` (key defaults to `JWT_SECRET`; token sent in the query string, so it ends up in access logs; not single-use; verify doesn't bind the token to the presenting user)
- **Fix:** Dedicated key, one-time-use nonce stored in Redis or DB, bind to user and session, strip `token` from access logs, or use object-store presigned URLs on a private bucket.

### DOC-06 — Upload, scan and delete aren't atomic; retention and orphan cleanup are incomplete
- **Severity:** MEDIUM · **Guide:** §8 MUST ("define retention, deletion, scan-failure handling and orphan cleanup. A failed metadata commit must not leave untracked objects indefinitely")
- **Where:** `FileService.upload` (not `@Transactional`: the metadata, audit and scan outbox commit separately), `processScan` (copies to `released/` and deletes quarantine **before** saving metadata; a DB failure leaves metadata pointing at a deleted key; no guard against concurrent scans), `delete` (hard delete, no retention or legal hold), `purgeRejectedOlderThanRetention` (only REJECTED), no storage-side sweep for objects without metadata
- **Fix:** One transaction for metadata, audit and outbox. Scan: DB state change first (optimistic version), then storage copy, then commit, then delete source (idempotent). Soft delete plus retention schedule per classification (ADR). A periodic orphan sweep comparing bucket listing vs metadata.
- **Done when:** Fault-injection tests at each step leave no untracked or dangling objects after the sweep.

### DOC-07 — Object storage uses root credentials everywhere
- **Severity:** MEDIUM · **Guide:** §9 (least privilege, private storage)
- **Where:** `.env.example` (`MINIO_ACCESS_KEY` must equal `MINIO_ROOT_USER`), the registry and file-service use root, ports 9000/9001 published
- **Fix:** A per-service MinIO/S3 user with a policy limited to its buckets; the registry uses a separate admin credential; keep storage private.

---

## 8. Reporting (guide §3 Reporting, §7 Lists & exports, §11 row 6, §13 REP01)

### RPT-01 — Attendance export truncates history (REP01)
- **Severity:** HIGH · **Guide:** §7 ("Export complete date ranges through background jobs"), §13 REP01
- **Where:** `report-service/.../application/AttendanceReportService.java:65` (`hrms.attendanceForEmployee(actor, target, 1, 500)`: a single page; the server caps size at 100 (`PageQuery.MAX_SIZE`); sort `workDate,asc`)
- **Failure scenario:** After about 100 attendance days, every later month exports as all `NO_RECORD`.
- **Fix:** Request the exact date range (`from`/`to`) from Time-Work and stream all pages, or better, build from a local read model (RPT-02). Run as a background job writing to object storage.
- **Done when:** A test with 3+ years of attendance exports any old or recent month completely.

### RPT-02 — Reporting isn't event-driven (no read models, checkpoints or rebuild)
- **Severity:** HIGH · **Guide:** §3 ("Reporting: Event-driven read models, export jobs and projection checkpoints"), §7 Ordering ("Provide replay and projection rebuild procedures")
- **Where:** `report-service/.../infrastructure/HrmsGatewayClient.java` (synchronous HTTP to People and Time-Work at export time); Time-Work and Leave publish no domain events (`attendance.*`, `leave.*`)
- **Fix:** Owners publish domain events (attendance recorded or corrected, leave approved or cancelled, payslip finalized) through the outbox. Reporting consumes them into its own tables with a per-stream checkpoint and version and gap detection, plus a documented rebuild procedure.
- **Done when:** An export works with Time-Work down, and a rebuild from replay reproduces identical results.

### RPT-03 — Report authorization ignores record scope; hard-coded zone
- **Severity:** MEDIUM · **Guide:** §4 (scope)
- **Where:** `AttendanceReportService.generateMonthlyCsv` and `canListAllExports` (`employees:edit` or `payroll:run` ⇒ any employee), `DEFAULT_ZONE = Asia/Dhaka`
- **Fix:** Use the shared scope-aware authorizer; take the zone from the employee location.

---

## 9. Messaging / Outbox / Inbox (guide §1 Messaging, §7 Retries/Events/Outbox/Ordering, §13 OPS01)

### MSG-01 — Outbox retry has no backoff and gives up after ~20 seconds; FAILED rows are never replayed
- **Severity:** HIGH · **Guide:** §7 ("exponential backoff and jitter with limits ... Route exhausted messages to a monitored dead-letter queue"), §13 OPS01 ("Broker ... interruptions recover predictably")
- **Where:** `platform-messaging/.../outbox/OutboxRelay.java:145` (`markFailedAttempt`: attempts++ and back to PENDING immediately; `outboxMaxAttempts=20`, `outboxPollMs=1000` → FAILED after ~20 polls), no `next_attempt_at`, no replay endpoint or job for FAILED
- **Failure scenario:** A 30-second RabbitMQ restart permanently strands finalize commands and results, notifications and scan jobs.
- **Fix:** Add a `next_attempt_at` column with exponential backoff plus jitter (cap about 5 min). Treat broker-unavailable errors as "don't count an attempt". Never auto-FAIL for transport errors. Alert on age. Provide an admin replay with audit.
- **Done when:** A test stops the broker for 10 minutes and everything is delivered after recovery.

### MSG-02 — Inbox claims the event *after* running the handler
- **Severity:** HIGH · **Guide:** §7 ("Deduplicate consumers; acknowledge after durable processing")
- **Where:** `platform-messaging/.../outbox/InboxGuard.java:41-60` (`existsByEventId` → `handler.get()` → insert); unique on `event_id` only (not `(consumer_name, event_id)`)
- **Problem:** Concurrent duplicate deliveries both run the handler. DB side effects are rolled back via the constraint, but non-DB side effects (HTTP calls, storage writes, in-memory cache) run twice. The duplicate path returns through an exception or rollback rather than cleanly.
- **Fix:** Insert the inbox row first (`INSERT ... ON CONFLICT DO NOTHING RETURNING`) inside the transaction, then run the handler. Make the unique key `(consumer_name, event_id)`. Forbid non-transactional side effects in handlers (outbox only).
- **Done when:** A concurrent duplicate-delivery test shows exactly one handler execution.

### MSG-03 — `@Transactional` on self-invoked or protected methods isn't applied
- **Severity:** HIGH · **Guide:** §6/§7 (one local transaction)
- **Where:** `LeaveCoverCommandListener.java:89` (`handle`), `FinalizeRecoveryJob.java:76` (`reconcileTenant`), `PayrollCalculationService.calculateMonth → calculate` (self-call)
- **Fix:** Move these into separate beans or use `TransactionTemplate` explicitly. Add an ArchUnit rule forbidding `@Transactional` on non-public methods.

### MSG-04 — Broker topology isn't production-grade
- **Severity:** HIGH · **Guide:** §1 ("RabbitMQ or a managed durable broker with persistent storage"), §9 (keep broker private, least privilege), §10 (back up required broker state)
- **Where (LIVE):** `rabbitmqctl list_queues` → all **classic** queues, 1 consumer each, no policies. A single `platform` user with the `administrator` tag and `.*` configure/write/read used by every service. Single node, ports 5672/15672 published. Every service declares every queue (`PlatformRabbitConfig`). `missing-queues-fatal: false`.
- **Fix:** Quorum queues (or a managed broker with HA). A per-service broker user with permissions limited to the queues it consumes and the exchange or routing keys it publishes. Topology owned by IaC (definitions from secrets at deploy time). Policies for DLQ max-length or TTL and alerting. TLS (5671). Management UI private. Set `missing-queues-fatal: true` in prod. Configure listener concurrency.
- **Done when:** A service user can't publish or consume outside its contract (test), and a broker node loss doesn't lose messages.

### MSG-05 — Event ordering, versioning and schema evolution not handled
- **Severity:** MEDIUM · **Guide:** §7 ("Include ... aggregateVersion ... Partition or route by aggregate where order matters. Detect stale versions and gaps. Provide replay and projection rebuild procedures")
- **Where:** `aggregateVersion` set to `1L` (file scan), revision (payroll) or `updatedAt` millis (People); no consumer checks it (grep shows none); `schemaVersion` always 1 with no version branching; one direct exchange; no event contract docs or tests
- **Fix:** A monotonic per-aggregate version stored on the aggregate. Consumers store the last applied version per aggregate and ignore stale ones or detect gaps. Publish AsyncAPI/JSON-Schema contracts with consumer contract tests (Pact or schema-compat check in CI).

### MSG-06 — Relay lease shorter than worst-case batch; relay runs on request threads
- **Severity:** MEDIUM · **Guide:** §7, §9 (bounded worker budgets)
- **Where:** `OutboxRelay.java:97` (`claimBatch` lease = max(30 s, 6 × confirm-timeout) while 50 rows × 5 s sequential confirm = 250 s, so other replicas reclaim in-flight rows and publish duplicates); `drainBatch()` invoked in `afterCommit` hooks and in the finalize wait loop on HTTP threads
- **Fix:** Publish the batch asynchronously and wait for confirms together (correlated futures), and keep lease > batch duration (or renew it). Drain only on scheduler threads (nudge with a signal instead of calling inline).

### MSG-07 — No outbox/inbox retention; draining depends on Redis
- **Severity:** MEDIUM · **Guide:** §9 (monitor outbox backlog), §10
- **Where:** `message_outbox`/`message_inbox` grow forever; `TenantOutboxScheduler` + `RoutedTenantScanner` (Redis SCAN; errors swallowed, so a Redis outage silently stops all publishing)
- **Fix:** Purge PUBLISHED rows older than N days and inbox rows past the dedupe window (with an index). In FIXED mode, drain without Redis; in DYNAMIC mode, fall back to the registry tenant list and alert.

### MSG-08 — Employee events broadcast salary and PII to services that don't need them
- **Severity:** MEDIUM · **Guide:** §9 (exclude sensitive contents), data minimization
- **Where:** `people-service/.../messaging/EmployeeProjectionPublisher.java:98-123` (`baseSalary`, `hourlyRate`, `phone`, `personalEmail`, `customFields` to the Leave and Time-Work queues, which only invalidate caches)
- **Fix:** Use a thin event (`employeeId`, `version`, changed fields), or a projection payload restricted to the fields each consumer needs. Salary never leaves Payroll or People-HR.

### MSG-09 — DLQs aren't monitored and have no redrive procedure
- **Severity:** LOW · **Guide:** §7, §9 (monitor dead-letter volume)
- **Fix:** Alert on DLQ depth > 0, and provide a redrive tool and runbook.

---

## 10. Contracts & resilience (guide §7)

### API-01 — Synchronous calls lack deadlines, retries and circuit breakers
- **Severity:** HIGH · **Guide:** §7 ("Set explicit HTTP and gRPC deadlines. Bound connection, request and worker budgets"; "Use exponential backoff and jitter with limits")
- **Where:** `payroll-service/.../grpc/HrmsEmployeeClient.java:53` (blocking stub without `withDeadlineAfter`), the registry `RestClient` in every `TenantDataSourceManager` and in `RegistryController` (no timeouts), `people-service/.../client/LeaveClient.java`/`TimeWorkClient.java`/`FilesGatewayClient.java` (RestClient defaults), `api-gateway/application.yml` (no `spring.cloud.gateway.httpclient.connect-timeout`/`response-timeout`), no Resilience4j anywhere, no bulkheads. The sync chain Payroll → People → (Leave + Time-Work) multiplies latency and failure.
- **Fix:** A deadline on every client (gRPC `withDeadlineAfter`, HTTP connect and read timeouts), retries only for idempotent GETs with backoff and jitter, circuit breakers and bulkheads (Resilience4j), and gateway per-route timeouts. Document the latency budget per call chain.
- **Done when:** A fault-injection test (slow People) doesn't exhaust Payroll threads, and calls fail fast within budget.

### API-02 — Service responsibilities leak; People is a synchronous hub and SPOF
- **Severity:** HIGH · **Guide:** §3 (authoritative responsibilities), best practice
- **Where:** `people-service/.../PayrollFactsService` (People aggregates Leave and Time-Work facts for Payroll), `people-service/.../AuditFederationService` (fan-out over HTTP), salary fields in People (PAY-10), Leave/Time-Work/Files/Reports calling People over HTTP on **every** request for authz (`PeopleAuthzClient`, `HrmsPermissionClient`, `PeopleDirectoryClient`), `HrmsPermissionClient` calling People `/api/me` impersonating the user with the service key
- **Fix:** Each owner exposes its own facts. Authorization data reaches other services through signed token claims (roles and scopes) or an event-fed local projection with explicit staleness bounds. Audit goes to a central append-only audit sink through events (or the reporting service), not synchronous fan-out. People stops orchestrating other domains.
- **Done when:** With People down, Leave and Time-Work reads still work within the documented staleness bounds, and payroll facts come from the owners.

### API-03 — The common error envelope isn't used; client errors become 500 and leak internals
- **Severity:** HIGH · **Guide:** §7 ("typed DTOs, validation and a common error envelope with code and correlationId")
- **Where:** `platform-common/.../api/ApiError.java` is used by **no** `ApiExceptionHandler` (grep). Handlers return ad-hoc `{error, cause}` maps. A catch-all `Exception.class → 500` (e.g. `tenant-registry/.../api/ApiExceptionHandler.java:33`) maps `ResponseStatusException(401)` to 500.
- **Evidence (LIVE):** An unknown path through the BFF → `500 {"cause":"No static resource ...","error":...}`. A bad path variable → `500 "Failed to convert value ..."`. The registry with a wrong admin key → `500 {"error":"401 UNAUTHORIZED ..."}`.
- **Fix:** A shared `@RestControllerAdvice` in a library producing `ApiError{code, message, correlationId, details}` with correct status mapping (`ResponseStatusException`, `NoResourceFoundException` → 404, `MethodArgumentTypeMismatch` → 400, `AccessDenied` → 403, optimistic lock → 409, unknown → 500 with no internal message).
- **Done when:** A contract test covers each error class across services.

### API-04 — Untyped DTOs, no API versioning, no published contracts
- **Severity:** MEDIUM · **Guide:** §7 ("Publish OpenAPI and protobuf contracts. Version incompatible changes. Use typed DTOs, validation"), §2, §14
- **Where:** 37 endpoints take `@RequestBody Map<String, Object>` (e.g. `PayrollController` config, salary and finalize; `OrgController`; `TeamController`; `LocationController`; `InternalLeaveBridgeController`). 0 routes under `/api/v1`. `hrms-api/src/main/proto/employee.proto` uses package `hrms` (unversioned). No OpenAPI files committed; specs only generated at runtime.
- **Fix:** Typed request records with Bean Validation, URI or media-type versioning, proto package `hrms.v1`, generated OpenAPI committed per service, and CI breaking-change detection (openapi-diff, buf breaking).

### API-05 — Inconsistent pagination and unbounded lists
- **Severity:** MEDIUM · **Guide:** §7 ("one zero-based page convention with bounded size and deterministic sort. Filter and aggregate in SQL")
- **Where:** `platform-common/.../api/PageQuery.java:29` (1-based: `Math.max(page, 1)`) vs `file-service` (0-based), 87 `findAll()` usages across services (top: `TaskService`, `SettingsController`, `OrgController`, `AttendanceService`, `EmployeeService`, `RolesController`, `LocationController`), `/api/internal/employees` returns the full directory
- **Fix:** Zero-based everywhere, max size enforced, a stable tiebreaker sort (id), and DB-level filtering. Internal list endpoints paged.

### API-06 — No rate limiting or request limits at the edge
- **Severity:** MEDIUM · **Guide:** §7 (bounded budgets), §9
- **Where:** `api-gateway/application.yml` (no `RequestRateLimiter`, no max body size besides file-service multipart)
- **Fix:** Redis rate limiter per user and IP (stricter on auth endpoints), request size limits, and timeouts (API-01).

---

## 11. Operations & delivery (guide §2, §9, §10, §14)

### OPS-01 — No metrics, tracing, structured logs or correlation in logs
- **Severity:** HIGH · **Guide:** §9 MUST ("collect structured logs, metrics and traces. Propagate correlation IDs across HTTP, gRPC and messages"; "monitor latency, errors, saturation, DB pool usage, queue lag, outbox backlog, dead-letter volume, scan failures and payroll reconciliation discrepancies")
- **Where:** No Micrometer/Prometheus/OpenTelemetry/logstash dependency in any `build.gradle`; actuator exposes only `health,info`; no MDC usage (correlation id never appears in logs); gRPC calls don't propagate `x-correlation-id`
- **Fix:** Micrometer + Prometheus registry, OpenTelemetry tracing (HTTP, gRPC, AMQP), JSON logs with MDC (`correlationId`, `tenantId`, `employeeId`; never tokens or document contents), and custom metrics (outbox pending age and count, DLQ depth, inbox duplicates, finalize ops by status and age, scan failures, reconciliation mismatches, Hikari pool usage). Dashboards and alert rules as code.
- **Done when:** A trace spans BFF → gateway → payroll → broker → leave → broker → payroll, and alerts fire in a staging drill.

### OPS-02 — No CI; images aren't built once and promoted; no scanning; unpinned images
- **Severity:** HIGH · **Guide:** §2 (CI configuration), §9 MUST ("build each artifact once in CI and promote the same image digest"), §14 ("CI runs unit, PostgreSQL integration, contract, authorization and critical frontend tests; it scans dependencies and images and retains reports")
- **Where:** No `.github/`, `.gitlab-ci.yml` or Jenkinsfile. Every `*/Dockerfile` builds from source with `COPY . .` (no `.dockerignore`). `eclipse-temurin:21-jdk`/`21-jre` and `minio/minio:latest` are unpinned (no digest). No SBOM or vulnerability scan.
- **Fix:** A CI pipeline: build → unit → Testcontainers integration → contract → authz negative tests → frontend critical tests → OWASP dependency-check/Trivy (fail on high) → build an image once (Jib or a multi-stage build with a pinned digest) → sign (cosign) → push digest → promote the same digest across environments. Retain reports.
- **Done when:** A PR can't merge without a green pipeline, and prod manifests reference image digests only.

### OPS-03 — Unsupported framework versions
- **Severity:** HIGH · **Guide:** §1 ("Select supported dependency releases through a compatibility and vulnerability check")
- **Where:** `build.gradle` (`org.springframework.boot 3.3.5`, `spring-cloud-dependencies 2023.0.3`; both lines are past OSS support as of 2026-11), `springdoc 2.6.0`, `grpc 1.68.1`, `protobuf 3.25.5`, `jjwt 0.12.6`, frontend `next 16.4.3`
- **Fix:** Upgrade to a currently supported Spring Boot and matching Spring Cloud release train. Run a dependency vulnerability scan and record the compatibility matrix in an ADR.

### OPS-04 — Test suite doesn't cover the guide's acceptance gates
- **Severity:** HIGH · **Guide:** §11 row 8 ("Replace with behavioral gates"), §12 exit conditions, §13, §14
- **Where (LIVE build):** `./gradlew build` → 103 unit tests pass; **0 tests** in `file-service`, `notification-service`, `tenant-registry`, `platform-messaging`; no Testcontainers or `@SpringBootTest`/`@DataJpaTest`; acceptance checks exist only as console scripts (`.tmp-smoke/*.mjs`, `scripts/qa-*.mjs`), which the guide rejects
- **Fix:** Automated suites for SEC01, DATA01, PAY01-03, REP01, DOC01 and OPS01 on PostgreSQL and RabbitMQ Testcontainers; concurrency tests; crash and duplicate-delivery tests (kill after commit, redeliver); authz negative matrix; migration tests (empty → current, previous → current); contract tests.
- **Done when:** All §13 tests run in CI with stored evidence.

### OPS-05 — Deployment hardening missing (exposed ports, no TLS, no limits, no graceful shutdown)
- **Severity:** HIGH · **Guide:** §9 MUST ("Run containers as non-root with resource limits and least filesystem permissions"; "expose liveness and readiness separately, handle graceful shutdown and test rolling deployments"; "keep databases, broker, object storage and service ports private")
- **Where:** `docker-compose.yml` publishes 5433-5436, 6379, 5672, 15672, 9000-9001 and every service port. Redis has no password. No TLS: gRPC `negotiationType: plaintext`, HTTP between services, JDBC without `sslmode`. No CPU or memory limits, no JVM memory flags, no read-only root FS. No `server.shutdown: graceful` or `spring.lifecycle.timeout-per-shutdown-phase` in any service. Readiness doesn't reflect DB, broker or Redis dependencies. App containers have no healthcheck, and `depends_on: service_started`. No production deployment manifests (Helm or k8s or platform config) exist.
- **Fix:** Production manifests for a managed container platform: private networking (only HTTPS ingress → BFF public), TLS or mTLS everywhere, Redis AUTH+TLS, resource requests and limits, a `-XX:MaxRAMPercentage` JVM flag, read-only FS, non-root (already done in the Dockerfiles), graceful shutdown with drain, readiness that includes critical dependencies (with care against cascading failure), PodDisruptionBudgets and replicas ≥ 2, and a rolling deploy test.
- **Done when:** A staging deploy proves only ingress is public and a rolling deploy causes no failed requests.

### OPS-06 — No backup, restore, recovery runbooks or decision records
- **Severity:** HIGH · **Guide:** §2 (runbooks, restore report), §10 (backups, PITR, restore drill, owners and escalation, replay or rollback documentation), §14, §15
- **Where:** `docs/` contains only `ARCHITECTURE_SPLIT.md`, `QII_HRMS_PLAN.md`, and Postman. There is no backup config, PITR, object-storage backup, broker definitions backup, restore drill, runbook, SLO/RTO/RPO or ADR.
- **Fix:** Write ADRs for the §15 decisions (IdP/MFA, payroll and leave policy, tenancy, SLO/RTO/RPO, hosting/broker, retention). Add encrypted backups plus PITR per DB, MinIO versioning and replication, and broker definitions export. Write runbooks for identity outage, broker outage, DB outage, stuck payroll, failed migration, malicious upload and credential exposure. Perform a restore drill into an isolated env verifying document references and payroll ↔ leave ledger reconciliation (depends on DB-04 being fixed).
- **Done when:** A restore drill report with timings and limitations is committed in `docs/ops/`.

### OPS-07 — Control plane in startup and request paths
- **Severity:** MEDIUM · **Guide:** §1 ("Keep future tenant provisioning outside the request path")
- **Where:** `FleetSchemaMigrationRunner` (all-tenant migration on every registry boot; Redis lock release isn't atomic, get then delete), registry `/token` synchronously calls People `auth-check` (login depends on registry + People), `tenant_routing` alias `hrms` still active
- **Fix:** Migrations as an explicit release job; login through the IdP (SEC-01) so the registry is out of the login path; atomic lock release (Lua compare-and-delete); remove the `hrms` alias after cutover.

### OPS-08 — Security and infrastructure code is copy-pasted per service
- **Severity:** MEDIUM · **Guide:** §14 (owned, documented components), maintainability
- **Where:** 7 different copies of `TenantDataSourceManager`, `TenantFilter`, `TenantRoutingDataSource`, `RoutingInvalidateListener`, `TenantOutboxScheduler`, `AuditService`, plus `RbacInterceptor`/`RequiresPermission`/`DataScopeService` in 3-5 services (the md5 of `TenantDataSourceManager` differs in all 7, so they have already drifted)
- **Fix:** Move these into `platform-common`/`platform-security`/`platform-tenancy` starters with auto-configuration. Services only configure them.

### OPS-09 — Required decisions aren't recorded
- **Severity:** MEDIUM · **Guide:** §2 ("Record decisions for identity provider, broker, hosting, tenancy, payroll policy and recovery targets. Do not silently choose business policies"), §15
- **Where:** Business policy is hard-coded silently: 22 working days, BDT, Asia/Dhaka, clamp-to-zero leave, partial deduction on approve, 7-day sessions, 12-hour revocation, 30-minute ACTION_REQUIRED threshold.
- **Fix:** Write the ADRs and move the values into effective-dated, approved configuration.

---

## 12. Audit (guide §10, §11 row 7)

### AUD-01 — Audit coverage gaps and synchronous federation
- **Severity:** MEDIUM · **Guide:** §11 ("Derive actor server-side; align access policy and audit with authoritative operations"), §10
- **Where:** No audit on `PUT /payroll/config`, `POST /payroll/salary-history`, registry control-plane actions, or Leave internal reassign. Actor is null on async paths (PAY-15). `people-service/.../AuditFederationService` aggregates through synchronous HTTP to 6 services (partial or failed when any is down; no pagination guarantees). Audit rows are mutable by the app role.
- **Fix:** Audit every authoritative write (actor, reason, before/after, correlationId). Ship audit events through the outbox to an append-only store owned by one service (or reporting). Deny UPDATE/DELETE on audit tables to app roles.

### AUD-02 — No audit retention policy
- **Severity:** MEDIUM · **Guide:** §15 (data retention: audit)
- **Fix:** An ADR plus a partitioned retention job.

### AUD-03 — IP and user-agent not captured consistently
- **Severity:** LOW · **Where:** `*_audit_log.ip` column exists but most `AuditService.record` calls pass none.
- **Fix:** Capture from the trusted gateway-forwarded header.

---

## 13. Fix order (maps to guide §12 stages)

**Stage 1 — Foundation / security (exit: SEC01 + cross-role negative tests pass)**
SEC-02 → SEC-07 → SEC-01 → SEC-03 → SEC-14 → DOC-01 → DOC-02 → DOC-04 → SEC-04 → SEC-05 → SEC-06 → SEC-12 → SEC-13 → SEC-08 → SEC-09 → SEC-10 → SEC-11 → SEC-15 → SEC-16 → SEC-17 → SEC-18 → API-03

**Stage 2 — Data integrity (exit: DATA01)**
DB-01 → DB-02 → DB-04 → DB-03 → DB-05 → DB-06 → LV-01 → LV-03 → LV-04 → PAY-04 → PAY-03 → DB-08 → DB-09 → DB-07

**Stage 3 — Distributed workflows (exit: PAY01/PAY02 crash + duplicate tests)**
MSG-03 → MSG-02 → MSG-01 → LV-02 → PAY-05 → PAY-06 → PAY-07 → PAY-08 → PAY-09 → PAY-12 → PAY-15 → MSG-04 → MSG-05 → MSG-06 → MSG-07 → MSG-08 → MSG-09 → LV-07

**Stage 4 — Features & reporting (exit: PAY03, REP01, DOC01)**
PAY-01 (+LV-05, TW-01) → PAY-02 → PAY-10 → PAY-11 → TW-02 → RPT-01 → RPT-02 → RPT-03 → DOC-03 → DOC-05 → DOC-06 → DOC-07 → PAY-13 → PAY-14 → LV-06 → TW-03 → TW-04 → API-01 → API-02 → API-04 → API-05 → API-06

**Stage 5 — Operations (exit: restore + release-recovery drills)**
OPS-02 → OPS-04 → OPS-03 → OPS-01 → OPS-05 → DB-10 → OPS-07 → OPS-08 → OPS-06 → OPS-09 → AUD-01 → AUD-02 → AUD-03 → DB-11

**Stage 6 — Pilot:** parallel payroll comparison with business sign-off (guide §12).

---

## 14. What is already good (keep it)

- Clear service split with a database per service per tenant at the naming level (`people_*`, `leave_*`, `timework_*`, `payroll_*`, `files_*`, `notif_*`, `reports_*`).
- A shared outbox/inbox library with publisher confirms (`CORRELATED`), mandatory returns, persistent delivery, `FOR UPDATE SKIP LOCKED` claim and stale-lease reclaim.
- Durable queues with DLX/DLQ per queue, and listener retry → DLQ (`default-requeue-rejected: false`).
- The finalize state machine already has operation id/key, a unique `operation_key` in both Payroll and Leave, and idempotent re-emit of recorded results.
- The gateway strips client trust headers, rejects path traversal and blocks `/internal/**`, and session revocation is fail-closed when Redis is down.
- Upload validation does magic-byte and structure checks, uses quarantine/released prefixes, and has short-lived download tokens with issuance and access audit.
- `LeaveBalance` has `@Version`; `uq_payslip_employee_period` exists; containers run as non-root; liveness and readiness probes are enabled.

---

## Appendix A — Live verification log (2026-11-09, local stack)

All probes were run against `qii_creative` and were **non-mutating**, apart from login sessions and audit rows created by download-URL issuance. No payslip, leave or employee data was changed.

| # | Probe | Result |
|---|---|---|
| A1 | `./gradlew build` | BUILD SUCCESSFUL, 103 tests, 0 failures; 4 modules have no tests |
| A2 | `GET :3000/api/auth/accounts` (no auth) | 200, full account list with roles |
| A3 | `POST :3000/api/auth/login {"employeeId":1}` (no password) | 200, SUPERADMIN `*:*`, cookie 7 d, no `Secure` |
| A4 | `GET :8081/api/me` with service key + `X-Employee-ID: 1`, no JWT | 200, SUPERADMIN profile |
| A5 | `GET :8080/api/registry/internal/routing/payroll/qii_creative` with service key | 200 with JDBC username and password |
| A6 | `redis-cli GET tenant-routing:people:qii_creative`; `CONFIG GET requirepass` | Password present; Redis has no password |
| A7 | `pg_roles` on postgres-hrms | `platform` is `rolsuper=t`; owns people/leave/timework/archive DBs |
| A8 | Leave credential reads `people_qii_creative.employee` | 21 rows readable |
| A9 | `POST /api/registry/provision {"tenantId":"audit_probe"}` | **Fails**: Liquibase "Unterminated dollar quote" in `people-003`; orphan DB left (dropped by auditor) |
| A10 | Employee 8: `GET /files?all=true`, then download-url and content for file 16 (employee 11's) | 200, 16 files listed, 23,733 bytes downloaded |
| A11 | People payroll facts for employee 8, 2026-10 | No `daysEmployed`/`workingDays`/`absentDays`/`leaveBalances`; leave `consumedByType:{}` |
| A12 | `POST /payroll/calculate {"employeeId":12,"periodLabel":"2026-08"}` as SUPERADMIN | 409 "Employee not employed in period" (no row written) |
| A13 | `POST :8086/api/internal/approvals/reassign` with only the service key | 200 `{"updated":0}` (non-existent ids used) |
| A14 | CORS preflight `Origin: https://evil.example` | ACAO reflects the evil origin |
| A15 | `GET :8090/docs/registry/v3/api-docs` (no auth) | 200 |
| A16 | `GET :8090/api/registry/tenants` with `X-Platform-Admin-Key: wrong` | Forwarded; registry answered HTTP 500 for a 401 condition |
| A17 | BFF unknown path / bad path variable | HTTP 500 with `cause` leaking exception text |
| A18 | `rabbitmqctl list_queues/list_users/list_policies` | All classic queues, 1 consumer each, single administrator user, no policies |
| A19 | `git ls-files .tmp-smoke` | `cookie.txt` with a session JWT is tracked |

## Appendix B — Guide clause coverage map

| Guide section | Issues |
|---|---|
| §1 Implementation decisions | DB-10, SEC-01, DB-01, MSG-04, OPS-03, OPS-09 |
| §2 Deliverables | OPS-02, OPS-06, OPS-09, API-04, SEC-17 |
| §3 Service architecture & data ownership | DB-01, DB-03, DB-07, API-02, RPT-02, LV-03, MSG-08 |
| §4 Identity & authorization | SEC-01 … SEC-18, DOC-02 |
| §5 Data & transaction rules | DB-01, DB-02, DB-04, DB-05, DB-06, DB-08, DB-09, PAY-03, PAY-04, LV-01, LV-03, LV-04 |
| §6 Payroll finalization & recovery | PAY-01 … PAY-09, PAY-12, LV-02 |
| §7 Contracts & resilience | API-01 … API-06, MSG-01, MSG-02, MSG-05, MSG-06, PAY-13, RPT-01 |
| §8 Document security | DOC-01 … DOC-07 |
| §9 Production deployment | OPS-01, OPS-02, OPS-05, SEC-12, SEC-13, SEC-17, MSG-04 |
| §10 Recovery & operations | OPS-06, PAY-06, AUD-01, MSG-01 |
| §11 Existing implementation changes | SEC-01 (row 1), PAY-08/PAY-09 (row 2), LV-01/PAY-04 (row 3), PAY-10 (row 4), DB-10 (row 5), RPT-01/TW-01/TW-03 (row 6), AUD-01/DOC-01 (row 7), OPS-04/OPS-05 (row 8) |
| §12 Stages / §13 Acceptance / §14 DoD | §1.1 table, §13 fix order, OPS-04 |
| §15 Decisions | OPS-09, DB-07, PAY-10, SEC-15, DB-10 |
