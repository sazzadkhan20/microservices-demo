package com.platform.gateway.config;

import com.platform.common.security.JwtSupport;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayConfig {

    @Bean
    public JwtSupport jwtSupport(GatewayProperties properties) {
        String serviceKey = properties.getSecurity().getServiceKey();
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException(
                    "platform.security.service-key (PLATFORM_SERVICE_KEY) must be set: downstream services require it");
        }
        return new JwtSupport(properties.getJwt().getSecret());
    }
}
