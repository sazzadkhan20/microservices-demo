# Database indexing (SRS NFR-07)

NFR-07: pages < 2 s and check-in < 1 s for 95 % of requests with 50 employees and 3 years of data.
Indexes were chosen from **measurements**, not guesses: the real service schemas were cloned into a
throwaway PostgreSQL 16 container, filled with a 3-year / 50-employee dataset, and the exact SQL the
repositories issue was timed with `EXPLAIN ANALYZE` before and after.

## 1. Dataset used (high side of realistic)

| Table | Rows | | Table | Rows |
|---|---|---|---|---|
| attendance_record | 39 200 | | notifications | 200 000 |
| work_log | 117 600 | | timework_audit_log | 400 000 |
| leave_application / approvals | 2 250 / 4 500 | | people_audit_log | 300 000 |
| leave_balance_ledger | 12 000 | | notification_audit_log | 200 000 |
| payslip_revision / payroll_line | 1 800 / 1 800 | | leave / payroll / files / report audit | 60–80 000 each |
| file_object / report_export | 8 000 / 6 000 | | | |

## 2. Indexes added (one Liquibase changeset each, with rollback)

| Service (changelog) | Index | Serves | Before → after |
|---|---|---|---|
| Time & Work (`002-read-path-indexes`) | `idx_work_log_employee_date (employee_id, work_date)` | work-log by employee / day / team range | 5–7 ms seq scan → 0.08–0.18 ms |
| | `idx_attendance_work_date (work_date)` | team day view, dashboard, reminder job (looped per day) | 0.56 → 0.04 ms |
| | `idx_attendance_open_session (employee_id) WHERE check_in_at IS NOT NULL AND check_out_at IS NULL` | live "who is checked in" board | 2.2 ms seq scan → 0.06 ms |
| | `idx_work_task_assignee_employee (employee_id)` | "my tasks" (PK leads with task_id) | small table today; grows with tasks |
| | `idx_timework_audit_at (at DESC)` | audit page newest-first | 21 ms → 0.06 ms |
| Leave (`002-read-path-indexes`) | `idx_leave_ledger_employee_created (employee_id, created_at DESC)` | balance history | 0.70 → 0.36 ms |
| | `idx_leave_ledger_year (year)` | balance report | 2.2 → 0.6 ms |
| | `idx_leave_ledger_operation_key_prefix (operation_key text_pattern_ops) WHERE operation_key IS NOT NULL` | cover reversal / finalize `LIKE 'COVER:…%'` (the unique index cannot serve LIKE under `en_US.utf8`) | 1.4 → 0.07 ms |
| | `idx_leave_audit_at (at DESC)` | audit page | 6.9 → 0.07 ms |
| People (`013-read-path-indexes`) | `idx_people_audit_at (at DESC)` | audit page | 17.8 → 0.07 ms |
| Payroll (`005-read-path-indexes`) | `idx_payroll_audit_at (at DESC)` | audit page | 5.9 → 0.07 ms |
| File (`002-read-path-indexes`) | `idx_file_scan_status_created (scan_status, created_at)` | scan sweeper | 0.9 → 0.15 ms |
| Notification (`002-read-path-indexes`) | `idx_notif_employee_created (employee_id, created_at DESC)` | inbox page | 3.7 → 0.06 ms |
| | `idx_notif_employee_unread (employee_id) WHERE read_flag = false` | bell badge count (every page load) | 2.8 → 0.06 ms |
| Report (`002-read-path-indexes`) | `idx_report_export_created (created_at DESC)` | export history | 0.8 → 0.05 ms |

Checked and **deliberately not indexed** (already served by an existing index, or the table stays tiny so
PostgreSQL would seq-scan anyway): attendance by employee/range (`uk_attendance_emp_date`), leave overlap
checks (`idx_leave_app_employee` / `idx_leave_app_status`), payslips (`idx_payslip_revision_employee`),
leave_balance by year (600 rows), employee by team/manager (50 rows), files/notification/report audit
(already indexed). Existing indexes were not dropped.

## 3. Query change (same results, fewer round trips)

The leave list endpoints (`listForCaller`, `listForEmployee`, internal `listAll`) loaded approval steps with
**one query per application** — 2 250 queries for an HR user at 3 years. They now load steps in batches of
500 (`findByApplicationIdInOrderByApplicationIdAscStepOrderAscIdAsc`) — 5 queries — and map them back in
exactly the original order. `LeaveListBatchApprovalsTest` proves the response is identical to the old
per-application mapping (1 203 applications across 3 batches, including applications without steps).

## 4. Why this is safe

* **Results cannot change.** An index only changes *how* PostgreSQL finds rows, never *which* rows. No
  column, constraint, default or data was modified, and none of the new indexes is `UNIQUE`, so no
  existing row can make a migration fail.
* **Migration safety.** Each index is its own changeset (`CREATE INDEX IF NOT EXISTS`, `dbms: postgresql`,
  rollback `DROP INDEX IF EXISTS`). Plain `CREATE INDEX` runs inside the changeset transaction — atomic,
  no half-built (`INVALID`) index is ever left behind. Each database is owned by one service and Liquibase
  runs before that service accepts traffic, so the short write lock has no waiting writers. Measured build
  time for all 15 indexes on the 3-year dataset: ≈1 s of database time.
* **Write cost.** One extra index entry per insert on the affected tables — microseconds; these tables
  are append-mostly.
* **Old migrations untouched**, so Liquibase checksums on existing databases still match.

## 5. When data grows far beyond NFR-07

For a table above a few million rows, add future indexes with `CREATE INDEX CONCURRENTLY` in a changeset
with `runInTransaction: false`, then verify `pg_index.indisvalid` (a failed concurrent build leaves an
INVALID index that `IF NOT EXISTS` would skip). Audit tables are the first candidates for a retention /
partitioning policy.

## 6. Checking index health in an environment

```sql
-- indexes never used since stats reset (candidates to review, not to drop blindly)
SELECT relname, indexrelname, idx_scan FROM pg_stat_user_indexes ORDER BY idx_scan, relname;
-- invalid indexes (should return nothing)
SELECT indexrelid::regclass FROM pg_index WHERE NOT indisvalid;
```

## 7. Verification on the running dev stack (after the change)

| Check | Result |
|---|---|
| Liquibase on all 7 service databases | 15/15 changesets `EXECUTED`, 15 indexes present, 0 invalid indexes |
| Backend unit / contract tests | 315 / 315 (313 existing + 2 new) |
| Security, authentication & RBAC probe | 112 / 112 |
| Payroll e2e (after `FORCE=1 seed-payroll-scenarios.sh`) | 103 / 104 — the same single known difference as before (Arpa's approved unpaid half-day) |
| Payroll recovery | 56 / 56 |
| Leave list vs database (read-only) | 84 / 84 applications, every approval step identical; `/leave/my` OK |
| Load test, 150 users realistic mix | 34 292 requests, **0 errors**, 171 req/s, p50 35 ms, p95 568 ms (before: 31 621 req, 157 req/s, p50 43 ms, p95 776 ms) |

Order safety: every repository list without `ORDER BY` on an indexed table was checked — callers key by a
unique (employee, day), sum minutes, or sort explicitly, so a different physical row order cannot change output.
