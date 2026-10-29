package com.platform.registry.infrastructure.provision;

import com.platform.registry.config.PlatformProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot copy from legacy shared {@code *_platform} DBs into per-tenant DBs.
 * Safe to re-run (skips when target already has rows / source DB missing).
 */
public final class TenantDataMigrator {

    private static final Logger log = LoggerFactory.getLogger(TenantDataMigrator.class);

    private TenantDataMigrator() {
    }

    public static int copyFileObjects(
            PlatformProperties.DbTarget target,
            String sourceDb,
            String targetJdbc,
            String tenantId) {
        String sourceJdbc = replaceDb(targetJdbc, sourceDb);
        if (!databaseReachable(sourceJdbc, target.getUsername(), target.getPassword())) {
            log.info("Skip file migrate — source DB {} unreachable", sourceDb);
            return 0;
        }
        try (Connection src = DriverManager.getConnection(sourceJdbc, target.getUsername(), target.getPassword());
             Connection dst = DriverManager.getConnection(targetJdbc, target.getUsername(), target.getPassword())) {
            if (count(dst, "file_object") > 0) {
                log.info("Skip file migrate — target already has rows ({})", targetJdbc);
                return 0;
            }
            List<Object[]> rows = new ArrayList<>();
            try (PreparedStatement ps = src.prepareStatement(
                    "SELECT id, tenant_id, original_name, content_type, size_bytes, storage_key, uploaded_by, created_at "
                            + "FROM file_object WHERE tenant_id = ?")) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new Object[]{
                                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                                rs.getLong(5), rs.getString(6), rs.getString(7), rs.getTimestamp(8)
                        });
                    }
                }
            }
            if (rows.isEmpty()) {
                return 0;
            }
            try (PreparedStatement ins = dst.prepareStatement(
                    "INSERT INTO file_object (id, tenant_id, original_name, content_type, size_bytes, storage_key, uploaded_by, created_at) "
                            + "VALUES (?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING")) {
                for (Object[] r : rows) {
                    for (int i = 0; i < r.length; i++) {
                        ins.setObject(i + 1, r[i]);
                    }
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            resetSequence(dst, "file_object");
            log.info("Migrated {} file_object rows for tenant {} → {}", rows.size(), tenantId, targetJdbc);
            return rows.size();
        } catch (Exception e) {
            log.warn("File migrate failed for {}: {}", tenantId, e.getMessage());
            return 0;
        }
    }

    public static int copyNotifications(
            PlatformProperties.DbTarget target,
            String sourceDb,
            String targetJdbc,
            String tenantId) {
        String sourceJdbc = replaceDb(targetJdbc, sourceDb);
        if (!databaseReachable(sourceJdbc, target.getUsername(), target.getPassword())) {
            log.info("Skip notif migrate — source DB {} unreachable", sourceDb);
            return 0;
        }
        try (Connection src = DriverManager.getConnection(sourceJdbc, target.getUsername(), target.getPassword());
             Connection dst = DriverManager.getConnection(targetJdbc, target.getUsername(), target.getPassword())) {
            if (count(dst, "notifications") > 0) {
                log.info("Skip notif migrate — target already has rows ({})", targetJdbc);
                return 0;
            }
            // Discover columns that exist in both schemas
            String sql = "SELECT id, tenant_id, employee_id, title, body, category, read_flag, dedupe_key, created_at "
                    + "FROM notifications WHERE tenant_id = ?";
            List<Object[]> rows = new ArrayList<>();
            try (PreparedStatement ps = src.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new Object[]{
                                rs.getLong("id"),
                                rs.getString("tenant_id"),
                                rs.getObject("employee_id"),
                                rs.getString("title"),
                                rs.getString("body"),
                                rs.getString("category"),
                                rs.getObject("read_flag"),
                                rs.getString("dedupe_key"),
                                rs.getTimestamp("created_at")
                        });
                    }
                }
            }
            if (rows.isEmpty()) {
                return 0;
            }
            try (PreparedStatement ins = dst.prepareStatement(
                    "INSERT INTO notifications (id, tenant_id, employee_id, title, body, category, read_flag, dedupe_key, created_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING")) {
                for (Object[] r : rows) {
                    for (int i = 0; i < r.length; i++) {
                        ins.setObject(i + 1, r[i]);
                    }
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            resetSequence(dst, "notifications");
            log.info("Migrated {} notifications for tenant {} → {}", rows.size(), tenantId, targetJdbc);
            return rows.size();
        } catch (Exception e) {
            log.warn("Notification migrate failed for {}: {}", tenantId, e.getMessage());
            return 0;
        }
    }

    private static String replaceDb(String jdbcUrl, String dbName) {
        int slash = jdbcUrl.lastIndexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("Invalid jdbc url: " + jdbcUrl);
        }
        String base = jdbcUrl.substring(0, slash + 1);
        String rest = jdbcUrl.substring(slash + 1);
        int q = rest.indexOf('?');
        String query = q >= 0 ? rest.substring(q) : "";
        return base + dbName + query;
    }

    private static boolean databaseReachable(String jdbc, String user, String pass) {
        try (Connection c = DriverManager.getConnection(jdbc, user, pass)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static long count(Connection c, String table) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void resetSequence(Connection c, String table) {
        try (Statement st = c.createStatement()) {
            st.execute("SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), "
                    + "COALESCE((SELECT MAX(id) FROM " + table + "), 1), true)");
        } catch (Exception e) {
            log.debug("Sequence reset skipped for {}: {}", table, e.getMessage());
        }
    }
}
