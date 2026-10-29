package com.platform.registry.api;

import com.platform.registry.config.PlatformProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PlatformAuthSupport {

    public static final String ADMIN_HEADER = "X-Platform-Admin-Key";
    public static final String SERVICE_HEADER = "X-Service-Key";

    private final PlatformProperties properties;

    public PlatformAuthSupport(PlatformProperties properties) {
        this.properties = properties;
    }

    public void requireAdmin(String provided) {
        String expected = properties.getSecurity().getAdminKey();
        if (provided == null || expected == null || !provided.equals(expected)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing " + ADMIN_HEADER);
        }
    }

    public void requireService(String provided) {
        String expected = properties.getSecurity().getServiceKey();
        if (provided == null || expected == null || !provided.equals(expected)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing " + SERVICE_HEADER);
        }
    }
}
