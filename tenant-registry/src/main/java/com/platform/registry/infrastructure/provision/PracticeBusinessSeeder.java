package com.platform.registry.infrastructure.provision;

import com.platform.registry.config.PlatformProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Seeds TechShop retail practice config (Sat–Thu, store coverage, union notice, typed leave).
 */
@Component
public class PracticeBusinessSeeder {

    private static final Logger log = LoggerFactory.getLogger(PracticeBusinessSeeder.class);
    public static final String PACK = "techshop-practice";

    private final BusinessPackApplier packApplier;

    public PracticeBusinessSeeder(BusinessPackApplier packApplier) {
        this.packApplier = packApplier;
    }

    public void seedRetailPractice(String hrmsJdbcUrl, String payrollJdbcUrl,
                                   PlatformProperties.DbTarget hrms, PlatformProperties.DbTarget payroll) {
        seedHrms(hrmsJdbcUrl, hrms.getUsername(), hrms.getPassword());
        seedPayroll(payrollJdbcUrl, payroll.getUsername(), payroll.getPassword());
        log.info("Seeded TechShop retail practice into {} and {}", hrmsJdbcUrl, payrollJdbcUrl);
    }

    public void reseedHrmsRetailLeavePack(String hrmsJdbcUrl, PlatformProperties.DbTarget hrms) {
        seedHrms(hrmsJdbcUrl, hrms.getUsername(), hrms.getPassword());
    }

    private void seedHrms(String jdbcUrl, String user, String password) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                clearLeaveAndOrg(st);

                // leave_policy + settings (+ empty fieldDefs) from pack
                packApplier.applyPolicySurface(conn, PACK);

                st.executeUpdate("""
                        INSERT INTO leave_type
                          (code, name, paid, requires_balance, allow_when_zero_balance,
                           allow_when_coverage_fail, deduct_balance, active, sort_order)
                        VALUES
                          ('ANNUAL', 'Annual Leave', TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 1)
                        """);

                st.executeUpdate("""
                        INSERT INTO store_shift_coverage (store_id, shift_pattern, min_staff) VALUES
                          ('S-DHK-01', 'MORNING', 2),
                          ('S-DHK-01', 'EVENING', 2)
                        """);

                st.executeUpdate("""
                        INSERT INTO leave_blackout (store_id, start_date, end_date, reason) VALUES
                          ('S-DHK-01', CURRENT_DATE + 60, CURRENT_DATE + 66, 'Annual clearance sale')
                        """);

                st.executeUpdate("""
                        INSERT INTO tenant_field_definition
                          (field_name, storage_type, jsonb_path, extension_table, extension_column,
                           filterable, indexed_flag, required_flag, unique_active, validation_regex)
                        VALUES
                          ('store_id', 'JSONB', 'custom_fields.store_id', NULL, NULL, TRUE, FALSE, FALSE, FALSE, NULL),
                          ('shift_pattern', 'JSONB', 'custom_fields.shift_pattern', NULL, NULL, TRUE, FALSE, FALSE, FALSE, NULL),
                          ('union_member_id', 'EXTENSION_TABLE', NULL, 'ext_indexed_attribute', 'attr_value', TRUE, TRUE, FALSE, FALSE, NULL)
                        """);

                st.executeUpdate("""
                        INSERT INTO department (code, name) VALUES
                          ('STORE_OPS', 'Store Operations'),
                          ('SALES', 'Sales'),
                          ('HR', 'Human Resources')
                        """);

                st.executeUpdate("""
                        INSERT INTO designation (code, name, approver_role) VALUES
                          ('SALES_ASSOCIATE', 'Sales Associate', NULL),
                          ('CASHIER', 'Cashier', NULL),
                          ('STORE_MANAGER', 'Store Manager', 'STORE_MANAGER'),
                          ('REGIONAL_HR', 'Regional HR', 'REGIONAL_HR')
                        """);

                st.executeUpdate("""
                        INSERT INTO employee
                          (employee_code, full_name, email, department_id, designation_id, manager_id,
                           base_salary, hourly_rate, custom_fields, active, created_at, updated_at)
                        VALUES
                          ('R-2001', 'Farhan Ahmed', 'farhan.hr@techshop.local',
                           (SELECT id FROM department WHERE code='HR'),
                           (SELECT id FROM designation WHERE code='REGIONAL_HR'),
                           NULL, 90000.0000, 0.0000, '{}'::jsonb, TRUE, NOW(), NOW()),
                          ('R-2002', 'Amina Begum', 'amina.mgr@techshop.local',
                           (SELECT id FROM department WHERE code='STORE_OPS'),
                           (SELECT id FROM designation WHERE code='STORE_MANAGER'),
                           NULL, 75000.0000, 0.0000,
                           '{"store_id":"S-DHK-01","shift_pattern":"MORNING"}'::jsonb, TRUE, NOW(), NOW()),
                          ('R-1001', 'Nadia Rahman', 'nadia@techshop.local',
                           (SELECT id FROM department WHERE code='SALES'),
                           (SELECT id FROM designation WHERE code='SALES_ASSOCIATE'),
                           NULL, 45000.0000, 22.5000,
                           '{"store_id":"S-DHK-01","shift_pattern":"MORNING"}'::jsonb, TRUE, NOW(), NOW()),
                          ('R-1002', 'Rahim Uddin', 'rahim@techshop.local',
                           (SELECT id FROM department WHERE code='SALES'),
                           (SELECT id FROM designation WHERE code='SALES_ASSOCIATE'),
                           NULL, 42000.0000, 20.0000,
                           '{"store_id":"S-DHK-01","shift_pattern":"MORNING"}'::jsonb, TRUE, NOW(), NOW()),
                          ('R-1003', 'Karim Ali', 'karim@techshop.local',
                           (SELECT id FROM department WHERE code='STORE_OPS'),
                           (SELECT id FROM designation WHERE code='CASHIER'),
                           NULL, 40000.0000, 19.0000,
                           '{"store_id":"S-DHK-01","shift_pattern":"MORNING"}'::jsonb, TRUE, NOW(), NOW()),
                          ('R-1004', 'Sara Khan', 'sara@techshop.local',
                           (SELECT id FROM department WHERE code='SALES'),
                           (SELECT id FROM designation WHERE code='SALES_ASSOCIATE'),
                           NULL, 41000.0000, 19.5000,
                           '{"store_id":"S-DHK-01","shift_pattern":"EVENING"}'::jsonb, TRUE, NOW(), NOW()),
                          ('R-1005', 'Imran Hossain', 'imran@techshop.local',
                           (SELECT id FROM department WHERE code='STORE_OPS'),
                           (SELECT id FROM designation WHERE code='CASHIER'),
                           NULL, 39500.0000, 18.5000,
                           '{"store_id":"S-DHK-01","shift_pattern":"EVENING"}'::jsonb, TRUE, NOW(), NOW())
                        """);

                st.executeUpdate("""
                        UPDATE employee SET manager_id = (SELECT id FROM employee WHERE employee_code='R-2001')
                        WHERE employee_code = 'R-2002'
                        """);
                st.executeUpdate("""
                        UPDATE employee SET manager_id = (SELECT id FROM employee WHERE employee_code='R-2002')
                        WHERE employee_code IN ('R-1001','R-1002','R-1003','R-1004','R-1005')
                        """);

                st.executeUpdate("""
                        INSERT INTO ext_indexed_attribute (employee_id, attr_key, attr_value)
                        SELECT id, 'union_member_id', 'UNION-7788' FROM employee WHERE employee_code = 'R-1001'
                        """);

                // Grant ANNUAL balance = annual_days for all staff for current year
                st.executeUpdate("""
                        INSERT INTO leave_balance (employee_id, leave_type_code, year, balance_days, updated_at)
                        SELECT id, 'ANNUAL', EXTRACT(YEAR FROM CURRENT_DATE)::int, 15.00, NOW()
                        FROM employee
                        """);

                // RBAC: clearLeaveAndOrg removes app_user_role — re-bind system roles (permissions already migrated).
                ensureRetailRbacRoles(st);
                st.executeUpdate("""
                        INSERT INTO app_user_role (employee_id, role_id)
                        SELECT e.id, r.id FROM employee e
                        JOIN app_role r ON r.code = CASE e.employee_code
                          WHEN 'R-2001' THEN 'HR'
                          WHEN 'R-2002' THEN 'MANAGER'
                          ELSE 'EMPLOYEE'
                        END
                        """);
            }
            conn.commit();
            logSeededUsers(conn, "TechShop");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed TechShop HRMS: " + e.getMessage(), e);
        }
    }

    /** Ensures SUPERADMIN/ADMIN/HR/MANAGER/EMPLOYEE exist with a usable permission matrix. */
    static void ensureRetailRbacRoles(Statement st) throws Exception {
        st.executeUpdate("""
                INSERT INTO app_role (code, name, description, system_role, active)
                SELECT v.code, v.name, v.description, TRUE, TRUE
                FROM (VALUES
                  ('SUPERADMIN', 'Super Admin', 'Full access'),
                  ('ADMIN', 'Admin', 'Company admin'),
                  ('HR', 'HR', 'Human resources'),
                  ('MANAGER', 'Manager', 'Team manager'),
                  ('EMPLOYEE', 'Employee', 'Standard employee')
                ) AS v(code, name, description)
                WHERE NOT EXISTS (SELECT 1 FROM app_role r WHERE r.code = v.code)
                """);
        st.executeUpdate("""
                INSERT INTO app_permission (feature, action, description)
                SELECT v.feature, v.action, v.description
                FROM (VALUES
                  ('employees','view','View employees'),
                  ('employees','create','Create employees'),
                  ('employees','edit','Edit employees'),
                  ('attendance','view','View attendance'),
                  ('attendance','checkin','Check in'),
                  ('attendance','correct','Correct attendance'),
                  ('leave','view','View leave'),
                  ('leave','request','Request leave'),
                  ('leave','approve','Approve leave'),
                  ('leave','manage','Manage leave'),
                  ('roles','view','View roles'),
                  ('roles','manage','Manage roles'),
                  ('payroll','view','View payroll'),
                  ('payroll','run','Run payroll'),
                  ('reports','view','View reports'),
                  ('reports','export','Export reports'),
                  ('notifications','view','View notifications'),
                  ('settings','manage','Manage settings'),
                  ('org','view','View org'),
                  ('org','manage','Manage org')
                ) AS v(feature, action, description)
                WHERE NOT EXISTS (
                  SELECT 1 FROM app_permission p WHERE p.feature = v.feature AND p.action = v.action
                )
                """);
        st.executeUpdate("""
                INSERT INTO app_role_permission (role_id, permission_id, data_scope)
                SELECT r.id, p.id, 'ALL'
                FROM app_role r CROSS JOIN app_permission p
                WHERE r.code IN ('SUPERADMIN','ADMIN','HR')
                  AND NOT EXISTS (
                    SELECT 1 FROM app_role_permission rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id
                  )
                """);
        st.executeUpdate("""
                INSERT INTO app_role_permission (role_id, permission_id, data_scope)
                SELECT r.id, p.id, 'TEAM'
                FROM app_role r CROSS JOIN app_permission p
                WHERE r.code = 'MANAGER'
                  AND p.feature IN ('employees','attendance','leave','org','reports','notifications')
                  AND p.action IN ('view','approve','correct','checkin','request')
                  AND NOT EXISTS (
                    SELECT 1 FROM app_role_permission rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id
                  )
                """);
        st.executeUpdate("""
                INSERT INTO app_role_permission (role_id, permission_id, data_scope)
                SELECT r.id, p.id, 'OWN'
                FROM app_role r CROSS JOIN app_permission p
                WHERE r.code = 'EMPLOYEE'
                  AND (
                    (p.feature = 'employees' AND p.action = 'view')
                    OR (p.feature = 'attendance' AND p.action IN ('view','checkin'))
                    OR (p.feature = 'leave' AND p.action IN ('view','request'))
                    OR (p.feature = 'notifications' AND p.action = 'view')
                    OR (p.feature = 'payroll' AND p.action = 'view')
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM app_role_permission rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id
                  )
                """);
    }

    static void clearLeaveAndOrg(Statement st) throws Exception {
        // Product tables (005) — safe no-op if empty; Impit/TechShop/Qii reseeds all clear employees
        safeDelete(st, "DELETE FROM app_user_role");
        safeDelete(st, "DELETE FROM work_log");
        safeDelete(st, "DELETE FROM work_task_assignee");
        safeDelete(st, "DELETE FROM work_task");
        safeDelete(st, "DELETE FROM attendance_record");
        st.executeUpdate("DELETE FROM leave_balance_ledger");
        st.executeUpdate("DELETE FROM leave_balance");
        st.executeUpdate("DELETE FROM leave_approval");
        st.executeUpdate("DELETE FROM leave_application");
        st.executeUpdate("DELETE FROM leave_blackout");
        st.executeUpdate("DELETE FROM store_shift_coverage");
        st.executeUpdate("DELETE FROM leave_type");
        st.executeUpdate("DELETE FROM leave_policy");
        st.executeUpdate("DELETE FROM ext_indexed_attribute");
        st.executeUpdate("DELETE FROM employee");
        st.executeUpdate("DELETE FROM designation");
        st.executeUpdate("DELETE FROM department");
        st.executeUpdate("DELETE FROM tenant_field_definition");
        st.executeUpdate("DELETE FROM tenant_settings");
        // Stable IDs after reseed (acceptance + FE demos assume Asman=1, cast 1..N)
        restartIdentity(st, "employee", "department", "designation", "leave_type", "leave_policy",
                "leave_application", "leave_approval", "leave_balance", "leave_balance_ledger",
                "store_shift_coverage", "leave_blackout", "tenant_field_definition", "tenant_settings",
                "ext_indexed_attribute", "attendance_record", "work_task", "work_log");
    }

    static void restartIdentity(Statement st, String... tables) {
        for (String table : tables) {
            try {
                // PostgreSQL identity/serial sequences are typically <table>_id_seq
                st.execute("ALTER SEQUENCE IF EXISTS " + table + "_id_seq RESTART WITH 1");
            } catch (Exception ignored) {
                try {
                    st.execute("SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), 1, false)");
                } catch (Exception ignored2) {
                    // table may lack a serial id
                }
            }
        }
    }

    private static void safeDelete(Statement st, String sql) {
        try {
            st.executeUpdate(sql);
        } catch (Exception ignored) {
            // table may not exist on pre-005 tenants mid-upgrade
        }
    }

    static void logSeededUsers(Connection conn, String label) throws Exception {
        try (Statement check = conn.createStatement();
             ResultSet rs = check.executeQuery(
                     "SELECT employee_code, full_name FROM employee ORDER BY employee_code")) {
            StringBuilder sb = new StringBuilder(label + " seeded users: ");
            while (rs.next()) {
                sb.append(rs.getString(1)).append('=').append(rs.getString(2)).append("; ");
            }
            LoggerFactory.getLogger(PracticeBusinessSeeder.class).info(sb.toString());
        }
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
                ps.setBigDecimal(1, new BigDecimal("2.0000"));
                ps.setBigDecimal(2, new BigDecimal("2.5000"));
                ps.setString(3, """
                        [{"type":"FIXED","amount":50,"label":"union_dues"}]
                        """);
                ps.setObject(4, OffsetDateTime.now(ZoneOffset.UTC));
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new IllegalStateException("payroll_config row missing after Liquibase");
                }
            }
            conn.commit();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed TechShop Payroll: " + e.getMessage(), e);
        }
    }
}
