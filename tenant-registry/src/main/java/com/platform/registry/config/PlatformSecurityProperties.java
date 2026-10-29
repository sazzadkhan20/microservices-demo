package com.platform.registry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.security")
public class PlatformSecurityProperties {

    /**
     * Required on provision / upgrade / tenant list / internal routing.
     * Header: X-Platform-Admin-Key
     */
    private String adminKey = "local-platform-admin-change-me";

    /** Shared secret for service-to-service internal routing. Header: X-Service-Key */
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
