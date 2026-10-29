package com.platform.hrms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * HRMS modular monolith (reusable multi-tenant service).
 *
 * <p>Layers (inside this deployable):
 * <ul>
 *   <li>{@code api} — REST + gRPC adapters</li>
 *   <li>{@code application} — use cases</li>
 *   <li>{@code domain} — entities</li>
 *   <li>{@code extension} — config-driven engines (SOLID extension points)</li>
 *   <li>{@code infrastructure} — JPA, Redis, multi-tenant routing</li>
 * </ul>
 *
 * <p>DataSource auto-config is excluded because we supply a routing DataSource.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class HrmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrmsApplication.class, args);
    }
}
