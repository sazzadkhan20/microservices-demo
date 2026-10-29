# Reusable Multi-Tenant HRMS + Payroll Platform (Gradle / Spring Boot)

Practice platform for **one reusable codebase** serving **many businesses** later.

**This prototype version intentionally has NO custom business profiles yet**  
(no Retail Corp / Service Co rules). You get:

- API Gateway (JWT trust boundary)
- Tenant Registry (control plane: create DBs + migrate + Redis routing)
- HRMS service (modular monolith, multi-tenant, REST + gRPC)
- Payroll service (modular monolith, multi-tenant, calls HRMS via gRPC)
- Separate PostgreSQL hosts so you can **see real DB structure**
- Swagger + Postman for testing

Custom businesses are added **one by one later** via provision + config (no service fork).

---

## Architecture (what actually happens)

```
Client / Postman / Swagger
        │
        ▼
┌──────────────────┐
│  API Gateway     │  :8090
│  JWT verify      │  extract tenant_id claim
│  inject X-Tenant-ID │  strip client spoof header
└────────┬─────────┘
         │
    ┌────┴─────┬────────────────┐
    ▼          ▼                ▼
Registry    HRMS             Payroll
:8080       :8081/:9081      :8082
control     shared code      shared code
plane       tenant DB route  tenant DB route
    │            │                │
    │            └──────gRPC──────┘
    ▼
Redis (routing L2 + pub/sub invalidate)
Postgres-registry  → registry_db
Postgres-hrms      → hrms_<tenantId>   (created on provision)
Postgres-payroll   → payroll_<tenantId> (created on provision)
```

**Core rule:** Gateway authenticates. Services route by tenant. Config/engines differentiate later. No forks. No cross-service DB access.

---

## Prerequisites

- **JDK 21**
- **Docker Desktop** (for Postgres + Redis)
- **Gradle Wrapper** included (`gradlew` / `gradlew.bat`) — no global Gradle install required
- Optional: Postman, DBeaver / pgAdmin

---

## Project modules (Gradle)

| Module | Type | Port | Role |
|---|---|---|---|
| `platform-common` | library | — | TenantContext, Redis keys, JWT helper |
| `hrms-api` | library | — | Protobuf / gRPC contract |
| `tenant-registry` | app | 8080 | Provision DBs, routing, tokens |
| `hrms-service` | app | 8081 / gRPC 9081 | Employees, leave policy |
| `payroll-service` | app | 8082 | Payroll calculate |
| `api-gateway` | app | 8090 | Public entry |

Each app is a **modular monolith** inside one deployable:

`api` → `application` → `domain` → `extension` → `infrastructure`

---

## How to run (local — recommended for learning)

### 1) Start infrastructure only

```bash
docker compose up -d redis postgres-registry postgres-hrms postgres-payroll
```

Wait until healthy. You now have:

| Container | Host port | What’s inside initially |
|---|---|---|
| postgres-registry | `localhost:5435` | `registry_db` (control plane) |
| postgres-hrms | `localhost:5433` | `hrms_host` placeholder only |
| postgres-payroll | `localhost:5434` | `payroll_host` placeholder only |
| redis | `localhost:6379` | empty until provision |

DB login (all hosts): user `platform` / password `platform`

### 2) Build everything

```bash
./gradlew build
# Windows:
gradlew.bat build
```

### 3) Start services (4 terminals)

```bash
./gradlew :tenant-registry:bootRun
./gradlew :hrms-service:bootRun
./gradlew :payroll-service:bootRun
./gradlew :api-gateway:bootRun
```

### 4) Optional: run apps in Docker too

```bash
docker compose --profile apps up -d --build
```

---

## How to see database structure

Use DBeaver / pgAdmin / `psql`.

### A) Control plane (always present after Registry starts)

Connect: `localhost:5435` / db `registry_db` / user `platform`

Table to inspect:

- `tenant_routing` — where each tenant’s HRMS/Payroll JDBC URL lives
- `databasechangelog` — Liquibase history for registry itself

At first start (before any provision), `tenant_routing` is empty. That is correct.

### B) After you provision a tenant (example `acme_demo`)

Registry will **auto-create separate databases**:

- on HRMS host (`localhost:5433`): `hrms_acme_demo`
- on Payroll host (`localhost:5434`): `payroll_acme_demo`

Connect to those DBs and inspect:

**HRMS tenant DB**

- `employee` (+ GIN on `custom_fields` JSONB)
- `leave_policy` (platform defaults)
- `tenant_field_definition` (empty until you add business field config later)
- `ext_indexed_attribute` (for future high-frequency filters)
- `databasechangelog`

**Payroll tenant DB**

- `payroll_config` (platform defaults: OT 1.5×, weekend 1.0×, no deductions)
- `payslip`
- `databasechangelog`

This is the main learning point: **one service code**, **many isolated DBs**.

---

## End-to-end workflow (prototype)

1. **Provision tenant** → creates DBs + Liquibase + Redis routing + pub/sub invalidate  
2. **Issue JWT** with claim `tenant_id`  
3. **Call Gateway** with `Authorization: Bearer <token>`  
4. Gateway injects trusted `X-Tenant-ID` (client spoof header is stripped)  
5. HRMS/Payroll route JDBC to that tenant DB  
6. Payroll calls HRMS over **gRPC** with the same tenant metadata  

Custom business rules/fields are **not** in this version. Next iteration: register field defs + payroll rules per tenant without redeploying engines when rule types already exist.

---

## Testing via API Gateway (recommended — no bypass)

**Use only Gateway Swagger / Postman.** Gateway verifies JWT and injects `X-Tenant-ID`.

| Entry | URL |
|---|---|
| **Gateway Swagger (use this)** | http://127.0.0.1:8090/swagger-ui.html |
| Postman collection | `postman/platform.postman_collection.json` |

Direct `:8081` / `:8082` Swagger is debug-only (bypasses trust boundary).

### Swagger step-by-step (Gateway)

1. Start infra + all 4 apps (Registry, HRMS, Payroll, Gateway).
2. Open http://127.0.0.1:8090/swagger-ui.html
3. Top dropdown → **Registry**
4. `POST /api/registry/token` → body `{ "tenantId": "techshop_retail" }` → Execute  
   Copy `accessToken` from response.
5. Click **Authorize** → paste token into **GatewayJWT** (Bearer) → Authorize → Close  
   (Do **not** set `X-Tenant-ID` yourself.)
6. Dropdown → **HRMS** → `GET /api/employees` → Execute → see practice employee.
7. `GET /api/leave-policy` → annualDays 15, workWeekDays 6.
8. Dropdown → **Payroll** → `GET /api/payroll/config` → OT 2.0, weekend 2.5, union dues.
9. `POST /api/payroll/calculate` with employeeId from step 6.

### Postman step-by-step

1. Import `postman/platform.postman_collection.json`
2. Run **Issue JWT for tenant** (saves `accessToken`)
3. Run **List employees** / **Get leave policy**
4. Run **Get payroll config** / **Calculate payroll**
5. Run **No JWT must be 401** to confirm gateway security

### Gateway path map

| Client calls Gateway | Forwards to |
|---|---|
| `POST /api/registry/token` | Registry `:8080` |
| `GET /hrms/api/employees` | HRMS `:8081` + injected `X-Tenant-ID` |
| `POST /payroll/api/payroll/calculate` | Payroll `:8082` + injected `X-Tenant-ID` |

---

## Config-driven leave (TechShop Sat–Thu)

Leave rules live in the **tenant DB as configuration**, not in Java `if (tenant == techshop)`.  
New business = new config rows → **no HRMS code change / redeploy** for rule differences.

### What TechShop config means

| Config | TechShop value | Meaning |
|---|---|---|
| `leave_policy.working_days` | SAT,SUN,MON,TUE,WED,THU | Friday is **off**; only these days count as leave days |
| `union_notice_days` | 7 | Union staff must apply ≥ 7 days ahead |
| `non_union_notice_days` | 3 | Non-union ≥ 3 days ahead |
| `store_shift_coverage` | S-DHK-01 + MORNING min 2 | Shop must keep enough morning staff |
| `approval_chain` | STORE_MANAGER → REGIONAL_HR | Two real-user approval steps |
| `leave_blackout` | sale week | Leave blocked in that window |
| `department` / `designation` | STORE_OPS, SALES, HR + titles | Mandatory org fields on every employee |
| `employee.manager_id` | reports-to | Store Manager step must be that real user |

### Seeded TechShop people (after upgrade-hrms reseed)

| Code | Name | Dept | Designation | Approves as |
|---|---|---|---|---|
| R-2001 | Farhan Ahmed | HR | Regional HR | `REGIONAL_HR` |
| R-2002 | Amina Begum | Store Ops | Store Manager | `STORE_MANAGER` (manager of floor staff) |
| R-1001 | Nadia Rahman | Sales | Sales Associate | — (union applicant) |
| R-1002 | Rahim Uddin | Sales | Sales Associate | — (morning relief) |
| R-1003 | Karim Ali | Store Ops | Cashier | — |
| R-1004 | Sara Khan | Sales | Sales Associate | evening |
| R-1005 | Imran Hossain | Store Ops | Cashier | evening |

### Code map (how dynamic rules work)

| Class | Role |
|---|---|
| `WorkingDaysCalculator` | Counts days using `working_days` from DB |
| `LeaveApplicationService` | Apply → notice/blackout/coverage/balance → **real approver** checks |
| `LeaveCalcEngine` | Reads policy for `GET /api/leave-policy` |
| `TenantConfigLoader` | Loads all leave-related config from tenant DB |

### DB tables to open (DataGrip port 5433 / `hrms_techshop_retail`)

- `department`, `designation` — org master data (`approver_role` links designation → leave step)  
- `employee` — `department_id`, `designation_id`, `manager_id` mandatory for leave  
- `leave_policy` — working days + notice + approval chain  
- `store_shift_coverage` — min staff per store/shift  
- `leave_blackout` — blocked dates  
- `leave_application` / `leave_approval` — requests + `approver_employee_id`  

### Test leave (after Registry upgrade + HRMS restart)

**Swagger:** http://127.0.0.1:8090/swagger-ui.html → Authorize JWT → HRMS  

1. `GET /api/org/departments` + `GET /api/org/designations`  
2. `GET /api/employees` → note ids for Nadia, Rahim, Amina, Farhan  
3. `POST /api/leave-applications` (dates ≥ 7 days ahead; Nadia is union):
```json
{
  "employeeId": <Nadia id>,
  "startDate": "2026-11-07",
  "endDate": "2026-11-10",
  "reason": "Family visit",
  "reliefEmployeeId": <Rahim id>
}
```
4. `POST .../approve` with `{"approverEmployeeId": <Amina id>}` — must be Nadia's manager  
5. `POST .../approve` with `{"approverEmployeeId": <Farhan id>}` → status `APPROVED`  
6. Wrong user (e.g. Rahim approving) → rejected by role/manager check  

**Postman:** re-import `postman/platform.postman_collection.json` → folder **3. Leave apply / approve flow**  

Set `leaveStart` / `leaveEnd` ≥ 7 days ahead (Nadia is union). List employees auto-fills manager/HR ids.  

### Upgrade / reseed business packs

```http
POST /api/registry/tenants/techshop_retail/upgrade-hrms?reseed=true&businessPack=techshop_retail
POST /api/registry/tenants/impit_limited/upgrade-hrms?reseed=true&businessPack=impit_limited
```

### Second practice tenant: Impit Limited (BPO)

Same HRMS JAR — different DB config.

```http
POST /api/registry/provision
{ "tenantId": "impit_limited", "displayName": "Impit Limited", "businessPack": "impit_limited" }
```

| Config | TechShop | Impit |
|---|---|---|
| DB | `hrms_techshop_retail` | `hrms_impit_limited` |
| Working days | Sat–Thu | Mon–Fri |
| Leave types | `ANNUAL` | `SICK`, `CASUAL`, `ANNUAL`, `UNPAID`, `OTHER` |
| Notice | ATTRIBUTE (union 7 / non-union 3) | NONE |
| Coverage | LOCATION_SHIFT + relief | LOCATION (one office), no relief; SICK allowed when coverage fails |
| Approvals | STORE_MANAGER → REGIONAL_HR | MANAGER (or ADMIN/SUPER_ADMIN) → HR |
| Employee code | free text | 4 digits (`tenant_settings.employeeCodePattern`) |
| Extra fields | store/shift/union | `virtual_name` (unique active), hometown, religion, gender, joining_* |

**Compare APIs (JWT per tenant):**
- `GET /api/leave-policy` — modes + types  
- `GET /api/leave-types`  
- `GET /api/leave-balances/employee/{id}`  
- `POST /api/leave-balances/adjust` — HR add/deduct  
- `POST /api/leave-applications` with `leaveTypeCode`  

DataGrip: port **5433** → open both `hrms_techshop_retail` and `hrms_impit_limited` side by side.

---

## Postman

Import: [`postman/platform.postman_collection.json`](postman/platform.postman_collection.json)

Suggested order in the collection:

1. Provision tenant  
2. Issue JWT (saves `accessToken`)  
3. Create employee  
4. List employees / leave policy  
5. Calculate payroll  
6. “No JWT” request → expect **401**

Collection variable `tenantId` defaults to `acme_demo`.

---

## Quick curl smoke test

```bash
# 1) Provision
curl -s -X POST http://localhost:8090/registry/provision \
  -H "Content-Type: application/json" \
  -d "{\"tenantId\":\"acme_demo\",\"displayName\":\"ACME Demo\"}"

# 2) Token
TOKEN=$(curl -s -X POST http://localhost:8090/registry/token \
  -H "Content-Type: application/json" \
  -d "{\"tenantId\":\"acme_demo\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

# 3) Create employee
curl -s -X POST http://localhost:8090/hrms/api/employees \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"employeeCode\":\"E001\",\"fullName\":\"Ada Lovelace\",\"email\":\"ada@example.com\",\"baseSalary\":5000,\"hourlyRate\":25,\"customFields\":{\"note\":\"later\"}}"

# 4) Calculate payroll (use employee id from previous response)
curl -s -X POST http://localhost:8090/payroll/api/payroll/calculate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"employeeId\":1,\"periodLabel\":\"2026-10\",\"regularHours\":160,\"overtimeHours\":10,\"weekendHours\":0}"
```

---

## Local vs Deploy configuration

Same code; only environment variables change.

| Concern | Local default | Docker profile `apps` |
|---|---|---|
| Registry DB | `localhost:5435/registry_db` | `postgres-registry:5432` |
| HRMS PG host | `localhost:5433` | `postgres-hrms:5432` |
| Payroll PG host | `localhost:5434` | `postgres-payroll:5432` |
| Redis | `localhost:6379` | `redis:6379` |
| HRMS gRPC from Payroll | `localhost:9081` | `hrms-service:9081` |
| JWT secret | shared practice secret | same env `JWT_SECRET` |

---

## What logic is built now (and what is deferred)

### Built (prototype)

- Gateway JWT trust boundary + header injection/stripping  
- Registry provision: `CREATE DATABASE` + Liquibase + Redis + pub/sub  
- Multi-tenant routing: Caffeine L1 → Redis L2 → Registry L3  
- HRMS employee CRUD + leave policy defaults  
- Config-driven engines (ready; definitions empty)  
- Payroll calculate via HRMS gRPC + default multipliers  
- Swagger + Postman  

### Deferred (next practice step — custom businesses)

- Seed Retail/Service field definitions and deduction rules  
- Config update APIs without redeploy  
- Approval chains / richer leave rules  
- Dedicated per-vertical extension tables beyond the generic indexed attribute table  

---

## Risks this practice teaches

| Risk | How we handle it |
|---|---|
| Client spoofs tenant | Gateway strips `X-Tenant-ID`, uses JWT claim only |
| ThreadLocal leak | `TenantContext.clear()` in `finally` (HTTP + gRPC) |
| Stale routing cache | Redis pub/sub invalidate + L1 eviction |
| Missing tenant routing | Clear error; provision first |
| Cross-service DB temptation | Payroll uses gRPC only |
| Connection pool growth | Per-tenant pool bounds + cache max size |

---

## Useful Gradle commands

```bash
./gradlew projects
./gradlew build
./gradlew :hrms-api:generateProto
./gradlew :tenant-registry:bootRun
./gradlew :hrms-service:bootRun
./gradlew :payroll-service:bootRun
./gradlew :api-gateway:bootRun
```

---

## Learning tip

After provision + one employee + one payslip:

1. Open `registry_db.tenant_routing`  
2. Open `hrms_acme_demo.employee`  
3. Open `payroll_acme_demo.payslip`  
4. Compare: same API paths, isolated data  

Then ask questions about any class/flow/DB table — comments in code explain the “why”, not only the “what”.
