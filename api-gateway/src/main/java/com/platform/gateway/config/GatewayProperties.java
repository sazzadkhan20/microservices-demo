package com.platform.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "platform")
public class GatewayProperties {

    private Jwt jwt = new Jwt();
    private Security security = new Security();
    private DemoAuth demoAuth = new DemoAuth();
    private Oidc oidc = new Oidc();
    private List<String> publicPaths = new ArrayList<>();
    private Session session = new Session();
    private People people = new People();

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public DemoAuth getDemoAuth() {
        return demoAuth;
    }

    public void setDemoAuth(DemoAuth demoAuth) {
        this.demoAuth = demoAuth;
    }

    public Oidc getOidc() {
        return oidc;
    }

    public void setOidc(Oidc oidc) {
        this.oidc = oidc;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public People getPeople() {
        return people;
    }

    public void setPeople(People people) {
        this.people = people;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
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

    /** Shared secret the gateway stamps on every proxied request (header X-Service-Key). */
    public static class Security {
        private String serviceKey;
        private String adminKey;
        /** Backend-only secret for system calls (e.g. People employee auth-check at login). */
        private String internalKey;

        public String getInternalKey() {
            return internalKey;
        }

        public void setInternalKey(String internalKey) {
            this.internalKey = internalKey;
        }

        public String getServiceKey() {
            return serviceKey;
        }

        public void setServiceKey(String serviceKey) {
            this.serviceKey = serviceKey;
        }

        public String getAdminKey() {
            return adminKey;
        }

        public void setAdminKey(String adminKey) {
            this.adminKey = adminKey;
        }
    }

    /**
     * Account-picker endpoints (token mint + account list). When enabled they require
     * header X-Demo-Auth-Key; when disabled they are not reachable through the gateway at all.
     */
    public static class DemoAuth {
        private boolean enabled = false;
        private String key;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    /**
     * Google OIDC session mint (guide §4 / SEC-01). BFF exchanges the auth code, then posts the
     * Google ID token here with {@code X-Oidc-Bff-Key}. Gateway re-verifies the token, maps
     * identity via People, and issues the platform session JWT.
     */
    public static class Oidc {
        private boolean enabled = false;
        private String googleClientId;
        private String bffKey;
        /** Comma-separated email domains allowed after Google verification (e.g. qiicreative.com.au,gmail.com). */
        private String allowedEmailDomains = "qiicreative.com.au";
        private String jwksUri = "https://www.googleapis.com/oauth2/v3/certs";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getGoogleClientId() {
            return googleClientId;
        }

        public void setGoogleClientId(String googleClientId) {
            this.googleClientId = googleClientId;
        }

        public String getBffKey() {
            return bffKey;
        }

        public void setBffKey(String bffKey) {
            this.bffKey = bffKey;
        }

        public String getAllowedEmailDomains() {
            return allowedEmailDomains;
        }

        public void setAllowedEmailDomains(String allowedEmailDomains) {
            this.allowedEmailDomains = allowedEmailDomains;
        }

        public String getJwksUri() {
            return jwksUri;
        }

        public void setJwksUri(String jwksUri) {
            this.jwksUri = jwksUri;
        }
    }

    /** Session token settings (gateway is the only token issuer and verifier). */
    public static class Session {
        private long tokenTtlSeconds = 604_800L;

        public long getTokenTtlSeconds() {
            return tokenTtlSeconds;
        }

        public void setTokenTtlSeconds(long tokenTtlSeconds) {
            this.tokenTtlSeconds = tokenTtlSeconds;
        }

        /** Refuse sessions of employees who may no longer sign in (offboarded / deactivated). */
        private boolean activeCheckEnabled = true;
        /** Bounded delay before a status change is seen at the edge. */
        private long activeCheckTtlSeconds = 30;

        public boolean isActiveCheckEnabled() {
            return activeCheckEnabled;
        }

        public void setActiveCheckEnabled(boolean activeCheckEnabled) {
            this.activeCheckEnabled = activeCheckEnabled;
        }

        public long getActiveCheckTtlSeconds() {
            return activeCheckTtlSeconds;
        }

        public void setActiveCheckTtlSeconds(long activeCheckTtlSeconds) {
            this.activeCheckTtlSeconds = activeCheckTtlSeconds;
        }
    }

    /** People service (employee identity source) used to validate logins. */
    public static class People {
        private String baseUrl = "http://127.0.0.1:8081";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
