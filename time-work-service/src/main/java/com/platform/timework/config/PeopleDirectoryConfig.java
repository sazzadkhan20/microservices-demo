package com.platform.timework.config;

import com.platform.common.people.PeopleDirectoryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class PeopleDirectoryConfig {

    @Bean
    public PeopleDirectoryClient peopleDirectoryClient(
            @Value("${platform.people-base-url:${platform.hrms-base-url:http://127.0.0.1:8081}}") String baseUrl,
            @Value("${platform.security.service-key}") String serviceKey,
            @Value("${platform.security.internal-key}") String internalKey) {
        return new PeopleDirectoryClient(baseUrl, serviceKey, internalKey, Duration.ofSeconds(30));
    }
}
