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
        return new JwtSupport(properties.getJwt().getSecret());
    }
}
