package com.platform.payroll;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Payroll modular monolith — reusable, multi-tenant.
 * Calls HRMS via gRPC / messaging (never touches HRMS databases).
 */
@SpringBootApplication(
        exclude = DataSourceAutoConfiguration.class,
        scanBasePackages = {"com.platform.payroll", "com.platform.messaging"})
@EntityScan(basePackages = {"com.platform.payroll", "com.platform.messaging.outbox"})
@EnableJpaRepositories(basePackages = {"com.platform.payroll", "com.platform.messaging.outbox"})
@EnableScheduling
public class PayrollApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayrollApplication.class, args);
    }
}
