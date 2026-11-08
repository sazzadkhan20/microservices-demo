package com.platform.registry.config;

import com.platform.common.security.ProdSecretsGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ProdSafetyConfig implements ApplicationRunner {

    @Value("${spring.profiles.active:}")
    private String profiles;

    @Value("${platform.security.service-key:}")
    private String serviceKey;

    @Value("${platform.security.internal-key:}")
    private String internalKey;

    @Value("${platform.security.admin-key:}")
    private String adminKey;

    @Value("${platform.jwt.secret:}")
    private String jwtSecret;

    @Override
    public void run(ApplicationArguments args) {
        ProdSecretsGuard.assertSafe(profiles, serviceKey, internalKey, adminKey, jwtSecret);
    }
}
