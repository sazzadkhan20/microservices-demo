#!/usr/bin/env python3
"""Generate people/leave/timework Liquibase masters for DB-per-service split."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "tenant-registry/src/main/resources/db/tenant-changelog"

OUTBOX = """
        - createTable:
            tableName: message_outbox
            columns:
              - column: { name: id, type: BIGSERIAL, constraints: { primaryKey: true } }
              - column: { name: event_id, type: VARCHAR(64), constraints: { nullable: false, unique: true } }
              - column: { name: event_type, type: VARCHAR(128), constraints: { nullable: false } }
              - column: { name: routing_key, type: VARCHAR(128), constraints: { nullable: false } }
              - column: { name: aggregate_id, type: VARCHAR(64), constraints: { nullable: false } }
              - column: { name: aggregate_version, type: BIGINT, constraints: { nullable: false } }
              - column: { name: correlation_id, type: VARCHAR(64), constraints: { nullable: false } }
              - column: { name: tenant_id, type: VARCHAR(100), constraints: { nullable: false } }
              - column: { name: payload_json, type: JSONB, constraints: { nullable: false } }
              - column: { name: status, type: VARCHAR(24), defaultValue: PENDING, constraints: { nullable: false } }
              - column: { name: attempts, type: INT, defaultValueNumeric: 0, constraints: { nullable: false } }
              - column: { name: last_error, type: TEXT }
              - column: { name: created_at, type: TIMESTAMPTZ, constraints: { nullable: false } }
              - column: { name: published_at, type: TIMESTAMPTZ }
        - createIndex:
            tableName: message_outbox
            indexName: idx_outbox_status_created
            columns:
              - column: { name: status }
              - column: { name: created_at }
        - createTable:
            tableName: message_inbox
            columns:
              - column: { name: id, type: BIGSERIAL, constraints: { primaryKey: true } }
              - column: { name: event_id, type: VARCHAR(64), constraints: { nullable: false, unique: true } }
              - column: { name: event_type, type: VARCHAR(128), constraints: { nullable: false } }
              - column: { name: consumer_name, type: VARCHAR(128), constraints: { nullable: false } }
              - column: { name: payload_json, type: JSONB, constraints: { nullable: false } }
              - column: { name: processed_at, type: TIMESTAMPTZ, constraints: { nullable: false } }
              - column: { name: result_json, type: JSONB }
"""

# Projection of People.employee — same table name so existing JPA Employee entity works.
# IDs are assigned by People (NOT identity/serial here).
EMP_REF = """
        - createTable:
            tableName: employee
            columns:
              - column: { name: id, type: BIGINT, constraints: { primaryKey: true } }
              - column: { name: employee_code, type: VARCHAR(64), constraints: { nullable: false } }
              - column: { name: full_name, type: VARCHAR(256), constraints: { nullable: false } }
              - column: { name: email, type: VARCHAR(256), constraints: { nullable: false } }
              - column: { name: status, type: VARCHAR(32), defaultValue: Active }
              - column: { name: active, type: BOOLEAN, defaultValueBoolean: true, constraints: { nullable: false } }
              - column: { name: department_id, type: BIGINT }
              - column: { name: designation_id, type: BIGINT }
              - column: { name: manager_id, type: BIGINT }
              - column: { name: location_id, type: BIGINT }
              - column: { name: team_id, type: BIGINT }
              - column: { name: employment_type, type: VARCHAR(32) }
              - column: { name: start_date, type: DATE }
              - column: { name: work_pattern, type: VARCHAR(32) }
              - column: { name: preferred_name, type: VARCHAR(128) }
              - column: { name: job_title, type: VARCHAR(128) }
              - column: { name: personal_email, type: VARCHAR(256) }
              - column: { name: phone, type: VARCHAR(64) }
              - column: { name: pay_currency, type: VARCHAR(8) }
              - column: { name: probation_end, type: DATE }
              - column: { name: contract_end, type: DATE }
              - column: { name: base_salary, type: NUMERIC(19,4), defaultValueNumeric: 0, constraints: { nullable: false } }
              - column: { name: hourly_rate, type: NUMERIC(19,4), defaultValueNumeric: 0, constraints: { nullable: false } }
              - column: { name: custom_fields, type: JSONB }
              - column: { name: created_at, type: TIMESTAMPTZ, constraints: { nullable: false } }
              - column: { name: updated_at, type: TIMESTAMPTZ, constraints: { nullable: false } }
        - addUniqueConstraint:
            tableName: employee
            columnNames: employee_code
            constraintName: uq_leave_tw_employee_code
"""

# For leave/timework we ALSO create a synonym view-compatible employee table name by using
# employee_ref as the physical table and mapping JPA @Table(name="employee_ref").
# Additionally create work_schedule_ref for leave day calculations.

WORK_SCHED_REF = """
        - createTable:
            tableName: work_schedule
            columns:
              - column: { name: id, type: BIGSERIAL, constraints: { primaryKey: true } }
              - column: { name: scope_type, type: VARCHAR(16), constraints: { nullable: false } }
              - column: { name: scope_id, type: BIGINT, constraints: { nullable: false } }
              - column: { name: mode, type: VARCHAR(16), defaultValue: FIXED, constraints: { nullable: false } }
              - column: { name: working_days, type: JSONB, constraints: { nullable: false } }
              - column: { name: start_time, type: VARCHAR(8) }
              - column: { name: end_time, type: VARCHAR(8) }
              - column: { name: grace_minutes, type: INT, defaultValueNumeric: 15 }
              - column: { name: half_day_hours, type: NUMERIC(6,2) }
              - column: { name: checkout_cutoff, type: VARCHAR(8), defaultValue: "23:59" }
              - column: { name: weekly_hours, type: NUMERIC(6,2) }
"""


def write(svc: str, body: str):
    d = ROOT / svc / "changes"
    d.mkdir(parents=True, exist_ok=True)
    (d / f"001-{svc}-core.yaml").write_text(body, encoding="utf-8")
    (ROOT / svc / "db.changelog-master.yaml").write_text(
        f"databaseChangeLog:\n  - include:\n      file: db/tenant-changelog/{svc}/changes/001-{svc}-core.yaml\n",
        encoding="utf-8",
    )
    print("wrote", svc)


people = f"""databaseChangeLog:
  - changeSet:
      id: people-001-core
      author: platform
      changes:
        - createTable:
            tableName: tenant_field_definition
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: field_name, type: VARCHAR(64), constraints: {{ nullable: false, unique: true }} }}
              - column: {{ name: storage_type, type: VARCHAR(32), constraints: {{ nullable: false }} }}
              - column: {{ name: jsonb_path, type: VARCHAR(128) }}
              - column: {{ name: extension_table, type: VARCHAR(128) }}
              - column: {{ name: extension_column, type: VARCHAR(128) }}
              - column: {{ name: filterable, type: BOOLEAN, defaultValueBoolean: true, constraints: {{ nullable: false }} }}
              - column: {{ name: indexed_flag, type: BOOLEAN, defaultValueBoolean: false, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: employee
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: employee_code, type: VARCHAR(64), constraints: {{ nullable: false, unique: true }} }}
              - column: {{ name: full_name, type: VARCHAR(256), constraints: {{ nullable: false }} }}
              - column: {{ name: email, type: VARCHAR(256), constraints: {{ nullable: false }} }}
              - column: {{ name: base_salary, type: NUMERIC(19,4), constraints: {{ nullable: false }} }}
              - column: {{ name: hourly_rate, type: NUMERIC(19,4), constraints: {{ nullable: false }} }}
              - column: {{ name: custom_fields, type: JSONB, constraints: {{ nullable: false }} }}
              - column: {{ name: active, type: BOOLEAN, defaultValueBoolean: true, constraints: {{ nullable: false }} }}
              - column: {{ name: created_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
              - column: {{ name: updated_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
              - column: {{ name: department_id, type: BIGINT }}
              - column: {{ name: designation_id, type: BIGINT }}
              - column: {{ name: manager_id, type: BIGINT }}
              - column: {{ name: preferred_name, type: VARCHAR(128) }}
              - column: {{ name: job_title, type: VARCHAR(128) }}
              - column: {{ name: location_id, type: BIGINT }}
              - column: {{ name: team_id, type: BIGINT }}
              - column: {{ name: employment_type, type: VARCHAR(32) }}
              - column: {{ name: start_date, type: DATE }}
              - column: {{ name: probation_end, type: DATE }}
              - column: {{ name: contract_end, type: DATE }}
              - column: {{ name: pay_currency, type: VARCHAR(8) }}
              - column: {{ name: personal_email, type: VARCHAR(256) }}
              - column: {{ name: status, type: VARCHAR(32), defaultValue: Active }}
              - column: {{ name: phone, type: VARCHAR(64) }}
              - column: {{ name: work_pattern, type: VARCHAR(32), defaultValue: FIXED }}
        - createTable:
            tableName: ext_indexed_attribute
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: attr_key, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: attr_value, type: VARCHAR(512) }}
        - createTable:
            tableName: department
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: code, type: VARCHAR(64), constraints: {{ nullable: false, unique: true }} }}
              - column: {{ name: name, type: VARCHAR(256), constraints: {{ nullable: false }} }}
              - column: {{ name: active, type: BOOLEAN, defaultValueBoolean: true, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: designation
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: code, type: VARCHAR(64), constraints: {{ nullable: false, unique: true }} }}
              - column: {{ name: name, type: VARCHAR(256), constraints: {{ nullable: false }} }}
              - column: {{ name: active, type: BOOLEAN, defaultValueBoolean: true, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: location
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: code, type: VARCHAR(64), constraints: {{ nullable: false, unique: true }} }}
              - column: {{ name: name, type: VARCHAR(256), constraints: {{ nullable: false }} }}
              - column: {{ name: active, type: BOOLEAN, defaultValueBoolean: true, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: team
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: code, type: VARCHAR(64), constraints: {{ nullable: false, unique: true }} }}
              - column: {{ name: name, type: VARCHAR(256), constraints: {{ nullable: false }} }}
              - column: {{ name: lead_employee_id, type: BIGINT }}
              - column: {{ name: max_on_leave, type: INT }}
              - column: {{ name: active, type: BOOLEAN, defaultValueBoolean: true, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: reporting_line_history
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: manager_id, type: BIGINT }}
              - column: {{ name: effective_from, type: DATE, constraints: {{ nullable: false }} }}
              - column: {{ name: effective_to, type: DATE }}
              - column: {{ name: created_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: employment_field_history
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: field_name, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: old_value, type: TEXT }}
              - column: {{ name: new_value, type: TEXT }}
              - column: {{ name: effective_date, type: DATE, constraints: {{ nullable: false }} }}
              - column: {{ name: created_by, type: BIGINT }}
              - column: {{ name: created_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: tenant_settings
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: settings_json, type: JSONB, constraints: {{ nullable: false }} }}
              - column: {{ name: updated_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: audit_log
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: actor_employee_id, type: BIGINT }}
              - column: {{ name: action, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: entity_type, type: VARCHAR(64) }}
              - column: {{ name: entity_id, type: VARCHAR(64) }}
              - column: {{ name: detail_json, type: JSONB }}
              - column: {{ name: created_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: app_role
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: code, type: VARCHAR(64), constraints: {{ nullable: false, unique: true }} }}
              - column: {{ name: name, type: VARCHAR(128), constraints: {{ nullable: false }} }}
              - column: {{ name: description, type: VARCHAR(512) }}
              - column: {{ name: system_role, type: BOOLEAN, defaultValueBoolean: false, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: app_permission
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: feature, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: action, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: description, type: VARCHAR(256) }}
        - addUniqueConstraint:
            tableName: app_permission
            columnNames: feature, action
            constraintName: uq_app_permission_feature_action
        - createTable:
            tableName: app_role_permission
            columns:
              - column: {{ name: role_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: permission_id, type: BIGINT, constraints: {{ nullable: false }} }}
        - addPrimaryKey:
            tableName: app_role_permission
            columnNames: role_id, permission_id
            constraintName: pk_app_role_permission
        - createTable:
            tableName: app_user_role
            columns:
              - column: {{ name: employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: role_id, type: BIGINT, constraints: {{ nullable: false }} }}
        - addPrimaryKey:
            tableName: app_user_role
            columnNames: employee_id, role_id
            constraintName: pk_app_user_role
{OUTBOX}
"""

# Leave: use employee table name matching JPA Employee entity for transitional compatibility,
# populated as projection (ids assigned by people, not identity). Use BIGINT id without BIGSERIAL.
leave = f"""databaseChangeLog:
  - changeSet:
      id: leave-001-core
      author: platform
      changes:
{EMP_REF}
{WORK_SCHED_REF}
        - createTable:
            tableName: leave_policy
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: annual_days, type: INT, constraints: {{ nullable: false }} }}
              - column: {{ name: work_week_days, type: INT, constraints: {{ nullable: false }} }}
              - column: {{ name: updated_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
              - column: {{ name: policy_json, type: JSONB }}
        - createTable:
            tableName: leave_type
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: code, type: VARCHAR(64), constraints: {{ nullable: false, unique: true }} }}
              - column: {{ name: name, type: VARCHAR(128), constraints: {{ nullable: false }} }}
              - column: {{ name: paid, type: BOOLEAN, defaultValueBoolean: true, constraints: {{ nullable: false }} }}
              - column: {{ name: active, type: BOOLEAN, defaultValueBoolean: true, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: leave_category
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: code, type: VARCHAR(64), constraints: {{ nullable: false, unique: true }} }}
              - column: {{ name: name, type: VARCHAR(128), constraints: {{ nullable: false }} }}
              - column: {{ name: leave_type_code, type: VARCHAR(64) }}
              - column: {{ name: quota_days, type: NUMERIC(10,2) }}
              - column: {{ name: half_day_allowed, type: BOOLEAN, defaultValueBoolean: false }}
              - column: {{ name: attachment_required, type: BOOLEAN, defaultValueBoolean: false }}
              - column: {{ name: max_per_request, type: NUMERIC(10,2) }}
              - column: {{ name: negative_cap, type: NUMERIC(10,2) }}
              - column: {{ name: applies_to_json, type: JSONB }}
              - column: {{ name: active, type: BOOLEAN, defaultValueBoolean: true, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: leave_application
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: leave_type_code, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: category_code, type: VARCHAR(64) }}
              - column: {{ name: start_date, type: DATE, constraints: {{ nullable: false }} }}
              - column: {{ name: end_date, type: DATE, constraints: {{ nullable: false }} }}
              - column: {{ name: days, type: NUMERIC(10,2), constraints: {{ nullable: false }} }}
              - column: {{ name: half_day, type: BOOLEAN, defaultValueBoolean: false }}
              - column: {{ name: status, type: VARCHAR(32), constraints: {{ nullable: false }} }}
              - column: {{ name: reason, type: TEXT }}
              - column: {{ name: store_id, type: BIGINT }}
              - column: {{ name: shift_pattern, type: VARCHAR(64) }}
              - column: {{ name: needs_relief, type: BOOLEAN, defaultValueBoolean: false }}
              - column: {{ name: relief_employee_id, type: BIGINT }}
              - column: {{ name: attachment_file_id, type: BIGINT }}
              - column: {{ name: created_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
              - column: {{ name: updated_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: leave_approval
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: application_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: step_no, type: INT, constraints: {{ nullable: false }} }}
              - column: {{ name: status, type: VARCHAR(32), constraints: {{ nullable: false }} }}
              - column: {{ name: approver_employee_id, type: BIGINT }}
              - column: {{ name: expected_approver_id, type: BIGINT }}
              - column: {{ name: comment, type: TEXT }}
              - column: {{ name: acted_at, type: TIMESTAMPTZ }}
        - createTable:
            tableName: leave_approval_delegate
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: owner_employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: delegate_employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: from_date, type: DATE, constraints: {{ nullable: false }} }}
              - column: {{ name: to_date, type: DATE, constraints: {{ nullable: false }} }}
              - column: {{ name: active, type: BOOLEAN, defaultValueBoolean: true, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: leave_balance
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: leave_type_code, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: year, type: INT, constraints: {{ nullable: false }} }}
              - column: {{ name: balance_days, type: NUMERIC(10,2), constraints: {{ nullable: false }} }}
              - column: {{ name: version, type: BIGINT, defaultValueNumeric: 0, constraints: {{ nullable: false }} }}
              - column: {{ name: updated_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: leave_balance_ledger
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: leave_type_code, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: year, type: INT, constraints: {{ nullable: false }} }}
              - column: {{ name: delta_days, type: NUMERIC(10,2), constraints: {{ nullable: false }} }}
              - column: {{ name: reason, type: VARCHAR(256) }}
              - column: {{ name: created_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: leave_blackout
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: name, type: VARCHAR(128) }}
              - column: {{ name: start_date, type: DATE, constraints: {{ nullable: false }} }}
              - column: {{ name: end_date, type: DATE, constraints: {{ nullable: false }} }}
              - column: {{ name: location_id, type: BIGINT }}
        - createTable:
            tableName: leave_operation
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: operation_key, type: VARCHAR(160), constraints: {{ nullable: false, unique: true }} }}
              - column: {{ name: event_id, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: period_label, type: VARCHAR(32), constraints: {{ nullable: false }} }}
              - column: {{ name: reverse_flag, type: BOOLEAN, defaultValueBoolean: false, constraints: {{ nullable: false }} }}
              - column: {{ name: status, type: VARCHAR(32), constraints: {{ nullable: false }} }}
              - column: {{ name: result_json, type: JSONB }}
              - column: {{ name: created_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
              - column: {{ name: completed_at, type: TIMESTAMPTZ }}
        - createTable:
            tableName: store_shift_coverage
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: store_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: shift_pattern, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: min_headcount, type: INT, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: holiday
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: name, type: VARCHAR(128), constraints: {{ nullable: false }} }}
              - column: {{ name: holiday_date, type: DATE, constraints: {{ nullable: false }} }}
              - column: {{ name: location_id, type: BIGINT }}
        - createTable:
            tableName: team
            columns:
              - column: {{ name: id, type: BIGINT, constraints: {{ primaryKey: true }} }}
              - column: {{ name: code, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: name, type: VARCHAR(256), constraints: {{ nullable: false }} }}
              - column: {{ name: lead_employee_id, type: BIGINT }}
              - column: {{ name: max_on_leave, type: INT }}
              - column: {{ name: active, type: BOOLEAN, defaultValueBoolean: true, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: location
            columns:
              - column: {{ name: id, type: BIGINT, constraints: {{ primaryKey: true }} }}
              - column: {{ name: code, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: name, type: VARCHAR(256), constraints: {{ nullable: false }} }}
              - column: {{ name: active, type: BOOLEAN, defaultValueBoolean: true, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: tenant_settings
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: settings_json, type: JSONB, constraints: {{ nullable: false }} }}
              - column: {{ name: updated_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: audit_log
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: actor_employee_id, type: BIGINT }}
              - column: {{ name: action, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: entity_type, type: VARCHAR(64) }}
              - column: {{ name: entity_id, type: VARCHAR(64) }}
              - column: {{ name: detail_json, type: JSONB }}
              - column: {{ name: created_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: app_role
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: code, type: VARCHAR(64), constraints: {{ nullable: false, unique: true }} }}
              - column: {{ name: name, type: VARCHAR(128), constraints: {{ nullable: false }} }}
              - column: {{ name: description, type: VARCHAR(512) }}
              - column: {{ name: system_role, type: BOOLEAN, defaultValueBoolean: false, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: app_permission
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: feature, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: action, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: description, type: VARCHAR(256) }}
        - createTable:
            tableName: app_role_permission
            columns:
              - column: {{ name: role_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: permission_id, type: BIGINT, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: app_user_role
            columns:
              - column: {{ name: employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: role_id, type: BIGINT, constraints: {{ nullable: false }} }}
{OUTBOX}
"""

timework = f"""databaseChangeLog:
  - changeSet:
      id: timework-001-core
      author: platform
      changes:
{EMP_REF}
        - createTable:
            tableName: work_schedule
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: scope_type, type: VARCHAR(16), constraints: {{ nullable: false }} }}
              - column: {{ name: scope_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: mode, type: VARCHAR(16), defaultValue: FIXED, constraints: {{ nullable: false }} }}
              - column: {{ name: working_days, type: JSONB, constraints: {{ nullable: false }} }}
              - column: {{ name: start_time, type: VARCHAR(8) }}
              - column: {{ name: end_time, type: VARCHAR(8) }}
              - column: {{ name: grace_minutes, type: INT, defaultValueNumeric: 15 }}
              - column: {{ name: half_day_hours, type: NUMERIC(6,2) }}
              - column: {{ name: checkout_cutoff, type: VARCHAR(8), defaultValue: "23:59" }}
              - column: {{ name: weekly_hours, type: NUMERIC(6,2) }}
        - createTable:
            tableName: attendance_record
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: work_date, type: DATE, constraints: {{ nullable: false }} }}
              - column: {{ name: check_in_at, type: TIMESTAMPTZ }}
              - column: {{ name: check_out_at, type: TIMESTAMPTZ }}
              - column: {{ name: status, type: VARCHAR(32) }}
              - column: {{ name: late_minutes, type: INT }}
              - column: {{ name: notes, type: TEXT }}
        - createTable:
            tableName: attendance_correction
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: attendance_id, type: BIGINT }}
              - column: {{ name: employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: work_date, type: DATE, constraints: {{ nullable: false }} }}
              - column: {{ name: requested_check_in, type: TIMESTAMPTZ }}
              - column: {{ name: requested_check_out, type: TIMESTAMPTZ }}
              - column: {{ name: original_check_in, type: TIMESTAMPTZ }}
              - column: {{ name: original_check_out, type: TIMESTAMPTZ }}
              - column: {{ name: status, type: VARCHAR(32), constraints: {{ nullable: false }} }}
              - column: {{ name: reason, type: TEXT }}
              - column: {{ name: decided_by, type: BIGINT }}
              - column: {{ name: created_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: holiday
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: name, type: VARCHAR(128), constraints: {{ nullable: false }} }}
              - column: {{ name: holiday_date, type: DATE, constraints: {{ nullable: false }} }}
              - column: {{ name: location_id, type: BIGINT }}
        - createTable:
            tableName: work_task
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: title, type: VARCHAR(256), constraints: {{ nullable: false }} }}
              - column: {{ name: description, type: TEXT }}
              - column: {{ name: status, type: VARCHAR(32), constraints: {{ nullable: false }} }}
              - column: {{ name: created_by, type: BIGINT }}
              - column: {{ name: due_date, type: DATE }}
              - column: {{ name: created_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: work_task_assignee
            columns:
              - column: {{ name: task_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: work_log
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: work_date, type: DATE, constraints: {{ nullable: false }} }}
              - column: {{ name: minutes, type: INT, constraints: {{ nullable: false }} }}
              - column: {{ name: task_id, type: BIGINT }}
              - column: {{ name: note, type: TEXT }}
              - column: {{ name: created_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: location
            columns:
              - column: {{ name: id, type: BIGINT, constraints: {{ primaryKey: true }} }}
              - column: {{ name: code, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: name, type: VARCHAR(256), constraints: {{ nullable: false }} }}
              - column: {{ name: active, type: BOOLEAN, defaultValueBoolean: true, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: tenant_settings
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: settings_json, type: JSONB, constraints: {{ nullable: false }} }}
              - column: {{ name: updated_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: audit_log
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: actor_employee_id, type: BIGINT }}
              - column: {{ name: action, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: entity_type, type: VARCHAR(64) }}
              - column: {{ name: entity_id, type: VARCHAR(64) }}
              - column: {{ name: detail_json, type: JSONB }}
              - column: {{ name: created_at, type: TIMESTAMPTZ, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: app_role
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: code, type: VARCHAR(64), constraints: {{ nullable: false, unique: true }} }}
              - column: {{ name: name, type: VARCHAR(128), constraints: {{ nullable: false }} }}
              - column: {{ name: description, type: VARCHAR(512) }}
              - column: {{ name: system_role, type: BOOLEAN, defaultValueBoolean: false, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: app_permission
            columns:
              - column: {{ name: id, type: BIGSERIAL, constraints: {{ primaryKey: true }} }}
              - column: {{ name: feature, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: action, type: VARCHAR(64), constraints: {{ nullable: false }} }}
              - column: {{ name: description, type: VARCHAR(256) }}
        - createTable:
            tableName: app_role_permission
            columns:
              - column: {{ name: role_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: permission_id, type: BIGINT, constraints: {{ nullable: false }} }}
        - createTable:
            tableName: app_user_role
            columns:
              - column: {{ name: employee_id, type: BIGINT, constraints: {{ nullable: false }} }}
              - column: {{ name: role_id, type: BIGINT, constraints: {{ nullable: false }} }}
{OUTBOX}
"""

write("people", people)
write("leave", leave)
write("timework", timework)
print("done")
