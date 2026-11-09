package com.platform.gateway.config;

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

    @Value("${platform.jwt.secret:}")
    private String jwtSecret;

    @Value("${platform.demo-auth.key:}")
    private String demoAuthKey;

    @Value("${platform.demo-auth.enabled:false}")
    private boolean demoAuthEnabled;

    @Value("${platform.security.require-gateway:true}")
    private boolean requireGateway;

    @Override
    public void run(ApplicationArguments args) {
        ProdSecretsGuard.assertSafe(profiles, serviceKey, jwtSecret, demoAuthKey);
        ProdSecretsGuard.assertAuthFlags(profiles, System.getenv("PLATFORM_ENV"),
                demoAuthEnabled, requireGateway);
    }
}
