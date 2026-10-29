-- Manual upgrade for existing hrms_techshop_retail (also covered by Liquibase 002 + seeder after Registry restart).

ALTER TABLE leave_policy ADD COLUMN IF NOT EXISTS working_days JSONB;
ALTER TABLE leave_policy ADD COLUMN IF NOT EXISTS union_notice_days INT NOT NULL DEFAULT 7;
ALTER TABLE leave_policy ADD COLUMN IF NOT EXISTS non_union_notice_days INT NOT NULL DEFAULT 3;
ALTER TABLE leave_policy ADD COLUMN IF NOT EXISTS require_coverage_check BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE leave_policy ADD COLUMN IF NOT EXISTS approval_chain JSONB;

CREATE TABLE IF NOT EXISTS store_shift_coverage (
  id BIGSERIAL PRIMARY KEY,
  store_id VARCHAR(64) NOT NULL,
  shift_pattern VARCHAR(64) NOT NULL,
  min_staff INT NOT NULL,
  CONSTRAINT uk_store_shift_coverage UNIQUE (store_id, shift_pattern)
);

CREATE TABLE IF NOT EXISTS leave_blackout (
  id BIGSERIAL PRIMARY KEY,
  store_id VARCHAR(64),
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  reason VARCHAR(256) NOT NULL
);

CREATE TABLE IF NOT EXISTS leave_application (
  id BIGSERIAL PRIMARY KEY,
  employee_id BIGINT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  working_days_counted NUMERIC(10,2) NOT NULL,
  status VARCHAR(32) NOT NULL,
  reason VARCHAR(512),
  union_member BOOLEAN NOT NULL DEFAULT FALSE,
  notice_days_given INT NOT NULL,
  notice_days_required INT NOT NULL,
  coverage_ok BOOLEAN NOT NULL,
  needs_relief BOOLEAN NOT NULL DEFAULT FALSE,
  relief_employee_id BIGINT,
  store_id VARCHAR(64),
  shift_pattern VARCHAR(64),
  coverage_snapshot JSONB NOT NULL,
  current_step INT NOT NULL DEFAULT 0,
  reject_reason VARCHAR(512),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS leave_approval (
  id BIGSERIAL PRIMARY KEY,
  application_id BIGINT NOT NULL,
  step_order INT NOT NULL,
  approver_role VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  actor_name VARCHAR(128),
  comment_text VARCHAR(512),
  decided_at TIMESTAMPTZ
);

DELETE FROM leave_approval;
DELETE FROM leave_application;
DELETE FROM leave_blackout;
DELETE FROM store_shift_coverage;
DELETE FROM leave_policy;
DELETE FROM ext_indexed_attribute;
DELETE FROM employee;
DELETE FROM tenant_field_definition;

INSERT INTO leave_policy (
  annual_days, work_week_days, working_days,
  union_notice_days, non_union_notice_days,
  require_coverage_check, approval_chain, updated_at
) VALUES (
  15, 6,
  '["SAT","SUN","MON","TUE","WED","THU"]'::jsonb,
  7, 3, TRUE,
  '[{"step":1,"role":"STORE_MANAGER"},{"step":2,"role":"REGIONAL_HR"}]'::jsonb,
  NOW()
);

INSERT INTO store_shift_coverage (store_id, shift_pattern, min_staff) VALUES
  ('S-DHK-01', 'MORNING', 2),
  ('S-DHK-01', 'EVENING', 2);

INSERT INTO leave_blackout (store_id, start_date, end_date, reason) VALUES
  ('S-DHK-01', CURRENT_DATE + 60, CURRENT_DATE + 66, 'Annual clearance sale');

INSERT INTO tenant_field_definition
  (field_name, storage_type, jsonb_path, extension_table, extension_column, filterable, indexed_flag)
VALUES
  ('store_id', 'JSONB', 'custom_fields.store_id', NULL, NULL, TRUE, FALSE),
  ('shift_pattern', 'JSONB', 'custom_fields.shift_pattern', NULL, NULL, TRUE, FALSE),
  ('union_member_id', 'EXTENSION_TABLE', NULL, 'ext_indexed_attribute', 'attr_value', TRUE, TRUE);

INSERT INTO employee
  (employee_code, full_name, email, base_salary, hourly_rate, custom_fields, active, created_at, updated_at)
VALUES
  ('R-1001', 'Nadia Rahman', 'nadia@techshop.local', 45000, 22.5,
   '{"store_id":"S-DHK-01","shift_pattern":"MORNING"}'::jsonb, TRUE, NOW(), NOW()),
  ('R-1002', 'Rahim Uddin', 'rahim@techshop.local', 42000, 20,
   '{"store_id":"S-DHK-01","shift_pattern":"MORNING"}'::jsonb, TRUE, NOW(), NOW()),
  ('R-1003', 'Karim Ali', 'karim@techshop.local', 40000, 19,
   '{"store_id":"S-DHK-01","shift_pattern":"MORNING"}'::jsonb, TRUE, NOW(), NOW());

INSERT INTO ext_indexed_attribute (employee_id, attr_key, attr_value)
SELECT id, 'union_member_id', 'UNION-7788' FROM employee WHERE employee_code = 'R-1001';
