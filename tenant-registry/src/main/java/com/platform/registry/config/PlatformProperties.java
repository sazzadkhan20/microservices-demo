package com.platform.registry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized provision settings (local vs Docker/deploy via env vars).
 */
@ConfigurationProperties(prefix = "platform")
public class PlatformProperties {

    private Jwt jwt = new Jwt();
    private Provision provision = new Provision();
    private Security security = new Security();
    private Fleet fleet = new Fleet();

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Provision getProvision() {
        return provision;
    }

    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Fleet getFleet() {
        return fleet;
    }

    public void setFleet(Fleet fleet) {
        this.fleet = fleet;
    }

    /**
     * Fleet schema rollout — keep every tenant DB on the same shared product changelog.
     * Large SaaS pattern: migrate all tenants on control-plane boot (then code serves traffic).
     */
    public static class Fleet {
        /** When true, registry runs migrate-all on startup (idempotent Liquibase). */
        private boolean autoMigrateOnStartup = true;
        /** Redis lock TTL while a replica owns fleet migrate. */
        private long lockTtlSeconds = 1800;
        /** How long a non-leader replica waits for the leader's done signal. */
        private long followerWaitSeconds = 1800;

        public boolean isAutoMigrateOnStartup() {
            return autoMigrateOnStartup;
        }

        public void setAutoMigrateOnStartup(boolean autoMigrateOnStartup) {
            this.autoMigrateOnStartup = autoMigrateOnStartup;
        }

        public long getLockTtlSeconds() {
            return lockTtlSeconds;
        }

        public void setLockTtlSeconds(long lockTtlSeconds) {
            this.lockTtlSeconds = lockTtlSeconds;
        }

        public long getFollowerWaitSeconds() {
            return followerWaitSeconds;
        }

        public void setFollowerWaitSeconds(long followerWaitSeconds) {
            this.followerWaitSeconds = followerWaitSeconds;
        }
    }

    public static class Security {
        /** Header X-Platform-Admin-Key for provision/upgrade/tenant list */
        private String adminKey;
        /** Header X-Service-Key for internal routing with DB credentials */
        private String serviceKey;

        public String getAdminKey() {
            return adminKey;
        }

        public void setAdminKey(String adminKey) {
            this.adminKey = adminKey;
        }

        public String getServiceKey() {
            return serviceKey;
        }

        public void setServiceKey(String serviceKey) {
            this.serviceKey = serviceKey;
        }
    }

    public static class Jwt {
        private String secret;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class Provision {
        /** @deprecated use {@link #people} — retained as YAML alias during cutover */
        @Deprecated
        private DbTarget hrms = new DbTarget();
        private DbTarget people = new DbTarget();
        private DbTarget leave = new DbTarget();
        private DbTarget timeWork = new DbTarget();
        private DbTarget payroll = new DbTarget();
        private DbTarget files = new DbTarget();
        private DbTarget notification = new DbTarget();
        private DbTarget report = new DbTarget();
        private Minio minio = new Minio();

        /** Prefer people; falls back to legacy hrms target if people changelog unset. */
        public DbTarget getHrms() {
            if (people.getChangelog() != null && !people.getChangelog().isBlank()) {
                return people;
            }
            return hrms;
        }

        public void setHrms(DbTarget hrms) {
            this.hrms = hrms;
            if (people.getChangelog() == null || people.getChangelog().isBlank()) {
                this.people = hrms;
            }
        }

        public DbTarget getPeople() {
            return getHrms();
        }

        public void setPeople(DbTarget people) {
            this.people = people;
        }

        public DbTarget getLeave() {
            return leave;
        }

        public void setLeave(DbTarget leave) {
            this.leave = leave;
        }

        public DbTarget getTimeWork() {
            return timeWork;
        }

        public void setTimeWork(DbTarget timeWork) {
            this.timeWork = timeWork;
        }

        public DbTarget getPayroll() {
            return payroll;
        }

        public void setPayroll(DbTarget payroll) {
            this.payroll = payroll;
        }

        public DbTarget getFiles() {
            return files;
        }

        public void setFiles(DbTarget files) {
            this.files = files;
        }

        public DbTarget getNotification() {
            return notification;
        }

        public void setNotification(DbTarget notification) {
            this.notification = notification;
        }

        public DbTarget getReport() {
            return report;
        }

        public void setReport(DbTarget report) {
            this.report = report;
        }

        public Minio getMinio() {
            return minio;
        }

        public void setMinio(Minio minio) {
            this.minio = minio;
        }
    }

    public static class Minio {
        private String endpoint = "http://127.0.0.1:9000";
        private String accessKey;
        private String secretKey;
        private boolean enabled = true;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class DbTarget {
        /** JDBC URL to the Postgres server's maintenance DB (usually /postgres). */
        private String adminUrl;
        private String host;
        private int port;
        private String username;
        private String password;
        /** Liquibase changelog applied inside each new tenant DB. */
        private String changelog;

        public String getAdminUrl() {
            return adminUrl;
        }

        public void setAdminUrl(String adminUrl) {
            this.adminUrl = adminUrl;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getChangelog() {
            return changelog;
        }

        public void setChangelog(String changelog) {
            this.changelog = changelog;
        }
    }
}
