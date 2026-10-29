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

    public static class Security {
        /** Header X-Platform-Admin-Key for provision/upgrade/tenant list */
        private String adminKey = "local-platform-admin-change-me";
        /** Header X-Service-Key for internal routing with DB credentials */
        private String serviceKey = "local-service-key-change-me";

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
        private DbTarget hrms = new DbTarget();
        private DbTarget payroll = new DbTarget();

        public DbTarget getHrms() {
            return hrms;
        }

        public void setHrms(DbTarget hrms) {
            this.hrms = hrms;
        }

        public DbTarget getPayroll() {
            return payroll;
        }

        public void setPayroll(DbTarget payroll) {
            this.payroll = payroll;
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
