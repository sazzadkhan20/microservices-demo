# Qii HRMS — Payroll specification and remediation plan

Source of truth for how payroll works in Qii HRMS v1, and the plan used to fix the implementation.

- **Business rules:** *Qii HRMS v1 Software Requirements Specification* (SRS):
  - §8.1 salary records;
  - §8.2 payroll calculation;
  - §8.3 payroll run;
  - BR-15..BR-19 and BR-24;
  - DR-02 and DR-03;
  - NFR-07 and NFR-13;
  - AT-14 and AT-15;
  - Appendix A.
- **Engineering rules:** *HRMS Microservices Developer Guide* (guide):
  - §3 ownership;
  - §5 data and transactions;
  - §6 payroll finalization and recovery;
  - §7 contracts and resilience;
  - §13 PAY01–PAY03 and DATA01.

Anything marked **Decision** is a policy choice the SRS does not settle. Guide §2 says such choices must be recorded, not silently chosen. Business owners (HR and Finance) must approve each decision before go-live. Until then the default shown here applies.

---

## 1. What payroll does (business view)

Once a month the company pays every person employed during that month. Payroll produces **gross pay and attendance-based deductions only**. Tax, provident fund, statutory items, bank payment files and overtime pay are out of scope (SRS §2.4, §16.2).

```
Salary records (Payroll) ─┐
Employment period (People) ├─► Monthly payroll run (draft, live) ─► Finalise (after month end, in order)
Schedule + attendance (TW) │        one row per person                 ├─ numbers locked (snapshot)
Approved leave + balances ─┘        notes explain exceptions          ├─ covered absences taken from leave balances
        (Leave)                     manual adjustment per person       └─ each employee notified "Payslip ready"
                                                                  Reopen ─► leave adjustments removed, back to draft
```

### 1.1 Salary records (FR-SAL-01..03)

| Rule | Detail |
|---|---|
| Who | Users with `salary:edit` (management only by default; HR has no salary access by default, SRS Appendix B) |
| Fields | pay type `MONTHLY` or `HOURLY`, amount (> 0) in the employee's currency (`BDT` or `AUD`), effective date, reason (mandatory) |
| History | Every change is a new row. Rows are never edited or deleted. Each change is written to the audit log with before and after values |
| Effective salary for a day | The latest record whose effective date is on or before that day |
| Visibility | The employee sees their own current pay, finalised payslips and a month-to-date estimate. Nobody else sees salary without permission |
| Ownership | **Payroll service owns salary** (guide §3). People's legacy `employee.base_salary/hourly_rate` columns are no longer read or written. The add-employee form sends the starting salary to Payroll |

### 1.2 Who gets a payroll row (FR-PAY-01)

Everyone employed for any part of the month gets a row: `start_date ≤ month end` and (`exit_date` is empty or `exit_date ≥ month start`). This includes leavers who are now inactive. People supplies the list; Payroll never reads People's database.

### 1.3 Monthly pay (FR-PAY-02..06, FR-PAY-09, BR-15..17)

All day counts use **the person's own schedule** (location, overridden by team, overridden by employee) and **the person's location holidays**. Time & Work owns both.

| Term | Definition |
|---|---|
| Working day | A day in the person's schedule that is not a non-optional holiday on their location calendar |
| W — working days in month | Working days in the whole calendar month (does not depend on joining or leaving) |
| E — working days employed | Working days in the month that fall inside the employment period |
| Per-day rate | `monthly gross ÷ W` (default, BR-15). Admin setting `perDayRateBasis = CALENDAR_30` gives `monthly gross ÷ 30` |
| Base pay | `Σ per-day rate` over the E working days employed. With one salary all month this is `rate × E`; a full month gives exactly the monthly gross |
| Absent days | Days with status **Absent = 1** plus **Half day = 0.5**, inside the employment period, counted up to the end of the month **or today, whichever is earlier** (FR-PAY-03) |
| Absence rule | `BALANCE_FIRST` (default): absences are covered by the remaining balance of the cover categories in order (default **Casual, then Annual**; never Sick), and only the excess is deducted. `DIRECT`: every absent day is deducted (FR-PAY-04) |
| Unpaid leave days | Approved leave in an **unpaid** category, on working days inside the month and employment period (full = 1, half = 0.5). **Always deducted** (FR-PAY-05, BR-17) |
| Deduction | `(absent days not covered + unpaid leave days) × per-day rate` (FR-PAY-06) |
| Adjustment | One manual amount per person per month, positive or negative, with a mandatory note that is shown on the payslip (FR-PAY-08) |
| Net pay | `base pay − deduction + adjustment` (FR-PAY-09). Totals are shown **per currency**, with no conversion (DR-02) |

**Daily status mapping (Appendix A, BR-04..07):**

| Status | Payroll effect (monthly pay) |
|---|---|
| P Present, L Late | none. **No money penalty for lateness** (BR-24, fixed) |
| HD Half day (worked < half-day threshold, no leave) | 0.5 absent |
| M Missing check-out | none for monthly pay (they attended). Noted on the row |
| A Absent (past working day, no check-in, no approved full-day leave) | 1 absent |
| OL On leave (paid) | none. The balance was already reduced when the leave was approved |
| OL On leave (unpaid) | counted in unpaid leave days |
| HO Holiday, WO Weekly off | not a working day |

Decisions D1–D5 were confirmed by the product owner on 12 Nov 2026 (D1 as written; D2–D5 delegated to engineering with the rules below).

**Decision D1 — half-day leave plus no check-in (confirmed).** Appendix A marks the day OL. Payroll counts the unworked half as **0.5 absent**; otherwise a half-day request would pay a full day. A half-day leave plus any check-in counts 0 absent. An *unpaid* half-day leave with no check-in therefore deducts exactly one day: 0.5 unpaid leave + 0.5 absent.

**Decision D2 — CALENDAR_30 basis.** The per-day rate is gross ÷ 30, and each uncovered absent day deducts one rate.
- Every calendar day of the month is worth **30 ÷ (days in the month)** paid days, so a full month is exactly 30 days (= the gross) in February and in 31-day months alike, and a partial month is paid in exact proportion: joined 2 Feb (27 of 28 days) → 28.93 days; joined 2 Oct (30 of 31) → 29.03 days, not a full month. (The literal "calendar days × gross/30" rule would pay a 2 Feb joiner 27/30 and a 2 Oct joiner a full month.)
- Rest days and holidays are paid as part of a month that was worked or covered by leave. Someone **unpaid on every working day** of their employment in the month earns nothing — the same outcome as the working-days basis; covered absences keep the month paid.
- A deduction is never more than the monthly pay it comes from.

**Decision D3 — salary segmentation (guide §6).** When salary changes mid-month, each day is valued at the salary effective that day: `rate(d) = gross(d) ÷ W` (or ÷ 30). Absent days are valued at their own day's rate. Cover is applied to absent days in date order. A payslip has **one currency** (DR-02, no conversion): a currency change must take effect on the 1st of a month — the salary API refuses a mid-month currency change (`CURRENCY_CHANGE_MID_MONTH`), including a record that would turn the following one into a mid-month change — and a month that still mixes currencies is an exception row that blocks finalise.

**Decision D4 — usable balance for cover.** The balance used is the category balance for the leave year that contains the month, minus days reserved by pending requests. The leave category period decides which category applies: code `CASUAL` matches `CASUAL` or a renewed copy `CASUAL_<year>` whose period contains the month. When the same code exists once per period (a 2026 and a 2027 row), Leave validates and deducts the balance of the row **whose period contains the payroll month** — a December month finalised in January still takes December's leave year.

**Decision D5 — negative net.** Pay is never negative. An adjustment that would make net pay negative is **refused when it is entered** (`NEGATIVE_NET`, with the most that can be recovered this month); the rest is recovered next month. If a recalculation later makes a row negative, the row is flagged and **blocks finalise** until the adjustment is corrected.

**Worked example (SRS §8.2, Arpa, September 2026):**

| Item | Calculation |
|---|---|
| Monthly gross | BDT 15,000 |
| W, E | 22, 22 (Sun–Thu; Sep 2026 has 22 such days and no holidays) |
| Per-day rate | 15,000 ÷ 22 = 681.8181… → shown as 681.82 |
| Absent days | 2.5 (2 Absent + 1 Half day) |
| Cover | 1 day from Casual (remaining Casual balance was 1) |
| Deducted days | 1.5 |
| Deduction | 1.5 × 681.8181… = 1,022.727… → **1,022.73** |
| Net pay | 15,000.00 − 1,022.73 = **13,977.27** |

### 1.4 Hourly pay (FR-PAY-07, BR-19, FR-LV-03, Q3, Q5)

| Rule | Detail |
|---|---|
| Pay | `Σ hours worked × hourly rate`. Hours = check-out − check-in of each closed shift (crossing midnight allowed). Each shift is valued at the rate effective on its work date |
| Missing check-out | The shift is **not paid until corrected** and is flagged with a count in the notes (BR-19) |
| Absences, leave | Not applicable. Hourly staff get no day-based leave (FR-LV-03), so there are no absence deductions and no cover |
| Overtime | Not paid; extra hours are only reported (Q3 default) |
| Manager approval of hours | Not required (Q5 default) |
| Adjustment, net | As for monthly pay |

### 1.5 Row notes (FR-PAY-10)

Each row explains its exceptions:
- pro-rated (joined or left in the month: "Joined 14 Sep — 13 of 22 working days");
- salary changed mid-month;
- absences beyond balance;
- unpaid leave;
- missing check-outs (hourly: unpaid until corrected);
- month not over ("Counted to 10 Nov — month not over");
- no salary record;
- negative net.

A row is **needs attention** (`exception = true`) when it has no salary record but was employed on a working day or worked a shift, or when its net pay is negative. A run with exceptions cannot be finalised.

### 1.6 Payroll run lifecycle (FR-PAY-11..14, BR-18)

```
          calculate / recalculate / adjustment (revision + 1)
              ┌────────┐
              ▼        │
 (none) ──► DRAFT ─────┘ ──finalise(confirm, revision)──► FINALIZING ──leave ok──► FINALIZED
              ▲                                              │                       │
              │                                    leave rejected                 reopen(confirm, reason)
              │                                              ▼                       ▼
              └──────────return to draft (revision+1)── ACTION_REQUIRED          REOPENING ──leave reversed──► DRAFT (revision + 1)
```

| Rule | Detail |
|---|---|
| Live draft | A month's run is a draft until finalised. Recalculate any time. Each recalculation re-reads salary, employment, attendance and leave, and bumps the run revision |
| Adjustments | Stored on their own (one per person per month) and kept across recalculations. Saving one recalculates that person's row |
| Finalise preconditions | (1) The month has ended in the company time zone (BR-18). (2) Months finalise **in order**: every month from the first payroll month up to the previous month has a FINALIZED run. (3) No exception rows. (4) The request carries the run revision the user reviewed (second confirmation), and it is still current. (5) The draft was calculated after the previous month was finalised; otherwise "Recalculate first", because its balances changed. (6) The draft was calculated **after the month ended** — a live draft only counts absences up to the day it was calculated. (7) After Leave rejected a finalise, the draft must be recalculated before the next attempt. (8) **What is finalised is what current facts give**: finalise re-reads every fact (employment, salary records, attendance, leave, cover balances, rules), recomputes each row with its adjustment and refuses with `409 FACTS_CHANGED`, listing every person whose numbers changed, if anything differs from the reviewed draft |
| Second confirmation | The UI shows the totals per currency and the leave that will be taken, then asks the user to type the month (`2026-09`) before sending `confirm: true` |
| On finalise | Numbers are locked as a snapshot (DR-03). Covered absences are taken from leave balances as ledger adjustments (kind *payroll*, with the payroll month). Each employee gets one "Payslip ready" notification |
| Warning (FR-PAY-13) | Viewing or calculating a month warns when the previous month is not finalised, because its covered absences will change this month's balances |
| Reopen (FR-PAY-14) | Needs `payroll:run`, a second confirmation and a reason. It is only allowed while the **next** month is not finalised (reverse order). The finalised snapshot is retained as a revision. The leave adjustments of that finalisation are reversed, and only then does the run return to DRAFT with revision + 1 |

### 1.7 Payslip (FR-PAY-15)

The payslip shows:
- employee, code, month, currency, pay type;
- base pay;
- per-day rate, or hours × rate;
- working days and working days employed;
- absent days;
- days covered by leave (per category);
- unpaid leave days;
- deducted days;
- deduction;
- adjustment and note;
- net pay;
- notes;
- status and finalised date.

Employees see **only FINALIZED** payslips, plus a live month-to-date estimate for the current month.

### 1.8 CSV export (FR-PAY-16, IR-05)

The export is UTF-8 with a BOM, one row per person, in this column order:
1. `period`, `employee_code`, `employee_name`;
2. `currency`, `pay_type`;
3. `working_days`, `days_employed`, `per_day_rate`;
4. `hours_worked`, `hourly_rate`;
5. `absent_days`, `covered_days`, `unpaid_leave_days`, `deducted_days`;
6. `base_pay`, `deduction`, `adjustment`, `adjustment_note`, `net_pay`;
7. `status`, `notes`.

Totals per currency follow as `TOTAL,<currency>` rows. Every export is audited (FR-AU-01). It needs `payroll:export`.

### 1.9 Rules (admin settings, NFR-17) and defaults

| Setting | Default (SRS) | Options |
|---|---|---|
| `perDayRateBasis` | `WORKING_DAYS` (BR-15, Q1) | `WORKING_DAYS`, `CALENDAR_30` |
| `absenceRule` | `BALANCE_FIRST` (FR-PAY-04) | `BALANCE_FIRST`, `DIRECT` |
| `absenceCoverOrder` | `CASUAL`, `ANNUAL` (BR-16, Q2; never Sick) | ordered list of **paid** leave category codes |
| `notifyPayslipReady` | `true` (FR-NT-11) | on / off |

The following rules are fixed and shown read-only:
- unpaid leave always deducted (BR-17);
- finalise only after month end, months in order (BR-18);
- hourly shifts without check-out unpaid until corrected (BR-19);
- no lateness penalty (BR-24);
- no tax or statutory deductions, no overtime pay (§16.2).

**Default behaviour is "no extra deductions":** the only deductions are uncovered absences and unpaid leave. The old configurable late penalty, overtime/weekend multipliers, fixed/percent money rules and day-25 period end are **removed**, because each contradicts BR-24, Q3, §16.2 or BR-18.

### 1.10 Permissions (SRS Appendix B)

| Permission | Allows | Default roles |
|---|---|---|
| `payroll:view` | own finalised payslips + month-to-date estimate | Employee, Admin, Super Admin |
| `salary:view` | own current pay | Employee, Admin, Super Admin |
| `salary:edit` | record salary changes; view anyone's salary history | Admin, Super Admin (removed from HR) |
| `payroll:run` | runs: calculate, adjust, finalise, reopen; payroll rules; any payslip | Admin, Super Admin |
| `payroll:export` | CSV export | Admin, Super Admin |

Every check runs on the server (NFR-02). Privileged payroll writes check current authority, so the permission cache is bypassed for writes (guide §4).

---

## 2. Production-grade engineering design (guide)

### 2.1 Ownership and data flow (guide §3, §7)

| Service | Owns | Payroll reads it through |
|---|---|---|
| People | employees, employment period, currency, location | `GET /api/internal/payroll/employees?period=YYYY-MM[&employeeIds=]` (service key + internal key) |
| Time & Work | schedules, holidays, attendance, statuses | `GET /api/internal/payroll/attendance?period=&asOf=[&employeeIds=]`: per employee, schedule mode and per-day `{date, working, holiday, status, absence (0/0.5/1), workedMinutes (closed shifts), missingCheckout}` |
| Leave | categories, balances, ledger | `GET /api/internal/payroll/leave?period=&coverOrder=[&employeeIds=]`: per employee, approved leave days in the month `{date, portion, paid, category}` and usable cover balances |
| Payroll | salary history, settings, runs, lines, adjustments, revisions, operations | — |

- **Batch, bounded calls:** one call per provider per run (NFR-07: 100 employees in under 10 s). Each call has an explicit timeout (connect 3 s, request 15 s). Calls fail closed: if a provider is down, the run is not recalculated, and Payroll never invents zero absences.
- **People no longer aggregates payroll facts.** The old `/api/payroll-facts/employee|active-employees|apply-leave-cover` endpoints are removed. Only the notification-setting lookup stays. People has no payroll responsibility.
- **No remote call inside a database transaction** (guide §7). Facts are fetched first. Then one short transaction locks the run row (`SELECT … FOR UPDATE`), checks its status and writes the lines.

### 2.2 Schema (payroll_db; guide §5)

| Table | Key columns / constraints |
|---|---|
| `payroll_settings` | single row (`id = 1` check); typed columns with check constraints; seeded with the SRS defaults |
| `salary_record` | `employee_id`, `pay_type` check (`MONTHLY`, `HOURLY`), `amount numeric(19,2) > 0`, `currency` check (`BDT`, `AUD`), `effective_date`, `reason` not null, `set_by`, `created_at`; **unique (`employee_id`, `effective_date`)**; append-only |
| `payroll_run` | `period char(7)` **unique**, `status` check, `revision`, `calculated_at/by`, `finalized_at/by`, `reopened_at/by`, `reopen_reason`, `operation_id`, `prev_finalized_at_seen`, `version` (optimistic lock) |
| `payroll_line` | `run_id` FK, `employee_id`; **unique (`run_id`, `employee_id`)**; `currency`, `pay_type`, money columns `numeric(19,2)`, day columns `numeric(6,2)`, `exception` flag, `notes jsonb`, `calc jsonb` (full calculation snapshot), `facts jsonb` (inputs snapshot), `status`, `revision` |
| `payroll_adjustment` | **unique (`period`, `employee_id`)**, `amount numeric(19,2) ≠ 0`, `note` not null, `created_by`, `updated_by`, timestamps |
| `payslip_revision` | append-only finalised snapshots: `run_id`, `period`, `employee_id`, `run_revision`, `snapshot jsonb` (numbers, calculation breakdown **and the input facts** behind them; employees are shown the payslip without the facts), `net_pay`, `currency`, `finalized_at/by`, `superseded_at`, `superseded_by_operation`; **unique (`run_id`, `run_revision`, `employee_id`)** |
| `payroll_operation` | `operation_id` unique, `operation_key` **unique** (`FINALIZE:<period>:r<rev>`, `REOPEN:<period>:r<rev>:a<n>`), `run_id`, `period`, `revision`, `action`, `status` check (`PENDING`, `SUCCEEDED`, `REJECTED`, `RESOLVED`), `attempts`, `last_sent_at`, `result jsonb`, `error`, `initiated_by`, `resolved_by/reason`, `version` (optimistic lock, `004`) |
| `payroll_job_lease` | `004`: one row per scheduled job (`payroll-operations`, `payroll-reconciliation`); `holder`, `locked_until` — only one payroll instance runs a job at a time |
| `payroll_audit_log` | existing; written **in the same transaction** as the change it records |

Money is `numeric(19,2)`, rounded HALF_UP at each money amount. Rates are kept unrounded (scale 10) inside the calculation and shown at 2 dp (DR-02, guide §5).

Legacy tables `payslip`, `finalize_operation` and `payroll_config` are superseded. Changesets are split expand / contract: `002` creates the new schema, and `003` drops the legacy tables after the data move. In production `003` ships one release after `002`.

### 2.3 Finalise and reopen workflow (guide §6, PAY01, PAY02)

1. `POST /api/payroll/runs/{period}/finalize {confirm: true, revision}` returns the existing operation for a repeated request (same revision). Otherwise it reads the current facts **outside** any transaction (a provider outage → 503, nothing changes), then locks the run, checks the preconditions in §1.6 including the facts comparison, and in one local transaction:
   - run → `FINALIZING`;
   - insert `payroll_operation(PENDING, key FINALIZE:<period>:r<rev>)`;
   - write the outbox command `payroll.leave-cover.command` with the lines that have covers;
   - write an audit entry.

   Response: always **202** `{operationId, statusUrl, resolvableAt}` with a `Location` header — also for a repeated request or a month with no cover, which completes in the same transaction (no remote dependency).
2. Leave consumes the command. Within **one** local transaction (the cover service runs with `MANDATORY` propagation inside the listener's transaction) it records:
   - the inbox key;
   - the `leave_operation` by key;
   - the ledger rows, `source_type = COVER`, with idempotency key `COVER:<opKey>:<emp>:<type>`;
   - the result outbox.

   It validates every cover against the usable balance before writing anything. **Invalid cover is rejected explicitly for the whole run**, with per-employee reasons, and a malformed command is rejected permanently. Every other failure (a lock conflict with a leave approval at the same moment, a database timeout) is **thrown**: the whole delivery rolls back and is retried, then dead-lettered, and payroll's reconciler re-sends it — a temporary problem never becomes a rejection. A duplicate command, or one whose key Leave already holds (including a fenced `CANCELLED` key), returns the recorded result and touches no balance.
3. Payroll consumes `leave.cover.result` idempotently (inbox). It locks the run first, then reads the operation, and acts only if the operation id, the run revision and `FINALIZING` all match; stale or duplicate results are ignored. A result that **contradicts** an already closed operation raises an alert (`payroll.operations.result.mismatch`, ERROR log) and changes nothing.
   - Success → run and lines `FINALIZED`, plus one `payslip_revision` per line, notifications (outbox, dedupe `payslip-ready:<period>:r<rev>:<emp>`) and audit.
   - Rejection → `ACTION_REQUIRED` with reasons shown. The user can fix the cause and press *Return to draft*; nothing was deducted.
4. Reopen: `POST /runs/{period}/reopen {confirm: true, reason}` → `REOPENING` plus the operation `REOPEN:<period>:r<rev>`. Leave reverses **exactly the ledger rows recorded for the finalise operation key** (keyed `REVERSE:<finalizeKey>:…`, so it restores once), then replies. Payroll marks the retained revisions superseded and returns the run to `DRAFT` with revision + 1.
5. **Recovery.**
   - *Outbox:* a message the broker cannot route (returned) stays pending in the outbox and is published again.
   - *Reconciler:* re-sends the command of `PENDING` operations after 2 minutes (same key, so it is idempotent), with exponential backoff capped at 30 min. It takes the run lock, and the operation's `version` guards the write, so a re-send can never overwrite a result that arrives at the same moment. It alerts (ERROR log, at most once per 15 min per operation, plus `payroll.operations.pending` / `payroll.operations.oldest.age.seconds`) when an operation is older than 15 min. Only the instance holding the `payroll_job_lease` runs it.
   - *Manual resolve* (`POST /operations/{id}/resolve {reason}`, audited): allowed only after `resolvableAt` (5 min, so the automatic re-send gets its chance). **The operator does not choose the outcome.** Payroll first **fences** the key in Leave (`POST /api/internal/payroll/operation/fence`): if Leave never processed it, Leave records `CANCELLED` under the unique key, so a command still in flight replays `CANCELLED` and can never deduct or restore leave; if Leave did process it, Leave returns that outcome. Payroll then applies Leave's outcome (`SUCCEEDED` → finalised / reopened; otherwise finalise → `ACTION_REQUIRED`, reopen → stays `FINALIZED`). An optional `outcome` from the operator is only compared and reported. If Leave cannot be reached the resolve fails with 503 and nothing changes.
   - *Reconciliation:* `GET /runs/{period}/reconciliation` compares the month with **every** cover row Leave holds for it, across all finalise keys: a finalised month must hold exactly its line covers under its current finalise key; a draft month must hold none. Rows under any other key are reported as **orphans**. An hourly sweep (`payroll.reconciliation.mismatches`) checks the last 24 months.
   - The payroll page shows every operation (state, sends, errors, who resolved it and why), a *Resolve…* action once allowed, and *Check against Leave ledger*.
   - Delivery is at-least-once with idempotent consumers. Exactly-once is not claimed.

### 2.4 API (all under gateway `/payroll/api/payroll`)

| Method | Path | Permission |
|---|---|---|
| GET | `/runs?page&size` | run |
| GET | `/runs/{period}` (run, totals per currency, warnings, finalise readiness) | run |
| GET | `/runs/{period}/lines?page&size&q&exceptionsOnly` | run |
| GET | `/runs/{period}/lines/{employeeId}` (payslip) | run |
| POST | `/runs/{period}/calculate` | run |
| PUT / DELETE | `/runs/{period}/adjustments/{employeeId}` `{amount, note}` | run |
| POST | `/runs/{period}/finalize` `{confirm, revision}` → 202 | run |
| POST | `/runs/{period}/reopen` `{confirm, reason}` → 202 | run |
| POST | `/runs/{period}/return-to-draft` (from ACTION_REQUIRED) | run |
| GET | `/runs/{period}/export.csv` | export |
| GET | `/runs/{period}/reconciliation` | run |
| GET | `/operations/{operationId}`, `/operations?status=PENDING` | run |
| POST | `/operations/{operationId}/resolve` `{reason, outcome?}` — fences Leave, applies Leave's outcome; 409 `TOO_EARLY` before `resolvableAt` | run |
| GET / PUT | `/settings` | view (read), run (write) |
| GET | `/me/payslips`, `/me/payslips/{period}`, `/me/estimate`, `/me/salary` | payroll:view / salary:view (own) |
| GET / POST | `/salary/employees/{id}` (history + current) / `{payType, amount, currency, effectiveDate, reason}` | salary:edit, or own with salary:view |

Errors use the common envelope `{code, message, correlationId}`:
- 409 for state or revision conflicts;
- 422 for business-rule refusals such as "month not over", "finalise 2026-09 first" or "3 rows need attention";
- 403 for permission.

### 2.5 Tests (NFR-13, AT-14, AT-15, PAY01–03, DATA01)

**Unit (engine):**
- the worked example;
- full month, joiner, leaver, mid-month salary change;
- `CALENDAR_30`, `DIRECT` vs `BALANCE_FIRST`, cover order;
- unpaid leave, half-day leave (D1), HD;
- month not over;
- hourly with a missing check-out;
- hourly rate change;
- negative net;
- rounding.

**Service tests:**
- finalise preconditions (order, month end, calculated before month end, after a Leave rejection, revision, exceptions, staleness);
- facts drift (attendance change, joiner/leaver) and the `FACTS_CHANGED` refusal;
- idempotent result handling: duplicate, stale, wrong-revision and contradicting results; run locked before the operation is read;
- re-send under the run lock; reconciler backoff, lease and alert de-duplication;
- manual resolve: too early, reason required, Leave fenced → rejected, Leave applied → completed, reopen stays finalised, Leave unreachable → unchanged;
- month reconciliation: matches, orphan key, draft holding cover, operation in flight;
- Leave: all-or-nothing cover, once per key, reversal once, month ledger, malformed command, temporary failure re-thrown, late command after a fence.

**End-to-end** (local stack, scripted): seeded scenarios for Sep–Nov 2026 calculated, finalised, reopened and re-finalised, with leave balances checked after each step (`scripts/test-payroll-e2e.mjs`, date-independent). **Recovery** (`scripts/test-payroll-recovery.mjs`): concurrency, Leave rejection, facts changed after review, lost command, manual resolve with the command in flight, manual resolve with lost results, orphan detection — against the real broker.

---

## 3. Gap analysis of the previous implementation → fix

| # | Previous behaviour | Violates | Fix |
|---|---|---|---|
| G1 | `workingDaysInMonth` = calendar days (30/31); `daysEmployed` = calendar days | FR-PAY-02, BR-15 | Time & Work supplies W and E from the person's schedule and holidays |
| G2 | Absent = any past scheduled day without check-in. It ignored approved leave, employment period and half days, and counted today | FR-PAY-03, BR-06 | Per-day status from Time & Work including leave; employment window; HD = 0.5; up to today |
| G3 | Unpaid leave counted whole requests even across months, not only working days | FR-PAY-05 | Per-day leave inside the month on working days |
| G4 | Late penalty (200/occurrence, enabled in Qii config) | BR-24, FR-ATT-06 | Removed |
| G5 | Overtime/weekend multipliers, fixed/percent money rules | §16.2, Q3 | Removed |
| G6 | `DAY_OF_MONTH` period end (day 25) allowed finalising before month end | BR-18, FR-PAY-12 | Fixed month end, company time zone |
| G7 | Finalise per payslip; no month run, no order check, no second confirmation | FR-PAY-11/12, BR-18 | `payroll_run` with lifecycle, order and revision checks |
| G8 | Adjustment only as a calculate parameter; lost when the month is recalculated | FR-PAY-08 | `payroll_adjustment` table, kept across recalculations, audited |
| G9 | Hourly: unpaid leave deducted at 8 h/day; late penalty | FR-LV-03, BR-24 | Hourly = closed shifts × rate only |
| G10 | Salary in People and Payroll (fallback to People base salary); no segmentation; duplicate history rows | guide §3/§6, FR-SAL-02 | Payroll owns `salary_record` (unique per date), segmented by day; People salary fields retired |
| G11 | Remote HTTP/gRPC calls inside `@Transactional` calculate | guide §7 | Fetch first, short locked transaction |
| G12 | People aggregated payroll facts (cross-service orchestration in the wrong service) | guide §3 | Payroll reads each owner directly via bounded batch endpoints |
| G13 | Employees saw DRAFT payslips; MTD from a stored draft | FR-SAL-03 | Own FINALIZED only + live estimate |
| G14 | Warnings and export loaded `findAll()` and filtered in memory | guide §7 | SQL by period |
| G15 | Reopen kept old finals inside JSON (`priorFinals`); no revision table | guide §5/§6 | `payslip_revision`, append-only |
| G16 | Finalise could wait in a sleep loop on the request thread (`wait=true`) | guide §6 | 202 + poll only |
| G17 | Audit in `REQUIRES_NEW` (could commit without the change, or the change without audit) | FR-AU-01 | Same transaction |
| G18 | Negative net allowed and finalisable | D5 | Flagged, blocks finalise |
| G19 | HR role held `salary:edit/view` | SRS Appendix B | Removed from HR by People migration |
| G20 | Leave cover: one command per payslip; reversal trusted payroll's numbers | guide §6 | One command per run revision; reversal replays the recorded ledger rows |
| G21 | Frontend: per-payslip calculate form with manual hours/absence overrides, multipliers and late penalty rules | FR-PAY-11, BR-24 | Month-run UI, adjustments, two-step finalise, reopen with reason, CSV, rules with SRS defaults, employee payslips |
| G22 | No row notes for month-not-over / joiner / leaver; no exception state | FR-PAY-10 | Notes + exceptions |
| G23 | Notification via a synchronous HTTP hook **and** outbox | guide §7 | Outbox only, dedupe key per revision |

## 4. Implementation order

1. Payroll schema `002`/`003`, settings defaults, salary record migration from legacy data.
2. Pure calculation engine and unit tests (worked example first).
3. Provider endpoints: People employees, Time & Work attendance, Leave facts; remove People aggregation.
4. Run service (calculate, adjustments, warnings, export) and finalise/reopen workflow with leave changes, reconciler and resolution.
5. API + RBAC; People migration for HR salary permissions.
6. Frontend: payroll run page, payslip view, rules, salary tab and employee form.
7. Seed scenarios (Sep–Nov 2026), end-to-end run, finalise/reopen checks, export of the seed snapshot.

## 5. Test scenarios seeded for Qii (Sep–Nov 2026)

| Person | Scenario | Expected (checked by hand and by test) |
|---|---|---|
| Arpa Sen (new, BDT 15,000, Dhaka Sun–Thu) | Sep: 2 Absent + 1 Half day; Casual balance 1, Annual 0 | rate 681.82, covered 1 Casual, deducted 1.5 = 1,022.73, net **13,977.27** |
| Tania Sultana (BDT 65,000) | Sep: 2 days approved **unpaid** leave | deduction 2 × 2,954.55 = 5,909.09, net 59,090.91 |
| Karim Ali (BDT 58,000 → 62,000 from 16 Sep) | Sep: mid-month raise | segmented base 60,000.00 (11 days × 2,636.36… + 11 days × 2,818.18…) |
| Tanvir Hasan (new, joined 14 Sep, BDT 44,000) | Sep joiner | E = 13 of 22, base 26,000.00 |
| Rumana Akter (new, resigned, exit 15 Oct, BDT 40,000) | Oct leaver | Oct W = 21, E = 11, base 40,000 × 11 ÷ 21 = 20,952.38 |
| Imran Hossain (6-day team, BDT 60,000) | Oct: half-day Casual leave with no check-in (D1), 1 Absent | absent 1.5, covered from Casual |
| Farhana Begum (BDT 70,000) | Oct: 3 Absent; Casual 0, Annual 2 | cover 2 Annual, deducted 1 |
| Owen Fraser (AUD 45/h, hourly) | Oct: shifts + 1 missing check-out | missing shift unpaid, flagged |
| Anindya Basak (BDT 200/h, hourly schedule) | Nov to date: shifts | hours × 200, month not over |
| Sophie Nguyen (AUD 7,200) / Marcus Blake (AUD 9,500) | Oct: +500 bonus / −1,000 advance recovery | net AUD 7,700.00 / 8,500.00 |
| Asman Rahman | late arrivals | no money penalty |
| Everyone else | full attendance | net = gross |

The flow seeded and kept in the database:
1. Calculate Sep, finalise it (leave balances reduced).
2. Reopen Sep (balances restored), then finalise it again.
3. Calculate and finalise Oct.
4. Nov stays a live draft (month not over).

How to reproduce:

```bash
FORCE=1 bash scripts/seed-payroll-scenarios.sh   # dev only — resets payroll runs and cover ledger
node scripts/test-payroll-e2e.mjs                # 102 checks: money, guards, finalise/reopen, ordering, CSV
./gradlew :payroll-service:test                  # engine (worked example etc.) + workflow gates
```

`scripts/seed/qii/*.sql` (loaded by `scripts/seed-qii-dev.sh load`) contains the resulting state, so a fresh dev
environment starts with September and October finalised and November as a live draft.

## 6. Verification evidence (12 Nov 2026, local stack)

| Check | Result |
|---|---|
| Engine unit tests (`PayrollEngineTest`) — worked example, joiner, leaver, segmentation, unpaid leave, cover order, Direct, ÷30, hourly, missing check-out, negative net, missing salary, month not over; D1 half-day leave (paid and unpaid); D2 ÷30 full February, proportional joiners/leavers, unpaid-every-working-day, covered month, segmentation; D3 day-rate absences, mid-month currency change; deduction cap | 29 / 29 pass |
| Workflow tests (`PayrollWorkflowTest`) — every finalise blocker incl. calculated-before-month-end and after-rejection, facts drift and `FACTS_CHANGED`, idempotent / stale / contradicting results, run-locked re-send, fenced manual resolve, month reconciliation; D5 negative-net adjustment refused with its limit; D3 currency change only on the 1st | 39 / 39 pass |
| Reconciler tests — backoff, single-instance lease, alert de-duplication | 4 / 4 pass |
| Leave tests (`LeaveCoverServiceTest`, `LeaveCoverCommandListenerTest`) — all-or-nothing cover, once per key, reversal once, month ledger, malformed command, temporary failure re-thrown, late command after a fence; D4 balance year of the category row valid in the month (December finalised in January) | 19 / 19 pass |
| End-to-end (`scripts/test-payroll-e2e.mjs`, date-independent) against People, Time & Work, Leave, Payroll, Gateway, RabbitMQ | 104 / 104 pass |
| Recovery (`scripts/test-payroll-recovery.mjs`) against the real broker — concurrency, balance changed before finalise, Leave rejection in a race, facts changed after review, lost command (re-sent on attempt 2), manual resolve with the command in flight (fenced; the delayed command took nothing), manual resolve with lost results (completed once), orphan detection | 56 / 56 pass |
| SRS worked example in the running system (Arpa, Sep 2026) | per-day 681.82, deduction 1,022.73, net **13,977.27** |
| AT-15 finalise → reopen → re-finalise | Arpa Casual 1 → 0 → 1 → 0; ledger net −1 (deducted once) |
| PAY01 duplicate finalise | same operation returned, no second deduction; reconciliation matches |
| DATA01 five concurrent recalculations | all 200, one row per employee (30 / 30) |
| NFR-07 performance | ~1 s per run for 30 employees (one batch call per provider) |
| Whole backend `./gradlew test` | **267 / 267 pass** (approval-chain code moved to `LeaveApprovalChainBuilder`, so `LeaveApplicationService` is 1123 lines; the Time & Work half-day tests match the fit-into-shift behaviour) |

Known limits (recorded, not hidden):
- Schedules are not versioned in Time & Work. Recalculating an old draft month uses the schedule as it is now.
  Finalised months are unaffected, because they are snapshots.
- Decisions D1–D5 were confirmed on 12 Nov 2026 (§1.3); any later change to them is a rule change with its own release.
- A salary change effective inside an already finalised month changes nothing in that month's snapshot; it applies only if that month is reopened and recalculated.
- Legacy People columns `employee.base_salary` and `employee.hourly_rate` are no longer read or written. They can
  be dropped in a later contract migration.
