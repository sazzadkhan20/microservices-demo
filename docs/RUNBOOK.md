# Operations runbook (guide §10)

Owners are placeholders until the decision record (guide §15) names them: **Tech lead** (platform),
**HR/Finance owner** (payroll), **Company IT** (identity). Every manual financial correction needs a reason,
the actor and before/after references (payroll resolve already records these).

## Signals to watch (Prometheus, `Authorization: Bearer $PLATFORM_METRICS_KEY`)

| Signal | Metric / endpoint | Act when |
|---|---|---|
| Latency / errors | `http_server_requests_seconds_*` (histograms, SLO buckets 100/300/800 ms/2 s) | p95 > 800 ms or 5xx > 1 % for 5 min |
| Saturation | `tomcat_threads_busy_threads`, `hikaricp_connections_pending`, `hikaricp_connections_active` | pending > 0 for 2 min |
| Load shedding | gateway `429`, service `503 SERVICE_BUSY` / `DEPENDENCY_UNAVAILABLE` (logged with correlationId) | sustained |
| Outbox backlog / DLQ | RabbitMQ `q.*.dlq` depth, `message_outbox` rows not `SENT` | DLQ > 0, backlog growing |
| Payroll | `payroll_reconciliation_mismatches`, `payroll_operations_result_mismatch_total`, operations older than 15 min | > 0 |
| Health | `/actuator/health/liveness`, `/actuator/health/readiness` (DB, broker, Redis) | readiness DOWN |

## Release, rollback, forward recovery

* Release / promote: `docs/CICD.md`. `deploy/deploy.sh` waits for every health check and rolls back to the
  last healthy manifest automatically; `release-history.log` on the server lists what ran when.
* Manual rollback: *Actions → Release → Run workflow* with the previous `prod-*` tag (same digests, no rebuild).
* Schema: migrations are expand-and-contract and run at service start with the `<svc>_migrator` identity.
  An old image must run on the new schema; if a migration itself fails the service stays unready, the
  previous containers keep serving — fix forward with a new changeset (never edit an applied one).

## Incidents

**Identity outage (Google OIDC down).** Existing sessions keep working (gateway validates locally; status
cache tolerates People hiccups for 10 min). New logins fail. Emergency access: a Tech-lead-approved
break-glass flow only — demo login can never start in production (`ProdSecretsGuard`).

**Broker outage (RabbitMQ).** Writes still commit; events wait in each service's `message_outbox` and the
relay retries with backoff. Payroll finalise/reopen show *pending*; the reconciler re-sends after recovery.
After recovery: check outbox drains to 0 and DLQs are empty; redrive DLQ messages after fixing the cause.

**Database outage / overload.** Services answer `503 SERVICE_BUSY` with `Retry-After` instead of hanging
(Hikari 5 s acquire, statement 60 s, lock 15 s, idle-in-transaction 120 s). Readiness goes DOWN so the
platform stops routing. Restore from PITR backup into an isolated environment first (§10 restore drill),
then reconcile payroll against leave ledgers and operation IDs (`GET /payroll/api/payroll/runs/{p}/reconciliation`).

**People service slow / down.** Other services serve cached directory and permission data for up to
5 minutes, then fail fast (circuit breaker, `503 DEPENDENCY_UNAVAILABLE`). Gateway uses the last known
employee status for up to 10 minutes; with nothing cached it returns 503 (users are not logged out).

**Stuck payroll operation.** `GET /payroll/api/payroll/operations` shows age and `resolvableAt`. The
reconciler re-sends automatically; after the minimum age a privileged user may *resolve* with a reason —
the fence makes a late Leave command a no-op, so no double deduction is possible.

**Malicious upload.** Files stay `QUARANTINED` until the scan releases them; infected/invalid files are
`REJECTED` and never downloadable. Retention job purges rejected objects after `FILE_RETENTION_REJECTED_DAYS`.
Review `files_audit_log` for the uploader and revoke their sessions if needed.

**Credential exposure.** Rotate the secret in the secrets manager, redeploy (same digests). JWT secret
rotation invalidates every session (users sign in again); `PLATFORM_SERVICE_KEY` / `PLATFORM_INTERNAL_KEY`
must change on all services in the same deploy. Revoke one person: `POST /api/session/revoke-employee/{id}`
with `X-Platform-Admin-Key` (sessions issued before that instant are refused; a later login works).

**Traffic spike / abuse.** Per-employee token bucket at the gateway (`GATEWAY_RATE_LIMIT_*`), then service
load shedding (503). Raise replicas before raising pool sizes; keep (pool × replicas) under Postgres
`max_connections`. `node scripts/load-test.mjs --profile spike` reproduces the pattern in staging.
