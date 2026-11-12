#!/usr/bin/env bash
# Payroll test scenarios for Qii Creative, Sep–Nov 2026 (docs/PAYROLL_SPEC.md §5). DEVELOPMENT ONLY.
#
#   bash scripts/seed-payroll-scenarios.sh            # refuses if payroll runs already exist
#   FORCE=1 bash scripts/seed-payroll-scenarios.sh    # resets payroll runs + cover ledger, then reseeds
#
# Seeds, per owning service database:
#   people_db    3 scenario employees (Arpa — SRS worked example, Tanvir — joiner, Rumana — leaver),
#                currency codes fixed to BDT/AUD (DR-02)
#   timework_db  attendance 1 Sep 2026 – yesterday (≤ 30 Nov) for everyone employed, with absences, a half day, late
#                arrivals, a missing check-out and hourly shifts
#   leave_db     unpaid leave, a half-day casual leave, cover balances for the scenarios
#   payroll_db   clean salary records (incl. a mid-month raise and hourly rates)
# Then run: node scripts/test-payroll-e2e.mjs   (calculates, finalises, reopens and checks every number)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PROJECT="${COMPOSE_PROJECT_NAME:-microservices-demo}"
HRMS="${PROJECT}-postgres-hrms-1"
PAY="${PROJECT}-postgres-payroll-1"
psqlq() { local c="$1" db="$2"; shift 2; docker exec -i "$c" psql -v ON_ERROR_STOP=1 -q -U postgres -d "$db" "$@"; }
val() { docker exec -i "$1" psql -U postgres -d "$2" -tAc "$3"; }

runs="$(val "$PAY" payroll_db "select count(*) from payroll_run")"
if [[ "$runs" != "0" && "${FORCE:-0}" != "1" ]]; then
  echo "payroll_db already has ${runs} payroll run(s) — refusing (FORCE=1 resets payroll in this DEV environment)"
  exit 1
fi

echo "==> people_db: scenario employees"
psqlq "$HRMS" people_db <<'SQL'
BEGIN;
UPDATE employee SET pay_currency = 'AUD' WHERE pay_currency IS NOT NULL AND upper(pay_currency) NOT IN ('BDT', 'AUD');
UPDATE location SET default_currency = 'AUD' WHERE default_currency IS NOT NULL AND upper(default_currency) NOT IN ('BDT', 'AUD');

INSERT INTO employee (employee_code, full_name, preferred_name, email, job_title, department_id, designation_id, manager_id,
                      location_id, team_id, employment_type, start_date, pay_currency, status, active, work_pattern,
                      exit_date, exit_reason, base_salary, hourly_rate, custom_fields, created_at, updated_at)
SELECT v.code, v.name, v.pref, v.email, v.title, 2, 6, v.mgr, 1, 1, 'FULL_TIME', v.start::date, 'BDT', v.status, v.active,
       'LOCATION_DEFAULT', v.exit::date, v.exit_reason, 0, 0, '{}'::jsonb, now(), now()
FROM (VALUES
  ('QII-0050', 'Arpa Sen',     'Arpa',   'arpa@qiicreative.local',   'Designer',        4, '2025-01-01', 'Active',   true,  NULL,         NULL),
  ('QII-0051', 'Tanvir Hasan', 'Tanvir', 'tanvir@qiicreative.local', 'Junior Designer', 4, '2026-09-14', 'Active',   true,  NULL,         NULL),
  ('QII-0052', 'Rumana Akter', 'Rumana', 'rumana@qiicreative.local', 'Copywriter',      3, '2023-05-01', 'Resigned', false, '2026-10-15', 'Resigned — relocating')
) AS v(code, name, pref, email, title, mgr, start, status, active, exit, exit_reason)
WHERE NOT EXISTS (SELECT 1 FROM employee e WHERE e.employee_code = v.code);

INSERT INTO users (employee_id, email, is_active, created_at, updated_at)
SELECT e.id, e.email, e.active, now(), now() FROM employee e
WHERE e.employee_code IN ('QII-0050', 'QII-0051', 'QII-0052')
  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.employee_id = e.id);

INSERT INTO app_user_role (employee_id, role_id)
SELECT e.id, r.id FROM employee e, app_role r
WHERE e.employee_code IN ('QII-0050', 'QII-0051', 'QII-0052') AND r.name = 'Employee'
ON CONFLICT DO NOTHING;
COMMIT;
SQL
ARPA="$(val "$HRMS" people_db "select id from employee where employee_code='QII-0050'")"
TANVIR="$(val "$HRMS" people_db "select id from employee where employee_code='QII-0051'")"
RUMANA="$(val "$HRMS" people_db "select id from employee where employee_code='QII-0052'")"
echo "    Arpa=${ARPA} Tanvir=${TANVIR} Rumana=${RUMANA}"

echo "==> payroll_db: reset payroll + salary records"
psqlq "$PAY" payroll_db -v arpa="$ARPA" -v tanvir="$TANVIR" -v rumana="$RUMANA" <<'SQL'
BEGIN;
DELETE FROM payslip_revision;
DELETE FROM payroll_operation;
DELETE FROM payroll_line;
DELETE FROM payroll_adjustment;
DELETE FROM payroll_run;
DELETE FROM salary_record;
INSERT INTO salary_record (employee_id, effective_date, pay_type, amount, currency, reason, set_by) VALUES
  (1,  '2019-01-15', 'MONTHLY', 150000, 'BDT', 'Starting salary', 1),
  (2,  '2020-03-01', 'MONTHLY',  95000, 'BDT', 'Starting salary', 1),
  (3,  '2020-06-01', 'MONTHLY',  90000, 'BDT', 'Starting salary', 1),
  (4,  '2021-02-10', 'MONTHLY', 110000, 'BDT', 'Starting salary', 1),
  (5,  '2021-08-01', 'MONTHLY',   9500, 'AUD', 'Starting salary', 1),
  (6,  '2022-01-15', 'MONTHLY',  65000, 'BDT', 'Starting salary', 1),
  (7,  '2022-05-01', 'MONTHLY',  60000, 'BDT', 'Starting salary', 1),
  (8,  '2022-09-12', 'MONTHLY',   7200, 'AUD', 'Starting salary', 1),
  (9,  '2023-01-08', 'MONTHLY',  58000, 'BDT', 'Starting salary', 1),
  (9,  '2026-09-16', 'MONTHLY',  62000, 'BDT', 'Annual review — promotion to Senior Designer', 1),
  (10, '2024-03-01', 'MONTHLY',  45000, 'BDT', 'Starting salary', 1),
  (11, '2024-06-15', 'HOURLY',      45, 'AUD', 'Starting rate (contractor)', 1),
  (12, '2021-11-01', 'MONTHLY',  70000, 'BDT', 'Starting salary', 1),
  (13, '2026-10-30', 'HOURLY',     200, 'BDT', 'Starting rate', 1),
  (14, '2026-10-30', 'HOURLY',     200, 'BDT', 'Starting rate', 1),
  (15, '2026-11-01', 'MONTHLY',  30000, 'BDT', 'Starting salary', 1),
  (16, '2026-11-01', 'MONTHLY',  30000, 'BDT', 'Starting salary', 1),
  (17, '2026-11-01', 'MONTHLY',  30000, 'BDT', 'Starting salary', 1),
  (18, '2026-11-01', 'MONTHLY',  30000, 'BDT', 'Starting salary', 1),
  (19, '2026-11-01', 'MONTHLY',  30000, 'BDT', 'Starting salary', 1),
  (20, '2026-11-01', 'MONTHLY',  30000, 'BDT', 'Starting salary', 1),
  (21, '2026-11-01', 'MONTHLY',  30000, 'BDT', 'Starting salary', 1),
  (22, '2026-11-10', 'MONTHLY',   6000, 'AUD', 'Starting salary', 1),
  (23, '2026-11-10', 'MONTHLY',   6000, 'AUD', 'Starting salary', 1),
  (24, '2026-11-10', 'MONTHLY',   6000, 'AUD', 'Starting salary', 1),
  (25, '2026-11-10', 'HOURLY',     150, 'BDT', 'Starting rate', 1),
  (27, '2026-11-10', 'MONTHLY',  30000, 'BDT', 'Starting salary', 1),
  (28, '2026-11-10', 'MONTHLY',  30000, 'BDT', 'Starting salary', 1),
  (:arpa,   '2025-01-01', 'MONTHLY', 15000, 'BDT', 'Starting salary', 1),
  (:tanvir, '2026-09-14', 'MONTHLY', 44000, 'BDT', 'Starting salary', 1),
  (:rumana, '2023-05-01', 'MONTHLY', 40000, 'BDT', 'Starting salary', 1);
-- Employee 26 deliberately has no salary record: the November draft flags the row (FR-PAY-10 exception).
COMMIT;
SQL

echo "==> timework_db: attendance 1 Sep 2026 – yesterday"
psqlq "$HRMS" timework_db -v arpa="$ARPA" -v tanvir="$TANVIR" -v rumana="$RUMANA" <<'SQL'
BEGIN;
DELETE FROM attendance_record WHERE work_date BETWEEN '2026-09-01' AND '2026-11-30';
WITH emp(id, zone, dows, t_in, t_out, d_from, d_to) AS (VALUES
  (1,  'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-09-01'::date, NULL::date),
  (2,  'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-09-01'::date, NULL::date),
  (3,  'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-09-01'::date, NULL::date),
  (4,  'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-09-01'::date, NULL::date),
  (5,  'Australia/Darwin', '{1,2,3,4,5}'::int[],   '09:00'::time, '17:00'::time, '2026-09-01'::date, NULL::date),
  (6,  'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-09-01'::date, NULL::date),
  (7,  'Asia/Dhaka',       '{0,1,2,3,4,6}'::int[], '12:00'::time, '20:00'::time, '2026-09-01'::date, NULL::date),
  (8,  'Australia/Darwin', '{1,2,3,4,5}'::int[],   '09:00'::time, '17:00'::time, '2026-09-01'::date, NULL::date),
  (9,  'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-09-01'::date, NULL::date),
  (10, 'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-09-01'::date, NULL::date),
  (11, 'Australia/Darwin', '{1,2,3,4,5}'::int[],   '09:00'::time, '17:00'::time, '2026-09-01'::date, NULL::date),
  (12, 'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-09-01'::date, NULL::date),
  (13, 'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-11-01'::date, NULL::date),
  (15, 'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-11-01'::date, NULL::date),
  (16, 'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-11-01'::date, NULL::date),
  (17, 'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-11-01'::date, NULL::date),
  (18, 'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-11-01'::date, NULL::date),
  (19, 'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-11-01'::date, NULL::date),
  (20, 'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-11-01'::date, NULL::date),
  (21, 'Asia/Dhaka',       '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-11-01'::date, NULL::date),
  (:arpa,   'Asia/Dhaka',  '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-09-01'::date, NULL::date),
  (:tanvir, 'Asia/Dhaka',  '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-09-14'::date, NULL::date),
  (:rumana, 'Asia/Dhaka',  '{0,1,2,3,4}'::int[],   '10:00'::time, '18:00'::time, '2026-09-01'::date, '2026-10-15'::date)
)
INSERT INTO attendance_record (employee_id, work_date, check_in_at, check_out_at, status_code, status_label,
                               time_zone, device_type, device, location_label, note, created_at, updated_at)
SELECT e.id, d::date, (d::date + e.t_in) AT TIME ZONE e.zone, (d::date + e.t_out) AT TIME ZONE e.zone,
       'P', 'Present', e.zone, 'Desktop', 'Seed', 'Office', 'Payroll scenario seed', now(), now()
-- d_to NULL = up to yesterday in the employee's own zone (capped at 30 Nov), so the November live draft
-- only has the absences the scenarios define, whatever day the seed runs.
FROM emp e, generate_series(e.d_from, COALESCE(e.d_to, LEAST(date '2026-11-30', (now() AT TIME ZONE e.zone)::date - 1)),
                            interval '1 day') d
WHERE extract(dow FROM d)::int = ANY (e.dows);

-- Absences (no check-in): Arpa 8–9 Sep; Imran 14 Oct; Farhana 5–7 Oct; Priya 4 Nov (live draft).
DELETE FROM attendance_record WHERE (employee_id, work_date) IN (
  (:arpa, '2026-09-08'), (:arpa, '2026-09-09'), (7, '2026-10-14'),
  (12, '2026-10-05'), (12, '2026-10-06'), (12, '2026-10-07'), (10, '2026-11-04'));
-- Approved leave days carry no attendance: Tania unpaid 8–9 Sep, Imran half-day 12 Oct, existing Nov leave.
DELETE FROM attendance_record WHERE (employee_id, work_date) IN (
  (6, '2026-09-08'), (6, '2026-09-09'), (7, '2026-10-12'), (6, '2026-11-04'), (8, '2026-11-04'), (12, '2026-11-06'));
-- Half day (BR-05): Arpa 10 Sep worked 3 h (< 4 h threshold).
UPDATE attendance_record SET check_out_at = ('2026-09-10'::date + '13:00'::time) AT TIME ZONE 'Asia/Dhaka',
       status_code = 'HD', status_label = 'Half Day' WHERE employee_id = :arpa AND work_date = '2026-09-10';
-- Late arrivals (reported only — BR-24): Asman 6 and 7 Sep at 10:40.
UPDATE attendance_record SET check_in_at = (work_date + '10:40'::time) AT TIME ZONE 'Asia/Dhaka',
       status_code = 'L', status_label = 'Late' WHERE employee_id = 1 AND work_date IN ('2026-09-06', '2026-09-07');
-- Missing check-out (BR-19): Owen 15 Oct — that shift is not paid until corrected.
UPDATE attendance_record SET check_out_at = NULL, status_code = 'M', status_label = 'Missing check-out'
 WHERE employee_id = 11 AND work_date = '2026-10-15';
-- Hourly schedule employee (Anindya): five 6-hour shifts + one open shift in November.
INSERT INTO attendance_record (employee_id, work_date, check_in_at, check_out_at, status_code, status_label,
                               time_zone, device_type, device, location_label, note, created_at, updated_at)
SELECT 14, d, (d + '10:00'::time) AT TIME ZONE 'Asia/Dhaka',
       CASE WHEN d = '2026-11-09' THEN NULL ELSE (d + '16:00'::time) AT TIME ZONE 'Asia/Dhaka' END,
       CASE WHEN d = '2026-11-09' THEN 'M' ELSE 'W' END, CASE WHEN d = '2026-11-09' THEN 'Missing check-out' ELSE 'Worked' END,
       'Asia/Dhaka', 'Mobile', 'Seed', 'Remote', 'Payroll scenario seed', now(), now()
FROM unnest(ARRAY['2026-11-01', '2026-11-03', '2026-11-04', '2026-11-05', '2026-11-08', '2026-11-09']::date[]) d;
COMMIT;
SQL

echo "==> leave_db: scenario leave + cover balances"
psqlq "$HRMS" leave_db -v arpa="$ARPA" -v tanvir="$TANVIR" -v rumana="$RUMANA" <<'SQL'
BEGIN;
-- FORCE reset: undo payroll cover / reversal ledger rows and earlier scenario rows, restoring balances.
UPDATE leave_balance b SET balance_days = b.balance_days - x.delta, updated_at = now()
FROM (SELECT employee_id, leave_type_code, year, sum(delta_days) delta FROM leave_balance_ledger
      WHERE source_type IN ('COVER', 'COVER_REVERSAL') OR operation_key LIKE 'SEED:PAYROLL:%'
      GROUP BY 1, 2, 3) x
WHERE b.employee_id = x.employee_id AND b.leave_type_code = x.leave_type_code AND b.year = x.year;
DELETE FROM leave_balance_ledger WHERE source_type IN ('COVER', 'COVER_REVERSAL') OR operation_key LIKE 'SEED:PAYROLL:%';
DELETE FROM leave_operation WHERE operation_key LIKE 'FINALIZE:%' OR operation_key LIKE 'REOPEN:%';
DELETE FROM leave_approval WHERE application_id IN (SELECT id FROM leave_application WHERE reason LIKE 'Payroll scenario seed%');
DELETE FROM leave_application WHERE reason LIKE 'Payroll scenario seed%';

-- Balances for the scenarios (2026 leave year), written through the ledger so ledger = balance.
CREATE TEMP TABLE target(employee_id bigint, code text, days numeric) ON COMMIT DROP;
INSERT INTO target VALUES
  (:arpa, 'CASUAL', 1), (:arpa, 'ANNUAL', 0), (:arpa, 'SICK', 10),
  (:tanvir, 'CASUAL', 10), (:tanvir, 'ANNUAL', 14), (:tanvir, 'SICK', 10),
  (:rumana, 'CASUAL', 5), (:rumana, 'ANNUAL', 0), (:rumana, 'SICK', 10),
  (7, 'CASUAL', 5.5), (12, 'CASUAL', 0), (12, 'ANNUAL', 2), (10, 'CASUAL', 0), (10, 'ANNUAL', 0);
INSERT INTO leave_balance (employee_id, leave_type_code, year, balance_days, updated_at, version)
SELECT t.employee_id, t.code, 2026, 0, now(), 0 FROM target t
WHERE NOT EXISTS (SELECT 1 FROM leave_balance b WHERE b.employee_id = t.employee_id AND b.leave_type_code = t.code AND b.year = 2026);
INSERT INTO leave_balance_ledger (employee_id, leave_type_code, year, delta_days, reason, actor_employee_id, created_at,
                                  operation_key, source_type, source_id)
SELECT t.employee_id, t.code, 2026, t.days - b.balance_days, 'Payroll scenario seed — opening balance', 1, now(),
       'SEED:PAYROLL:BAL:' || t.employee_id || ':' || t.code, 'ADJUST', 'seed'
FROM target t JOIN leave_balance b ON b.employee_id = t.employee_id AND b.leave_type_code = t.code AND b.year = 2026
WHERE t.days <> b.balance_days;
UPDATE leave_balance b SET balance_days = t.days, updated_at = now()
FROM target t WHERE b.employee_id = t.employee_id AND b.leave_type_code = t.code AND b.year = 2026;

-- Approved leave: Tania UNPAID 8–9 Sep (2 days, always deducted); Imran CASUAL first half of 12 Oct.
INSERT INTO leave_application (employee_id, start_date, end_date, working_days_counted, status, reason, union_member,
       notice_days_given, notice_days_required, coverage_ok, needs_relief, coverage_snapshot, current_step, created_at,
       updated_at, leave_type_code, paid, balance_deducted, half_day_part, warnings)
VALUES
  (6, '2026-09-08', '2026-09-09', 2.0, 'APPROVED', 'Payroll scenario seed — family matter', false, 14, 0, true, false,
   '{}'::jsonb, 2, '2026-08-25T04:00:00Z', '2026-08-26T04:00:00Z', 'UNPAID', false, false, 'FULL', '{}'::jsonb),
  (7, '2026-10-12', '2026-10-12', 0.5, 'APPROVED', 'Payroll scenario seed — appointment', false, 7, 0, true, false,
   '{}'::jsonb, 2, '2026-10-05T04:00:00Z', '2026-10-06T04:00:00Z', 'CASUAL', true, true, 'FIRST_HALF', '{}'::jsonb);
INSERT INTO leave_approval (application_id, step_order, approver_role, status, actor_name, decided_at, approver_employee_id)
SELECT a.id, s.step, s.role, 'APPROVED', s.who, a.updated_at, s.emp
FROM leave_application a
CROSS JOIN (VALUES (1, 'MANAGER', 'Lina Ahmed', 4::bigint), (2, 'HR', 'Nadia Chowdhury', 2::bigint)) s(step, role, who, emp)
WHERE a.reason LIKE 'Payroll scenario seed%';
INSERT INTO leave_balance_ledger (employee_id, leave_type_code, year, delta_days, reason, actor_employee_id, created_at,
                                  operation_key, source_type, source_id)
SELECT 7, 'CASUAL', 2026, -0.5, 'Leave approved', 2, now(), 'SEED:PAYROLL:APPROVE:' || id, 'APPROVE', id::text
FROM leave_application WHERE employee_id = 7 AND reason LIKE 'Payroll scenario seed%';
UPDATE leave_balance SET balance_days = balance_days - 0.5, updated_at = now()
WHERE employee_id = 7 AND leave_type_code = 'CASUAL' AND year = 2026;
COMMIT;
SQL

echo ""
echo "Seeded. Next: node scripts/test-payroll-e2e.mjs"
