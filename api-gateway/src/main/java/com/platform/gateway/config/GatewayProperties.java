package com.platform.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "platform")
public class GatewayProperties {

    private Jwt jwt = new Jwt();
    private Security security = new Security();
    private DemoAuth demoAuth = new DemoAuth();
    private List<String> publicPaths = new ArrayList<>();

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

        public String getServiceKey() {
            return serviceKey;
        }

        public void setServiceKey(String serviceKey) {
            this.serviceKey = serviceKey;
        }
    }

    /**
     * Account-picker endpoints (token mint + account list). When enabled they require
     * header X-Demo-Auth-Key; when disabled they are not reachable through the gateway at all.
     */
    public static class DemoAuth {
        private boolean enabled = true;
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
}
