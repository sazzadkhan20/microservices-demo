# Service split — People / Leave / Time & Work (+ platform services)

Per **HRMS Microservices Developer Guide §3**. Each service owns its database, migrations, runtime role, **local outbox/inbox**, and **local audit trail**. Services do **not** share JDBC. Communicate via HTTP contracts and RabbitMQ.

## Ports & databases (single company, fixed connections)

| Service | Port | Database |
|---------|------|----------|
| `people-service` | **8081** HTTP/actuator · **9081** gRPC only (do not HTTP-probe) | `people_db` |
| `leave-service` | **8086** | `leave_db` |
| `time-work-service` | **8087** | `timework_db` |
| `payroll-service` | **8082** | `payroll_db` |
| `file-service` | **8084** | `files_db` |
| `notification-service` | **8083** | `notification_db` |
| `report-service` | **8085** | `reports_db` |

Gateway **8090** (no database). Connection, roles and deployment: [DEPLOYMENT_FIXED_DB.md](DEPLOYMENT_FIXED_DB.md).

## Domain ownership

| Service | Owns |
|---------|------|
| **People** | Employees, org, RBAC, employment/reporting history, field defs, `tenant_settings` (company settings), `people_audit_log` |
| **Leave** | Leave domain, `leave_settings`, `leave_audit_log` |
| **Time & Work** | Schedules, holidays, attendance, tasks, work logs, `timework_settings`, `timework_audit_log` |
| **Payroll** | Salary history, payslips, finalize ops, `payroll_audit_log` |
| **Documents (files)** | File objects, scan state, **access audit** → `files_audit_log` |
| **Notifications** | In-app messages, delivery, `notification_audit_log` |
| **Reporting** | Export jobs / read models, `report_audit_log` |

## Audit (production pattern)

Admin UI `GET /api/audit` (People) **federates** sibling `/api/internal/audit` feeds over HTTP and returns one stream sorted **newest first**. No shared audit JDBC.

Each service writes **only its own** audit rows into a **service-named** table:

| DB | Audit table |
|----|-------------|
| people_db | `people_audit_log` |
| leave_db | `leave_audit_log` |
| timework_db | `timework_audit_log` |
| payroll_db | `payroll_audit_log` |
| files_db | `files_audit_log` |
| notification_db | `notification_audit_log` |
| reports_db | `report_audit_log` |

No shared audit JDBC.

## Messaging (guide outbox/inbox)

Every messaging service has **local**:

- `message_outbox` — write in same DB transaction as business change; relay publishes to RabbitMQ after commit
- `message_inbox` — idempotent consume

Same table **names**, different **databases/rows**. Required for People, Leave, Time&Work, Payroll, Files, Notification, Reports.

Broker controls (guide §1 / §7):

- Durable RabbitMQ topology from `docker/rabbitmq/definitions.json` (queues + DLQs + bindings)
- `publisher-confirm-type: correlated` on every publishing service
- Outbox relay waits for broker confirm **before** marking `PUBLISHED`, without holding a DB write transaction open during the wait
- Leave-cover duplicate commands re-emit the recorded `leave_operation` result (guide §6)

Integrity:

- `uq_payslip_employee_period` on `(employee_id, period_label)` (DATA01)
- `leave_operation.operation_key` unique
- Actuator liveness/readiness probes enabled on gateway + domain services

## Communication

| Need | Mechanism |
|------|-----------|
| Leave / Time&Work need employee/RBAC | HTTP People internal APIs |
| Leave needs schedule/holiday | HTTP Time&Work |
| Time&Work needs leave impact | HTTP Leave |
| Payroll leave cover / finalize | RabbitMQ command/result + local outbox/inbox |
| File scan / report export / notifications | RabbitMQ + local outbox/inbox |
| People employee changed | RabbitMQ cache-invalidate only |

## Runtime rule

> One service = one DB role. Share IDs and contracts (HTTP / events), never databases.

## Java package ownership

| Service | Root package | Notes |
|---------|--------------|-------|
| People | `com.platform.people.**` | Local Employee/RBAC entities. gRPC stubs remain `com.platform.hrms.api.grpc` (generated from `hrms-api`). |
| Leave | `com.platform.leave.**` | Leave domain only. People access via `client.people.*` (`EmployeeView`, `PeopleEmployeeGateway`, `PeopleAuthzClient`). Schedules via `client.timework.*`. |
| Time & Work | `com.platform.timework.**` | Schedule/attendance/tasks only. People access via `client.people.*`. |

No service keeps a `com.platform.hrms` application package.
