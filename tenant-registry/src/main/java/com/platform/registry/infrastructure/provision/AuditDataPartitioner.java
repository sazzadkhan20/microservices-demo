package com.platform.registry.infrastructure.provision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Splits cloned monolith audit rows into each service's owned audit table (guide §3).
 */
@Component
public class AuditDataPartitioner {

    private static final Logger log = LoggerFactory.getLogger(AuditDataPartitioner.class);

    public Map<String, Integer> partitionFromPeople(
            String peopleJdbc,
            String leaveJdbc,
            String timeWorkJdbc,
            String payrollJdbc,
            String filesJdbc,
            String notifJdbc,
            String reportJdbc,
            String username,
            String password) {
        Map<String, Integer> moved = new LinkedHashMap<>();
        try (Connection people = DriverManager.getConnection(peopleJdbc, username, password)) {
            ensureAuditTable(people, "people_audit_log");
            String source = resolveSource(people);
            if (source == null) {
                log.info("No People audit source to partition");
                return moved;
            }

            moved.put("leave", copyOwned(people, leaveJdbc, username, password, source, "leave_audit_log", AuditOwnership.LEAVE));
            moved.put("timework", copyOwned(people, timeWorkJdbc, username, password, source, "timework_audit_log", AuditOwnership.TIMEWORK));
            moved.put("payroll", copyOwned(people, payrollJdbc, username, password, source, "payroll_audit_log", AuditOwnership.PAYROLL));
            moved.put("files", copyOwned(people, filesJdbc, username, password, source, "files_audit_log", AuditOwnership.FILES));
            moved.put("notification", copyOwned(people, notifJdbc, username, password, source, "notification_audit_log", AuditOwnership.NOTIFICATION));
            moved.put("report", copyOwned(people, reportJdbc, username, password, source, "report_audit_log", AuditOwnership.REPORT));

            if ("audit_log".equals(source)) {
                moved.put("people", copyOwned(people, peopleJdbc, username, password, "audit_log", "people_audit_log", AuditOwnership.PEOPLE));
                try (Statement st = people.createStatement()) {
                    st.execute("DROP TABLE IF EXISTS audit_log CASCADE");
                }
            }

            moved.put("people_deleted_foreign", slim(peopleJdbc, username, password, "people_audit_log", AuditOwnership.PEOPLE));
            moved.put("leave_slimmed", slim(leaveJdbc, username, password, "leave_audit_log", AuditOwnership.LEAVE));
            moved.put("timework_slimmed", slim(timeWorkJdbc, username, password, "timework_audit_log", AuditOwnership.TIMEWORK));
            moved.put("payroll_slimmed", slim(payrollJdbc, username, password, "payroll_audit_log", AuditOwnership.PAYROLL));
            moved.put("files_slimmed", slim(filesJdbc, username, password, "files_audit_log", AuditOwnership.FILES));
            moved.put("notification_slimmed", slim(notifJdbc, username, password, "notification_audit_log", AuditOwnership.NOTIFICATION));
            moved.put("report_slimmed", slim(reportJdbc, username, password, "report_audit_log", AuditOwnership.REPORT));

            log.info("Partitioned audit ownership: {}", moved);
            return moved;
        } catch (Exception e) {
            throw new IllegalStateException("Audit partition failed: " + e.getMessage(), e);
        }
    }

    private static String resolveSource(Connection people) throws Exception {
        if (tableExists(people, "people_audit_log") && count(people, "people_audit_log") > 0) {
            return "people_audit_log";
        }
        if (tableExists(people, "audit_log") && count(people, "audit_log") > 0) {
            return "audit_log";
        }
        if (tableExists(people, "people_audit_log")) {
            return "people_audit_log";
        }
        return null;
    }

    private int copyOwned(
            Connection src,
            String destJdbc,
            String user,
            String pass,
            String srcTable,
            String destTable,
            Set<String> ownedKeys) throws Exception {
        if (destJdbc == null || destJdbc.isBlank()) {
            return 0;
        }
        try (Connection dst = DriverManager.getConnection(destJdbc, user, pass)) {
            ensureAuditTable(dst, destTable);
            String inList = AuditOwnership.sqlInList(ownedKeys);
            int copied = 0;
            String sql = "SELECT at, actor_employee_id, action, entity, entity_id, "
                    + "before_json::text, after_json::text, ip FROM \"" + srcTable
                    + "\" WHERE lower(replace(coalesce(entity,''), ' ', '')) IN (" + inList + ")";
            try (Statement st = src.createStatement();
                 ResultSet rs = st.executeQuery(sql);
                 PreparedStatement ps = dst.prepareStatement(
                         "INSERT INTO \"" + destTable + "\" (at, actor_employee_id, action, entity, entity_id, before_json, after_json, ip) "
                                 + "SELECT ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ? "
                                 + "WHERE NOT EXISTS (SELECT 1 FROM \"" + destTable
                                 + "\" d WHERE d.at = ? AND d.action = ? "
                                 + "AND coalesce(d.entity,'') = coalesce(?, '') "
                                 + "AND coalesce(d.entity_id,'') = coalesce(?, ''))")) {
                while (rs.next()) {
                    java.sql.Timestamp at = rs.getTimestamp(1);
                    String action = rs.getString(3);
                    String entity = rs.getString(4);
                    String entityId = rs.getString(5);
                    ps.setTimestamp(1, at);
                    ps.setObject(2, rs.getObject(2));
                    ps.setString(3, action);
                    ps.setString(4, entity);
                    ps.setString(5, entityId);
                    ps.setString(6, rs.getString(6));
                    ps.setString(7, rs.getString(7));
                    ps.setString(8, rs.getString(8));
                    ps.setTimestamp(9, at);
                    ps.setString(10, action);
                    ps.setString(11, entity);
                    ps.setString(12, entityId);
                    copied += ps.executeUpdate();
                }
            }
            return copied;
        }
    }

    private int slim(String jdbc, String user, String pass, String table, Set<String> ownedKeys) throws Exception {
        if (jdbc == null || jdbc.isBlank()) {
            return 0;
        }
        try (Connection conn = DriverManager.getConnection(jdbc, user, pass)) {
            ensureAuditTable(conn, table);
            String inList = AuditOwnership.sqlInList(ownedKeys);
            try (Statement st = conn.createStatement()) {
                return st.executeUpdate(
                        "DELETE FROM \"" + table + "\" WHERE lower(replace(coalesce(entity,''), ' ', '')) NOT IN ("
                                + inList + ")");
            }
        }
    }

    private static void ensureAuditTable(Connection conn, String table) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS "%s" (
                      id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                      at TIMESTAMPTZ NOT NULL,
                      actor_employee_id BIGINT,
                      action VARCHAR(64) NOT NULL,
                      entity VARCHAR(64),
                      entity_id VARCHAR(64),
                      before_json JSONB,
                      after_json JSONB,
                      ip VARCHAR(64)
                    )
                    """.formatted(table));
        }
    }

    private static boolean tableExists(Connection conn, String table) throws Exception {
        try (ResultSet rs = conn.getMetaData().getTables(null, "public", table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static long count(Connection conn, String table) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM \"" + table + "\"")) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }
}
