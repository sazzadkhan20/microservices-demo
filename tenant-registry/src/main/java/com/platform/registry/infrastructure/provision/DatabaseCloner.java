package com.platform.registry.infrastructure.provision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Copies selected domain tables between tenant DBs during provision bootstrap only.
 * Never used for cross-service runtime access — each service only opens its own JDBC.
 */
@Component
public class DatabaseCloner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCloner.class);

    /**
     * Leave SoT only — employee/RBAC/org live in People (API).
     * Settings are NOT cloned: {@link SettingsOwnership} writes leave_settings slice separately.
     */
    public static final List<String> LEAVE_SEED_TABLES = List.of(
            "leave_policy",
            "leave_type",
            "leave_category",
            "leave_balance",
            "leave_balance_ledger",
            "leave_application",
            "leave_approval",
            "leave_approval_delegate",
            "leave_blackout",
            "leave_operation",
            "store_shift_coverage"
            // audit is service-local (leave_audit_log) — never clone from People
    );

    /**
     * Time&amp;Work SoT only — employee/RBAC/org live in People (API).
     * Settings are NOT cloned: {@link SettingsOwnership} writes timework_settings slice separately.
     */
    public static final List<String> TIMEWORK_SEED_TABLES = List.of(
            "work_schedule",
            "holiday",
            "attendance_record",
            "attendance_correction",
            "work_task",
            "work_task_assignee",
            "work_log"
            // audit is service-local (timework_audit_log) — never clone from People
    );

    /**
     * Drop Leave/Time&Work owned tables from People after distribute so DataGrip / schema
     * browsers show a clean People SoT (runtime People code never queries these).
     */
    public static final List<String> PEOPLE_PURGE_FOREIGN_SQL = List.of(
            "DROP TABLE IF EXISTS leave_operation CASCADE",
            "DROP TABLE IF EXISTS leave_approval_delegate CASCADE",
            "DROP TABLE IF EXISTS leave_approval CASCADE",
            "DROP TABLE IF EXISTS leave_application CASCADE",
            "DROP TABLE IF EXISTS leave_balance_ledger CASCADE",
            "DROP TABLE IF EXISTS leave_balance CASCADE",
            "DROP TABLE IF EXISTS leave_blackout CASCADE",
            "DROP TABLE IF EXISTS leave_category CASCADE",
            "DROP TABLE IF EXISTS leave_type CASCADE",
            "DROP TABLE IF EXISTS leave_policy CASCADE",
            "DROP TABLE IF EXISTS store_shift_coverage CASCADE",
            "DROP TABLE IF EXISTS attendance_correction CASCADE",
            "DROP TABLE IF EXISTS attendance_record CASCADE",
            "DROP TABLE IF EXISTS work_task_assignee CASCADE",
            "DROP TABLE IF EXISTS work_log CASCADE",
            "DROP TABLE IF EXISTS work_task CASCADE",
            "DROP TABLE IF EXISTS work_schedule CASCADE",
            "DROP TABLE IF EXISTS holiday CASCADE"
    );

    public void cloneAllTables(String sourceJdbc, String destJdbc, String username, String password) {
        cloneTables(sourceJdbc, destJdbc, username, password, null);
    }

    /**
     * Recreate empty Leave/Time&amp;Work table shells on People so pack seeders can write,
     * then {@link #cloneTables} + People purge restore DB-per-service ownership.
     * Needed after People has already dropped foreign SoT tables (reseed / post-purge).
     */
    public void ensureEmptyTablesExist(
            String sourceJdbc,
            String destJdbc,
            String username,
            String password,
            List<String> tables) {
        if (tables == null || tables.isEmpty()) {
            return;
        }
        try (Connection src = DriverManager.getConnection(sourceJdbc, username, password);
             Connection dst = DriverManager.getConnection(destJdbc, username, password);
             Statement srcSt = src.createStatement();
             Statement dstSt = dst.createStatement()) {
            dst.setAutoCommit(false);
            int created = 0;
            for (String table : tables) {
                if (!tableExists(srcSt, table)) {
                    continue;
                }
                if (tableExists(dstSt, table)) {
                    continue;
                }
                String ddl = buildCreateTableDdl(srcSt, table);
                dstSt.execute(ddl);
                created++;
            }
            dst.commit();
            if (created > 0) {
                log.info("Restored {} empty bootstrap table(s) on {} from {}", created, destJdbc, sourceJdbc);
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed ensuring bootstrap tables " + sourceJdbc + " → " + destJdbc + ": " + e.getMessage(), e);
        }
    }

    private static String buildCreateTableDdl(Statement srcSt, String table) throws Exception {
        StringBuilder ddl = new StringBuilder("CREATE TABLE \"").append(table).append("\" (");
        boolean first = true;
        try (ResultSet cols = srcSt.executeQuery(
                "SELECT column_name, udt_name, character_maximum_length, numeric_precision, numeric_scale, "
                        + "is_nullable, column_default "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema='public' AND table_name='" + table + "' "
                        + "ORDER BY ordinal_position")) {
            while (cols.next()) {
                if (!first) {
                    ddl.append(", ");
                }
                first = false;
                String name = cols.getString("column_name");
                String udt = cols.getString("udt_name");
                String nullable = cols.getString("is_nullable");
                String colDefault = cols.getString("column_default");
                ddl.append('"').append(name).append("\" ");
                if (colDefault != null && colDefault.contains("nextval")) {
                    // Pack seeders omit id — need identity defaults on restored shells.
                    ddl.append(mapUdt(udt, cols)).append(" GENERATED BY DEFAULT AS IDENTITY");
                } else {
                    ddl.append(mapUdt(udt, cols));
                    if (colDefault != null && !colDefault.isBlank()) {
                        ddl.append(" DEFAULT ").append(colDefault);
                    }
                }
                if ("NO".equalsIgnoreCase(nullable)) {
                    ddl.append(" NOT NULL");
                }
            }
        }
        if (first) {
            throw new IllegalStateException("No columns found for table " + table);
        }
        ddl.append(')');
        return ddl.toString();
    }

    private static String mapUdt(String udt, ResultSet cols) throws Exception {
        if (udt == null) {
            return "text";
        }
        return switch (udt) {
            case "int8" -> "bigint";
            case "int4" -> "integer";
            case "int2" -> "smallint";
            case "bool" -> "boolean";
            case "text" -> "text";
            case "varchar", "bpchar" -> {
                int len = cols.getInt("character_maximum_length");
                yield len > 0 ? "varchar(" + len + ")" : "varchar";
            }
            case "numeric" -> {
                int p = cols.getInt("numeric_precision");
                int s = cols.getInt("numeric_scale");
                yield p > 0 ? "numeric(" + p + "," + s + ")" : "numeric";
            }
            case "timestamptz" -> "timestamptz";
            case "timestamp" -> "timestamp";
            case "date" -> "date";
            case "time" -> "time";
            case "jsonb" -> "jsonb";
            case "json" -> "json";
            case "uuid" -> "uuid";
            case "float8" -> "double precision";
            case "float4" -> "real";
            default -> udt;
        };
    }

    public void cloneTables(
            String sourceJdbc,
            String destJdbc,
            String username,
            String password,
            List<String> allowlist) {
        try (Connection src = DriverManager.getConnection(sourceJdbc, username, password);
             Connection dst = DriverManager.getConnection(destJdbc, username, password);
             Statement srcSt = src.createStatement();
             Statement dstSt = dst.createStatement()) {

            dst.setAutoCommit(false);
            dstSt.execute("SET session_replication_role = replica");

            List<String> tables = new ArrayList<>();
            try (ResultSet rs = srcSt.executeQuery(
                    "SELECT tablename FROM pg_tables WHERE schemaname='public' "
                            + "AND tablename NOT LIKE 'databasechangelog%' "
                            + "ORDER BY tablename")) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (allowlist == null || allowlist.contains(name)) {
                        tables.add(name);
                    }
                }
            }

            for (String table : tables) {
                if (!tableExists(dstSt, table)) {
                    log.warn("Skip clone of missing dest table {}", table);
                    continue;
                }
                dstSt.execute("TRUNCATE TABLE \"" + table + "\" CASCADE");
            }

            for (String table : tables) {
                if (!tableExists(dstSt, table)) {
                    continue;
                }
                try (ResultSet colsRs = srcSt.executeQuery(
                        "SELECT column_name FROM information_schema.columns "
                                + "WHERE table_schema='public' AND table_name='" + table
                                + "' ORDER BY ordinal_position")) {
                    List<String> cols = new ArrayList<>();
                    while (colsRs.next()) {
                        cols.add(colsRs.getString(1));
                    }
                    if (cols.isEmpty()) {
                        continue;
                    }
                    // Only columns that exist on both sides
                    List<String> destCols = new ArrayList<>();
                    try (ResultSet dCols = dstSt.executeQuery(
                            "SELECT column_name FROM information_schema.columns "
                                    + "WHERE table_schema='public' AND table_name='" + table
                                    + "' ORDER BY ordinal_position")) {
                        while (dCols.next()) {
                            destCols.add(dCols.getString(1));
                        }
                    }
                    cols.retainAll(destCols);
                    if (cols.isEmpty()) {
                        continue;
                    }
                    String colList = String.join(", ", cols.stream().map(c -> "\"" + c + "\"").toList());
                    try (ResultSet data = srcSt.executeQuery("SELECT " + colList + " FROM \"" + table + "\"")) {
                        int colCount = cols.size();
                        String placeholders = String.join(", ", java.util.Collections.nCopies(colCount, "?"));
                        String insertSql = "INSERT INTO \"" + table + "\" (" + colList + ") VALUES (" + placeholders + ")";
                        try (var ps = dst.prepareStatement(insertSql)) {
                            int batch = 0;
                            while (data.next()) {
                                for (int i = 1; i <= colCount; i++) {
                                    ps.setObject(i, data.getObject(i));
                                }
                                ps.addBatch();
                                if (++batch % 500 == 0) {
                                    ps.executeBatch();
                                }
                            }
                            ps.executeBatch();
                        }
                    }
                }
                // Savepoint: a failed setval aborts the PG transaction even if Java catches it.
                var setvalSp = dst.setSavepoint("setval_" + table);
                try {
                    dstSt.execute(
                            "SELECT setval(pg_get_serial_sequence('public." + table + "', 'id'), "
                                    + "COALESCE((SELECT MAX(id) FROM \"" + table + "\"), 1), true)");
                    dst.releaseSavepoint(setvalSp);
                } catch (Exception ignored) {
                    dst.rollback(setvalSp);
                }
            }

            dstSt.execute("SET session_replication_role = DEFAULT");
            dst.commit();
            log.info("Cloned {} owned tables from {} → {}", tables.size(), sourceJdbc, destJdbc);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed cloning DB " + sourceJdbc + " → " + destJdbc + ": " + e.getMessage(), e);
        }
    }

    private static boolean tableExists(Statement st, String table) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='"
                        + table + "'")) {
            return rs.next();
        }
    }
}
