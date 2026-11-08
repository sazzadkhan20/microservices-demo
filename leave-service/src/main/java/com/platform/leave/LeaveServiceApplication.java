package com.platform.leave;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Leave service — policies, approvals, balances, ledger (guide §3).
 * Own DB: leave_&lt;tenant&gt;. Employee/RBAC via People HTTP; schedules via Time&amp;Work HTTP.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@ComponentScan(basePackages = {"com.platform.leave", "com.platform.messaging"})
@EntityScan(basePackages = {"com.platform.leave", "com.platform.messaging.outbox"})
@EnableJpaRepositories(basePackages = {"com.platform.leave", "com.platform.messaging.outbox"})
@EnableScheduling
public class LeaveServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeaveServiceApplication.class, args);
    }
}
