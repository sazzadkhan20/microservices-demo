package com.platform.timework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Time &amp; Work — schedules, attendance, tasks, work logs (guide §3).
 * Own fixed DB: timework_db. Employee/RBAC via People HTTP; leave facts via Leave HTTP.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.platform.timework", "com.platform.messaging"})
@EntityScan(basePackages = {"com.platform.timework", "com.platform.messaging.outbox"})
@EnableJpaRepositories(basePackages = {"com.platform.timework", "com.platform.messaging.outbox"})
@EnableScheduling
public class TimeWorkApplication {

    public static void main(String[] args) {
        SpringApplication.run(TimeWorkApplication.class, args);
    }
}
