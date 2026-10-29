package com.platform.payroll;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Payroll modular monolith — reusable, multi-tenant.
 * Calls HRMS via gRPC only (never touches HRMS databases).
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class PayrollApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayrollApplication.class, args);
    }
}
