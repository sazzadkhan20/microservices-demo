package com.platform.registry.infrastructure.provision;

import com.platform.registry.config.PlatformProperties;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Creates a dedicated PostgreSQL database for a tenant and runs Liquibase migrations.
 *
 * <p>Why separate DBs: isolation, clearer browsing of per-business schema,
 * and realistic multi-tenant SaaS practice (database-per-tenant).
 *
 * <p>Works the same locally and in Docker/deploy — only JDBC URLs change via env.
 */
@Component
public class PostgresDatabaseProvisioner {

    private static final Logger log = LoggerFactory.getLogger(PostgresDatabaseProvisioner.class);

    /**
     * CREATE DATABASE if missing, then apply changelog inside that DB.
     *
     * @return jdbc url pointing at the new tenant database
     */
    public String createAndMigrate(String dbName, PlatformProperties.DbTarget target) {
        createDatabaseIfAbsent(dbName, target);
        String tenantJdbcUrl = "jdbc:postgresql://" + target.getHost() + ":" + target.getPort() + "/" + dbName;
        migrateExisting(tenantJdbcUrl, target);
        return tenantJdbcUrl;
    }

    /** Re-run Liquibase on an already-created tenant DB (applies new changelogs only). */
    public void migrateExisting(String tenantJdbcUrl, PlatformProperties.DbTarget target) {
        runLiquibase(tenantJdbcUrl, target.getUsername(), target.getPassword(), stripClasspath(target.getChangelog()));
    }

    private void createDatabaseIfAbsent(String dbName, PlatformProperties.DbTarget target) {
        // Connect to maintenance DB (postgres) on that server
        try (Connection conn = DriverManager.getConnection(target.getAdminUrl(), target.getUsername(), target.getPassword());
             Statement st = conn.createStatement()) {

            boolean exists;
            try (ResultSet rs = st.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + dbName.replace("'", "''") + "'")) {
                exists = rs.next();
            }
            if (exists) {
                log.info("Database {} already exists on {}:{}", dbName, target.getHost(), target.getPort());
                return;
            }

            // CREATE DATABASE cannot run inside a transaction
            st.executeUpdate("CREATE DATABASE " + dbName + " OWNER " + target.getUsername());
            log.info("Created database {} on {}:{}", dbName, target.getHost(), target.getPort());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create database " + dbName + ": " + e.getMessage(), e);
        }
    }

    private void runLiquibase(String jdbcUrl, String user, String password, String changelogClasspath) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            try (Liquibase liquibase = new Liquibase(changelogClasspath, new ClassLoaderResourceAccessor(), database)) {
                // Do not clearCheckSums — edited applied changesets must fail loudly so fleet drift is visible.
                liquibase.update(new Contexts(), new LabelExpression());
            }
            log.info("Liquibase migrated {} using {}", jdbcUrl, changelogClasspath);
        } catch (Exception e) {
            throw new IllegalStateException("Liquibase failed for " + jdbcUrl + ": " + e.getMessage(), e);
        }
    }

    private static String stripClasspath(String changelog) {
        if (changelog == null) {
            throw new IllegalArgumentException("changelog is required");
        }
        return changelog.startsWith("classpath:") ? changelog.substring("classpath:".length()) : changelog;
    }
}
