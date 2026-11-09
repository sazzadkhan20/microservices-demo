package com.platform.people;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * People service — employees, org, RBAC, employment (guide §3).
 * Own fixed DB: people_db. Cross-service data via HTTP / RabbitMQ only.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.platform.people", "com.platform.messaging"})
@EntityScan(basePackages = {"com.platform.people", "com.platform.messaging.outbox"})
@EnableJpaRepositories(basePackages = {"com.platform.people", "com.platform.messaging.outbox"})
@EnableScheduling
public class PeopleApplication {

    public static void main(String[] args) {
        SpringApplication.run(PeopleApplication.class, args);
    }
}
