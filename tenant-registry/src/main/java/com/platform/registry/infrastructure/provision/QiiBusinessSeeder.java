package com.platform.registry.infrastructure.provision;

import com.platform.registry.config.PlatformProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Seeds Qii Creative product tenant — Asman Super Admin, BD+AU locations, RBAC matrix,
 * rich cast, Sun–Thu schedules, leave categories, opening balances.
 * Config-only: no tenant if-else in engines.
 */
@Component
public class QiiBusinessSeeder {

    private static final Logger log = LoggerFactory.getLogger(QiiBusinessSeeder.class);

    public void seedQiiCreative(String hrmsJdbcUrl, String payrollJdbcUrl,
                                PlatformProperties.DbTarget hrms, PlatformProperties.DbTarget payroll) {
        seedHrms(hrmsJdbcUrl, hrms.getUsername(), hrms.getPassword());
        if (payrollJdbcUrl != null) {
            seedPayroll(payrollJdbcUrl, payroll.getUsername(), payroll.getPassword());
        }
        log.info("Seeded Qii Creative into {} and {}", hrmsJdbcUrl, payrollJdbcUrl);
    }

    public void reseedHrms(String hrmsJdbcUrl, PlatformProperties.DbTarget hrms) {
        seedHrms(hrmsJdbcUrl, hrms.getUsername(), hrms.getPassword());
    }

    private void seedHrms(String jdbcUrl, String user, String password) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                clearQiiTables(st);
                PracticeBusinessSeeder.clearLeaveAndOrg(st);

                seedRbac(st);
                seedOrg(st);
                seedEmployees(st);
                seedUserRoles(st);
                seedSchedules(st);
                seedTasks(st);
                seedLeave(st);
                seedHolidays(st);
                seedAttendanceAndLogs(st);
                seedSettings(st);
            }
            conn.commit();
            PracticeBusinessSeeder.logSeededUsers(conn, "Qii Creative");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed Qii Creative HRMS: " + e.getMessage(), e);
        }
    }

    private static void clearQiiTables(Statement st) throws Exception {
        st.executeUpdate("DELETE FROM app_user_role");
        st.executeUpdate("DELETE FROM app_role_permission");
        st.executeUpdate("DELETE FROM app_permission");
        st.executeUpdate("DELETE FROM app_role");
        st.executeUpdate("DELETE FROM work_log");
        st.executeUpdate("DELETE FROM work_task_assignee");
        st.executeUpdate("DELETE FROM work_task");
        st.executeUpdate("DELETE FROM attendance_record");
        st.executeUpdate("DELETE FROM work_schedule");
        st.executeUpdate("DELETE FROM holiday");
        st.executeUpdate("DELETE FROM leave_category");
        st.executeUpdate("DELETE FROM team");
        st.executeUpdate("DELETE FROM location");
        st.executeUpdate("DELETE FROM audit_log");
        PracticeBusinessSeeder.restartIdentity(st,
                "app_role", "app_permission", "app_role_permission", "app_user_role",
                "location", "team", "holiday", "leave_category", "work_schedule",
                "work_task", "work_log", "attendance_record", "audit_log");
    }

    private static void seedRbac(Statement st) throws Exception {
        st.executeUpdate("""
                INSERT INTO app_role (code, name, description, system_role, active) VALUES
                  ('SUPERADMIN', 'Super Admin', 'Full platform access', TRUE, TRUE),
                  ('ADMIN', 'Admin', 'Company admin', TRUE, TRUE),
                  ('HR', 'HR', 'Human resources', TRUE, TRUE),
                  ('MANAGER', 'Manager', 'Team manager', TRUE, TRUE),
                  ('EMPLOYEE', 'Employee', 'Standard employee', TRUE, TRUE)
                """);

        st.executeUpdate("""
                INSERT INTO app_permission (feature, action, description) VALUES
                  ('employees', 'view', 'View employees'),
                  ('employees', 'create', 'Create employees'),
                  ('employees', 'edit', 'Edit employees'),
                  ('attendance', 'view', 'View attendance'),
                  ('attendance', 'checkin', 'Self check-in/out'),
                  ('attendance', 'correct', 'Correct attendance'),
                  ('leave', 'view', 'View leave'),
                  ('leave', 'request', 'Request leave'),
                  ('leave', 'approve', 'Approve leave'),
                  ('leave', 'manage', 'Manage leave balances'),
                  ('roles', 'view', 'View roles'),
                  ('roles', 'manage', 'Manage roles'),
                  ('payroll', 'view', 'View payroll'),
                  ('payroll', 'run', 'Run payroll'),
                  ('reports', 'view', 'View reports'),
                  ('reports', 'export', 'Export reports'),
                  ('notifications', 'view', 'View notifications'),
                  ('settings', 'manage', 'Manage settings'),
                  ('org', 'view', 'View org'),
                  ('org', 'manage', 'Manage org')
                """);

        // SUPERADMIN + ADMIN → all ALL
        st.executeUpdate("""
                INSERT INTO app_role_permission (role_id, permission_id, data_scope)
                SELECT r.id, p.id, 'ALL'
                FROM app_role r CROSS JOIN app_permission p
                WHERE r.code IN ('SUPERADMIN', 'ADMIN')
                """);

        // HR
        st.executeUpdate("""
                INSERT INTO app_role_permission (role_id, permission_id, data_scope)
                SELECT r.id, p.id,
                  CASE WHEN p.feature IN ('payroll','settings','roles') THEN 'ALL' ELSE 'ALL' END
                FROM app_role r CROSS JOIN app_permission p
                WHERE r.code = 'HR'
                  AND NOT (p.feature = 'roles' AND p.action = 'manage')
                  AND p.feature NOT IN ('settings')
                """);

        // MANAGER
        st.executeUpdate("""
                INSERT INTO app_role_permission (role_id, permission_id, data_scope)
                SELECT r.id, p.id,
                  CASE
                    WHEN p.feature IN ('employees','attendance','leave') AND p.action IN ('view','approve','correct') THEN 'TEAM'
                    WHEN p.feature = 'leave' AND p.action = 'request' THEN 'OWN'
                    WHEN p.feature = 'attendance' AND p.action = 'checkin' THEN 'OWN'
                    WHEN p.feature IN ('notifications','reports','org') AND p.action = 'view' THEN 'TEAM'
                    ELSE 'OWN'
                  END
                FROM app_role r CROSS JOIN app_permission p
                WHERE r.code = 'MANAGER'
                  AND (
                    (p.feature = 'employees' AND p.action = 'view')
                    OR (p.feature = 'attendance' AND p.action IN ('view','checkin','correct'))
                    OR (p.feature = 'leave' AND p.action IN ('view','request','approve'))
                    OR (p.feature = 'notifications' AND p.action = 'view')
                    OR (p.feature = 'reports' AND p.action = 'view')
                    OR (p.feature = 'org' AND p.action = 'view')
                  )
                """);

        // EMPLOYEE
        st.executeUpdate("""
                INSERT INTO app_role_permission (role_id, permission_id, data_scope)
                SELECT r.id, p.id, 'OWN'
                FROM app_role r CROSS JOIN app_permission p
                WHERE r.code = 'EMPLOYEE'
                  AND (
                    (p.feature = 'attendance' AND p.action IN ('view','checkin'))
                    OR (p.feature = 'leave' AND p.action IN ('view','request'))
                    OR (p.feature = 'notifications' AND p.action = 'view')
                    OR (p.feature = 'employees' AND p.action = 'view')
                    OR (p.feature = 'payroll' AND p.action = 'view')
                  )
                """);
    }

    private static void seedOrg(Statement st) throws Exception {
        st.executeUpdate("""
                INSERT INTO location (code, name, country, time_zone, default_currency) VALUES
                  ('BD-DHK', 'Dhaka HQ', 'Bangladesh', 'Asia/Dhaka', 'BDT'),
                  ('AU-SYD', 'Sydney Studio', 'Australia', 'Australia/Sydney', 'AUD')
                """);

        st.executeUpdate("""
                INSERT INTO department (code, name) VALUES
                  ('EXEC', 'Executive'),
                  ('CREATIVE', 'Creative'),
                  ('PROD', 'Production'),
                  ('CLIENT', 'Client Services'),
                  ('HR', 'People & Culture'),
                  ('FIN', 'Finance')
                """);

        st.executeUpdate("""
                INSERT INTO designation (code, name, approver_role) VALUES
                  ('SUPER_ADMIN', 'Super Admin', 'SUPERADMIN'),
                  ('ADMIN', 'Administrator', 'ADMIN'),
                  ('HR_LEAD', 'HR Lead', 'HR'),
                  ('CREATIVE_DIR', 'Creative Director', 'MANAGER'),
                  ('PROD_MGR', 'Production Manager', 'MANAGER'),
                  ('DESIGNER', 'Designer', NULL),
                  ('PRODUCER', 'Producer', NULL),
                  ('ACCOUNT_MGR', 'Account Manager', NULL),
                  ('FINANCE', 'Finance Officer', NULL)
                """);

        st.executeUpdate("""
                INSERT INTO team (code, name, department_id, lead_employee_id, max_on_leave, clash_action) VALUES
                  ('DESIGN', 'Design Team', (SELECT id FROM department WHERE code='CREATIVE'), NULL, 1, 'ALERT'),
                  ('MOTION', 'Motion Team', (SELECT id FROM department WHERE code='CREATIVE'), NULL, 1, 'ALERT'),
                  ('PROD', 'Production Pod', (SELECT id FROM department WHERE code='PROD'), NULL, 1, 'ALERT'),
                  ('ACCOUNTS', 'Client Accounts', (SELECT id FROM department WHERE code='CLIENT'), NULL, 1, 'ALERT')
                """);
    }

    private static void seedEmployees(Statement st) throws Exception {
        // Asman = Super Admin (always)
        st.executeUpdate("""
                INSERT INTO employee
                  (employee_code, full_name, email, department_id, designation_id, manager_id,
                   preferred_name, job_title, location_id, team_id, employment_type, start_date,
                   pay_currency, status, phone, work_pattern,
                   base_salary, hourly_rate, custom_fields, active, created_at, updated_at)
                VALUES
                  ('QII-0001', 'Asman Rahman', 'asman@qiicreative.local',
                   (SELECT id FROM department WHERE code='EXEC'),
                   (SELECT id FROM designation WHERE code='SUPER_ADMIN'),
                   NULL, 'Asman', 'Founder & Super Admin',
                   (SELECT id FROM location WHERE code='BD-DHK'), NULL, 'FULL_TIME', '2019-01-15',
                   'BDT', 'Active', '+8801700000001', 'FIXED',
                   150000.0000, 0.0000,
                   '{"gender":"M","country":"BD"}'::jsonb, TRUE, NOW(), NOW()),

                  ('QII-0002', 'Nadia Chowdhury', 'nadia@qiicreative.local',
                   (SELECT id FROM department WHERE code='HR'),
                   (SELECT id FROM designation WHERE code='HR_LEAD'),
                   NULL, 'Nadia', 'Head of People',
                   (SELECT id FROM location WHERE code='BD-DHK'), NULL, 'FULL_TIME', '2020-03-01',
                   'BDT', 'Active', '+8801700000002', 'FIXED',
                   95000.0000, 0.0000,
                   '{"gender":"F","country":"BD"}'::jsonb, TRUE, NOW(), NOW()),

                  ('QII-0003', 'Rafiul Islam', 'rafiul@qiicreative.local',
                   (SELECT id FROM department WHERE code='EXEC'),
                   (SELECT id FROM designation WHERE code='ADMIN'),
                   NULL, 'Rafi', 'Operations Admin',
                   (SELECT id FROM location WHERE code='BD-DHK'), NULL, 'FULL_TIME', '2020-06-01',
                   'BDT', 'Active', '+8801700000003', 'FIXED',
                   90000.0000, 0.0000,
                   '{"gender":"M","country":"BD"}'::jsonb, TRUE, NOW(), NOW()),

                  ('QII-0010', 'Lina Ahmed', 'lina@qiicreative.local',
                   (SELECT id FROM department WHERE code='CREATIVE'),
                   (SELECT id FROM designation WHERE code='CREATIVE_DIR'),
                   NULL, 'Lina', 'Creative Director',
                   (SELECT id FROM location WHERE code='BD-DHK'),
                   (SELECT id FROM team WHERE code='DESIGN'), 'FULL_TIME', '2021-02-10',
                   'BDT', 'Active', '+8801700000010', 'FIXED',
                   110000.0000, 0.0000,
                   '{"gender":"F","country":"BD"}'::jsonb, TRUE, NOW(), NOW()),

                  ('QII-0011', 'Marcus Blake', 'marcus@qiicreative.local',
                   (SELECT id FROM department WHERE code='PROD'),
                   (SELECT id FROM designation WHERE code='PROD_MGR'),
                   NULL, 'Marcus', 'Production Manager',
                   (SELECT id FROM location WHERE code='AU-SYD'),
                   (SELECT id FROM team WHERE code='PROD'), 'FULL_TIME', '2021-08-01',
                   'AUD', 'Active', '+61400000011', 'FIXED',
                   95000.0000, 0.0000,
                   '{"gender":"M","country":"AU"}'::jsonb, TRUE, NOW(), NOW()),

                  ('QII-0020', 'Tania Sultana', 'tania@qiicreative.local',
                   (SELECT id FROM department WHERE code='CREATIVE'),
                   (SELECT id FROM designation WHERE code='DESIGNER'),
                   NULL, 'Tania', 'Senior Designer',
                   (SELECT id FROM location WHERE code='BD-DHK'),
                   (SELECT id FROM team WHERE code='DESIGN'), 'FULL_TIME', '2022-01-15',
                   'BDT', 'Active', '+8801700000020', 'FIXED',
                   65000.0000, 0.0000,
                   '{"gender":"F","country":"BD"}'::jsonb, TRUE, NOW(), NOW()),

                  ('QII-0021', 'Imran Hossain', 'imran@qiicreative.local',
                   (SELECT id FROM department WHERE code='CREATIVE'),
                   (SELECT id FROM designation WHERE code='DESIGNER'),
                   NULL, 'Imran', 'Motion Designer',
                   (SELECT id FROM location WHERE code='BD-DHK'),
                   (SELECT id FROM team WHERE code='MOTION'), 'FULL_TIME', '2022-05-01',
                   'BDT', 'Active', '+8801700000021', 'FIXED',
                   60000.0000, 0.0000,
                   '{"gender":"M","country":"BD"}'::jsonb, TRUE, NOW(), NOW()),

                  ('QII-0022', 'Sophie Nguyen', 'sophie@qiicreative.local',
                   (SELECT id FROM department WHERE code='PROD'),
                   (SELECT id FROM designation WHERE code='PRODUCER'),
                   NULL, 'Sophie', 'Producer',
                   (SELECT id FROM location WHERE code='AU-SYD'),
                   (SELECT id FROM team WHERE code='PROD'), 'FULL_TIME', '2022-09-12',
                   'AUD', 'Active', '+61400000022', 'FIXED',
                   72000.0000, 0.0000,
                   '{"gender":"F","country":"AU"}'::jsonb, TRUE, NOW(), NOW()),

                  ('QII-0023', 'Karim Ali', 'karim@qiicreative.local',
                   (SELECT id FROM department WHERE code='CLIENT'),
                   (SELECT id FROM designation WHERE code='ACCOUNT_MGR'),
                   NULL, 'Karim', 'Account Manager',
                   (SELECT id FROM location WHERE code='BD-DHK'),
                   (SELECT id FROM team WHERE code='ACCOUNTS'), 'FULL_TIME', '2023-01-08',
                   'BDT', 'Active', '+8801700000023', 'FIXED',
                   58000.0000, 0.0000,
                   '{"gender":"M","country":"BD"}'::jsonb, TRUE, NOW(), NOW()),

                  ('QII-0024', 'Priya Das', 'priya@qiicreative.local',
                   (SELECT id FROM department WHERE code='CREATIVE'),
                   (SELECT id FROM designation WHERE code='DESIGNER'),
                   NULL, 'Priya', 'Junior Designer',
                   (SELECT id FROM location WHERE code='BD-DHK'),
                   (SELECT id FROM team WHERE code='DESIGN'), 'FULL_TIME', '2024-03-01',
                   'BDT', 'Active', '+8801700000024', 'FIXED',
                   45000.0000, 0.0000,
                   '{"gender":"F","country":"BD"}'::jsonb, TRUE, NOW(), NOW()),

                  ('QII-0025', 'Owen Fraser', 'owen@qiicreative.local',
                   (SELECT id FROM department WHERE code='PROD'),
                   (SELECT id FROM designation WHERE code='PRODUCER'),
                   NULL, 'Owen', 'Junior Producer',
                   (SELECT id FROM location WHERE code='AU-SYD'),
                   (SELECT id FROM team WHERE code='PROD'), 'HOURLY', '2024-06-15',
                   'AUD', 'Active', '+61400000025', 'HOURLY',
                   0.0000, 45.0000,
                   '{"gender":"M","country":"AU"}'::jsonb, TRUE, NOW(), NOW()),

                  ('QII-0030', 'Farhana Begum', 'farhana@qiicreative.local',
                   (SELECT id FROM department WHERE code='FIN'),
                   (SELECT id FROM designation WHERE code='FINANCE'),
                   NULL, 'Farhana', 'Finance Officer',
                   (SELECT id FROM location WHERE code='BD-DHK'), NULL, 'FULL_TIME', '2021-11-01',
                   'BDT', 'Active', '+8801700000030', 'FIXED',
                   70000.0000, 0.0000,
                   '{"gender":"F","country":"BD"}'::jsonb, TRUE, NOW(), NOW())
                """);

        // Reporting lines: staff → managers → Asman; HR/Admin → Asman
        st.executeUpdate("""
                UPDATE employee SET manager_id = (SELECT id FROM employee WHERE employee_code='QII-0001')
                WHERE employee_code IN ('QII-0002','QII-0003','QII-0010','QII-0011','QII-0030')
                """);
        st.executeUpdate("""
                UPDATE employee SET manager_id = (SELECT id FROM employee WHERE employee_code='QII-0010')
                WHERE employee_code IN ('QII-0020','QII-0021','QII-0024')
                """);
        st.executeUpdate("""
                UPDATE employee SET manager_id = (SELECT id FROM employee WHERE employee_code='QII-0011')
                WHERE employee_code IN ('QII-0022','QII-0025')
                """);
        st.executeUpdate("""
                UPDATE employee SET manager_id = (SELECT id FROM employee WHERE employee_code='QII-0010')
                WHERE employee_code = 'QII-0023'
                """);

        st.executeUpdate("""
                UPDATE team SET lead_employee_id = (SELECT id FROM employee WHERE employee_code='QII-0010')
                WHERE code IN ('DESIGN','MOTION','ACCOUNTS')
                """);
        st.executeUpdate("""
                UPDATE team SET lead_employee_id = (SELECT id FROM employee WHERE employee_code='QII-0011')
                WHERE code = 'PROD'
                """);
    }

    private static void seedUserRoles(Statement st) throws Exception {
        st.executeUpdate("""
                INSERT INTO app_user_role (employee_id, role_id)
                SELECT e.id, r.id FROM employee e
                JOIN (VALUES
                  ('QII-0001','SUPERADMIN'),
                  ('QII-0002','HR'),
                  ('QII-0003','ADMIN'),
                  ('QII-0010','MANAGER'),
                  ('QII-0011','MANAGER'),
                  ('QII-0020','EMPLOYEE'),
                  ('QII-0021','EMPLOYEE'),
                  ('QII-0022','EMPLOYEE'),
                  ('QII-0023','EMPLOYEE'),
                  ('QII-0024','EMPLOYEE'),
                  ('QII-0025','EMPLOYEE'),
                  ('QII-0030','EMPLOYEE')
                ) AS m(code, role) ON e.employee_code = m.code
                JOIN app_role r ON r.code = m.role
                """);
    }

    private static void seedSchedules(Statement st) throws Exception {
        // BD Sun–Thu 10:00–18:00 grace 15
        st.executeUpdate("""
                INSERT INTO work_schedule
                  (scope_type, scope_id, mode, working_days, start_time, end_time, grace_minutes, half_day_hours, checkout_cutoff)
                VALUES
                  ('LOCATION', (SELECT id FROM location WHERE code='BD-DHK'), 'FIXED',
                   '["SUN","MON","TUE","WED","THU"]'::jsonb, '10:00', '18:00', 15, 4, '23:59'),
                  ('LOCATION', (SELECT id FROM location WHERE code='AU-SYD'), 'FIXED',
                   '["MON","TUE","WED","THU","FRI"]'::jsonb, '09:00', '17:00', 15, 4, '23:59')
                """);
    }

    private static void seedLeave(Statement st) throws Exception {
        st.executeUpdate("""
                INSERT INTO leave_policy (
                  annual_days, work_week_days, working_days,
                  union_notice_days, non_union_notice_days,
                  require_coverage_check, approval_chain,
                  notice_mode, default_notice_days, coverage_mode,
                  location_field, shift_field, require_relief_on_coverage_fail,
                  default_leave_type_code, updated_at
                ) VALUES (
                  14, 5,
                  '["SUN","MON","TUE","WED","THU"]'::jsonb,
                  0, 0, FALSE,
                  '[{"step":1,"role":"MANAGER"},{"step":2,"role":"HR"}]'::jsonb,
                  'NONE', 0, 'NONE',
                  NULL, NULL, FALSE,
                  'CASUAL', NOW()
                )
                """);

        st.executeUpdate("""
                INSERT INTO leave_type
                  (code, name, paid, requires_balance, allow_when_zero_balance,
                   allow_when_coverage_fail, deduct_balance, active, sort_order)
                VALUES
                  ('CASUAL', 'Casual Leave', TRUE, TRUE, FALSE, TRUE, TRUE, TRUE, 1),
                  ('ANNUAL', 'Annual Leave', TRUE, TRUE, FALSE, TRUE, TRUE, TRUE, 2),
                  ('SICK', 'Sick Leave', TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, 3),
                  ('UNPAID', 'Unpaid Leave', FALSE, FALSE, TRUE, TRUE, FALSE, TRUE, 4)
                """);

        st.executeUpdate("""
                INSERT INTO leave_category
                  (code, name, quota_days, paid, half_day_allowed, attachment_mode, notice_days,
                   max_per_request, negative_cap, approval_chain, active)
                VALUES
                  ('CASUAL', 'Casual Leave', 10, TRUE, TRUE, 'NEVER', 0, 3, 0,
                   '[{"step":1,"role":"MANAGER"}]'::jsonb, TRUE),
                  ('ANNUAL', 'Annual Leave', 14, TRUE, TRUE, 'NEVER', 7, 14, 0,
                   '[{"step":1,"role":"MANAGER"},{"step":2,"role":"HR"}]'::jsonb, TRUE),
                  ('SICK', 'Sick Leave', 10, TRUE, TRUE, 'AFTER_DAYS', 0, 5, -2,
                   '[{"step":1,"role":"MANAGER"}]'::jsonb, TRUE),
                  ('UNPAID', 'Unpaid Leave', 0, FALSE, TRUE, 'NEVER', 3, 30, 0,
                   '[{"step":1,"role":"MANAGER"},{"step":2,"role":"HR"}]'::jsonb, TRUE)
                """);

        st.executeUpdate("""
                INSERT INTO leave_balance (employee_id, leave_type_code, year, balance_days, updated_at)
                SELECT e.id, t.code, EXTRACT(YEAR FROM CURRENT_DATE)::int, t.bal, NOW()
                FROM employee e
                CROSS JOIN (VALUES
                  ('CASUAL', 10.00),
                  ('ANNUAL', 14.00),
                  ('SICK', 10.00)
                ) AS t(code, bal)
                WHERE e.employee_code <> 'QII-0001'
                """);

        // Asman also gets balances
        st.executeUpdate("""
                INSERT INTO leave_balance (employee_id, leave_type_code, year, balance_days, updated_at)
                SELECT e.id, t.code, EXTRACT(YEAR FROM CURRENT_DATE)::int, t.bal, NOW()
                FROM employee e
                CROSS JOIN (VALUES
                  ('CASUAL', 10.00),
                  ('ANNUAL', 20.00),
                  ('SICK', 10.00)
                ) AS t(code, bal)
                WHERE e.employee_code = 'QII-0001'
                """);
    }

    private static void seedHolidays(Statement st) throws Exception {
        st.executeUpdate("""
                INSERT INTO holiday (location_id, name, start_date, end_date, holiday_type, optional_flag)
                VALUES
                  ((SELECT id FROM location WHERE code='BD-DHK'), 'Pohela Boishakh',
                   DATE_TRUNC('year', CURRENT_DATE)::date + 103,
                   DATE_TRUNC('year', CURRENT_DATE)::date + 103, 'PUBLIC', FALSE),
                  ((SELECT id FROM location WHERE code='BD-DHK'), 'Victory Day',
                   MAKE_DATE(EXTRACT(YEAR FROM CURRENT_DATE)::int, 12, 16),
                   MAKE_DATE(EXTRACT(YEAR FROM CURRENT_DATE)::int, 12, 16), 'PUBLIC', FALSE),
                  ((SELECT id FROM location WHERE code='AU-SYD'), 'Australia Day',
                   MAKE_DATE(EXTRACT(YEAR FROM CURRENT_DATE)::int, 1, 26),
                   MAKE_DATE(EXTRACT(YEAR FROM CURRENT_DATE)::int, 1, 26), 'PUBLIC', FALSE),
                  ((SELECT id FROM location WHERE code='AU-SYD'), 'ANZAC Day',
                   MAKE_DATE(EXTRACT(YEAR FROM CURRENT_DATE)::int, 4, 25),
                   MAKE_DATE(EXTRACT(YEAR FROM CURRENT_DATE)::int, 4, 25), 'PUBLIC', FALSE)
                """);
    }

    private static void seedTasks(Statement st) throws Exception {
        st.executeUpdate("""
                INSERT INTO work_task (title, client_name, status, created_by, created_at)
                VALUES
                  ('Brand identity refresh', 'Northwind Media', 'OPEN',
                   (SELECT id FROM employee WHERE employee_code='QII-0001'), NOW()),
                  ('Website landing page', 'Qii Internal', 'OPEN',
                   (SELECT id FROM employee WHERE employee_code='QII-0001'), NOW()),
                  ('Social campaign pack', 'Blue Ocean Co', 'OPEN',
                   (SELECT id FROM employee WHERE employee_code='QII-0010'), NOW())
                """);
    }

    /** Demo attendance + work logs so FE tables/filters show real Qii business data. */
    private static void seedAttendanceAndLogs(Statement st) throws Exception {
        st.executeUpdate("""
                INSERT INTO attendance_record
                  (employee_id, employee_code_snapshot, work_date, check_in_at, check_out_at,
                   status_code, status_label, ip_address, device, device_type, location_label,
                   time_zone, latitude, longitude, note, created_at, updated_at)
                SELECT e.id, e.employee_code, CURRENT_DATE - offs.d,
                       (CURRENT_DATE - offs.d) + TIME '10:05',
                       (CURRENT_DATE - offs.d) + TIME '18:10',
                       CASE WHEN offs.late THEN 'L' ELSE 'P' END,
                       CASE WHEN offs.late THEN 'Late' ELSE 'Present' END,
                       '103.120.' || (10 + offs.d) || '.' || (20 + offs.d),
                       'Mozilla/5.0', 'Desktop',
                       CASE WHEN e.pay_currency = 'AUD' THEN 'Darwin office' ELSE 'Dhaka office' END,
                       CASE WHEN e.pay_currency = 'AUD' THEN 'Australia/Darwin' ELSE 'Asia/Dhaka' END,
                       CASE WHEN e.pay_currency = 'AUD' THEN -12.4634000 ELSE 23.8103000 END,
                       CASE WHEN e.pay_currency = 'AUD' THEN 130.8456000 ELSE 90.4125000 END,
                       NULL, NOW(), NOW()
                FROM employee e
                CROSS JOIN (VALUES (0, FALSE), (1, TRUE), (2, FALSE), (3, FALSE)) AS offs(d, late)
                WHERE e.active = TRUE
                  AND e.employee_code NOT IN ('QII-0001')
                """);

        // Open punch for Asman yesterday (late-night checkout demo) — only if missing
        st.executeUpdate("""
                INSERT INTO attendance_record
                  (employee_id, employee_code_snapshot, work_date, check_in_at, check_out_at,
                   status_code, status_label, ip_address, device, device_type, location_label,
                   time_zone, note, created_at, updated_at)
                SELECT e.id, e.employee_code, CURRENT_DATE - 1,
                       (CURRENT_DATE - 1) + TIME '10:12', NULL,
                       'P', 'Present', '103.120.1.1', 'Mozilla/5.0', 'Desktop',
                       'Dhaka office', 'Asia/Dhaka', 'Still working late', NOW(), NOW()
                FROM employee e
                WHERE e.employee_code = 'QII-0001'
                  AND NOT EXISTS (
                    SELECT 1 FROM attendance_record a
                    WHERE a.employee_id = e.id AND a.work_date = CURRENT_DATE - 1
                  )
                """);

        st.executeUpdate("""
                INSERT INTO work_log (employee_id, work_date, task_id, minutes, note, created_at)
                SELECT e.id, CURRENT_DATE - offs.d, t.id, 120 + (offs.d * 15),
                       'Seeded work log', NOW()
                FROM employee e
                CROSS JOIN (VALUES (0), (1), (2)) AS offs(d)
                CROSS JOIN LATERAL (SELECT id FROM work_task ORDER BY id LIMIT 1) t
                WHERE e.active = TRUE AND e.employee_code LIKE 'QII-002%'
                """);

        st.executeUpdate("""
                INSERT INTO leave_application
                  (employee_id, leave_type_code, paid, balance_deducted, start_date, end_date,
                   working_days_counted, status, reason, union_member, notice_days_given,
                   notice_days_required, coverage_ok, needs_relief, coverage_snapshot,
                   current_step, created_at, updated_at)
                SELECT e.id, 'CASUAL', TRUE, FALSE, CURRENT_DATE + 5, CURRENT_DATE + 5,
                       1.00, 'PENDING_APPROVAL', 'Family errand', FALSE, 0, 0, TRUE, FALSE,
                       '{}'::jsonb, 1, NOW(), NOW()
                FROM employee e WHERE e.employee_code = 'QII-0020'
                """);

        st.executeUpdate("""
                INSERT INTO leave_application
                  (employee_id, leave_type_code, paid, balance_deducted, start_date, end_date,
                   working_days_counted, status, reason, union_member, notice_days_given,
                   notice_days_required, coverage_ok, needs_relief, coverage_snapshot,
                   current_step, created_at, updated_at)
                SELECT e.id, 'ANNUAL', TRUE, TRUE, CURRENT_DATE + 20, CURRENT_DATE + 22,
                       3.00, 'APPROVED', 'Short break', FALSE, 14, 7, TRUE, FALSE,
                       '{}'::jsonb, 2, NOW(), NOW()
                FROM employee e WHERE e.employee_code = 'QII-0022'
                """);
    }

    private static void seedSettings(Statement st) throws Exception {
        st.executeUpdate("""
                INSERT INTO tenant_settings (settings, updated_at)
                VALUES (
                  '{
                    "businessPack":"qii_creative",
                    "employeeCodePattern":"^QII-\\\\d{4}$",
                    "defaultTimeZone":"Asia/Dhaka",
                    "workLogRule":"BLOCK",
                    "workLogMinPercent":80,
                    "workLogEditDaysBack":7,
                    "teamLeaveMaxOnLeave":1,
                    "teamLeaveClashAction":"ALERT",
                    "payrollAbsenceCoverOrder":["CASUAL","ANNUAL"],
                    "notificationsPollSeconds":30,
                    "monthlyReportDay":1
                  }'::jsonb,
                  NOW()
                )
                """);

        st.executeUpdate("""
                INSERT INTO tenant_field_definition
                  (field_name, storage_type, jsonb_path, extension_table, extension_column,
                   filterable, indexed_flag, required_flag, unique_active, validation_regex)
                VALUES
                  ('gender', 'JSONB', 'custom_fields.gender', NULL, NULL, TRUE, FALSE, FALSE, FALSE, NULL),
                  ('country', 'JSONB', 'custom_fields.country', NULL, NULL, TRUE, FALSE, FALSE, FALSE, NULL)
                """);
    }

    private void seedPayroll(String jdbcUrl, String user, String password) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE payroll_config
                       SET overtime_multiplier = ?,
                           weekend_multiplier = ?,
                           deduction_rules = ?::jsonb,
                           updated_at = ?
                    """)) {
                ps.setBigDecimal(1, new BigDecimal("1.5000"));
                ps.setBigDecimal(2, new BigDecimal("2.0000"));
                ps.setString(3, """
                        [{"type":"CONFIG","key":"absenceCoverOrder","value":["CASUAL","ANNUAL"]},
                         {"type":"CONFIG","key":"unpaidAlwaysDeduct","value":true},
                         {"type":"CONFIG","key":"dayDivisor","value":"WORKING_DAYS"}]
                        """);
                ps.setObject(4, OffsetDateTime.now(ZoneOffset.UTC));
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new IllegalStateException("payroll_config row missing after Liquibase");
                }
            }
            conn.commit();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed Qii Payroll: " + e.getMessage(), e);
        }
    }
}
