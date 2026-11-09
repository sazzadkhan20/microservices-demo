# Qii HRMS — microservices backend (Gradle / Spring Boot)

Backend for **Qii Creative's** HRMS: one company, independently deployable services, one fixed
database per service (HRMS Microservices Developer Guide §1, §3, §5).

```
Browser ──► Next.js BFF (:3000, httpOnly session cookie)
                │
                ▼
        API Gateway (:8090)  verifies session JWT, strips client identity headers,
                │            injects X-Employee-ID + X-Service-Key, mints demo sessions
   ┌──────┬─────┼───────┬─────────┬──────────┬───────────┐
   ▼      ▼     ▼       ▼         ▼          ▼           ▼
 People Leave Time&Work Payroll  Documents Notifications Reporting
 :8081  :8086  :8087    :8082    :8084      :8083         :8085
   │      │      │        │         │          │            │
people_db leave_db timework_db payroll_db files_db notification_db reports_db
            (each service owns its database, migrations and runtime role)

Service-to-service: RabbitMQ (transactional outbox → broker → idempotent inbox) for workflows
(payroll finalize ↔ leave cover, document scan, notifications, report exports, employee changes);
read-only HTTP/gRPC lookups with timeouts for directory / RBAC.
Redis: gateway session revocation only. MinIO: private document storage.
```

## Modules

| Module | Purpose |
|---|---|
| `platform-common` | Shared security (JWT, service key, permission client), API envelope, config helpers |
| `platform-messaging` | Transactional outbox / inbox + RabbitMQ topology (all services) |
| `hrms-api` | gRPC contract (Payroll → People) |
| `api-gateway` | Sole entry behind the BFF; JWT trust boundary; demo token mint (`POST /api/auth/token`) |
| `people-service` | Employees, org, reporting lines, RBAC, company settings |
| `leave-service` | Leave types/policies, approval chains, balances, immutable ledger, payroll leave cover |
| `time-work-service` | Schedules, holidays, attendance, corrections, tasks, work logs |
| `payroll-service` | Salary history, payslips, async finalize/reopen workflow |
| `file-service` | Document metadata, quarantine/scan, ACL, short-lived downloads |
| `notification-service` | In-app notifications, dedupe |
| `report-service` | Attendance exports |
| `frontend/qii-hrms` | Next.js app + BFF |

## Run locally

Prerequisites: Java 21, Docker, Node 20+.

```bash
cp .env.example .env                 # set every *_PASSWORD / key
docker compose up -d                 # Postgres (3 hosts), RabbitMQ, Redis, MinIO
./gradlew bootJar
# start each service (any order) — each one migrates its own database on startup:
java -jar people-service/build/libs/people-service-1.0.0-SNAPSHOT.jar
# … leave, time-work, payroll, file, notification, report, api-gateway
bash scripts/seed-qii-dev.sh load    # first time only: Qii reference data
node scripts/sync-frontend-env.mjs && (cd frontend/qii-hrms && npm run dev)
```

Or everything in containers: `bash scripts/up-local.sh` (`docker compose --profile apps up -d --build`).

Converting an environment created with the old multi-tenant registry: `bash scripts/migrate-local-to-fixed-db.sh` (one time).

Demo login (local only, `DEMO_AUTH_ENABLED=true`): the login page's account picker → BFF → gateway
`POST /api/auth/token`. Production must use OIDC (guide §4); the prod profile refuses demo auth.

## Databases

See **[docs/DEPLOYMENT_FIXED_DB.md](docs/DEPLOYMENT_FIXED_DB.md)** — service → database map, the
`<svc>_migrator` / `<svc>_app` role model, migration ownership and how to deploy fixed connections to
staging/production.

| Host (local port) | Databases |
|---|---|
| postgres-hrms (5433) | `people_db`, `leave_db`, `timework_db` |
| postgres-payroll (5434) | `payroll_db` |
| postgres-platform (5436) | `files_db`, `notification_db`, `reports_db` |

## API

- Swagger UI (local): http://127.0.0.1:8090/swagger-ui.html — pick a service from the dropdown.
- Postman: `docs/postman/Qii_HRMS.postman_collection.json`.

| Gateway prefix | Service |
|---|---|
| `/hrms/api/leave-*`, `/hrms/api/approvals/**`, `/hrms/api/leave/**` | leave-service |
| `/hrms/api/attendance/**`, `work-schedules`, `holidays`, `tasks`, `work-logs` | time-work-service |
| `/hrms/**` (everything else), `/people/**` | people-service |
| `/payroll/**` | payroll-service |
| `/files/**` | file-service |
| `/notifications/**` | notification-service |
| `/reports/**` | report-service |
| `/api/auth/token`, `/api/session/**` | gateway (local endpoints) |

`/**/internal/**` paths are never reachable through the gateway.

## Tests

```bash
./gradlew test
```

Production-readiness findings and their remediation order: [docs/BACKEND_PRODUCTION_READINESS_AUDIT.md](docs/BACKEND_PRODUCTION_READINESS_AUDIT.md).
