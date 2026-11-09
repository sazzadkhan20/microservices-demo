# Single-company deployment — fixed database connections

Guide §1: *"One company. Use fixed service database connections. Keep future tenant provisioning outside the
request path."* The Qii HRMS backend runs for **one company (Qii Creative)**. There is no tenant registry,
no tenant header, no per-request database routing and no tenant id in tokens, events or tables.

## 1. Service → database map

| Service | Database | Runtime role (`<SVC>_DB_USER`) | Migration role (`<SVC>_DB_MIGRATION_USER`) | Env prefix |
|---|---|---|---|---|
| people-service | `people_db` | `people_app` | `people_migrator` | `PEOPLE_` |
| leave-service | `leave_db` | `leave_app` | `leave_migrator` | `LEAVE_` |
| time-work-service | `timework_db` | `timework_app` | `timework_migrator` | `TIMEWORK_` |
| payroll-service | `payroll_db` | `payroll_app` | `payroll_migrator` | `PAYROLL_` |
| file-service | `files_db` | `files_app` | `files_migrator` | `FILES_` |
| notification-service | `notification_db` | `notification_app` | `notification_migrator` | `NOTIFICATION_` |
| report-service | `reports_db` | `report_app` | `report_migrator` | `REPORT_` |

The api-gateway has no database; it uses Redis only for session revocation.

Every service reads exactly these variables (Spring `spring.datasource.*` / `spring.liquibase.*`):

```
<SVC>_DB_URL                    jdbc:postgresql://<host>:5432/<database>?sslmode=require   (prod)
<SVC>_DB_USER / _DB_PASSWORD    runtime role — SELECT/INSERT/UPDATE/DELETE only
<SVC>_DB_MIGRATION_USER / _DB_MIGRATION_PASSWORD   schema owner — used only by Liquibase at startup
<SVC>_DB_POOL_SIZE              Hikari max pool size (default 10)
```

## 2. Database roles (guide §5 "restricted runtime roles")

`docker/postgres/service-db.sql` is the single source of truth for role setup. For every service database it:

- creates `<svc>_migrator` (LOGIN, not superuser, no CREATEDB/CREATEROLE) and makes it the database owner;
- creates `<svc>_app` (LOGIN, not superuser) with `CONNECT` on **only its own** database, `USAGE` on schema
  `public`, DML on all tables and sequences, plus default privileges so tables Liquibase creates later are
  covered automatically;
- revokes `CONNECT` from `PUBLIC`, so no other service's role can open the database.

Verified locally: `leave_app` → `people_db` fails with *"User does not have CONNECT privilege"*, and
`people_app` `CREATE TABLE` fails with *"permission denied for schema public"*.

The Postgres superuser (`postgres`) is used only by bootstrap/migration scripts, never by a service.

## 3. Schema migrations (guide §5 "each service owns its migrations")

Each service owns its Liquibase changelog: `<service>/src/main/resources/db/changelog/db.changelog-master.yaml`.

- Historic changesets keep their original classpath path `db/tenant-changelog/...` (copied from the removed
  registry) so `DATABASECHANGELOG` history on existing databases matches and nothing re-runs.
- New changes go under `db/changelog/changes/`. `001-single-company.yaml` drops the old `tenant_id` columns
  (`message_outbox`, `file_object`, `notifications`), adds replacement indexes, and makes
  `notifications.dedupe_key` unique.
- Liquibase runs on service startup **as the migration role**. Readiness (`/actuator/health/readiness`)
  includes the database, so a service that cannot migrate or connect is never ready.

## 4. Environments

### Local (Docker Compose)

```
cp .env.example .env            # set every *_PASSWORD
docker compose up -d            # Postgres hosts create their databases + roles on first boot
./gradlew :people-service:bootRun   # (or: docker compose --profile apps up -d --build)
bash scripts/seed-qii-dev.sh load   # optional: Qii reference data into the empty databases
```

An existing environment from the old multi-tenant layout is converted once with
`bash scripts/migrate-local-to-fixed-db.sh` (rename `*_qii_creative` → fixed names, create roles, move documents
to `FILE_STORAGE_BUCKET`, drop legacy tenant databases and the registry).

### Staging / production

1. Provision a managed PostgreSQL cluster (guide §1 allows one shared cluster initially) with TLS, encrypted
   backups and point-in-time recovery (guide §10).
2. Create the seven databases and roles by running `docker/postgres/service-db.sql` once per database as the
   cluster admin (`psql -v db=people_db -v migrator=people_migrator -v migrator_pw=… -v app=people_app
   -v app_pw=… -f service-db.sql`). This is an operator/infra step, never part of a request.
3. Store each service's two credentials in the secrets manager; inject them as the env vars above. The app
   credential and the migration credential are separate secrets and can be rotated independently.
4. Deploy each service. On start it migrates its own schema with the migrator credential, then serves with the
   app credential. Run with `SPRING_PROFILES_ACTIVE=prod`: the startup guard rejects demo auth and default
   secrets.
5. Keep databases, broker, Redis, object storage and service ports on a private network; only the HTTPS
   ingress → BFF is public (guide §9).

### Fresh data

- Development/staging: `scripts/seed-qii-dev.sh load` loads the committed Qii reference snapshot
  (`scripts/seed/qii/*.sql`, data only, no outbox/inbox/Liquibase rows). Refresh it with `… export`.
- Production: start from schema only and import real HR data. Business data is never silently invented
  (guide §2).

## 5. What changed from the multi-tenant design

| Before | Now |
|---|---|
| `tenant-registry` service + `registry_db` (provisioning, routing, token mint) | Removed. Token mint lives in the gateway (`POST /api/auth/token`, demo only). |
| Per-request `X-Tenant-ID` → Redis/registry → per-tenant Hikari pool | One Spring Boot DataSource per service from `<SVC>_DB_*` env. |
| DB credentials in registry DB and Redis | Credentials only in each service's own environment / secrets manager. |
| Shared `platform` **superuser** for every service | Per-service `<svc>_app` (DML) and `<svc>_migrator` (owner) roles. |
| `tenant_id` in JWT claims, headers, events, outbox, files, notifications | Removed (JWT carries `employee_id` + `jti`; events have no tenant). |
| Per-tenant MinIO bucket `t-<tenant>` | One private bucket `FILE_STORAGE_BUCKET` (default `qii-hrms-documents`). |
| Registry-run Liquibase for all services | Each service migrates its own database. |
| Outbox drained per tenant via Redis SCAN | `OutboxRelay` drains on its own schedule; no Redis dependency in services. |

Company configuration tables (`tenant_settings` in people_db, `leave_settings`, `timework_settings`) are kept
as-is: they hold the company's settings, not tenancy.
