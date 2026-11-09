package com.platform.payroll.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Payroll owns one fixed PostgreSQL database (spring.datasource.*, guide §1/§5).
 * The DataSource and Liquibase are configured by Spring Boot from application.yml.
 */
@Configuration
public class PayrollConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
