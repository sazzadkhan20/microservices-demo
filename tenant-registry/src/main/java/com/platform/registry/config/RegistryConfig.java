package com.platform.registry.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.security.JwtSupport;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(PlatformProperties.class)
public class RegistryConfig {

    @Bean
    public JwtSupport jwtSupport(PlatformProperties properties) {
        return new JwtSupport(properties.getJwt().getSecret());
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
