ALTER TABLE app_permission ADD COLUMN IF NOT EXISTS scoped BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE app_permission ADD COLUMN IF NOT EXISTS label VARCHAR(128);
ALTER TABLE app_permission ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 100;
ALTER TABLE app_permission ADD COLUMN IF NOT EXISTS matrix_visible BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE app_permission SET matrix_visible = FALSE, label = COALESCE(label, feature)
WHERE (feature, action) IN (
  ('employees','view'),('employees','create'),('employees','edit'),
  ('attendance','checkin'),('attendance','correct'),
  ('leave','view'),('leave','request'),('leave','approve'),('leave','manage'),
  ('roles','manage'),('roles','assign'),
  ('payroll','run'),
  ('notifications','view'),
  ('settings','manage'),
  ('org','view'),('org','manage')
);

INSERT INTO app_permission (feature, action, description, scoped, label, sort_order, matrix_visible)
SELECT v.feature, v.action, v.description, v.scoped, v.label, v.sort_order, TRUE
FROM (VALUES
  ('directory','view','View employee directory', TRUE, 'Employee directory', 10),
  ('profile_personal','view','View personal profile', TRUE, 'Employee profile: personal', 20),
  ('profile_personal','edit','Edit personal profile', TRUE, 'Employee profile: personal', 20),
  ('profile_employment','view','View employment profile', TRUE, 'Employee profile: employment', 30),
  ('profile_employment','create','Create employment records', TRUE, 'Employee profile: employment', 30),
  ('profile_employment','edit','Edit employment profile', TRUE, 'Employee profile: employment', 30),
  ('documents','view','View employee documents', TRUE, 'Employee documents', 40),
  ('documents','create','Upload employee documents', TRUE, 'Employee documents', 40),
  ('documents','delete','Delete employee documents', TRUE, 'Employee documents', 40),
  ('hierarchy','view','View org hierarchy', FALSE, 'Org hierarchy', 50),
  ('hierarchy','edit','Edit org hierarchy', FALSE, 'Org hierarchy', 50),
  ('leave_setup','view','View leave categories', FALSE, 'Leave categories and quotas', 60),
  ('leave_setup','create','Create leave categories', FALSE, 'Leave categories and quotas', 60),
  ('leave_setup','edit','Edit leave categories', FALSE, 'Leave categories and quotas', 60),
  ('leave_setup','delete','Delete leave categories', FALSE, 'Leave categories and quotas', 60),
  ('leave_requests','view','View leave requests', TRUE, 'Leave requests', 70),
  ('leave_requests','create','Create leave requests', TRUE, 'Leave requests', 70),
  ('leave_requests','approve','Approve leave requests', TRUE, 'Leave requests', 70),
  ('leave_balances','view','View leave balances', TRUE, 'Leave balances', 80),
  ('leave_balances','edit','Edit leave balances', TRUE, 'Leave balances', 80),
  ('attendance','view','View attendance records', TRUE, 'Attendance records', 90),
  ('attendance','edit','Edit attendance / corrections', TRUE, 'Attendance records', 90),
  ('attendance','export','Export attendance', TRUE, 'Attendance records', 90),
  ('attendance_settings','view','View attendance settings', FALSE, 'Attendance settings', 100),
  ('attendance_settings','edit','Edit attendance settings', FALSE, 'Attendance settings', 100),
  ('holidays','view','View holiday calendars', FALSE, 'Holiday calendars', 110),
  ('holidays','create','Create holidays', FALSE, 'Holiday calendars', 110),
  ('holidays','edit','Edit holidays', FALSE, 'Holiday calendars', 110),
  ('holidays','delete','Delete holidays', FALSE, 'Holiday calendars', 110),
  ('checklists','view','View onboarding/offboarding', TRUE, 'Onboarding and offboarding', 120),
  ('checklists','create','Create checklists', TRUE, 'Onboarding and offboarding', 120),
  ('checklists','edit','Edit checklists', TRUE, 'Onboarding and offboarding', 120),
  ('tasks','view','View tasks', TRUE, 'Tasks (until the project module)', 130),
  ('tasks','create','Create tasks', TRUE, 'Tasks (until the project module)', 130),
  ('tasks','edit','Edit tasks', TRUE, 'Tasks (until the project module)', 130),
  ('worklogs','view','View work logs', TRUE, 'Work logs', 140),
  ('worklogs','create','Create work logs', TRUE, 'Work logs', 140),
  ('worklogs','edit','Edit work logs', TRUE, 'Work logs', 140),
  ('salary','view','View salary and payslips', TRUE, 'Salary details and payslips', 150),
  ('salary','edit','Edit salary', TRUE, 'Salary details and payslips', 150),
  ('payroll','view','View payroll runs', FALSE, 'Payroll runs', 160),
  ('payroll','create','Create payroll runs', FALSE, 'Payroll runs', 160),
  ('payroll','approve','Approve payroll runs', FALSE, 'Payroll runs', 160),
  ('payroll','export','Export payroll', FALSE, 'Payroll runs', 160),
  ('emails','view','View monthly report emails', TRUE, 'Monthly report emails', 170),
  ('emails','create','Send monthly report emails', TRUE, 'Monthly report emails', 170),
  ('roles','view','View roles', FALSE, 'Roles and permissions', 180),
  ('roles','create','Create roles', FALSE, 'Roles and permissions', 180),
  ('roles','edit','Edit roles / assign people', FALSE, 'Roles and permissions', 180),
  ('roles','delete','Delete roles', FALSE, 'Roles and permissions', 180),
  ('reports','view','View reports', TRUE, 'Reports and HR dashboard', 190),
  ('reports','export','Export reports', TRUE, 'Reports and HR dashboard', 190),
  ('audit','view','View audit log', FALSE, 'Audit log', 200)
) AS v(feature, action, description, scoped, label, sort_order)
WHERE NOT EXISTS (
  SELECT 1 FROM app_permission p WHERE p.feature = v.feature AND p.action = v.action
);

UPDATE app_permission p SET
  scoped = v.scoped, label = v.label, sort_order = v.sort_order, matrix_visible = TRUE
FROM (VALUES
  ('directory','view', TRUE, 'Employee directory', 10),
  ('profile_personal','view', TRUE, 'Employee profile: personal', 20),
  ('profile_personal','edit', TRUE, 'Employee profile: personal', 20),
  ('profile_employment','view', TRUE, 'Employee profile: employment', 30),
  ('profile_employment','create', TRUE, 'Employee profile: employment', 30),
  ('profile_employment','edit', TRUE, 'Employee profile: employment', 30),
  ('documents','view', TRUE, 'Employee documents', 40),
  ('documents','create', TRUE, 'Employee documents', 40),
  ('documents','delete', TRUE, 'Employee documents', 40),
  ('hierarchy','view', FALSE, 'Org hierarchy', 50),
  ('hierarchy','edit', FALSE, 'Org hierarchy', 50),
  ('leave_setup','view', FALSE, 'Leave categories and quotas', 60),
  ('leave_setup','create', FALSE, 'Leave categories and quotas', 60),
  ('leave_setup','edit', FALSE, 'Leave categories and quotas', 60),
  ('leave_setup','delete', FALSE, 'Leave categories and quotas', 60),
  ('leave_requests','view', TRUE, 'Leave requests', 70),
  ('leave_requests','create', TRUE, 'Leave requests', 70),
  ('leave_requests','approve', TRUE, 'Leave requests', 70),
  ('leave_balances','view', TRUE, 'Leave balances', 80),
  ('leave_balances','edit', TRUE, 'Leave balances', 80),
  ('attendance','view', TRUE, 'Attendance records', 90),
  ('attendance','edit', TRUE, 'Attendance records', 90),
  ('attendance','export', TRUE, 'Attendance records', 90),
  ('attendance_settings','view', FALSE, 'Attendance settings', 100),
  ('attendance_settings','edit', FALSE, 'Attendance settings', 100),
  ('holidays','view', FALSE, 'Holiday calendars', 110),
  ('holidays','create', FALSE, 'Holiday calendars', 110),
  ('holidays','edit', FALSE, 'Holiday calendars', 110),
  ('holidays','delete', FALSE, 'Holiday calendars', 110),
  ('checklists','view', TRUE, 'Onboarding and offboarding', 120),
  ('checklists','create', TRUE, 'Onboarding and offboarding', 120),
  ('checklists','edit', TRUE, 'Onboarding and offboarding', 120),
  ('tasks','view', TRUE, 'Tasks (until the project module)', 130),
  ('tasks','create', TRUE, 'Tasks (until the project module)', 130),
  ('tasks','edit', TRUE, 'Tasks (until the project module)', 130),
  ('worklogs','view', TRUE, 'Work logs', 140),
  ('worklogs','create', TRUE, 'Work logs', 140),
  ('worklogs','edit', TRUE, 'Work logs', 140),
  ('salary','view', TRUE, 'Salary details and payslips', 150),
  ('salary','edit', TRUE, 'Salary details and payslips', 150),
  ('payroll','view', FALSE, 'Payroll runs', 160),
  ('payroll','create', FALSE, 'Payroll runs', 160),
  ('payroll','approve', FALSE, 'Payroll runs', 160),
  ('payroll','export', FALSE, 'Payroll runs', 160),
  ('emails','view', TRUE, 'Monthly report emails', 170),
  ('emails','create', TRUE, 'Monthly report emails', 170),
  ('roles','view', FALSE, 'Roles and permissions', 180),
  ('roles','create', FALSE, 'Roles and permissions', 180),
  ('roles','edit', FALSE, 'Roles and permissions', 180),
  ('roles','delete', FALSE, 'Roles and permissions', 180),
  ('reports','view', TRUE, 'Reports and HR dashboard', 190),
  ('reports','export', TRUE, 'Reports and HR dashboard', 190),
  ('audit','view', FALSE, 'Audit log', 200)
) AS v(feature, action, scoped, label, sort_order)
WHERE p.feature = v.feature AND p.action = v.action;

INSERT INTO app_role_permission (role_id, permission_id, data_scope)
SELECT r.id, p.id, 'ALL'
FROM app_role r CROSS JOIN app_permission p
WHERE UPPER(r.code) = 'SUPERADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM app_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO app_role_permission (role_id, permission_id, data_scope)
SELECT r.id, p.id, 'ALL'
FROM app_role r CROSS JOIN app_permission p
WHERE UPPER(r.code) = 'ADMIN'
  AND NOT (p.feature = 'roles' AND p.action IN ('manage','create','delete'))
  AND NOT EXISTS (
    SELECT 1 FROM app_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, liquibase)
SELECT 'hrms-011-permission-catalog-columns', 'platform', 'db/tenant-changelog/hrms/changes/011-permission-catalog.yaml', NOW(),
  COALESCE((SELECT MAX(orderexecuted) FROM databasechangelog), 0) + 1, 'EXECUTED', NULL, 'sql', 'manual'
WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'databasechangelog')
  AND NOT EXISTS (SELECT 1 FROM databasechangelog WHERE id = 'hrms-011-permission-catalog-columns');

SELECT column_name FROM information_schema.columns
WHERE table_name = 'app_permission' AND column_name IN ('label','scoped','sort_order','matrix_visible')
ORDER BY 1;
SELECT COUNT(*) AS matrix_perms FROM app_permission WHERE matrix_visible;
