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
 * Seeds Impit Limited (BPO / property preservation) — Mon–Fri, typed leave, office coverage,
 * HR-managed balances, MANAGER|ADMIN → HR approvals, virtual_name unique.
 */
@Component
public class ImpitBusinessSeeder {

    private static final Logger log = LoggerFactory.getLogger(ImpitBusinessSeeder.class);

    public void seedImpitPractice(String hrmsJdbcUrl, String payrollJdbcUrl,
                                  PlatformProperties.DbTarget hrms, PlatformProperties.DbTarget payroll) {
        seedHrms(hrmsJdbcUrl, hrms.getUsername(), hrms.getPassword());
        seedPayroll(payrollJdbcUrl, payroll.getUsername(), payroll.getPassword());
        log.info("Seeded Impit Limited practice into {} and {}", hrmsJdbcUrl, payrollJdbcUrl);
    }

    public void reseedHrms(String hrmsJdbcUrl, PlatformProperties.DbTarget hrms) {
        seedHrms(hrmsJdbcUrl, hrms.getUsername(), hrms.getPassword());
    }

    private void seedHrms(String jdbcUrl, String user, String password) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                PracticeBusinessSeeder.clearLeaveAndOrg(st);

                st.executeUpdate("""
                        INSERT INTO leave_policy (
                          annual_days, work_week_days, working_days,
                          union_notice_days, non_union_notice_days,
                          require_coverage_check, approval_chain,
                          notice_mode, default_notice_days, coverage_mode,
                          location_field, shift_field, require_relief_on_coverage_fail,
                          default_leave_type_code, updated_at
                        ) VALUES (
                          0, 5,
                          '["MON","TUE","WED","THU","FRI"]'::jsonb,
                          0, 0, TRUE,
                          '[{"step":1,"role":"MANAGER"},{"step":2,"role":"HR"}]'::jsonb,
                          'NONE', 0, 'LOCATION',
                          'office_id', 'shift_pattern', FALSE,
                          'CASUAL', NOW()
                        )
                        """);

                st.executeUpdate("""
                        INSERT INTO leave_type
                          (code, name, paid, requires_balance, allow_when_zero_balance,
                           allow_when_coverage_fail, deduct_balance, active, sort_order)
                        VALUES
                          ('SICK', 'Sick Leave', TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, 1),
                          ('CASUAL', 'Casual Leave', TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 2),
                          ('ANNUAL', 'Annual Leave', TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 3),
                          ('UNPAID', 'Unpaid Leave', FALSE, FALSE, TRUE, FALSE, FALSE, TRUE, 4),
                          ('OTHER', 'Other Leave', TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 5)
                        """);

                // Single office coverage (no shift split)
                st.executeUpdate("""
                        INSERT INTO store_shift_coverage (store_id, shift_pattern, min_staff) VALUES
                          ('OFFICE-DHK', '*', 3)
                        """);

                st.executeUpdate("""
                        INSERT INTO tenant_field_definition
                          (field_name, storage_type, jsonb_path, extension_table, extension_column,
                           filterable, indexed_flag, required_flag, unique_active, validation_regex)
                        VALUES
                          ('virtual_name', 'EXTENSION_TABLE', NULL, 'ext_indexed_attribute', 'attr_value',
                           TRUE, TRUE, TRUE, TRUE, NULL),
                          ('office_id', 'JSONB', 'custom_fields.office_id', NULL, NULL, TRUE, FALSE, TRUE, FALSE, NULL),
                          ('hometown', 'JSONB', 'custom_fields.hometown', NULL, NULL, TRUE, FALSE, FALSE, FALSE, NULL),
                          ('religion', 'JSONB', 'custom_fields.religion', NULL, NULL, FALSE, FALSE, FALSE, FALSE, NULL),
                          ('gender', 'JSONB', 'custom_fields.gender', NULL, NULL, TRUE, FALSE, FALSE, FALSE, NULL),
                          ('joining_date', 'JSONB', 'custom_fields.joining_date', NULL, NULL, TRUE, FALSE, TRUE, FALSE, NULL),
                          ('joining_salary', 'JSONB', 'custom_fields.joining_salary', NULL, NULL, FALSE, FALSE, FALSE, FALSE, NULL)
                        """);

                st.executeUpdate("""
                        INSERT INTO tenant_settings (settings, updated_at)
                        VALUES (
                          '{"businessPack":"impit_limited","employeeCodePattern":"^\\\\d{4}$"}'::jsonb,
                          NOW()
                        )
                        """);

                st.executeUpdate("""
                        INSERT INTO department (code, name) VALUES
                          ('OPS', 'Operations'),
                          ('QA', 'Quality Assurance'),
                          ('ADMIN', 'Administration'),
                          ('HR', 'Human Resources')
                        """);

                st.executeUpdate("""
                        INSERT INTO designation (code, name, approver_role) VALUES
                          ('AGENT', 'BPO Agent', NULL),
                          ('TEAM_LEAD', 'Team Lead', NULL),
                          ('MANAGER', 'Manager', 'MANAGER'),
                          ('ADMIN_OFFICER', 'Admin Officer', 'ADMIN'),
                          ('SUPER_ADMIN', 'Super Admin', 'SUPER_ADMIN'),
                          ('HR_OFFICER', 'HR Officer', 'HR')
                        """);

                // 4-digit employee codes; virtual_name in extension table
                st.executeUpdate("""
                        INSERT INTO employee
                          (employee_code, full_name, email, department_id, designation_id, manager_id,
                           base_salary, hourly_rate, custom_fields, active, created_at, updated_at)
                        VALUES
                          ('1001', 'Nusrat Jahan', 'nusrat.hr@impit.local',
                           (SELECT id FROM department WHERE code='HR'),
                           (SELECT id FROM designation WHERE code='HR_OFFICER'),
                           NULL, 80000.0000, 0.0000,
                           '{"office_id":"OFFICE-DHK","hometown":"Dhaka","religion":"Islam","gender":"F","joining_date":"2022-01-10","joining_salary":75000}'::jsonb,
                           TRUE, NOW(), NOW()),
                          ('1002', 'Tanvir Hasan', 'tanvir.admin@impit.local',
                           (SELECT id FROM department WHERE code='ADMIN'),
                           (SELECT id FROM designation WHERE code='SUPER_ADMIN'),
                           NULL, 95000.0000, 0.0000,
                           '{"office_id":"OFFICE-DHK","hometown":"Chittagong","religion":"Islam","gender":"M","joining_date":"2021-06-01","joining_salary":90000}'::jsonb,
                           TRUE, NOW(), NOW()),
                          ('1003', 'Rina Akter', 'rina.mgr@impit.local',
                           (SELECT id FROM department WHERE code='OPS'),
                           (SELECT id FROM designation WHERE code='MANAGER'),
                           NULL, 70000.0000, 0.0000,
                           '{"office_id":"OFFICE-DHK","hometown":"Sylhet","religion":"Islam","gender":"F","joining_date":"2023-03-15","joining_salary":65000}'::jsonb,
                           TRUE, NOW(), NOW()),
                          ('2001', 'Alex Carter', 'alex@impit.local',
                           (SELECT id FROM department WHERE code='OPS'),
                           (SELECT id FROM designation WHERE code='AGENT'),
                           NULL, 45000.0000, 18.0000,
                           '{"office_id":"OFFICE-DHK","hometown":"Dhaka","religion":"Christianity","gender":"M","joining_date":"2024-08-01","joining_salary":42000}'::jsonb,
                           TRUE, NOW(), NOW()),
                          ('2002', 'Maya Lopez', 'maya@impit.local',
                           (SELECT id FROM department WHERE code='OPS'),
                           (SELECT id FROM designation WHERE code='AGENT'),
                           NULL, 44000.0000, 17.5000,
                           '{"office_id":"OFFICE-DHK","hometown":"Khulna","religion":"Christianity","gender":"F","joining_date":"2024-09-12","joining_salary":41000}'::jsonb,
                           TRUE, NOW(), NOW()),
                          ('2003', 'Jamal Uddin', 'jamal@impit.local',
                           (SELECT id FROM department WHERE code='QA'),
                           (SELECT id FROM designation WHERE code='TEAM_LEAD'),
                           NULL, 52000.0000, 20.0000,
                           '{"office_id":"OFFICE-DHK","hometown":"Rajshahi","religion":"Islam","gender":"M","joining_date":"2023-11-20","joining_salary":50000}'::jsonb,
                           TRUE, NOW(), NOW()),
                          ('2004', 'Priya Sen', 'priya@impit.local',
                           (SELECT id FROM department WHERE code='QA'),
                           (SELECT id FROM designation WHERE code='AGENT'),
                           NULL, 43000.0000, 17.0000,
                           '{"office_id":"OFFICE-DHK","hometown":"Dhaka","religion":"Hinduism","gender":"F","joining_date":"2025-01-05","joining_salary":40000}'::jsonb,
                           TRUE, NOW(), NOW()),
                          ('1004', 'Sabbir Khan', 'sabbir.admin@impit.local',
                           (SELECT id FROM department WHERE code='ADMIN'),
                           (SELECT id FROM designation WHERE code='ADMIN_OFFICER'),
                           NULL, 60000.0000, 0.0000,
                           '{"office_id":"OFFICE-DHK","hometown":"Barisal","religion":"Islam","gender":"M","joining_date":"2022-09-01","joining_salary":55000}'::jsonb,
                           TRUE, NOW(), NOW())
                        """);

                // Managers: Rina reports to Nusrat; floor staff report to Rina; admin reports to Tanvir
                st.executeUpdate("""
                        UPDATE employee SET manager_id = (SELECT id FROM employee WHERE employee_code='1001')
                        WHERE employee_code IN ('1003','1002')
                        """);
                st.executeUpdate("""
                        UPDATE employee SET manager_id = (SELECT id FROM employee WHERE employee_code='1003')
                        WHERE employee_code IN ('2001','2002','2003','2004')
                        """);
                st.executeUpdate("""
                        UPDATE employee SET manager_id = (SELECT id FROM employee WHERE employee_code='1002')
                        WHERE employee_code = '1004'
                        """);

                st.executeUpdate("""
                        INSERT INTO ext_indexed_attribute (employee_id, attr_key, attr_value)
                        SELECT id, 'virtual_name', v.vname FROM employee e
                        JOIN (VALUES
                          ('1001','HR.Nusrat'),
                          ('1002','SA.Tanvir'),
                          ('1003','MGR.Rina'),
                          ('1004','ADM.Sabbir'),
                          ('2001','OPS.Alex'),
                          ('2002','OPS.Maya'),
                          ('2003','QA.Jamal'),
                          ('2004','QA.Priya')
                        ) AS v(code, vname) ON e.employee_code = v.code
                        """);

                // Sample balances after probation (HR-managed; no auto accrual)
                st.executeUpdate("""
                        INSERT INTO leave_balance (employee_id, leave_type_code, year, balance_days, updated_at)
                        SELECT e.id, t.code, EXTRACT(YEAR FROM CURRENT_DATE)::int, t.bal, NOW()
                        FROM employee e
                        CROSS JOIN (VALUES
                          ('SICK', 2.00),
                          ('CASUAL', 2.00),
                          ('ANNUAL', 2.00),
                          ('OTHER', 0.00)
                        ) AS t(code, bal)
                        WHERE e.employee_code IN ('2001','2002','2003','2004','1003','1004')
                        """);
            }
            conn.commit();
            PracticeBusinessSeeder.logSeededUsers(conn, "Impit");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed Impit HRMS: " + e.getMessage(), e);
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
                ps.setBigDecimal(1, new BigDecimal("1.5000"));
                ps.setBigDecimal(2, new BigDecimal("2.0000"));
                ps.setString(3, "[]");
                ps.setObject(4, OffsetDateTime.now(ZoneOffset.UTC));
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new IllegalStateException("payroll_config row missing after Liquibase");
                }
            }
            conn.commit();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed Impit Payroll: " + e.getMessage(), e);
        }
    }
}
