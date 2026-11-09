package com.platform.payroll.config;

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

    @Value("${spring.rabbitmq.password:}")
    private String rabbitPassword;

    @Value("${platform.security.require-gateway:true}")
    private boolean requireGateway;

    @Value("${platform.messaging.enabled:true}")
    private boolean messagingEnabled;

    @Override
    public void run(ApplicationArguments args) {
        ProdSecretsGuard.assertSafe(profiles, serviceKey, internalKey, rabbitPassword);
        ProdSecretsGuard.assertAuthFlags(profiles, System.getenv("PLATFORM_ENV"),
                false, requireGateway);
        if (ProdSecretsGuard.isProd(profiles, System.getenv("PLATFORM_ENV")) && !messagingEnabled) {
            throw new IllegalStateException(
                    "Refusing to start in production: platform.messaging.enabled must be true (PAY-09).");
        }
    }
}
