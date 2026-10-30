-- FR-EMP-07 — employment field change history (safe to re-run).
CREATE TABLE IF NOT EXISTS employment_field_history (
  id          BIGSERIAL PRIMARY KEY,
  employee_id BIGINT NOT NULL,
  field_name  VARCHAR(64) NOT NULL,
  old_value   TEXT,
  new_value   TEXT,
  changed_by  BIGINT,
  changed_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_emp_field_hist_employee ON employment_field_history (employee_id);
CREATE INDEX IF NOT EXISTS idx_emp_field_hist_changed_at ON employment_field_history (changed_at);

INSERT INTO databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, liquibase)
SELECT 'hrms-012-employment-field-history', 'platform', 'db/tenant-changelog/hrms/changes/012-employment-history.yaml', NOW(),
  COALESCE((SELECT MAX(orderexecuted) FROM databasechangelog), 0) + 1, 'EXECUTED', NULL, 'createTable+createIndex', 'manual'
WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'databasechangelog')
  AND NOT EXISTS (SELECT 1 FROM databasechangelog WHERE id = 'hrms-012-employment-field-history');
