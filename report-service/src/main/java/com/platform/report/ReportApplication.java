package com.platform.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.platform.report", "com.platform.messaging"})
@EntityScan(basePackages = {"com.platform.report", "com.platform.messaging.outbox"})
@EnableJpaRepositories(basePackages = {"com.platform.report", "com.platform.messaging.outbox"})
@EnableScheduling
public class ReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportApplication.class, args);
    }
}
